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

class GitLabFrontendTargetedImportResolverTest {

    @Test
    void shouldResolveSyntheticCrmNxDeepImportBeforeTheAliasIndexSuffix() {
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var scope = new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "release/2026.08", List.of()
        );
        var bootstrapPath = "apps/synthetic-crm/src/app/app.config.ts";
        var servicePath = "libs/shared/data-access-swagger/src/lib/api/services/crm-customer-controller.service.ts";
        var files = Map.of(
                "tsconfig.base.json", """
                        {
                          "compilerOptions": {
                            "baseUrl": ".",
                            "paths": {
                              "@synthetic-crm/*": ["libs/*/src/index.ts"]
                            }
                          }
                        }
                        """,
                servicePath, "export class CrmCustomerControllerService {}"
        );
        when(repositoryPort.readFile(
                anyString(), anyString(), anyString(), anyString(), anyInt()
        )).thenAnswer(invocation -> {
            var path = invocation.getArgument(3, String.class);
            var source = files.get(path);
            return source == null ? null : new GitLabRepositoryFileContent(
                    scope.group(), scope.projectName(), scope.ref(), path, source, false
            );
        });
        var session = new GitLabFrontendTargetedSourceSession(
                repositoryPort, scope, GitLabFrontendGraphLimits.defaults(), false
        );

        var result = new GitLabFrontendTargetedImportResolver(session, bootstrapPath).resolve(
                bootstrapPath,
                "@synthetic-crm/shared/data-access-swagger/src/lib/api/services/crm-customer-controller.service"
        );

        assertThat(result).containsExactly(servicePath);
    }
}
