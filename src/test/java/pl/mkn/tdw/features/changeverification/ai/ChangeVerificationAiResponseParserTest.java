package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationAiResponseParserTest {

    private final ChangeVerificationAiResponseParser parser = new ChangeVerificationAiResponseParser(new ObjectMapper());

    @Test
    void shouldParseJsonResponseFromMarkdownFence() {
        var response = parser.parse("""
                ```json
                {
                  "status": "FAILED",
                  "findings": [
                    {
                      "id": "cv-001",
                      "severity": "HIGH",
                      "source": "ACCEPTANCE_CRITERIA",
                      "summary": "Missing cleanup",
                      "details": "Acceptance criteria mention cleanup, but MR metadata does not show it.",
                      "references": ["change-verification/jira-issue.md"],
                      "suggestedAction": "Add cleanup endpoint or update story."
                    }
                  ],
                  "suggestedActions": ["Clarify cleanup path."],
                  "visibilityLimits": ["No diff content available."],
                  "confidence": "medium"
                }
                ```
                """);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.findings()).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.id()).isEqualTo("cv-001");
                    assertThat(finding.source()).isEqualTo("ACCEPTANCE_CRITERIA");
                });
        assertThat(response.visibilityLimits()).contains("No diff content available.");
    }

    @Test
    void shouldReturnInconclusiveFallbackWhenJsonIsMissing() {
        var response = parser.parse("Compliance looks fine.");

        assertThat(response.status()).isEqualTo("INCONCLUSIVE");
        assertThat(response.visibilityLimits()).singleElement()
                .asString()
                .contains("did not contain JSON");
    }
}
