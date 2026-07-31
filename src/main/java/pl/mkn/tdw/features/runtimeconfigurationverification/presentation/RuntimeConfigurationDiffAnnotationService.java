package pl.mkn.tdw.features.runtimeconfigurationverification.presentation;

import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiObservation;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiObservationType;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationFunctionalImpact;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationFinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuntimeConfigurationDiffAnnotationService {

    private static final int MAX_COMMENT_LENGTH = 280;

    public List<RuntimeConfigurationDiffAnnotation> create(
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationAiSecondOpinion secondOpinion
    ) {
        if (deterministic == null || secondOpinion == null) {
            return List.of();
        }

        var knownDifferenceIds = deterministic.differences().stream()
                .map(difference -> difference.differenceId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var findingsById = new LinkedHashMap<String, RuntimeConfigurationFinding>();
        deterministic.findings().forEach(finding -> findingsById.put(finding.findingId(), finding));

        var annotations = new ArrayList<RuntimeConfigurationDiffAnnotation>();
        secondOpinion.observations().stream()
                .map(observation -> observationAnnotation(
                        observation,
                        knownDifferenceIds,
                        findingsById
                ))
                .filter(java.util.Objects::nonNull)
                .forEach(annotations::add);
        secondOpinion.functionalImpacts().stream()
                .map(impact -> impactAnnotation(impact, knownDifferenceIds, findingsById))
                .filter(java.util.Objects::nonNull)
                .forEach(annotations::add);
        return List.copyOf(annotations);
    }

    private RuntimeConfigurationDiffAnnotation observationAnnotation(
            RuntimeConfigurationAiObservation observation,
            Set<String> knownDifferenceIds,
            Map<String, RuntimeConfigurationFinding> findingsById
    ) {
        var resolvedIds = resolveDifferenceIds(
                observation.differenceIds(),
                observation.findingIds(),
                knownDifferenceIds,
                findingsById
        );
        var comment = shortComment(firstNonBlank(observation.summary(), observation.explanation()));
        if (blank(observation.observationId()) || blank(comment) || resolvedIds.isEmpty()) {
            return null;
        }
        return new RuntimeConfigurationDiffAnnotation(
                observation.observationId(),
                RuntimeConfigurationDiffAnnotationKind.OBSERVATION,
                comment,
                null,
                observation.type() == RuntimeConfigurationAiObservationType.HYPOTHESIS,
                resolvedIds,
                observation.findingIds()
        );
    }

    private RuntimeConfigurationDiffAnnotation impactAnnotation(
            RuntimeConfigurationFunctionalImpact impact,
            Set<String> knownDifferenceIds,
            Map<String, RuntimeConfigurationFinding> findingsById
    ) {
        var resolvedIds = resolveDifferenceIds(
                impact.differenceIds(),
                impact.findingIds(),
                knownDifferenceIds,
                findingsById
        );
        var comment = shortComment(joinNonBlank(impact.affectedFunctionality(), impact.impact()));
        if (blank(impact.impactId()) || blank(comment) || resolvedIds.isEmpty()) {
            return null;
        }
        return new RuntimeConfigurationDiffAnnotation(
                impact.impactId(),
                RuntimeConfigurationDiffAnnotationKind.FUNCTIONAL_IMPACT,
                comment,
                impact.confidence(),
                impact.hypothesis(),
                resolvedIds,
                impact.findingIds()
        );
    }

    private List<String> resolveDifferenceIds(
            List<String> directDifferenceIds,
            List<String> findingIds,
            Set<String> knownDifferenceIds,
            Map<String, RuntimeConfigurationFinding> findingsById
    ) {
        var resolved = new LinkedHashSet<String>();
        addKnown(resolved, directDifferenceIds, knownDifferenceIds);
        for (var findingId : findingIds) {
            var finding = findingsById.get(findingId);
            if (finding != null) {
                addKnown(resolved, finding.differenceIds(), knownDifferenceIds);
            }
        }
        return List.copyOf(resolved);
    }

    private void addKnown(
            Set<String> target,
            List<String> candidates,
            Set<String> knownDifferenceIds
    ) {
        candidates.stream()
                .filter(knownDifferenceIds::contains)
                .forEach(target::add);
    }

    private String joinNonBlank(String first, String second) {
        if (blank(first)) {
            return second;
        }
        if (blank(second)) {
            return first;
        }
        return first + ": " + second;
    }

    private String firstNonBlank(String first, String second) {
        return !blank(first) ? first : second;
    }

    private String shortComment(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= MAX_COMMENT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_COMMENT_LENGTH - 1).stripTrailing() + "…";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
