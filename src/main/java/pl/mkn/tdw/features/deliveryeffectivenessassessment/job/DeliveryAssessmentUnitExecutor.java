package pl.mkn.tdw.features.deliveryeffectivenessassessment.job;

import java.util.concurrent.CompletableFuture;

public interface DeliveryAssessmentUnitExecutor {

    CompletableFuture<Void> runAsync(Runnable task);
}
