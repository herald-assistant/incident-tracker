package pl.mkn.tdw.features.configdriftviewer.ai;

import pl.mkn.tdw.features.configdriftviewer.ai.preparation.ConfigDriftViewerPromptPreparation;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

public interface ConfigDriftViewerAiRunner {

    ConfigDriftViewerAiRunResult run(
            String runReference,
            ConfigDriftViewerJobStartRequest request,
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerDeepContext deepContext,
            ConfigDriftViewerPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            AnalysisAiToolEvidenceListener evidenceListener,
            AnalysisAiActivityListener activityListener
    );
}
