package pl.mkn.tdw.features.uxinspector.job.api;

import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus;

public record UxInspectorJobRequestSnapshot(
        String systemId,
        String systemLabel,
        String branch,
        String viewId,
        String question,
        String captureId,
        UxInspectorCapture capture,
        String aiModel,
        String reasoningEffort,
        UxInspectorTargetResolutionStatus targetResolutionStatus,
        int targetCandidateCount
) {
}
