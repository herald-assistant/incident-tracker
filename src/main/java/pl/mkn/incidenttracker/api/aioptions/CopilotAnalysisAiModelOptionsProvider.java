package pl.mkn.incidenttracker.api.aioptions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.options.CopilotModelOptionsResponse;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiAuthRefResolver;

@Service
@RequiredArgsConstructor
class CopilotAnalysisAiModelOptionsProvider implements AnalysisAiModelOptionsProvider {

    private final CopilotModelOptionsProvider copilotModelOptionsProvider;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;

    @Override
    public AnalysisAiModelOptionsResponse modelOptions() {
        var auth = runAuthMapper.toRunAuth(authRefResolver.resolveForCurrentRequest());
        return toAnalysisResponse(copilotModelOptionsProvider.modelOptions(auth));
    }

    private AnalysisAiModelOptionsResponse toAnalysisResponse(CopilotModelOptionsResponse response) {
        if (response == null) {
            return new AnalysisAiModelOptionsResponse(null, null, null, null);
        }

        return new AnalysisAiModelOptionsResponse(
                response.defaultModel(),
                response.defaultReasoningEffort(),
                response.defaultReasoningEfforts(),
                response.models().stream()
                        .map(this::toAnalysisModelOption)
                        .toList()
        );
    }

    private AnalysisAiModelOption toAnalysisModelOption(CopilotModelOption option) {
        return new AnalysisAiModelOption(
                option.id(),
                option.name(),
                option.supportsReasoningEffort(),
                option.reasoningEfforts(),
                option.defaultReasoningEffort()
        );
    }
}
