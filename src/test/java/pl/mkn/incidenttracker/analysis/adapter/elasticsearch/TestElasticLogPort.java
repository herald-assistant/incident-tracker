package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import java.util.List;

public class TestElasticLogPort implements ElasticLogPort {

    @Override
    public List<ElasticLogEntry> findLogEntries(String correlationId) {
        if ("not-found".equals(correlationId)) {
            return List.of();
        }

        if (correlationId.startsWith("timeout-")) {
            return List.of(
                    testEntry(
                            "2026-04-11T20:57:33.285Z",
                            "ERROR",
                            "svc",
                            "c.e.s.response.TimeoutHandler",
                            "Catalog call timed out",
                            null,
                            "backend",
                            "r/tenant-alpha-main-dev3/backend:20260411-205733-1-dev-atlas-0123456789abcdef0123456789abcdef01234567"
                    ),
                    testEntry(
                            "2026-04-11T20:57:33.290Z",
                            "WARN",
                            "svc",
                            "c.e.s.integration.TimeoutLogger",
                            "Request timeout exceeded",
                            null,
                            "backend",
                            "r/tenant-alpha-main-dev3/backend:20260411-205733-1-dev-atlas-0123456789abcdef0123456789abcdef01234567"
                    )
            );
        }

        if (correlationId.startsWith("db-lock-")) {
            return List.of(
                    testEntry(
                            "2026-04-11T20:49:03.325Z",
                            "ERROR",
                            "svc",
                            "c.e.s.workflow.ApiHandler",
                            "Deadlock updating order",
                            "ex.MissingRecord: not found\n"
                                    + "\tat com.example.synthetic.workflowstate.domain.core.ActiveCaseRecordDomainRepository.get(ActiveCaseRecordDomainRepository.java:74)\n"
                                    + "\tat com.example.synthetic.workflowstate.services.core.ActiveCaseRecordQueryService.get(ActiveCaseRecordQueryService.java:69)",
                            "backend",
                            "r/tenant-alpha-main-dev1/backend:20260411-204903-1-dev-zephyr-89abcdef0123456789abcdef0123456789abcdef"
                    ),
                    testEntry(
                            "2026-04-11T20:49:03.332Z",
                            "WARN",
                            "svc",
                            "c.e.s.throughput.LoadGate",
                            "Lock wait too long",
                            null,
                            "backend",
                            "r/tenant-alpha-main-dev1/backend:20260411-204903-1-dev-zephyr-89abcdef0123456789abcdef0123456789abcdef"
                    )
            );
        }

        return List.of(
                testEntry(
                        "2026-04-11T18:43:24.519Z",
                        "INFO",
                        "svc",
                        "c.e.s.security.CtxFilter",
                        "CorrelationId " + correlationId + " found in logs",
                        null,
                        "backend",
                        "r/tenant-alpha-main-dev2/backend:20260411-184324-1-dev-quartz-fedcba9876543210fedcba9876543210fedcba98"
                ),
                testEntry(
                        "2026-04-11T18:43:24.525Z",
                        "INFO",
                        "svc",
                        "c.e.s.security.CtxFilter",
                        "No strong error pattern found",
                        null,
                        "backend",
                        "r/tenant-alpha-main-dev2/backend:20260411-184324-1-dev-quartz-fedcba9876543210fedcba9876543210fedcba98"
                )
        );
    }

    @Override
    public ElasticLogSearchResult searchLogsByCorrelationId(String correlationId) {
        var entries = findLogEntries(correlationId);

        return new ElasticLogSearchResult(
                correlationId,
                "test",
                entries.size(),
                entries.size(),
                entries.size(),
                0,
                false,
                entries,
                entries.isEmpty()
                        ? "No Elasticsearch logs found for correlationId: " + correlationId
                        : "OK"
        );
    }

    private ElasticLogEntry testEntry(
            String timestamp,
            String level,
            String serviceName,
            String className,
            String message,
            String exception,
            String containerName,
            String containerImage
    ) {
        return new ElasticLogEntry(
                timestamp,
                level,
                serviceName,
                className,
                message,
                exception,
                "main",
                null,
                "ns",
                "pod",
                containerName,
                containerImage,
                "test-index",
                serviceName + "-" + level,
                false,
                false
        );
    }

}


