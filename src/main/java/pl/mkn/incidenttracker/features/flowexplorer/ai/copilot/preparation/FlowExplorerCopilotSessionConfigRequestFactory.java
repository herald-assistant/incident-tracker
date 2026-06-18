package pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotNamedSkillDirectoryResolver;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiOptions;

import java.util.List;

@Component
public class FlowExplorerCopilotSessionConfigRequestFactory {

    private static final String FLOW_EXPLORER_TOOL_DENIED_MESSAGE =
            "Use only the inline Flow Explorer artifacts and the explicitly enabled Flow Explorer tools for this session.";

    private final CopilotNamedSkillDirectoryResolver skillDirectoryResolver;

    @Autowired
    public FlowExplorerCopilotSessionConfigRequestFactory(CopilotNamedSkillDirectoryResolver skillDirectoryResolver) {
        this.skillDirectoryResolver = skillDirectoryResolver;
    }

    public FlowExplorerCopilotSessionConfigRequestFactory(CopilotSkillRuntimeLoader skillRuntimeLoader) {
        this(new CopilotNamedSkillDirectoryResolver(skillRuntimeLoader));
    }

    public CopilotSessionConfigRequest create(
            String copilotSessionId,
            FlowExplorerCopilotToolAccessPolicy toolAccessPolicy,
            AnalysisAiOptions options
    ) {
        return create(copilotSessionId, toolAccessPolicy, options, FlowExplorerCopilotRuntimeSkillNames.allSkillNames());
    }

    public CopilotSessionConfigRequest createForFollowUp(
            String copilotSessionId,
            FlowExplorerCopilotToolAccessPolicy toolAccessPolicy,
            AnalysisAiOptions options
    ) {
        return create(
                copilotSessionId,
                toolAccessPolicy,
                options,
                FlowExplorerCopilotRuntimeSkillNames.followUpSkillNames()
        );
    }

    private CopilotSessionConfigRequest create(
            String copilotSessionId,
            FlowExplorerCopilotToolAccessPolicy toolAccessPolicy,
            AnalysisAiOptions options,
            List<String> skillNames
    ) {
        var policy = toolAccessPolicy != null
                ? toolAccessPolicy
                : FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of());

        return new CopilotSessionConfigRequest(
                copilotSessionId,
                policy.enabledTools(),
                policy.availableToolNames(),
                skillDirectoryResolver.resolveSkillDirectories(skillNames),
                modelSelection(options),
                FLOW_EXPLORER_TOOL_DENIED_MESSAGE
        );
    }

    private CopilotModelSelection modelSelection(AnalysisAiOptions options) {
        return options != null
                ? new CopilotModelSelection(options.model(), options.reasoningEffort())
                : CopilotModelSelection.DEFAULT;
    }
}
