package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.rpc.*;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.*;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class CopilotPromptBudgetServiceTest {
    private final CopilotSdkProperties properties = new CopilotSdkProperties();

    private CopilotPromptBudget measure(int characters, boolean extended) {
        properties.getContextTier().setEstimatedCharactersPerToken(1);
        properties.getContextTier().setReservedTokens(10);
        var profile = new CopilotModelOption("crm-mini", "CRM model", false, List.of(), "", 120, extended ? 520 : 0,
                "", null, 100, extended ? 500 : 0, 20);
        var service = new CopilotPromptBudgetService(properties,
                auth -> new CopilotModelOptionsResponse("crm-mini", "", List.of(), List.of(profile)));
        return service.measure(new CopilotPreparedSession("crm-run", new CopilotClientOptions(), new SessionConfig().setModel("crm-mini"),
                new MessageOptions(), "x".repeat(characters), Map.of()));
    }

    @Test void shouldRespectPromptBudgetAndReserveForModelWithoutLongTier() {
        var budget = measure(75, false);
        assertThat(budget.known()).isTrue();
        assertThat(budget.estimatedInputTokens()).isEqualTo(85);
        assertThat(budget.promptTokenLimit()).isEqualTo(100);
        assertThat(budget.usableTokens()).isEqualTo(80);
        assertThat(budget.maxOutputTokens()).isEqualTo(20);
        assertThat(budget.fits()).isFalse();
        assertThat(budget.requiresLongContext()).isFalse();
    }

    @Test void shouldUseExtendedBudgetOnlyWhenPolicyAllowsIt() {
        var budget = measure(150, true);
        assertThat(budget.requiresLongContext()).isTrue();
        assertThat(budget.promptTokenLimit()).isEqualTo(500);
        assertThat(budget.fits()).isTrue();
        properties.getContextTier().setEnabled(false);
        budget = measure(150, true);
        assertThat(budget.promptTokenLimit()).isEqualTo(100);
        assertThat(budget.fits()).isFalse();
    }

    @Test void shouldExposeUnknownBudgetWithoutClaimingVerifiedCapacity() {
        var service = new CopilotPromptBudgetService(properties,
                auth -> new CopilotModelOptionsResponse("crm-unknown", "", List.of(), List.of()));
        var budget = service.measure(new CopilotPreparedSession("crm-run", new CopilotClientOptions(), new SessionConfig().setModel("crm-unknown"),
                new MessageOptions(), "CRM context", Map.of()));
        assertThat(budget.known()).isFalse();
        assertThat(budget.estimatedInputTokens()).isPositive();
    }

    @Test void shouldRejectInvalidSafetyMargin() {
        properties.setPromptBudgetSafetyRatio(Double.NaN);
        assertThatThrownBy(() -> measure(10, false)).hasMessageContaining("prompt-budget-safety-ratio");
    }

    @Test void shouldRecognizeOnlyAnExplicitProviderContextOverflow() {
        var failure = new RuntimeException("Session error: 400 prompt token count of 50000 exceeds the limit of 30000");
        var overflow = CopilotPromptOverflowException.fromProvider(failure, null);
        assertThat(overflow).isNotNull();
        assertThat(overflow.inputTokens()).isEqualTo(50000);
        assertThat(overflow.promptTokenLimit()).isEqualTo(30000);
        assertThat(CopilotPromptOverflowException.fromProvider(new RuntimeException("429 rate limit"), null)).isNull();
        assertThat(CopilotPromptOverflowException.fromProvider(new RuntimeException("400 invalid JSON"), null)).isNull();
    }
}
