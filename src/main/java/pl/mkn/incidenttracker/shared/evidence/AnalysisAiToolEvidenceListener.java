package pl.mkn.incidenttracker.shared.evidence;

public interface AnalysisAiToolEvidenceListener {

    AnalysisAiToolEvidenceListener NO_OP = section -> {
    };

    void onToolEvidenceUpdated(AnalysisEvidenceSection section);

}
