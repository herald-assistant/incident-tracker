package pl.mkn.tdw.features.releasesummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.report.*;
import pl.mkn.tdw.shared.ai.report.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseSummaryReportTest {
    static final String SOURCE="gitlab:CRM/customer@"+"a".repeat(40)+":db/customer.xml";
    static final ReleaseSummaryScope SCOPE=new ReleaseSummaryScope("crm","CRM","CRM","digest",
            List.of(new ReleaseSummaryScope.Repository("customer","customer",List.of(""),"refs/heads/current","a".repeat(40),"refs/tags/next","b".repeat(40))),List.of(Map.of("type","system","id","crm","payload",Map.of("name","CRM"))));
    static ReleaseSummaryEvidence evidence(boolean structured) {
        return new ReleaseSummaryEvidence(List.of(new ReleaseSummaryEvidence.Source(SOURCE,"customer","customer","BASELINE","a".repeat(40),"db/customer.xml","c".repeat(40),"<databaseChangeLog/>",List.of())),
                List.of(new ReleaseSummaryEvidence.Unit("unit1","customer","db/customer.xml","ADDED",null,SOURCE,
                        List.of(new ReleaseSummaryEvidence.Element("column1","column","/changeSet/column",null,Map.of("name","label","type","varchar(20)","remarks","a|b"))),structured)),
                List.of(),List.of(),1,true);
    }
    final ReleaseSummaryReportService reports=new ReleaseSummaryReportService(new ObjectMapper());
    static AnalysisReport completed(ReleaseSummaryReportService reports,boolean structured) {
        var draft=reports.scaffold("report",SCOPE,evidence(structured));
        var sections=draft.sections().stream().map(s->{
            var text=s.markdown().replace("DO_UZUPELNIENIA","Pole pozwala zapisać etykietę klienta.");
            if(s.id().startsWith("DB_DETAIL")) {
                var rows=ReleaseSummaryTables.parse(text);
                var functional=ReleaseSummaryTables.table(ReleaseSummaryReportService.FUNCTION,
                        List.of(List.of("column1","Oznaczenie klienta","Zapis i odczyt etykiety","Wyszukiwanie","WNIOSEK","[\""+SOURCE+"\"]","NIE_DOTYCZY")));
                text=ReleaseSummaryTables.table(ReleaseSummaryReportService.TECH,rows.get(0).rows())+"\n"+functional;
            }
            if(s.id().equals("COVERAGE"))text=text.replace("EXPECTED",structured?"DOCUMENTED":"AI_IDENTIFIED");
            return new AnalysisReportSection(s.id(),s.title(),s.order(),text,s.meta());
        }).toList();
        return new AnalysisReport(draft.reportId(),draft.header(),draft.subHeader(),draft.markdownSummary(),sections,draft.meta());
    }
    @Test void requiresEveryAttributeBothTablesAndReadSources() {
        var report=completed(reports,true);
        var section=report.sections().get(4);var evidence=evidence(true);
        var known=reports.initialSources(SCOPE,evidence);
        assertDoesNotThrow(()->reports.validateSection(section.id(),section.markdown(),evidence,known));
        assertEquals("COMPLETED",reports.project(report,evidence,known).status());
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(section.id(),section.markdown()
                .replace(",\"type\":\"varchar(20)\"","").replace("\"type\":\"varchar(20)\",",""),evidence,known));
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(section.id(),section.markdown().replace("column1","invented"),evidence,known));
        assertThrows(IllegalArgumentException.class,()->reports.validateSection(section.id(),section.markdown(),evidence,Set.of()));
    }
    @Test void rawSqlAndScaffoldNeverClaimVerifiedCompletion() {
        var evidence=evidence(false);var known=reports.initialSources(SCOPE,evidence);
        assertEquals("PARTIAL",reports.project(completed(reports,false),evidence,known).status());
        assertEquals(1,reports.project(completed(reports,false),evidence,known).documentedElements());
        assertEquals("PARTIAL",reports.project(reports.scaffold("report",SCOPE,evidence),evidence,known).status());
    }
    @Test void parsesEscapedPipesAndRejectsMalformedOrFencedTables() {
        assertEquals(List.of("a|b","c"),ReleaseSummaryTables.parse("| x | y |\n| --- | --- |\n| a\\|b | c |").get(0).rows().get(0));
        assertThrows(IllegalArgumentException.class,()->ReleaseSummaryTables.parse("| x | y |\n| --- | --- |\n| a |"));
        assertTrue(ReleaseSummaryTables.parse("```\n| x | y |\n| --- | --- |\n| a | b |\n```").isEmpty());
    }
}

