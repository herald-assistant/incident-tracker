package pl.mkn.tdw.shared.evidence;

import java.util.List;

public record AnalysisEvidenceSection(
        String provider,
        String category,
        List<AnalysisEvidenceItem> items
) {

    public AnalysisEvidenceSection {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

}

