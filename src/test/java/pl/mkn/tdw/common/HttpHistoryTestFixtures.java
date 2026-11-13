package pl.mkn.tdw.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunHttpHistorySnapshot;
import java.time.Instant;

public final class HttpHistoryTestFixtures {
    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    public static final Instant TIME = Instant.parse("2026-11-11T10:00:00Z");
    private HttpHistoryTestFixtures() {}

    public static ObjectNode body(JsonNode value) {
        return MAPPER.createObjectNode().put("format", value.isTextual() ? "text" : "json")
                .putNull("reason").set("value", value);
    }

    public static ObjectNode entry(long id, JsonNode value) {
        var entry = MAPPER.createObjectNode().put("id", id).put("startedAt", TIME.toString())
                .put("durationMs", 0).put("state", "success");
        var request = entry.putObject("request").put("method", "GET")
                .put("url", "https://crm.example.com/api/contacts?view=active&sort=name");
        request.putObject("headers").putArray("Accept").add("application/json").add("text/plain");
        request.set("body", body(MAPPER.nullNode()));
        var response = entry.putObject("response").put("status", 200);
        response.putObject("headers");
        response.set("body", body(value));
        return entry;
    }

    public static ObjectNode block(ArrayNode entries) {
        return MAPPER.createObjectNode().put("status", "AVAILABLE").put("capturedAt", TIME.toString())
                .put("redactions", 0).set("entries", entries);
    }

    public static ObjectNode file(byte[] bytes) {
        var file = MAPPER.createObjectNode().put("$tdwType", "File").put("name", "crm-contacts.bin")
                .put("type", "application/octet-stream").put("size", bytes.length).put("lastModified", 123456789);
        file.putObject("content").put("encoding", "base64").put("data", java.util.Base64.getEncoder().encodeToString(bytes))
                .put("byteLength", bytes.length).put("redacted", false).putNull("reason");
        return file;
    }

    public static LocalAnalysisRunHttpHistorySnapshot snapshot(ArrayNode entries) {
        return new LocalAnalysisRunHttpHistorySnapshot("AVAILABLE", entries, TIME, "https://crm.example.com", 0, null);
    }
}
