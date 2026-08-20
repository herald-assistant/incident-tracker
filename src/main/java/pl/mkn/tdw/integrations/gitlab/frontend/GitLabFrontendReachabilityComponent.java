package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendReachabilityComponent(
        String componentId,
        int breadthFirstOrder,
        int depth,
        boolean connectedToSelectedScreen,
        String discoveryKind,
        String symbol,
        String selector,
        String sourcePath,
        String templatePath,
        String status,
        List<GitLabTypeScriptTemplateBinding> templateBindings,
        List<GitLabTypeScriptSymbolCandidate> entrySymbols,
        List<GitLabTypeScriptSymbolCandidate> includedSymbols,
        List<String> dependencyIds,
        List<String> childComponentIds,
        String sliceContent,
        int sourceCharacters,
        int returnedCharacters,
        boolean truncated,
        List<String> limitations
) {
    public GitLabFrontendReachabilityComponent {
        templateBindings = templateBindings != null ? List.copyOf(templateBindings) : List.of();
        entrySymbols = entrySymbols != null ? List.copyOf(entrySymbols) : List.of();
        includedSymbols = includedSymbols != null ? List.copyOf(includedSymbols) : List.of();
        dependencyIds = dependencyIds != null ? List.copyOf(dependencyIds) : List.of();
        childComponentIds = childComponentIds != null ? List.copyOf(childComponentIds) : List.of();
        sliceContent = sliceContent != null ? sliceContent : "";
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
