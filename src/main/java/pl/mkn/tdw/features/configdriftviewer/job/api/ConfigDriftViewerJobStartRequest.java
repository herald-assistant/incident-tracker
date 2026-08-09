package pl.mkn.tdw.features.configdriftviewer.job.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.List;

public record ConfigDriftViewerJobStartRequest(
        @NotNull(message = "mode must be provided")
        ConfigDriftViewerMode mode,
        @NotBlank(message = "repositoryId must be provided")
        @Size(max = 100, message = "repositoryId must not exceed 100 characters")
        @Pattern(
                regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
                message = "repositoryId contains unsupported characters"
        )
        String repositoryId,
        @NotEmpty(message = "systemIds must contain at least one system")
        @Size(max = 50, message = "systemIds must not contain more than 50 systems")
        List<
                @NotBlank(message = "systemIds must not contain blank values")
                @Size(max = 100, message = "systemId must not exceed 100 characters")
                @Pattern(
                        regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
                        message = "systemId contains unsupported characters"
                ) String> systemIds,
        @NotBlank(message = "sourceBranch must be provided")
        @Pattern(
                regexp = "(?:dev|test|uat|zt)\\d*",
                message = "sourceBranch must use dev, test, uat or zt with an optional numeric suffix"
        )
        String sourceBranch,
        @NotBlank(message = "targetBranch must be provided")
        @Pattern(
                regexp = "(?:dev|test|uat|zt)\\d*",
                message = "targetBranch must use dev, test, uat or zt with an optional numeric suffix"
        )
        String targetBranch,
        @Size(max = 255, message = "codeRef must not exceed 255 characters")
        String codeRef,
        @Size(max = 80, message = "model must not exceed 80 characters")
        String model,
        @Size(max = 40, message = "reasoningEffort must not exceed 40 characters")
        String reasoningEffort
) {

    public ConfigDriftViewerJobStartRequest {
        repositoryId = normalize(repositoryId);
        systemIds = systemIds != null
                ? systemIds.stream().map(ConfigDriftViewerJobStartRequest::normalize).toList()
                : null;
        sourceBranch = normalize(sourceBranch);
        targetBranch = normalize(targetBranch);
        codeRef = normalize(codeRef);
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    @AssertTrue(message = "systemIds must contain unique values")
    public boolean isSystemSelectionUnique() {
        return systemIds == null || systemIds.stream().distinct().count() == systemIds.size();
    }

    @AssertTrue(message = "sourceBranch and targetBranch must be different")
    public boolean isBranchPairDistinct() {
        return sourceBranch == null
                || targetBranch == null
                || !sourceBranch.equals(targetBranch);
    }

    @AssertTrue(message = "codeRef contains an unsupported Git reference")
    public boolean isCodeReferenceValid() {
        if (codeRef == null) {
            return true;
        }
        return codeRef.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")
                && !codeRef.contains("..")
                && !codeRef.contains("//")
                && !codeRef.contains("@{")
                && !codeRef.endsWith("/")
                && !codeRef.endsWith(".");
    }

    @AssertTrue(message = "codeRef, model and reasoningEffort are supported only in DEEP mode")
    public boolean isDeepOnlyInputValid() {
        return mode == null
                || mode == ConfigDriftViewerMode.DEEP
                || (codeRef == null && model == null && reasoningEffort == null);
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    public ConfigDriftViewerJobStartRequest forSystem(String systemId) {
        return new ConfigDriftViewerJobStartRequest(
                mode,
                repositoryId,
                List.of(systemId),
                sourceBranch,
                targetBranch,
                codeRef,
                model,
                reasoningEffort
        );
    }

    @JsonIgnore
    public String componentSystemId() {
        if (systemIds == null || systemIds.size() != 1) {
            throw new IllegalStateException("A component-scoped request must contain exactly one systemId.");
        }
        return systemIds.get(0);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
