package pl.mkn.tdw.aiplatform.copilot.runtime.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.AnalysisAiToolResultContent;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotToolResultContentFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CopilotToolResultContentFactory factory = new CopilotToolResultContentFactory(objectMapper);

    @Test
    void preservesJsonStructureWhileLimitingLargeResults() throws Exception {
        var candidates = IntStream.range(0, 100)
                .mapToObj(index -> Map.of(
                        "filePath", "src/app/crm/customer/customer-%03d.component.ts".formatted(index),
                        "content", "export class Customer%03dComponent { %s }"
                                .formatted(index, "field = 'value'; ".repeat(300))))
                .toList();
        var rawContent = objectMapper.writeValueAsString(Map.of(
                "status", "ok",
                "candidates", candidates,
                "summary", "CRM repository candidates"
        ));

        var result = factory.capture(rawContent);

        assertThat(result.format()).isEqualTo(AnalysisAiToolResultContent.Format.JSON);
        assertThat(result.value()).isInstanceOf(Map.class);
        assertThat(result.truncated()).isTrue();
        assertThat(result.originalCharacters()).isEqualTo(rawContent.length());
        assertThat(result.retainedCharacters()).isLessThanOrEqualTo(
                CopilotToolResultContentFactory.MAX_RESULT_CHARACTERS);
        assertThat(result.omittedEntries() + result.truncatedStrings()).isPositive();
        assertThat(objectMapper.writeValueAsString(result.value())).hasSize(result.retainedCharacters());

        var captured = objectMapper.convertValue(result.value(), Map.class);
        assertThat(captured).containsEntry("status", "ok").containsKey("candidates");
        assertThat((List<?>) captured.get("candidates")).isNotEmpty();
    }

    @Test
    void limitsPlainTextAtCompleteLineBoundaries() {
        var rawContent = "line with CRM diagnostic context\n".repeat(500);

        var result = factory.capture(rawContent);

        assertThat(result.format()).isEqualTo(AnalysisAiToolResultContent.Format.TEXT);
        assertThat(result.value()).isInstanceOf(String.class);
        assertThat(result.truncated()).isTrue();
        assertThat(result.retainedCharacters()).isLessThanOrEqualTo(
                CopilotToolResultContentFactory.MAX_RESULT_CHARACTERS);
        assertThat(result.omittedEntries()).isPositive();
        assertThat((String) result.value()).endsWith("\n");
    }

    @Test
    void keepsSmallJsonResultWithoutChangingItsTypes() {
        var result = factory.capture("{\"status\":\"ok\",\"count\":4,\"paths\":[\"a.ts\",\"b.ts\"]}");

        assertThat(result.format()).isEqualTo(AnalysisAiToolResultContent.Format.JSON);
        assertThat(result.truncated()).isFalse();
        assertThat(result.value()).isEqualTo(Map.of(
                "status", "ok",
                "count", 4,
                "paths", List.of("a.ts", "b.ts")
        ));
    }
}
