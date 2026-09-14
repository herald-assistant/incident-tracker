package pl.mkn.tdw.features.operationalcontextassistance.draft;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OperationalContextAssistanceDraftValidationToolContextTest {

    @Autowired
    private ToolCallbackProvider[] applicationProviders;

    @Test
    void validationToolIsNotPublishedThroughGlobalMcpProviders() {
        assertThat(Arrays.stream(applicationProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .map(callback -> callback.getToolDefinition().name()).toList())
                .doesNotContain(OperationalContextAssistanceDraftValidationTools.NAME);
    }
}
