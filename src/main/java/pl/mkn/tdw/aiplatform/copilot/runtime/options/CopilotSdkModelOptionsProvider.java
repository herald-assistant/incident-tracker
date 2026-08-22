package pl.mkn.tdw.aiplatform.copilot.runtime.options;

import com.github.copilot.generated.rpc.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkModelLister;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAuthMode;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotLocalTokenMissingException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotReauthRequiredException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotSdkModelOptionsProvider implements CopilotModelOptionsProvider {

    private final CopilotSdkModelLister modelLister;
    private final CopilotSdkProperties properties;

    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();

    @Override
    public synchronized CopilotModelOptionsResponse modelOptions(CopilotRunAuth auth) {
        var cacheKey = cacheKey(auth);
        var cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.response();
        }

        try {
            var response = responseFrom(modelLister.listModels(auth));
            cache.put(cacheKey, new CacheEntry(response, Instant.now().plus(cacheTtl())));
            return response;
        } catch (CopilotLocalTokenMissingException
                 | GitHubCopilotAuthRequiredException
                 | GitHubCopilotReauthRequiredException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "Copilot model options are unavailable; returning configured defaults only. reason={}",
                    exception.getMessage()
            );
            log.debug("Copilot model options lookup failure details.", exception);
            return fallbackResponse();
        }
    }

    private CopilotModelOptionsResponse responseFrom(List<Model> modelInfos) {
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

    private CopilotModelOption toModelOption(Model modelInfo) {
        var efforts = reasoningEfforts(modelInfo);
        var supportsReasoningEffort = supportsReasoningEffort(modelInfo);
        var contextWindows = contextWindows(modelInfo);
        return new CopilotModelOption(
                normalized(modelInfo.id()),
                modelName(modelInfo),
                supportsReasoningEffort,
                supportsReasoningEffort ? efforts : List.of(),
                normalized(modelInfo.defaultReasoningEffort()),
                contextWindows.defaultWindowTokens(),
                contextWindows.longContextWindowTokens()
        );
    }

    private String modelName(Model modelInfo) {
        if (StringUtils.hasText(modelInfo.name())) {
            return modelInfo.name().trim();
        }

        return normalized(modelInfo.id());
    }

    private boolean supportsReasoningEffort(Model modelInfo) {
        if (!reasoningEfforts(modelInfo).isEmpty()) {
            return true;
        }
        if (StringUtils.hasText(modelInfo.defaultReasoningEffort())) {
            return true;
        }
        if (modelInfo.capabilities() == null || modelInfo.capabilities().supports() == null) {
            return false;
        }

        return Boolean.TRUE.equals(modelInfo.capabilities().supports().reasoningEffort());
    }

    private List<String> reasoningEfforts(Model modelInfo) {
        var values = new LinkedHashSet<String>();
        if (modelInfo.supportedReasoningEfforts() != null) {
            for (var effort : modelInfo.supportedReasoningEfforts()) {
                if (StringUtils.hasText(effort)) {
                    values.add(effort.trim());
                }
            }
        }
        if (values.isEmpty() && StringUtils.hasText(modelInfo.defaultReasoningEffort())) {
            values.add(modelInfo.defaultReasoningEffort().trim());
        }

        return List.copyOf(values);
    }

    private ContextWindows contextWindows(Model modelInfo) {
        var limits = modelInfo.capabilities() != null ? modelInfo.capabilities().limits() : null;
        var tokenPrices = modelInfo.billing() != null ? modelInfo.billing().tokenPrices() : null;
        if (limits == null || tokenPrices == null) {
            return ContextWindows.unsupported();
        }

        var defaultPromptTokens = positive(tokenPrices.contextMax());
        if (defaultPromptTokens == 0L) {
            return ContextWindows.unsupported();
        }

        var outputTokens = positive(limits.maxOutputTokens());
        var defaultWindowTokens = safeAdd(defaultPromptTokens, outputTokens);
        var longPromptTokens = Math.max(
                positive(limits.maxPromptTokens()),
                tokenPrices.longContext() != null ? positive(tokenPrices.longContext().contextMax()) : 0L
        );
        var longWindowTokens = Math.max(
                positive(limits.maxContextWindowTokens()),
                safeAdd(longPromptTokens, outputTokens)
        );

        return longWindowTokens > defaultWindowTokens
                ? new ContextWindows(defaultWindowTokens, longWindowTokens)
                : ContextWindows.unsupported();
    }

    private long positive(Number value) {
        return value != null ? Math.max(value.longValue(), 0L) : 0L;
    }

    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
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

    private String cacheKey(CopilotRunAuth auth) {
        var mode = auth != null && auth.mode() != null ? auth.mode() : CopilotAuthMode.LOCAL_TOKEN;
        if (mode == CopilotAuthMode.GITHUB_APP) {
            return mode.name() + ":" + (auth.principalId() != null ? auth.principalId() : "");
        }

        return mode.name();
    }

    private record CacheEntry(
            CopilotModelOptionsResponse response,
            Instant expiresAt
    ) {
    }

    private record ContextWindows(
            long defaultWindowTokens,
            long longContextWindowTokens
    ) {

        private static ContextWindows unsupported() {
            return new ContextWindows(0L, 0L);
        }
    }
}
