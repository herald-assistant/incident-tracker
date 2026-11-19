package pl.mkn.tdw.integrations.gitlab.contract.frontend;

import java.util.List;
import java.util.regex.Pattern;

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
        String templateContent,
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
    private static final Pattern SELECTOR_ELEMENT = Pattern.compile("(?:^|,)\\s*([A-Za-z][A-Za-z0-9_-]*)");

    public static boolean selectorMatchesTag(String selectors, String tag) {
        if (selectors == null || tag == null) return false;
        var elements = SELECTOR_ELEMENT.matcher(selectors);
        while (elements.find()) {
            if (tag.equalsIgnoreCase(elements.group(1))) return true;
        }
        return false;
    }

    public GitLabFrontendReachabilityComponent {
        templateBindings = templateBindings != null ? List.copyOf(templateBindings) : List.of();
        entrySymbols = entrySymbols != null ? List.copyOf(entrySymbols) : List.of();
        includedSymbols = includedSymbols != null ? List.copyOf(includedSymbols) : List.of();
        dependencyIds = dependencyIds != null ? List.copyOf(dependencyIds) : List.of();
        childComponentIds = childComponentIds != null ? List.copyOf(childComponentIds) : List.of();
        templateContent = templateContent != null ? templateContent : "";
        sliceContent = sliceContent != null ? sliceContent : "";
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
