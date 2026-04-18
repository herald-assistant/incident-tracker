package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/elasticsearch/logs")
@RequiredArgsConstructor
public class ElasticLogSearchController {

    private final ElasticLogSearchService elasticLogSearchService;

    @PostMapping("/search")
    public ElasticLogSearchResult search(@Valid @RequestBody ElasticLogSearchRequest request) {
        return elasticLogSearchService.search(request);
    }

}
