package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

public interface RuntimeConfigurationDeterministicContextListener {

    RuntimeConfigurationDeterministicContextListener NO_OP = new RuntimeConfigurationDeterministicContextListener() {
    };

    default void onSourceStarted() {
    }

    default void onSourceCompleted() {
    }

    default void onParseStarted() {
    }

    default void onParseCompleted() {
    }

    default void onDiffStarted() {
    }

    default void onDiffCompleted(RuntimeConfigurationDeterministicBuildResult result) {
    }
}
