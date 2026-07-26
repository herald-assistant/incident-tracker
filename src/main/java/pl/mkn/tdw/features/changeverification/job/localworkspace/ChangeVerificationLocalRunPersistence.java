package pl.mkn.tdw.features.changeverification.job.localworkspace;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStateSnapshot;

@FunctionalInterface
public interface ChangeVerificationLocalRunPersistence {

    ChangeVerificationLocalRunPersistence NO_OP = snapshot -> {
    };

    void persistRunSnapshot(ChangeVerificationJobStateSnapshot snapshot);
}
