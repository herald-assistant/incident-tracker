package pl.mkn.tdw.features.changeverification.execution;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeAssertionResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeDbAssertionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeExecutionRequest;

import java.util.List;

public interface ChangeVerificationReadonlyDbAssertionExecutor {

    List<ChangeVerificationSmokeAssertionResultResponse> execute(
            List<String> legacyAssertions,
            List<ChangeVerificationSmokeDbAssertionResponse> assertionSpecs,
            ChangeVerificationSmokeExecutionRequest request
    );
}
