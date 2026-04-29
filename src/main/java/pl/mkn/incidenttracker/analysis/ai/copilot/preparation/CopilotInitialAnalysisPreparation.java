package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisPreparation;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;

public record CopilotInitialAnalysisPreparation(
        InitialAnalysisRequest request,
        CopilotPreparedSession session
) implements InitialAnalysisPreparation {

    @Override
    public String providerName() {
        return session.providerName();
    }

    @Override
    public String correlationId() {
        return session.correlationId();
    }

    @Override
    public String prompt() {
        return session.prompt();
    }

    @Override
    public void close() {
        session.close();
    }
}
