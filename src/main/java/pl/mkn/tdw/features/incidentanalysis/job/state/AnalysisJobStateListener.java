package pl.mkn.tdw.features.incidentanalysis.job.state;

import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.features.incidentanalysis.evidence.AnalysisContext;
import pl.mkn.tdw.features.incidentanalysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisExecutionListener;

/**
 * Adapts incident analysis flow events to the in-memory job state projection
 * used by the operator UI.
 *
 * <p>This class is intentionally kept next to {@link AnalysisJobState}: it does
 * not orchestrate analysis, fetch evidence, or call AI. It only translates
 * {@link AnalysisExecutionListener} callbacks into state mutations that make
 * progress, prepared prompts, AI tool evidence, and terminal results visible
 * through the job API.</p>
 */
public final class AnalysisJobStateListener implements AnalysisExecutionListener {

    private final AnalysisJobState job;
    private final Runnable onStateChanged;

    public AnalysisJobStateListener(AnalysisJobState job) {
        this(job, () -> {
        });
    }

    public AnalysisJobStateListener(AnalysisJobState job, Runnable onStateChanged) {
        this.job = job;
        this.onStateChanged = onStateChanged != null ? onStateChanged : () -> {
        };
    }

    @Override
    public void onProviderStarted(AnalysisEvidenceProvider provider, AnalysisContext context) {
        job.markEvidenceStepStarted(provider.descriptor());
        onStateChanged.run();
    }

    @Override
    public void onProviderCompleted(
            AnalysisEvidenceProvider provider,
            AnalysisEvidenceSection section,
            AnalysisContext updatedContext
    ) {
        job.markEvidenceStepCompleted(provider.descriptor(), section);
        onStateChanged.run();
    }

    @Override
    public void onAiStarted(InitialAnalysisRequest request, AnalysisContext context) {
        job.markAiStarted();
        onStateChanged.run();
    }

    @Override
    public void onAiPromptPrepared(
            InitialAnalysisRequest request,
            String preparedPrompt,
            AnalysisContext context
    ) {
        job.markAiPromptPrepared(preparedPrompt);
        onStateChanged.run();
    }

    @Override
    public void onAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
        job.markAiToolEvidenceUpdated(section);
        onStateChanged.run();
    }

    @Override
    public void onAiActivity(AnalysisAiActivityEvent event) {
        job.markAiActivity(event);
        onStateChanged.run();
    }
}
