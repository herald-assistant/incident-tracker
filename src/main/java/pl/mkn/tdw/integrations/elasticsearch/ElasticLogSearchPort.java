package pl.mkn.tdw.integrations.elasticsearch;

import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallLogsRequest;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallLogsResult;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallSummaryRequest;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallSummaryResult;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticLogSearchRequest;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticLogSearchResult;

public interface ElasticLogSearchPort {

    ElasticLogSearchResult search(ElasticLogSearchRequest request);

    ElasticHttpCallSummaryResult summarizeHttpCalls(ElasticHttpCallSummaryRequest request);

    ElasticHttpCallLogsResult fetchHttpCallLogs(ElasticHttpCallLogsRequest request);
}
