package pl.mkn.tdw.features.deliveryscopecomplexity.source;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeComplexityProperties;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Component
public class BoundedDeliveryScopeSourceExecutor implements DeliveryScopeSourceExecutor {

    private final ThreadPoolTaskExecutor executor;

    public BoundedDeliveryScopeSourceExecutor(DeliveryScopeComplexityProperties properties) {
        var parallelism = Math.max(1, properties.getMaxParallelSourceRequests());
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(Math.max(1, properties.getMaxIssuesPerJob()));
        executor.setThreadNamePrefix("delivery-scope-source-");
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
