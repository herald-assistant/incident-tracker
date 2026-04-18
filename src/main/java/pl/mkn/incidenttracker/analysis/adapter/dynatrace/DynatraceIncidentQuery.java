package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.evidence.view.ElasticLogEvidenceView;

import java.time.Instant;
import java.util.LinkedHashSet;
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

    public static DynatraceIncidentQuery from(String correlationId, ElasticLogEvidenceView logEvidence) {
        if (logEvidence == null || logEvidence.isEmpty()) {
            return null;
        }

        Instant incidentStart = null;
        Instant incidentEnd = null;
        var namespaces = new LinkedHashSet<String>();
        var podNames = new LinkedHashSet<String>();
        var containerNames = new LinkedHashSet<String>();
        var serviceNames = new LinkedHashSet<String>();

        for (var entry : logEvidence.entries()) {
            addValue(namespaces, entry.namespace());
            addValue(podNames, entry.podName());
            addValue(containerNames, entry.containerName());
            addValue(serviceNames, entry.serviceName());

            var parsedTimestamp = parseInstant(entry.timestamp());
            if (parsedTimestamp != null) {
                incidentStart = incidentStart == null || parsedTimestamp.isBefore(incidentStart)
                        ? parsedTimestamp
                        : incidentStart;
                incidentEnd = incidentEnd == null || parsedTimestamp.isAfter(incidentEnd)
                        ? parsedTimestamp
                        : incidentEnd;
            }
        }

        if (incidentStart == null || incidentEnd == null) {
            return null;
        }

        var query = new DynatraceIncidentQuery(
                correlationId,
                incidentStart,
                incidentEnd,
                List.copyOf(namespaces),
                List.copyOf(podNames),
                List.copyOf(containerNames),
                List.copyOf(serviceNames)
        );

        return query.hasLookupSignals() ? query : null;
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

    private static void addValue(LinkedHashSet<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

}
