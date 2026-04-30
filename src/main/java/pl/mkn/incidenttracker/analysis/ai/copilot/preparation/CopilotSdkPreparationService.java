package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotPreparedSessionFactory;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;

@Service
@RequiredArgsConstructor
public class CopilotSdkPreparationService {

    private final CopilotInitialAnalysisRunAssembler runAssembler;
    private final CopilotPreparedSessionFactory preparedSessionFactory;
    private final CopilotSessionMetricsRegistry metricsRegistry;

    public CopilotInitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
        var preparationStart = System.nanoTime();
        var assembly = runAssembler.assemble(request);
        metricsRegistry.recordPreparation(
                assembly.toolSessionContext(),
                request,
                assembly.renderedArtifacts(),
                assembly.prompt(),
                (System.nanoTime() - preparationStart) / 1_000_000
        );

        return new CopilotInitialAnalysisPreparation(
                request,
                preparedSessionFactory.prepare(assembly.runRequest())
        );
    }

}
