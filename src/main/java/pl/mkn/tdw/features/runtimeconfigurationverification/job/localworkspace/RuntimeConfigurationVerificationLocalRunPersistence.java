package pl.mkn.tdw.features.runtimeconfigurationverification.job.localworkspace;

import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;

@FunctionalInterface
public interface RuntimeConfigurationVerificationLocalRunPersistence {

    RuntimeConfigurationVerificationLocalRunPersistence NO_OP = snapshot -> {
    };

    void persistRunSnapshot(RuntimeConfigurationVerificationJobStateSnapshot snapshot);
}
