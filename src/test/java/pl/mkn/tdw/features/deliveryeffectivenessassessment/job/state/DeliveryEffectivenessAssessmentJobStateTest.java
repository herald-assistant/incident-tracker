package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.state;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAssessmentDimensions;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAssessmentScore;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStartRequest;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentSourceResult;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryAssessmentTestFixtures.unit;

class DeliveryEffectivenessAssessmentJobStateTest {

    @Test
    void shouldExposePartialAggregateAndCompleteWithWarnings() {
        var state = state();
        var first = unit("CRM-1", mergeRequest(1, "src/A.java", "+A"));
        var second = unit("CRM-2", mergeRequest(2, "src/B.java", "+B"));
        ready(state, first, second);

        state.markUnitCompleted(first.unitId(), score(5, 0.8), null, null);
        var partial = state.snapshot();

        assertThat(partial.status()).isEqualTo("ANALYZING");
        assertThat(partial.aggregate().totalDeliveredStoryPoints()).isEqualTo(5);
        assertThat(partial.aggregate().coverage()).isEqualTo(0.5);

        state.markUnitNotScorable(second.unitId(), "No changed files");
        state.finalizeJob();
        var completed = state.snapshot();

        assertThat(completed.status()).isEqualTo("COMPLETED_WITH_WARNINGS");
        assertThat(completed.aggregate().assessedUnits()).isEqualTo(1);
        assertThat(completed.aggregate().notScorableUnits()).isEqualTo(1);
        assertThat(completed.report().sections()).singleElement()
                .satisfies(section -> assertThat(section.markdown()).contains("Total Delivered Story Points: **5**"));
    }

    @Test
    void shouldNotAllowLateCompletionToOverwriteTimedOutUnit() {
        var state = state();
        var deliveryUnit = unit("CRM-1", mergeRequest(1, "src/A.java", "+A"));
        ready(state, deliveryUnit);

        state.markUnitFailed(deliveryUnit.unitId(), "TIMEOUT", "Timed out");
        state.markUnitCompleted(deliveryUnit.unitId(), score(13, 1.0), null, null);
        state.finalizeJob();

        var snapshot = state.snapshot();
        assertThat(snapshot.units()).singleElement().satisfies(unit -> {
            assertThat(unit.status()).isEqualTo("FAILED");
            assertThat(unit.assessment()).isNull();
            assertThat(unit.errorCode()).isEqualTo("TIMEOUT");
        });
        assertThat(snapshot.status()).isEqualTo("COMPLETED_WITH_WARNINGS");
    }

    @Test
    void shouldRetainAiDiagnosticsAndAggregateVisibilityForNotScorableUnit() {
        var state = state();
        var deliveryUnit = unit("CRM-1", mergeRequest(1, "src/A.java", "+A"));
        var usage = usage();
        var report = report();
        ready(state, deliveryUnit);

        state.markUnitPreparedPrompt(deliveryUnit.unitId(), "one-shot prompt");
        state.markUnitVisibilityLimits(deliveryUnit.unitId(), List.of("Diff content was truncated."));
        state.markUnitNotScorable(
                deliveryUnit.unitId(),
                List.of("Acceptance criteria were incomplete."),
                usage,
                report
        );
        state.finalizeJob();

        var snapshot = state.snapshot();
        assertThat(snapshot.units()).singleElement().satisfies(unit -> {
            assertThat(unit.status()).isEqualTo("NOT_SCORABLE");
            assertThat(unit.usage()).isEqualTo(usage);
            assertThat(unit.report()).isEqualTo(report);
            assertThat(unit.preparedPrompt()).isEqualTo("one-shot prompt");
            assertThat(unit.promptPreparedAt()).isNotNull();
            assertThat(unit.visibilityLimits()).containsExactly(
                    "Diff content was truncated.",
                    "Acceptance criteria were incomplete."
            );
        });
        assertThat(snapshot.aggregate().usage()).isEqualTo(usage);
        assertThat(snapshot.visibilityLimits()).containsExactly(
                "Diff content was truncated.",
                "Acceptance criteria were incomplete."
        );
        assertThat(snapshot.report().meta().visibilityLimits()).isEqualTo(snapshot.visibilityLimits());
        assertThat(snapshot.report().meta().gaps()).containsExactly("No assessable Delivery Unit result was produced.");
        assertThat(snapshot.report().meta().warnings()).containsExactly("1 Delivery Unit was not scorable.");
        assertThat(snapshot.steps()).anySatisfy(step -> {
            assertThat(step.code()).isEqualTo("AI_INPUT_PREPARATION");
            assertThat(step.itemCount()).isEqualTo(1);
        });
    }

    @Test
    void shouldSumEachUnitUsageExactlyOnce() {
        var state = state();
        var first = unit("CRM-1", mergeRequest(1, "src/A.java", "+A"));
        var second = unit("CRM-2", mergeRequest(2, "src/B.java", "+B"));
        ready(state, first, second);
        var firstUsage = new AnalysisAiUsage(
                100, 20, 30, 0, 120, 0.33, 500, 1, "gpt-5.4-mini", null, null, null
        );
        var secondUsage = new AnalysisAiUsage(
                200, 40, 80, 0, 240, 1.0, 700, 2, "gpt-5.4-mini", null, null, null
        );

        state.markUnitCompleted(first.unitId(), score(3, 0.7), firstUsage, null);
        state.markUnitCompleted(second.unitId(), score(5, 0.8), secondUsage, null);

        assertThat(state.snapshot().aggregate().usage()).isEqualTo(new AnalysisAiUsage(
                300, 60, 110, 0, 360, 1.33, 1200, 3, "gpt-5.4-mini", null, null, null
        ));
    }

    private DeliveryEffectivenessAssessmentJobState state() {
        return new DeliveryEffectivenessAssessmentJobState(
                "job-1",
                new DeliveryEffectivenessAssessmentJobStartRequest(
                        "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"),
                        "gpt-5", "medium"
                )
        );
    }

    private void ready(DeliveryEffectivenessAssessmentJobState state, DeliveryUnit... units) {
        state.markDiscoveryStarted();
        state.markUnitsReady(
                new DeliveryAssessmentSourceResult("jql", units.length, false, List.of(), List.of()),
                List.of(units)
        );
    }

    private DeliveryAssessmentScore score(int points, double confidence) {
        return new DeliveryAssessmentScore(
                points,
                50,
                new DeliveryAssessmentDimensions(2, 2, 2, 2, 2, 2),
                confidence,
                List.of("evidence"),
                List.of(),
                List.of()
        );
    }

    private AnalysisAiUsage usage() {
        return new AnalysisAiUsage(100, 20, 30, 0, 120, 1.0, 500, 4, "gpt-5", null, null, null);
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                "report-1", "Assessment", "DU-CRM-1", "Insufficient evidence", List.of(), AnalysisReportMeta.empty()
        );
    }
}
