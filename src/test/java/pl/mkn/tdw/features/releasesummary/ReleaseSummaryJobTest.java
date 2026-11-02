package pl.mkn.tdw.features.releasesummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.releasesummary.context.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.report.*;
import pl.mkn.tdw.features.releasesummary.ai.*;
import pl.mkn.tdw.features.releasesummary.job.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryJobTest {
    @Test void checkpointsBeforeGatewayFailureAndKeepsPartialReport() {
        var fixture=new Fixture(ReleaseSummaryReportTest.evidence(true));
        var context=new AtomicReference<ReleaseSummaryAiContext>();
        when(fixture.ai.assemble(anyString(),any(),anyString(),any(),any(),any(),isNull(),any(),any())).thenAnswer(i->{
            context.set(i.getArgument(4));return new CopilotRunRequest(i.getArgument(0),"prompt",
                    new CopilotSessionConfigRequest("crm-session",List.of(),List.of(),CopilotModelSelection.DEFAULT,""),Map.of(),null);
        });
        var report=ReleaseSummaryReportTest.completed(fixture.reports,true);
        when(fixture.ai.execute(any())).thenAnswer(i->{
            context.get().reportMutated=true;context.get().checkpoint.accept(report);
            verify(fixture.persistence,atLeastOnce()).save(argThat(s->s.status().equals("RUNNING")&&s.report()==report),any(),any());
            throw new CopilotSdkInvocationException("CRM simulated timeout");
        });
        var accepted=fixture.service.start(request());
        assertEquals("QUEUED",accepted.status());
        verifyNoInteractions(fixture.ai);
        fixture.tasks.get(0).run();
        var result=fixture.service.get(accepted.jobId());
        assertEquals("PARTIAL",result.status());assertEquals(report,result.report());assertNotNull(result.result());
    }
    @Test void incompleteSourcesNeverStartAiAndNoChangesIsDeterministic() {
        for(var complete:List.of(false,true)) {
            var evidence=new ReleaseSummaryEvidence(List.of(),List.of(),complete);
            var fixture=new Fixture(evidence);var accepted=fixture.service.start(request());fixture.tasks.get(0).run();
            assertEquals(complete?"NO_CHANGES":"BLOCKED",fixture.service.get(accepted.jobId()).status());verifyNoInteractions(fixture.ai);
        }
    }
    @Test void finalProseCannotReplaceReportTools() {
        var fixture=new Fixture(ReleaseSummaryReportTest.evidence(true));
        when(fixture.ai.assemble(anyString(),any(),anyString(),any(),any(),any(),isNull(),any(),any())).thenReturn(
                new CopilotRunRequest("crm","prompt",new CopilotSessionConfigRequest("crm-session",List.of(),List.of(),CopilotModelSelection.DEFAULT,""),Map.of(),null));
        when(fixture.ai.execute(any())).thenReturn(new CopilotExecutionResult("Tekst nie jest raportem",null,"crm-session",null));
        var accepted=fixture.service.start(request());fixture.tasks.get(0).run();
        var result=fixture.service.get(accepted.jobId());
        assertEquals("REPORT_NOT_WRITTEN",result.errorCode());
        assertNull(result.report(),"A private scaffold is not a published report");
    }
    @Test void missingModelNeverPublishesScaffold() {
        var fixture=new Fixture(ReleaseSummaryReportTest.evidence(true));
        doThrow(new ReleaseSummaryException("MODEL_REQUIRED","Wybierz model AI"))
                .when(fixture.ai).requireModel(any());
        var accepted=fixture.service.start(request());fixture.tasks.get(0).run();
        var result=fixture.service.get(accepted.jobId());
        assertEquals("BLOCKED",result.status());assertEquals("MODEL_REQUIRED",result.errorCode());
        assertNull(result.report());assertNotNull(result.evidence());
        verify(fixture.ai,never()).execute(any());
    }
    private static ReleaseSummaryRequest request(){return new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,
            new GitLabRef(GitLabRef.Kind.BRANCH,"current"),new GitLabRef(GitLabRef.Kind.TAG,"next"),"crm-model",null);}
    private static class Fixture {
        final ReleaseSummaryAiRunner ai=mock(ReleaseSummaryAiRunner.class);
        final ReleaseSummaryPersistence persistence=mock(ReleaseSummaryPersistence.class);
        final ReleaseSummaryReportService reports=ReleaseSummaryReportTest.reports(new ObjectMapper());
        final List<Runnable> tasks=new ArrayList<>();
        final ReleaseSummaryJobService service;
        Fixture(ReleaseSummaryEvidence evidence) {
            var catalog=mock(ReleaseSummaryCatalog.class);var collector=mock(ReleaseSummaryDiffCollector.class);
            var captured=OperationalContextReadSession.fromSnapshot(OperationalContextSnapshot.local(OperationalContextDtos.OperationalContextCatalog.empty()));
            when(catalog.capture()).thenReturn(captured);when(catalog.pin(any(),any())).thenReturn(ReleaseSummaryReportTest.SCOPE);
            when(collector.collect(any())).thenReturn(evidence);
            var preparation=mock(ReleaseSummaryPreparation.class);
            when(preparation.prepare(any(),any())).thenAnswer(invocation->new ReleaseSummaryPreparation.Prepared(invocation.getArgument(1),Set.of(),List.of()));
            service=new ReleaseSummaryJobService(catalog,collector,preparation,new ReleaseSummaryPrompt(new ObjectMapper(),reports),reports,ai,persistence,
                    ()->AnalysisAiAuthRef.localToken("CRM"),new LocalAnalysisRunOperationGuard(),tasks::add);
        }
    }
}

