package pl.mkn.incidenttracker.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerSnippetCardResult(
        List<FlowExplorerSnippetCard> cards,
        List<String> limitations,
        boolean budgetReached,
        int totalCharacterCount
) {

    public FlowExplorerSnippetCardResult {
        cards = cards != null ? List.copyOf(cards) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        totalCharacterCount = cards.stream()
                .mapToInt(FlowExplorerSnippetCard::characterCount)
                .sum();
    }

    public static FlowExplorerSnippetCardResult empty() {
        return new FlowExplorerSnippetCardResult(List.of(), List.of(), false, 0);
    }
}
