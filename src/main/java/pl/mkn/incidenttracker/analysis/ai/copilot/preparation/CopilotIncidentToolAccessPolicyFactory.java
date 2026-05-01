package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageEvaluator;
import pl.mkn.incidenttracker.analysis.ai.initial.InitialAnalysisRequest;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotIncidentToolAccessPolicyFactory {

    private final CopilotEvidenceCoverageEvaluator coverageEvaluator;

    public CopilotIncidentToolAccessPolicy create(
            InitialAnalysisRequest request,
            List<ToolDefinition> registeredTools
    ) {
        return CopilotIncidentToolAccessPolicy.fromCoverage(
                registeredTools,
                coverageEvaluator.evaluate(request)
        );
    }

    public CopilotIncidentToolAccessPolicy createForFollowUp(
            AnalysisAiChatRequest request,
            List<ToolDefinition> registeredTools
    ) {
        return CopilotIncidentToolAccessPolicy.fromFollowUpSession(
                registeredTools,
                StringUtils.hasText(request.environment()),
                StringUtils.hasText(request.gitLabGroup()) && StringUtils.hasText(request.gitLabBranch())
        );
    }
}
