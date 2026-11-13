package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.stream.StreamSupport;

/** Optional blocks remain trees until independently decoded and validated. */
public record UxInspectorCaptureUpload(@NotNull ObjectNode capture, JsonNode formFields, JsonNode store, JsonNode httpHistory) {
    public record FormFields(@NotNull @Pattern(regexp = "AVAILABLE") String status,
                             @NotNull @Valid List<@NotNull FormField> fields) {}

    public record FormField(@NotNull String tag, @NotNull String type, @NotNull String name,
                            @NotNull String id, @NotNull String testId, @NotNull String label,
                            Boolean disabled, @NotNull JsonNode value, @NotNull String display,
                            @NotNull @Pattern(regexp = "dom|display-text") String source,
                            Boolean checked, Boolean indeterminate, Boolean invalid,
                            @NotNull List<@NotNull String> errors, @NotNull List<@NotNull String> descriptions,
                            Boolean nativeInvalid, @NotNull String nativeValidationMessage) {
        @AssertTrue @JsonIgnore
        public boolean isValueValid() {
            return value != null && (value.isTextual() || value.isArray()
                    && StreamSupport.stream(value.spliterator(), false).allMatch(JsonNode::isTextual));
        }
    }

    public record Store(@NotNull @Pattern(regexp = "AVAILABLE") String status, @NotNull JsonNode state) {
        @AssertTrue @JsonIgnore
        public boolean isStateContainer() { return state != null && state.isContainerNode(); }
    }
}
