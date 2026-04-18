package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElasticLogSearchServiceTest {

    @Test
    void shouldSearchLogsUsingConfiguredConnectionDetails() {
        var serviceFixture = newServiceFixture();

        serviceFixture.server.expect(requestTo(
                        "https://openshift-test.example.internal/s/default/api/console/proxy?path=logs-*/_search&method=GET"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "size": 200,
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
                                    "fields.correlationId": "69dab5cf730bcfb7213791c7c60713da"
                                  }
                                }
                              ]
                            }
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "took": 68,
                          "timed_out": false,
                          "hits": {
                            "total": {
                              "value": 1,
                              "relation": "eq"
                            },
                            "hits": [
                              {
                                "_index": ".ds-projects.TENANT-ALPHA.prj000000104201-2026.03.27-000377",
                                "_id": "AZ1-VqcWw8HLH2XqEH6M",
                                "_source": {
                                  "@timestamp": "2026-04-11T20:58:21.520Z",
                                  "fields": {
                                    "class": "c.e.synthetic.integration.CustomFeignLogger",
                                    "correlationId": "69dab5cf730bcfb7213791c7c60713da",
                                    "exception": "",
                                    "message": "FeignClient: Received response for request PUT with status: 504 and body: Gateway Timeout",
                                    "microservice": "case-evaluation-service",
                                    "spanId": "9523d3e5142ca4a8",
                                    "thread": "https-jsse-nio-8443-exec-7",
                                    "type": "INFO"
                                  },
                                  "kubernetes": {
                                    "container": {
                                      "name": "backend"
                                    },
                                    "namespace": "tenant-alpha-main-dev1",
                                    "pod": {
                                      "name": "backend-846b75885c-4v4gp"
                                    }
                                  }
                                }
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = serviceFixture.service.search(new ElasticLogSearchRequest(
                "69dab5cf730bcfb7213791c7c60713da"
        ));

        assertEquals("69dab5cf730bcfb7213791c7c60713da", response.correlationId());
        assertEquals("logs-*", response.indexPattern());
        assertEquals(200, response.requestedSize());
        assertEquals(1, response.returnedHits());
        assertEquals("tenant-alpha-main-dev1", response.entries().get(0).namespace());

        serviceFixture.server.verify();
    }

    @Test
    void shouldReturnReadableErrorWhenNoLogsAreFound() {
        var serviceFixture = newServiceFixture();

        serviceFixture.server.expect(requestTo(
                        "https://openshift-test.example.internal/s/default/api/console/proxy?path=logs-*/_search&method=GET"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "took": 10,
                          "timed_out": false,
                          "hits": {
                            "total": {
                              "value": 0,
                              "relation": "eq"
                            },
                            "hits": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                ElasticLogSearchException.class,
                () -> serviceFixture.service.search(new ElasticLogSearchRequest(
                        "missing-correlation-id"
                ))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("No Elasticsearch logs found for correlationId: missing-correlation-id", exception.getResponse().message());
        assertTrue(exception.getResponse().entries().isEmpty());

        serviceFixture.server.verify();
    }

    @Test
    void shouldReturnReadableErrorWhenProxyEndpointIsMissing() {
        var serviceFixture = newServiceFixture();

        serviceFixture.server.expect(requestTo(
                        "https://openshift-test.example.internal/s/default/api/console/proxy?path=logs-*/_search&method=GET"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        var exception = assertThrows(
                ElasticLogSearchException.class,
                () -> serviceFixture.service.search(new ElasticLogSearchRequest(
                        "69da964c3e384d44c92482ea7357d578"
                ))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(
                "Elasticsearch/Kibana endpoint not found for space default at https://openshift-test.example.internal",
                exception.getResponse().message()
        );

        serviceFixture.server.verify();
    }

    private ServiceFixture newServiceFixture() {
        var properties = new ElasticProperties();
        properties.setBaseUrl("https://openshift-test.example.internal");
        properties.setKibanaSpaceId("default");
        properties.setIndexPattern("logs-*");
        properties.setSearchSize(200);
        properties.setSearchMaxMessageCharacters(2_000);
        properties.setSearchMaxExceptionCharacters(6_000);
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var service = new ElasticLogSearchService(
                properties,
                new ElasticLogSearchClient(ElasticRestClientFactory.forMockServer(restClientBuilder))
        );
        return new ServiceFixture(service, server);
    }

    private record ServiceFixture(
            ElasticLogSearchService service,
            MockRestServiceServer server
    ) {
    }

}


