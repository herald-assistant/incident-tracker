package pl.mkn.tdw.features.uxinspector.job;

import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

/** Complete, validated input retained with a started run. */
public record UxInspectorAnalysisRequest(
        String systemId,
        String branch,
        String viewId,
        String question,
        UxInspectorCapture capture,
        String model,
        String reasoningEffort
) {
    public AnalysisAiOptions aiOptions() { return new AnalysisAiOptions(model, reasoningEffort); }
}
