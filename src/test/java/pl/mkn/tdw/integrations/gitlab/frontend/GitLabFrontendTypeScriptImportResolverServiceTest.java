package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabFrontendTypeScriptImportResolverServiceTest {

    @Test
    void shouldResolveRepositoryRootImportWithinConfiguredSourceScope() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var revision = "2222222222222222222222222222222222222222";
        var scope = new GitLabFrontendRepositoryScope(
                "CRM", "crm-agent-portal", revision, List.of("src")
        );
        var consumerPath = "src/app/profile/contact-preferences.service.ts";
        var importedPath = "src/app/api/crm/error-messages.ts";
        var files = Map.of(
                consumerPath, "import { messagesLookup } from 'src/app/api/crm/error-messages';",
                importedPath, "export function messagesLookup() {}"
        );
        when(repositoryPort.readFile(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    var path = invocation.getArgument(3, String.class);
                    var source = files.get(path);
                    return source == null ? null : new GitLabRepositoryFileContent(
                            scope.group(), scope.projectName(), scope.ref(), path, source, false
                    );
                });

        var resolved = new GitLabFrontendTypeScriptImportResolverService(repositoryPort).resolve(
                scope, consumerPath, "src/app/api/crm/error-messages", "messagesLookup"
        );

        assertThat(resolved.filePath()).isEqualTo(importedPath);
        assertThat(resolved.declaringTypeName()).isEqualTo("messagesLookup");
    }

    @Test
    void shouldResolveAliasedImportOnDemandAtThePinnedRevision() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var revision = "1111111111111111111111111111111111111111";
        var scope = new GitLabFrontendRepositoryScope(
                "CRM", "crm-agent-portal", revision, List.of("apps/crm")
        );
        var consumerPath = "apps/crm/src/app/contact/contact.component.ts";
        var facadePath = "apps/crm/src/app/contact/contact.facade.ts";
        var files = Map.of(
                consumerPath, "import { CrmContactFacade as ContactFacade } from './contact.facade';",
                facadePath, "export class CrmContactFacade {}"
        );
        when(repositoryPort.readFile(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    var path = invocation.getArgument(3, String.class);
                    var source = files.get(path);
                    return source == null ? null : new GitLabRepositoryFileContent(
                            scope.group(), scope.projectName(), scope.ref(), path, source, false
                    );
                });

        var resolved = new GitLabFrontendTypeScriptImportResolverService(repositoryPort).resolve(
                scope, consumerPath, "./contact.facade", "ContactFacade"
        );

        assertThat(resolved.filePath()).isEqualTo(facadePath);
        assertThat(resolved.declaringTypeName()).isEqualTo("CrmContactFacade");
    }
}
