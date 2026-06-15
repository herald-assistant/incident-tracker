package pl.mkn.incidenttracker.integrations.gitlab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record GitLabRepositoryEndpointDocumentation(
        String source,
        String summary,
        String description,
        String operationId,
        List<String> tags,
        List<GitLabRepositoryEndpointParameterDocumentation> parameters
) {
    public GitLabRepositoryEndpointDocumentation {
        tags = copyTexts(tags);
        parameters = parameters != null ? List.copyOf(parameters) : List.of();
    }

    public boolean empty() {
        return !hasText(summary)
                && !hasText(description)
                && !hasText(operationId)
                && tags.isEmpty()
                && parameters.isEmpty();
    }

    public GitLabRepositoryEndpointDocumentation merge(GitLabRepositoryEndpointDocumentation other) {
        if (other == null || other.empty()) {
            return this;
        }
        if (empty()) {
            return other;
        }
        return new GitLabRepositoryEndpointDocumentation(
                mergeSource(source, other.source()),
                firstText(summary, other.summary()),
                firstText(description, other.description()),
                firstText(operationId, other.operationId()),
                joinTexts(tags, other.tags()),
                mergeParameters(parameters, other.parameters())
        );
    }

    private static List<GitLabRepositoryEndpointParameterDocumentation> mergeParameters(
            List<GitLabRepositoryEndpointParameterDocumentation> left,
            List<GitLabRepositoryEndpointParameterDocumentation> right
    ) {
        var merged = new ArrayList<GitLabRepositoryEndpointParameterDocumentation>();
        if (left != null) {
            merged.addAll(left);
        }
        for (var candidate : right != null ? right : List.<GitLabRepositoryEndpointParameterDocumentation>of()) {
            var mergedExisting = false;
            for (var index = 0; index < merged.size(); index++) {
                var existing = merged.get(index);
                if (existing.sameParameter(candidate)) {
                    merged.set(index, existing.merge(candidate));
                    mergedExisting = true;
                    break;
                }
            }
            if (!mergedExisting) {
                merged.add(candidate);
            }
        }
        return List.copyOf(merged);
    }

    private static List<String> joinTexts(List<String> left, List<String> right) {
        var values = new LinkedHashSet<String>();
        values.addAll(copyTexts(left));
        values.addAll(copyTexts(right));
        return List.copyOf(values);
    }

    private static List<String> copyTexts(List<String> values) {
        var copied = new LinkedHashSet<String>();
        for (var value : values != null ? values : List.<String>of()) {
            if (hasText(value)) {
                copied.add(value.trim());
            }
        }
        return List.copyOf(copied);
    }

    private static String mergeSource(String left, String right) {
        if (!hasText(left)) {
            return hasText(right) ? right : null;
        }
        if (!hasText(right) || left.equals(right)) {
            return left;
        }
        return left + "+" + right;
    }

    private static String firstText(String left, String right) {
        return hasText(left) ? left : hasText(right) ? right : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
