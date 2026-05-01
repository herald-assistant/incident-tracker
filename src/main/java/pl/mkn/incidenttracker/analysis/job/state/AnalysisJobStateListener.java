package pl.mkn.incidenttracker.analysis.job.state;

import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.flow.AnalysisExecutionListener;

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
}
