package pl.mkn.incidenttracker.analysis.mcp.elasticsearch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult;

import static pl.mkn.incidenttracker.analysis.mcp.elasticsearch.ElasticToolNames.SEARCH_LOGS_BY_CORRELATION_ID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ElasticMcpTools {

    private final ElasticLogPort elasticLogPort;

    @Tool(
            name = SEARCH_LOGS_BY_CORRELATION_ID,
            description = "Search Elasticsearch/Kibana logs by business correlationId using the configured Kibana console proxy and return structured log entries."
    )
    public ElasticLogSearchResult searchLogsByCorrelationId(
            @ToolParam(description = "Business correlationId of the analyzed incident.")
            String correlationId
    ) {
        log.info("Tool request [{}] correlationId={}", SEARCH_LOGS_BY_CORRELATION_ID, correlationId);

        var result = elasticLogPort.searchLogsByCorrelationId(correlationId);

        log.info(
                "Tool result [{}] correlationId={} requestedSize={} returnedHits={} totalHits={} timedOut={}",
                SEARCH_LOGS_BY_CORRELATION_ID,
                correlationId,
                result.requestedSize(),
                result.returnedHits(),
                result.totalHits(),
                result.timedOut()
        );

        return result;
    }

}
