package pl.mkn.tdw.features.changeverification.execution;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationNameValueResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeAssertionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeCleanupResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeDbAssertionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeExecutionRequest;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChangeVerificationSmokeExecutionServiceTest {

    @Test
    void shouldExecuteReadyHttpSmokeTestAndApplyPolicies() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://test.example.com/api/customers/123?includeStatus=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"ACTIVE\"}", MediaType.APPLICATION_JSON));

        var service = new ChangeVerificationSmokeExecutionService(
                restClientBuilder,
                new ChangeVerificationExecutionProperties()
        );

        var result = service.execute(
                smokePack(),
                new ChangeVerificationSmokeExecutionRequest(
                        "https://test.example.com",
                        "test",
                        "crm",
                        List.of(),
                        Map.of("customerId", "123"),
                        false
                )
        );

        assertThat(result).singleElement()
                .satisfies(test -> {
                    assertThat(test.status()).isEqualTo("PASSED");
                    assertThat(test.http().statusCode()).isEqualTo(200);
                    assertThat(test.responseAssertions()).singleElement()
                            .extracting("status")
                            .isEqualTo("PASSED");
                    assertThat(test.dbAssertions()).isEmpty();
                });
        server.verify();
    }

    @Test
    void shouldBlockUnreviewedTestBeforeHttpExecution() {
        var service = new ChangeVerificationSmokeExecutionService(
                RestClient.builder(),
                new ChangeVerificationExecutionProperties()
        );

        var test = smokePack().tests().get(0);
        var result = service.execute(
                new ChangeVerificationSmokePackResponse(
                        true,
                        "NEEDS_REVIEW",
                        "CRM-123 smoke verification",
                        List.of(new ChangeVerificationSmokeTestResponse(
                                test.id(),
                                test.name(),
                                test.method(),
                                test.path(),
                                test.purpose(),
                                test.headers(),
                                test.queryParams(),
                                test.requestBody(),
                                test.responseAssertions(),
                                test.dbAssertions(),
                                test.dbAssertionSpecs(),
                                test.cleanup(),
                                test.cleanupHints(),
                                test.sourceRefs(),
                                test.riskCovered(),
                                "NEEDS_REVIEW"
                        )),
                        List.of(),
                        List.of(),
                        "medium"
                ),
                new ChangeVerificationSmokeExecutionRequest("https://test.example.com", null, null, List.of(), Map.of(), false)
        );

        assertThat(result).singleElement()
                .satisfies(testResult -> {
                    assertThat(testResult.status()).isEqualTo("NEEDS_REVIEW");
                    assertThat(testResult.http()).isNull();
                });
    }

    @Test
    void shouldExecuteAllowlistedCleanupEndpointWhenRequested() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://test.example.com/api/customers/123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test.example.com/api/test-data/customers/123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        var properties = new ChangeVerificationExecutionProperties();
        properties.setCleanupEndpointAllowlist(List.of("/api/test-data/.*"));
        var service = new ChangeVerificationSmokeExecutionService(
                restClientBuilder,
                properties
        );

        var result = service.execute(
                cleanupSmokePack(),
                new ChangeVerificationSmokeExecutionRequest(
                        "https://test.example.com",
                        null,
                        null,
                        List.of(),
                        Map.of("customerId", "123"),
                        true
                )
        );

        assertThat(result).singleElement()
                .satisfies(test -> {
                    assertThat(test.status()).isEqualTo("PASSED");
                    assertThat(test.cleanup().status()).isEqualTo("EXECUTED");
                });
        server.verify();
    }

    private static ChangeVerificationSmokePackResponse smokePack() {
        return new ChangeVerificationSmokePackResponse(
                true,
                "READY",
                "CRM-123 smoke verification",
                List.of(new ChangeVerificationSmokeTestResponse(
                        "smoke-001",
                        "Customer profile exposes status",
                        "GET",
                        "/api/customers/{{customerId}}",
                        "Verify status field for active customer.",
                        List.of(new ChangeVerificationNameValueResponse("Accept", "application/json", true)),
                        List.of(new ChangeVerificationNameValueResponse("includeStatus", "true", true)),
                        null,
                        List.of(new ChangeVerificationSmokeAssertionResponse("STATUS", "status", "EQUALS", "200")),
                        List.of(),
                        List.of(new ChangeVerificationSmokeDbAssertionResponse(
                                "db-001",
                                "select status from customer where id = '{{customerId}}'",
                                "EXISTS",
                                null,
                                "Status row exists"
                        )),
                        new ChangeVerificationSmokeCleanupResponse("NONE", null, null, null, null, List.of()),
                        List.of(),
                        List.of("change-verification/merge-requests.md"),
                        "Acceptance criterion: status is returned.",
                        "READY"
                )),
                List.of(),
                List.of(),
                "medium"
        );
    }

    private static ChangeVerificationSmokePackResponse cleanupSmokePack() {
        var test = smokePack().tests().get(0);
        return new ChangeVerificationSmokePackResponse(
                true,
                "READY",
                "CRM-123 smoke verification",
                List.of(new ChangeVerificationSmokeTestResponse(
                        test.id(),
                        test.name(),
                        test.method(),
                        test.path(),
                        test.purpose(),
                        test.headers(),
                        List.of(),
                        test.requestBody(),
                        test.responseAssertions(),
                        List.of(),
                        List.of(),
                        new ChangeVerificationSmokeCleanupResponse(
                                "ENDPOINT",
                                "DELETE",
                                "/api/test-data/customers/{{customerId}}",
                                null,
                                null,
                                List.of()
                        ),
                        test.cleanupHints(),
                        test.sourceRefs(),
                        test.riskCovered(),
                        test.reviewStatus()
                )),
                List.of(),
                List.of(),
                "medium"
        );
    }
}
