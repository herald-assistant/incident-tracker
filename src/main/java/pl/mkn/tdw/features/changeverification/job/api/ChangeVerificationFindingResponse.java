package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationFindingResponse(
        String id,
        ChangeVerificationFindingSeverity severity,
        String source,
        String summary,
        String details,
        List<String> references,
        String suggestedAction
) {

    public ChangeVerificationFindingResponse {
        references = references != null ? List.copyOf(references) : List.of();
    }
}
