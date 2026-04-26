package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CopilotToolDescriptionDecorator {

    private static final String GUIDANCE_HEADER = "Copilot guidance:";

    private final CopilotToolGuidanceCatalog guidanceCatalog;

    public String decorate(String toolName, String description) {
        var baseDescription = StringUtils.hasText(description) ? description.trim() : "";
        var guidance = guidanceCatalog.guidanceFor(toolName);

        if (guidance.isEmpty() || baseDescription.contains(GUIDANCE_HEADER)) {
            return baseDescription;
        }

        var builder = new StringBuilder(baseDescription);
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(GUIDANCE_HEADER);

        for (var guidanceLine : guidance) {
            builder.append("\n- ").append(guidanceLine);
        }

        return builder.toString();
    }
}
