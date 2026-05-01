package pl.mkn.incidenttracker.integrations.gitlab.source;

import java.util.List;

public record GitLabSourceResolveMatch(
        String matchedPath,
        Integer score,
        List<String> candidates
) {
}
