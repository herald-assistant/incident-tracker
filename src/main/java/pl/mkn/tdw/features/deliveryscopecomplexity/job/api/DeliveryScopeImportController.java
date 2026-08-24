package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.importing.DeliveryScopeImportService;

@RestController
@RequestMapping("/api/delivery-scope-complexity/imports")
@RequiredArgsConstructor
public class DeliveryScopeImportController {

    private final DeliveryScopeImportService importService;

    @PostMapping
    public DeliveryScopeComplexityJobStateSnapshot importReadOnly(@RequestBody JsonNode document) {
        return importService.importReadOnly(document);
    }
}
