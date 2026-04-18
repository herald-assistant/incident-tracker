package pl.mkn.incidenttracker.analysis.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static pl.mkn.incidenttracker.analysis.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.incidenttracker.analysis.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.analysis.operationalcontext.OperationalContextMaps.textList;

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
        return textListAny(
                entry,
                "signals.serviceNames",
                "signals.containerNames",
                "signals.projectNames",
                "signals.packagePrefixes",
                "signals.endpoints",
                "signals.hosts",
                "signals.queues",
                "signals.topics",
                "signals.schemas",
                "signals.spans",
                "signals.markers",
                "signals.errors",
                "signals.events"
        );
    }

    static boolean containsId(Map<String, Object> entry, String path, String id) {
        return textList(entry, path).stream()
                .map(OperationalContextMaps::normalize)
                .anyMatch(normalize(id)::equals);
    }

    static Set<String> matchedIds(List<OperationalContextMatchedEntry<Map<String, Object>>> matches) {
        return matches.stream()
                .map(match -> text(match.entry(), "id"))
                .filter(StringUtils::hasText)
                .map(OperationalContextMaps::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static boolean anyOverlap(List<String> leftValues, Set<String> rightValues) {
        if (leftValues.isEmpty() || rightValues.isEmpty()) {
            return false;
        }

        return leftValues.stream()
                .map(OperationalContextMaps::normalize)
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
