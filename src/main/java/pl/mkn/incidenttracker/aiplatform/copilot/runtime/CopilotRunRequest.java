package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public record CopilotRunRequest(
        String runReference,
        CopilotRunAuth auth,
        String prompt,
        CopilotSessionConfigRequest sessionConfigRequest,
        Map<String, String> artifactContents,
        Consumer<AnalysisEvidenceSection> evidenceSink,
        Consumer<AnalysisAiActivityEvent> activitySink
) {

    private static final Consumer<AnalysisEvidenceSection> NO_OP_EVIDENCE_SINK = section -> {
    };
    private static final Consumer<AnalysisAiActivityEvent> NO_OP_ACTIVITY_SINK = event -> {
    };

    public CopilotRunRequest {
        auth = auth != null ? auth : CopilotRunAuth.localToken();
        sessionConfigRequest = Objects.requireNonNull(sessionConfigRequest, "sessionConfigRequest must not be null");
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
        evidenceSink = evidenceSink != null ? evidenceSink : NO_OP_EVIDENCE_SINK;
        activitySink = activitySink != null ? activitySink : NO_OP_ACTIVITY_SINK;
    }

    public CopilotRunRequest(
            String runReference,
            CopilotRunAuth auth,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            Map<String, String> artifactContents,
            Consumer<AnalysisEvidenceSection> evidenceSink
    ) {
        this(
                runReference,
                auth,
                prompt,
                sessionConfigRequest,
                artifactContents,
                evidenceSink,
                NO_OP_ACTIVITY_SINK
        );
    }

    public CopilotRunRequest(
            String runReference,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            Map<String, String> artifactContents,
            Consumer<AnalysisEvidenceSection> evidenceSink
    ) {
        this(runReference, CopilotRunAuth.localToken(), prompt, sessionConfigRequest, artifactContents, evidenceSink);
    }

    public CopilotRunRequest(
            String runReference,
            String prompt,
            CopilotSessionConfigRequest sessionConfigRequest,
            Map<String, String> artifactContents,
            Consumer<AnalysisEvidenceSection> evidenceSink,
            Consumer<AnalysisAiActivityEvent> activitySink
    ) {
        this(
                runReference,
                CopilotRunAuth.localToken(),
                prompt,
                sessionConfigRequest,
                artifactContents,
                evidenceSink,
                activitySink
        );
    }

}
