package pl.mkn.tdw.features.deliveryscopecomplexity.deliveryunit;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.deliveryscopecomplexity.source.DeliveryScopeIssueSource;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("deliveryScopeUnitBuilder")
public class DeliveryUnitBuilder {

    public List<DeliveryUnit> build(List<DeliveryScopeIssueSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        var issuesByKey = new LinkedHashMap<String, DeliveryScopeIssueSource>();
        var issueKeysByMergeRequest = new LinkedHashMap<String, Set<String>>();
        for (var source : sources) {
            issuesByKey.put(source.issue().issueKey(), source);
            for (var mergeRequest : source.mergeRequests()) {
                issueKeysByMergeRequest.computeIfAbsent(identity(mergeRequest), ignored -> new LinkedHashSet<>())
                        .add(source.issue().issueKey());
            }
        }

        var visited = new LinkedHashSet<String>();
        var units = new ArrayList<DeliveryUnit>();
        for (var issueKey : issuesByKey.keySet()) {
            if (!visited.add(issueKey)) {
                continue;
            }
            var componentKeys = new LinkedHashSet<String>();
            var queue = new ArrayDeque<String>();
            queue.add(issueKey);
            while (!queue.isEmpty()) {
                var current = queue.removeFirst();
                componentKeys.add(current);
                var source = issuesByKey.get(current);
                if (source == null) {
                    continue;
                }
                for (var mergeRequest : source.mergeRequests()) {
                    for (var connectedKey : issueKeysByMergeRequest.getOrDefault(identity(mergeRequest), Set.of())) {
                        if (visited.add(connectedKey)) {
                            queue.addLast(connectedKey);
                        }
                    }
                }
            }
            units.add(toUnit(componentKeys, issuesByKey));
        }
        return List.copyOf(units);
    }

    private DeliveryUnit toUnit(
            Set<String> componentKeys,
            Map<String, DeliveryScopeIssueSource> issuesByKey
    ) {
        var issueSources = componentKeys.stream()
                .map(issuesByKey::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(source -> source.issue().issueKey()))
                .toList();
        var mergeRequests = new LinkedHashMap<String, GitLabMergeRequest>();
        var limitations = new LinkedHashSet<String>();
        issueSources.forEach(source -> {
            source.mergeRequests().forEach(mergeRequest -> mergeRequests.putIfAbsent(identity(mergeRequest), mergeRequest));
            limitations.addAll(source.limitations());
        });
        var unitId = "DU-" + String.join("-", issueSources.stream()
                .map(source -> source.issue().issueKey())
                .toList());
        return new DeliveryUnit(
                unitId,
                issueSources.stream().map(DeliveryScopeIssueSource::issue).toList(),
                List.copyOf(mergeRequests.values()),
                List.copyOf(limitations)
        );
    }

    public static String identity(GitLabMergeRequest mergeRequest) {
        if (mergeRequest.id() != null) {
            return "id:" + mergeRequest.id();
        }
        if (StringUtils.hasText(mergeRequest.webUrl())) {
            return "url:" + mergeRequest.webUrl().trim();
        }
        return "project:" + mergeRequest.projectPath() + "!" + mergeRequest.iid();
    }
}
