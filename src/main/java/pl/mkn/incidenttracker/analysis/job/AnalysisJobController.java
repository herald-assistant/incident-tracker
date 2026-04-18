package pl.mkn.incidenttracker.analysis.job;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.incidenttracker.analysis.AnalysisRequest;

@RestController
@RequestMapping("/analysis/jobs")
@RequiredArgsConstructor
public class AnalysisJobController {

    private final AnalysisJobService analysisJobService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisJobResponse start(@Valid @RequestBody AnalysisRequest request) {
        return analysisJobService.startAnalysis(request);
    }

    @GetMapping("/{analysisId}")
    public AnalysisJobResponse get(@PathVariable String analysisId) {
        return analysisJobService.getAnalysis(analysisId);
    }

}
