package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationSmokePackResponseParserTest {

    private final ChangeVerificationSmokePackResponseParser parser =
            new ChangeVerificationSmokePackResponseParser(new ObjectMapper());

    @Test
    void shouldParseSmokePackJson() {
        var response = parser.parse("""
                {
                  "requested": true,
                  "status": "READY",
                  "postmanCollectionName": "CRM-123 smoke verification",
                  "tests": [
                    {
                      "id": "smoke-001",
                      "name": "Customer profile exposes status",
                      "method": "GET",
                      "path": "/api/customers/{{customerId}}",
                      "purpose": "Verify status field.",
                      "headers": [{"name": "Accept", "value": "application/json", "enabled": true}],
                      "queryParams": [],
                      "requestBody": null,
                      "responseAssertions": [{"type": "STATUS", "target": "status", "operator": "EQUALS", "expectedValue": "200"}],
                      "dbAssertions": ["select status from customer where id = :customerId"],
                      "cleanup": {"strategy": "NONE", "method": null, "path": null, "requestBody": null, "manualSql": null, "hints": []},
                      "cleanupHints": ["No cleanup needed."],
                      "sourceRefs": ["change-verification/merge-requests.md"],
                      "riskCovered": "Acceptance criterion.",
                      "reviewStatus": "READY"
                    }
                  ],
                  "visibilityLimits": [],
                  "suggestedActions": ["Review customerId environment variable."],
                  "confidence": "medium"
                }
                """);

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.tests()).singleElement()
                .satisfies(test -> {
                    assertThat(test.headers()).singleElement()
                            .extracting("name")
                            .isEqualTo("Accept");
                    assertThat(test.cleanup().strategy()).isEqualTo("NONE");
                });
    }

    @Test
    void shouldReturnFallbackWhenJsonIsInvalid() {
        var response = parser.parse("not json");

        assertThat(response.status()).isEqualTo("INCONCLUSIVE");
        assertThat(response.visibilityLimits()).singleElement()
                .asString()
                .contains("did not contain JSON");
    }
}
