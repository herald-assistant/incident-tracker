package pl.mkn.incidenttracker.analysis.sync;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.incidenttracker.analysis.AnalysisRequest;
import pl.mkn.incidenttracker.analysis.AnalysisResultResponse;

@RestController
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisResultResponse analyze(@Valid @RequestBody AnalysisRequest request) {
        return analysisService.analyze(request);
    }

}
