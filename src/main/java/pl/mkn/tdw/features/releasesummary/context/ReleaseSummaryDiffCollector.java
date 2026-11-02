package pl.mkn.tdw.features.releasesummary.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryScope;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryComparisonPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison;
import java.util.ArrayList;
import static pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence.*;

@Service
@RequiredArgsConstructor
public class ReleaseSummaryDiffCollector {
    private final GitLabRepositoryComparisonPort comparisons;
    private final ReleaseSummaryDiffProperties properties;
    public ReleaseSummaryEvidence collect(ReleaseSummaryScope scope) {
        var changes=new ArrayList<ChangedFile>();var gaps=new ArrayList<Gap>();
        for(var repo:scope.repositories()) try {
            var comparison=comparisons.compare(scope.group(),repo.project(),repo.baselineCommit(),repo.targetCommit());
            if(comparison==null||!repo.baselineCommit().equals(comparison.baselineCommit())
                    ||!repo.targetCommit().equals(comparison.targetCommit())||!comparison.fileListComplete())
                throw new IllegalStateException("Unverified comparison manifest.");
            for(var change:comparison.changes()) {
                if(repo.prefixes().stream().noneMatch(prefix->inScope(change.oldPath(),prefix)||inScope(change.newPath(),prefix)))continue;
                if(!properties.matches(change))continue;
                changes.add(new ChangedFile(repo.repositoryId(),repo.project(),change));
                if(!change.patchComplete())gaps.add(new Gap("PATCH_INCOMPLETE",repo.repositoryId(),"COMPARISON",
                        change.newPath()!=null?change.newPath():change.oldPath(),
                        "Patch nie zawiera pelnej zmiany. AI moze odczytac plik na przypietym SHA.",false));
            }
        } catch(RuntimeException exception) {
            gaps.add(new Gap("CHANGE_LIST_UNAVAILABLE",repo.repositoryId(),"COMPARISON","",
                    "Nie mozna potwierdzic kompletnej listy zmian Git dla repozytorium.",true));
        }
        return new ReleaseSummaryEvidence(changes,gaps,gaps.stream().noneMatch(Gap::blocking));
    }
    private boolean inScope(String path,String prefix) {
        return path!=null&&(prefix.isEmpty()||path.equals(prefix)||path.startsWith(prefix+"/"));
    }
}