package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record GitLabEndpointUseCaseCallTargetResolution(
        List<GitLabEndpointUseCaseResolvedCall> calls,
        Map<String, List<GitLabEndpointUseCaseResolvedCall>> callsByCaller,
        List<GitLabEndpointUseCaseWarning> warnings
) {
    GitLabEndpointUseCaseCallTargetResolution {
        calls = calls != null ? List.copyOf(calls) : List.of();
        callsByCaller = copyMap(callsByCaller);
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    static GitLabEndpointUseCaseCallTargetResolution from(
            List<GitLabEndpointUseCaseResolvedCall> calls,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var byCaller = new LinkedHashMap<String, java.util.ArrayList<GitLabEndpointUseCaseResolvedCall>>();
        for (var call : calls != null ? calls : List.<GitLabEndpointUseCaseResolvedCall>of()) {
            byCaller.computeIfAbsent(call.call().callerMethodId(), ignored -> new java.util.ArrayList<>())
                    .add(call);
        }

        var immutableByCaller = new LinkedHashMap<String, List<GitLabEndpointUseCaseResolvedCall>>();
        byCaller.forEach((caller, callerCalls) -> immutableByCaller.put(caller, List.copyOf(callerCalls)));
        return new GitLabEndpointUseCaseCallTargetResolution(calls, immutableByCaller, warnings);
    }

    private static Map<String, List<GitLabEndpointUseCaseResolvedCall>> copyMap(
            Map<String, List<GitLabEndpointUseCaseResolvedCall>> source
    ) {
        var copy = new LinkedHashMap<String, List<GitLabEndpointUseCaseResolvedCall>>();
        if (source != null) {
            source.forEach((key, value) -> copy.put(key, value != null ? List.copyOf(value) : List.of()));
        }
        return Map.copyOf(copy);
    }
}
