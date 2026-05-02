package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

@Service
@RequiredArgsConstructor
public class CopilotIncidentInitialPreparationService {

    private final CopilotIncidentInitialRunAssembler runAssembler;
    private final CopilotRunPreparationService runPreparationService;

    public CopilotInitialAnalysisPreparation prepare(InitialAnalysisRequest request) {
        var assembly = runAssembler.assemble(request);

        return new CopilotInitialAnalysisPreparation(
                request,
                runPreparationService.prepare(assembly.runRequest())
        );
    }
}
