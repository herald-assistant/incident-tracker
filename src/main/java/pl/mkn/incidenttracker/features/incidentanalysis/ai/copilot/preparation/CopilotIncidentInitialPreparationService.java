package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;

@Service
@RequiredArgsConstructor
public class CopilotIncidentInitialPreparationService {

    private final CopilotIncidentInitialRunAssembler runAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSessionMetricsRegistry metricsRegistry;

    public CopilotInitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
        var preparationStart = System.nanoTime();
        var assembly = runAssembler.assemble(request);
        var metrics = assembly.metrics();
        metricsRegistry.recordPreparation(
                metrics.toolSessionContext(),
                request,
                metrics.renderedArtifacts(),
                assembly.runRequest().prompt(),
                (System.nanoTime() - preparationStart) / 1_000_000
        );

        return new CopilotInitialAnalysisPreparation(
                request,
                runPreparationService.prepare(assembly.runRequest())
        );
    }

}
