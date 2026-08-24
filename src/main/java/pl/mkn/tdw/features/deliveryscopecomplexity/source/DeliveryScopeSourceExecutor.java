package pl.mkn.tdw.features.deliveryscopecomplexity.source;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface DeliveryScopeSourceExecutor {

    <T> CompletableFuture<T> supplyAsync(Supplier<T> task);
}
