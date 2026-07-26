package pl.mkn.tdw.aiplatform.copilot.tools.evidence;

import pl.mkn.tdw.agenttools.gitlab.evidence.GitLabToolEvidenceSink;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.function.BiPredicate;

public class CopilotGitLabToolEvidenceSink implements GitLabToolEvidenceSink {

    private final CopilotToolEvidenceSessionStore.SessionToolEvidence delegate;

    public CopilotGitLabToolEvidenceSink(CopilotToolEvidenceSessionStore.SessionToolEvidence delegate) {
        this.delegate = delegate;
    }

    @Override
    public AnalysisEvidenceSection upsertItem(
            String provider,
            String category,
            String key,
            String orderNamespace,
            String fallbackKey,
            AnalysisEvidenceItem candidate,
            BiPredicate<AnalysisEvidenceItem, AnalysisEvidenceItem> keepExisting
    ) {
        return delegate.upsertItem(provider, category, key, orderNamespace, fallbackKey, candidate, keepExisting);
    }

    @Override
    public AnalysisEvidenceSection appendItem(
            String provider,
            String category,
            String key,
            String orderNamespace,
            String fallbackKey,
            AnalysisEvidenceItem candidate
    ) {
        return delegate.appendItem(provider, category, key, orderNamespace, fallbackKey, candidate);
    }
}
