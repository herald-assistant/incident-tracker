package pl.mkn.tdw.features.deliveryeffectivenessassessment.job;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class BoundedDeliveryAssessmentUnitExecutor implements DeliveryAssessmentUnitExecutor {

    private final ThreadPoolTaskExecutor executor;

    public BoundedDeliveryAssessmentUnitExecutor(DeliveryEffectivenessAssessmentProperties properties) {
        var parallelism = Math.max(1, properties.getMaxParallelAnalyses());
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(Math.max(1, properties.getMaxIssuesPerJob()));
        executor.setThreadNamePrefix("delivery-assessment-");
        executor.initialize();
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable task, Duration timeout) {
        var result = new CompletableFuture<Void>();
        try {
            executor.execute(() -> {
                result.orTimeout(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
                try {
                    task.run();
                    result.complete(null);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
