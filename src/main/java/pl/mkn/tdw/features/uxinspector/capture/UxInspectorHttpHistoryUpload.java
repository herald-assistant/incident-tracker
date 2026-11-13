package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UxInspectorHttpHistoryUpload(
        @NotNull @Pattern(regexp = "AVAILABLE") String status,
        @NotNull @Size(max = 50) @Valid List<@NotNull Entry> entries,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Instant capturedAt,
        @PositiveOrZero int redactions
) {
    public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    public record Unavailable(@NotNull @Pattern(regexp = "UNAVAILABLE") String status, @NotNull UnavailableReason reason) {}
    public enum UnavailableReason { GETTER_MISSING, GETTER_FAILED, TIMEOUT, INVALID_HISTORY, SERIALIZATION_FAILED }
    public enum State {
        @JsonProperty("pending") PENDING, @JsonProperty("success") SUCCESS,
        @JsonProperty("error") ERROR, @JsonProperty("cancelled") CANCELLED
    }
    public enum Format {
        @JsonProperty("json") JSON, @JsonProperty("text") TEXT, @JsonProperty("unavailable") UNAVAILABLE
    }
    public enum BodyReason { SOURCE_OMITTED, BODY_SERIALIZATION_FAILED }

    public record Entry(@Min(1) @Max(MAX_SAFE_INTEGER) long id,
                        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
                        @DecimalMin("0") BigDecimal durationMs, @NotNull State state,
                        @NotNull @Valid Request request, @Valid Response response) {
        @AssertTrue @JsonIgnore
        public boolean isDurationFinite() { return durationMs == null || Double.isFinite(durationMs.doubleValue()); }
    }
    public record Request(@NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,31}") String method,
                          @NotBlank String url, @NotNull Map<String, @NotNull List<@NotNull String>> headers,
                          @NotNull @Valid Body body) {}
    public record Response(@Min(0) @Max(599) int status,
                           @NotNull Map<String, @NotNull List<@NotNull String>> headers, @NotNull @Valid Body body) {}
    public record Body(@NotNull Format format, @NotNull JsonNode value, BodyReason reason) {
        @AssertTrue @JsonIgnore
        public boolean isConsistent() {
            if (format == null || value == null) return false;
            return switch (format) {
                case JSON -> reason == null;
                case TEXT -> value.isTextual() && reason == null;
                case UNAVAILABLE -> value.isNull() && reason != null;
            };
        }
    }
}
