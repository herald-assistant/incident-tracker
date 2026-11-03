package pl.mkn.tdw.features.releasesummary.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabCommit;

public record ReleaseSummaryRange(
        @NotNull @Valid ReleaseSummaryDateSelection selection,
        @NotBlank String repositoryId,
        @NotNull @Valid GitLabCommit baseline,
        @NotNull @Valid GitLabCommit target) {
    public ReleaseSummaryRange {
        if (selection == null || repositoryId == null || repositoryId.isBlank() || baseline == null || target == null)
            throw new IllegalArgumentException("Brak podglądu obu commitów dla zakresu dat.");
    }
    public boolean noChanges() { return baseline.id().equals(target.id()); }
}
