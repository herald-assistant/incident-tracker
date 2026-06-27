package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisPreparation;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

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
        return request.correlationId();
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
