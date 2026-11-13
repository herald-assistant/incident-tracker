package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

import static pl.mkn.tdw.features.uxinspector.capture.UxInspectorHttpHistoryUpload.MAX_SAFE_INTEGER;

public final class UxInspectorHttpNativeBody {
    private UxInspectorHttpNativeBody() {}

    public record FormData(@JsonProperty("$tdwType") @NotNull @Pattern(regexp = "FormData") String kind,
                           @NotNull @Valid List<@NotNull Field> entries) {}
    public record Field(@NotNull String name, @NotNull JsonNode value) {
        @AssertTrue @JsonIgnore
        public boolean isValueValid() {
            return value != null && (value.isTextual() || value.isObject() && "File".equals(value.path("$tdwType").asText()));
        }
    }
    public interface Binary {
        String type();
        long size();
        Content content();
    }
    public record File(@JsonProperty("$tdwType") @NotNull @Pattern(regexp = "File") String kind,
                       @NotNull String type, @Min(0) @Max(MAX_SAFE_INTEGER) long size,
                       @NotNull String name, @Min(-MAX_SAFE_INTEGER) @Max(MAX_SAFE_INTEGER) long lastModified,
                       @NotNull @Valid Content content) implements Binary {}
    public record Blob(@JsonProperty("$tdwType") @NotNull @Pattern(regexp = "Blob") String kind,
                       @NotNull String type, @Min(0) @Max(MAX_SAFE_INTEGER) long size,
                       @NotNull @Valid Content content) implements Binary {}
    public enum Encoding { @JsonProperty("utf-8") UTF8, @JsonProperty("base64") BASE64 }
    public enum ReadFailure { BINARY_READ_FAILED }
    public record Content(Encoding encoding, String data, @Min(0) @Max(MAX_SAFE_INTEGER) Long byteLength,
                          boolean redacted, ReadFailure reason) {
        @AssertTrue @JsonIgnore
        public boolean isConsistent() {
            return reason == null ? encoding != null && data != null && byteLength != null
                    : encoding == null && data == null && byteLength == null && !redacted;
        }
    }
}
