package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CopilotInitialContextModelSelectorTest {

    private static final CopilotModelSelection OVERFLOW = new CopilotModelSelection("crm-extended", "high");

    @Test
    void shouldKeepDefaultBelowWindowAndPublishDecisionBeforeReturningRequest() {
        var events = new ArrayList<AnalysisAiActivityEvent>();
        var input = request(69, events);
        var result = selector(settings(), models(false)).select(input, OVERFLOW);
        assertThat(result.sessionConfigRequest().modelSelection()).isEqualTo(new CopilotModelSelection("crm-default", "low"));
        assertThat(result.prompt()).isEqualTo(input.prompt());
        assertThat(result.artifactContents()).isEqualTo(input.artifactContents());
        assertThat(result.sessionConfigRequest().skillsEnabled()).isFalse();
        assertThat(result.sessionConfigRequest().tools()).isEmpty();
        assertThat(events).singleElement().satisfies(event -> assertThat(event.details())
                .containsEntry("selectedModel", "crm-default")
                .containsEntry("estimatedInitialTokens", 79L));
    }

    @Test
    void shouldSelectOverflowAtConservativeBoundaryWithItsOwnReasoning() {
        var events = new ArrayList<AnalysisAiActivityEvent>();
        var result = selector(settings(), models(false)).select(request(90, events), OVERFLOW);
        assertThat(result.sessionConfigRequest().modelSelection()).isEqualTo(OVERFLOW);
        assertThat(result.sessionConfigRequest().contextTierPreference()).isEqualTo(CopilotContextTierPreference.LONG_CONTEXT_REQUIRED);
        assertThat(events).singleElement().satisfies(event -> assertThat(event.details())
                .containsEntry("reasoningEffort", "high")
                .containsEntry("estimatedInitialTokens", 100L));
    }

    @Test
    void shouldKeepDefaultWithLongContextEvenWhenOverflowIsUnavailable() {
        var result = selector(settings(), models(true)).select(request(100, new ArrayList<>()),
                new CopilotModelSelection("crm-absent", "high"));
        assertThat(result.sessionConfigRequest().modelSelection().model()).isEqualTo("crm-default");
        assertThat(result.sessionConfigRequest().contextTierPreference()).isEqualTo(CopilotContextTierPreference.LONG_CONTEXT_REQUIRED);
    }

    @Test
    void shouldRespectInputLimitEvenWhenTotalWindowHasSpace() {
        var limited = new CopilotModelOption("crm-default", "CRM", true, List.of("low"), "low",
                200, 0, "", null, 50, 0);
        var result = selector(settings(), List.of(limited, models(false).get(1)))
                .select(request(50, new ArrayList<>()), OVERFLOW);
        assertThat(result.sessionConfigRequest().modelSelection()).isEqualTo(OVERFLOW);
    }

    @Test
    void shouldCountDurableInstructionsAndReserveButNotDiagnosticArtifactsTwice() {
        var input = request(30, new ArrayList<>());
        input = input.withSessionConfigRequest(input.sessionConfigRequest().withDurableSystemInstructions("C".repeat(60)));
        var result = selector(settings(), models(false)).select(input, OVERFLOW);
        assertThat(result.sessionConfigRequest().modelSelection()).isEqualTo(OVERFLOW);
        assertThat(result.sessionConfigRequest().durableSystemInstructions()).hasSize(60);
    }

    @Test
    void shouldRejectMaterialBeyondExtendedWindowBeforeSessionPreparation() {
        assertThatThrownBy(() -> selector(settings(), models(false)).select(request(990, new ArrayList<>()), OVERFLOW))
                .isInstanceOf(CopilotRequiredContextTierException.class).hasMessageContaining("rozszerzone okno");
    }

    @Test
    void shouldRejectUnknownDefaultAndUnknownCapacity() {
        assertThatThrownBy(() -> selector(settings(), List.of()).select(request(1, new ArrayList<>()), OVERFLOW))
                .hasMessageContaining("nie potwierdza modelu");
        var unknown = new CopilotModelOption("crm-default", "CRM", true, List.of("low"), "low", 0, 0);
        assertThatThrownBy(() -> selector(settings(), List.of(unknown)).select(request(1, new ArrayList<>()), OVERFLOW))
                .hasMessageContaining("Brak wiarygodnego limitu");
    }

    @Test
    void shouldRejectUnsupportedReasoningAndOverflowWithoutTier() {
        assertThatThrownBy(() -> selector(settings(), models(false)).select(request(100, new ArrayList<>()),
                new CopilotModelSelection("crm-extended", "invalid"))).hasMessageContaining("reasoning effort");
        assertThatThrownBy(() -> selector(settings(), models(false)).select(request(100, new ArrayList<>()),
                new CopilotModelSelection("crm-default", "low"))).hasMessageContaining("wymaganego long_context");
    }

    @Test
    void shouldRejectRequiredTierWhenPlatformPolicyIsDisabled() {
        var properties = settings();
        properties.getContextTier().setEnabled(false);
        assertThatThrownBy(() -> selector(properties, models(false)).select(request(100, new ArrayList<>()), OVERFLOW))
                .hasMessageContaining("wymaganego long_context");
        assertThat(selector(properties, models(false)).select(request(1, new ArrayList<>()), OVERFLOW)
                .sessionConfigRequest().modelSelection().model()).isEqualTo("crm-default");
    }

    private CopilotSdkProperties settings() {
        var properties = new CopilotSdkProperties();
        properties.getContextTier().setEstimatedCharactersPerToken(1);
        properties.getContextTier().setReservedTokens(10);
        return properties;
    }

    private List<CopilotModelOption> models(boolean defaultExtended) {
        return List.of(
                new CopilotModelOption("crm-default", "CRM default", true, List.of("low"), "low", 100, defaultExtended ? 1000 : 0),
                new CopilotModelOption("crm-extended", "CRM extended", true, List.of("high"), "high", 200, 1000)
        );
    }

    private CopilotInitialContextModelSelector selector(CopilotSdkProperties properties, List<CopilotModelOption> models) {
        return new CopilotInitialContextModelSelector(properties,
                auth -> new CopilotModelOptionsResponse(null, null, List.of(), models));
    }

    private CopilotRunRequest request(int characters, List<AnalysisAiActivityEvent> events) {
        var config = new CopilotSessionConfigRequest("crm-session", List.of(), List.of(),
                new CopilotModelSelection("crm-default", "low"), "No tools", false);
        return new CopilotRunRequest("crm-run", "C".repeat(characters), config,
                Map.of("crm.md", "Diagnostic projection already embedded in the prompt"), null, events::add);
    }
}
