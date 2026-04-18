package pl.mkn.incidenttracker.analysis.ai;

import java.util.List;

public record AnalysisEvidenceSection(
        String provider,
        String category,
        List<AnalysisEvidenceItem> items
) {

    public boolean hasItems() {
        return !items.isEmpty();
    }

}
