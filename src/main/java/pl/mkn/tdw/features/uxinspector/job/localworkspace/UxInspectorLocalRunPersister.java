package pl.mkn.tdw.features.uxinspector.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStateSnapshot;
import pl.mkn.tdw.features.uxinspector.job.export.UxInspectorExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UxInspectorLocalRunPersister implements UxInspectorLocalRunPersistence {
    public static final String FEATURE = "ux-inspector";
    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore store;

    @Override
    public void persistRunSnapshot(UxInspectorJobStateSnapshot snapshot) {
        persistRunSnapshot(snapshot, null, null);
    }

    @Override
    public void persistRunSnapshot(UxInspectorJobStateSnapshot snapshot, AnalysisAiAuthRef authRef, String copilotSessionId) {
        if (snapshot == null) return;
        var timestamp = snapshot.completedAt() != null ? snapshot.completedAt()
                : snapshot.updatedAt() != null ? snapshot.updatedAt() : snapshot.createdAt();
        var eligible = (snapshot.status() == pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStatus.COMPLETED
                || snapshot.status() == pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStatus.PARTIAL)
                && snapshot.report() != null && snapshot.result() != null && StringUtils.hasText(copilotSessionId);
        var continuation = eligible
                ? new LocalAnalysisRunContinuation(true, null,
                    authRef != null ? authRef.mode() : AnalysisAiAuthRef.MODE_LOCAL_TOKEN,
                    authRef != null ? authRef.principalId() : null, copilotSessionId.trim(),
                    LocalAnalysisRunContinuation.COPILOT_RUNTIME_GITHUB_COPILOT_SDK,
                    LocalAnalysisRunContinuation.CONTINUATION_MODE_COPILOT_SESSION)
                : new LocalAnalysisRunContinuation(false, null, null, null, null, null, null);
        var record = LocalAnalysisRunRecord.v1(objectMapper.valueToTree(UxInspectorExportEnvelope.from(snapshot, timestamp)), continuation);
        store.save(new LocalAnalysisRunIndexEntry(snapshot.jobId(), LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION, "runs/" + snapshot.jobId() + "/run.json", FEATURE,
                displayName(snapshot), snapshot.status().name(), snapshot.createdAt(), snapshot.updatedAt(),
                snapshot.completedAt()), record);
    }

    private String displayName(UxInspectorJobStateSnapshot snapshot) {
        var target = snapshot.result() != null ? snapshot.result().targetLabel() : null;
        if (!StringUtils.hasText(target) && snapshot.request() != null && snapshot.request().capture() != null) {
            target = snapshot.request().capture().target().accessibleName();
        }
        if (!StringUtils.hasText(target) && snapshot.request() != null) target = snapshot.request().viewId();
        return StringUtils.hasText(target) ? target.trim() : "UX Inspector run";
    }
}
