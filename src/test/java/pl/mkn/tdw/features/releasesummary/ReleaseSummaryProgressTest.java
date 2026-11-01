package pl.mkn.tdw.features.releasesummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.api.analysisruns.AnalysisRunHistoryService;
import pl.mkn.tdw.features.releasesummary.ai.*;
import pl.mkn.tdw.features.releasesummary.context.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.job.*;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.localworkspace.storage.*;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryProgressTest {
    @TempDir Path directory;

    @Test void historyIsDurableAndReadableBeforeTheExecutorStartsWork() {
        var f=new ReleaseSummaryContinuationTest.Fixture();
        var mapper=new ObjectMapper().findAndRegisterModules();
        var properties=new LocalWorkspaceProperties();properties.setDirectory(directory.toString());
        var paths=new LocalWorkspacePaths(properties);
        var store=new FileSystemLocalAnalysisRunStore(properties,paths,new LocalWorkspaceJsonFileStore(mapper));
        var persistence=new ReleaseSummaryPersistence(mapper,store);
        var service=new ReleaseSummaryJobService(f.catalog,f.collector,new ReleaseSummaryPrompt(mapper,f.reports),f.reports,
                f.ai,persistence,()->AnalysisAiAuthRef.localToken("CRM"),new LocalAnalysisRunOperationGuard(),task->{
            // Read through a separate store instance before dispatch, not through job memory.
            var reader=new FileSystemLocalAnalysisRunStore(properties,paths,new LocalWorkspaceJsonFileStore(mapper));
            var history=new AnalysisRunHistoryService(reader,List.of(),null);
            var entry=history.listRuns().runs().get(0);
            assertEquals("QUEUED",entry.status());
            assertEquals("crm · current → next",entry.name());
            assertNull(entry.completedAt());
            var saved=persistence.read(reader.findById(entry.analysisId()).orElseThrow());
            assertEquals(List.of("RESOLVE_REFS","COLLECT_LIQUIBASE","PREPARE_PROMPT","AI_REPORT"),
                    saved.steps().stream().map(s->s.code()).toList());
            assertTrue(saved.steps().stream().allMatch(s->s.status().equals("PENDING")&&s.startedAt()==null));
            f.tasks.add(task);
        });
        var accepted=service.start(f.request);
        assertEquals(accepted,service.get(accepted.jobId()));
        verifyNoInteractions(f.catalog,f.collector,f.ai);
        when(f.collector.collect(any())).thenReturn(new ReleaseSummaryEvidence(List.of(),List.of(),List.of(),List.of(),0,true));
        f.tasks.remove(0).run();
        assertEquals("NO_CHANGES",store.listRuns().get(0).status());
    }

    @Test void failedPersistenceDoesNotDispatchAndRejectedDispatchLeavesHistoryEntry() {
        for(boolean rejectDispatch:List.of(false,true)) {
            var f=new ReleaseSummaryContinuationTest.Fixture();
            var persistence=mock(ReleaseSummaryPersistence.class);
            var tasks=new ArrayList<Runnable>();
            if(!rejectDispatch) doThrow(new IllegalStateException("CRM storage failure")).when(persistence).save(any(),any(),any());
            var service=new ReleaseSummaryJobService(f.catalog,f.collector,new ReleaseSummaryPrompt(f.mapper,f.reports),f.reports,
                    f.ai,persistence,()->AnalysisAiAuthRef.localToken("CRM"),new LocalAnalysisRunOperationGuard(),task->{
                tasks.add(task);throw new IllegalStateException("CRM executor unavailable");
            });
            if(rejectDispatch) {
                var failed=service.start(f.request);
                assertEquals("SCHEDULING_FAILED",failed.errorCode());
                verify(persistence).save(argThat(s->s.status().equals("QUEUED")),any(),isNull());
                verify(persistence).save(argThat(s->s.status().equals("FAILED")),any(),isNull());
            } else {
                assertThrows(IllegalStateException.class,()->service.start(f.request));
                assertTrue(tasks.isEmpty());
            }
            verifyNoInteractions(f.ai);
        }
    }
}
