package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import com.github.copilot.rpc.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.coverage.CopilotIncidentEvidenceCoverageEvaluator;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotIncidentToolAccessPolicyFactory {

    private final CopilotIncidentEvidenceCoverageEvaluator coverageEvaluator;

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
                StringUtils.hasText(request.gitLabBranch())
        );
    }
}
