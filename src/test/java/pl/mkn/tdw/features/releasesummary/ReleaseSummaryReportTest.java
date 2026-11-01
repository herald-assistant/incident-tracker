package pl.mkn.tdw.features.releasesummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.releasesummary.context.LiquibaseDocumentParser;
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
            List.of(new ReleaseSummaryScope.Repository("customer","customer",List.of(""),"refs/heads/current","a".repeat(40),"refs/tags/next","b".repeat(40))),List.of(Map.of("type","system","id","crm","payload",Map.of("name","CRM"))));
    static ReleaseSummaryEvidence evidence(boolean structured) {
        var xml="<databaseChangeLog><changeSet id=\"labels\" author=\"crm\"><addColumn tableName=\"customer\"><column name=\"label\" type=\"varchar(20)\" remarks=\"a|b\"><constraints nullable=\"true\"/></column></addColumn></changeSet></databaseChangeLog>";
        return new ReleaseSummaryEvidence(List.of(new ReleaseSummaryEvidence.Source(SOURCE,"customer","customer","TARGET","b".repeat(40),"db/customer.xml","c".repeat(40),xml,List.of())),
                List.of(new ReleaseSummaryEvidence.Unit("unit1","customer","db/customer.xml","ADDED",null,SOURCE,
                        List.of(new ReleaseSummaryEvidence.Element("column1","column","/databaseChangeLog:#1/changeSet:labels#1/addColumn:customer#1/column:label#1",null,Map.of("name","label","type","varchar(20)","remarks","a|b"))),structured)),
                List.of(),List.of(),1,true);
    }
    static ReleaseSummaryReportService reports(ObjectMapper mapper) {
        var settings=mock(GitLabSettingsPort.class);when(settings.getBaseUrl()).thenReturn("https://gitlab.example.com");
        return new ReleaseSummaryReportService(mapper,new ReleaseSummaryReportInventory(new LiquibaseDocumentParser()),settings);
    }
    final ReleaseSummaryReportService reports=reports(new ObjectMapper());
    static AnalysisReport completed(ReleaseSummaryReportService reports,boolean structured) {
        var seed=reports.scaffold("report",SCOPE,evidence(structured));
        var sections=reports.templates(SCOPE,evidence(structured)).stream().map(s->{
            var text=s.markdown().replace(ReleaseSummaryReportService.PENDING,"Wniosek: Oznaczenie klienta służy do wyszukiwania i jest zapisywane przy edycji jego karty.")
                    .replace("Opisz krótko zakres zmian.","Dodanie etykiety klienta.")
                    .replace("Opisz wpływ i ryzyko.","Nowe opcjonalne pole nie wymaga uzupełnienia historycznych rekordów.")
                    .replace("Podaj konkretne sprawdzenia.","Sprawdzić zapis i odczyt etykiety klienta.");
            if(s.id().equals("OVERVIEW"))text="Wdrożenie dodaje opcjonalną etykietę klienta do wyszukiwania. Porównanie: current → next.";
            return new AnalysisReportSection(s.id(),s.title(),s.order(),text,s.meta());
        }).toList();
        return new AnalysisReport(seed.reportId(),seed.header(),seed.subHeader(),"Dodanie etykiety klienta.",sections,seed.meta());
    }
    @Test void requiresEveryDatabaseObjectPreservesDefinitionsAndRejectsUnreadSources() {
        var report=completed(reports,true);var section=detail(report);var evidence=evidence(true);var known=reports.initialSources(SCOPE,evidence);
        assertDoesNotThrow(()->reports.validateSection(section.id(),section.markdown(),evidence,known));
        assertEquals("COMPLETED",reports.project(report,evidence,known).status());
        assertTrue(section.markdown().contains("Kolumna customer.label"));assertTrue(section.markdown().contains("NULL dozwolony"));
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(section.id(),section.markdown().replace("varchar(20)","varchar(21)"),evidence,known));
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(section.id(),section.markdown().replace("customer.label","customer.invented"),evidence,known));
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(section.id(),section.markdown(),evidence,Set.of()));
        assertFalse(section.markdown().contains("column1"));assertFalse(section.markdown().contains("databaseChangeLog"));assertFalse(section.markdown().contains("gitlab:CRM"));
        assertEquals(4,ReleaseSummaryTables.parse(section.markdown()).get(0).headers().size());
    }
    @Test void rawSqlIncompleteResearchAndPrivateSeedNeverClaimCompleteReport() {
        var evidence=evidence(false);var known=reports.initialSources(SCOPE,evidence);
        assertEquals("PARTIAL",reports.project(completed(reports,false),evidence,known).status());
        assertEquals(1,reports.project(completed(reports,false),evidence,known).documentedElements());
        var seed=reports.scaffold("report",SCOPE,evidence);assertTrue(seed.sections().isEmpty());
        assertEquals(0,reports.project(seed,evidence,known).documentedElements());
        var template=reports.templates(SCOPE,evidence).stream().filter(s->s.id().startsWith("DB_DETAIL")).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(template.id(),template.markdown(),evidence,known));
        var completed=completed(reports,false);var sections=completed.sections().stream().map(s->s.id().startsWith("DB_DETAIL")?
                new AnalysisReportSection(s.id(),s.title(),s.order(),s.markdown().replace("Wniosek:","Do wyjaśnienia:"),s.meta()):s).toList();
        assertTrue(reports.project(new AnalysisReport("report","CRM","CRM","",sections,AnalysisReportMeta.empty()),evidence,known)
                .visibilityLimits().stream().anyMatch(s->s.contains("customer.label")));
    }
    @Test void indexCoversAllDetailsAndNoChangesNeedsNoEmptyAiTables() {
        var evidence=evidence(true);var known=reports.initialSources(SCOPE,evidence);var report=completed(reports,true);
        var index=report.sections().stream().filter(s->s.id().equals("CHANGE_INDEX")).findFirst().orElseThrow();
        assertTrue(index.markdown().contains("#report-section-"+detail(report).id()));
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(index.id(),index.markdown().replace("#report-section-","#missing-"),evidence,known));
        var empty=new ReleaseSummaryEvidence(List.of(),List.of(),List.of(),List.of(),28,true);
        var noChanges=reports.noChanges("report",SCOPE);
        assertEquals("NO_CHANGES",reports.project(noChanges,empty,Set.of()).status());
        assertEquals(1,noChanges.sections().size());
    }
    @Test void sourceLinksPinRevisionAndEncodePathsWithoutLongVisibleLabels() {
        var ref="gitlab:CRM/customer@"+"b".repeat(40)+":db/customer labels (new).xml";
        var links=reports.sourceLinks(Set.of(ref));
        assertEquals("https://gitlab.example.com/CRM/customer/-/blob/"+"b".repeat(40)+"/db/customer%20labels%20%28new%29.xml",links.get(ref));
        assertTrue(reports.citations(List.of(ref)).startsWith("[1](https://"));
    }
    @Test void parsesEscapedPipesAndRejectsMalformedOrFencedTables() {
        assertEquals(List.of("a|b","c"),ReleaseSummaryTables.parse("| x | y |\n| --- | --- |\n| a\\|b | c |").get(0).rows().get(0));
        assertThrows(IllegalArgumentException.class,()->ReleaseSummaryTables.parse("| x | y |\n| --- | --- |\n| a |"));
        assertTrue(ReleaseSummaryTables.parse("```\n| x | y |\n| --- | --- |\n| a | b |\n```").isEmpty());
    }
    static AnalysisReportSection detail(AnalysisReport report){return report.sections().stream().filter(s->s.id().startsWith("DB_DETAIL")).findFirst().orElseThrow();}
}
