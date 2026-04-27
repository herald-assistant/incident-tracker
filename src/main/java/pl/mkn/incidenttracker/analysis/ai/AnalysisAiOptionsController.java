package pl.mkn.incidenttracker.analysis.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis/ai/options")
@RequiredArgsConstructor
public class AnalysisAiOptionsController {

    private final AnalysisAiModelOptionsProvider modelOptionsProvider;

    @GetMapping
    public AnalysisAiModelOptionsResponse options() {
        return modelOptionsProvider.modelOptions();
    }
}
