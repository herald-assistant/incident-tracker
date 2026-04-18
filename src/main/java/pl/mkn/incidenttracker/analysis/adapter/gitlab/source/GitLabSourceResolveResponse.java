package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import java.util.List;

public record GitLabSourceResolveResponse(
        String matchedPath,
        Integer score,
        List<String> candidates,
        String content,
        String message
) {
}
