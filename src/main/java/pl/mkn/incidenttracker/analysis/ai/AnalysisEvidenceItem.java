package pl.mkn.incidenttracker.analysis.ai;

import java.util.List;

public record AnalysisEvidenceItem(
        String title,
        List<AnalysisEvidenceAttribute> attributes
) {
}
