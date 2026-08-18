package pl.mkn.tdw.features.deliverycomplexityassessment.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.importing.DeliveryAssessmentImportService;

@RestController
@RequestMapping("/api/delivery-complexity-assessment/imports")
@RequiredArgsConstructor
public class DeliveryAssessmentImportController {

    private final DeliveryAssessmentImportService importService;

    @PostMapping
    public DeliveryComplexityAssessmentJobStateSnapshot importReadOnly(@RequestBody JsonNode document) {
        return importService.importReadOnly(document);
    }
}
