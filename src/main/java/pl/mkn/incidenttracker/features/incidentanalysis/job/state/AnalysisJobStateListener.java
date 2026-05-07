package pl.mkn.incidenttracker.features.incidentanalysis.job.state;

import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.features.incidentanalysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.flow.AnalysisExecutionListener;

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

    public AnalysisJobStateListener(AnalysisJobState job) {
        this.job = job;
    }

    @Override
    public void onProviderStarted(AnalysisEvidenceProvider provider, AnalysisContext context) {
        job.markEvidenceStepStarted(provider.descriptor());
    }

    @Override
    public void onProviderCompleted(
            AnalysisEvidenceProvider provider,
            AnalysisEvidenceSection section,
            AnalysisContext updatedContext
    ) {
        job.markEvidenceStepCompleted(provider.descriptor(), section);
    }

    @Override
    public void onAiStarted(InitialAnalysisRequest request, AnalysisContext context) {
        job.markAiStarted();
    }

    @Override
    public void onAiPromptPrepared(
            InitialAnalysisRequest request,
            String preparedPrompt,
            AnalysisContext context
    ) {
        job.markAiPromptPrepared(preparedPrompt);
    }

    @Override
    public void onAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
        job.markAiToolEvidenceUpdated(section);
    }

    @Override
    public void onAiActivity(AnalysisAiActivityEvent event) {
        job.markAiActivity(event);
    }
}
