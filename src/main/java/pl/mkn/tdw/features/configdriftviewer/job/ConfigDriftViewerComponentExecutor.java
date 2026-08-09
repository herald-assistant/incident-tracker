package pl.mkn.tdw.features.configdriftviewer.job;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ConfigDriftViewerComponentExecutor {

    CompletableFuture<Void> runAsync(Runnable task);
}
