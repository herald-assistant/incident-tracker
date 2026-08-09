package pl.mkn.tdw.features.configdriftviewer.job.localworkspace;

import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;

@FunctionalInterface
public interface ConfigDriftViewerLocalRunPersistence {

    ConfigDriftViewerLocalRunPersistence NO_OP = snapshot -> {
    };

    void persistRunSnapshot(ConfigDriftViewerJobStateSnapshot snapshot);
}
