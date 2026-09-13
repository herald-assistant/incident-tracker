package pl.mkn.tdw.integrations.operationalcontext;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.nio.file.LinkOption;

@Component
@Slf4j
@RequiredArgsConstructor
final class LocalOperationalContextStore {

    private static final String BATCH_JOURNAL = ".opctx-batch-journal";
    private static final String PREPARED = "prepared";

    private final OperationalContextProperties properties;
    private final OperationalContextDocumentSource classpathSource;
    private final OperationalContextCatalogCodec catalogCodec;
    private final OperationalContextAtomicMover atomicMover;
    private final OperationalContextCatalogValidationService validationService;

    @PostConstruct
    void initializeLocalCopyAtStartup() {
        if (properties.isEnabled()) {
            loadOrBootstrap();
        }
    }

    synchronized OperationalContextStoredSnapshot loadOrBootstrap() {
        var root = properties.resolvedStorageDirectory();
        if (!Files.exists(root)) {
            bootstrap(root, classpathSource.loadDocuments().contents());
        }
        recoverBatch(root);
        return load(root);
    }

    synchronized OperationalContextStoredSnapshot publishCandidate(Map<String, String> candidateDocuments) {
        var root = properties.resolvedStorageDirectory();
        var current = loadOrBootstrap();
        var decision = assess(current, candidateDocuments, false);
        if (decision.changedDocuments().isEmpty()) {
            return current;
        }
        if (!decision.assessment().valid()) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_CANDIDATE,
                    "Operational context candidate violates catalog validation rules"
            );
        }

        var changedDocument = decision.changedDocuments().get(0);
        replace(root, changedDocument, decision.candidateSnapshot().rawDocuments().content(changedDocument));
        log.info("Operational context local copy updated document={}", changedDocument);
        return decision.candidateSnapshot();
    }

    synchronized OperationalContextCandidateAssessment assessCandidate(Map<String, String> candidateDocuments) {
        return assess(loadOrBootstrap(), candidateDocuments, false).assessment();
    }

    synchronized OperationalContextStoredSnapshot decodeCandidate(Map<String, String> candidateDocuments) {
        return snapshot(normalized(candidateDocuments));
    }

    synchronized OperationalContextCandidateAssessment assessBatchCandidate(Map<String, String> candidateDocuments) {
        return assess(loadOrBootstrap(), candidateDocuments, true).assessment();
    }

    synchronized OperationalContextStoredSnapshot publishBatchCandidate(
            Map<String, String> candidateDocuments, String expectedDigest
    ) {
        var root = properties.resolvedStorageDirectory();
        var current = loadOrBootstrap();
        if (!Objects.equals(expectedDigest, current.readSnapshot().contentDigest())) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.STALE_CANDIDATE,
                    "Operational context local copy changed before batch publication"
            );
        }
        var decision = assess(current, candidateDocuments, true);
        if (!decision.assessment().valid()) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_CANDIDATE,
                    "Operational context batch violates catalog validation rules"
            );
        }
        if (decision.changedDocuments().isEmpty()) {
            return current;
        }
        commitBatch(root, current, decision);
        log.info("Operational context local copy updated batch documents={}", decision.changedDocuments());
        return decision.candidateSnapshot();
    }

    private CandidateDecision assess(
            OperationalContextStoredSnapshot current,
            Map<String, String> candidateDocuments,
            boolean allowMultiple
    ) {
        var candidate = normalized(candidateDocuments);
        var changed = ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES.stream()
                .filter(name -> !Objects.equals(current.rawDocuments().content(name), candidate.get(name)))
                .toList();
        if (changed.isEmpty()) {
            return new CandidateDecision(List.of(), current, new OperationalContextCandidateAssessment(List.of()));
        }
        if (!allowMultiple && changed.size() != 1) {
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
        var violations = decision.violations().stream()
                .map(violation -> new OperationalContextCatalogPreviewViolation(
                        violation.code(), violation.fingerprint(), violation.ruleCode(), violation.severity()
                ))
                .toList();
        return new CandidateDecision(
                changed, candidateSnapshot,
                new OperationalContextCandidateAssessment(violations)
        );
    }

    private record CandidateDecision(
            List<String> changedDocuments,
            OperationalContextStoredSnapshot candidateSnapshot,
            OperationalContextCandidateAssessment assessment
    ) {
    }

    /**
     * The prepared marker is the transaction boundary. Until it is removed, startup restores
     * every original YAML from the journal. A missing marker means no replacements began, or
     * all replacements completed and the batch was committed.
     */
    private void commitBatch(
            Path root, OperationalContextStoredSnapshot current, CandidateDecision decision
    ) {
        var journal = root.resolve(BATCH_JOURNAL).normalize();
        try {
            Files.createDirectory(journal);
            Files.createDirectory(journal.resolve("backup"));
            Files.createDirectory(journal.resolve("candidate"));
            for (var name : ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES) {
                var backup = journal.resolve("backup").resolve(name);
                Files.copy(root.resolve(name), backup);
                forceFile(backup);
                writeDurable(journal.resolve("candidate").resolve(name),
                        decision.candidateSnapshot().rawDocuments().content(name));
            }
            writeDurable(journal.resolve("prepared.tmp"),
                    current.readSnapshot().contentDigest() + "\n"
                            + decision.candidateSnapshot().readSnapshot().contentDigest() + "\n");
            atomicMover.replaceFile(journal.resolve("prepared.tmp"), journal.resolve(PREPARED));
            for (var name : decision.changedDocuments()) {
                replaceFromJournal(root, journal.resolve("candidate").resolve(name), name);
            }
            Files.delete(journal.resolve(PREPARED));
        } catch (IOException exception) {
            try {
                recoverBatch(root);
            } catch (OperationalContextStoreException recoveryFailure) {
                exception.addSuppressed(recoveryFailure);
            }
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.LOCAL_COPY_UNAVAILABLE,
                    "Cannot commit operational context batch; the journal remains for recovery",
                    exception
            );
        }
        cleanupJournalQuietly(journal);
    }

    private void recoverBatch(Path root) {
        var journal = root.resolve(BATCH_JOURNAL).normalize();
        if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(journal, LinkOption.NOFOLLOW_LINKS)) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.CORRUPT_STORE,
                    "Operational context batch journal is not a directory"
            );
        }
        var marker = journal.resolve(PREPARED);
        try {
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                        || !Files.isDirectory(journal.resolve("backup"), LinkOption.NOFOLLOW_LINKS)) {
                    throw new OperationalContextStoreException(
                            OperationalContextStoreException.Code.CORRUPT_STORE,
                            "Operational context batch journal is incomplete"
                    );
                }
                for (var name : ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES) {
                    var backup = journal.resolve("backup").resolve(name);
                    if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) {
                        throw new OperationalContextStoreException(
                                OperationalContextStoreException.Code.CORRUPT_STORE,
                                "Operational context batch backup is incomplete: " + name
                        );
                    }
                }
                for (var name : ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES) {
                    replaceFromJournal(root, journal.resolve("backup").resolve(name), name);
                }
                Files.delete(marker);
                log.warn("Recovered interrupted operational context batch from local journal");
            }
            cleanupJournal(journal);
        } catch (IOException exception) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.LOCAL_COPY_UNAVAILABLE,
                    "Cannot recover interrupted operational context batch",
                    exception
            );
        }
    }

    private void replaceFromJournal(Path root, Path source, String name) throws IOException {
        var temporary = root.resolve("." + name + "." + UUID.randomUUID() + ".tmp").normalize();
        var target = root.resolve(name).normalize();
        if (!temporary.getParent().equals(root) || !target.getParent().equals(root)) {
            throw new IOException("Batch document path escaped the operational context directory");
        }
        try {
            Files.copy(source, temporary);
            forceFile(temporary);
            atomicMover.replaceFile(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeDurable(Path path, String content) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
    }

    private void forceFile(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void cleanupJournal(Path journal) throws IOException {
        Files.deleteIfExists(journal.resolve("prepared.tmp"));
        for (var name : ClasspathOperationalContextDocumentSource.DOCUMENT_NAMES) {
            Files.deleteIfExists(journal.resolve("backup").resolve(name));
            Files.deleteIfExists(journal.resolve("candidate").resolve(name));
        }
        Files.deleteIfExists(journal.resolve("backup"));
        Files.deleteIfExists(journal.resolve("candidate"));
        Files.deleteIfExists(journal);
    }

    private void cleanupJournalQuietly(Path journal) {
        try {
            cleanupJournal(journal);
        } catch (IOException exception) {
            log.warn("Committed operational context batch left a removable journal directory", exception);
        }
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
