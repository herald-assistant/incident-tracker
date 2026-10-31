package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryCatalog;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryRequest;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryException;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRefPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevisionPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryRevision;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import java.util.*;
import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryCatalogTest {
    @Test void pinsEveryExplicitRepositoryAndKeepsWholePayload() {
        var system = system(Map.of("id","crm","name","CRM","summary","Obsługa klientów",
                "aliases",List.of("Customer workspace")));
        var repos = List.of(repository(Map.of("id","crm-api","git",Map.of("provider","gitlab","projectPath","CRM/api"))),
                repository(Map.of("id","crm-data","git",Map.of("provider","gitlab","projectPath","CRM/data"))));
        var scopes = List.of(repositorySearchScope(Map.of("id","crm-code","target",Map.of("type","system","id","crm"),
                "repositories",List.of(Map.of("repoId","crm-api","pathPrefixes",List.of("src")),Map.of("repoId","crm-data")))));
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
}
