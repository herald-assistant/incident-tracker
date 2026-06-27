package pl.mkn.tdw.agenttools.elasticsearch;

public final class ElasticToolNames {

    public static final String PREFIX = "elastic_";

    public static final String SEARCH_LOGS_BY_CORRELATION_ID = PREFIX + "search_logs_by_correlation_id";
    public static final String SUMMARIZE_HTTP_CALLS_BY_PATH = PREFIX + "summarize_http_calls_by_path";
    public static final String FETCH_HTTP_CALL_LOGS = PREFIX + "fetch_http_call_logs";

    private ElasticToolNames() {
    }
}
