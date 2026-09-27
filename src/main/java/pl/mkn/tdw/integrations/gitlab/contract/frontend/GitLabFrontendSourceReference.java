package pl.mkn.tdw.integrations.gitlab.contract.frontend;

public record GitLabFrontendSourceReference(
        String path,
        String symbol,
        Integer startLine,
        Integer endLine
) {
}

