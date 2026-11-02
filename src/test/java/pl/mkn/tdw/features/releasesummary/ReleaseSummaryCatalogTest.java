package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryCatalog;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryRequest;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryException;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRefPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevisionPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRefPage;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryRevision;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import java.util.*;
import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryCatalogTest {
        @Test void pinsIncludedRepositoriesAndKeepsWholePayload() {
        var system = system(Map.of("id","crm","name","CRM","summary","Obsługa klientów",
                "aliases",List.of("Customer workspace")));
        var repos = List.of(repository(Map.of("id","crm-api","git",Map.of("provider","gitlab","projectPath","CRM/api"))),
                repository(Map.of("id","crm-data","git",Map.of("provider","gitlab","projectPath","CRM/data"))));
        var scopes = List.of(repositorySearchScope(Map.of("id","crm-code","target",Map.of("type","system","id","crm"),
                "repositories",List.of(Map.of("repoId","crm-api","role","primary","priority",1,"pathPrefixes",List.of("src")),
                        Map.of("repoId","crm-data","role","primary","priority",1)))));
        var catalog = new OperationalContextCatalog(null,null,List.of(system),null,repos,scopes,null,null,null,null,null);
        var session=OperationalContextReadSession.fromSnapshot(new OperationalContextSnapshot("digest","local",catalog));
        var settings=mock(GitLabSettingsPort.class); when(settings.getGroup()).thenReturn("CRM");
        var revisions=mock(GitLabRepositoryRevisionPort.class);
        when(revisions.resolveRevision(eq("CRM"),anyString(),anyString())).thenAnswer(i ->
                new GitLabRepositoryRevision("CRM",i.getArgument(1),i.getArgument(2),"a".repeat(40),null));
        var service=new ReleaseSummaryCatalog(mock(OperationalContextPort.class),settings,mock(GitLabRepositoryRefPort.class),revisions);
        var request=new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,
                new GitLabRef(GitLabRef.Kind.BRANCH,"release"),new GitLabRef(GitLabRef.Kind.TAG,"release"),null,null);
        var pinned=service.pin(request,session);
        assertEquals(2,pinned.repositories().size());
        assertEquals(system.payload(),pinned.entities().get(0).get("payload"));
        assertEquals(4,pinned.entities().size());
        assertEquals(List.of("src"),pinned.repositories().get(0).prefixes());
        verify(revisions).resolveRevision("CRM","data","refs/tags/release");
        when(revisions.resolveRevision("CRM","data","refs/tags/release")).thenThrow(new IllegalStateException());
        assertThrows(ReleaseSummaryException.class,()->service.pin(request,session));
        verify(revisions,never()).resolveRevision(anyString(),anyString(),eq("main"));
        reset(revisions);
        var sequence=new java.util.concurrent.atomic.AtomicInteger();
        when(revisions.resolveRevision(eq("CRM"),anyString(),anyString())).thenAnswer(i->new GitLabRepositoryRevision(
                "CRM",i.getArgument(1),i.getArgument(2),Integer.toHexString(sequence.incrementAndGet()).repeat(40),null));
        var same=new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,request.baselineRef(),request.baselineRef(),null,null);
        var stable=service.pin(same,session);
        assertEquals(2,sequence.get());
        stable.repositories().forEach(r->assertEquals(r.baselineCommit(),r.targetCommit()));
    }

    @ParameterizedTest
    @MethodSource("repositorySelectionCases")
    void appliesRoleAndPriorityToApplicationsRefsAndPinning(String role,Integer priority,boolean included) {
        var entry=new LinkedHashMap<String,Object>();
        entry.put("repoId","crm-api");
        if(role!=null) entry.put("role",role);
        if(priority!=null) entry.put("priority",priority);
        var session=selectionSession(List.of(entry));
        var context=mock(OperationalContextPort.class);
        when(context.capture()).thenReturn(session);
        var settings=mock(GitLabSettingsPort.class);
        when(settings.getGroup()).thenReturn("CRM");
        var refs=mock(GitLabRepositoryRefPort.class);
        var revisions=mock(GitLabRepositoryRevisionPort.class);
        var service=new ReleaseSummaryCatalog(context,settings,refs,revisions);
        var commit="a".repeat(40);
        var ref=new GitLabRef(GitLabRef.Kind.COMMIT,commit);
        var request=new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,ref,ref,null,null);
        if(included) {
            when(revisions.resolveRevision("CRM","api",commit))
                    .thenReturn(new GitLabRepositoryRevision("CRM","api",commit,commit,null));
            assertEquals(1,service.applications().get(0).repositoryCount());
            assertEquals(List.of("crm-api"),service.refs("crm",GitLabRef.Kind.COMMIT,commit,1)
                    .branches().get(0).repositories());
            assertEquals(List.of("crm-api"),service.pin(request,session).repositories().stream()
                    .map(repository->repository.repositoryId()).toList());
            verify(revisions,times(2)).resolveRevision("CRM","api",commit);
        } else {
            assertTrue(service.applications().isEmpty());
            assertEquals("NO_REPOSITORIES",assertThrows(ReleaseSummaryException.class,
                    ()->service.refs("crm",GitLabRef.Kind.BRANCH,"",1)).code());
            assertEquals("NO_REPOSITORIES",assertThrows(ReleaseSummaryException.class,
                    ()->service.pin(request,session)).code());
            verifyNoInteractions(revisions);
        }
        verifyNoInteractions(refs);
    }

    static java.util.stream.Stream<Arguments> repositorySelectionCases() {
        return java.util.stream.Stream.of(
                Arguments.of("primary",1,true),Arguments.of("shared-library",1,false),
                Arguments.of("primary",2,false),Arguments.of("shared-library",2,false),
                Arguments.of("primary",0,false),Arguments.of("shared-library",null,false),
                Arguments.of("primary",null,false),Arguments.of("supporting-library",1,false),
                Arguments.of("supporting-contracts",1,false),Arguments.of(null,1,false),
                Arguments.of(null,null,false));
    }

    @Test void mergesOnlyIncludedPrefixesBeforeRepositoryResolution() {
        var session=selectionSession(List.of(
                Map.of("repoId","crm-api","role","primary","priority",1,"pathPrefixes",List.of("src")),
                Map.of("repoId","crm-api","role","primary","priority",1,"pathPrefixes",List.of("db")),
                Map.of("repoId","crm-api","role","shared-library","priority",1),
                Map.of("repoId","crm-api","role","primary","priority",2),
                Map.of("repoId","crm-api","role","supporting-contracts","priority",1,"pathPrefixes",List.of("other")),
                Map.of("repoId","crm-missing","role","supporting-library","priority",1)));
        var context=mock(OperationalContextPort.class);
        when(context.capture()).thenReturn(session);
        var settings=mock(GitLabSettingsPort.class);
        when(settings.getGroup()).thenReturn("CRM");
        var revisions=mock(GitLabRepositoryRevisionPort.class);
        when(revisions.resolveRevision("CRM","api","refs/heads/release"))
                .thenReturn(new GitLabRepositoryRevision("CRM","api","refs/heads/release","a".repeat(40),null));
        var ref=new GitLabRef(GitLabRef.Kind.BRANCH,"release");
        var refs=mock(GitLabRepositoryRefPort.class);
        when(refs.listRefs("CRM/api",GitLabRef.Kind.BRANCH,"release",1))
                .thenReturn(new GitLabRefPage(List.of(new pl.mkn.tdw.integrations.gitlab.contract.GitLabRefOption(ref,null,false)),null));
        var service=new ReleaseSummaryCatalog(context,settings,refs,revisions);
        var scope=service.pin(new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,ref,ref,null,null),session);
        assertEquals(1,service.applications().get(0).repositoryCount());
        assertEquals(1,scope.repositories().size());
        assertEquals(List.of("src","db"),scope.repositories().get(0).prefixes());
        assertEquals(List.of("crm-api"),service.refs("crm",GitLabRef.Kind.BRANCH,"release",1)
                .branches().get(0).repositories());
        verify(refs).listRefs("CRM/api",GitLabRef.Kind.BRANCH,"release",1);
        verifyNoMoreInteractions(refs);
        verify(revisions).resolveRevision("CRM","api","refs/heads/release");
        verifyNoMoreInteractions(revisions);
    }

    @Test void aggregatesNewestCommitWhileKeepingMissingDatesAndPerRepositoryProvenance() {
        var catalog=new OperationalContextCatalog(null,null,List.of(system(Map.of("id","crm","name","CRM"))),null,
            List.of(repository(Map.of("id","crm-api","git",Map.of("provider","gitlab","projectPath","CRM/api"))),
                    repository(Map.of("id","crm-data","git",Map.of("provider","gitlab","projectPath","CRM/data"))),
                    repository(Map.of("id","crm-web","git",Map.of("provider","gitlab","projectPath","CRM/web")))),
            List.of(repositorySearchScope(Map.of("id","crm-code","target",Map.of("type","system","id","crm"),
                "repositories",List.of(Map.of("repoId","crm-api","role","primary","priority",1),
                    Map.of("repoId","crm-data","role","primary","priority",1),
                    Map.of("repoId","crm-web","role","primary","priority",1))))),null,null,null,null,null);
        var context=mock(OperationalContextPort.class);
        when(context.capture()).thenReturn(OperationalContextReadSession.fromSnapshot(new OperationalContextSnapshot("digest","local",catalog)));
        var settings=mock(GitLabSettingsPort.class);when(settings.getGroup()).thenReturn("CRM");
        var refs=mock(GitLabRepositoryRefPort.class);var revisions=mock(GitLabRepositoryRevisionPort.class);
        var ref=new GitLabRef(GitLabRef.Kind.BRANCH,"release");
        var dates=Arrays.asList("2026-11-01T12:00:00Z","2026-11-02T12:00:00Z",null);
        int index=0;
        for(var project:List.of("api","data","web")) {
            var date=dates.get(index++);
            when(refs.listRefs("CRM/"+project,GitLabRef.Kind.BRANCH,"",1)).thenReturn(new GitLabRefPage(List.of(
                new pl.mkn.tdw.integrations.gitlab.contract.GitLabRefOption(ref,date,project.equals("api"))),null));
            when(revisions.resolveRevision(eq("CRM"),eq(project),anyString())).thenAnswer(invocation ->
                new GitLabRepositoryRevision("CRM",project,invocation.getArgument(2),"a".repeat(40),date));
        }
        var service=new ReleaseSummaryCatalog(context,settings,refs,revisions);
        var option=service.refs("crm",GitLabRef.Kind.BRANCH,"",1).branches().get(0);
        assertEquals("2026-11-02T12:00:00Z",option.committedAt());assertTrue(option.isDefault());
        assertEquals(List.of("crm-api","crm-data","crm-web"),option.repositories());
        assertEquals(3,option.repositoryCommits().size());assertNull(option.repositoryCommits().get(2).committedAt());
        verifyNoInteractions(revisions);
        var commitOption=service.refs("crm",GitLabRef.Kind.COMMIT,"a".repeat(40),1).branches().get(0);
        assertEquals(option.committedAt(),commitOption.committedAt());assertEquals(option.repositoryCommits(),commitOption.repositoryCommits());
    }

    private OperationalContextReadSession selectionSession(List<Map<String,Object>> entries) {
        var catalog=new OperationalContextCatalog(null,null,List.of(system(Map.of("id","crm","name","CRM"))),null,
                List.of(repository(Map.of("id","crm-api","git",Map.of("provider","gitlab","projectPath","CRM/api")))),
                List.of(repositorySearchScope(Map.of("id","crm-code","target",Map.of("type","system","id","crm"),
                        "repositories",entries))),null,null,null,null,null);
        return OperationalContextReadSession.fromSnapshot(new OperationalContextSnapshot("digest","local",catalog));
    }
}
