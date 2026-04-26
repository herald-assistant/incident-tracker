package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiPreparedAnalysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CopilotSdkPreparedRequest(
        String correlationId,
        CopilotClientOptions clientOptions,
        SessionConfig sessionConfig,
        MessageOptions messageOptions,
        String prompt,
        Map<String, String> artifactContents,
        AnalysisAiAnalysisRequest request
) implements AnalysisAiPreparedAnalysis {

    public CopilotSdkPreparedRequest {
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
    }

    public CopilotSdkPreparedRequest(
            String correlationId,
            CopilotClientOptions clientOptions,
            SessionConfig sessionConfig,
            MessageOptions messageOptions,
            String prompt,
            Map<String, String> artifactContents
    ) {
        this(correlationId, clientOptions, sessionConfig, messageOptions, prompt, artifactContents, null);
    }

    @Override
    public String providerName() {
        return "copilot-sdk";
    }

    @Override
    public void close() {
        // Artifact-only prepared requests do not require runtime cleanup.
    }
}
