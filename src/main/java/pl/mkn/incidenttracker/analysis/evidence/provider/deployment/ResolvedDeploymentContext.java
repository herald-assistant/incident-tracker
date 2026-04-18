package pl.mkn.incidenttracker.analysis.evidence.provider.deployment;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;

import java.util.Locale;
import java.util.Objects;

public record ResolvedDeploymentContext(
        String environment,
        String branch,
        String projectNameHint,
        String containerName,
        String containerImage,
        String commitSha
) {

    public String key() {
        return String.join(
                "::",
                Objects.toString(environment, ""),
                Objects.toString(branch, ""),
                Objects.toString(projectNameHint, ""),
                Objects.toString(containerName, ""),
                Objects.toString(containerImage, "")
        );
    }

    public boolean matches(ElasticLogEvidenceView.LogEntry logEntry) {
        if (logEntry == null) {
            return false;
        }

        if (StringUtils.hasText(containerImage)
                && containerImage.equals(logEntry.containerImage())) {
            return true;
        }

        if (!StringUtils.hasText(containerName)
                || !containerName.equals(logEntry.containerName())) {
            return false;
        }

        if (!StringUtils.hasText(environment) || !StringUtils.hasText(logEntry.namespace())) {
            return true;
        }

        return logEntry.namespace()
                .trim()
                .toLowerCase(Locale.ROOT)
                .contains(environment.trim().toLowerCase(Locale.ROOT));
    }
}
