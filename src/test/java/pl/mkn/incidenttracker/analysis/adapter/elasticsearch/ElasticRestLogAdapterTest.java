package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElasticRestLogAdapterTest {

    @Test
    void shouldSearchLogsThroughKibanaProxyAndMapStructuredEntry() {
        var properties = elasticProperties();
        properties.setToolSize(2);
        properties.setToolMaxMessageCharacters(120);
        properties.setToolMaxExceptionCharacters(40);
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new ElasticRestLogAdapter(
                properties,
                new ElasticLogSearchClient(ElasticRestClientFactory.forMockServer(restClientBuilder))
        );

        server.expect(requestTo(
                        "https://openshift-test.example.internal/s/default/api/console/proxy?path=logs-*/_search&method=GET"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "size": 2,
                          "sort": [
                            {
                              "@timestamp": "asc"
                            }
                          ],
                          "query": {
                            "bool": {
                              "filter": [
                                {
                                  "term": {
                                    "fields.correlationId": "69dab5bdc21dbf9099025d075e682e8a"
                                  }
                                }
                              ]
                            }
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "took": 307,
                          "timed_out": false,
                          "hits": {
                            "total": {
                              "value": 1,
                              "relation": "eq"
                            },
                            "hits": [
                              {
                                "_index": ".ds-projects.TENANT-ALPHA.prj000000104201-2026.03.27-000377",
                                "_id": "AZ1-Vg6o1WPiTTEp9_tC",
                                "_source": {
                                  "@timestamp": "2026-04-11T20:57:33.285Z",
                                  "fields": {
                                    "class": "c.e.synthetic.workflow.WorkflowApiExceptionHandler",
                                    "correlationId": "69dab5bdc21dbf9099025d075e682e8a",
                                    "exception": "com.example.synthetic.workflowstate.services.common.exception.EntityNotFoundException: ActiveCaseRecord with caseId 7001234567 not found",
                                    "message": "Loan processing exception",
                                    "microservice": "case-evaluation-service",
                                    "spanId": "a8cdba8fe9a5ec96",
                                    "thread": "https-jsse-nio-8443-exec-10",
                                    "type": "ERROR"
                                  },
                                  "kubernetes": {
                                    "container": {
                                      "name": "backend"
                                    },
                                    "namespace": "tenant-alpha-main-dev1",
                                    "pod": {
                                      "name": "backend-846b75885c-4v4gp"
                                    }
                                  },
                                  "container": {
                                    "image": {
                                      "name": "reg.local/tenant-alpha-main-dev1/backend:20260409"
                                    }
                                  }
                                }
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.searchLogsByCorrelationId("69dab5bdc21dbf9099025d075e682e8a");

        assertEquals(1, result.returnedHits());
        assertEquals(1, result.totalHits());
        assertEquals(307, result.tookMillis());
        assertEquals("OK", result.message());
        assertEquals("ERROR", result.entries().get(0).level());
        assertEquals("case-evaluation-service", result.entries().get(0).serviceName());
        assertEquals("c.e.synthetic.workflow.WorkflowApiExceptionHandler", result.entries().get(0).className());
        assertEquals("tenant-alpha-main-dev1", result.entries().get(0).namespace());
        assertTrue(result.entries().get(0).exceptionTruncated());
        assertEquals(40, result.entries().get(0).exception().length());

        server.verify();
    }

    @Test
    void shouldUseEvidenceDefaultsWhenFindingLogEntries() {
        var properties = elasticProperties();
        properties.setEvidenceSize(1);
        properties.setEvidenceMaxMessageCharacters(5);
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new ElasticRestLogAdapter(
                properties,
                new ElasticLogSearchClient(ElasticRestClientFactory.forMockServer(restClientBuilder))
        );

        server.expect(requestTo(
                        "https://openshift-test.example.internal/s/default/api/console/proxy?path=logs-*/_search&method=GET"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "size": 1,
                          "sort": [
                            {
                              "@timestamp": "asc"
                            }
                          ],
                          "query": {
                            "bool": {
                              "filter": [
                                {
                                  "term": {
                                    "fields.correlationId": "69da964c3e384d44c92482ea7357d578"
                                  }
                                }
                              ]
                            }
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "took": 54,
                          "timed_out": false,
                          "hits": {
                            "total": {
                              "value": 1,
                              "relation": "eq"
                            },
                            "hits": [
                              {
                                "_index": ".ds-projects.TENANT-ALPHA.prj000000104201-2026.03.27-000377",
                                "_id": "AZ1929sS-w-FXjZZz8cy",
                                "_source": {
                                  "@timestamp": "2026-04-11T18:43:24.519Z",
                                  "fields": {
                                    "class": "c.e.synthetic.security.RequestContextFilter",
                                    "correlationId": "69da964c3e384d44c92482ea7357d578",
                                    "message": "Authenticated user 'user-204', setting security context",
                                    "microservice": "case-evaluation-service",
                                    "thread": "https-jsse-nio-8443-exec-7",
                                    "type": "INFO"
                                  }
                                }
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var entries = adapter.findLogEntries("69da964c3e384d44c92482ea7357d578");

        assertEquals(1, entries.size());
        assertEquals("Authe", entries.get(0).message());
        assertTrue(entries.get(0).messageTruncated());

        server.verify();
    }

    private static ElasticProperties elasticProperties() {
        var properties = new ElasticProperties();
        properties.setBaseUrl("https://openshift-test.example.internal");
        properties.setKibanaSpaceId("default");
        properties.setIndexPattern("logs-*");
        return properties;
    }

}


