package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

public record DynatraceIncidentQuery(
        String correlationId,
        Instant incidentStart,
        Instant incidentEnd,
        List<String> namespaces,
        List<String> podNames,
        List<String> containerNames,
        List<String> serviceNames
) {

    public DynatraceIncidentQuery {
        namespaces = normalizedValues(namespaces);
        podNames = normalizedValues(podNames);
        containerNames = normalizedValues(containerNames);
        serviceNames = normalizedValues(serviceNames);
    }

    public boolean hasTimeWindow() {
        return incidentStart != null
                && incidentEnd != null
                && !incidentEnd.isBefore(incidentStart);
    }

    public boolean hasLookupSignals() {
        return !namespaces.isEmpty()
                || !podNames.isEmpty()
                || !containerNames.isEmpty()
                || !serviceNames.isEmpty();
    }

    private static List<String> normalizedValues(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

}
