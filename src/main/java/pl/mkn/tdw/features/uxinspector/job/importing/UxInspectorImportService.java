package pl.mkn.tdw.features.uxinspector.job.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCaptureNormalizer;
import pl.mkn.tdw.features.uxinspector.job.api.*;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportEnvelope;
import pl.mkn.tdw.features.uxinspector.job.localworkspace.UxInspectorLocalRunPersistence;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UxInspectorImportService {
    private static final int MAX_IMPORT_CHARS = 1_000_000;
    private final ObjectMapper objectMapper;
    private final LocalWorkspaceProperties properties;
    private final UxInspectorLocalRunPersistence persistence;
    private final UxInspectorCaptureNormalizer captureNormalizer;

    public UxInspectorJobStateSnapshot importReadOnly(JsonNode document) {
        validateDocument(document);
        UxInspectorExportEnvelope envelope;
        try { envelope = objectMapper.treeToValue(document, UxInspectorExportEnvelope.class); }
        catch (Exception exception) { throw invalid("UX Inspector export has an invalid structure."); }
        validateEnvelope(envelope);
        if (!properties.isEnabled()) throw invalid("Local workspace is required to import UX Inspector results.");
        var source = envelope.payload().job();
        var imported = new UxInspectorJobStateSnapshot("ux-inspector-import-" + UUID.randomUUID(), source.request(),
                source.status(), null, null, source.errorCode(), source.errorMessage(), source.createdAt(), Instant.now(),
                source.completedAt(), source.steps(), source.contextSections(), source.toolEvidenceSections(),
                source.aiActivityEvents(), source.toolFeedback(), null, source.result(), source.report(), source.usage(),
                source.sourceRevision(), new UxInspectorOutputAvailability("AVAILABLE", "UX_INSPECTOR_IMPORTED_OUTPUT_AVAILABLE",
                "A read-only imported UX Inspector answer is available.", List.of()), true);
        try { persistence.persistRunSnapshot(imported); }
        catch (RuntimeException exception) { throw invalid("UX Inspector import could not be persisted."); }
        return imported;
    }

    private void validateDocument(JsonNode document) {
        if (document == null || !document.isObject() || document.toString().length() > MAX_IMPORT_CHARS) throw invalid("UX Inspector export is required and must be bounded.");
        var allowed = Set.of("schema", "version", "exportedAt", "payload");
        var names = new java.util.HashSet<String>(); document.fieldNames().forEachRemaining(names::add);
        if (!allowed.equals(names)) throw invalid("UX Inspector export contains missing or unknown top-level fields.");
    }
    private void validateEnvelope(UxInspectorExportEnvelope value) {
        if (value == null || !UxInspectorExportEnvelope.SCHEMA.equals(value.schema())) throw invalid("Unsupported UX Inspector export schema.");
        if (value.version() != UxInspectorExportEnvelope.VERSION) throw invalid("Unsupported UX Inspector export version.");
        var payload = value.payload();
        if (value.exportedAt() == null || payload == null || !UxInspectorExportEnvelope.PAYLOAD_TYPE.equals(payload.type())
                || !UxInspectorExportEnvelope.RESULT_CONTRACT.equals(payload.resultContract())) throw invalid("Unsupported UX Inspector result contract.");
        var job = payload.job();
        if (job == null || job.request() == null || job.result() == null || job.report() == null
                || job.completedAt() == null || job.sourceRevision() == null
                || job.result().sourceRevision() == null || job.result().view() == null
                || (job.status() != UxInspectorJobStatus.COMPLETED && job.status() != UxInspectorJobStatus.PARTIAL)
                || job.report().sections().size() != 1 || !"answer".equals(job.report().sections().get(0).id())
                || job.request().capture() == null || job.request().capture().version() != 3) {
            throw invalid("Only a completed UX Inspector export v2 result with capture v3 can be imported.");
        }
        try {
            if (!captureNormalizer.normalize(job.request().capture()).equals(job.request().capture())) {
                throw invalid("UX Inspector import contains a non-canonical capture.");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid("UX Inspector import contains an invalid capture.");
        }
        if (!Objects.equals(job.request().capture().captureId(), job.result().captureId())
                || !Objects.equals(job.request().sourceRevision(), job.sourceRevision().revision())
                || !Objects.equals(job.sourceRevision(), job.result().sourceRevision())
                || !Objects.equals(job.request().viewId(), job.result().view().viewId())
                || !Objects.equals(job.report().sections().get(0).markdown(), job.result().answer())
                || !Objects.equals(job.report().markdownSummary(), job.result().thesis())) {
            throw invalid("UX Inspector import contains inconsistent result scope or report data.");
        }
    }
    private UxInspectorJobException invalid(String message) {
        return new UxInspectorJobException("UX_INSPECTOR_IMPORT_INVALID", UserFacingErrorType.BAD_REQUEST, message);
    }
}
