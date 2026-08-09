package pl.mkn.tdw.features.configdriftviewer.ai;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAgreementStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConclusion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConfidence;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiExecutionStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiObservation;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiObservationType;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiSecondOpinion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDriftViewerAgreementEvaluatorTest {

    private final ConfigDriftViewerAgreementEvaluator agreementEvaluator =
            new ConfigDriftViewerAgreementEvaluator();
    private final ConfigDriftViewerCombinedStatusEvaluator statusEvaluator =
            new ConfigDriftViewerCombinedStatusEvaluator();

    @Test
    void shouldDetectAgreementDisagreementAndIncompleteFloor() {
        var review = ConfigDriftViewerAiTestFixtures.deterministic(
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
        );
        var agrees = opinion(ConfigDriftViewerAiConclusion.REVIEW_REQUIRED, List.of("finding-1"));
        var disagrees = opinion(ConfigDriftViewerAiConclusion.NO_CONCERN, List.of());

        assertThat(agreementEvaluator.evaluate(review, agrees).status())
                .isEqualTo(ConfigDriftViewerAgreementStatus.AGREEMENT);
        assertThat(agreementEvaluator.evaluate(review, disagrees).status())
                .isEqualTo(ConfigDriftViewerAgreementStatus.DISAGREEMENT);

        var incomplete = ConfigDriftViewerAiTestFixtures.deterministic(
                ConfigDriftViewerDeterministicStatus.INCOMPLETE
        );
        assertThat(statusEvaluator.evaluate(incomplete, disagrees))
                .isEqualTo(ConfigDriftViewerStatus.INCOMPLETE);
        assertThat(agreementEvaluator.evaluate(incomplete, disagrees).status())
                .isEqualTo(ConfigDriftViewerAgreementStatus.DISAGREEMENT);
    }

    @Test
    void shouldKeepDeterministicReviewAndAllowAiOnlyToRaiseRisk() {
        var review = ConfigDriftViewerAiTestFixtures.deterministic(
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
        );
        assertThat(statusEvaluator.evaluate(review, opinion(ConfigDriftViewerAiConclusion.NO_CONCERN, List.of())))
                .isEqualTo(ConfigDriftViewerStatus.REVIEW_REQUIRED);
        assertThat(statusEvaluator.evaluate(
                review,
                opinion(ConfigDriftViewerAiConclusion.LIKELY_CONFIGURATION_ERROR, List.of("finding-1"))
        )).isEqualTo(ConfigDriftViewerStatus.LIKELY_CONFIGURATION_ERROR);
    }

    private ConfigDriftViewerAiSecondOpinion opinion(
            ConfigDriftViewerAiConclusion conclusion,
            List<String> findingIds
    ) {
        var observations = findingIds.isEmpty()
                ? List.<ConfigDriftViewerAiObservation>of()
                : List.of(new ConfigDriftViewerAiObservation(
                "observation-1",
                ConfigDriftViewerAiObservationType.GROUNDED_OBSERVATION,
                "Observation",
                "Explanation",
                List.of("difference-1"),
                findingIds,
                List.of(),
                List.of()
        ));
        return new ConfigDriftViewerAiSecondOpinion(
                ConfigDriftViewerAiExecutionStatus.COMPLETED,
                conclusion,
                ConfigDriftViewerAiConfidence.MEDIUM,
                "Summary",
                observations,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
