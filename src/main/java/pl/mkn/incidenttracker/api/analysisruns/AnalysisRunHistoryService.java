package pl.mkn.incidenttracker.api.analysisruns;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.incidenttracker.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisRunHistoryService {

    private final LocalAnalysisRunStore localAnalysisRunStore;

    public LocalAnalysisRunListResponse listRuns() {
        return new LocalAnalysisRunListResponse(
                localAnalysisRunStore.listRuns().stream()
                        .map(this::toListItem)
                        .toList()
        );
    }

    public LocalAnalysisRunDetailResponse getRun(String analysisId) {
        var indexEntry = indexEntryOrThrow(analysisId);
        var record = recordOrThrow(indexEntry.analysisId());
        return toDetail(indexEntry, record);
    }

    public LocalAnalysisRunDetailResponse renameRun(String analysisId, String name) {
        var indexEntry = indexEntryOrThrow(analysisId);
        localAnalysisRunStore.rename(indexEntry.analysisId(), name.trim());
        return getRun(indexEntry.analysisId());
    }

    public void deleteRun(String analysisId) {
        var indexEntry = indexEntryOrThrow(analysisId);
        localAnalysisRunStore.delete(indexEntry.analysisId());
    }

    private LocalAnalysisRunIndexEntry indexEntryOrThrow(String analysisId) {
        var normalizedAnalysisId = requireAnalysisId(analysisId);
        return localAnalysisRunStore.listRuns().stream()
                .filter(entry -> entry.analysisId().equals(normalizedAnalysisId))
                .findFirst()
                .orElseThrow(() -> new LocalAnalysisRunNotFoundException(normalizedAnalysisId));
    }

    private LocalAnalysisRunRecord recordOrThrow(String analysisId) {
        var normalizedAnalysisId = requireAnalysisId(analysisId);
        return localAnalysisRunStore.findById(normalizedAnalysisId)
                .orElseThrow(() -> new LocalAnalysisRunCorruptedException(normalizedAnalysisId));
    }

    private LocalAnalysisRunListItemResponse toListItem(LocalAnalysisRunIndexEntry entry) {
        return new LocalAnalysisRunListItemResponse(
                entry.analysisId(),
                entry.feature(),
                entry.name(),
                entry.createdAt(),
                entry.updatedAt(),
                entry.completedAt()
        );
    }

    private LocalAnalysisRunDetailResponse toDetail(
            LocalAnalysisRunIndexEntry entry,
            LocalAnalysisRunRecord record
    ) {
        return new LocalAnalysisRunDetailResponse(
                entry.analysisId(),
                entry.feature(),
                entry.name(),
                entry.createdAt(),
                entry.updatedAt(),
                entry.completedAt(),
                record.exportEnvelope(),
                record.continuation() != null && record.continuation().enabled()
        );
    }

    private String requireAnalysisId(String analysisId) {
        if (!StringUtils.hasText(analysisId)) {
            throw new LocalAnalysisRunNotFoundException(analysisId);
        }
        return analysisId.trim();
    }
}
