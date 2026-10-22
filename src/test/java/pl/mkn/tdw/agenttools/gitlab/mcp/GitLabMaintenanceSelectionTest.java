package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextNavigationTestCreator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogEntityType.SYSTEM;
import static pl.mkn.tdw.testsupport.operationalcontext.OperationalContextMaintenanceExamples.*;

class GitLabMaintenanceSelectionTest {
    @ParameterizedTest
    @ValueSource(strings = {"weak", "medium", "strong", "exact"})
    void systemSignalStrengthDoesNotIsolateAnIdentityCollisionFromScopeSelection(String strength) {
        var before = catalog(documents());
        var raw = decoded(documents());
        entity(raw, SYSTEM, "crm-contact-core").put("matchSignals", Map.of(strength,
                Map.of("artifactIds", List.of("crm-agent-portal"))));
        var after = catalog(rendered(raw));

        assertThat(selected(before, "crm-agent-portal")).containsExactly("crm-agent-portal-code-scope");
        assertThat(selected(after, "crm-agent-portal")).containsExactlyInAnyOrder(
                "crm-agent-portal-code-scope", "crm-contact-core-code-scope", "crm-contact-preferences-code-scope");
    }

    @Test
    void referenceCleanupKeepsActualHiddenApplicationScopeAndRepositoryBoundaries() {
        var before = catalog(withRedundantReferences());
        var after = catalog(documents());
        var inputs = new LinkedHashSet<String>();
        before.systems().forEach(system -> {
            inputs.add(system.id()); inputs.add(system.name());
            if (system.shortName() != null) inputs.add(system.shortName());
            inputs.addAll(system.genericSignals());
        });
        for (var input : inputs) {
            var oldResolver = resolver(before);
            var newResolver = resolver(after);
            var context = new ToolContext(Map.of(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES, List.of(input)));
            var oldScope = oldResolver.resolve(List.of(), context, before);
            var newScope = newResolver.resolve(List.of(), context, after);
            assertThat(newScope).as("hidden input %s", input).isEqualTo(oldScope);
            var oldSelected = oldResolver.codeSearchScopes(before, oldScope.applicationNames());
            var newSelected = newResolver.codeSearchScopes(after, newScope.applicationNames());
            assertThat(newSelected).as("scope and all repository boundaries for %s", input).isEqualTo(oldSelected);
        }
    }

    @Test
    void sharedExplicitOwnerDoesNotAddTheOtherSystemsScopeToApplicationSelection() {
        var raw = decoded(documents());
        entity(raw, SYSTEM, "crm-agent-portal").put("ownership", Map.of("ownerTeamIds", List.of("crm-domain-team"),
                "ownershipStatus", "explicit", "confidence", "high"));
        var catalog = catalog(rendered(raw));
        var navigation = OperationalContextNavigationTestCreator.navigation().build(catalog);
        assertThat(navigation.forEntity("team", "crm-domain-team")).hasSize(3);
        assertThat(navigation.forEntity("system", "crm-agent-portal"))
                .noneMatch(link -> link.target().id().equals("crm-contact-core") && link.via().stream().anyMatch(via -> via.type().equals("team")));
        assertThat(selected(catalog, "crm-agent-portal")).containsExactly("crm-agent-portal-code-scope");
    }

    private static List<String> selected(OperationalContextCatalog catalog, String application) {
        return resolver(catalog).codeSearchScopes(catalog, List.of(application)).stream().map(scope -> scope.id()).toList();
    }

    private static GitLabApplicationScopeResolver resolver(OperationalContextCatalog catalog) {
        return new GitLabApplicationScopeResolver(OperationalContextNavigationTestCreator.navigation().build(catalog));
    }
}
