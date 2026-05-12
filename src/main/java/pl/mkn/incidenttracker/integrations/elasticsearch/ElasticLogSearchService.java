package pl.mkn.incidenttracker.integrations.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ElasticLogSearchService {

    private final ElasticProperties properties;
    private final ElasticLogSearchClient elasticLogSearchClient;

    public ElasticLogSearchResult search(ElasticLogSearchRequest request) {
        return executeSearch(
                request.correlationId().trim(),
                properties.getSearchSize(),
                properties.getSearchMaxMessageCharacters(),
                properties.getSearchMaxExceptionCharacters()
        );
    }

    public ElasticHttpCallSummaryResult summarizeHttpCalls(ElasticHttpCallSummaryRequest request) {
        var criteria = new ElasticHttpCallSummaryCriteria(
                effectiveKibanaSpaceId(),
                effectiveIndexPattern(),
                request.pathPattern().trim(),
                trimOrNull(request.method()),
                trimOrNull(request.serviceName()),
                effectiveValue(request.timeWindowDays(), properties.getHttpSummaryTimeWindowDays(), 30),
                effectiveValue(request.sampleSize(), properties.getHttpSummarySize(), 1_000)
        );

        try {
            return elasticLogSearchClient.summarizeHttpCalls(configuredConnectionDetails(), criteria);
        } catch (RestClientResponseException exception) {
            throw mapHttpFailure("HTTP_CALL_SUMMARY", exception);
        }
    }

    public ElasticHttpCallLogsResult fetchHttpCallLogs(ElasticHttpCallLogsRequest request) {
        var detailLevel = request.detailLevel() != null ? request.detailLevel() : ElasticLogDetailLevel.COMPACT;
        var criteria = new ElasticHttpCallLogsCriteria(
                effectiveKibanaSpaceId(),
                effectiveIndexPattern(),
                trimOrNull(request.correlationId()),
                trimOrNull(request.path()),
                request.status(),
                trimOrNull(request.method()),
                effectiveValue(request.timeWindowDays(), properties.getHttpFetchTimeWindowDays(), 30),
                effectiveValue(request.size(), properties.getHttpFetchSize(), 200),
                maxMessageCharacters(detailLevel),
                maxExceptionCharacters(detailLevel),
                detailLevel
        );

        try {
            return elasticLogSearchClient.fetchHttpCallLogs(configuredConnectionDetails(), criteria);
        } catch (RestClientResponseException exception) {
            throw mapHttpFailure("HTTP_CALL_LOGS", exception);
        }
    }

    private ElasticLogSearchResult executeSearch(
            String correlationId,
            int size,
            int maxMessageCharacters,
            int maxExceptionCharacters
    ) {
        try {
            var response = elasticLogSearchClient.search(
                    configuredConnectionDetails(),
                    new ElasticLogSearchCriteria(
                            effectiveKibanaSpaceId(),
                            effectiveIndexPattern(),
                            correlationId,
                            size,
                            maxMessageCharacters,
                            maxExceptionCharacters
                    )
            );

            if (response.entries().isEmpty()) {
                throw failure(HttpStatus.NOT_FOUND, response);
            }

            return response;
        } catch (RestClientResponseException exception) {
            throw mapFailure(exception, correlationId, size);
        }
    }

    private ElasticLogSearchException mapFailure(RestClientResponseException exception, String correlationId, int size) {
        var status = exception.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;

        return failure(
                status,
                new ElasticLogSearchResult(
                        correlationId,
                        effectiveIndexPattern(),
                        size,
                        0,
                        0,
                        0,
                        false,
                        List.of(),
                        buildFailureMessage(exception)
                )
        );
    }

    private ElasticHttpCallSearchException mapHttpFailure(String operation, RestClientResponseException exception) {
        var status = exception.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;

        return new ElasticHttpCallSearchException(
                status,
                new ElasticHttpCallDiagnosticError(
                        operation,
                        effectiveIndexPattern(),
                        buildFailureMessage(exception)
                )
        );
    }

    private String buildFailureMessage(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 404) {
            return "Elasticsearch/Kibana endpoint not found for space "
                    + effectiveKibanaSpaceId()
                    + " at "
                    + properties.getBaseUrl();
        }

        return "Elasticsearch/Kibana log search failed with status " + exception.getStatusCode().value();
    }

    private ElasticLogSearchException failure(HttpStatus status, ElasticLogSearchResult response) {
        return new ElasticLogSearchException(status, response);
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

    private String effectiveKibanaSpaceId() {
        return StringUtils.hasText(properties.getKibanaSpaceId()) ? properties.getKibanaSpaceId().trim() : "default";
    }

    private String effectiveIndexPattern() {
        return StringUtils.hasText(properties.getIndexPattern()) ? properties.getIndexPattern().trim() : "logs-*";
    }

    private int effectiveValue(Integer requested, int configuredDefault, int max) {
        var effective = requested != null ? requested : configuredDefault;
        if (effective <= 0) {
            effective = configuredDefault;
        }
        return Math.min(effective, max);
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int maxMessageCharacters(ElasticLogDetailLevel detailLevel) {
        return switch (detailLevel) {
            case SUMMARY -> properties.getHttpFetchSummaryMaxMessageCharacters();
            case FULL -> Integer.MAX_VALUE;
            case COMPACT -> properties.getHttpFetchCompactMaxMessageCharacters();
        };
    }

    private int maxExceptionCharacters(ElasticLogDetailLevel detailLevel) {
        return switch (detailLevel) {
            case SUMMARY -> properties.getHttpFetchSummaryMaxExceptionCharacters();
            case FULL -> Integer.MAX_VALUE;
            case COMPACT -> properties.getHttpFetchCompactMaxExceptionCharacters();
        };
    }

}
