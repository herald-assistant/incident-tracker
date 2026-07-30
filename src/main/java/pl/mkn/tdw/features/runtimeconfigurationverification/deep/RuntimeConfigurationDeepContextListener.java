package pl.mkn.tdw.features.runtimeconfigurationverification.deep;

import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;

public interface RuntimeConfigurationDeepContextListener {

    RuntimeConfigurationDeepContextListener NO_OP = new RuntimeConfigurationDeepContextListener() {
    };

    default void onOperationalContextStarted() {
    }

    default void onOperationalContextCompleted() {
    }

    default void onCodeGroundingStarted() {
    }

    default void onCodeGroundingCompleted() {
    }

    default void onOwnershipStarted() {
    }

    default void onOwnershipCompleted(RuntimeConfigurationDeepContext context) {
    }
}
