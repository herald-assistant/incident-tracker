package pl.mkn.tdw.integrations.gitlab.contract.source;

import java.util.List;

public record GitLabSourceResolveResponse(
        String matchedPath,
        Integer score,
        List<String> candidates,
        String content,
        String message
) {
}
