package pl.mkn.tdw.features.uxinspector.context;

import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;

import java.util.List;
import java.util.Set;

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
        Set<String> allowedSourcePaths,
        List<String> limitations,
        GitLabFrontendScreenReachabilityGraph graph
) {
    public UxInspectorTargetContext {
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        allowedSourcePaths = allowedSourcePaths != null ? Set.copyOf(allowedSourcePaths) : Set.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        focusedSourceSlice = focusedSourceSlice != null ? focusedSourceSlice : "";
    }
}
