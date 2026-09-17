package pl.mkn.tdw.integrations.gitlab.openapi;

import java.util.List;

public record GitLabOpenApiOperationCandidate(
        String httpMethod,
        String path,
        String operationId,
        String summary,
        List<String> tags
) {

    public GitLabOpenApiOperationCandidate {
        tags = tags != null ? List.copyOf(tags) : List.of();
    }
}
