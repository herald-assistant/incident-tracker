package pl.mkn.incidenttracker.analysis.ai;

public interface AnalysisAiProvider {

    default String preparePrompt(AnalysisAiAnalysisRequest request) {
        return null;
    }

    default AnalysisAiPreparedAnalysis prepare(AnalysisAiAnalysisRequest request) {
        return new SimplePreparedAnalysis(
                "unknown",
                request != null ? request.correlationId() : null,
                preparePrompt(request),
                request
        );
    }

    default AnalysisAiAnalysisResponse analyze(
            AnalysisAiAnalysisRequest request,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        return analyze(request);
    }

    default AnalysisAiAnalysisResponse analyze(
            AnalysisAiPreparedAnalysis preparedAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener
    ) {
        if (preparedAnalysis instanceof SimplePreparedAnalysis simplePreparedAnalysis
                && simplePreparedAnalysis.request() != null) {
            return analyze(simplePreparedAnalysis.request(), toolEvidenceListener);
        }

        throw new UnsupportedOperationException("Prepared analysis is not supported by this provider.");
    }

    AnalysisAiAnalysisResponse analyze(AnalysisAiAnalysisRequest request);

}
