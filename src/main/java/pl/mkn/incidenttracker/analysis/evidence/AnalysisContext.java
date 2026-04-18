package pl.mkn.incidenttracker.analysis.evidence;

import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

public record AnalysisContext(
        String correlationId,
        List<AnalysisEvidenceSection> evidenceSections
) {

    public static AnalysisContext initialize(String correlationId) {
        return new AnalysisContext(correlationId, List.of());
    }

    public AnalysisContext withSection(AnalysisEvidenceSection section) {
        if (!section.hasItems()) {
            return this;
        }

        var updatedSections = new ArrayList<>(evidenceSections);
        updatedSections.add(section);
        return new AnalysisContext(correlationId, List.copyOf(updatedSections));
    }

    public boolean hasAnyEvidence() {
        return !evidenceSections.isEmpty();
    }

}
