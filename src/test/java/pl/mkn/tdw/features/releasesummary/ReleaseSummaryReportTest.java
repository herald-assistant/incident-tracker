package pl.mkn.tdw.features.releasesummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.report.*;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.shared.ai.report.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryReportTest {
    static final String SOURCE="gitlab:CRM/customer@"+"b".repeat(40)+":db/customer.xml";
    static final ReleaseSummaryScope SCOPE=new ReleaseSummaryScope("crm","CRM","CRM","digest",
            List.of(new ReleaseSummaryScope.Repository("customer","customer",List.of(""),"a".repeat(40),"a".repeat(40),"b".repeat(40),"b".repeat(40))),
            List.of(Map.of("type","system","id","crm","payload",Map.of("name","CRM"))));
    static ReleaseSummaryEvidence evidence(boolean completePatch) {
        var base=ReleaseSummaryCrmFixture.evidence("db/customer.xml",null,"<databaseChangeLog/>");
        if(completePatch)return base;
        var change=base.changes().get(0).change();
        return new ReleaseSummaryEvidence(List.of(new ReleaseSummaryEvidence.ChangedFile("customer","customer",
                new pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison.Change(change.oldPath(),change.newPath(),change.oldBlobId(),change.newBlobId(),change.oldMode(),change.newMode(),null,false))),
                List.of(new ReleaseSummaryEvidence.Gap("PATCH_INCOMPLETE","customer","COMPARISON","db/customer.xml","Odczytaj zrodlo.",false)),true);
    }
    static ReleaseSummaryReportService reports(ObjectMapper mapper) {
        var settings=mock(GitLabSettingsPort.class);when(settings.getBaseUrl()).thenReturn("https://gitlab.example.com");
        return new ReleaseSummaryReportService(mapper,settings);
    }
    static Set<String> sources(){return Set.of(SOURCE,"opctx:system:crm");}
    static AnalysisReport completed(ReleaseSummaryReportService reports,boolean confirmedResearch) {
        var meta=new AnalysisReportMeta(List.of(new AnalysisReportReference("source","Definicja pola",reports.sourceLinks(Set.of(SOURCE)).get(SOURCE),"CRM")),null,null,
                confirmedResearch?List.of():List.of("Nie ustalono zastosowania pola."),null,null);
        return new AnalysisReport("report","Zmiany danych","CRM","Dodanie etykiety klienta.",List.of(
                new AnalysisReportSection("OVERVIEW","Podsumowanie",1,"Wdrozenie dodaje opcjonalna etykiete klienta do wyszukiwania.",AnalysisReportMeta.empty()),
                new AnalysisReportSection("TABLE_LIST","Tabele",2,ReleaseSummaryTables.table(List.of("Tabela","Zakres zmian"),List.of(List.of("customer","Etykieta klienta"))),AnalysisReportMeta.empty()),
                new AnalysisReportSection("DB_DETAIL_customer","customer",3,ReleaseSummaryTables.table(ReleaseSummaryReportService.DETAIL,List.of(List.of("Kolumna customer.label",
                        "Dodanie. typ: varchar(20); NULL dozwolony; wartosc domyslna: nie okreslono","Oznaczenie klienta sluzy do wyszukiwania i jest zapisywane przy edycji jego karty."))),meta),
                new AnalysisReportSection("APPENDIX","Warunki",1000000,"Nowe pole nie wymaga uzupelnienia historycznych rekordow.",AnalysisReportMeta.empty())),AnalysisReportMeta.empty());
    }
    final ReleaseSummaryReportService reports=reports(new ObjectMapper());
    @Test void diffDoesNotRegisterAFullFileCitationAndUnreadSourcesAreRejected() {
        var evidence=evidence(true);var known=reports.initialSources(SCOPE,evidence);var section=detail(completed(reports,true));
        assertEquals(Set.of("opctx:system:crm"),known);
        assertThrows(IllegalArgumentException.class,()->reports.validatePublication(section.id(),section.markdown(),section.meta(),evidence,known));
        assertDoesNotThrow(()->reports.validatePublication(section.id(),section.markdown(),section.meta(),evidence,sources()));
    }
    @Test void publicationHasHonestAiCoverageWithoutInventedExpectedElementCounts() {
        var projected=reports.project(completed(reports,true),evidence(false),sources());
        assertEquals("COMPLETED",projected.status());assertEquals("AI_ASSESSED",projected.semanticCoverage());
        assertEquals(1,projected.changedFiles());assertEquals(1,projected.documentedTables());assertEquals(1,projected.readSources());
        assertFalse(projected.visibilityLimits().isEmpty());
        assertEquals("PARTIAL",reports.project(completed(reports,false),evidence(true),sources()).status());
        var seed=reports.scaffold("report",SCOPE,evidence(true));assertTrue(seed.sections().isEmpty());
        assertEquals("PARTIAL",reports.project(seed,evidence(true),sources()).status());
    }
    @Test void indexAndDetailNamesMustAgreeAndSectionOrdersMustBeValid() {
        var complete=completed(reports,true);var changed=complete.sections().stream().map(section->section.id().equals("TABLE_LIST")?
                new AnalysisReportSection(section.id(),section.title(),section.order(),section.markdown().replace("customer","invented"),section.meta()):section).toList();
        assertEquals("PARTIAL",reports.project(new AnalysisReport("report","CRM","CRM","",changed,AnalysisReportMeta.empty()),evidence(true),sources()).status());
        for(var id:List.of("UNKNOWN","DB_DETAIL_../customer","DB_DETAIL_"))assertThrows(IllegalArgumentException.class,()->reports.validateOrder(id,3,evidence(true)));
        assertThrows(IllegalArgumentException.class,()->reports.validateOrder("APPENDIX",4,evidence(true)));
    }
    @Test void noChangesRequiresACompleteEmptyManifestNotMissingPatches() {
        assertFalse(evidence(false).noChanges());
        var empty=new ReleaseSummaryEvidence(List.of(),List.of(),true);
        assertEquals("NO_CHANGES",reports.project(reports.noChanges("report",SCOPE),empty,Set.of()).status());
        assertFalse(new ReleaseSummaryEvidence(List.of(),List.of(),false).noChanges());
    }
    @Test void malformedTablesJargonPlaceholderAndInlineLinksAreRejected() {
        var section=detail(completed(reports,true));
        for(var bad:List.of("DAO odczytuje DTO.","Opis w changelogu.","Zobacz [zrodlo](https://gitlab.example.com).","DO_UZUPELNIENIA_1")) {
            var text=section.markdown().replace("Oznaczenie klienta sluzy do wyszukiwania i jest zapisywane przy edycji jego karty.",bad);
            assertThrows(IllegalArgumentException.class,()->reports.validateSection(section.id(),text,evidence(true),sources()));
        }
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(section.id(),section.markdown()+"\nALTER TABLE customer SET UNLOGGED",evidence(true),sources()));
        assertThrows(IllegalArgumentException.class,()->ReleaseSummaryTables.parse("| x | y |\n| --- | --- |\n| a |"));
        assertTrue(ReleaseSummaryTables.parse("```\n| x | y |\n| --- | --- |\n| a | b |\n```").isEmpty());
        assertEquals(List.of("a|b","c"),ReleaseSummaryTables.parse("| x | y |\n| --- | --- |\n| a\\|b | c |").get(0).rows().get(0));
    }
    @Test void sourceLinksPinRevisionEncodePathsAndRejectUnregisteredTargets() {
        var ref="gitlab:CRM/customer@"+"b".repeat(40)+":db/customer labels (new).xml";
        assertEquals("https://gitlab.example.com/CRM/customer/-/blob/"+"b".repeat(40)+"/db/customer%20labels%20%28new%29.xml",reports.sourceLinks(Set.of(ref)).get(ref));
        assertThrows(IllegalArgumentException.class,()->reports.validateMeta(new AnalysisReportMeta(List.of(new AnalysisReportReference("source","CRM","https://unknown.example.com","")),null,null,null,null,null),sources()));
    }
    @Test void largeTablesPreserveEveryEscapedRow() {
        var rows=new ArrayList<List<String>>();for(int index=0;index<2000;index++)rows.add(List.of("crm-"+index,"Opis | "+index));
        assertEquals(rows,ReleaseSummaryTables.parse(ReleaseSummaryTables.table(List.of("id","opis"),rows)).get(0).rows());
    }
    static AnalysisReportSection detail(AnalysisReport report){return report.sections().stream().filter(section->section.id().startsWith("DB_DETAIL")).findFirst().orElseThrow();}
}