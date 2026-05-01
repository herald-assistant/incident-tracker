package pl.mkn.incidenttracker.aiplatform.copilot.runtime.options;

import com.github.copilot.sdk.json.ModelInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkModelLister;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotSdkModelOptionsProvider implements CopilotModelOptionsProvider {

    private final CopilotSdkModelLister modelLister;
    private final CopilotSdkProperties properties;

    private volatile CacheEntry cache;

    @Override
    public CopilotModelOptionsResponse modelOptions() {
        var cached = cache;
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.response();
        }

        try {
            var response = responseFrom(modelLister.listModels());
            cache = new CacheEntry(response, Instant.now().plus(cacheTtl()));
            return response;
        } catch (RuntimeException exception) {
            log.warn(
                    "Copilot model options are unavailable; returning configured defaults only. reason={}",
                    exception.getMessage()
            );
            log.debug("Copilot model options lookup failure details.", exception);
            return fallbackResponse();
        }
    }

    private CopilotModelOptionsResponse responseFrom(List<ModelInfo> modelInfos) {
        var models = modelInfos == null
                ? List.<CopilotModelOption>of()
                : modelInfos.stream()
                .filter(Objects::nonNull)
                .map(this::toModelOption)
                .filter(option -> StringUtils.hasText(option.id()))
                .toList();

        return new CopilotModelOptionsResponse(
                normalized(properties.getModel()),
                normalized(properties.getReasoningEffort()),
                defaultReasoningEfforts(models),
                models
        );
    }

    private CopilotModelOption toModelOption(ModelInfo modelInfo) {
        var efforts = reasoningEfforts(modelInfo);
        var supportsReasoningEffort = supportsReasoningEffort(modelInfo);
        return new CopilotModelOption(
                normalized(modelInfo.getId()),
                modelName(modelInfo),
                supportsReasoningEffort,
                supportsReasoningEffort ? efforts : List.of(),
                normalized(modelInfo.getDefaultReasoningEffort())
        );
    }

    private String modelName(ModelInfo modelInfo) {
        if (StringUtils.hasText(modelInfo.getName())) {
            return modelInfo.getName().trim();
        }

        return normalized(modelInfo.getId());
    }

    private boolean supportsReasoningEffort(ModelInfo modelInfo) {
        if (!reasoningEfforts(modelInfo).isEmpty()) {
            return true;
        }
        if (StringUtils.hasText(modelInfo.getDefaultReasoningEffort())) {
            return true;
        }
        if (modelInfo.getCapabilities() == null || modelInfo.getCapabilities().getSupports() == null) {
            return false;
        }

        return modelInfo.getCapabilities().getSupports().isReasoningEffort();
    }

    private List<String> reasoningEfforts(ModelInfo modelInfo) {
        var values = new LinkedHashSet<String>();
        if (modelInfo.getSupportedReasoningEfforts() != null) {
            for (var effort : modelInfo.getSupportedReasoningEfforts()) {
                if (StringUtils.hasText(effort)) {
                    values.add(effort.trim());
                }
            }
        }
        if (values.isEmpty() && StringUtils.hasText(modelInfo.getDefaultReasoningEffort())) {
            values.add(modelInfo.getDefaultReasoningEffort().trim());
        }

        return List.copyOf(values);
    }

    private List<String> defaultReasoningEfforts(List<CopilotModelOption> models) {
        var defaultModel = normalized(properties.getModel());
        if (!StringUtils.hasText(defaultModel)) {
            return List.of();
        }

        return models.stream()
                .filter(model -> defaultModel.equals(model.id()))
                .findFirst()
                .map(CopilotModelOption::reasoningEfforts)
                .orElse(List.of());
    }

    private CopilotModelOptionsResponse fallbackResponse() {
        return new CopilotModelOptionsResponse(
                normalized(properties.getModel()),
                normalized(properties.getReasoningEffort()),
                List.of(),
                List.of()
        );
    }

    private Duration cacheTtl() {
        return properties.getModelOptionsCacheTtl() != null
                ? properties.getModelOptionsCacheTtl()
                : Duration.ofMinutes(10);
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record CacheEntry(
            CopilotModelOptionsResponse response,
            Instant expiresAt
    ) {
    }
}
