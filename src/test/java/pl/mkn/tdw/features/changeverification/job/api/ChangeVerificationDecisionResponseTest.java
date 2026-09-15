package pl.mkn.tdw.features.changeverification.job.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.changeverification.ChangeVerificationTestFixtures.additionalRule;
import static pl.mkn.tdw.features.changeverification.ChangeVerificationTestFixtures.sourceRule;

class ChangeVerificationDecisionResponseTest {

    @Test
    void shouldDeriveAllFourDecisionStatesOnlyFromSourceRules() {
        assertThat(ChangeVerificationDecisionResponse.from(List.of()).status())
                .isEqualTo(ChangeVerificationDecisionStatus.INCONCLUSIVE);
        assertThat(ChangeVerificationDecisionResponse.from(List.of(
                sourceRule("one", ChangeVerificationRuleScope.STORY, ChangeVerificationRuleOutcome.SATISFIED)
        )).status()).isEqualTo(ChangeVerificationDecisionStatus.READY);
        assertThat(ChangeVerificationDecisionResponse.from(List.of(
                sourceRule("one", ChangeVerificationRuleScope.STORY, ChangeVerificationRuleOutcome.SATISFIED),
                sourceRule("two", ChangeVerificationRuleScope.INSTRUCTION, ChangeVerificationRuleOutcome.NOT_VERIFIED)
        )).status()).isEqualTo(ChangeVerificationDecisionStatus.NEEDS_EVIDENCE);
        assertThat(ChangeVerificationDecisionResponse.from(List.of(
                sourceRule("one", ChangeVerificationRuleScope.STORY, ChangeVerificationRuleOutcome.NOT_SATISFIED),
                sourceRule("two", ChangeVerificationRuleScope.INSTRUCTION, ChangeVerificationRuleOutcome.NOT_VERIFIED)
        )).status()).isEqualTo(ChangeVerificationDecisionStatus.NEEDS_ACTION);

        assertThat(ChangeVerificationDecisionResponse.from(List.of(additionalRule("additional"))))
                .satisfies(decision -> {
                    assertThat(decision.status()).isEqualTo(ChangeVerificationDecisionStatus.INCONCLUSIVE);
                    assertThat(decision.totalRules()).isZero();
                });
    }
}
