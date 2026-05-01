package pl.mkn.incidenttracker.integrations.gitlab.source;

import java.util.List;

public record GitLabSourceResolveResponse(
        String matchedPath,
        Integer score,
        List<String> candidates,
        String content,
        String message
) {
}
