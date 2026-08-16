package pl.mkn.tdw.features.uiexplorer.job.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailability;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailabilityStatus;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerImportException;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerImportPersistenceException;
import pl.mkn.tdw.features.uiexplorer.job.export.UiExplorerExportEnvelope;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunPersistence;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunSnapshotSanitizer;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UiExplorerImportService {

    private static final int MAX_IMPORT_CHARACTERS = 2_000_000;

    private final ObjectMapper objectMapper;
    private final LocalWorkspaceProperties localWorkspaceProperties;
    private final UiExplorerLocalRunSnapshotSanitizer sanitizer;
    private final UiExplorerLocalRunPersistence localRunPersistence;

    public UiExplorerJobStateSnapshot importReadOnly(JsonNode document) {
        validateDocument(document);
        var envelope = parse(document);
        validateEnvelope(envelope);
        var importedAt = Instant.now();
        var imported = asImported(sanitizer.sanitize(envelope.payload().job()), importedAt);
        if (!localWorkspaceProperties.isEnabled()) {
            throw new UiExplorerImportPersistenceException();
        }
        try {
            localRunPersistence.persistTerminalSnapshot(imported);
        } catch (RuntimeException exception) {
            throw new UiExplorerImportPersistenceException();
        }
        return imported;
    }

    private UiExplorerExportEnvelope parse(JsonNode document) {
        try {
            return objectMapper.treeToValue(document, UiExplorerExportEnvelope.class);
        } catch (Exception exception) {
            throw invalid("UI Explorer export has an invalid structure.");
        }
    }

    private void validateDocument(JsonNode document) {
        if (document == null || document.isNull()) {
            throw invalid("UI Explorer export is required.");
        }
        if (!document.isObject() || document.toString().length() > MAX_IMPORT_CHARACTERS) {
            throw invalid("UI Explorer export has an invalid or oversized structure.");
        }
    }

    private void validateEnvelope(UiExplorerExportEnvelope envelope) {
        if (envelope == null || !UiExplorerExportEnvelope.SCHEMA.equals(envelope.schema())) {
            throw invalid("Unsupported UI Explorer export schema.");
        }
        if (envelope.version() != UiExplorerExportEnvelope.VERSION) {
            throw invalid("Unsupported UI Explorer export version.");
        }
        if (envelope.exportedAt() == null
                || envelope.payload() == null
                || !UiExplorerExportEnvelope.PAYLOAD_TYPE.equals(envelope.payload().type())
                || !UiExplorerExportEnvelope.RESULT_CONTRACT.equals(envelope.payload().resultContract())) {
            throw invalid("Unsupported UI Explorer result contract.");
        }
        validateJob(envelope.payload().job());
    }

    private void validateJob(UiExplorerJobStateSnapshot job) {
        if (job == null
                || (job.status() != UiExplorerJobStatus.COMPLETED && job.status() != UiExplorerJobStatus.PARTIAL)
                || job.request() == null
                || job.result() == null
                || job.result().screen() == null
                || job.sourceRevision() == null
                || job.result().sourceRevision() == null
                || job.result().sections().isEmpty()
                || job.createdAt() == null
                || job.completedAt() == null) {
            throw invalid("Only a completed UI Explorer result can be imported.");
        }
        if (!StringUtils.hasText(job.request().systemId())
                || !StringUtils.hasText(job.request().screenId())
                || !StringUtils.hasText(job.request().branch())
                || !StringUtils.hasText(job.request().sourceRevision())
                || !StringUtils.hasText(job.sourceRevision().branch())
                || !StringUtils.hasText(job.sourceRevision().revision())
                || !job.request().systemId().equals(job.result().screen().systemId())
                || !job.request().screenId().equals(job.result().screen().screenId())
                || !job.sourceRevision().equals(job.result().sourceRevision())
                || !job.request().branch().equals(job.sourceRevision().branch())
                || !job.request().sourceRevision().equals(job.sourceRevision().revision())) {
            throw invalid("UI Explorer export has inconsistent screen or source revision data.");
        }
    }

    private UiExplorerJobStateSnapshot asImported(UiExplorerJobStateSnapshot source, Instant importedAt) {
        var importedJobId = "ui-explorer-import-" + UUID.randomUUID();
        var outputAvailability = new UiExplorerOutputAvailability(
                UiExplorerOutputAvailabilityStatus.AVAILABLE,
                "UI_EXPLORER_IMPORTED_OUTPUT_AVAILABLE",
                "A read-only imported UI Explorer result and report are available.",
                List.of()
        );
        return new UiExplorerJobStateSnapshot(
                importedJobId,
                source.request(),
                source.status(),
                null,
                null,
                source.errorCode(),
                source.errorMessage(),
                source.createdAt(),
                importedAt,
                source.completedAt(),
                source.steps(),
                source.contextSections(),
                source.toolEvidenceSections(),
                source.aiActivityEvents(),
                List.of(),
                null,
                source.result(),
                source.report(),
                source.usage(),
                source.sourceRevision(),
                outputAvailability,
                true
        );
    }

    private UiExplorerImportException invalid(String message) {
        return new UiExplorerImportException(message);
    }
}
