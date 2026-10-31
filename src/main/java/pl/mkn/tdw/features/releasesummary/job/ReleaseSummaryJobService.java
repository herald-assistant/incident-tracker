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
                    state.step("COLLECT_LIQUIBASE","Zmiany Liquibase","RUNNING","Weryfikacja drzew i domknięcia zależności.",0);persist(state);
                }
                var evidence=collector.collect(scope);
                synchronized(state) {
                    state.evidence=evidence;
                    state.step("COLLECT_LIQUIBASE","Zmiany Liquibase",evidence.sourceComplete()?"COMPLETED":"BLOCKED",
                            evidence.sourceComplete()?"Materiał został zweryfikowany.":"Niekompletny materiał źródłowy.",evidence.units().size());
                    if(!evidence.sourceComplete()){state.finish("BLOCKED","SOURCE_INCOMPLETE","Sprawdź luki w materiale źródłowym.");persist(state);return;}
                }
                var initial=reports.scaffold("release-summary-report-"+state.id,scope,evidence);
                if(evidence.noChanges()) {
                    var sections=initial.sections().stream().map(s->s.id().equals("DEPLOYMENT_IMPACT")?
                            new pl.mkn.tdw.shared.ai.report.AnalysisReportSection(s.id(),s.title(),s.order(),
                                    pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryTables.table(ReleaseSummaryReportService.HEADERS.get(s.id()),
                                            List.of(List.of("Migracje","Brak zmian Liquibase.","Zweryfikowano pełne drzewa obu rewizji.","[]"))),s.meta()):s).toList();
                    synchronized(state) {
                        state.report=new AnalysisReport(initial.reportId(),initial.header(),initial.subHeader(),"Nie znaleziono zmian Liquibase w zadanym zakresie.",sections,initial.meta());
                        state.result=reports.project(state.report,evidence,reports.initialSources(scope,evidence));
                        state.finish("NO_CHANGES",null,null);persist(state);return;
                    }
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
                    state.context=context;state.prompt=prompt;state.report=initial;
                    state.result=reports.project(initial,evidence,context.sources());
                    state.step("AI_REPORT","Raport AI","RUNNING","Analiza funkcjonalna i zapis tabel.",evidence.units().size());
                    persist(state);persistence.saveRuntime(state.id,context);
                }
                var usage=new CopilotUsageAccumulator();
                var run=ai.assemble(state.id,state.request,prompt,state.auth,context,initial,null,
                        section->{synchronized(state){state.toolEvidence.removeIf(s->Objects.equals(s.provider(),section.provider())&&Objects.equals(s.category(),section.category()));
                            state.toolEvidence.add(section);state.updatedAt=Instant.now();persist(state);}},
                        event->{synchronized(state){if(state.activities.stream().noneMatch(e->Objects.equals(e.eventId(),event.eventId()))){
                            state.activities.add(event);usage.recordActivity(event);state.usage=usage.snapshot();state.updatedAt=Instant.now();persist(state);}}});
                synchronized(state){state.sessionId=run.sessionConfigRequest().sessionId();}
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
                    String code=e instanceof CopilotRequiredContextTierException?"CONTEXT_TOO_LARGE":
                            e instanceof ReleaseSummaryException ex?ex.code():"ANALYSIS_FAILED";
                    var status=partial?"PARTIAL":code.equals("CONTEXT_TOO_LARGE")||e instanceof ReleaseSummaryException?"BLOCKED":"FAILED";
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
        var state=jobs.get(id);
        if(state==null) return persistence.find(id).map(persistence::read)
                .orElseThrow(()->new ReleaseSummaryException("JOB_NOT_FOUND","Nie znaleziono analizy."));
        synchronized(state){return state.snapshot();}
    }
}

