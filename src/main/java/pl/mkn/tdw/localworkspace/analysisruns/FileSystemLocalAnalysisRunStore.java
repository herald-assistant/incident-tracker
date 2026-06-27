package pl.mkn.tdw.localworkspace.analysisruns;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.LocalWorkspaceStorageException;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileSystemLocalAnalysisRunStore implements LocalAnalysisRunStore {

    private final LocalWorkspaceProperties properties;
    private final LocalWorkspacePaths paths;
    private final LocalWorkspaceJsonFileStore jsonFileStore;

    @Override
    public List<LocalAnalysisRunIndexEntry> listRuns() {
        if (!properties.isEnabled()) {
            return List.of();
        }

        return readIndex().runs().stream()
                .sorted(Comparator.comparing(
                        LocalAnalysisRunIndexEntry::updatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .toList();
    }

    @Override
    public Optional<LocalAnalysisRunRecord> findById(String analysisId) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        return jsonFileStore.read(paths.runFile(analysisId), LocalAnalysisRunRecord.class);
    }

    @Override
    public void save(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record) {
        if (!properties.isEnabled()) {
            return;
        }

        var normalizedEntry = withWorkspaceRunPath(indexEntry);
        jsonFileStore.writeAtomic(paths.runFile(normalizedEntry.analysisId()), record);
        writeIndex(upsert(readIndex(), normalizedEntry));
    }

    @Override
    public void rename(String analysisId, String name) {
        if (!properties.isEnabled()) {
            return;
        }

        var index = readIndex();
        var updatedRuns = index.runs().stream()
                .map(entry -> entry.analysisId().equals(analysisId) ? entry.withName(name, Instant.now()) : entry)
                .toList();
        writeIndex(new LocalAnalysisRunIndex(index.schema(), index.version(), updatedRuns));
    }

    @Override
    public void delete(String analysisId) {
        if (!properties.isEnabled()) {
            return;
        }

        var index = readIndex();
        var updatedRuns = index.runs().stream()
                .filter(entry -> !entry.analysisId().equals(analysisId))
                .toList();
        writeIndex(new LocalAnalysisRunIndex(index.schema(), index.version(), updatedRuns));
        deleteRunDirectory(paths.runDirectory(analysisId));
    }

    private LocalAnalysisRunIndex readIndex() {
        return jsonFileStore.read(paths.indexFile(), LocalAnalysisRunIndex.class)
                .orElseGet(LocalAnalysisRunIndex::empty);
    }

    private void writeIndex(LocalAnalysisRunIndex index) {
        jsonFileStore.writeAtomic(paths.indexFile(), index);
    }

    private LocalAnalysisRunIndex upsert(
            LocalAnalysisRunIndex index,
            LocalAnalysisRunIndexEntry indexEntry
    ) {
        var updatedRuns = index.runs().stream()
                .filter(entry -> !entry.analysisId().equals(indexEntry.analysisId()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        updatedRuns.add(indexEntry);
        return new LocalAnalysisRunIndex(index.schema(), index.version(), updatedRuns);
    }

    private LocalAnalysisRunIndexEntry withWorkspaceRunPath(LocalAnalysisRunIndexEntry indexEntry) {
        return indexEntry.withRunPath(paths.runPath(indexEntry.analysisId()));
    }

    private void deleteRunDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }

        try (var files = Files.walk(directory)) {
            var pathsToDelete = files
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (var path : pathsToDelete) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            log.warn("Failed to delete local analysis run directory path={}", directory, exception);
            throw new LocalWorkspaceStorageException("Failed to delete local analysis run directory: " + directory, exception);
        }
    }
}
