package pl.mkn.tdw.features.uxinspector.context;

import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;

import java.util.List;

public record UxInspectorTargetContext(
        String systemId,
        String systemLabel,
        UxInspectorSourceScope sourceScope,
        UxInspectorViewIdentity view,
        UxInspectorSourceRevision sourceRevision,
        UxInspectorTargetResolutionStatus status,
        List<UxInspectorTargetCandidate> candidates,
        UxInspectorSourceBinding sourceBinding,
        String focusedSourceSlice,
        List<String> limitations,
        GitLabFrontendScreenReachabilityGraph graph
) {
    public UxInspectorTargetContext {
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        focusedSourceSlice = focusedSourceSlice != null ? focusedSourceSlice : "";
    }
}
