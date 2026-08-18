package pl.mkn.tdw.features.deliverycomplexityassessment.job;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface DeliveryAssessmentUnitExecutor {

    CompletableFuture<Void> runAsync(Runnable task);

    default CompletableFuture<Void> runAsync(Runnable task, Duration timeout) {
        var timeoutMs = Math.max(1, timeout.toMillis());
        return runAsync(task).orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
