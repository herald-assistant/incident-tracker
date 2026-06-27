package pl.mkn.tdw.agenttools.elasticsearch.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallLogsRequest;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallLogsResult;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallSummaryRequest;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallSummaryResult;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogDetailLevel;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogPort;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogSearchResult;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogSearchService;

import java.util.Locale;

import static pl.mkn.tdw.agenttools.elasticsearch.ElasticToolNames.FETCH_HTTP_CALL_LOGS;
import static pl.mkn.tdw.agenttools.elasticsearch.ElasticToolNames.SEARCH_LOGS_BY_CORRELATION_ID;
import static pl.mkn.tdw.agenttools.elasticsearch.ElasticToolNames.SUMMARIZE_HTTP_CALLS_BY_PATH;

@Component
@Slf4j
public class ElasticMcpTools {

    private static final String REASON_DESCRIPTION = "Short reason in Polish for the operator. Use one practical sentence.";

    private final ElasticLogPort elasticLogPort;
    private final ElasticLogSearchService elasticLogSearchService;

    @Autowired
    public ElasticMcpTools(
            ElasticLogPort elasticLogPort,
            ElasticLogSearchService elasticLogSearchService
    ) {
        this.elasticLogPort = elasticLogPort;
        this.elasticLogSearchService = elasticLogSearchService;
    }

    public ElasticMcpTools(ElasticLogPort elasticLogPort) {
        this(elasticLogPort, null);
    }

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

    @Tool(
            name = SUMMARIZE_HTTP_CALLS_BY_PATH,
            description = """
                    Summarizes Elasticsearch/Kibana HTTP call logs by a grounded request path or stable path prefix.
                    Use this when the current incident only shows a downstream/external HTTP failure without enough detail
                    and comparing recent successful and failed calls for the same endpoint family can reveal status,
                    payload-shape, constraint or data-pattern differences. Returns status buckets and lightweight samples.
                    """
    )
    public ElasticHttpCallSummaryResult summarizeHttpCallsByPath(
            @ToolParam(description = "Grounded request path fragment or stable endpoint prefix, for example /external/path/.")
            String pathPattern,
            @ToolParam(required = false, description = "Optional HTTP method filter, for example GET or POST.")
            String method,
            @ToolParam(required = false, description = "Optional service/microservice name filter when known from logs.")
            String serviceName,
            @ToolParam(required = false, description = "Lookback window in days from 1 to 30. Defaults to backend configuration.")
            Integer timeWindowDays,
            @ToolParam(required = false, description = "Maximum number of sample hits from 1 to 1000. Defaults to backend configuration.")
            Integer sampleSize,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var startedAt = System.nanoTime();
        logRequest(
                SUMMARIZE_HTTP_CALLS_BY_PATH,
                toolContext,
                "pathPattern=%s method=%s serviceName=%s timeWindowDays=%s sampleSize=%s"
                        .formatted(pathPattern, method, serviceName, timeWindowDays, sampleSize)
        );

        var result = requireLogSearchService(SUMMARIZE_HTTP_CALLS_BY_PATH).summarizeHttpCalls(
                new ElasticHttpCallSummaryRequest(
                        requireText(pathPattern, "pathPattern must not be blank"),
                        method,
                        serviceName,
                        timeWindowDays,
                        sampleSize
                )
        );

        logResult(
                SUMMARIZE_HTTP_CALLS_BY_PATH,
                toolContext,
                startedAt,
                "pathPattern=%s returnedHits=%d totalHits=%d buckets=%d timedOut=%s"
                        .formatted(
                                result.pathPattern(),
                                result.returnedHits(),
                                result.totalHits(),
                                result.statusBuckets().size(),
                                result.timedOut()
                        )
        );

        return result;
    }

    @Tool(
            name = FETCH_HTTP_CALL_LOGS,
            description = """
                    Fetches Elasticsearch/Kibana logs for a concrete HTTP call path, status or method.
                    If path is omitted, the tool fetches logs for the current incident correlationId from hidden ToolContext.
                    If path is provided, it searches by that path without forcing the current incident correlationId, which
                    allows comparison with nearby successful calls returned by the HTTP summary tool.
                    """
    )
    public ElasticHttpCallLogsResult fetchHttpCallLogs(
            @ToolParam(required = false, description = "Concrete request path or path fragment returned by summary samples. If omitted, current hidden correlationId is used.")
            String path,
            @ToolParam(required = false, description = "Optional HTTP status filter from 100 to 599.")
            Integer status,
            @ToolParam(required = false, description = "Optional HTTP method filter, for example GET or POST.")
            String method,
            @ToolParam(required = false, description = "Lookback window in days from 1 to 30. Defaults to backend configuration.")
            Integer timeWindowDays,
            @ToolParam(required = false, description = "Maximum number of log entries from 1 to 200. Defaults to backend configuration.")
            Integer size,
            @ToolParam(required = false, description = "Log detail level: SUMMARY, COMPACT or FULL. Defaults to COMPACT.")
            String detailLevel,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var effectiveCorrelationId = StringUtils.hasText(path)
                ? null
                : contextValue(toolContext, AgentToolContextKeys.CORRELATION_ID);
        if (!StringUtils.hasText(path) && !StringUtils.hasText(effectiveCorrelationId)) {
            throw new IllegalStateException(
                    "path is required when current correlationId is missing from hidden ToolContext."
            );
        }

        var normalizedStatus = validateStatus(status);
        var normalizedDetailLevel = parseDetailLevel(detailLevel);
        var startedAt = System.nanoTime();
        logRequest(
                FETCH_HTTP_CALL_LOGS,
                toolContext,
                "path=%s status=%s method=%s timeWindowDays=%s size=%s detailLevel=%s currentCorrelationIdUsed=%s"
                        .formatted(
                                path,
                                normalizedStatus,
                                method,
                                timeWindowDays,
                                size,
                                normalizedDetailLevel,
                                StringUtils.hasText(effectiveCorrelationId)
                        )
        );

        var result = requireLogSearchService(FETCH_HTTP_CALL_LOGS).fetchHttpCallLogs(
                new ElasticHttpCallLogsRequest(
                        effectiveCorrelationId,
                        path,
                        normalizedStatus,
                        method,
                        timeWindowDays,
                        size,
                        normalizedDetailLevel
                )
        );

        logResult(
                FETCH_HTTP_CALL_LOGS,
                toolContext,
                startedAt,
                "path=%s correlationId=%s returnedHits=%d totalHits=%d timedOut=%s"
                        .formatted(
                                result.path(),
                                result.correlationId(),
                                result.returnedHits(),
                                result.totalHits(),
                                result.timedOut()
                        )
        );

        return result;
    }

    private ElasticLogSearchService requireLogSearchService(String toolName) {
        if (elasticLogSearchService == null) {
            throw new IllegalStateException("Tool " + toolName + " requires ElasticLogSearchService.");
        }
        return elasticLogSearchService;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private Integer validateStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be a valid HTTP status code from 100 to 599.");
        }
        return status;
    }

    private ElasticLogDetailLevel parseDetailLevel(String detailLevel) {
        if (!StringUtils.hasText(detailLevel)) {
            return ElasticLogDetailLevel.COMPACT;
        }
        try {
            return ElasticLogDetailLevel.valueOf(detailLevel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("detailLevel must be one of SUMMARY, COMPACT or FULL.", exception);
        }
    }

    private void logRequest(String toolName, ToolContext toolContext, String details) {
        log.info(
                "Tool request [{}] correlationId={} analysisRunId={} copilotSessionId={} toolCallId={} details={}",
                toolName,
                contextValue(toolContext, AgentToolContextKeys.CORRELATION_ID),
                contextValue(toolContext, AgentToolContextKeys.ANALYSIS_RUN_ID),
                contextValue(toolContext, AgentToolContextKeys.COPILOT_SESSION_ID),
                contextValue(toolContext, AgentToolContextKeys.TOOL_CALL_ID),
                details
        );
    }

    private void logResult(String toolName, ToolContext toolContext, long startedAt, String details) {
        log.info(
                "Tool result [{}] correlationId={} analysisRunId={} copilotSessionId={} toolCallId={} durationMs={} details={}",
                toolName,
                contextValue(toolContext, AgentToolContextKeys.CORRELATION_ID),
                contextValue(toolContext, AgentToolContextKeys.ANALYSIS_RUN_ID),
                contextValue(toolContext, AgentToolContextKeys.COPILOT_SESSION_ID),
                contextValue(toolContext, AgentToolContextKeys.TOOL_CALL_ID),
                (System.nanoTime() - startedAt) / 1_000_000,
                details
        );
    }

    private String contextValue(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        var value = toolContext.getContext().get(key);
        return value != null ? value.toString() : null;
    }

}
