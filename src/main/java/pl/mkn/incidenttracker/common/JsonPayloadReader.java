package pl.mkn.incidenttracker.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class JsonPayloadReader {

    private final ObjectMapper objectMapper;

    public JsonNode readJsonNode(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return null;
        }

        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse payload as JSON. reason={}", exception.getMessage());
            return null;
        }
    }

    public String prettyPayload(JsonNode payload, String rawPayload, String fallback) {
        if (payload == null || payload.isNull()) {
            return StringUtils.hasText(rawPayload) ? rawPayload.trim() : fallback;
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to pretty print payload. reason={}", exception.getMessage());
            return StringUtils.hasText(rawPayload) ? rawPayload.trim() : fallback;
        }
    }

    public String readTopLevelText(JsonNode payload, String fieldName) {
        if (payload == null || !payload.isObject()) {
            return null;
        }

        var field = payload.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }

        if (field.isValueNode()) {
            return field.asText();
        }

        return prettyPayload(field, field.toString(), null);
    }
}
