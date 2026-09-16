package pl.mkn.tdw.features.uxinspector.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsResponse;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UxInspectorAiSelectionValidatorTest {
    private final CopilotModelOptionsProvider provider = mock(CopilotModelOptionsProvider.class);
    private final UxInspectorAiSelectionValidator validator =
            new UxInspectorAiSelectionValidator(provider, new CopilotRunAuthMapper());
    private final AnalysisAiAuthRef auth = AnalysisAiAuthRef.localToken("CRM test");

    @Test
    void shouldAcceptOnlyModelAndReasoningPairsFromCurrentCatalog() {
        when(provider.modelOptions(any())).thenReturn(options());

        assertThatCode(() -> validator.validate("gpt-crm", "medium", auth)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("fast-crm", null, auth)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("gpt-crm", "ultra", auth))
                .isInstanceOf(UxInspectorJobException.class).hasMessageContaining("supported");
        assertThatThrownBy(() -> validator.validate("fast-crm", "low", auth))
                .isInstanceOf(UxInspectorJobException.class).hasMessageContaining("does not accept");
        assertThatThrownBy(() -> validator.validate("removed-model", "medium", auth))
                .isInstanceOf(UxInspectorJobException.class).hasMessageContaining("not available");
    }

    @Test
    void shouldFailClosedWhenCatalogIsUnavailable() {
        when(provider.modelOptions(any())).thenReturn(new CopilotModelOptionsResponse(null, null, null, null));

        assertThatThrownBy(() -> validator.validate("gpt-crm", "medium", auth))
                .isInstanceOf(UxInspectorJobException.class).hasMessageContaining("unavailable");
    }

    private CopilotModelOptionsResponse options() {
        return new CopilotModelOptionsResponse("gpt-crm", "medium", List.of("low", "medium"), List.of(
                new CopilotModelOption("gpt-crm", "CRM reasoning", true, List.of("low", "medium"), "medium", 100, 200),
                new CopilotModelOption("fast-crm", "CRM fast", false, List.of(), "", 100, 0)
        ));
    }
}
