package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ElasticRestLogAdapter implements ElasticLogPort {

    private final ElasticProperties properties;
    private final ElasticLogSearchClient elasticLogSearchClient;

    @Override
    public List<ElasticLogEntry> findLogEntries(String correlationId) {
        return search(evidenceCriteria(correlationId)).entries();
    }

    @Override
    public ElasticLogSearchResult searchLogsByCorrelationId(String correlationId) {
        return search(toolCriteria(correlationId));
    }

    private ElasticLogSearchResult search(ElasticLogSearchCriteria criteria) {
        try {
            return elasticLogSearchClient.search(
                    configuredConnectionDetails(),
                    criteria
            );
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Elasticsearch/Kibana log search failed for correlationId " + criteria.correlationId()
                            + " with status " + exception.getStatusCode().value(),
                    exception
            );
        }
    }

    private ElasticConnectionDetails configuredConnectionDetails() {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("analysis.elasticsearch.base-url must be configured.");
        }

        return new ElasticConnectionDetails(
                properties.getBaseUrl(),
                properties.getAuthorizationHeader()
        );
    }

    private ElasticLogSearchCriteria evidenceCriteria(String correlationId) {
        return configuredCriteria(
                correlationId,
                properties.getEvidenceSize(),
                properties.getEvidenceMaxMessageCharacters(),
                properties.getEvidenceMaxExceptionCharacters()
        );
    }

    private ElasticLogSearchCriteria toolCriteria(String correlationId) {
        return configuredCriteria(
                correlationId,
                properties.getToolSize(),
                properties.getToolMaxMessageCharacters(),
                properties.getToolMaxExceptionCharacters()
        );
    }

    private ElasticLogSearchCriteria configuredCriteria(
            String correlationId,
            int size,
            int maxMessageCharacters,
            int maxExceptionCharacters
    ) {
        return new ElasticLogSearchCriteria(
                effectiveKibanaSpaceId(),
                effectiveIndexPattern(),
                correlationId,
                size,
                maxMessageCharacters,
                maxExceptionCharacters
        );
    }

    private String effectiveKibanaSpaceId() {
        return StringUtils.hasText(properties.getKibanaSpaceId()) ? properties.getKibanaSpaceId().trim() : "default";
    }

    private String effectiveIndexPattern() {
        return StringUtils.hasText(properties.getIndexPattern()) ? properties.getIndexPattern().trim() : "logs-*";
    }

}
