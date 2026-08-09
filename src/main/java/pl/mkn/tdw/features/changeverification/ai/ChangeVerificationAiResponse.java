package pl.mkn.tdw.features.changeverification.ai;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVerificationCheckResponse;

import java.util.List;
import java.util.Locale;

public record ChangeVerificationAiResponse(
        String status,
        List<ChangeVerificationVerificationCheckResponse> verificationChecks,
        List<ChangeVerificationFindingResponse> findings,
        List<String> suggestedActions,
        List<String> visibilityLimits,
        String confidence
) {

    private static final String ORIGIN_DEFINED = "DEFINED";
    private static final String ORIGIN_INFERRED_CRITICAL = "INFERRED_CRITICAL";
    private static final int MAX_INFERRED_CRITICAL_CHECKS = 5;

    public ChangeVerificationAiResponse {
        verificationChecks = limitInferredCriticalChecks(verificationChecks);
        status = definedComplianceStatus(verificationChecks);
        findings = findings != null ? List.copyOf(findings) : List.of();
        suggestedActions = suggestedActions != null ? List.copyOf(suggestedActions) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    private static List<ChangeVerificationVerificationCheckResponse> limitInferredCriticalChecks(
            List<ChangeVerificationVerificationCheckResponse> checks
    ) {
        if (checks == null || checks.isEmpty()) {
            return List.of();
        }

        var inferredCount = 0;
        var accepted = new java.util.ArrayList<ChangeVerificationVerificationCheckResponse>();
        for (var check : checks) {
            if (check == null) {
                continue;
            }
            if (ORIGIN_INFERRED_CRITICAL.equals(normalize(check.origin()))) {
                if (inferredCount >= MAX_INFERRED_CRITICAL_CHECKS) {
                    continue;
                }
                inferredCount++;
            }
            accepted.add(check);
        }
        return List.copyOf(accepted);
    }

    private static String definedComplianceStatus(List<ChangeVerificationVerificationCheckResponse> checks) {
        var definedChecks = checks.stream()
                .filter(check -> ORIGIN_DEFINED.equals(normalize(check.origin())))
                .toList();
        if (definedChecks.isEmpty()) {
            return "INCONCLUSIVE";
        }

        var statuses = definedChecks.stream()
                .map(ChangeVerificationVerificationCheckResponse::verificationStatus)
                .map(ChangeVerificationAiResponse::normalize)
                .toList();
        if (statuses.stream().anyMatch("FAILED"::equals)) {
            return "FAILED";
        }
        if (statuses.stream().anyMatch("WARNING"::equals)) {
            return "PASSED_WITH_WARNINGS";
        }
        if (statuses.stream().anyMatch("NOT_VERIFIED"::equals)) {
            return statuses.stream().anyMatch("PASSED"::equals)
                    ? "PASSED_WITH_WARNINGS"
                    : "INCONCLUSIVE";
        }
        return statuses.stream().allMatch("PASSED"::equals)
                ? "PASSED"
                : "INCONCLUSIVE";
    }

    private static String normalize(String value) {
        return value != null ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
