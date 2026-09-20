package pl.mkn.tdw.shared.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultActivityDetailsSanitizerTest {

    @Test
    void keepsOnlyVerifiedStructuredToolResultsAndSuccess() {
        var result = new AnalysisAiToolResultContent(
                AnalysisAiToolResultContent.Format.JSON,
                Map.of("filePath", "src/app/crm/customer-profile.ts", "lines", List.of(10, 11)),
                false,
                78,
                78,
                0,
                0
        );

        var sanitized = ToolResultActivityDetailsSanitizer.sanitize(Map.of(
                "success", true,
                "resultContent", result,
                "arguments", Map.of("hiddenScope", "private"),
                "resultContentPreview", "legacy preview"
        ));

        assertThat(sanitized)
                .containsEntry("success", true)
                .containsEntry("resultContent", result)
                .doesNotContainKeys("arguments", "resultContentPreview");
    }

    @Test
    void rejectsMalformedOrUnboundedResultObjects() {
        var malformed = Map.of(
                "format", "JSON",
                "value", "x".repeat(12_001),
                "truncated", true,
                "originalCharacters", 12_001,
                "retainedCharacters", 12_001,
                "omittedEntries", 0,
                "truncatedStrings", 0
        );

        assertThat(ToolResultActivityDetailsSanitizer.sanitize(Map.of(
                "success", true,
                "resultContent", malformed
        ))).containsOnly(Map.entry("success", true));
    }
}
