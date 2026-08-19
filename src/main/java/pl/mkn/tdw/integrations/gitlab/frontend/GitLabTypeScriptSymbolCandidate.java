package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabTypeScriptSymbolCandidate(
        String declaringTypeName,
        String symbolName,
        GitLabTypeScriptSymbolKind kind,
        String signature,
        int lineStart,
        int lineEnd
) {
}
