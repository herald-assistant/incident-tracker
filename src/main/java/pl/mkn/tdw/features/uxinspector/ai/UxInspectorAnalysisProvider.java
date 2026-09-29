package pl.mkn.tdw.features.uxinspector.ai;

import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.job.UxInspectorAnalysisRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

public interface UxInspectorAnalysisProvider {
    UxInspectorAiAnalysis analyze(String runReference, UxInspectorAnalysisRequest request,
                                  UxInspectorTargetContext context, UxInspectorPromptPreparation preparation,
                                  AnalysisAiAuthRef authRef, AnalysisAiToolEvidenceListener evidenceListener,
                                  AnalysisAiActivityListener activityListener);
}
