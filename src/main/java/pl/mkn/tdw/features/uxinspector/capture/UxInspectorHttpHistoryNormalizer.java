package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunHttpHistorySnapshot;

import java.util.HashSet;

import static pl.mkn.tdw.features.uxinspector.capture.UxInspectorHttpHistoryUpload.*;

@Component
@RequiredArgsConstructor
public class UxInspectorHttpHistoryNormalizer {
    private final UxInspectorCaptureJson json;
    private final UxInspectorHttpHistorySanitizer sanitizer;

    public LocalAnalysisRunHttpHistorySnapshot normalize(JsonNode block, String origin) {
        try {
            if (block == null) return unavailable(origin, "INVALID_HISTORY");
            if (!"AVAILABLE".equals(block.path("status").asText())) {
                return unavailable(origin, json.read(block, Unavailable.class).reason().name());
            }
            var history = json.read(block, UxInspectorHttpHistoryUpload.class);
            var ids = new HashSet<Long>();
            var count = new int[]{0};
            var entries = history.entries().stream().map(entry -> {
                if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate HTTP entry ID");
                var request = entry.request();
                var safeRequest = new Request(request.method(), sanitizer.url(request.url(), count),
                        sanitizer.headers(request.headers(), count), body(request.body(), count));
                var response = entry.response();
                var safeResponse = response == null ? null : new Response(response.status(),
                        sanitizer.headers(response.headers(), count), body(response.body(), count));
                return new Entry(entry.id(), entry.startedAt(), entry.durationMs(), entry.state(), safeRequest, safeResponse);
            }).toList();
            return new LocalAnalysisRunHttpHistorySnapshot("AVAILABLE", (ArrayNode) json.tree(entries),
                    history.capturedAt(), origin, Math.addExact(history.redactions(), count[0]), null);
        } catch (RuntimeException exception) {
            return unavailable(origin, "INVALID_HISTORY");
        }
    }

    private Body body(Body body, int[] count) {
        return body.format() == Format.UNAVAILABLE ? body
                : new Body(body.format(), sanitizer.redact(body.value().deepCopy(), count), null);
    }

    public static LocalAnalysisRunHttpHistorySnapshot unavailable(String origin, String reason) {
        return new LocalAnalysisRunHttpHistorySnapshot("UNAVAILABLE", JsonNodeFactory.instance.arrayNode(), null, origin, 0, reason);
    }
}
