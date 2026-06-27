package pl.mkn.tdw.shared.ai;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AnalysisAiActivityEvent(
        String eventId,
        String parentEventId,
        String type,
        String category,
        String status,
        String title,
        String summary,
        String turnId,
        String interactionId,
        String toolCallId,
        String toolName,
        Instant timestamp,
        Map<String, Object> details
) {

    public AnalysisAiActivityEvent {
        timestamp = timestamp != null ? timestamp : Instant.now();
        details = details != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(details))
                : Map.of();
    }
}
