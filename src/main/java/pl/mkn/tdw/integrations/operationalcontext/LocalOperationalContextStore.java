package pl.mkn.tdw.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
final class LocalOperationalContextStore {

    private final OperationalContextProperties properties;
    private final OperationalContextDocumentSource classpathSource;
    private final OperationalContextCatalogCodec catalogCodec;
    private final OperationalContextAtomicMover atomicMover;
    private final OperationalContextCatalogValidationService validationService;

    synchronized OperationalContextStoredSnapshot loadOrBootstrap() {
        var root = properties.resolvedStorageDirectory();
        if (!Files.exists(root)) {
            bootstrap(root, classpathSource.loadDocuments().contents());
        }
        return load(root);
    }

    synchronized OperationalContextStoredSnapshot publishCandidate(Map<String, String> candidateDocuments) {
        var root = properties.resolvedStorageDirectory();
        var current = loadOrBootstrap();
        var candidate = normalized(candidateDocuments);
        var changed = candidate.entrySet().stream()
                .filter(entry -> !java.util.Objects.equals(current.rawDocuments().content(entry.getKey()), entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (changed.isEmpty()) {
            return current;
        }
        if (changed.size() != 1) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_CANDIDATE,
                    "One maintenance operation must change exactly one operational context document"
            );
        }

        var candidateSnapshot = snapshot(candidate);
        var decision = validationService.validateForCommit(
                candidateSnapshot.readSnapshot().catalog(),
                current.validationFindings()
        );
        if (!decision.allowed()) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_CANDIDATE,
                    "Operational context candidate violates catalog validation rules"
            );
        }

        replace(root, changed.get(0), candidate.get(changed.get(0)));
        log.info("Operational context local copy updated document={}", changed.get(0));
        return candidateSnapshot;
    }

    private void bootstrap(Path root, Map<String, String> seed) {
        var parent = root.getParent();
        if (parent == null) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_STORAGE_PATH,
                    "Operational context directory cannot be a filesystem root"
            );
        }
        var staging = parent.resolve(".operational-context-seed-" + UUID.randomUUID()).normalize();
        try {
            Files.createDirectories(parent);
            Files.createDirectory(staging);
            for (var entry : normalized(seed).entrySet()) {
                Files.writeString(
                        staging.resolve(entry.getKey()),
                        entry.getValue(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                );
            }
            atomicMover.moveDirectory(staging, root);
            log.info("Operational context local copy initialized directory={}", root);
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            cleanupStaging(staging);
        } catch (Exception exception) {
            cleanupStaging(staging);
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.LOCAL_COPY_UNAVAILABLE,
                    "Cannot initialize operational context local copy",
                    exception
            );
        }
    }

    private OperationalContextStoredSnapshot load(Path root) {
        if (!Files.isDirectory(root)) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.LOCAL_COPY_UNAVAILABLE,
                    "Operational context local copy is not a directory"
            );
        }
        var contents = new LinkedHashMap<String, String>();
        try {
            for (var name : ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES) {
                var file = root.resolve(name).normalize();
                if (!file.getParent().equals(root) || !Files.isRegularFile(file)) {
                    throw new OperationalContextStoreException(
                            OperationalContextStoreException.Code.CORRUPT_STORE,
                            "Operational context local copy is missing document: " + name
                    );
                }
                contents.put(name, Files.readString(file, StandardCharsets.UTF_8));
            }
            return snapshot(contents);
        } catch (OperationalContextStoreException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.LOCAL_COPY_UNAVAILABLE,
                    "Cannot read operational context local copy",
                    exception
            );
        }
    }

    private OperationalContextStoredSnapshot snapshot(Map<String, String> contents) {
        try {
            var raw = new OperationalContextRawDocuments("tdw-data/operational-context", contents);
            var decoded = catalogCodec.decode(raw);
            var validation = validationService.validate(decoded.catalog());
            return new OperationalContextStoredSnapshot(
                    raw,
                    decoded.decodedDocuments(),
                    decoded.catalog(),
                    validation.findings(),
                    Instant.now()
            );
        } catch (OperationalContextStoreException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_CANDIDATE,
                    "Operational context local copy cannot be decoded",
                    exception
            );
        }
    }

    private Map<String, String> normalized(Map<String, String> documents) {
        if (documents == null || !documents.keySet().equals(SetHolder.DOCUMENT_NAMES)) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_CANDIDATE,
                    "Operational context local copy must contain the complete document set"
            );
        }
        var result = new LinkedHashMap<String, String>();
        ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES.forEach(name ->
                result.put(name, java.util.Objects.requireNonNullElse(documents.get(name), ""))
        );
        return Map.copyOf(result);
    }

    private void replace(Path root, String name, String content) {
        var target = root.resolve(name).normalize();
        var temporary = root.resolve("." + name + "." + UUID.randomUUID() + ".tmp").normalize();
        if (!target.getParent().equals(root) || !temporary.getParent().equals(root)) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_STORAGE_PATH,
                    "Operational context document escaped the local directory"
            );
        }
        try {
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            atomicMover.replaceFile(temporary, target);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Best-effort cleanup of a generated temporary file.
            }
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.LOCAL_COPY_UNAVAILABLE,
                    "Cannot update operational context document: " + name,
                    exception
            );
        }
    }

    private void cleanupStaging(Path staging) {
        try {
            if (!Files.isDirectory(staging)) {
                return;
            }
            for (var name : ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES) {
                Files.deleteIfExists(staging.resolve(name));
            }
            Files.deleteIfExists(staging);
        } catch (IOException ignored) {
            // Best-effort cleanup of a generated seed directory.
        }
    }

    private static final class SetHolder {
        private static final java.util.Set<String> DOCUMENT_NAMES =
                java.util.Set.copyOf(ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES);
    }
}
