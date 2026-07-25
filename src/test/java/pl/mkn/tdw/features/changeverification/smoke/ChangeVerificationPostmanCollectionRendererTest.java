package pl.mkn.tdw.features.changeverification.smoke;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationNameValueResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeAssertionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeCleanupResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationPostmanCollectionRendererTest {

    @Test
    void shouldRenderPostmanCollectionFromSmokePack() {
        var renderer = new ChangeVerificationPostmanCollectionRenderer();

        var collection = renderer.render(new ChangeVerificationSmokePackResponse(
                true,
                "READY",
                "CRM-123 smoke verification",
                List.of(new ChangeVerificationSmokeTestResponse(
                        "smoke-001",
                        "Customer profile exposes status",
                        "GET",
                        "/api/customers/{{customerId}}",
                        "Verify status field.",
                        List.of(new ChangeVerificationNameValueResponse("Accept", "application/json", true)),
                        List.of(new ChangeVerificationNameValueResponse("includeStatus", "true", true)),
                        null,
                        List.of(new ChangeVerificationSmokeAssertionResponse("STATUS", "status", "EQUALS", "200")),
                        List.of("select status from customer where id = :customerId"),
                        List.of(),
                        new ChangeVerificationSmokeCleanupResponse("NONE", null, null, null, null, List.of()),
                        List.of(),
                        List.of("change-verification/merge-requests.md"),
                        "Acceptance criterion.",
                        "READY"
                )),
                List.of(),
                List.of(),
                "medium"
        ));

        assertThat(collection).containsKey("info");
        assertThat(collection.get("item").toString()).contains("Customer profile exposes status");
        assertThat(collection.get("item").toString()).contains("{{baseUrl}}/api/customers/{{customerId}}?includeStatus=true");
        assertThat(collection.get("item").toString()).contains("pm.response.to.have.status(200)");
    }
}
