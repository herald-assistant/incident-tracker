package pl.mkn.tdw.features.runtimeconfigurationverification.ai;

import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparation;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

public interface RuntimeConfigurationAiRunner {

    RuntimeConfigurationAiRunResult run(
            String runReference,
            RuntimeConfigurationVerificationJobStartRequest request,
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deepContext,
            RuntimeConfigurationPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            AnalysisAiToolEvidenceListener evidenceListener,
            AnalysisAiActivityListener activityListener
    );
}
