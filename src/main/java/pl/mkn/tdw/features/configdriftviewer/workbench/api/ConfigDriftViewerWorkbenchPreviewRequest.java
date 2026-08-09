package pl.mkn.tdw.features.configdriftviewer.workbench.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;

public record ConfigDriftViewerWorkbenchPreviewRequest(
        @NotNull(message = "mode must be provided")
        ConfigDriftViewerMode mode,
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
        String codeRef
) {

    public ConfigDriftViewerWorkbenchPreviewRequest {
        repositoryId = normalize(repositoryId);
        systemId = normalize(systemId);
        sourceBranch = normalize(sourceBranch);
        targetBranch = normalize(targetBranch);
        codeRef = normalize(codeRef);
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

    @AssertTrue(message = "codeRef is supported only in DEEP mode")
    public boolean isCodeReferenceModeValid() {
        return mode == null
                || mode == ConfigDriftViewerMode.DEEP
                || codeRef == null;
    }

    public ConfigDriftViewerJobStartRequest asPreparationRequest() {
        return new ConfigDriftViewerJobStartRequest(
                mode,
                repositoryId,
                java.util.List.of(systemId),
                sourceBranch,
                targetBranch,
                codeRef,
                null,
                null
        );
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
