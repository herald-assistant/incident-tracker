package pl.mkn.tdw.features.releasesummary.context;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison;

import java.util.List;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "release-summary.diff")
@Validated
@Getter
@Setter
public class ReleaseSummaryDiffProperties {
    @NotEmpty
    private List<@NotBlank String> pathContainsAny = List.of("sql", "changelog");

    public boolean matches(GitLabRepositoryComparison.Change change) {
        return matches(change.oldPath()) || matches(change.newPath());
    }

    private boolean matches(String path) {
        if (path == null) return false;
        var normalized = path.toLowerCase(Locale.ROOT);
        return pathContainsAny.stream()
                .map(keyword -> keyword.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }
}
