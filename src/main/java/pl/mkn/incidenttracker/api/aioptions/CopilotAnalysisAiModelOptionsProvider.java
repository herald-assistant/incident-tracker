package pl.mkn.incidenttracker.api.aioptions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.options.CopilotModelOptionsResponse;

@Service
@RequiredArgsConstructor
class CopilotAnalysisAiModelOptionsProvider implements AnalysisAiModelOptionsProvider {

    private final CopilotModelOptionsProvider copilotModelOptionsProvider;

    @Override
    public AnalysisAiModelOptionsResponse modelOptions() {
        return toAnalysisResponse(copilotModelOptionsProvider.modelOptions());
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
