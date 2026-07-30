package pl.mkn.tdw.features.runtimeconfigurationverification.ai;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAgreementStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConclusion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConfidence;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiExecutionStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiObservation;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiObservationType;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationVerificationStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationAgreementEvaluatorTest {

    private final RuntimeConfigurationAgreementEvaluator agreementEvaluator =
            new RuntimeConfigurationAgreementEvaluator();
    private final RuntimeConfigurationCombinedStatusEvaluator statusEvaluator =
            new RuntimeConfigurationCombinedStatusEvaluator();

    @Test
    void shouldDetectAgreementDisagreementAndIncompleteFloor() {
        var review = RuntimeConfigurationAiTestFixtures.deterministic(
                RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
        );
        var agrees = opinion(RuntimeConfigurationAiConclusion.REVIEW_REQUIRED, List.of("finding-1"));
        var disagrees = opinion(RuntimeConfigurationAiConclusion.NO_CONCERN, List.of());

        assertThat(agreementEvaluator.evaluate(review, agrees).status())
                .isEqualTo(RuntimeConfigurationAgreementStatus.AGREEMENT);
        assertThat(agreementEvaluator.evaluate(review, disagrees).status())
                .isEqualTo(RuntimeConfigurationAgreementStatus.DISAGREEMENT);

        var incomplete = RuntimeConfigurationAiTestFixtures.deterministic(
                RuntimeConfigurationDeterministicStatus.INCOMPLETE
        );
        assertThat(statusEvaluator.evaluate(incomplete, disagrees))
                .isEqualTo(RuntimeConfigurationVerificationStatus.INCOMPLETE);
        assertThat(agreementEvaluator.evaluate(incomplete, disagrees).status())
                .isEqualTo(RuntimeConfigurationAgreementStatus.DISAGREEMENT);
    }

    @Test
    void shouldKeepDeterministicReviewAndAllowAiOnlyToRaiseRisk() {
        var review = RuntimeConfigurationAiTestFixtures.deterministic(
                RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
        );
        assertThat(statusEvaluator.evaluate(review, opinion(RuntimeConfigurationAiConclusion.NO_CONCERN, List.of())))
                .isEqualTo(RuntimeConfigurationVerificationStatus.REVIEW_REQUIRED);
        assertThat(statusEvaluator.evaluate(
                review,
                opinion(RuntimeConfigurationAiConclusion.LIKELY_CONFIGURATION_ERROR, List.of("finding-1"))
        )).isEqualTo(RuntimeConfigurationVerificationStatus.LIKELY_CONFIGURATION_ERROR);
    }

    private RuntimeConfigurationAiSecondOpinion opinion(
            RuntimeConfigurationAiConclusion conclusion,
            List<String> findingIds
    ) {
        var observations = findingIds.isEmpty()
                ? List.<RuntimeConfigurationAiObservation>of()
                : List.of(new RuntimeConfigurationAiObservation(
                "observation-1",
                RuntimeConfigurationAiObservationType.GROUNDED_OBSERVATION,
                "Observation",
                "Explanation",
                List.of("difference-1"),
                findingIds,
                List.of(),
                List.of()
        ));
        return new RuntimeConfigurationAiSecondOpinion(
                RuntimeConfigurationAiExecutionStatus.COMPLETED,
                conclusion,
                RuntimeConfigurationAiConfidence.MEDIUM,
                "Summary",
                observations,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
