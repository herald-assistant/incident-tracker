package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleOutcome;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleScope;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationAiResponseParserTest {

    private final ChangeVerificationAiResponseParser parser =
            new ChangeVerificationAiResponseParser(new ObjectMapper());

    @Test
    void shouldParseOneCanonicalLedgerWithLinkedVisibilityLimit() {
        var response = parser.parse(validLedger());

        assertThat(response.rules()).singleElement().satisfies(rule -> {
            assertThat(rule.id()).isEqualTo("story-001");
            assertThat(rule.scope()).isEqualTo(ChangeVerificationRuleScope.STORY);
            assertThat(rule.outcome()).isEqualTo(ChangeVerificationRuleOutcome.SATISFIED);
            assertThat(rule.source().quote()).isEqualTo("Klient jest widoczny po zapisie.");
            assertThat(rule.evidence()).singleElement()
                    .satisfies(evidence -> assertThat(evidence.reference()).contains("CustomerFlowTest"));
        });
        assertThat(response.additionalChecks()).singleElement()
                .satisfies(rule -> assertThat(rule.scope()).isEqualTo(ChangeVerificationRuleScope.ADDITIONAL));
        assertThat(response.visibilityLimits()).singleElement()
                .satisfies(limit -> assertThat(limit.affectedRuleIds()).containsExactly("additional-001"));
    }

    @Test
    void shouldRejectWholeResponseInsteadOfSilentlyDroppingInvalidRule() {
        var response = parser.parse(validLedger().replace(
                "\"releaseImpact\": \"NONE\"",
                "\"releaseImpact\": \"WARNING\""
        ));

        assertThat(response.rules()).isEmpty();
        assertThat(response.additionalChecks()).isEmpty();
        assertThat(response.visibilityLimits()).singleElement()
                .satisfies(limit -> assertThat(limit.message()).contains("invalid or duplicate rule"));
    }

    @Test
    void shouldRejectVisibilityLimitThatIsNotLinkedToAnExistingRule() {
        var response = parser.parse(validLedger().replace("additional-001\"]", "missing-rule\"]"));

        assertThat(response.rules()).isEmpty();
        assertThat(response.visibilityLimits()).singleElement()
                .satisfies(limit -> assertThat(limit.message()).contains("unlinked visibility limit"));
    }

    @Test
    void shouldApplyOutcomeValidationToAdditionalChecks() {
        var response = parser.parse(validLedger().replace(
                "\"outcome\": \"NOT_VERIFIED\"",
                "\"outcome\": \"SATISFIED\""
        ));

        assertThat(response.rules()).isEmpty();
        assertThat(response.additionalChecks()).isEmpty();
        assertThat(response.visibilityLimits()).singleElement()
                .satisfies(limit -> assertThat(limit.message()).contains("invalid or duplicate rule"));
    }

    @Test
    void shouldRejectInferredInterpretationForSourceRule() {
        var response = parser.parse(validLedger().replace(
                "\"interpretationType\": \"EXPLICIT\"",
                "\"interpretationType\": \"INFERRED\""
        ));

        assertThat(response.rules()).isEmpty();
        assertThat(response.additionalChecks()).isEmpty();
    }

    @Test
    void shouldRejectMoreThanFiveAdditionalChecks() {
        var additionalCheck = additionalCheck();
        var sixAdditionalChecks = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> additionalCheck.replace("additional-001", "additional-00" + index))
                .collect(java.util.stream.Collectors.joining(","));
        var response = parser.parse(validLedger().replace(additionalCheck, sixAdditionalChecks));

        assertThat(response.rules()).isEmpty();
        assertThat(response.additionalChecks()).isEmpty();
        assertThat(response.visibilityLimits()).singleElement()
                .satisfies(limit -> assertThat(limit.message()).contains("more than five additional checks"));
    }

    @Test
    void shouldFallbackWhenRequiredCollectionsAreMissing() {
        var response = parser.parse("{\"rules\":[]}");

        assertThat(response.rules()).isEmpty();
        assertThat(response.visibilityLimits()).singleElement()
                .satisfies(limit -> assertThat(limit.message()).contains("required rule ledger collections"));
    }

    static String validLedger() {
        return """
                {
                  "rules": [{
                    "id": "story-001",
                    "scope": "STORY",
                    "source": {
                      "type": "ACCEPTANCE_CRITERION",
                      "label": "CRM-123 AC",
                      "reference": "CRM-123#ac-1",
                      "quote": "Klient jest widoczny po zapisie."
                    },
                    "normalizedRule": "Zapisany klient pojawia sie na liscie.",
                    "interpretationType": "EXPLICIT",
                    "outcome": "SATISFIED",
                    "releaseImpact": "NONE",
                    "conclusion": "Test potwierdza widocznosc klienta.",
                    "evidence": [{
                      "summary": "Test scenariusza zapisu przechodzi.",
                      "reference": "src/test/java/example/CustomerFlowTest.java"
                    }],
                    "missingEvidence": [],
                    "action": null,
                    "rationale": null,
                    "riskIfOmitted": null,
                    "signals": [],
                    "confidence": null
                  }],
                  "additionalChecks": [%s],
                  "visibilityLimits": [{
                    "message": "Brak testu integracyjnego ponowienia.",
                    "affectedRuleIds": ["additional-001"]
                  }]
                }
                """.formatted(additionalCheck());
    }

    private static String additionalCheck() {
        return """
                {
                  "id": "additional-001",
                  "scope": "ADDITIONAL",
                  "source": {
                    "type": "AI_SUGGESTION",
                    "label": "AI-suggested check",
                    "reference": "AI",
                    "quote": "Sprawdz idempotencje zapisu klienta."
                  },
                  "normalizedRule": "Ponowienie nie tworzy duplikatu.",
                  "interpretationType": "INFERRED",
                  "outcome": "NOT_VERIFIED",
                  "releaseImpact": "REVIEW",
                  "conclusion": "Brak testu ponowienia.",
                  "evidence": [],
                  "missingEvidence": ["Brak testu idempotencji."],
                  "action": "Dodaj test ponowienia.",
                  "rationale": "Zmiana dotyka zapisu.",
                  "riskIfOmitted": "Moze powstac duplikat.",
                  "signals": ["CustomerService.save"],
                  "confidence": "MEDIUM"
                }
                """.trim();
    }
}
