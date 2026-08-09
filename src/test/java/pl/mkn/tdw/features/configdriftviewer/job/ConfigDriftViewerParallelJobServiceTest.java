package pl.mkn.tdw.features.configdriftviewer.job;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.job.localworkspace.ConfigDriftViewerLocalRunPersistence;
import pl.mkn.tdw.features.configdriftviewer.job.state.ConfigDriftViewerJobState;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ConfigDriftViewerParallelJobServiceTest {

    @Test
    void shouldBoundParallelismPreserveOrderAndSerializePersistence() throws Exception {
        var componentRunner = mock(ConfigDriftViewerComponentRunner.class);
        var coordinatorExecutor = new CapturingTaskExecutor();
        var activeComponents = new AtomicInteger();
        var maxActiveComponents = new AtomicInteger();
        var firstWaveStarted = new CountDownLatch(2);
        var releaseComponents = new CountDownLatch(1);
        var activePersists = new AtomicInteger();
        var maxActivePersists = new AtomicInteger();
        ConfigDriftViewerLocalRunPersistence persistence = snapshot -> {
            var active = activePersists.incrementAndGet();
            maxActivePersists.accumulateAndGet(active, Math::max);
            try {
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
            } finally {
                activePersists.decrementAndGet();
            }
        };
        doAnswer(invocation -> {
            var component = invocation.getArgument(0, ConfigDriftViewerJobState.class);
            var stateUpdated = invocation.getArgument(3, Runnable.class);
            var active = activeComponents.incrementAndGet();
            maxActiveComponents.accumulateAndGet(active, Math::max);
            firstWaveStarted.countDown();
            try {
                assertTrue(releaseComponents.await(5, TimeUnit.SECONDS));
                component.markFailed("TEST_COMPONENT_FINISHED", "Test component finished.");
                stateUpdated.run();
            } finally {
                activeComponents.decrementAndGet();
            }
            return null;
        }).when(componentRunner).run(any(), any(), any(), any());

        try (var componentExecutor = new FixedTaskExecutor(2)) {
            var service = new ConfigDriftViewerJobService(
                    componentRunner,
                    persistence,
                    coordinatorExecutor,
                    componentExecutor,
                    mock(AnalysisAiAuthRefResolver.class),
                    new CopilotRunAuthMapper(),
                    mock(CopilotAccessTokenResolver.class)
            );
            var systemIds = List.of("system-1", "system-2", "system-3", "system-4", "system-5");
            var created = service.startJob(new ConfigDriftViewerJobStartRequest(
                    ConfigDriftViewerMode.BASIC,
                    "runtime-config",
                    systemIds,
                    "dev1",
                    "uat1",
                    null,
                    null,
                    null
            ));

            var coordinator = CompletableFuture.runAsync(coordinatorExecutor::runNext);
            assertTrue(firstWaveStarted.await(5, TimeUnit.SECONDS));
            assertEquals(2, activeComponents.get());
            assertEquals(2, maxActiveComponents.get());

            releaseComponents.countDown();
            coordinator.get(10, TimeUnit.SECONDS);
            var completed = service.getJob(created.jobId());

            assertEquals(2, maxActiveComponents.get());
            assertEquals(1, maxActivePersists.get());
            assertEquals(systemIds, completed.components().stream().map(component -> component.systemId()).toList());
            assertEquals("FAILED", completed.status());
        } finally {
            releaseComponents.countDown();
        }
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        private void runNext() {
            var task = tasks.poll();
            if (task == null) {
                throw new IllegalStateException("No captured task to run.");
            }
            task.run();
        }
    }

    private static final class FixedTaskExecutor
            implements ConfigDriftViewerComponentExecutor, AutoCloseable {

        private final ExecutorService delegate;

        private FixedTaskExecutor(int parallelism) {
            delegate = Executors.newFixedThreadPool(parallelism);
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            return CompletableFuture.runAsync(task, delegate);
        }

        @Override
        public void close() throws InterruptedException {
            delegate.shutdown();
            if (!delegate.awaitTermination(5, TimeUnit.SECONDS)) {
                delegate.shutdownNow();
            }
        }
    }
}
