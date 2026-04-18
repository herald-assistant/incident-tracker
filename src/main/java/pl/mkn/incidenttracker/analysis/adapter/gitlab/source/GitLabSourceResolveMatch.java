package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import java.util.List;

public record GitLabSourceResolveMatch(
        String matchedPath,
        Integer score,
        List<String> candidates
) {
}
