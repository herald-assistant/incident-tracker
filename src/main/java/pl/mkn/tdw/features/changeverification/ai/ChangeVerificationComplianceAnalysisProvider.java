package pl.mkn.tdw.features.changeverification.ai;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

public interface ChangeVerificationComplianceAnalysisProvider {

    ChangeVerificationComplianceAnalysis analyze(
            String jobId,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    );
}
