package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import java.time.Instant;
import java.util.List;

public record DynatraceIncidentEvidence(
        List<ServiceMatch> serviceMatches,
        List<ProblemSummary> problems,
        List<MetricSummary> metrics
) {

    public DynatraceIncidentEvidence {
        serviceMatches = serviceMatches != null ? List.copyOf(serviceMatches) : List.of();
        problems = problems != null ? List.copyOf(problems) : List.of();
        metrics = metrics != null ? List.copyOf(metrics) : List.of();
    }

    public static DynatraceIncidentEvidence empty() {
        return new DynatraceIncidentEvidence(List.of(), List.of(), List.of());
    }

    public record ServiceMatch(
            String entityId,
            String displayName,
            int matchScore,
            List<String> matchedNamespaces,
            List<String> matchedPods,
            List<String> matchedContainers,
            List<String> matchedServiceNames
    ) {

        public ServiceMatch {
            matchedNamespaces = matchedNamespaces != null ? List.copyOf(matchedNamespaces) : List.of();
            matchedPods = matchedPods != null ? List.copyOf(matchedPods) : List.of();
            matchedContainers = matchedContainers != null ? List.copyOf(matchedContainers) : List.of();
            matchedServiceNames = matchedServiceNames != null ? List.copyOf(matchedServiceNames) : List.of();
        }
    }

    public record ProblemSummary(
            String problemId,
            String displayId,
            String title,
            String impactLevel,
            String severityLevel,
            String status,
            Instant startTime,
            Instant endTime,
            String rootCauseEntityId,
            String rootCauseEntityName,
            List<String> affectedEntities,
            List<String> impactedEntities,
            List<ProblemEvidence> evidenceDetails
    ) {

        public ProblemSummary {
            affectedEntities = affectedEntities != null ? List.copyOf(affectedEntities) : List.of();
            impactedEntities = impactedEntities != null ? List.copyOf(impactedEntities) : List.of();
            evidenceDetails = evidenceDetails != null ? List.copyOf(evidenceDetails) : List.of();
        }
    }

    public record ProblemEvidence(
            String evidenceType,
            String displayName,
            String entityName,
            String groupingEntityName,
            boolean rootCauseRelevant,
            String eventType,
            String metricId,
            String unit,
            Double valueBeforeChangePoint,
            Double valueAfterChangePoint,
            Instant startTime,
            Instant endTime
    ) {
    }

    public record MetricSummary(
            String entityId,
            String entityDisplayName,
            String metricId,
            String metricLabel,
            String unit,
            String resolution,
            Instant queryFrom,
            Instant queryTo,
            int nonNullPoints,
            Double minValue,
            Double maxValue,
            Double averageValue,
            Double lastValue
    ) {
    }

}
