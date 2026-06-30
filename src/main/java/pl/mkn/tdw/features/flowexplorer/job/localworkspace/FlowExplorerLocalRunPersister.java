package pl.mkn.tdw.features.flowexplorer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.export.FlowExplorerExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
@RequiredArgsConstructor
public class FlowExplorerLocalRunPersister implements FlowExplorerLocalRunPersistence {

    static final String FEATURE = "flow-explorer";

    private static final String COMPLETED = "COMPLETED";

    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore localAnalysisRunStore;

    @Override
    public void persistCompletedInitialRun(
            FlowExplorerJobStateSnapshot snapshot,
            AnalysisAiAuthRef authRef,
            String copilotSessionId
    ) {
        if (snapshot == null || !COMPLETED.equals(snapshot.status())) {
            return;
        }

        var exportEnvelope = FlowExplorerExportEnvelope.from(snapshot, snapshot.completedAt());
        var record = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(exportEnvelope),
                continuation(authRef, copilotSessionId)
        );
        localAnalysisRunStore.save(indexEntry(snapshot), record);
    }

    private LocalAnalysisRunIndexEntry indexEntry(FlowExplorerJobStateSnapshot snapshot) {
        return new LocalAnalysisRunIndexEntry(
                snapshot.jobId(),
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + snapshot.jobId() + "/run.json",
                FEATURE,
                displayName(snapshot),
                snapshot.status(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt()
        );
    }

    private String displayName(FlowExplorerJobStateSnapshot snapshot) {
        var target = endpointLabel(snapshot);
        var goal = snapshot.goal() != null ? snapshot.goal().name() : null;
        if (StringUtils.hasText(target) && StringUtils.hasText(goal)) {
            return target + " / " + goal;
        }
        if (StringUtils.hasText(target)) {
            return target;
        }
        if (StringUtils.hasText(snapshot.systemId()) && StringUtils.hasText(goal)) {
            return snapshot.systemId() + " / " + goal;
        }
        return StringUtils.hasText(snapshot.jobId()) ? snapshot.jobId() : "Flow Explorer run";
    }

    private String endpointLabel(FlowExplorerJobStateSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.httpMethod()) && StringUtils.hasText(snapshot.endpointPath())) {
            return snapshot.httpMethod() + " " + snapshot.endpointPath();
        }
        if (StringUtils.hasText(snapshot.endpointPath())) {
            return snapshot.endpointPath();
        }
        return snapshot.endpointId();
    }

    private LocalAnalysisRunContinuation continuation(
            AnalysisAiAuthRef authRef,
            String copilotSessionId
    ) {
        var resolvedAuthRef = authRef != null ? authRef : AnalysisAiAuthRef.localToken(null);
        return new LocalAnalysisRunContinuation(
                StringUtils.hasText(copilotSessionId),
                null,
                resolvedAuthRef.mode(),
                resolvedAuthRef.principalId()
        ).withLatestCopilotSession(copilotSessionId);
    }
}
