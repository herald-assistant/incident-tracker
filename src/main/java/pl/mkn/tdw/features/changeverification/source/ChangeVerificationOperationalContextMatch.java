package pl.mkn.tdw.features.changeverification.source;

import java.util.List;

public record ChangeVerificationOperationalContextMatch(
        String repositoryId,
        String codeSearchScopeId,
        String codeSearchScopeName,
        String scopeType,
        String targetType,
        String targetId,
        String repositoryRole,
        Integer priority,
        String reason,
        List<String> readFor,
        String searchMode,
        List<String> pathPrefixes,
        List<String> limitations
) {

    public ChangeVerificationOperationalContextMatch {
        readFor = readFor != null ? List.copyOf(readFor) : List.of();
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
