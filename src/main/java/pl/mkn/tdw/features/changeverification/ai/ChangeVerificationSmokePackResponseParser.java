package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChangeVerificationSmokePackResponseParser {

    private final ObjectMapper objectMapper;

    public ChangeVerificationSmokePackResponse parse(String content) {
        var json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            return fallback("AI response did not contain JSON smoke pack result.");
        }

        try {
            var response = objectMapper.readValue(json, ChangeVerificationSmokePackResponse.class);
            if (!StringUtils.hasText(response.status())) {
                return fallback("AI response did not contain smoke pack status.");
            }
            return response;
        } catch (JsonProcessingException exception) {
            return fallback("AI response JSON could not be parsed: " + exception.getMessage());
        }
    }

    public ChangeVerificationSmokePackResponse fallback(String limitation) {
        return new ChangeVerificationSmokePackResponse(
                true,
                "INCONCLUSIVE",
                null,
                List.of(),
                List.of(limitation),
                List.of("Review changed endpoints manually and create smoke tests from the discovered MR files."),
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
