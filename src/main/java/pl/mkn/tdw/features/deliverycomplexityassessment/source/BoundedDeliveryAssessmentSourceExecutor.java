package pl.mkn.tdw.features.deliverycomplexityassessment.source;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryComplexityAssessmentProperties;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Component
public class BoundedDeliveryAssessmentSourceExecutor implements DeliveryAssessmentSourceExecutor {

    private final ThreadPoolTaskExecutor executor;

    public BoundedDeliveryAssessmentSourceExecutor(DeliveryComplexityAssessmentProperties properties) {
        var parallelism = Math.max(1, properties.getMaxParallelSourceRequests());
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(Math.max(1, properties.getMaxIssuesPerJob()));
        executor.setThreadNamePrefix("delivery-assessment-source-");
        executor.initialize();
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
