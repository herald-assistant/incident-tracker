package pl.mkn.incidenttracker.analysis.mcp.elasticsearch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult;

@Component
@Slf4j
@RequiredArgsConstructor
public class ElasticMcpTools {

    private final ElasticLogPort elasticLogPort;

    @Tool(
            name = "elastic_search_logs_by_correlation_id",
            description = "Search Elasticsearch/Kibana logs by business correlationId using the configured Kibana console proxy and return structured log entries."
    )
    public ElasticLogSearchResult searchLogsByCorrelationId(
            @ToolParam(description = "Business correlationId of the analyzed incident.")
            String correlationId
    ) {
        log.info("Tool request [{}] correlationId={}", "elastic_search_logs_by_correlation_id", correlationId);

        var result = elasticLogPort.searchLogsByCorrelationId(correlationId);

        log.info(
                "Tool result [{}] correlationId={} requestedSize={} returnedHits={} totalHits={} timedOut={}",
                "elastic_search_logs_by_correlation_id",
                correlationId,
                result.requestedSize(),
                result.returnedHits(),
                result.totalHits(),
                result.timedOut()
        );

        return result;
    }

}
