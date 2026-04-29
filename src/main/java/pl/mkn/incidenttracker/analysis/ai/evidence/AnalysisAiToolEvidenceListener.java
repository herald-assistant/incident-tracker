package pl.mkn.incidenttracker.analysis.ai.evidence;

public interface AnalysisAiToolEvidenceListener {

    AnalysisAiToolEvidenceListener NO_OP = section -> {
    };

    void onToolEvidenceUpdated(AnalysisEvidenceSection section);

}

