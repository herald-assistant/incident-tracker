package pl.mkn.incidenttracker.analysis.ai.copilot.runtime;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CopilotPreparedSession(
        String correlationId,
        CopilotClientOptions clientOptions,
        SessionConfig sessionConfig,
        MessageOptions messageOptions,
        String prompt,
        Map<String, String> artifactContents
) implements AutoCloseable {

    public CopilotPreparedSession {
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
    }

    public String providerName() {
        return "copilot-sdk";
    }

    @Override
    public void close() {
        // Artifact-only prepared sessions do not require runtime cleanup.
    }
}
