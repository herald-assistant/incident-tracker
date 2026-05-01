package pl.mkn.incidenttracker.api.elasticsearch;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogSearchRequest;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogSearchResult;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogSearchService;

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
