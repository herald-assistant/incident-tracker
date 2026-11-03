package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryCatalog;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.integrations.gitlab.*;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import java.time.*;
import java.util.*;
import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryCatalogTest {
    @Test void resolvesBothBoundariesOnOnePinnedHeadAndPreservesCatalogPayloadAndPrefixes() {
        var f=new Fixture(List.of(entry("primary",1,"src"), entry("primary",1,"db"),entry("shared-library",1,"other")));
        var range=f.service.preview("crm",selection());
        var scope=f.service.pin(new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,range,null,null),f.session);
        assertEquals(1,scope.repositories().size());
        var repo=scope.repositories().get(0);
        assertEquals(List.of("src","db"),repo.prefixes());
        assertEquals(range.baseline().id(),repo.baselineCommit());assertEquals(range.target().id(),repo.targetCommit());
        assertEquals(range.target().id(),repo.targetRef());
        assertEquals("Obsługa klientów",((Map<?,?>)scope.entities().get(0).get("payload")).get("summary"));
        verify(f.history,times(2)).latestBefore("CRM","customer","c".repeat(40),Instant.parse("2026-09-30T22:00:00Z"));
        verify(f.history,times(2)).latestBefore("CRM","customer","c".repeat(40),Instant.parse("2026-10-31T23:00:00Z"));
        verify(f.revisions,times(2)).resolveRevision("CRM","customer","refs/heads/release");
    }
    @Test void branchPaginationUsesOnlyOnePrimaryRepository() {
        var f=new Fixture(List.of(entry("primary",1,"")));
        when(f.refs.listRefs("CRM/customer",GitLabRef.Kind.BRANCH,"rel",2)).thenReturn(new GitLabRefPage(
                List.of(new GitLabRefOption(new GitLabRef(GitLabRef.Kind.BRANCH,"release"),"2026-10-29T10:18:00Z",true)),3));
        var page=f.service.branches("crm","rel",2);
        assertEquals(3,page.nextPage());assertTrue(page.truncated());assertEquals("refs/heads/release",page.branches().get(0).name());
        assertEquals(List.of("customer"),page.branches().get(0).repositories());
        verifyNoInteractions(f.history,f.revisions);
    }
    @ParameterizedTest @CsvSource({"primary,1,true","shared-library,1,false","primary,2,false","primary,0,false",
            "primary,,false",",1,false",",,false","supporting-library,1,false","supporting-contracts,1,false"})
    void filtersRolesAndPrioritiesConsistently(String role,Integer priority,boolean included) {
        var row=new HashMap<String,Object>();row.put("repoId","customer");
        if(role!=null)row.put("role",role);if(priority!=null)row.put("priority",priority);
        var f=new Fixture(List.of(row));
        assertEquals(included?1:0,f.service.applications().size());
        if(included) assertEquals("customer",f.service.preview("crm",selection()).repositoryId());
        else {
            assertEquals("NO_REPOSITORIES",assertThrows(ReleaseSummaryException.class,()->f.service.preview("crm",selection())).code());
            assertEquals("NO_REPOSITORIES",assertThrows(ReleaseSummaryException.class,()->f.service.branches("crm","",1)).code());
            verifyNoInteractions(f.history,f.revisions,f.refs);
        }
    }
    @Test void refusesMultiplePrimaryRepositoriesRatherThanPickingOne() {
        var f=new Fixture(List.of(entry("primary",1,""),Map.of("repoId","crm-data","role","primary","priority",1)));
        assertEquals("SINGLE_PRIMARY_REQUIRED",assertThrows(ReleaseSummaryException.class,()->f.service.preview("crm",selection())).code());
        verifyNoInteractions(f.history,f.revisions);
    }
    @Test void noEarlierCommitIsAnExplicitGapAndIdenticalCommitsAreVisible() {
        var f=new Fixture(List.of(entry("primary",1,"")));
        doReturn(Optional.empty()).when(f.history).latestBefore(anyString(),anyString(),anyString(),any());
        assertEquals("BASELINE_NOT_FOUND",assertThrows(ReleaseSummaryException.class,()->f.service.preview("crm",selection())).code());
        doReturn(Optional.of(ReleaseSummaryCrmFixture.range().baseline())).when(f.history).latestBefore(anyString(),anyString(),anyString(),any());
        assertTrue(f.service.preview("crm",selection()).noChanges());
    }
    @Test void changingBranchHistoryCannotSilentlyChangeThePreviewedCommits() {
        var f=new Fixture(List.of(entry("primary",1,"")));
        var range=f.service.preview("crm",selection());
        when(f.history.latestBefore(eq("CRM"),eq("customer"),anyString(),eq(Instant.parse("2026-10-31T23:00:00Z"))))
                .thenReturn(Optional.of(new GitLabCommit("d".repeat(40),Instant.parse("2026-10-30T10:00:00Z"),"Zmiana kontaktu klienta")));
        assertEquals("RANGE_CHANGED",assertThrows(ReleaseSummaryException.class,()->f.service.pin(
                new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,range,null,null),f.session)).code());
    }
    @Test void newerCommitsOutsideThePeriodDoNotInvalidateThePreview() {
        var f=new Fixture(List.of(entry("primary",1,"")));
        var range=f.service.preview("crm",selection());
        when(f.revisions.resolveRevision("CRM","customer","refs/heads/release"))
                .thenReturn(new GitLabRepositoryRevision("CRM","customer","refs/heads/release","d".repeat(40),null));
        var scope=f.service.pin(new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,range,null,null),f.session);
        assertEquals(range.target().id(),scope.repositories().get(0).targetCommit());
        verify(f.history).latestBefore("CRM","customer","d".repeat(40),Instant.parse("2026-10-31T23:00:00Z"));
    }
    @Test void usesLocalMidnightsOnTheDayOfDaylightSavingTransitionAndIncludesTheEndDate() {
        var f=new Fixture(List.of(entry("primary",1,"")));
        f.service.preview("crm",new ReleaseSummaryDateSelection("release",LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-25"),"Europe/Warsaw"));
        verify(f.history).latestBefore("CRM","customer","c".repeat(40),Instant.parse("2026-10-24T22:00:00Z"));
        verify(f.history).latestBefore("CRM","customer","c".repeat(40),Instant.parse("2026-10-25T23:00:00Z"));
    }
    @Test void validatesBranchDatesAndZone() {
        assertEquals(selection(),new ReleaseSummaryDateSelection(selection().branch(),selection().dateFrom(),selection().dateTo(),selection().timeZone()));
        assertThrows(IllegalArgumentException.class,()->new ReleaseSummaryDateSelection("../main",LocalDate.now(),LocalDate.now(),"UTC"));
        assertThrows(IllegalArgumentException.class,()->new ReleaseSummaryDateSelection("main",LocalDate.parse("2026-10-02"),LocalDate.parse("2026-10-01"),"UTC"));
        assertThrows(DateTimeException.class,()->new ReleaseSummaryDateSelection("main",LocalDate.now(),LocalDate.now(),"unknown"));
    }
    private static ReleaseSummaryDateSelection selection(){return ReleaseSummaryCrmFixture.range().selection();}
    private static Map<String,Object> entry(String role,int priority,String prefix){return Map.of("repoId","customer","role",role,"priority",priority,"pathPrefixes",List.of(prefix));}
    private static class Fixture {
        final GitLabRepositoryRevisionPort revisions=mock(GitLabRepositoryRevisionPort.class);
        final GitLabRepositoryHistoryPort history=mock(GitLabRepositoryHistoryPort.class);
        final GitLabRepositoryRefPort refs=mock(GitLabRepositoryRefPort.class);
        final OperationalContextReadSession session;
        final ReleaseSummaryCatalog service;
        Fixture(List<Map<String,Object>> entries) {
            var catalog=new OperationalContextCatalog(null,null,List.of(system(Map.of("id","crm","name","CRM","summary","Obsługa klientów"))),null,
                    List.of(repository(Map.of("id","customer","git",Map.of("provider","gitlab","projectPath","CRM/customer"))),
                            repository(Map.of("id","crm-data","git",Map.of("provider","gitlab","projectPath","CRM/data")))),
                    List.of(repositorySearchScope(Map.of("id","crm-code","target",Map.of("type","system","id","crm"),"repositories",entries))),null,null,null,null,null);
            session=OperationalContextReadSession.fromSnapshot(new OperationalContextSnapshot("digest","local",catalog));
            var context=mock(OperationalContextPort.class);when(context.capture()).thenReturn(session);
            var settings=mock(GitLabSettingsPort.class);when(settings.getGroup()).thenReturn("CRM");
            service=new ReleaseSummaryCatalog(context,settings,refs,revisions,history);
            when(revisions.resolveRevision("CRM","customer","refs/heads/release"))
                    .thenReturn(new GitLabRepositoryRevision("CRM","customer","refs/heads/release","c".repeat(40),null));
            when(history.latestBefore(anyString(),anyString(),anyString(),any())).thenAnswer(i->Optional.of(
                    ((Instant)i.getArgument(3)).isBefore(Instant.parse("2026-10-26T00:00:00Z"))?
                            ReleaseSummaryCrmFixture.range().baseline():ReleaseSummaryCrmFixture.range().target()));
        }
    }
}
