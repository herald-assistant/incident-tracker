package pl.mkn.tdw.features.runtimeconfigurationverification.job;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface RuntimeConfigurationComponentExecutor {

    CompletableFuture<Void> runAsync(Runnable task);
}
