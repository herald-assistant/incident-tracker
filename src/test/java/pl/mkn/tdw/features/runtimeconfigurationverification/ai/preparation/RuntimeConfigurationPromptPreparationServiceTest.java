package pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiTestFixtures;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationPromptPreparationServiceTest {

    private final RuntimeConfigurationPromptPreparationService service =
            new RuntimeConfigurationPromptPreparationService(
                    new RuntimeConfigurationAiArtifactService(new ObjectMapper())
            );

    @Test
    void shouldPrepareBasicPromptWithoutDeepContextOrCodeInstructions() {
        var preparation = service.prepare(
                request(RuntimeConfigurationVerificationMode.BASIC),
                RuntimeConfigurationAiTestFixtures.deterministic(RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED),
                RuntimeConfigurationAiTestFixtures.deep()
        );

        assertThat(preparation.prompt())
                .contains("runtime-configuration-basic-review")
                .contains("Nie używaj Operational Context ani kodu")
                .contains("parametry zmienione i niezmienione")
                .doesNotContain("runtime-configuration/deep-context.json");
        assertThat(preparation.artifactContents())
                .doesNotContainKey("runtime-configuration/deep-context.json");
    }

    @Test
    void shouldPrepareDeepPromptWithContextCodeAndOwnershipLimitations() {
        var preparation = service.prepare(
                request(RuntimeConfigurationVerificationMode.DEEP),
                RuntimeConfigurationAiTestFixtures.deterministic(RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED),
                RuntimeConfigurationAiTestFixtures.deep()
        );

        assertThat(preparation.prompt())
                .contains("runtime-configuration-deep-review")
                .contains("focused verification")
                .contains("Ownership jest faktem backendowym")
                .contains("runtime-configuration/deep-context.json");
        assertThat(preparation.artifactContents())
                .containsKey("runtime-configuration/deep-context.json");
    }

    private RuntimeConfigurationVerificationJobStartRequest request(
            RuntimeConfigurationVerificationMode mode
    ) {
        return new RuntimeConfigurationVerificationJobStartRequest(
                mode,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                "release-1",
                "gpt-5.4",
                "medium"
        );
    }
}
