package pl.mkn.tdw.features.releasesummary.contract;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison;
import java.util.List;

public record ReleaseSummaryEvidence(List<ChangedFile> changes, List<Gap> gaps, boolean changeListComplete) {
    public ReleaseSummaryEvidence {
        changes=List.copyOf(changes); gaps=List.copyOf(gaps);
    }
    public record ChangedFile(String repositoryId,String project,GitLabRepositoryComparison.Change change) {}
    public record Gap(String code,String repositoryId,String side,String path,String detail,boolean blocking) {}
    public boolean noChanges() { return changeListComplete && changes.isEmpty() && gaps.stream().noneMatch(Gap::blocking); }
}

