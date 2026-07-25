package pl.mkn.tdw.features.changeverification.ai;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

public interface ChangeVerificationSmokePackAnalysisProvider {

    ChangeVerificationSmokePackAnalysis analyze(
            String jobId,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis
    );

    default ChangeVerificationSmokePackAnalysis analyze(
            String jobId,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        return analyze(jobId, request, sourceDiscovery, complianceAnalysis);
    }

    default ChangeVerificationSmokePackAnalysis analyze(
            String jobId,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        return analyze(jobId, request, sourceDiscovery, complianceAnalysis, toolEvidenceListener);
    }
}
