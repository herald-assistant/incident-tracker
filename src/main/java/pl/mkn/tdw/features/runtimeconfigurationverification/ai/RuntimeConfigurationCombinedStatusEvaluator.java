package pl.mkn.tdw.features.runtimeconfigurationverification.ai;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConclusion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiExecutionStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationVerificationStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;

@Component
public class RuntimeConfigurationCombinedStatusEvaluator {

    public RuntimeConfigurationVerificationStatus evaluate(
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationAiSecondOpinion opinion
    ) {
        if (deterministic == null || deterministic.status() == RuntimeConfigurationDeterministicStatus.INCOMPLETE) {
            return RuntimeConfigurationVerificationStatus.INCOMPLETE;
        }
        if (opinion == null || opinion.executionStatus() != RuntimeConfigurationAiExecutionStatus.COMPLETED) {
            return RuntimeConfigurationVerificationStatus.INCOMPLETE;
        }
        if (opinion != null && opinion.conclusion() == RuntimeConfigurationAiConclusion.LIKELY_CONFIGURATION_ERROR) {
            return RuntimeConfigurationVerificationStatus.LIKELY_CONFIGURATION_ERROR;
        }
        if (deterministic.status() == RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
                || opinion != null && opinion.conclusion() == RuntimeConfigurationAiConclusion.REVIEW_REQUIRED) {
            return RuntimeConfigurationVerificationStatus.REVIEW_REQUIRED;
        }
        return RuntimeConfigurationVerificationStatus.NO_BLOCKING_ANOMALIES;
    }
}
