package pl.mkn.tdw.features.incidentanalysis.evidence;

import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

public record AnalysisContext(
        String correlationId,
        AnalysisLogInput logInput,
        List<AnalysisEvidenceSection> evidenceSections
) {

    public static AnalysisContext initialize(String correlationId) {
        return initialize(AnalysisLogInput.elasticsearch(correlationId));
    }

    public static AnalysisContext initialize(AnalysisLogInput logInput) {
        return new AnalysisContext(logInput.correlationId(), logInput, List.of());
    }

    public AnalysisContext withSection(AnalysisEvidenceSection section) {
        if (!section.hasItems()) {
            return this;
        }

        var updatedSections = new ArrayList<>(evidenceSections);
        updatedSections.add(section);
        return new AnalysisContext(correlationId, logInput, List.copyOf(updatedSections));
    }

    public boolean hasAnyEvidence() {
        return !evidenceSections.isEmpty();
    }

}
