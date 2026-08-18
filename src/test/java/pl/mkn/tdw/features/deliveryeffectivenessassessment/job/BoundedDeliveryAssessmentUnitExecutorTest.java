package pl.mkn.tdw.features.deliveryeffectivenessassessment.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedDeliveryAssessmentUnitExecutorTest {

    @Test
    void shouldStartItemTimeoutOnlyAfterTaskLeavesQueue() throws Exception {
        var properties = new DeliveryEffectivenessAssessmentProperties();
        properties.setMaxParallelAnalyses(1);
        properties.setMaxIssuesPerJob(2);
        var executor = new BoundedDeliveryAssessmentUnitExecutor(properties);
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);

        try {
            var first = executor.runAsync(() -> {
                firstStarted.countDown();
                await(releaseFirst);
            }, Duration.ofSeconds(2));
            assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

            var second = executor.runAsync(secondStarted::countDown, Duration.ofMillis(50));
            Thread.sleep(150);

            assertThat(second).isNotDone();
            releaseFirst.countDown();

            second.join();
            first.join();
            assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirst.countDown();
            executor.shutdown();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
