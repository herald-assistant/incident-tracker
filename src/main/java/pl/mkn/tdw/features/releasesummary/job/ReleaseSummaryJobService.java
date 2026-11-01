package pl.mkn.tdw.features.releasesummary.job;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotRequiredContextTierException;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotUsageAccumulator;
import pl.mkn.tdw.features.releasesummary.context.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.ai.*;
import pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ReleaseSummaryJobService {
    private final ReleaseSummaryCatalog catalog;
    private final LiquibaseEvidenceCollector collector;
    private final ReleaseSummaryPrompt prompts;
    private final ReleaseSummaryReportService reports;
    private final ReleaseSummaryAiRunner ai;
    private final ReleaseSummaryPersistence persistence;
    private final AnalysisAiAuthRefResolver authResolver;
    private final LocalAnalysisRunOperationGuard operationGuard;
    private final TaskExecutor applicationTaskExecutor;
    private final Map<String,ReleaseSummaryRunState> jobs=new ConcurrentHashMap<>();
    @Value("${release-summary.enabled:true}")
    private boolean enabled=true;

    public ReleaseSummarySnapshot start(ReleaseSummaryRequest request) {
        if(!enabled)throw new ReleaseSummaryException("FEATURE_DISABLED","Nowe analizy Release summary są wyłączone.");
        var auth=authResolver.resolveForCurrentRequest();
        var id=UUID.randomUUID().toString();
        var state=new ReleaseSummaryRunState(id,request,auth);
        jobs.put(id,state);
        try {persist(state);} catch(RuntimeException e){jobs.remove(id);throw e;}
        var queued=state.snapshot();
        try {applicationTaskExecutor.execute(()->run(state));}
        catch(RuntimeException e) {synchronized(state){state.finish("FAILED","SCHEDULING_FAILED","Nie udało się zaplanować analizy.");persist(state);return state.snapshot();}}
        return queued;
    }
    private void run(ReleaseSummaryRunState state) {
        try(var lease=operationGuard.tryAcquire(state.id).orElseThrow(()->new IllegalStateException("Analysis already active"))) {
            try {
                synchronized(state) {
                    state.status="RUNNING";state.step("RESOLVE_REFS","Przypięcie rewizji","RUNNING","Odczyt pełnych encji i refów.",0);persist(state);
                }
                var captured=catalog.capture();
                var scope=catalog.pin(state.request,captured);
                synchronized(state) {
                    state.scope=scope;state.step("RESOLVE_REFS","Przypięcie rewizji","COMPLETED","Oba refy zostały przypięte do commitów.",scope.repositories().size());
                    state.step("COLLECT_LIQUIBASE","Zmiany Liquibase per plik","RUNNING","Weryfikacja drzew i domknięcia zależności.",0);persist(state);
                }
                var evidence=collector.collect(scope);
                synchronized(state) {
                    state.evidence=evidence;
                    state.contextSections=ReleaseSummaryProgressEvidence.sections(scope,evidence);
                    state.step("COLLECT_LIQUIBASE","Zmiany Liquibase per plik",evidence.sourceComplete()?"COMPLETED":"BLOCKED",
                            evidence.sourceComplete()?"Zweryfikowano źródła. Otwórz krok, aby porównać treść każdego pliku.":"Niekompletny materiał źródłowy. Otwórz krok, aby sprawdzić zebrane pliki i luki.",
                            state.contextSections.get(0).items().size(),List.of(),state.contextSections.stream()
                                    .map(section->new pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference(section.provider(),section.category())).toList());
                    persist(state);
                    if(!evidence.sourceComplete()){state.finish("BLOCKED","SOURCE_INCOMPLETE","Sprawdź luki w materiale źródłowym.");persist(state);return;}
                }
                var initial=reports.scaffold("release-summary-report-"+state.id,scope,evidence);
                if(evidence.noChanges()) {
                    synchronized(state) {
                        state.report=reports.noChanges(initial.reportId(),scope);
                        state.result=reports.project(state.report,evidence,reports.initialSources(scope,evidence));
                        state.finish("NO_CHANGES",null,null);persist(state);return;
                    }
                }
                synchronized(state) {
                    state.step("PREPARE_PROMPT","Inicjalny prompt AI","RUNNING","Przygotowanie instrukcji i pełnego materiału dla modelu.",0);
                    persist(state);
                }
                var revisions=new ArrayList<GitLabRepositoryToolScope.PinnedRevision>();
                for(var repo:scope.repositories()) {
                    revisions.add(new GitLabRepositoryToolScope.PinnedRevision(repo.project(),repo.baselineRef(),repo.baselineCommit()));
                    revisions.add(new GitLabRepositoryToolScope.PinnedRevision(repo.project(),repo.targetRef(),repo.targetCommit()));
                }
                var repositoryScope=GitLabRepositoryToolScope.forRevisions(scope.group(),revisions);
                var context=new ReleaseSummaryAiContext(scope,evidence,captured,repositoryScope,reports.initialSources(scope,evidence),
                        report->checkpoint(state,report),()->persistence.saveRuntime(state.id,state.context),true);
                var prompt=prompts.render(scope,evidence);
                synchronized(state) {
                    state.context=context;state.prompt=prompt;
                    state.result=reports.project(initial,evidence,context.sources());
                    state.step("PREPARE_PROMPT","Inicjalny prompt AI","COMPLETED","Pełny prompt zapisany przed wywołaniem modelu. Możesz go otworzyć i skopiować.",1,
                            List.of(ReleaseSummaryProgressEvidence.FILES),List.of());
                    persist(state);persistence.saveRuntime(state.id,context);
                }
                ai.requireModel(state.request);
                var usage=new CopilotUsageAccumulator();
                var run=ai.assemble(state.id,state.request,prompt,state.auth,context,initial,null,
                        section->{synchronized(state){state.toolEvidence.removeIf(s->Objects.equals(s.provider(),section.provider())&&Objects.equals(s.category(),section.category()));
                            state.toolEvidence.add(section);state.updatedAt=Instant.now();persist(state);}},
                        event->{synchronized(state){if(state.activities.stream().noneMatch(e->Objects.equals(e.eventId(),event.eventId()))){
                            state.activities.add(event);usage.recordActivity(event);state.usage=usage.snapshot();state.updatedAt=Instant.now();persist(state);}}});
                synchronized(state){
                    state.sessionId=run.sessionConfigRequest().sessionId();
                    state.step("AI_REPORT","Raport AI","RUNNING","Analiza funkcjonalna i zapis tabel.",evidence.units().size());
                    persist(state);
                }
                var execution=ai.execute(run);
                synchronized(state) {
                    state.usage=execution.usage()!=null?execution.usage():state.usage;
                    state.sessionId=execution.sessionId()!=null?execution.sessionId():state.sessionId;
                    if(!context.reportMutated||execution.report()==null) state.finish("FAILED","REPORT_NOT_WRITTEN","AI nie zapisało raportu przez report tools.");
                    else {state.report=execution.report();state.result=reports.project(state.report,evidence,context.sources());state.finish(state.result.status(),null,null);}
                    persist(state);
                }
            } catch(RuntimeException e) {
                synchronized(state) {
                    boolean partial=state.context!=null&&state.context.reportMutated;
                    String code=e instanceof CopilotRequiredContextTierException?"CONTEXT_PREFLIGHT_FAILED":
                            e instanceof ReleaseSummaryException ex?ex.code():"ANALYSIS_FAILED";
                    var status=partial?"PARTIAL":code.equals("CONTEXT_PREFLIGHT_FAILED")||e instanceof ReleaseSummaryException?"BLOCKED":"FAILED";
                    state.finish(status,code,e instanceof CopilotRequiredContextTierException||e instanceof ReleaseSummaryException?e.getMessage():
                            "Analiza została przerwana. Zachowano dostępny materiał i zapisane sekcje.");
                    persist(state);
                }
            }
        }
    }
    private void checkpoint(ReleaseSummaryRunState state,AnalysisReport report) {
        synchronized(state) {state.report=report;state.result=reports.project(report,state.evidence,state.context.sources());state.updatedAt=Instant.now();persist(state);}
    }
    private void persist(ReleaseSummaryRunState state) {persistence.save(state.snapshot(),state.auth,state.sessionId);}
    public ReleaseSummarySnapshot get(String id) {
        var state=state(id);
        synchronized(state){state.mutationsEnabled=enabled;return state.snapshot();}
    }
    private ReleaseSummaryRunState state(String id) {
        return jobs.computeIfAbsent(id,key->{
            var record=persistence.find(key).orElseThrow(()->new ReleaseSummaryException("JOB_NOT_FOUND","Nie znaleziono analizy."));
            var saved=persistence.read(record);
            if(!key.equals(saved.jobId()))throw new ReleaseSummaryException("INVALID_SAVED_RUN","Identyfikator zapisu nie odpowiada analizie.");
            var continuation=record.continuation();
            var auth=continuation!=null?new pl.mkn.tdw.shared.ai.AnalysisAiAuthRef("github",continuation.authMode(),
                    continuation.authPrincipalRef(),null,true):null;
            var restored=ReleaseSummaryRunState.restore(saved,auth,continuation!=null?continuation.copilotSessionId():null);
            restored.mutationsEnabled=enabled;
            if(restored.sessionId!=null) try {
                var runtime=persistence.runtime(record);
                if(restored.scope==null||!Objects.equals(runtime.catalog().contentDigest(),restored.scope.catalogDigest()))
                    throw new IllegalArgumentException("Snapshot katalogu nie odpowiada analizie.");
                var pinned=new ArrayList<GitLabRepositoryToolScope.PinnedRevision>();
                for(var repo:restored.scope.repositories()) {
                    pinned.add(new GitLabRepositoryToolScope.PinnedRevision(repo.project(),repo.baselineRef(),repo.baselineCommit()));
                    pinned.add(new GitLabRepositoryToolScope.PinnedRevision(repo.project(),repo.targetRef(),repo.targetCommit()));
                }
                var repositoryScope=GitLabRepositoryToolScope.forRevisions(restored.scope.group(),pinned);
                repositoryScope.restoreResearchRevisions(runtime.revisions());
                restored.context=new ReleaseSummaryAiContext(restored.scope,restored.evidence,
                        pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextReadSession.fromSnapshot(runtime.catalog()),
                        repositoryScope,runtime.knownSources(),report->checkpoint(restored,report),
                        ()->persistence.saveRuntime(key,restored.context),false);
            } catch(RuntimeException e) {
                restored.continuationProblem="Brak kompletnego zapisanego kontekstu; wynik jest tylko do odczytu.";
            }
            boolean interrupted=Set.of("QUEUED","RUNNING").contains(restored.status);
            for(int i=0;i<restored.messages.size();i++) {
                var m=restored.messages.get(i);
                if("ASSISTANT".equals(m.role())&&"IN_PROGRESS".equals(m.status())) {
                    var now=Instant.now();interrupted=true;
                    restored.messages.set(i,new pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse(m.id(),m.role(),"FAILED",m.content(),
                            "CHAT_INTERRUPTED","Odpowiedź przerwał restart. Pytanie nie zostało wysłane ponownie.",m.createdAt(),now,now,
                            m.toolEvidenceSections(),m.aiActivityEvents(),m.toolFeedback(),m.prompt(),m.usage()));
                }
            }
            if(Set.of("QUEUED","RUNNING").contains(restored.status))
                restored.finish(restored.result!=null&&restored.result.documentedElements()>0?"PARTIAL":"INTERRUPTED",
                        "RUN_INTERRUPTED","Analizę przerwał restart. Zachowano dostępny materiał; nie uruchomiono jej ponownie.");
            if(interrupted)persist(restored);
            return restored;
        });
    }
    private LocalAnalysisRunOperationGuard.Lease acquire(String id) {
        return operationGuard.tryAcquire(id).orElseThrow(()->new ReleaseSummaryException("JOB_BUSY","Inna operacja na tym raporcie jest w toku."));
    }
    private void requireMutation(ReleaseSummaryRunState state,boolean chat) {
        if(!enabled)throw new ReleaseSummaryException("FEATURE_DISABLED","Wynik jest tylko do odczytu.");
        if(!Set.of("COMPLETED","PARTIAL","NO_CHANGES").contains(state.status)||state.report==null)
            throw new ReleaseSummaryException("CONTINUATION_UNAVAILABLE","Raport nie jest gotowy do kontynuacji lub edycji.");
        if(state.continuationProblem!=null)throw new ReleaseSummaryException("CONTINUATION_UNAVAILABLE",state.continuationProblem);
        if(chat&&(state.context==null||state.sessionId==null||state.auth==null))
            throw new ReleaseSummaryException("CONTINUATION_UNAVAILABLE","Brak zapisanej sesji i kontekstu AI.");
    }
    public ReleaseSummarySnapshot chat(String id,String message,boolean updateReport) {
        var lease=acquire(id);
        try {
            var state=state(id);
            var assistant=beginChat(state,message);
            var accepted=state.snapshot();
            try {applicationTaskExecutor.execute(()->{try(lease){followUp(state,message,updateReport,assistant);}});}
            catch(RuntimeException e) {synchronized(state){assistant.fail("SCHEDULING_FAILED","Nie udało się zaplanować odpowiedzi.");saveMessage(state,assistant);}lease.close();}
            return accepted;
        } catch(RuntimeException e){lease.close();throw e;}
    }
    private pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState beginChat(ReleaseSummaryRunState state,String message) {
        synchronized(state) {
            requireMutation(state,true);
            if(message==null||message.isBlank()||message.length()>20000)throw new ReleaseSummaryException("INVALID_MESSAGE","Wpisz wiadomość o długości do 20000 znaków.");
            var now=Instant.now();
            state.messages.add(pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState.completedUser(UUID.randomUUID().toString(),message.trim(),now).snapshot());
            var assistant=pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState.inProgressAssistant(UUID.randomUUID().toString(),now);
            state.messages.add(assistant.snapshot());state.updatedAt=now;persist(state);return assistant;
        }
    }
    private void saveMessage(ReleaseSummaryRunState state,pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState message) {
        for(int i=0;i<state.messages.size();i++)if(state.messages.get(i).id().equals(message.id()))state.messages.set(i,message.snapshot());
        state.updatedAt=Instant.now();persist(state);
    }
    private void followUp(ReleaseSummaryRunState state,String message,boolean updateReport,
            pl.mkn.tdw.shared.ai.chat.AnalysisChatMessageState assistant) {
        var previous=state.report;var usage=new CopilotUsageAccumulator();
        var old=state.context;
        var context=new ReleaseSummaryAiContext(state.scope,state.evidence,old.catalog,old.repositoryScope,old.sources(),
                report->checkpoint(state,report),()->persistence.saveRuntime(state.id,state.context),updateReport);
        synchronized(state){state.context=context;}
        String prompt=(updateReport?"Operator jawnie zleca korektę raportu przez report tools. ":"Pytanie tylko do odczytu. Nie zmieniaj raportu. ")+
                "Odczytuj kod na zapisanych SHA; opctx zawiera zapisany katalog. Zachowaj coverage i wskazuj źródła.\n"+
                pl.mkn.tdw.shared.ai.report.AnalysisReportManualEditGuidance.forFollowUp(state.report)+"\n"+
                "Polecenie operatora:\n"+message;
        try {
            var run=ai.assemble(state.id+"-chat-"+assistant.id(),state.request,prompt,state.auth,context,previous,state.sessionId,
                    section->{synchronized(state){assistant.addToolEvidence(section);saveMessage(state,assistant);}},
                    event->{synchronized(state){assistant.addActivity(event);usage.recordActivity(event);
                        assistant.updateProgress(prompt,usage.snapshot());saveMessage(state,assistant);}});
            var response=ai.execute(run);
            synchronized(state) {
                if(updateReport&&context.reportMutated&&response.report()!=null)checkpoint(state,response.report());
                if(context.reportMutated)state.finish(state.result.status(),null,null);
                state.sessionId=response.sessionId()!=null?response.sessionId():state.sessionId;
                var turnUsage=response.usage()!=null?response.usage():usage.snapshot();
                assistant.addToolEvidence(pl.mkn.tdw.shared.ai.report.AnalysisReportChangeEvidence.between(previous,state.report));
                assistant.complete(response.content(),prompt,turnUsage);saveMessage(state,assistant);
                persistence.saveRuntime(state.id,context);
            }
        } catch(RuntimeException e) {
            synchronized(state) {
                if(context.reportMutated)state.finish("PARTIAL","CHAT_PARTIAL","Korektę przerwano; zapisane sekcje zostały zachowane.");
                assistant.addToolEvidence(pl.mkn.tdw.shared.ai.report.AnalysisReportChangeEvidence.between(previous,state.report));
                assistant.complete("",prompt,usage.snapshot());
                assistant.fail("CHAT_FAILED","Nie udało się zakończyć kontynuacji. Zachowano raport i przypięte rewizje; nie utworzono sesji zastępczej.");
                saveMessage(state,assistant);
            }
        }
    }
    public ReleaseSummarySnapshot edit(String id,pl.mkn.tdw.shared.ai.report.AnalysisReportEditRequest request) {
        try(var lease=acquire(id)){return editUnderLease(id,request);}
    }
    ReleaseSummarySnapshot editUnderLease(String id,pl.mkn.tdw.shared.ai.report.AnalysisReportEditRequest request) {
        var state=state(id);
        synchronized(state) {
            requireMutation(state,false);
            var updated=pl.mkn.tdw.shared.ai.report.AnalysisReportManualEditor.apply(state.report,request);
            var sources=state.context!=null?state.context.sources():reports.initialSources(state.scope,state.evidence);
            try {for(var section:updated.sections())reports.validatePublication(section.id(),section.markdown(),section.meta(),state.evidence,sources);}
            catch(IllegalArgumentException e){throw new ReleaseSummaryException("INVALID_REPORT_EDIT",e.getMessage());}
            state.report=updated;state.result=reports.project(updated,state.evidence,sources);
            state.finish(state.result.status(),null,null);persist(state);return state.snapshot();
        }
    }
    void continueUnderLease(String id,String message) {
        var state=state(id);var assistant=beginChat(state,message);followUp(state,message,false,assistant);
    }
    public ReleaseSummaryPersistence.Envelope export(String id) {
        var snapshot=get(id);
        if(!snapshot.exportAvailable())throw new ReleaseSummaryException("JOB_BUSY","Eksport jest dostępny po zakończeniu bieżącej operacji.");
        return persistence.envelope(snapshot);
    }
}

