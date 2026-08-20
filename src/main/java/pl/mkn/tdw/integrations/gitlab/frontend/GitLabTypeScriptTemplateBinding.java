package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabTypeScriptTemplateBinding(
        GitLabTypeScriptTemplateBindingKind kind,
        String target,
        String expression,
        List<String> referencedSymbols,
        int lineStart
) {
    public GitLabTypeScriptTemplateBinding {
        referencedSymbols = referencedSymbols != null ? List.copyOf(referencedSymbols) : List.of();
    }
}
