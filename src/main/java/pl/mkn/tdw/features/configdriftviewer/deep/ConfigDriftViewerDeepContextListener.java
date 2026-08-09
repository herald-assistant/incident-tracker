package pl.mkn.tdw.features.configdriftviewer.deep;

import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;

public interface ConfigDriftViewerDeepContextListener {

    ConfigDriftViewerDeepContextListener NO_OP = new ConfigDriftViewerDeepContextListener() {
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

    default void onOwnershipCompleted(ConfigDriftViewerDeepContext context) {
    }
}
