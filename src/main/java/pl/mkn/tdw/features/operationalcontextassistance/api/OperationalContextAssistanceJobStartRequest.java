package pl.mkn.tdw.features.operationalcontextassistance.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceRepositoryFacts;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

public record OperationalContextAssistanceJobStartRequest(
        @NotNull OperationalContextAssistanceMode mode,
        @NotBlank @Size(max = 4000) String description,
        @Valid Target target,
        @Valid GitLabSource gitLabSource,
        @Valid OperationalContextAssistanceRepositoryFacts repositoryFacts,
        @Size(max = 80) String model,
        @Size(max = 40) String reasoningEffort
) {

    @AssertTrue(message = "target must be absent for CREATE_AREA and present for other modes")
    public boolean isTargetConsistent() {
        return mode == null || (mode == OperationalContextAssistanceMode.CREATE_AREA) == (target == null);
    }

    @AssertTrue(message = "repositoryFacts requires CREATE_AREA with gitLabSource")
    public boolean isRepositoryFactsConsistent() {
        return repositoryFacts == null
                || (mode == OperationalContextAssistanceMode.CREATE_AREA && gitLabSource != null);
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("Unknown assistance request field: " + field);
    }

    public record Target(
            @NotNull Kind kind,
            @NotBlank @Size(max = 80) String entityType,
            @NotBlank @Size(max = 120) String entityId,
            @Size(max = 160) String id
    ) {
        public enum Kind { ENTITY, VALIDATION_FINDING, OPEN_QUESTION }

        @AssertTrue(message = "finding id is required for VALIDATION_FINDING or OPEN_QUESTION")
        public boolean isFindingIdConsistent() {
            return kind == null || (kind == Kind.ENTITY ? id == null : id != null && !id.isBlank());
        }

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("Unknown assistance target field: " + field);
        }
    }

    public record GitLabSource(
            @Size(max = 512)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._/-]*") String project,
            @Size(max = 2048) String projectUrl,
            @NotBlank @Size(max = 255)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._/-]*") String ref
    ) {
        @AssertTrue(message = "exactly one of project or projectUrl is required")
        public boolean isSingleProjectSelection() {
            return (project == null) != (projectUrl == null);
        }

        @AssertTrue(message = "project, projectUrl and ref must be valid GitLab source inputs")
        public boolean isSafeRelativeSelection() {
            return safe(project) && safe(ref) && (projectUrl == null || !projectUrl.isBlank());
        }

        private boolean safe(String value) {
            return value == null || (!value.endsWith("/") && !value.contains("//")
                    && !value.contains("..") && !value.contains("/./")
                    && !value.endsWith("/.") && !value.contains("@{"));
        }

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("Unknown assistance GitLab source field: " + field);
        }
    }
}
