package pl.mkn.tdw.api.elasticsearch;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogSearchPort;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallLogsRequest;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallLogsResult;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallSummaryRequest;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallSummaryResult;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticLogSearchRequest;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticLogSearchResult;

@RestController
@RequestMapping("/api/elasticsearch/logs")
@RequiredArgsConstructor
public class ElasticLogSearchController {

    private final ElasticLogSearchPort elasticLogSearchService;

    @PostMapping("/search")
    public ElasticLogSearchResult search(@Valid @RequestBody ElasticLogSearchRequest request) {
        return elasticLogSearchService.search(request);
    }

    @PostMapping("/http-calls/summary")
    public ElasticHttpCallSummaryResult summarizeHttpCalls(@Valid @RequestBody ElasticHttpCallSummaryRequest request) {
        return elasticLogSearchService.summarizeHttpCalls(request);
    }

    @PostMapping("/http-calls/fetch")
    public ElasticHttpCallLogsResult fetchHttpCallLogs(@Valid @RequestBody ElasticHttpCallLogsRequest request) {
        return elasticLogSearchService.fetchHttpCallLogs(request);
    }
}
