package pl.mkn.tdw.features.uiexplorer.ai;

import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparation;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

public interface UiExplorerAnalysisProvider {

    UiExplorerAiAnalysis analyze(
            String runReference,
            UiExplorerJobStartRequest request,
            UiExplorerScreenReachabilityContext context,
            UiExplorerPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    );
}
