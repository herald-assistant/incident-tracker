package pl.mkn.tdw.aiplatform.copilot.runtime.options;

import com.github.copilot.generated.rpc.Model;
import com.github.copilot.generated.rpc.ModelBillingTokenPrices;
import com.github.copilot.generated.rpc.ModelBillingTokenPricesLongContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkModelLister;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotLocalTokenMissingException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotFineGrainedPat;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotPatInvalidException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotPatSource;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class CopilotSdkModelOptionsProvider implements CopilotModelOptionsProvider {

    private final CopilotSdkModelLister modelLister;
    private final CopilotSdkProperties properties;
    private final CopilotPatSource patSource;

    private String cachedPatDigest;
    private CacheEntry cached;

    @Override
    public synchronized CopilotModelOptionsResponse modelOptions(CopilotRunAuth auth) {
        var pat = patSource.currentPat();
        if (!StringUtils.hasText(pat)) {
            throw new CopilotLocalTokenMissingException();
        }
        if (!CopilotFineGrainedPat.valid(pat)) {
            throw new CopilotPatInvalidException();
        }
        var patDigest = digest(pat.trim());
        if (patDigest.equals(cachedPatDigest) && cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.response();
        }

        try {
            var response = responseFrom(modelLister.listModels(auth));
            cachedPatDigest = patDigest;
            cached = new CacheEntry(response, Instant.now().plus(cacheTtl()));
            return response;
        } catch (CopilotLocalTokenMissingException | CopilotPatInvalidException exception) {
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

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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
                null,
                contextWindows.defaultWindowTokens(),
                contextWindows.longContextWindowTokens(),
                modelInfo.modelPickerCategory() != null ? modelInfo.modelPickerCategory().getValue() : "",
                pricing(modelInfo),
                contextWindows.defaultPromptTokens(),
                contextWindows.longPromptTokens()
        );
    }

    private CopilotModelPricing pricing(Model modelInfo) {
        var tokenPrices = modelInfo.billing() != null ? modelInfo.billing().tokenPrices() : null;
        if (tokenPrices == null || tokenPrices.batchSize() == null || tokenPrices.batchSize() <= 0) {
            return null;
        }
        var batchSize = tokenPrices.batchSize();
        var longContext = tokenPrices.longContext();
        return new CopilotModelPricing(
                rates(tokenPrices, batchSize),
                longContext != null ? rates(longContext, batchSize) : null,
                longContext != null ? positive(tokenPrices.contextMax()) : null
        );
    }

    private CopilotModelTokenRates rates(ModelBillingTokenPrices prices, long batchSize) {
        return new CopilotModelTokenRates(
                perMillion(prices.inputPrice(), batchSize),
                perMillion(prices.cacheReadPrice() != null ? prices.cacheReadPrice() : prices.cachePrice(), batchSize),
                perMillion(prices.cacheWritePrice(), batchSize),
                perMillion(prices.outputPrice(), batchSize)
        );
    }

    private CopilotModelTokenRates rates(ModelBillingTokenPricesLongContext prices, long batchSize) {
        return new CopilotModelTokenRates(
                perMillion(prices.inputPrice(), batchSize),
                perMillion(prices.cacheReadPrice() != null ? prices.cacheReadPrice() : prices.cachePrice(), batchSize),
                perMillion(prices.cacheWritePrice(), batchSize),
                perMillion(prices.outputPrice(), batchSize)
        );
    }

    private Double perMillion(Double price, long batchSize) {
        if (price == null || !Double.isFinite(price) || price < 0) {
            return null;
        }
        var normalized = price * 1_000_000D / batchSize;
        return Double.isFinite(normalized) ? normalized : null;
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
        return List.copyOf(values);
    }

    private ContextWindows contextWindows(Model modelInfo) {
        var limits = modelInfo.capabilities() != null ? modelInfo.capabilities().limits() : null;
        var tokenPrices = modelInfo.billing() != null ? modelInfo.billing().tokenPrices() : null;
        if (limits == null) {
            return ContextWindows.unsupported();
        }
        var outputTokens = positive(limits.maxOutputTokens());
        var billedDefaultPrompt = tokenPrices != null
                ? promptBudget(tokenPrices.maxPromptTokens(), tokenPrices.contextMax()) : 0L;
        var maximumPrompt = positive(limits.maxPromptTokens());
        var maximumWindow = positive(limits.maxContextWindowTokens());
        if (billedDefaultPrompt == 0L) {
            var window = maximumWindow > 0L ? maximumWindow
                    : maximumPrompt > 0L ? safeAdd(maximumPrompt, outputTokens) : 0L;
            return new ContextWindows(window, 0L, maximumPrompt, 0L);
        }
        var defaultWindow = boundedWindow(safeAdd(billedDefaultPrompt, outputTokens), maximumWindow);
        var defaultPrompt = boundedWindow(billedDefaultPrompt, maximumPrompt);
        var extendedPrices = tokenPrices.longContext();
        var extendedPrompt = extendedPrices != null
                ? promptBudget(extendedPrices.maxPromptTokens(), extendedPrices.contextMax()) : 0L;
        extendedPrompt = extendedPrompt > 0L ? boundedWindow(extendedPrompt, maximumPrompt) : maximumPrompt;
        var extendedWindow = extendedPrompt > 0L
                ? boundedWindow(safeAdd(extendedPrompt, outputTokens), maximumWindow) : maximumWindow;
        return new ContextWindows(defaultWindow, extendedWindow > defaultWindow ? extendedWindow : 0L,
                defaultPrompt, extendedWindow > defaultWindow ? extendedPrompt : 0L);
    }

    private long promptBudget(Number current, Number legacy) {
        return positive(current) > 0L ? positive(current) : positive(legacy);
    }

    private long boundedWindow(long derivedWindow, long maximumWindow) {
        return maximumWindow > 0L ? Math.min(derivedWindow, maximumWindow) : derivedWindow;
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

    private record CacheEntry(
            CopilotModelOptionsResponse response,
            Instant expiresAt
    ) {
    }

    private record ContextWindows(
            long defaultWindowTokens,
            long longContextWindowTokens,
            long defaultPromptTokens,
            long longPromptTokens
    ) {

        private static ContextWindows unsupported() {
            return new ContextWindows(0L, 0L, 0L, 0L);
        }
    }
}
