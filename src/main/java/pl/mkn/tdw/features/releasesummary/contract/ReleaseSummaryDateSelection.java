package pl.mkn.tdw.features.releasesummary.contract;

import jakarta.validation.constraints.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import java.time.LocalDate;
import java.time.ZoneId;

public record ReleaseSummaryDateSelection(
        @NotBlank @Size(max=255) String branch,
        @NotNull LocalDate dateFrom, @NotNull LocalDate dateTo,
        @NotBlank @Size(max=100) String timeZone) {
    public ReleaseSummaryDateSelection {
        if (branch == null || dateFrom == null || dateTo == null || timeZone == null || dateFrom.isAfter(dateTo))
            throw new IllegalArgumentException("Wybierz branch i poprawny zakres dat Od/Do.");
        branch = new GitLabRef(GitLabRef.Kind.BRANCH, branch.startsWith("refs/heads/") ? branch.substring(11) : branch).canonical();
        ZoneId.of(timeZone);
        dateTo.plusDays(1);
    }
}
