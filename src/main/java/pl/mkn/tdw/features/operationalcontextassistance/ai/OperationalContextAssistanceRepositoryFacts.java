package pl.mkn.tdw.features.operationalcontextassistance.ai;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Explicit operator statements about the selected repository, not observations from its source files. */
public record OperationalContextAssistanceRepositoryFacts(
        @NotNull Usage usage,
        @Size(max = 160) String systemName,
        @Size(max = 160) String runtimeServiceName,
        @Size(max = 5) List<@NotBlank @Size(max = 120) String> systemIds
) {
    public enum Usage { UNKNOWN, DEPLOYED_SYSTEM, SHARED_LIBRARY, EXISTING_SYSTEM }

    public OperationalContextAssistanceRepositoryFacts {
        systemIds = systemIds == null ? List.of() : List.copyOf(systemIds);
    }

    @AssertTrue(message = "repository facts must match the selected usage")
    public boolean isUsageConsistent() {
        if (usage == null || !optionalTextValid(systemName) || !optionalTextValid(runtimeServiceName)) {
            return false;
        }
        return switch (usage) {
            case UNKNOWN -> systemName == null && runtimeServiceName == null && systemIds.isEmpty();
            case DEPLOYED_SYSTEM -> systemIds.isEmpty();
            case SHARED_LIBRARY -> systemName == null && runtimeServiceName == null
                    && systemIds.size() <= 5 && systemIds.stream().distinct().count() == systemIds.size();
            case EXISTING_SYSTEM -> systemName == null && runtimeServiceName == null && systemIds.size() == 1;
        };
    }

    private boolean optionalTextValid(String value) {
        return value == null || (!value.isBlank() && value.length() <= 160);
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("Unknown repository facts field: " + field);
    }
}
