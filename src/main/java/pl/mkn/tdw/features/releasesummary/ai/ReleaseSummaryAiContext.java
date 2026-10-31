package pl.mkn.tdw.features.releasesummary.ai;

import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextReadSession;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ReleaseSummaryAiContext {
    public static final String KEY="releaseSummaryContext";
    public final ReleaseSummaryScope scope;
    public final ReleaseSummaryEvidence evidence;
    public final OperationalContextReadSession catalog;
    public final GitLabRepositoryToolScope repositoryScope;
    public final Set<String> knownSources=ConcurrentHashMap.newKeySet();
    public final Consumer<AnalysisReport> checkpoint;
    public final Runnable privateCheckpoint;
    public final boolean reportWritable;
    public volatile boolean reportMutated;
    public ReleaseSummaryAiContext(ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence,
            OperationalContextReadSession catalog,GitLabRepositoryToolScope repositoryScope,Set<String> sources,
            Consumer<AnalysisReport> checkpoint,Runnable privateCheckpoint,boolean reportWritable) {
        this.scope=scope;this.evidence=evidence;this.catalog=catalog;this.repositoryScope=repositoryScope;
        this.knownSources.addAll(sources);this.checkpoint=checkpoint;this.privateCheckpoint=privateCheckpoint;this.reportWritable=reportWritable;
    }
    public Set<String> sources() {
        var result=new java.util.HashSet<>(knownSources);result.addAll(repositoryScope.readSourceRefs());return Set.copyOf(result);
    }
}

