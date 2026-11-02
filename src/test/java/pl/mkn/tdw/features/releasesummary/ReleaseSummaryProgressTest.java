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

    @Test void initialPromptContainsOnlySelectedDiffsAndScopedInstructionsAndSourcesSurviveRestart() {
    var f=new ReleaseSummaryContinuationTest.Fixture();
    var selectedPath="db/changelog/customer.sql";
    var selected=new pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison.Change(selectedPath,selectedPath,
        "c".repeat(40),"d".repeat(40),"100644","100644","@@ -1 +1 @@\n-SELECT 'before';\n+SELECT 'after';",true);
    var unrelated=new pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison.Change("pom.xml","pom.xml",
        "e".repeat(40),"f".repeat(40),"100644","100644","POM_PATCH_NOT_FOR_AI",true);
    var comparison=mock(pl.mkn.tdw.integrations.gitlab.GitLabRepositoryComparisonPort.class);
    when(comparison.compare(anyString(),anyString(),anyString(),anyString())).thenReturn(
        new pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison("a".repeat(40),"b".repeat(40),
            List.of(selected,unrelated),true,List.of()));
    var evidence=new ReleaseSummaryDiffCollector(comparison,new ReleaseSummaryDiffProperties()).collect(ReleaseSummaryReportTest.SCOPE);
    when(f.collector.collect(any())).thenReturn(evidence);
    var source="gitlab:CRM/customer@"+"a".repeat(40)+":"+selectedPath;
    var instruction=new pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionSource("CRM/customer","b".repeat(40),
        ".github/instructions/sql.instructions.md","SCOPED_INSTRUCTIONS","Explain customer labels using CRM terminology.",false,null,List.of(selectedPath));
    when(f.preparation.prepare(any(),any())).thenReturn(new ReleaseSummaryPreparation.Prepared(evidence,Set.of(source),List.of(instruction)));
    var id=f.complete();var prompt=f.service.get(id).preparedPrompt();
    assertTrue(prompt.contains("SELECT 'after'"));assertTrue(prompt.contains("Explain customer labels using CRM terminology."));
    assertTrue(prompt.contains("sql.instructions.md"));assertFalse(prompt.contains("pom.xml"));
    assertFalse(prompt.contains("POM_PATCH_NOT_FOR_AI"));assertTrue(f.context.get().sources().contains(source));
    clearInvocations(f.catalog,f.preparation);f.service=f.service();f.service.get(id);
    f.service.chat(id,"Explain the prepared source.",false);f.next();
    assertTrue(f.context.get().sources().contains(source));verifyNoInteractions(f.catalog,f.preparation);
    assertEquals(prompt,f.service.get(id).preparedPrompt());
    }

    @Test void filesAndExactPromptArePersistedBeforeAiAndSurviveItsFailure() {
        var f=new ReleaseSummaryContinuationTest.Fixture();
        var evidence=ReleaseSummaryReportTest.evidence(true);
        var expected=new ReleaseSummaryPrompt(f.mapper,f.reports).render(ReleaseSummaryReportTest.SCOPE,
            new ReleaseSummaryPreparation.Prepared(evidence,Set.of(),List.of()));
        doAnswer(invocation->{
            pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest request=invocation.getArgument(0);
            var saved=f.persistence.read(f.records.values().iterator().next());
            assertEquals(expected,request.prompt());
            assertEquals(request.prompt(),saved.preparedPrompt());
            assertEquals(List.of("COMPLETED","COMPLETED","COMPLETED","RUNNING"),saved.steps().stream().map(s->s.status()).toList());
            assertTrue(saved.steps().get(2).completedAt().compareTo(saved.steps().get(3).startedAt())<=0);
            assertEquals(List.of(ReleaseSummaryProgressEvidence.FILES),saved.steps().get(1).producesEvidence());
                assertEquals(evidence.changes().get(0).change().patch(),saved.contextSections().get(0).items().get(0).attributes().stream()
                    .filter(a->a.name().equals("Patch")).findFirst().orElseThrow().value());
            assertEquals(saved,f.service.get(saved.jobId()));
            throw new IllegalStateException("CRM gateway failed");
        }).when(f.ai).execute(any());
        var accepted=f.service.start(f.request);f.next();
        var failed=f.service.get(accepted.jobId());
        assertEquals("FAILED",failed.status());
        assertEquals(expected,failed.preparedPrompt());
        assertEquals("COMPLETED",failed.steps().get(2).status());
        f.service=f.service();
        var restored=f.service.get(accepted.jobId());
        assertEquals(failed.contextSections(),restored.contextSections());
        assertEquals(failed.steps(),restored.steps());
        assertEquals(expected,restored.preparedPrompt());
    }

    @Test void collectionGapsAreAvailableWithoutPreparingOrSendingPrompt() {
        var f=new ReleaseSummaryContinuationTest.Fixture();
        var gap=new ReleaseSummaryEvidence.Gap("SOURCE_UNAVAILABLE","customer","TARGET","db/customer.xml","Nie można odczytać pliku CRM.",true);
        when(f.collector.collect(any())).thenReturn(new ReleaseSummaryEvidence(List.of(),List.of(gap),false));
        var accepted=f.service.start(f.request);f.next();
        var blocked=f.persistence.read(f.records.get(accepted.jobId()));
        assertEquals("BLOCKED",blocked.status());assertNull(blocked.preparedPrompt());
        assertEquals(List.of(ReleaseSummaryProgressEvidence.FILES,ReleaseSummaryProgressEvidence.GAPS),blocked.steps().get(1).producesEvidence());
        assertEquals("SKIPPED",blocked.steps().get(2).status());
        assertEquals(1,blocked.contextSections().get(1).items().size());
        verifyNoInteractions(f.ai);
    }

    @Test void historyIsDurableAndReadableBeforeTheExecutorStartsWork() {
        var f=new ReleaseSummaryContinuationTest.Fixture();
        var mapper=new ObjectMapper().findAndRegisterModules();
        var properties=new LocalWorkspaceProperties();properties.setDirectory(directory.toString());
        var paths=new LocalWorkspacePaths(properties);
        var store=new FileSystemLocalAnalysisRunStore(properties,paths,new LocalWorkspaceJsonFileStore(mapper));
        var persistence=new ReleaseSummaryPersistence(mapper,store);
        var service=new ReleaseSummaryJobService(f.catalog,f.collector,f.preparation,new ReleaseSummaryPrompt(mapper,f.reports),f.reports,
                f.ai,persistence,()->AnalysisAiAuthRef.localToken("CRM"),new LocalAnalysisRunOperationGuard(),task->{
            // Read through a separate store instance before dispatch, not through job memory.
            var reader=new FileSystemLocalAnalysisRunStore(properties,paths,new LocalWorkspaceJsonFileStore(mapper));
            var history=new AnalysisRunHistoryService(reader,List.of(),null);
            var entry=history.listRuns().runs().get(0);
            assertEquals("QUEUED",entry.status());
            assertEquals("crm · current → next",entry.name());
            assertNull(entry.completedAt());
            var saved=persistence.read(reader.findById(entry.analysisId()).orElseThrow());
            assertEquals(List.of("RESOLVE_REFS","COLLECT_DIFF","PREPARE_PROMPT","AI_REPORT"),
                    saved.steps().stream().map(s->s.code()).toList());
            assertTrue(saved.steps().stream().allMatch(s->s.status().equals("PENDING")&&s.startedAt()==null));
            f.tasks.add(task);
        });
        var accepted=service.start(f.request);
        assertEquals(accepted,service.get(accepted.jobId()));
        verifyNoInteractions(f.catalog,f.collector,f.ai);
        when(f.collector.collect(any())).thenReturn(new ReleaseSummaryEvidence(List.of(),List.of(),true));
        f.tasks.remove(0).run();
        assertEquals("NO_CHANGES",store.listRuns().get(0).status());
    }

    @Test void failedPersistenceDoesNotDispatchAndRejectedDispatchLeavesHistoryEntry() {
        for(boolean rejectDispatch:List.of(false,true)) {
            var f=new ReleaseSummaryContinuationTest.Fixture();
            var persistence=mock(ReleaseSummaryPersistence.class);
            var tasks=new ArrayList<Runnable>();
            if(!rejectDispatch) doThrow(new IllegalStateException("CRM storage failure")).when(persistence).save(any(),any(),any());
            var service=new ReleaseSummaryJobService(f.catalog,f.collector,f.preparation,new ReleaseSummaryPrompt(f.mapper,f.reports),f.reports,
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
