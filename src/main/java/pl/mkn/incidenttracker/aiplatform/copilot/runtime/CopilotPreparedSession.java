package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public record CopilotPreparedSession(
        String runReference,
        CopilotClientOptions clientOptions,
        SessionConfig sessionConfig,
        MessageOptions messageOptions,
        String prompt,
        Map<String, String> artifactContents,
        Consumer<AnalysisEvidenceSection> evidenceSink
) implements AutoCloseable {

    private static final Consumer<AnalysisEvidenceSection> NO_OP_EVIDENCE_SINK = section -> {
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
                clientOptions,
                sessionConfig,
                messageOptions,
                prompt,
                artifactContents,
                NO_OP_EVIDENCE_SINK
        );
    }

    public CopilotPreparedSession {
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
        evidenceSink = evidenceSink != null ? evidenceSink : NO_OP_EVIDENCE_SINK;
    }

    public String providerName() {
        return "copilot-sdk";
    }

    public CopilotPreparedSession withEvidenceSink(Consumer<AnalysisEvidenceSection> evidenceSink) {
        return new CopilotPreparedSession(
                runReference,
                clientOptions,
                sessionConfig,
                messageOptions,
                prompt,
                artifactContents,
                evidenceSink
        );
    }

    @Override
    public void close() {
        // Artifact-only prepared sessions do not require runtime cleanup.
    }
}
