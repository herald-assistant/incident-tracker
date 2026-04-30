package pl.mkn.incidenttracker.shared.evidence;

import java.util.List;

public record AnalysisEvidenceItem(
        String title,
        List<AnalysisEvidenceAttribute> attributes
) {

    public AnalysisEvidenceItem {
        attributes = attributes != null ? List.copyOf(attributes) : List.of();
    }
}

