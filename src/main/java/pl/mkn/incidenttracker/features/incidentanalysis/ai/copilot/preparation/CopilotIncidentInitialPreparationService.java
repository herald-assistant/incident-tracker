package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotSessionPreparationMetrics;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.CopilotSessionTelemetry;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CopilotIncidentInitialPreparationService {

    private final CopilotIncidentInitialRunAssembler runAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSessionTelemetry telemetry;

    public CopilotInitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
        var preparationStart = System.nanoTime();
        var assembly = runAssembler.assemble(request);
        var metrics = assembly.metrics();
        var evidenceSections = evidenceSections(request);
        var durationMs = (System.nanoTime() - preparationStart) / 1_000_000;
        telemetry.recordPreparation(new CopilotSessionPreparationMetrics(
                metrics.toolSessionContext().analysisRunId(),
                metrics.toolSessionContext().copilotSessionId(),
                metrics.toolSessionContext().correlationId(),
                evidenceSections.size(),
                evidenceSections.stream().mapToInt(section -> section.items().size()).sum(),
                metrics.renderedArtifacts().size(),
                metrics.renderedArtifacts().stream()
                        .mapToLong(artifact -> artifact.content() != null ? artifact.content().length() : 0L)
                        .sum(),
                assembly.runRequest().prompt() != null ? assembly.runRequest().prompt().length() : 0L,
                durationMs
        ));

        return new CopilotInitialAnalysisPreparation(
                request,
                runPreparationService.prepare(assembly.runRequest())
        );
    }

    private List<AnalysisEvidenceSection> evidenceSections(InitialAnalysisRequest request) {
        return request != null && request.evidenceSections() != null
                ? request.evidenceSections()
                : List.of();
    }
}
