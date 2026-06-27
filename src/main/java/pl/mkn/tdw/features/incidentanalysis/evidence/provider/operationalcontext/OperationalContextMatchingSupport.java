package pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextMaps.normalize;

final class OperationalContextMatchingSupport {

    private OperationalContextMatchingSupport() {
    }

    static List<String> genericSignals(OperationalContextEntry entry) {
        return entry.genericSignals();
    }

    static Set<String> matchedIds(List<? extends OperationalContextMatchedEntry<? extends OperationalContextEntry>> matches) {
        return matches.stream()
                .map(match -> match.entry().id())
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
