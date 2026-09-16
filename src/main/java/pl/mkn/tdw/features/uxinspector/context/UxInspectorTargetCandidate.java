package pl.mkn.tdw.features.uxinspector.context;

import java.util.List;

public record UxInspectorTargetCandidate(
        String candidateId,
        int score,
        List<String> matchReasons,
        String componentId,
        String symbol,
        String selector,
        String sourcePath,
        String templatePath,
        Integer templateLine,
        String sourceSlice,
        List<String> relatedSourcePaths
) {
    public UxInspectorTargetCandidate {
        matchReasons = matchReasons != null ? List.copyOf(matchReasons) : List.of();
        relatedSourcePaths = relatedSourcePaths != null ? List.copyOf(relatedSourcePaths) : List.of();
        sourceSlice = sourceSlice != null ? sourceSlice : "";
    }
}

