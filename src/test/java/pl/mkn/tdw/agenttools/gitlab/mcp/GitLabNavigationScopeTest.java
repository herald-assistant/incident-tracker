package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextNavigationTestCreator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GitLabNavigationScopeTest {
    @Test
    void sharedIntegrationDoesNotGrantTheOtherParticipantsSemanticRepositoryScope() {
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(), List.of(),
                List.of(Map.of("id", "crm-contacts"), Map.of("id", "crm-notifications")),
                List.of(Map.of("id", "crm-contact-events", "participants", Map.of(
                        "source", Map.of("system", "crm-contacts"),
                        "targets", List.of(Map.of("system", "crm-notifications", "boundedContext", "crm-messaging"))))),
                List.of(Map.of("id", "crm-messaging-repo")),
                List.of(Map.of("id", "crm-messaging-code", "target", Map.of("type", "bounded-context", "id", "crm-messaging"),
                        "repositories", List.of(Map.of("repoId", "crm-messaging-repo", "role", "primary", "searchMode", "whole-repository")))),
                List.of(Map.of("id", "crm-messaging")), List.of(), List.of(), List.of());
        var navigation = OperationalContextNavigationTestCreator.navigation().build(catalog);
        assertTrue(navigation.forEntity("system", "crm-contacts").stream()
                .anyMatch(link -> link.indirect() && link.target().id().equals("crm-messaging")));
        assertTrue(new GitLabApplicationScopeResolver(navigation).codeSearchScopes(catalog, List.of("crm-contacts")).isEmpty());
    }
}
