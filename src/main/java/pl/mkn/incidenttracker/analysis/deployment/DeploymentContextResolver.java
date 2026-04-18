package pl.mkn.incidenttracker.analysis.deployment;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class DeploymentContextResolver {

    private static final Pattern IMAGE_PATTERN = Pattern.compile(
            ".*/(?<namespace>[a-z0-9-]+-(?<environment>dev\\d+))/(?<project>[^:]+):"
                    + "(?<build>\\d{8}-\\d{6}-\\d+)-(?<branchSlug>[a-z0-9][a-z0-9-]*)-(?<sha>[0-9a-f]{40})$"
    );
    private static final Pattern SPECIAL_NAMESPACE_PATTERN = Pattern.compile(
            "^[a-z0-9-]+-(?<environment>(?:zt|uat)\\d+)$"
    );
    private static final String SPECIAL_ENVIRONMENT_BRANCH = "release-candidate";

    public ResolvedDeploymentContext resolve(ElasticLogEvidenceView.LogEntry logEntry) {
        var standardDeployment = resolveFromImage(logEntry);
        if (standardDeployment != null) {
            return standardDeployment;
        }

        return resolveFromSpecialNamespace(logEntry);
    }

    private ResolvedDeploymentContext resolveFromImage(ElasticLogEvidenceView.LogEntry logEntry) {
        if (logEntry == null || !StringUtils.hasText(logEntry.containerImage())) {
            return null;
        }

        var matcher = IMAGE_PATTERN.matcher(logEntry.containerImage().trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }

        return new ResolvedDeploymentContext(
                matcher.group("environment"),
                normalizeBranch(matcher.group("branchSlug")),
                firstNonBlank(logEntry.containerName(), matcher.group("project")),
                logEntry.containerName(),
                logEntry.containerImage(),
                matcher.group("sha")
        );
    }

    private ResolvedDeploymentContext resolveFromSpecialNamespace(ElasticLogEvidenceView.LogEntry logEntry) {
        if (logEntry == null || !StringUtils.hasText(logEntry.namespace())) {
            return null;
        }

        var matcher = SPECIAL_NAMESPACE_PATTERN.matcher(logEntry.namespace().trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }

        return new ResolvedDeploymentContext(
                matcher.group("environment"),
                SPECIAL_ENVIRONMENT_BRANCH,
                firstNonBlank(logEntry.containerName(), extractProjectNameFromContainerImage(logEntry.containerImage())),
                logEntry.containerName(),
                logEntry.containerImage(),
                null
        );
    }

    private String normalizeBranch(String branchSlug) {
        return StringUtils.hasText(branchSlug) ? branchSlug.replaceFirst("-", "/") : null;
    }

    private String extractProjectNameFromContainerImage(String containerImage) {
        if (!StringUtils.hasText(containerImage)) {
            return null;
        }

        var trimmed = containerImage.trim();
        var lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == trimmed.length() - 1) {
            return null;
        }

        var tail = trimmed.substring(lastSlash + 1);
        var tagSeparator = tail.indexOf(':');
        var projectName = tagSeparator >= 0 ? tail.substring(0, tagSeparator) : tail;
        return StringUtils.hasText(projectName) ? projectName : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
