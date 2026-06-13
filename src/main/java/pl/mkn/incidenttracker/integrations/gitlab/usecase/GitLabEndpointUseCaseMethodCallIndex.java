package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record GitLabEndpointUseCaseMethodCallIndex(
        List<GitLabEndpointUseCaseMethodCallInfo> calls,
        Map<String, List<GitLabEndpointUseCaseMethodCallInfo>> callsByCaller
) {
    GitLabEndpointUseCaseMethodCallIndex {
        calls = calls != null ? List.copyOf(calls) : List.of();
        callsByCaller = copyMap(callsByCaller);
    }

    static GitLabEndpointUseCaseMethodCallIndex from(List<GitLabEndpointUseCaseMethodCallInfo> calls) {
        var safeCalls = calls != null ? List.copyOf(calls) : List.<GitLabEndpointUseCaseMethodCallInfo>of();
        var byCaller = new LinkedHashMap<String, java.util.ArrayList<GitLabEndpointUseCaseMethodCallInfo>>();
        for (var call : safeCalls) {
            byCaller.computeIfAbsent(call.callerMethodId(), ignored -> new java.util.ArrayList<>())
                    .add(call);
        }

        var immutableByCaller = new LinkedHashMap<String, List<GitLabEndpointUseCaseMethodCallInfo>>();
        byCaller.forEach((caller, callerCalls) -> immutableByCaller.put(caller, List.copyOf(callerCalls)));
        return new GitLabEndpointUseCaseMethodCallIndex(safeCalls, immutableByCaller);
    }

    private static Map<String, List<GitLabEndpointUseCaseMethodCallInfo>> copyMap(
            Map<String, List<GitLabEndpointUseCaseMethodCallInfo>> source
    ) {
        var copy = new LinkedHashMap<String, List<GitLabEndpointUseCaseMethodCallInfo>>();
        if (source != null) {
            source.forEach((key, value) -> copy.put(key, value != null ? List.copyOf(value) : List.of()));
        }
        return Map.copyOf(copy);
    }
}
