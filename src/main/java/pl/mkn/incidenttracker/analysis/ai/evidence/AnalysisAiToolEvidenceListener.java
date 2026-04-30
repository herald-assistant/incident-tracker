package pl.mkn.incidenttracker.analysis.ai.evidence;

import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

public interface AnalysisAiToolEvidenceListener {

    AnalysisAiToolEvidenceListener NO_OP = section -> {
    };

    void onToolEvidenceUpdated(AnalysisEvidenceSection section);

}

