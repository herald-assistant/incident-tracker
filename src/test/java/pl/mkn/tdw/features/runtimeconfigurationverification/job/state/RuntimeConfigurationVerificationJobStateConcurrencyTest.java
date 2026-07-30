package pl.mkn.tdw.features.runtimeconfigurationverification.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.state.RuntimeConfigurationVerificationJobState;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeConfigurationVerificationJobStateConcurrencyTest {

    @Test
    void shouldCreateConsistentSnapshotsDuringConcurrentAiUpdates() throws Exception {
        var state = new RuntimeConfigurationVerificationJobState(
                "job-1",
                RuntimeConfigurationVerificationJobServiceTest.request()
        );
        state.markSourceStarted();
        state.markSourceCompleted();
        state.markParseStarted();
        state.markParseCompleted();
        state.markDiffStarted();
        state.markDiffCompleted(RuntimeConfigurationVerificationJobServiceTest.deterministic());
        state.markAiStarted("safe prompt");

        var executor = Executors.newFixedThreadPool(8);
        try {
            for (var index = 0; index < 100; index++) {
                var item = index;
                executor.submit(() -> state.markAiActivity(new AnalysisAiActivityEvent(
                        "event-" + item,
                        null,
                        "MESSAGE",
                        "AI",
                        "COMPLETED",
                        "Activity",
                        "Safe summary",
                        null,
                        null,
                        null,
                        null,
                        Instant.now(),
                        Map.of()
                )));
                executor.submit(() -> state.markAiToolEvidenceUpdated(
                        new AnalysisEvidenceSection("runtime-config", "category-" + item, List.of())
                ));
                executor.submit(state::snapshot);
            }
            executor.shutdown();
            org.junit.jupiter.api.Assertions.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        var snapshot = state.snapshot();
        assertEquals(100, snapshot.aiActivityEvents().size());
        assertEquals(100, snapshot.toolEvidenceSections().size());
        assertEquals(List.of("SOURCE", "PARSE", "DIFF", "AI"),
                snapshot.steps().stream().map(step -> step.code()).toList());
    }
}
