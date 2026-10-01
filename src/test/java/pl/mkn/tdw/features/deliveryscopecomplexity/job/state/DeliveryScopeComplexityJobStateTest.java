package pl.mkn.tdw.features.deliveryscopecomplexity.job.state;

import static pl.mkn.tdw.shared.ai.AnalysisAiTestFixtures.invocation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryScopeDimensionScore;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryScopeScore;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.DeliveryScopeScoreBreakdown;
import pl.mkn.tdw.features.deliveryscopecomplexity.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.api.DeliveryScopeComplexityJobStartRequest;
import pl.mkn.tdw.features.deliveryscopecomplexity.source.DeliveryScopeSourceResult;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.unit;

class DeliveryScopeComplexityJobStateTest {

    @Test
    void shouldExposePartialAggregateAndCompleteWithWarnings() {
        var state = state();
        var first = unit("CRM-1", mergeRequest(1, "src/A.java", "+A"));
        var second = unit("CRM-2", mergeRequest(2, "src/B.java", "+B"));
        ready(state, first, second);

        state.markUnitCompleted(first.unitId(), score(5, 0.8), null);
        var partial = state.snapshot();

        assertThat(partial.status()).isEqualTo("ANALYZING");
        assertThat(partial.aggregate().totalComplexityPoints()).isEqualTo(5.0);
        assertThat(partial.aggregate().averageComplexityScore()).isEqualTo(5.0);
        assertThat(partial.aggregate().coverage()).isEqualTo(0.5);
        assertThat(partial.units().get(0).issues()).singleElement().satisfies(issue -> {
            assertThat(issue.timeSpentSeconds()).isEqualTo(14400L);
            assertThat(issue.originalEstimateSeconds()).isEqualTo(28800L);
            assertThat(issue.remainingEstimateSeconds()).isEqualTo(7200L);
            assertThat(issue.timeTrackingCapturedAt()).isEqualTo(Instant.parse("2026-07-11T08:00:00Z"));
        });

        state.markUnitNotScorable(second.unitId(), "No changed files");
        state.finalizeJob();
        var completed = state.snapshot();

        assertThat(completed.status()).isEqualTo("COMPLETED_WITH_WARNINGS");
        assertThat(completed.aggregate().assessedUnits()).isEqualTo(1);
        assertThat(completed.aggregate().notScorableUnits()).isEqualTo(1);
    }

    @Test
    void shouldNotAllowLateCompletionToOverwriteTimedOutUnit() {
        var state = state();
        var deliveryUnit = unit("CRM-1", mergeRequest(1, "src/A.java", "+A"));
        ready(state, deliveryUnit);

        state.markUnitFailed(deliveryUnit.unitId(), "TIMEOUT", "Timed out");
        state.markUnitCompleted(deliveryUnit.unitId(), score(13, 1.0), null);
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
    void shouldRetainAiDiagnosticsOnTheNotScorableUnit() {
        var state = state();
        var deliveryUnit = unit("CRM-1", mergeRequest(1, "src/A.java", "+A"));
        var usage = usage();
        ready(state, deliveryUnit);

        state.markUnitAiInvocation(deliveryUnit.unitId(), invocation("one-shot prompt", "{\"classification\":\"INSUFFICIENT_EVIDENCE\"}", null));
        state.markUnitVisibilityLimits(deliveryUnit.unitId(), List.of("Diff content was truncated."));
        state.markUnitNotScorable(
                deliveryUnit.unitId(),
                List.of("Acceptance criteria were incomplete."),
                usage
        );
        state.finalizeJob();

        var snapshot = state.snapshot();
        assertThat(snapshot.units()).singleElement().satisfies(unit -> {
            assertThat(unit.status()).isEqualTo("NOT_SCORABLE");
            assertThat(unit.usage()).isEqualTo(usage);
            assertThat(unit.aiInvocations().get(0).preparedPrompt()).isEqualTo("one-shot prompt");
            assertThat(unit.aiInvocations().get(0).startedAt()).isNotNull();
            assertThat(unit.aiInvocations().get(0).rawResponse()).isEqualTo("{\"classification\":\"INSUFFICIENT_EVIDENCE\"}");
            assertThat(unit.visibilityLimits()).containsExactly(
                    "Diff content was truncated.",
                    "Acceptance criteria were incomplete."
            );
        });
        assertThat(snapshot.aggregate().usage()).isEqualTo(usage);
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
                100, 20, 30L, 0L, 120, 0.33, 500, 1, "gpt-5.4-mini", null, null, null,
                4L
        );
        var secondUsage = new AnalysisAiUsage(
                200, 40, 80L, 0L, 240, 1.0, 700, 2, "gpt-5.4-mini", null, null, null,
                7L
        );

        state.markUnitCompleted(first.unitId(), score(3, 0.7), firstUsage);
        state.markUnitCompleted(second.unitId(), score(5, 0.8), secondUsage);

        assertThat(state.snapshot().aggregate().usage()).isEqualTo(new AnalysisAiUsage(
                300, 60, 110L, 0L, 360, 1.33, 1200, 3, "gpt-5.4-mini", null, null, null,
                11L
        ));
    }

    private DeliveryScopeComplexityJobState state() {
        return new DeliveryScopeComplexityJobState(
                "job-1",
                new DeliveryScopeComplexityJobStartRequest(
                        "CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"),
                        "gpt-5", "medium"
                )
        );
    }

    private void ready(DeliveryScopeComplexityJobState state, DeliveryUnit... units) {
        state.markDiscoveryStarted();
        state.markUnitsReady(
                new DeliveryScopeSourceResult("jql", units.length, false, List.of(), List.of()),
                List.of(units)
        );
    }

    private DeliveryScopeScore score(int points, double confidence) {
        return new DeliveryScopeScore(
                points,
                emptyBreakdown(),
                confidence,
                List.of("evidence"),
                List.of(),
                List.of()
        );
    }

    private DeliveryScopeScoreBreakdown emptyBreakdown() {
        var dimension = new DeliveryScopeDimensionScore(0, 0.0, 0.5, 0.0, 0.0, 0.0, List.of());
        return new DeliveryScopeScoreBreakdown(
                dimension, dimension, dimension, dimension, dimension, dimension
        );
    }

    private AnalysisAiUsage usage() {
        return new AnalysisAiUsage(100, 20, 30L, 0L, 120, 1.0, 500, 4, "gpt-5", null, null, null, null);
    }


    @Test
    void shouldKeepCompletedScoreAndMetadataWarningsInCurrentExportRoundTrip() throws Exception {
        var state = state();
        var deliveryUnit = unit("CRM-1", mergeRequest(1, "src/Customer.java", "+status"));
        ready(state, deliveryUnit);
        var base = score(5, 0);
        var scored = new DeliveryScopeScore(base.finalScore(), base.dimensions(), 0,
                base.evidenceSummary(), List.of("AI_METADATA_WARNING"), List.of("Metadane AI: confidence: przyjęto 0."));
        var raw = "{\"coverage\":[\"CRM/customer-api!9#metadata\"]}";
        state.markUnitAiInvocation(deliveryUnit.unitId(), invocation("CRM assessment prompt", raw, usage()));
        state.markUnitCompleted(deliveryUnit.unitId(), scored, usage());
        state.finalizeJob();
        var snapshot = state.snapshot();
        assertThat(snapshot.units()).singleElement().satisfies(unit -> {
            assertThat(unit.status()).isEqualTo("COMPLETED");
            assertThat(unit.assessment().qualityFlags()).contains("AI_METADATA_WARNING");
            assertThat(unit.visibilityLimits()).contains("Metadane AI: confidence: przyjęto 0.");
            assertThat(unit.aiInvocations().get(0).rawResponse()).isEqualTo(raw);
        });
        assertThat(snapshot.aggregate().totalComplexityPoints()).isEqualTo(5);
        assertThat(snapshot.aggregate().assessedUnits()).isEqualTo(1);
        var mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var envelope = pl.mkn.tdw.features.deliveryscopecomplexity.job.export.DeliveryScopeComplexityExportEnvelope.from(snapshot, snapshot.completedAt());
        var serialized = mapper.writeValueAsString(envelope);
        var restored = mapper.readValue(serialized, pl.mkn.tdw.features.deliveryscopecomplexity.job.export.DeliveryScopeComplexityExportEnvelope.class);
        assertThat(restored.version()).isEqualTo(1);
        assertThat(restored.payload().job().units()).isEqualTo(snapshot.units());
        assertThat(restored.payload().job().aggregate()).isEqualTo(snapshot.aggregate());
    }
}
