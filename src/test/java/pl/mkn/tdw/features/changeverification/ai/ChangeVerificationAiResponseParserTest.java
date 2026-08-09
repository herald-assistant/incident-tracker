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
                  "verificationChecks": [
                    {
                      "id": "story-001",
                      "origin": "DEFINED",
                      "scope": "STORY_COMPLIANCE",
                      "criterionSource": "acceptance criteria",
                      "criterionQuote": "Status is returned.",
                      "interpretationType": "explicit",
                      "expectedCriterion": "Status endpoint returns current customer status.",
                      "verificationStatus": "FAILED",
                      "verifiedAgainst": "MR changed files and controller implementation",
                      "analysis": "Controller change does not expose the status field.",
                      "evidenceRefs": ["change-verification/jira-issue.md", "src/main/java/CustomerController.java"],
                      "gaps": [],
                      "suggestedAction": "Expose status in the response DTO."
                    }
                  ],
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
        assertThat(response.verificationChecks()).singleElement()
                .satisfies(check -> {
                    assertThat(check.scope()).isEqualTo("STORY_COMPLIANCE");
                    assertThat(check.origin()).isEqualTo("DEFINED");
                    assertThat(check.interpretationType()).isEqualTo("explicit");
                    assertThat(check.criterionQuote()).isEqualTo("Status is returned.");
                });
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

    @Test
    void shouldRejectPreviousCheckContractWithoutOrigin() {
        var response = parser.parse("""
                {
                  "status": "PASSED",
                  "verificationChecks": [{
                    "id": "story-001",
                    "scope": "STORY_COMPLIANCE",
                    "verificationStatus": "PASSED"
                  }],
                  "findings": [],
                  "suggestedActions": [],
                  "visibilityLimits": [],
                  "confidence": "high"
                }
                """);

        assertThat(response.status()).isEqualTo("INCONCLUSIVE");
        assertThat(response.verificationChecks()).isEmpty();
        assertThat(response.visibilityLimits()).singleElement()
                .asString()
                .contains("supported origin");
    }

    @Test
    void shouldCapInferredChecksAndExcludeThemFromComplianceStatus() {
        var inferredChecks = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> """
                        {
                          "id": "critical-%d",
                          "origin": "INFERRED_CRITICAL",
                          "scope": "INFERRED_CRITICAL_CHECKS",
                          "criterionSource": "AI_SUGGESTION",
                          "criterionQuote": "n/a",
                          "interpretationType": "inferred",
                          "criticality": "HIGH",
                          "inferenceRationale": "Release-critical signal %d",
                          "inferenceSignals": ["signal-%d"],
                          "riskIfOmitted": "Risk %d",
                          "confidence": "medium",
                          "expectedCriterion": "Critical check %d",
                          "verificationStatus": "FAILED",
                          "verifiedAgainst": "file-%d",
                          "analysis": "Not implemented.",
                          "evidenceRefs": ["file-%d"],
                          "gaps": [],
                          "suggestedAction": "Confirm."
                        }
                        """.formatted(index, index, index, index, index, index, index))
                .collect(java.util.stream.Collectors.joining(","));
        var response = parser.parse("""
                {
                  "status": "FAILED",
                  "verificationChecks": [
                    {
                      "id": "story-001",
                      "origin": "DEFINED",
                      "scope": "STORY_COMPLIANCE",
                      "criterionSource": "acceptance criteria",
                      "criterionQuote": "Status is returned.",
                      "interpretationType": "explicit",
                      "expectedCriterion": "Status endpoint returns status.",
                      "verificationStatus": "PASSED",
                      "verifiedAgainst": "StatusController.java",
                      "analysis": "Implemented.",
                      "evidenceRefs": ["StatusController.java"],
                      "gaps": [],
                      "suggestedAction": ""
                    },
                    %s
                  ],
                  "findings": [],
                  "suggestedActions": [],
                  "visibilityLimits": [],
                  "confidence": "medium"
                }
                """.formatted(inferredChecks));

        assertThat(response.status()).isEqualTo("PASSED");
        assertThat(response.verificationChecks())
                .filteredOn(check -> "INFERRED_CRITICAL".equals(check.origin()))
                .hasSize(5);
    }
}
