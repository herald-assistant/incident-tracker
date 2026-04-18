package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DynatraceRestIncidentAdapterTest {

    @Test
    void shouldFetchServiceProblemsAndMetricsForBestMatchingEntity() {
        var properties = dynatraceProperties();
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new DynatraceRestIncidentAdapter(
                properties,
                DynatraceRestClientFactory.forMockServer(restClientBuilder)
        );

        expectGet(server, """
                {
                  "totalCount": 3,
                  "pageSize": 200,
                  "entities": [
                    {
                      "entityId": "SERVICE-CONTROLLER",
                      "type": "SERVICE",
                      "displayName": "backend || controller"
                    },
                    {
                      "entityId": "SERVICE-BACKEND",
                      "type": "SERVICE",
                      "displayName": "backend || case-evaluation-service"
                    },
                    {
                      "entityId": "SERVICE-OTHER",
                      "type": "SERVICE",
                      "displayName": "alerts"
                    }
                  ]
                }
                """,
                "/api/v2/entities",
                "entitySelector=type(%22SERVICE%22)",
                "pageSize=200"
        );
        expectGet(server, """
                {
                  "totalCount": 1,
                  "pageSize": 10,
                  "problems": [
                    {
                      "problemId": "-7738361456728905949_1775948280000V2",
                      "displayId": "P-26042756",
                      "title": "Multiple service problems",
                      "impactLevel": "SERVICES",
                      "severityLevel": "ERROR",
                      "status": "CLOSED",
                      "affectedEntities": [
                        {
                          "name": "ns"
                        },
                        {
                          "name": "svc"
                        }
                      ],
                      "impactedEntities": [
                        {
                          "name": "ns"
                        },
                        {
                          "name": "svc"
                        }
                      ],
                      "rootCauseEntity": {
                        "entityId": {
                          "id": "SERVICE-BACKEND"
                        },
                        "name": "svc"
                      },
                      "startTime": 1775948580000,
                      "endTime": 1775949000000,
                      "evidenceDetails": {
                        "details": [
                          {
                            "evidenceType": "EVENT",
                            "displayName": "fail",
                            "entity": {
                              "name": "svc"
                            },
                            "rootCauseRelevant": true,
                            "eventType": "SERVICE_ERROR_RATE_INCREASED",
                            "startTime": 1775948280000,
                            "endTime": 1775949000000
                          },
                          {
                            "evidenceType": "TRANSACTIONAL",
                            "displayName": "latency",
                            "entity": {
                              "name": "svc"
                            },
                            "rootCauseRelevant": true,
                            "unit": "MicroSecond",
                            "valueBeforeChangePoint": 7013.5,
                            "valueAfterChangePoint": 563702.3,
                            "startTime": 1775947440000,
                            "endTime": 1775948640000
                          }
                        ]
                      }
                    }
                  ]
                }
                """,
                "/api/v2/problems",
                "entitySelector=entityId(%22SERVICE-BACKEND%22)",
                "problemSelector=severityLevel(%22ERROR%22,%22PERFORMANCE%22)"
        );
        expectMetric(server, "builtin:service.response.time:percentile(95)", """
                {
                  "resolution": "1m",
                  "result": [
                    {
                      "data": [
                        {
                          "dimensionMap": {
                            "dt.entity.service": "SERVICE-BACKEND"
                          },
                          "values": [null, 6385, null, 8670]
                        }
                      ]
                    }
                  ]
                }
                """);
        expectMetric(server, "builtin:service.errors.total.count:value:rate(1m):default(0)", """
                {
                  "resolution": "1m",
                  "result": [
                    {
                      "data": [
                        {
                          "dimensionMap": {
                            "dt.entity.service": "SERVICE-BACKEND"
                          },
                          "values": [0, 0, 0, 0]
                        }
                      ]
                    }
                  ]
                }
                """);
        expectMetric(server, "builtin:service.errors.fourxx.count:value:rate(1m):default(0)", """
                {
                  "resolution": "1m",
                  "result": [
                    {
                      "data": [
                        {
                          "dimensionMap": {
                            "dt.entity.service": "SERVICE-BACKEND"
                          },
                          "values": [0, 0, 0, 0]
                        }
                      ]
                    }
                  ]
                }
                """);
        expectMetric(server, "builtin:service.errors.fivexx.count:value:rate(1m):default(0)", """
                {
                  "resolution": "1m",
                  "result": [
                    {
                      "data": [
                        {
                          "dimensionMap": {
                            "dt.entity.service": "SERVICE-BACKEND"
                          },
                          "values": [0, 1, 2, 1]
                        }
                      ]
                    }
                  ]
                }
                """);
        expectMetric(server, "builtin:service.successes.server.rate:avg", """
                {
                  "resolution": "1m",
                  "result": [
                    {
                      "data": [
                        {
                          "dimensionMap": {
                            "dt.entity.service": "SERVICE-BACKEND"
                          },
                          "values": [100, 100, 100, 100]
                        }
                      ]
                    }
                  ]
                }
                """);

        var evidence = adapter.loadIncidentEvidence(new DynatraceIncidentQuery(
                "corr-123",
                Instant.parse("2026-04-11T20:57:33.285Z"),
                Instant.parse("2026-04-11T20:57:33.285Z"),
                java.util.List.of("tenant-alpha-main-dev3"),
                java.util.List.of("backend-65df9ffbbb-79p7j"),
                java.util.List.of("backend"),
                java.util.List.of("case-evaluation-service")
        ));

        assertEquals(1, evidence.serviceMatches().size());
        assertEquals("SERVICE-BACKEND", evidence.serviceMatches().get(0).entityId());
        assertEquals(1, evidence.problems().size());
        assertEquals("P-26042756", evidence.problems().get(0).displayId());
        assertEquals(3, evidence.metrics().size());
        assertEquals("service.response.time.p95", evidence.metrics().get(0).metricLabel());
        assertEquals("ms", evidence.metrics().get(0).unit());
        assertEquals(6.39d, evidence.metrics().get(0).minValue(), 0.01d);
        assertEquals(8.67d, evidence.metrics().get(0).maxValue(), 0.01d);
        assertEquals(7.53d, evidence.metrics().get(0).averageValue(), 0.01d);
        assertEquals(8.67d, evidence.metrics().get(0).lastValue(), 0.01d);
        assertEquals("service.errors.5xx.rate", evidence.metrics().get(1).metricLabel());
        assertEquals("service.success.rate", evidence.metrics().get(2).metricLabel());

        server.verify();
    }

    private static void expectMetric(MockRestServiceServer server, String selector, String responseBody) {
        expectGet(server, responseBody,
                "/api/v2/metrics/query",
                "metricSelector=" + selector,
                "entitySelector=entityId(%22SERVICE-BACKEND%22)"
        );
    }

    private static void expectGet(MockRestServiceServer server, String responseBody, String... uriFragments) {
        server.expect(request -> {
                    assertEquals(HttpMethod.GET, request.getMethod());
                    assertEquals("Api-Token dt0c01.test-token", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                    var uri = request.getURI().toString();
                    for (var fragment : uriFragments) {
                        assertTrue(uri.contains(fragment), "Missing URI fragment: " + fragment + " in " + uri);
                    }
                })
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private static DynatraceProperties dynatraceProperties() {
        var properties = new DynatraceProperties();
        properties.setBaseUrl("https://managed.example.com/e/environment-id");
        properties.setApiToken("dt0c01.test-token");
        properties.setEntityCandidateLimit(1);
        properties.setMetricEntityLimit(1);
        return properties;
    }

}

