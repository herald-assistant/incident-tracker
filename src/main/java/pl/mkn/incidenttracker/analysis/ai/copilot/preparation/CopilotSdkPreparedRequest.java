package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;

public record CopilotSdkPreparedRequest(
        String correlationId,
        CopilotClientOptions clientOptions,
        SessionConfig sessionConfig,
        MessageOptions messageOptions,
        String prompt
) {
}
