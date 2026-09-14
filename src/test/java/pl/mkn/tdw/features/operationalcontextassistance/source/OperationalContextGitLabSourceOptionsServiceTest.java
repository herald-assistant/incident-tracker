package pl.mkn.tdw.features.operationalcontextassistance.source;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGit;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationalContextGitLabSourceOptionsServiceTest {

    @Test
    void listsUniqueCatalogProjectsInConfiguredGroupUsingRelativeRequestValues() {
        var properties = properties(" /CRM/runtime/ ");
        var operationalContextPort = mock(OperationalContextPort.class);
        when(operationalContextPort.loadContext(any())).thenReturn(catalog(
                repository("a", "gitlab", "CRM/runtime/libs", "a", "CRM/runtime/libs/a"),
                repository("a-duplicate", "gitlab", "crm/RUNTIME/libs", "a", "crm/runtime/libs/a"),
                repository("b", "gitlab", "CRM/runtime", "b", "CRM/runtime/b"),
                repository("other-group", "gitlab", "CRM/other", "other", "CRM/other/other"),
                repository("other-provider", "github", "CRM/runtime", "github", "CRM/runtime/github"),
                repository("unsafe", "gitlab", "CRM/runtime", "../outside", null)
        ));

        var options = new OperationalContextGitLabSourceOptionsService(properties, operationalContextPort).getOptions();

        assertThat(options.configuredGroup()).isEqualTo("CRM/runtime");
        assertThat(options.configuredBaseUrl()).isEqualTo("https://gitlab.example.com");
        assertThat(options.projects()).extracting(
                project -> project.project() + "|" + project.projectPath()
        ).containsExactly(
                "b|CRM/runtime/b",
                "libs/a|CRM/runtime/libs/a"
        );
    }

    @Test
    void usesCanonicalProjectPathsForNestedRepositoryGroups() {
        var properties = properties("CRM");
        var operationalContextPort = mock(OperationalContextPort.class);
        when(operationalContextPort.loadContext(any())).thenReturn(catalog(
                repository("nested", "gitlab", "CRM/runtime", "customer-api", "CRM/runtime/customer-api"),
                repository("root", "gitlab", "CRM", "portal", "CRM/portal"),
                repository("name-only", "gitlab", "CRM", "ignored", null),
                repository("provider-missing", null, "CRM", "ignored", "CRM/ignored")
        ));

        var options = new OperationalContextGitLabSourceOptionsService(properties, operationalContextPort).getOptions();

        assertThat(options.projects()).extracting(project -> project.project())
                .containsExactly("portal", "runtime/customer-api");
        assertThat(options.projects()).extracting(project -> project.projectPath())
                .containsExactly("CRM/portal", "CRM/runtime/customer-api");
    }

    @Test
    void ignoresForeignFullProjectPathWhenRepositoryGroupIsMissing() {
        var properties = properties("CRM/runtime");
        var operationalContextPort = mock(OperationalContextPort.class);
        when(operationalContextPort.loadContext(any())).thenReturn(catalog(
                repository("foreign", "gitlab", null, "other", "other-group/other"),
                repository("local", "gitlab", null, "customer-api", "CRM/runtime/customer-api")
        ));

        var options = new OperationalContextGitLabSourceOptionsService(properties, operationalContextPort).getOptions();

        assertThat(options.projects()).extracting(project -> project.project())
                .containsExactly("customer-api");
    }

    @Test
    void returnsNoSuggestionsForEmptyCatalogOrMissingGroup() {
        var operationalContextPort = mock(OperationalContextPort.class);
        when(operationalContextPort.loadContext(any())).thenReturn(OperationalContextCatalog.empty());

        var emptyCatalog = new OperationalContextGitLabSourceOptionsService(
                properties("CRM"), operationalContextPort
        ).getOptions();
        assertThat(emptyCatalog.configuredGroup()).isEqualTo("CRM");
        assertThat(emptyCatalog.projects()).isEmpty();

        var missingGroupPort = mock(OperationalContextPort.class);
        var missingGroup = new OperationalContextGitLabSourceOptionsService(
                properties(null), missingGroupPort
        ).getOptions();
        assertThat(missingGroup.configuredGroup()).isNull();
        assertThat(missingGroup.projects()).isEmpty();
        verifyNoInteractions(missingGroupPort);
    }

    @Test
    void doesNotExposeMalformedConfiguredBaseUrl() {
        var properties = properties("CRM");
        properties.setBaseUrl("https://user:secret@gitlab.example.com/gitlab?token=secret");
        var operationalContextPort = mock(OperationalContextPort.class);
        when(operationalContextPort.loadContext(any())).thenReturn(OperationalContextCatalog.empty());

        var options = new OperationalContextGitLabSourceOptionsService(properties, operationalContextPort).getOptions();

        assertThat(options.configuredBaseUrl()).isNull();
        assertThat(options.configuredGroup()).isEqualTo("CRM");
    }

    private GitLabProperties properties(String group) {
        var properties = new GitLabProperties();
        properties.setGroup(group);
        properties.setBaseUrl(" https://gitlab.example.com/ ");
        return properties;
    }

    private OperationalContextCatalog catalog(OperationalContextRepository... repositories) {
        return new OperationalContextCatalog(
                List.of(), List.of(), List.of(), List.of(), List.of(repositories),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private OperationalContextRepository repository(
            String id, String provider, String group, String project, String projectPath
    ) {
        return new OperationalContextRepository(
                id, id, null, "application", "active", null, null, null,
                List.of(), List.of(),
                new OperationalContextGit(provider, group, project, projectPath, "main", null, List.of()),
                null, null, List.of(), Map.of()
        );
    }
}
