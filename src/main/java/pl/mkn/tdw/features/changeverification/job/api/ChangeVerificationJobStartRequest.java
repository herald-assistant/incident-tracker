package pl.mkn.tdw.features.changeverification.job.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

public record ChangeVerificationJobStartRequest(
        @Size(max = 80, message = "issueKey must not exceed 80 characters")
        String issueKey,
        @Size(max = 1000, message = "issueUrl must not exceed 1000 characters")
        String issueUrl,
        Boolean checkStoryCompliance,
        Boolean checkInstructionCompliance,
        @Size(max = 4000, message = "userInstructions must not exceed 4000 characters")
        String userInstructions,
        @Size(max = 80, message = "model must not exceed 80 characters")
        String model,
        @Size(max = 40, message = "reasoningEffort must not exceed 40 characters")
        String reasoningEffort
) {

    public ChangeVerificationJobStartRequest {
        issueKey = normalize(issueKey);
        issueUrl = normalize(issueUrl);
        checkStoryCompliance = checkStoryCompliance != null ? checkStoryCompliance : true;
        checkInstructionCompliance = checkInstructionCompliance != null ? checkInstructionCompliance : true;
        userInstructions = normalize(userInstructions);
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    @AssertTrue(message = "issueKey or issueUrl must be provided")
    public boolean isIssueSourcePresent() {
        return StringUtils.hasText(issueKey) || StringUtils.hasText(issueUrl);
    }

    @AssertTrue(message = "at least one compliance scope must be selected")
    public boolean isComplianceScopeSelected() {
        return checkStoryCompliance || checkInstructionCompliance;
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
