package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import java.time.Instant;
import java.util.List;

public class TestDynatraceIncidentPort implements DynatraceIncidentPort {

    @Override
    public DynatraceIncidentEvidence loadIncidentEvidence(DynatraceIncidentQuery query) {
        if ("not-found".equals(query.correlationId())) {
            return DynatraceIncidentEvidence.empty();
        }

        if (query.correlationId().startsWith("timeout-")) {
            return new DynatraceIncidentEvidence(
                    List.of(new DynatraceIncidentEvidence.ServiceMatch(
                            "SERVICE-TIMEOUT",
                            "svc",
                            320,
                            query.namespaces(),
                            query.podNames(),
                            query.containerNames(),
                            query.serviceNames()
                    )),
                    List.of(new DynatraceIncidentEvidence.ProblemSummary(
                            "-7738361456728905949_1775948280000V2",
                            "P-26042756",
                            "timeout",
                            "SERVICES",
                            "ERROR",
                            "CLOSED",
                            Instant.parse("2026-04-11T20:58:00Z"),
                            Instant.parse("2026-04-11T21:10:00Z"),
                            "SERVICE-TIMEOUT",
                            "svc",
                            List.of("svc"),
                            List.of("svc"),
                            List.of(
                                    new DynatraceIncidentEvidence.ProblemEvidence(
                                            "EVENT",
                                            "fail",
                                            "svc",
                                            null,
                                            true,
                                            "SERVICE_ERROR_RATE_INCREASED",
                                            null,
                                            null,
                                            null,
                                            null,
                                            Instant.parse("2026-04-11T20:58:00Z"),
                                            Instant.parse("2026-04-11T21:10:00Z")
                                    ),
                                    new DynatraceIncidentEvidence.ProblemEvidence(
                                            "TRANSACTIONAL",
                                            "latency",
                                            "svc",
                                            null,
                                            true,
                                            null,
                                            null,
                                            "MicroSecond",
                                            7013.5,
                                            563702.3,
                                            Instant.parse("2026-04-11T20:50:00Z"),
                                            Instant.parse("2026-04-11T21:05:00Z")
                                    )
                            )
                    )),
                    List.of(
                            new DynatraceIncidentEvidence.MetricSummary(
                                    "SERVICE-TIMEOUT",
                                    "svc",
                                    "builtin:service.response.time:percentile(95)",
                                    "service.response.time.p95",
                                    "ms",
                                    "1m",
                                    query.incidentStart(),
                                    query.incidentEnd(),
                                    3,
                                    6.39,
                                    8.67,
                                    7.28,
                                    8.67
                            ),
                            new DynatraceIncidentEvidence.MetricSummary(
                                    "SERVICE-TIMEOUT",
                                    "svc",
                                    "builtin:service.successes.server.rate:avg",
                                    "service.success.rate",
                                    "%",
                                    "1m",
                                    query.incidentStart(),
                                    query.incidentEnd(),
                                    3,
                                    100.0,
                                    100.0,
                                    100.0,
                                    100.0
                            )
                    )
            );
        }

        if (query.correlationId().startsWith("db-lock-")) {
            return new DynatraceIncidentEvidence(
                    List.of(new DynatraceIncidentEvidence.ServiceMatch(
                            "SERVICE-DB-LOCK",
                            "svc",
                            310,
                            query.namespaces(),
                            query.podNames(),
                            query.containerNames(),
                            query.serviceNames()
                    )),
                    List.of(new DynatraceIncidentEvidence.ProblemSummary(
                            "-1860977652372487379_1775945100000V2",
                            "P-26042703",
                            "db lock",
                            "SERVICES",
                            "ERROR",
                            "CLOSED",
                            Instant.parse("2026-04-11T20:50:00Z"),
                            Instant.parse("2026-04-11T21:09:00Z"),
                            "SERVICE-DB-LOCK",
                            "svc",
                            List.of("svc"),
                            List.of("svc"),
                            List.of(
                                    new DynatraceIncidentEvidence.ProblemEvidence(
                                            "EVENT",
                                            "db",
                                            "svc",
                                            null,
                                            true,
                                            "DATABASE_CONNECTION_FAILURE",
                                            null,
                                            null,
                                            null,
                                            null,
                                            Instant.parse("2026-04-11T20:50:00Z"),
                                            Instant.parse("2026-04-11T21:09:00Z")
                                    ),
                                    new DynatraceIncidentEvidence.ProblemEvidence(
                                            "TRANSACTIONAL",
                                            "throughput",
                                            "svc",
                                            null,
                                            false,
                                            null,
                                            null,
                                            "PerMinute",
                                            0.0,
                                            238.4,
                                            Instant.parse("2026-04-11T20:44:00Z"),
                                            Instant.parse("2026-04-11T21:04:00Z")
                                    )
                            )
                    )),
                    List.of(new DynatraceIncidentEvidence.MetricSummary(
                            "SERVICE-DB-LOCK",
                            "svc",
                            "builtin:service.errors.fivexx.count:value:rate(1m):default(0)",
                            "service.errors.5xx.rate",
                            "count/min",
                            "1m",
                            query.incidentStart(),
                            query.incidentEnd(),
                            4,
                            0.0,
                            4.0,
                            2.0,
                            4.0
                    ))
            );
        }

        return new DynatraceIncidentEvidence(
                List.of(new DynatraceIncidentEvidence.ServiceMatch(
                        "SERVICE-GENERIC",
                        "svc",
                        250,
                        query.namespaces(),
                        query.podNames(),
                        query.containerNames(),
                        query.serviceNames()
                )),
                List.of(),
                List.of(new DynatraceIncidentEvidence.MetricSummary(
                        "SERVICE-GENERIC",
                        "svc",
                        "builtin:service.successes.server.rate:avg",
                        "service.success.rate",
                        "%",
                        "1m",
                        query.incidentStart(),
                        query.incidentEnd(),
                        3,
                        100.0,
                        100.0,
                        100.0,
                        100.0
                ))
        );
    }

}

