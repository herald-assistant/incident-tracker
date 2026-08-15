package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendSourceReference(
        String path,
        String symbol,
        Integer startLine,
        Integer endLine
) {
}

