package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageEvaluator;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotToolAccessPolicyFactory {

    private final CopilotEvidenceCoverageEvaluator coverageEvaluator;

    public CopilotToolAccessPolicy create(
            AnalysisAiAnalysisRequest request,
            List<ToolDefinition> registeredTools
    ) {
        return CopilotToolAccessPolicy.fromCoverage(
                registeredTools,
                coverageEvaluator.evaluate(request)
        );
    }
}
