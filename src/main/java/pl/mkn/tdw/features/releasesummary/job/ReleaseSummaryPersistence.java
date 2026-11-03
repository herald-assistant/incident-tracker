package pl.mkn.tdw.features.releasesummary.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.features.releasesummary.ai.ReleaseSummaryAiContext;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummarySnapshot;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextSnapshot;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ReleaseSummaryPersistence {
    public static final String FEATURE="release-summary";
    private final ObjectMapper mapper;
    private final LocalAnalysisRunStore store;
    public record Envelope(String schema,int version,Instant exportedAt,Payload payload) {
        public boolean supported() {return "tdw.release-summary-export".equals(schema)&&version==1&&payload!=null
                &&"release-summary-analysis".equals(payload.type())&&pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService.CONTRACT.equals(payload.resultContract())&&payload.job()!=null
                &&payload.job().request()!=null&&payload.job().request().range()!=null&&payload.job().request().mode()==pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryRequest.Mode.LIQUIBASE
                &&(payload.job().result()==null||pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService.CONTRACT.equals(payload.job().result().resultContract()));}
    }
    public record Payload(String type,String resultContract,ReleaseSummarySnapshot job) {}
    public record RuntimeState(int version,OperationalContextSnapshot catalog,List<GitLabRepositoryToolScope.PinnedRevision> revisions,Set<String> knownSources) {}
    public Envelope envelope(ReleaseSummarySnapshot job) {
        return new Envelope("tdw.release-summary-export",1,job.updatedAt(),new Payload("release-summary-analysis",pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService.CONTRACT,job));
    }
    public void save(ReleaseSummarySnapshot job,AnalysisAiAuthRef auth,String sessionId) {
        var enabled=Set.of("COMPLETED","PARTIAL").contains(job.status())&&job.report()!=null&&sessionId!=null;
        var continuation=new LocalAnalysisRunContinuation(enabled,job.comparison()!=null?job.comparison().group():null,
                auth!=null?auth.mode():null,auth!=null?auth.principalId():null,sessionId,
                LocalAnalysisRunContinuation.COPILOT_RUNTIME_GITHUB_COPILOT_SDK,LocalAnalysisRunContinuation.CONTINUATION_MODE_COPILOT_SESSION);
        LocalAnalysisRunSnapshotWriter.save(store,mapper.valueToTree(envelope(job)),continuation,
                new LocalAnalysisRunSnapshotWriter.Metadata(job.jobId(),FEATURE,
                        (job.comparison()!=null?job.comparison().systemName():job.request().systemId())+" · "+
                                job.request().range().selection().branch()+" · "+job.request().range().selection().dateFrom()+" → "+job.request().range().selection().dateTo(),
                        job.status(),job.createdAt(),job.updatedAt(),job.completedAt()));
    }
    public void saveRuntime(String id,ReleaseSummaryAiContext context) {
        var revisions=context.repositoryScope.pinnedRevisions().entrySet().stream().map(e->{
            int at=e.getKey().indexOf('@'); return new GitLabRepositoryToolScope.PinnedRevision(e.getKey().substring(0,at),e.getKey().substring(at+1),e.getValue());
        }).toList();
        store.updateStoreSnapshot(id,new LocalAnalysisRunStoreSnapshot(
                mapper.valueToTree(new RuntimeState(1,context.catalog.snapshot(),revisions,context.sources())),Instant.now(),FEATURE,0));
    }
    public Optional<LocalAnalysisRunRecord> find(String id) {return store.findById(id);}
    public ReleaseSummarySnapshot read(LocalAnalysisRunRecord record) {
        try {
            var job=record.exportEnvelope().at("/payload/job");
            var evidence=job.path("evidence");
            if(!evidence.isMissingNode()&&!evidence.isNull()&&(!evidence.path("changeListComplete").isBoolean()
                    ||!evidence.path("changes").isArray()||!evidence.path("gaps").isArray()
                    ||evidence.has("units")||evidence.has("sources")||evidence.has("scannedFiles")||evidence.has("sourceComplete")))
                throw new IllegalArgumentException("Niepoprawny manifest diff-first.");
            var result=job.path("result");
            if(!result.isMissingNode()&&!result.isNull()&&(!result.path("changeListComplete").isBoolean()
                    ||!result.path("changedFiles").canConvertToInt()||!result.path("documentedTables").canConvertToInt()
                    ||!result.path("readSources").canConvertToInt()||result.has("coverage")||result.has("expectedElements")))
                throw new IllegalArgumentException("Niepoprawny wynik diff-first.");
            var value=mapper.treeToValue(record.exportEnvelope(),Envelope.class);
            if(!value.supported())throw new IllegalArgumentException("Nieobsługiwany format Release summary.");
            return value.payload().job();
        } catch(Exception e){throw new IllegalArgumentException("Nie można odczytać analizy Release summary.",e);}
    }
    public RuntimeState runtime(LocalAnalysisRunRecord record) {
        try {
            if(record.storeSnapshot()==null||!FEATURE.equals(record.storeSnapshot().source()))throw new IllegalArgumentException("Brak kontekstu kontynuacji.");
            var state=mapper.treeToValue(record.storeSnapshot().state(),RuntimeState.class);
            if(state.version()!=1||state.catalog()==null)throw new IllegalArgumentException("Nieobsługiwany kontekst kontynuacji.");
            return state;
        } catch(Exception e){throw new IllegalArgumentException("Brak poprawnego kontekstu kontynuacji.",e);}
    }
}

