package pl.mkn.tdw.features.incidentanalysis.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;
import pl.mkn.tdw.features.incidentanalysis.job.export.IncidentAnalysisExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
@RequiredArgsConstructor
public class IncidentAnalysisLocalRunPersister implements IncidentAnalysisLocalRunPersistence {

    private static final String FEATURE = "incident-analysis";
    private static final String COMPLETED = "COMPLETED";

    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore localAnalysisRunStore;

    @Override
    public void persistCompletedInitialRun(
            AnalysisJobStateSnapshot snapshot,
            InitialAnalysisRequest aiRequest,
            String copilotSessionId
    ) {
        if (snapshot == null || !COMPLETED.equals(snapshot.status())) {
            return;
        }

        var exportEnvelope = IncidentAnalysisExportEnvelope.from(snapshot, snapshot.completedAt());
        var record = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(exportEnvelope),
                continuation(aiRequest, copilotSessionId)
        );
        localAnalysisRunStore.save(indexEntry(snapshot), record);
    }

    private LocalAnalysisRunIndexEntry indexEntry(AnalysisJobStateSnapshot snapshot) {
        return new LocalAnalysisRunIndexEntry(
                snapshot.analysisId(),
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + snapshot.analysisId() + "/run.json",
                FEATURE,
                displayName(snapshot),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt()
        );
    }

    private String displayName(AnalysisJobStateSnapshot snapshot) {
        return StringUtils.hasText(snapshot.correlationId())
                ? snapshot.correlationId()
                : snapshot.analysisId();
    }

    private LocalAnalysisRunContinuation continuation(
            InitialAnalysisRequest aiRequest,
            String copilotSessionId
    ) {
        var authRef = aiRequest != null ? aiRequest.authRef() : AnalysisAiAuthRef.localToken(null);
        return new LocalAnalysisRunContinuation(
                true,
                aiRequest != null ? aiRequest.gitLabGroup() : null,
                authRef != null ? authRef.mode() : null,
                authRef != null ? authRef.principalId() : null
        ).withLatestCopilotSession(copilotSessionId);
    }
}
