package pl.mkn.tdw.features.configdriftviewer.job;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryProperties;

import java.util.concurrent.CompletableFuture;

@Component
public class BoundedConfigDriftViewerComponentExecutor
        implements ConfigDriftViewerComponentExecutor {

    private final ThreadPoolTaskExecutor executor;

    public BoundedConfigDriftViewerComponentExecutor(
            ConfigDriftViewerRepositoryProperties properties
    ) {
        var parallelism = properties.getMaxParallelComponents();
        executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("runtime-config-component-");
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(parallelism * 50);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(30);
        executor.initialize();
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }

    int maxParallelComponents() {
        return executor.getMaxPoolSize();
    }

    int queueCapacity() {
        return executor.getThreadPoolExecutor().getQueue().remainingCapacity();
    }

    String threadNamePrefix() {
        return executor.getThreadNamePrefix();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
