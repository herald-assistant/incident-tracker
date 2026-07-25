package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationSmokeCleanupResponse(
        String strategy,
        String method,
        String path,
        String requestBody,
        String manualSql,
        List<String> hints
) {

    public ChangeVerificationSmokeCleanupResponse {
        hints = hints != null ? List.copyOf(hints) : List.of();
    }
}
