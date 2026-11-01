package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.job.ReleaseSummaryProgressEvidence;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseSummaryProgressEvidenceTest {
    @Test void showsFullSourceOfAddedModifiedAndRemovedFiles() {
        for(var texts:List.of(new String[]{null,ReleaseSummaryCrmFixture.XML},new String[]{ReleaseSummaryCrmFixture.XML,null},
                new String[]{ReleaseSummaryCrmFixture.XML,ReleaseSummaryCrmFixture.XML.replace("varchar(40)","varchar(80)")})) {
            var evidence=ReleaseSummaryCrmFixture.evidence(ReleaseSummaryCrmFixture.PATH,texts[0],texts[1]);
            var section=ReleaseSummaryProgressEvidence.sections(ReleaseSummaryCrmFixture.scope(),evidence).get(0);
            assertEquals(1,section.items().size());
            var values=attributes(section.items().get(0).attributes());
            assertEquals(texts[0]==null?"":texts[0],values.get("beforeContent"));
            assertEquals(texts[1]==null?"":texts[1],values.get("afterContent"));
            assertTrue(values.get("Punkt wejścia").contains(ReleaseSummaryCrmFixture.BEFORE));
            assertTrue(values.get("Stan docelowy").contains(ReleaseSummaryCrmFixture.AFTER));
        }
    }

    @Test void groupsParserUnitsPerFileWithoutMixingRepositoriesAndExplainsUnchangedDependency() {
        var original=ReleaseSummaryCrmFixture.evidence();
        var unit=original.units().get(0);
        var source=original.sources().get(0);
        var other=new ReleaseSummaryEvidence.Unit("other","crm-archive",unit.path(),"ADDED",null,source.sourceRef(),unit.elements(),true);
        var dependency=new ReleaseSummaryEvidence.Unit("dep",unit.repositoryId(),"db/master.xml","DEPENDENCY_CHANGED",source.sourceRef(),source.sourceRef(),unit.elements(),true);
        var evidence=new ReleaseSummaryEvidence(original.sources(),List.of(unit,unit,other,dependency),List.of(),List.of(),3,true);
        var items=ReleaseSummaryProgressEvidence.sections(ReleaseSummaryCrmFixture.scope(),evidence).get(0).items();
        assertEquals(3,items.size());
        assertNotEquals(items.get(0).title(),items.get(1).title());
        var values=attributes(items.get(2).attributes());
        assertEquals(values.get("beforeContent"),values.get("afterContent"));
        assertTrue(values.get("Uwagi").contains("Treść pliku bez zmian"));
    }

    @Test void missingReadNeverLooksLikeAnEmptyOrRemovedFile() {
        var original=ReleaseSummaryCrmFixture.evidence();
        var unavailable=new ReleaseSummaryEvidence(List.of(),original.units(),List.of(),List.of(),0,false);
        var values=attributes(ReleaseSummaryProgressEvidence.sections(ReleaseSummaryCrmFixture.scope(),unavailable).get(0).items().get(0).attributes());
        assertFalse(values.containsKey("afterContent"));
        assertTrue(values.get("Odczyt — stan docelowy").contains("niedostępna"));
    }
    private Map<String,String> attributes(List<AnalysisEvidenceAttribute> values) {
        return values.stream().collect(Collectors.toMap(AnalysisEvidenceAttribute::name,AnalysisEvidenceAttribute::value));
    }
}
