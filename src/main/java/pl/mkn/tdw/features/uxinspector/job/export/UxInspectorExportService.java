package pl.mkn.tdw.features.uxinspector.job.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.job.UxInspectorJobService;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStateSnapshot;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStatus;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UxInspectorExportService {
    private final ObjectMapper objectMapper;
    private final UxInspectorJobService jobService;
    private final LocalAnalysisRunStore store;

    public UxInspectorExportEnvelope export(String jobId) {
        var id = StringUtils.hasText(jobId) ? jobId.trim() : "";
        UxInspectorJobStateSnapshot snapshot;
        try { snapshot = jobService.getJob(id); }
        catch (UxInspectorJobException exception) {
            snapshot = store.findById(id).map(value -> parse(id, value.exportEnvelope())).orElseThrow(() -> exception);
        }
        if ((snapshot.status() != UxInspectorJobStatus.COMPLETED && snapshot.status() != UxInspectorJobStatus.PARTIAL)
                || snapshot.result() == null || snapshot.report() == null) {
            throw new UxInspectorJobException("UX_INSPECTOR_EXPORT_UNAVAILABLE", UserFacingErrorType.CONFLICT,
                    "Only a completed UX Inspector run with a report can be exported.");
        }
        return UxInspectorExportEnvelope.from(snapshot, Instant.now());
    }

    private UxInspectorJobStateSnapshot parse(String id, com.fasterxml.jackson.databind.JsonNode node) {
        try {
            var envelope = objectMapper.treeToValue(node, UxInspectorExportEnvelope.class);
            if (envelope == null || !UxInspectorExportEnvelope.SCHEMA.equals(envelope.schema())
                    || envelope.version() != UxInspectorExportEnvelope.VERSION || envelope.payload() == null
                    || !UxInspectorExportEnvelope.PAYLOAD_TYPE.equals(envelope.payload().type())
                    || !UxInspectorExportEnvelope.RESULT_CONTRACT.equals(envelope.payload().resultContract())
                    || envelope.payload().job() == null || !id.equals(envelope.payload().job().jobId())) throw new IllegalArgumentException();
            return envelope.payload().job();
        } catch (Exception exception) {
            throw new UxInspectorJobException("UX_INSPECTOR_EXPORT_UNAVAILABLE", UserFacingErrorType.CONFLICT,
                    "The UX Inspector history entry cannot be exported.");
        }
    }
}

