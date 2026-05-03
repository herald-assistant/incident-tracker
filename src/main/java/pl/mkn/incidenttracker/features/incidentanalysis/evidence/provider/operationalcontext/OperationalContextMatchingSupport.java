package pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.textList;

final class OperationalContextMatchingSupport {

    private OperationalContextMatchingSupport() {
    }

    static String textAny(Map<String, Object> entry, String... paths) {
        for (var path : paths) {
            var value = text(entry, path);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    static List<String> textListAny(Map<String, Object> entry, String... paths) {
        var values = new LinkedHashSet<String>();
        for (var path : paths) {
            values.addAll(textList(entry, path));
        }
        return List.copyOf(values);
    }

    static List<String> genericSignals(Map<String, Object> entry) {
        var values = new LinkedHashSet<String>();
        for (var confidence : List.of("exact", "strong", "medium", "weak")) {
            for (var key : List.of(
                    "serviceNames",
                    "containerNames",
                    "projectNames",
                    "packagePrefixes",
                    "endpointPrefixes",
                    "endpointTemplates",
                    "operationNames",
                    "hosts",
                    "hostPatterns",
                    "queues",
                    "topics",
                    "routingKeys",
                    "datasourceNames",
                    "schemas",
                    "spans",
                    "markers",
                    "errors",
                    "events",
                    "terms",
                    "classHints",
                    "paths",
                    "pathHints"
            )) {
                values.addAll(textList(entry, "matchSignals." + confidence + "." + key));
            }
        }
        values.addAll(textListAny(
                entry,
                "transport.http.endpointPrefixes",
                "transport.http.endpointTemplates",
                "transport.http.operationNames",
                "transport.http.hosts",
                "transport.http.hostPatterns",
                "transport.http.clientNames",
                "transport.messaging.queues",
                "transport.messaging.topics",
                "transport.messaging.routingKeys",
                "transport.database.datasourceNames"
        ));
        for (var channel : mapList(entry, "channels")) {
            values.addAll(textList(channel, "signals"));
            values.addAll(textListAny(channel, "type", "name"));
        }
        return List.copyOf(values);
    }

    static boolean containsId(Map<String, Object> entry, String path, String id) {
        return textList(entry, path).stream()
                .map(value -> normalize(value))
                .anyMatch(normalize(id)::equals);
    }

    static Set<String> matchedIds(List<OperationalContextMatchedEntry<Map<String, Object>>> matches) {
        return matches.stream()
                .map(match -> text(match.entry(), "id"))
                .filter(StringUtils::hasText)
                .map(value -> normalize(value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static boolean anyOverlap(List<String> leftValues, Set<String> rightValues) {
        if (leftValues.isEmpty() || rightValues.isEmpty()) {
            return false;
        }

        return leftValues.stream()
                .map(value -> normalize(value))
                .anyMatch(rightValues::contains);
    }

    static boolean containsAnyId(Set<String> ids, String... candidates) {
        for (var candidate : candidates) {
            if (ids.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    static boolean isMeaningfulSignal(String normalizedCandidate) {
        return normalizedCandidate.length() >= 3
                || normalizedCandidate.contains("/")
                || normalizedCandidate.contains(".")
                || normalizedCandidate.contains("-")
                || normalizedCandidate.contains("_");
    }

}
