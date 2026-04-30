package pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentQuery;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

public final class DynatraceIncidentQueryFactory {

    private DynatraceIncidentQueryFactory() {
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
