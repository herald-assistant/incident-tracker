package pl.mkn.tdw.features.releasesummary.job;

import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryScope;
import pl.mkn.tdw.shared.evidence.*;
import java.util.*;

public final class ReleaseSummaryProgressEvidence {
    public static final AnalysisEvidenceReference FILES=new AnalysisEvidenceReference("GitLab","Diff miedzy rewizjami");
    public static final AnalysisEvidenceReference GAPS=new AnalysisEvidenceReference("GitLab","Ograniczenia porownania");
    private ReleaseSummaryProgressEvidence() {}
    public static List<AnalysisEvidenceSection> sections(ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        var items=new ArrayList<AnalysisEvidenceItem>();
        for(var file:evidence.changes()) {
            var change=file.change();var attributes=new ArrayList<AnalysisEvidenceAttribute>();
            attributes.add(attribute("Rodzaj zmiany",change.changeType()));
            attributes.add(attribute("Poprzednia sciezka",Objects.toString(change.oldPath(),"Brak pliku")));
            attributes.add(attribute("Nowa sciezka",Objects.toString(change.newPath(),"Brak pliku")));
            attributes.add(attribute("Tryb przed",Objects.toString(change.oldMode(),"")));
            attributes.add(attribute("Tryb po",Objects.toString(change.newMode(),"")));
            scope.repositories().stream().filter(repo->repo.repositoryId().equals(file.repositoryId())).findFirst().ifPresent(repo->{
                attributes.add(attribute("Punkt wejscia",repo.baselineRef()+" @ "+repo.baselineCommit()));
                attributes.add(attribute("Stan docelowy",repo.targetRef()+" @ "+repo.targetCommit()));
            });
            attributes.add(attribute("Kompletnosc patcha",change.patchComplete()?"Dostepny patch; nie jest pelna trescia pliku.":"Niepotwierdzona. Dostepny celowany odczyt przez AI."));
            if(change.patch()!=null)attributes.add(attribute("Patch",change.patch()));
            items.add(new AnalysisEvidenceItem(file.repositoryId()+" / "+Objects.toString(change.newPath(),change.oldPath()),attributes));
        }
        var sections=new ArrayList<AnalysisEvidenceSection>();
        sections.add(new AnalysisEvidenceSection(FILES.provider(),FILES.category(),items));
        if(!evidence.gaps().isEmpty())sections.add(new AnalysisEvidenceSection(GAPS.provider(),GAPS.category(),evidence.gaps().stream().map(gap->
                new AnalysisEvidenceItem(gap.repositoryId()+" / "+gap.path(),List.of(attribute("Ograniczenie",gap.detail()),
                        attribute("Blokuje AI",gap.blocking()?"Tak":"Nie")))).toList()));
        return List.copyOf(sections);
    }
    private static AnalysisEvidenceAttribute attribute(String name,String value){return new AnalysisEvidenceAttribute(name,value);}
}