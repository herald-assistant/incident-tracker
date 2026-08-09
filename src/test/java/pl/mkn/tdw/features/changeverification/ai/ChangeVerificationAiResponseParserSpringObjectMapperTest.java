package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ChangeVerificationAiResponseParserSpringObjectMapperTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldParseFirstInferredCheckAfterSixDefinedChecksWithRuntimeObjectMapper() {
        var definedChecks = IntStream.rangeClosed(1, 6)
                .mapToObj(index -> """
                        {
                          "id": "defined-%d",
                          "origin": "DEFINED",
                          "scope": "%s",
                          "criterionSource": "source-%d",
                          "criterionQuote": "criterion-%d",
                          "interpretationType": "explicit",
                          "criticality": null,
                          "inferenceRationale": null,
                          "inferenceSignals": [],
                          "riskIfOmitted": null,
                          "confidence": null,
                          "expectedCriterion": "Expected criterion %d",
                          "verificationStatus": "PASSED",
                          "verifiedAgainst": "file-%d",
                          "analysis": "Implemented.",
                          "evidenceRefs": ["file-%d"],
                          "gaps": [],
                          "suggestedAction": ""
                        }
                        """.formatted(
                                index,
                                index <= 3 ? "STORY_COMPLIANCE" : "INSTRUCTION_COMPLIANCE",
                                index,
                                index,
                                index,
                                index,
                                index
                        ))
                .collect(Collectors.joining(","));

        var response = new ChangeVerificationAiResponseParser(objectMapper).parse("""
                {
                  "status": "PASSED_WITH_WARNINGS",
                  "verificationChecks": [
                    %s,
                    {
                      "id": "inferred-001",
                      "origin": "INFERRED_CRITICAL",
                      "scope": "INFERRED_CRITICAL_CHECKS",
                      "criterionSource": "AI_SUGGESTION",
                      "criterionQuote": "n/a",
                      "interpretationType": "inferred",
                      "criticality": "BLOCKER",
                      "inferenceRationale": "Event publication is the only initialization trigger.",
                      "inferenceSignals": ["publisher catches Exception", "no retry or outbox"],
                      "riskIfOmitted": "Checklist may not be initialized.",
                      "confidence": "high",
                      "expectedCriterion": "Publication failure must be observable.",
                      "verificationStatus": "FAILED",
                      "verifiedAgainst": "DefaultDecisionProcessMqPublisher.java",
                      "analysis": "The exception is swallowed.",
                      "evidenceRefs": ["DefaultDecisionProcessMqPublisher.java"],
                      "gaps": [],
                      "suggestedAction": "Propagate the failure or add reliable delivery."
                    }
                  ],
                  "findings": [],
                  "suggestedActions": [],
                  "visibilityLimits": [],
                  "confidence": "high"
                }
                """.formatted(definedChecks));

        assertThat(response.verificationChecks()).hasSize(7);
        assertThat(response.verificationChecks().get(6).inferenceSignals())
                .containsExactly("publisher catches Exception", "no retry or outbox");
        assertThat(response.status()).isEqualTo("PASSED");
    }

    @Test
    void shouldUseTheLastInferenceSignalsValueWhenModelRepeatsTheField() {
        var response = new ChangeVerificationAiResponseParser(objectMapper).parse("""
                {
                  "status": "PASSED",
                  "verificationChecks": [{
                    "id": "inferred-001",
                    "origin": "INFERRED_CRITICAL",
                    "scope": "INFERRED_CRITICAL_CHECKS",
                    "criterionSource": "AI_SUGGESTION",
                    "criterionQuote": "n/a",
                    "interpretationType": "inferred",
                    "criticality": "HIGH",
                    "inferenceRationale": "A concrete release signal exists.",
                    "inferenceSignals": [],
                    "riskIfOmitted": "The release may fail.",
                    "confidence": "medium",
                    "expectedCriterion": "The signal must be handled.",
                    "verificationStatus": "WARNING",
                    "verifiedAgainst": "Service.java",
                    "analysis": "The handling is incomplete.",
                    "evidenceRefs": ["Service.java"],
                    "gaps": [],
                    "suggestedAction": "Confirm handling.",
                    "inferenceSignals": ["Service.java catches the failure"]
                  }],
                  "findings": [],
                  "suggestedActions": [],
                  "visibilityLimits": [],
                  "confidence": "medium"
                }
                """);

        assertThat(response.verificationChecks()).singleElement()
                .satisfies(check -> assertThat(check.inferenceSignals())
                        .containsExactly("Service.java catches the failure"));
    }
}
