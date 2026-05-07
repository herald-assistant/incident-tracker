package pl.mkn.incidenttracker.shared.ai;

public interface AnalysisAiActivityListener {

    AnalysisAiActivityListener NO_OP = event -> {
    };

    void onAiActivity(AnalysisAiActivityEvent event);
}
