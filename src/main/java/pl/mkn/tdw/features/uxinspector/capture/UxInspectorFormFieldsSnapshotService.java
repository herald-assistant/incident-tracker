package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UxInspectorFormFieldsSnapshotService {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PENDING = 8;
    private static final long PENDING_SECONDS = 900;
    private static final Set<String> FIELD_KEYS = Set.of("tag", "type", "name", "id", "testId", "label",
            "disabled", "value", "display", "source", "checked", "indeterminate", "invalid", "errors",
            "descriptions", "nativeInvalid", "nativeValidationMessage");
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(password|passwd|token|secret|authorization|bearer|cookie|session|csrf|jwt|api.?key|one[-_ ]?time|otp|cvv|cvc).*"
    );
    private static final Pattern JWT = Pattern.compile("^[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}$");
    private final ObjectMapper objectMapper;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public synchronized UploadReceipt stage(InputStream input, AnalysisAiAuthRef auth) throws IOException {
        pending.entrySet().removeIf(entry -> entry.getValue().createdAt().plusSeconds(PENDING_SECONDS).isBefore(Instant.now()));
        if (pending.size() >= MAX_PENDING) throw new IllegalArgumentException("Too many pending form field snapshots.");
        var bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Form field snapshot exceeds the size limit.");
        JsonNode envelope;
        try { envelope = objectMapper.readTree(bytes); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid form field snapshot JSON.");
        }
        if (envelope == null || !envelope.isObject() || !keys(envelope).equals(Set.of("captureId", "origin", "fields"))
                || !envelope.path("captureId").isTextual() || !envelope.path("origin").isTextual()
                || !envelope.path("fields").isArray()) {
            throw new IllegalArgumentException("Invalid form field snapshot envelope.");
        }
        var captureId = envelope.path("captureId").asText();
        var origin = envelope.path("origin").asText();
        if (!captureId.matches("[A-Za-z0-9_-]{8,96}") || !origin.matches("https?://[^/]{1,240}")) {
            throw new IllegalArgumentException("Invalid form field snapshot scope.");
        }
        var safeFields = objectMapper.createArrayNode();
        var omittedSensitive = 0;
        for (var field : envelope.path("fields")) {
            validate(field);
            if (sensitive(field)) { omittedSensitive++; continue; }
            safeFields.add(field.deepCopy());
        }
        var safeBytes = objectMapper.writeValueAsBytes(safeFields).length;
        if (safeBytes > MAX_BYTES) throw new IllegalArgumentException("Validated form field snapshot exceeds the size limit.");
        var ref = UUID.randomUUID().toString();
        pending.put(ref, new Pending(new Snapshot(captureId, origin, safeFields),
                auth != null ? auth.mode() : null, auth != null ? auth.principalId() : null, Instant.now()));
        return new UploadReceipt(ref, safeBytes, omittedSensitive);
    }

    public synchronized Snapshot claim(String ref, UxInspectorCapture capture, AnalysisAiAuthRef auth) {
        if (ref == null || ref.isBlank()) return null;
        var candidate = pending.remove(ref);
        if (candidate == null || candidate.createdAt().plusSeconds(PENDING_SECONDS).isBefore(Instant.now())
                || capture == null || capture.captureProfile() != UxInspectorCapture.CaptureProfile.FORM_DIAGNOSTICS
                || !candidate.snapshot().captureId().equals(capture.captureId())
                || !candidate.snapshot().origin().equals(capture.page().origin())
                || !Objects.equals(candidate.authMode(), auth != null ? auth.mode() : null)
                || !Objects.equals(candidate.principalId(), auth != null ? auth.principalId() : null)) return null;
        return candidate.snapshot();
    }

    private void validate(JsonNode field) {
        if (field == null || !field.isObject() || !keys(field).equals(FIELD_KEYS)) throw invalid();
        for (var key : Set.of("tag", "type", "name", "id", "testId", "label", "display", "source",
                "nativeValidationMessage")) {
            if (!field.path(key).isTextual()) throw invalid();
        }
        if (!Set.of("dom", "display-text").contains(field.path("source").asText())) throw invalid();
        var value = field.path("value");
        if (!value.isTextual() && !(value.isArray() && strings(value))) throw invalid();
        for (var key : Set.of("disabled", "checked", "indeterminate", "invalid", "nativeInvalid")) {
            if (!field.path(key).isBoolean() && !field.path(key).isNull()) throw invalid();
        }
        if (!field.path("errors").isArray() || !strings(field.path("errors"))
                || !field.path("descriptions").isArray() || !strings(field.path("descriptions"))) throw invalid();
    }

    private boolean sensitive(JsonNode field) {
        for (var key : Set.of("type", "name", "id", "testId", "label")) {
            if (SENSITIVE.matcher(field.path(key).asText()).matches()) return true;
        }
        if (Set.of("password", "file", "hidden").contains(field.path("type").asText().toLowerCase())) return true;
        if (secretValue(field.path("value")) || secretValue(field.path("display"))) return true;
        for (var key : Set.of("errors", "descriptions")) {
            for (var message : field.path(key)) if (secretValue(message)) return true;
        }
        return secretValue(field.path("nativeValidationMessage"));
    }

    private boolean secretValue(JsonNode value) {
        if (value.isArray()) {
            for (var child : value) if (secretValue(child)) return true;
            return false;
        }
        var text = value.asText("").trim();
        return text.matches("(?i)^Bearer\\s+.*") || JWT.matcher(text).matches();
    }

    private boolean strings(JsonNode values) {
        for (var value : values) if (!value.isTextual()) return false;
        return true;
    }

    private Set<String> keys(JsonNode value) {
        var result = new HashSet<String>();
        value.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid form field snapshot fields."); }

    public record Snapshot(String captureId, String origin, ArrayNode fields) {}
    private record Pending(Snapshot snapshot, String authMode, String principalId, Instant createdAt) {}
    public record UploadReceipt(String formFieldsSnapshotRef, int bytes, int omittedSensitiveFields) {}
}
