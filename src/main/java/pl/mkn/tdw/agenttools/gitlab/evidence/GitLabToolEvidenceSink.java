package pl.mkn.tdw.agenttools.gitlab.evidence;

import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.function.BiPredicate;

public interface GitLabToolEvidenceSink {

    AnalysisEvidenceSection upsertItem(
            String provider,
            String category,
            String key,
            String orderNamespace,
            String fallbackKey,
            AnalysisEvidenceItem candidate,
            BiPredicate<AnalysisEvidenceItem, AnalysisEvidenceItem> keepExisting
    );

    AnalysisEvidenceSection appendItem(
            String provider,
            String category,
            String key,
            String orderNamespace,
            String fallbackKey,
            AnalysisEvidenceItem candidate
    );
}
