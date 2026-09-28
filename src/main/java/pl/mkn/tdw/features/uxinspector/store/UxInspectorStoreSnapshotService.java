package pl.mkn.tdw.features.uxinspector.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStoreSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UxInspectorStoreSnapshotService {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PENDING = 8;
    private static final long PENDING_SECONDS = 900;
    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i).*(password|passwd|token|secret|authorization|bearer|cookie|session|csrf|jwt|api.?key|one[-_ ]?time|otp|cvv|cvc).*");
    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore runStore;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public synchronized UploadReceipt stage(InputStream input, AnalysisAiAuthRef auth) throws IOException {
        pending.entrySet().removeIf(entry -> entry.getValue().createdAt().plusSeconds(PENDING_SECONDS).isBefore(Instant.now()));
        if (pending.size() >= MAX_PENDING) throw new IllegalArgumentException("Too many pending store snapshots.");
        var bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Store snapshot exceeds the size limit.");
        JsonNode node;
        try { node = objectMapper.readTree(bytes); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid store snapshot JSON.");
        }
        if (node == null || !node.isObject() || node.size() != 3
                || !node.hasNonNull("captureId") || !node.hasNonNull("origin") || !node.has("state")) {
            throw new IllegalArgumentException("Invalid store snapshot envelope.");
        }
        var captureId = node.path("captureId").asText("");
        var origin = node.path("origin").asText("");
        if (!captureId.matches("[A-Za-z0-9_-]{8,96}") || !origin.matches("https?://[^/]{1,240}")) {
            throw new IllegalArgumentException("Invalid store snapshot scope.");
        }
        var state = node.get("state");
        if (state == null || (!state.isObject() && !state.isArray())) {
            throw new IllegalArgumentException("Store snapshot must be a JSON object or array.");
        }
        var safeState = state.deepCopy();
        var redactions = redact(safeState);
        var safeBytes = objectMapper.writeValueAsBytes(safeState).length;
        if (safeBytes > MAX_BYTES) throw new IllegalArgumentException("Redacted store snapshot exceeds the size limit.");
        var snapshot = new Snapshot(captureId, origin, safeState, redactions);
        var ref = UUID.randomUUID().toString();
        pending.put(ref, new Pending(snapshot, auth != null ? auth.mode() : null,
                auth != null ? auth.principalId() : null, Instant.now()));
        return new UploadReceipt(ref, safeBytes, redactions);
    }

    public synchronized LocalAnalysisRunStoreSnapshot claim(String ref, UxInspectorCapture capture, AnalysisAiAuthRef auth) {
        if (ref == null || ref.isBlank()) return null;
        var candidate = pending.remove(ref);
        if (candidate == null || candidate.createdAt().plusSeconds(PENDING_SECONDS).isBefore(Instant.now())
                || capture == null || !candidate.snapshot().captureId().equals(capture.captureId())
                || !candidate.snapshot().origin().equals(capture.page().origin())
                || !java.util.Objects.equals(candidate.authMode(), auth != null ? auth.mode() : null)
                || !java.util.Objects.equals(candidate.principalId(), auth != null ? auth.principalId() : null)) {
            return null;
        }
        return new LocalAnalysisRunStoreSnapshot(candidate.snapshot().state(), capture.capturedAt(),
                candidate.snapshot().origin(), candidate.snapshot().redactions());
    }

    public java.util.Optional<LocalAnalysisRunStoreSnapshot> find(String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9._-]+")) return java.util.Optional.empty();
        try {
            return runStore.findById(runId).map(record -> record.storeSnapshot());
        } catch (RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    private int redact(JsonNode node) {
        var count = 0;
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (SENSITIVE_KEY.matcher(entry.getKey()).matches()) {
                    object.put(entry.getKey(), "[REDACTED]");
                    count++;
                } else count += redact(entry.getValue());
            }
        } else if (node instanceof ArrayNode array) {
            for (var child : array) count += redact(child);
        }
        return count;
    }

    private record Snapshot(String captureId, String origin, JsonNode state, int redactions) {}
    private record Pending(Snapshot snapshot, String authMode, String principalId, Instant createdAt) {}
    public record UploadReceipt(String storeSnapshotRef, int bytes, int redactions) {}
}
