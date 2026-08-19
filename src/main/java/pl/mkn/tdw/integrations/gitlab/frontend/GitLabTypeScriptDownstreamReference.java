package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabTypeScriptDownstreamReference(
        String sourceSymbol,
        String ownerSymbol,
        String memberSymbol,
        String targetSymbol,
        String moduleSpecifier,
        String targetSourcePath
) {
}
