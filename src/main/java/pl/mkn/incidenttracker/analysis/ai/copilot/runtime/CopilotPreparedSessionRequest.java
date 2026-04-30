package pl.mkn.incidenttracker.analysis.ai.copilot.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CopilotPreparedSessionRequest(
        String runReference,
        String prompt,
        CopilotSessionConfigRequest sessionConfigRequest,
        Map<String, String> artifactContents
) {

    public CopilotPreparedSessionRequest {
        sessionConfigRequest = Objects.requireNonNull(sessionConfigRequest, "sessionConfigRequest must not be null");
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
    }
}
