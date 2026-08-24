package pl.mkn.tdw.features.deliveryscopecomplexity.job;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface DeliveryScopeUnitExecutor {

    CompletableFuture<Void> runAsync(Runnable task);

    default CompletableFuture<Void> runAsync(Runnable task, Duration timeout) {
        var timeoutMs = Math.max(1, timeout.toMillis());
        return runAsync(task).orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
