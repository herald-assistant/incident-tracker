package pl.mkn.tdw.integrations.gitlab.contract.frontend;

public record GitLabTypeScriptSymbolCandidate(
        String declaringTypeName,
        String symbolName,
        GitLabTypeScriptSymbolKind kind,
        String signature,
        int lineStart,
        int lineEnd
) {
}
