package pl.mkn.tdw.shared.ai;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AnalysisAiUsageTotalsTest {
    @Test
    void shouldSumConsumptionAndKeepWindowObservationsAsMaximum() {
        var one = new AnalysisAiUsage(100, 20, 5L, 0L, 120, .5, 40, 1, "crm-model", 500L, 140L, 3L, 10L);
        var two = new AnalysisAiUsage(200, 30, 0L, 0L, 230, 1., 60, 1, "crm-model", 500L, 250L, 4L, 20L);
        var result = AnalysisAiUsageTotals.sum(List.of(one, two));
        assertThat(result.inputTokens()).isEqualTo(300);
        assertThat(result.totalTokens()).isEqualTo(350);
        assertThat(result.apiCallCount()).isEqualTo(2);
        assertThat(result.aiCredits()).isEqualTo(1.5);
        assertThat(result.contextTokenLimit()).isEqualTo(500);
        assertThat(result.contextCurrentTokens()).isEqualTo(250);
        assertThat(result.reasoningTokens()).isEqualTo(30);
    }
    @Test
    void shouldKeepOptionalConsumptionUnknownAndAcceptMissingFailedAttemptUsage() {
        var known = new AnalysisAiUsage(100, 20, 5L, 0L, 120, .5, 40, 1, "crm-model", null, null, null, 10L);
        var unknown = new AnalysisAiUsage(0, 0, null, null, 0, null, 0, 1, "crm-model", null, null, null, null);
        var result = AnalysisAiUsageTotals.sum(Arrays.asList(known, unknown, null));
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.aiCredits()).isNull();
        assertThat(result.reasoningTokens()).isNull();
        assertThat(AnalysisAiUsageTotals.sum(Arrays.asList(null, null))).isNull();
    }
}
