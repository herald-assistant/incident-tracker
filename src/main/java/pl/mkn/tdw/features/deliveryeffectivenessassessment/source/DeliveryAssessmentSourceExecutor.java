package pl.mkn.tdw.features.deliveryeffectivenessassessment.source;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface DeliveryAssessmentSourceExecutor {

    <T> CompletableFuture<T> supplyAsync(Supplier<T> task);
}
