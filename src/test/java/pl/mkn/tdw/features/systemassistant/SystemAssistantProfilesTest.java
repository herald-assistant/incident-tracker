package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration.*;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantException;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SystemAssistantProfilesTest {
    @org.junit.jupiter.api.io.TempDir Path workspace;
    private final SystemAssistantConfigurationService configurations = SystemAssistantTestSupport.configuration(new OpenApiImporter(new ObjectMapper()));

    private SystemAssistantConfiguration configuration(String content) throws Exception {
        var yaml = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"));
        return new SystemAssistantConfiguration(List.of(new ApiDefinition("crm", "CRM", yaml), new ApiDefinition("other", "Other CRM", yaml)),
                List.of(new Skill("shared", "Shared", content), new Skill("private", "Private", "PRIVATE")),
                List.of(new Profile("first", "First", "*", (List.of("crm", "deleted")).stream().map(apiId -> new SystemAssistantConfiguration.ApiBinding((String) apiId, null)).toList(), List.of("shared", "removed")),
                        new Profile("second", "Second", "*", (List.of("crm")).stream().map(apiId -> new SystemAssistantConfiguration.ApiBinding((String) apiId, null)).toList(), List.of("shared", "private")),
                        new Profile("empty", "Empty", "*", (List.of("missing")).stream().map(apiId -> new SystemAssistantConfiguration.ApiBinding((String) apiId, null)).toList(), List.of("missing"))), List.of());
    }

    @Test void resolvesOnlyAssignedCurrentResourcesWithoutChangingGlobalCatalogOrAcceptedSnapshots() throws Exception {
        configurations.save(configuration("FIRST"));
        var accepted = configurations.snapshot().forProfile("first");
        assertThat(accepted.configuration().skills()).extracting(Skill::content).containsExactly("FIRST");
        assertThat(accepted.operations()).hasSize(3).allMatch(item -> item.name().startsWith("oa_crm_"));
        assertThat(configurations.snapshot().operations()).hasSize(6);
        configurations.save(configuration("UPDATED"));
        assertThat(accepted.configuration().skills().get(0).content()).isEqualTo("FIRST");
        for (var id : List.of("first", "second"))
            assertThat(configurations.snapshot().forProfile(id).configuration().skills().get(0).content()).isEqualTo("UPDATED");
        assertThat(configurations.snapshot().forProfile("empty").operations()).isEmpty();
        assertThat(configurations.snapshot().forProfile("empty").configuration().skills()).isEmpty();
        var current = configuration("UPDATED");
        configurations.save(new SystemAssistantConfiguration(List.of(), current.skills(), current.profiles(), List.of()));
        assertThat(configurations.snapshot().forProfile("first").operations()).isEmpty();
        assertThat(configurations.current().profiles().get(0).apiBindings().stream().map(SystemAssistantConfiguration.ApiBinding::apiId).toList()).contains("crm", "deleted");
    }

    @Test void rejectsMissingProfileAndDuplicateReferencesWithoutPublishingAnotherConfiguration() throws Exception {
        var current = configuration("CURRENT"); configurations.save(current);
        assertThatThrownBy(() -> configurations.snapshot().forProfile("deleted-profile")).isInstanceOf(SystemAssistantException.class);
        assertThatThrownBy(() -> configurations.save(new SystemAssistantConfiguration(current.apis(), current.skills(),
                List.of(new Profile("bad", "Bad", "*", (List.of("crm", "crm")).stream().map(apiId -> new SystemAssistantConfiguration.ApiBinding((String) apiId, null)).toList(), List.of())), List.of())))
                .isInstanceOf(SystemAssistantException.class);
        assertThat(configurations.current()).isEqualTo(current);
    }

    @Test void switchesProfileWithinTheSameHistoryAndAcceptsProfilesWithOnlyMissingResources() throws Exception {
        configurations.save(configuration("CURRENT"));
        var seen = new java.util.concurrent.CopyOnWriteArrayList<SystemAssistantConfigurationService.Snapshot>();
        var ai = org.mockito.Mockito.mock(pl.mkn.tdw.features.systemassistant.ai.SystemAssistantAiPort.class);
        org.mockito.Mockito.when(ai.answer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            seen.add(invocation.getArgument(1));
            return new pl.mkn.tdw.features.systemassistant.ai.SystemAssistantAiPort.Reply("Odpowiedź CRM", null);
        });
        var service = new pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationService(configurations, ai,
                SystemAssistantTestSupport.history(workspace), new pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard(), org.mockito.Mockito.mock(pl.mkn.tdw.integrations.httpauth.OidcLoginPort.class));
        try {
            var id = service.create(null).id();
            for (var profile : List.of("first", "second", "empty")) {
                service.send(id, new pl.mkn.tdw.features.systemassistant.contract.SystemAssistantMessageRequest("Dalej", profile, null, null));
                org.awaitility.Awaitility.await().until(() -> !service.get(id).busy());
            }
            assertThat(service.get(id).messages()).hasSize(6);
            assertThat(seen.get(0).configuration().skills()).extracting(Skill::id).containsExactly("shared");
            assertThat(seen.get(1).configuration().skills()).extracting(Skill::id).containsExactly("shared", "private");
            assertThat(seen.get(2).operations()).isEmpty();
            assertThatThrownBy(() -> service.send(id,
                    new pl.mkn.tdw.features.systemassistant.contract.SystemAssistantMessageRequest("Nie wykonuj", "gone", null, null)))
                    .isInstanceOf(SystemAssistantException.class);
            assertThat(service.get(id).messages()).hasSize(6);
        } finally { service.close(); }
    }
}
