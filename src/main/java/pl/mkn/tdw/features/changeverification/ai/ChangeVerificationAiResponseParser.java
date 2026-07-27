package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChangeVerificationAiResponseParser {

    private final ObjectMapper objectMapper;

    public ChangeVerificationAiResponse parse(String content) {
        var json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            return fallback("AI response did not contain JSON compliance result.");
        }

        try {
            var response = objectMapper.readValue(json, ChangeVerificationAiResponse.class);
            if (!StringUtils.hasText(response.status())) {
                return fallback("AI response did not contain compliance status.");
            }
            return response;
        } catch (JsonProcessingException exception) {
            return fallback("AI response JSON could not be parsed: " + exception.getMessage());
        }
    }

    public ChangeVerificationAiResponse fallback(String limitation) {
        return new ChangeVerificationAiResponse(
                "INCONCLUSIVE",
                List.of(),
                List.of(),
                List.of("Run compliance verification again or inspect collected evidence manually."),
                List.of(limitation),
                "low"
        );
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        var trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        var fenceStart = trimmed.indexOf("```json");
        if (fenceStart >= 0) {
            var jsonStart = trimmed.indexOf('\n', fenceStart);
            var fenceEnd = trimmed.indexOf("```", jsonStart + 1);
            if (jsonStart >= 0 && fenceEnd > jsonStart) {
                return trimmed.substring(jsonStart + 1, fenceEnd).trim();
            }
        }

        var objectStart = trimmed.indexOf('{');
        var objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1).trim();
        }

        return null;
    }
}
