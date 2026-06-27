package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.ResumeSessionConfig;
import com.github.copilot.rpc.SessionConfig;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public record CopilotPreparedSession(
        String runReference,
        CopilotSessionTarget sessionTarget,
        CopilotClientOptions clientOptions,
        SessionConfig sessionConfig,
        ResumeSessionConfig resumeSessionConfig,
        MessageOptions messageOptions,
        String prompt,
        Map<String, String> artifactContents,
        Consumer<AnalysisEvidenceSection> evidenceSink,
        Consumer<AnalysisAiActivityEvent> activitySink
) implements AutoCloseable {

    private static final Consumer<AnalysisEvidenceSection> NO_OP_EVIDENCE_SINK = section -> {
    };
    private static final Consumer<AnalysisAiActivityEvent> NO_OP_ACTIVITY_SINK = event -> {
    };

    public CopilotPreparedSession(
            String runReference,
            CopilotClientOptions clientOptions,
            SessionConfig sessionConfig,
            MessageOptions messageOptions,
            String prompt,
            Map<String, String> artifactContents
    ) {
        this(
                runReference,
                CopilotSessionTarget.newSession(),
                clientOptions,
                sessionConfig,
                null,
                messageOptions,
                prompt,
                artifactContents,
                NO_OP_EVIDENCE_SINK,
                NO_OP_ACTIVITY_SINK
        );
    }

    public CopilotPreparedSession(
            String runReference,
            CopilotClientOptions clientOptions,
            SessionConfig sessionConfig,
            MessageOptions messageOptions,
            String prompt,
            Map<String, String> artifactContents,
            Consumer<AnalysisEvidenceSection> evidenceSink,
            Consumer<AnalysisAiActivityEvent> activitySink
    ) {
        this(
                runReference,
                CopilotSessionTarget.newSession(),
                clientOptions,
                sessionConfig,
                null,
                messageOptions,
                prompt,
                artifactContents,
                evidenceSink,
                activitySink
        );
    }

    public CopilotPreparedSession {
        sessionTarget = sessionTarget != null ? sessionTarget : CopilotSessionTarget.newSession();
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
        evidenceSink = evidenceSink != null ? evidenceSink : NO_OP_EVIDENCE_SINK;
        activitySink = activitySink != null ? activitySink : NO_OP_ACTIVITY_SINK;
    }

    public String providerName() {
        return "copilot-sdk";
    }

    public CopilotPreparedSession withEvidenceSink(Consumer<AnalysisEvidenceSection> evidenceSink) {
        return new CopilotPreparedSession(
                runReference,
                sessionTarget,
                clientOptions,
                sessionConfig,
                resumeSessionConfig,
                messageOptions,
                prompt,
                artifactContents,
                evidenceSink,
                activitySink
        );
    }

    public CopilotPreparedSession withActivitySink(Consumer<AnalysisAiActivityEvent> activitySink) {
        return new CopilotPreparedSession(
                runReference,
                sessionTarget,
                clientOptions,
                sessionConfig,
                resumeSessionConfig,
                messageOptions,
                prompt,
                artifactContents,
                evidenceSink,
                activitySink
        );
    }

    @Override
    public void close() {
        // Artifact-only prepared sessions do not require runtime cleanup.
    }
}
