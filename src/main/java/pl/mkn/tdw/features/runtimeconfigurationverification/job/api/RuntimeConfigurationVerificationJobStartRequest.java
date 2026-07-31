package pl.mkn.tdw.features.runtimeconfigurationverification.job.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

public record RuntimeConfigurationVerificationJobStartRequest(
        @NotNull(message = "mode must be provided")
        RuntimeConfigurationVerificationMode mode,
        @NotBlank(message = "repositoryId must be provided")
        @Size(max = 100, message = "repositoryId must not exceed 100 characters")
        @Pattern(
                regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
                message = "repositoryId contains unsupported characters"
        )
        String repositoryId,
        @NotBlank(message = "systemId must be provided")
        @Size(max = 100, message = "systemId must not exceed 100 characters")
        @Pattern(
                regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
                message = "systemId contains unsupported characters"
        )
        String systemId,
        @NotBlank(message = "sourceBranch must be provided")
        @Pattern(
                regexp = "(?:dev\\d|zt00\\d)",
                message = "sourceBranch must match devX or zt00X"
        )
        String sourceBranch,
        @NotBlank(message = "targetBranch must be provided")
        @Pattern(
                regexp = "(?:dev\\d|zt00\\d)",
                message = "targetBranch must match devX or zt00X"
        )
        String targetBranch,
        @Size(max = 255, message = "codeRef must not exceed 255 characters")
        String codeRef,
        @Size(max = 80, message = "model must not exceed 80 characters")
        String model,
        @Size(max = 40, message = "reasoningEffort must not exceed 40 characters")
        String reasoningEffort
) {

    public RuntimeConfigurationVerificationJobStartRequest {
        repositoryId = normalize(repositoryId);
        systemId = normalize(systemId);
        sourceBranch = normalize(sourceBranch);
        targetBranch = normalize(targetBranch);
        codeRef = normalize(codeRef);
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
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
                || mode == RuntimeConfigurationVerificationMode.DEEP
                || (codeRef == null && model == null && reasoningEffort == null);
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
