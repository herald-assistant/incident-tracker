package pl.mkn.tdw.features.releasesummary;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.mkn.tdw.features.releasesummary.context.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.report.*;
import pl.mkn.tdw.features.releasesummary.ai.*;
import pl.mkn.tdw.features.releasesummary.job.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.shared.ai.*;
import pl.mkn.tdw.shared.ai.report.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryContinuationTest {
    @Test void restoresExactRevisionsCatalogAndReadOnlyQuestionsAcrossRestart() {
        var f=new Fixture();var id=f.complete();
        var before=f.service.get(id);clearInvocations(f.catalog,f.ai);
        f.service=f.service();var restored=f.service.get(id);
        assertEquals(before.report(),restored.report());assertTrue(restored.chatAvailability().available());
        var accepted=f.service.chat(id,"Dlaczego klient ma etykietę?",false);
        assertEquals("IN_PROGRESS",accepted.chatMessages().get(1).status());assertFalse(accepted.exportAvailable());
        assertThrows(ReleaseSummaryException.class,()->f.service.chat(id,"Drugie pytanie",true));
        assertThrows(ReleaseSummaryException.class,()->f.service.edit(id,edit(before.report(),"Zmienione podsumowanie")));
        f.next();
        assertFalse(f.context.get().reportWritable);
        assertEquals("digest",f.context.get().catalog.contentDigest());
        assertEquals(2,f.context.get().repositoryScope.pinnedRevisions().size());
        assertEquals(before.report(),f.service.get(id).report());
        assertEquals("COMPLETED",f.service.get(id).chatMessages().get(1).status());
        verifyNoInteractions(f.catalog);
        verify(f.ai).assemble(anyString(),any(),contains("tylko do odczytu"),any(),any(),any(),eq("crm-session"),any(),any());
    }
    @Test void editsAreConditionalValidatedAndPersistedAndExplicitAiCorrectionsAreWritable() {
        var f=new Fixture();var id=f.complete();var original=f.service.get(id);
        var request=edit(original.report(),"Uzasadnienie CRM po przeglądzie operatora.");
        var edited=f.service.edit(id,request);
        assertEquals(1,edited.report().manualEdit().revision());
        assertEquals(original.result().changedFiles(),edited.result().changedFiles());
        assertThrows(AnalysisReportEditException.class,()->f.service.edit(id,request));
        var detail=edited.report().sections().stream().filter(s->s.id().startsWith("DB_DETAIL")).findFirst().orElseThrow();
        assertThrows(ReleaseSummaryException.class,()->f.service.edit(id,new AnalysisReportEditRequest(edited.report().revisionSha256(),null,
                List.of(new AnalysisReportSectionEdit(detail.id(),detail.markdown()+"\nOpis poza tabela.")))));
        var index=edited.report().sections().stream().filter(section->section.id().equals("TABLE_LIST")).findFirst().orElseThrow();
        assertThrows(ReleaseSummaryException.class,()->f.service.edit(id,new AnalysisReportEditRequest(edited.report().revisionSha256(),null,
            List.of(new AnalysisReportSectionEdit(index.id(),index.markdown().replace("customer","invented"))))));
        f.service=f.service();assertEquals(edited.report(),f.service.get(id).report());
        f.service.chat(id,"Uzupełnij raport.",true);f.next();
        assertTrue(f.context.get().reportWritable);
        assertEquals("COMPLETED",f.service.get(id).status());
    }
    @Test void restartMarksPendingWorkWithoutResendingAndMissingPrivateContextDisablesContinuation() {
        var f=new Fixture();var id=f.complete();
        f.service.chat(id,"Pytanie przerwane restartem",false);clearInvocations(f.ai);
        f.service=f.service();var recovered=f.service.get(id);
        assertEquals("FAILED",recovered.chatMessages().get(1).status());
        assertEquals("CHAT_INTERRUPTED",recovered.chatMessages().get(1).errorCode());
        verifyNoInteractions(f.ai);
        f.records.computeIfPresent(id,(key,record)->record.withStoreSnapshot(null));f.service=f.service();
        assertFalse(f.service.get(id).chatAvailability().available());
        assertThrows(ReleaseSummaryException.class,()->f.service.chat(id,"Nie odtwarzaj źródeł",false));
        var queued=f.service.start(f.request);f.service=f.service();
        assertEquals("INTERRUPTED",f.service.get(queued.jobId()).status());
    }
    @Test void publicExportContainsNoPrivateSessionAndAcceptsOnlyCurrentEnvelopeAndFlagKeepsHistoryReadable() throws Exception {
        var f=new Fixture();var id=f.complete();var envelope=f.service.export(id);
        var json=f.mapper.writeValueAsString(envelope);
        assertFalse(json.contains("crm-session"));assertFalse(json.contains("authMode"));assertFalse(json.contains("knownSources"));
        assertEquals(f.service.get(id).report(),f.persistence.read(LocalAnalysisRunRecord.v1(f.mapper.readTree(json),null)).report());
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)f.mapper.readTree(json);node.put("version",2);
        assertThrows(IllegalArgumentException.class,()->f.persistence.read(LocalAnalysisRunRecord.v1(node,null)));
        var wrongMode=(com.fasterxml.jackson.databind.node.ObjectNode)f.mapper.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode)wrongMode.at("/payload/job/request")).put("mode","API");
        assertThrows(IllegalArgumentException.class,()->f.persistence.read(LocalAnalysisRunRecord.v1(wrongMode,null)));
        var oldEvidence=(com.fasterxml.jackson.databind.node.ObjectNode)f.mapper.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode)oldEvidence.at("/payload/job/evidence")).putArray("units");
        assertThrows(IllegalArgumentException.class,()->f.persistence.read(LocalAnalysisRunRecord.v1(oldEvidence,null)));
        ReflectionTestUtils.setField(f.service,"enabled",false);
        assertNotNull(f.service.get(id).report());assertFalse(f.service.get(id).chatAvailability().available());
        assertThrows(ReleaseSummaryException.class,()->f.service.chat(id,"Pytanie",false));
        assertThrows(ReleaseSummaryException.class,()->f.service.edit(id,edit(envelope.payload().job().report(),"Zmiana")));
    }
    private static AnalysisReportEditRequest edit(AnalysisReport report,String text) {
        return new AnalysisReportEditRequest(report.revisionSha256(),text,List.of());
    }
    static class Fixture {
        final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        final ReleaseSummaryAiRunner ai=mock(ReleaseSummaryAiRunner.class);
        final ReleaseSummaryCatalog catalog=mock(ReleaseSummaryCatalog.class);
        final ReleaseSummaryDiffCollector collector=mock(ReleaseSummaryDiffCollector.class);
        final ReleaseSummaryPreparation preparation=mock(ReleaseSummaryPreparation.class);
        final LocalAnalysisRunStore store=mock(LocalAnalysisRunStore.class);
        final Map<String,LocalAnalysisRunRecord> records=new HashMap<>();
        final ReleaseSummaryPersistence persistence=new ReleaseSummaryPersistence(mapper,store);
        final ReleaseSummaryReportService reports=ReleaseSummaryReportTest.reports(mapper);
        final List<Runnable> tasks=new ArrayList<>();
        final AtomicReference<ReleaseSummaryAiContext> context=new AtomicReference<>();
        final ReleaseSummaryRequest request=new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,
                new GitLabRef(GitLabRef.Kind.BRANCH,"current"),new GitLabRef(GitLabRef.Kind.TAG,"next"),"crm-model",null);
        ReleaseSummaryJobService service;
        Fixture() {
            when(store.findById(anyString())).thenAnswer(i->Optional.ofNullable(records.get(i.getArgument(0))));
            doAnswer(i->{LocalAnalysisRunIndexEntry index=i.getArgument(0);LocalAnalysisRunRecord record=i.getArgument(1);
                var previous=records.get(index.analysisId());records.put(index.analysisId(),previous!=null?record.withStoreSnapshot(previous.storeSnapshot()):record);return null;
            }).when(store).save(any(),any());
            doAnswer(i->{String id=i.getArgument(0);records.put(id,records.get(id).withStoreSnapshot(i.getArgument(1)));return null;})
                    .when(store).updateStoreSnapshot(anyString(),any());
            when(catalog.capture()).thenReturn(OperationalContextReadSession.fromSnapshot(new OperationalContextSnapshot("digest","CRM",OperationalContextDtos.OperationalContextCatalog.empty())));
            when(catalog.pin(any(),any())).thenReturn(ReleaseSummaryReportTest.SCOPE);
            when(collector.collect(any())).thenReturn(ReleaseSummaryReportTest.evidence(true));
            when(preparation.prepare(any(),any())).thenAnswer(invocation->new ReleaseSummaryPreparation.Prepared(invocation.getArgument(1),Set.of(),List.of()));
            when(ai.assemble(anyString(),any(),anyString(),any(),any(),any(),nullable(String.class),any(),any())).thenAnswer(i->{
                context.set(i.getArgument(4));context.get().knownSources.add(ReleaseSummaryReportTest.SOURCE);return new CopilotRunRequest(i.getArgument(0),i.getArgument(2),
                        new CopilotSessionConfigRequest("crm-session",List.of(),List.of(),CopilotModelSelection.DEFAULT,""),Map.of(),null).withInitialReport(i.getArgument(5));
            });
            when(ai.execute(any())).thenAnswer(i->{
                var report=ReleaseSummaryReportTest.completed(reports,true);
                if(context.get().reportWritable){context.get().reportMutated=true;context.get().checkpoint.accept(report);}
                return new CopilotExecutionResult("Etykieta służy wyszukiwaniu klienta.",null,"crm-session",report);
            });
            service=service();
        }
        ReleaseSummaryJobService service() {return new ReleaseSummaryJobService(catalog,collector,preparation,new ReleaseSummaryPrompt(mapper,reports),reports,ai,persistence,
                ()->AnalysisAiAuthRef.localToken("CRM"),new LocalAnalysisRunOperationGuard(),tasks::add);}
        String complete(){var job=service.start(request);next();assertEquals("COMPLETED",service.get(job.jobId()).status());return job.jobId();}
        void next(){tasks.remove(0).run();}
    }
}
