package pl.mkn.incidenttracker.analysis.sync;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.AnalysisRequest;
import pl.mkn.incidenttracker.analysis.AnalysisResultResponse;
import pl.mkn.incidenttracker.analysis.flow.AnalysisOrchestrator;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisOrchestrator analysisOrchestrator;

    public AnalysisResultResponse analyze(AnalysisRequest request) {
        return analysisOrchestrator.analyze(request.correlationId()).result();
    }

}
