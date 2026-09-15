package pl.mkn.tdw.features.changeverification.job.report;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleLedgerResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleOutcome;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleScope;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVisibilityLimitResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.changeverification.ChangeVerificationTestFixtures.additionalRule;
import static pl.mkn.tdw.features.changeverification.ChangeVerificationTestFixtures.result;
import static pl.mkn.tdw.features.changeverification.ChangeVerificationTestFixtures.sourceRule;

class ChangeVerificationReportMapperTest {

    @Test
    void shouldCreateDeterministicProjectionOfTheRuleLedger() {
        var storyRule = sourceRule("story-001", ChangeVerificationRuleScope.STORY,
                ChangeVerificationRuleOutcome.NOT_SATISFIED);
        var instructionRule = sourceRule("instruction-001", ChangeVerificationRuleScope.INSTRUCTION,
                ChangeVerificationRuleOutcome.SATISFIED);
        var ledger = new ChangeVerificationRuleLedgerResponse(
                true,
                true,
                null,
                List.of(storyRule, instructionRule),
                List.of(additionalRule("additional-001")),
                List.of(new ChangeVerificationVisibilityLimitResponse(
                        "Brak testu przegladarkowego dla story-001.",
                        List.of("story-001")
                ))
        );

        var report = ChangeVerificationReportMapper.toReport(result(ledger));

        assertThat(report.subHeader()).isEqualTo("Decision NEEDS_ACTION");
        assertThat(report.sections()).extracting(section -> section.id())
                .containsExactly(
                        ChangeVerificationReportSectionIds.RULE_LEDGER,
                        ChangeVerificationReportSectionIds.ADDITIONAL_CHECKS
                );
        assertThat(report.sections().get(0).markdown())
                .contains(storyRule.source().quote())
                .contains(storyRule.action())
                .contains(instructionRule.source().reference());
        assertThat(report.meta().visibilityLimits())
                .containsExactly("Brak testu przegladarkowego dla story-001.");
    }

    @Test
    void shouldNotCreateAdditionalSectionWhenAiAddedNoChecks() {
        var ledger = new ChangeVerificationRuleLedgerResponse(
                true,
                false,
                null,
                List.of(sourceRule("story-001", ChangeVerificationRuleScope.STORY,
                        ChangeVerificationRuleOutcome.SATISFIED)),
                List.of(),
                List.of()
        );

        var report = ChangeVerificationReportMapper.toReport(result(ledger));

        assertThat(report.sections()).singleElement()
                .satisfies(section -> assertThat(section.id())
                        .isEqualTo(ChangeVerificationReportSectionIds.RULE_LEDGER));
    }
}
