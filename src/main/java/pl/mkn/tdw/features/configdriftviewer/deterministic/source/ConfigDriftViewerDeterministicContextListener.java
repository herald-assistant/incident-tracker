package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

public interface ConfigDriftViewerDeterministicContextListener {

    ConfigDriftViewerDeterministicContextListener NO_OP = new ConfigDriftViewerDeterministicContextListener() {
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

    default void onDiffCompleted(ConfigDriftViewerDeterministicBuildResult result) {
    }
}
