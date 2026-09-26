package pl.mkn.tdw.testsupport.agenttools;

import pl.mkn.tdw.agenttools.elasticsearch.mcp.ElasticMcpTools;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogPort;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogSearchPort;

public final class ElasticMcpToolsTestCreator {

    private ElasticMcpToolsTestCreator() {
    }

    public static ElasticMcpTools create(ElasticLogPort elasticLogPort) {
        return new ElasticMcpTools(elasticLogPort, null);
    }

    public static ElasticMcpTools create(
            ElasticLogPort elasticLogPort,
            ElasticLogSearchPort elasticLogSearchService
    ) {
        return new ElasticMcpTools(elasticLogPort, elasticLogSearchService);
    }
}
