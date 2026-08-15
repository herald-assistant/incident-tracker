package pl.mkn.tdw.features.uiexplorer.catalog;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGit;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchTarget;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextSnapshot;

import java.util.List;
import java.util.Map;

public final class UiExplorerOperationalContextTestCatalog {

    private UiExplorerOperationalContextTestCatalog() {
    }

    public static OperationalContextCatalog eligibleCrmCatalog() {
        return catalog(
                crmSystem("frontend"),
                crmRepository("frontend", "crm/agent-portal"),
                crmScope(List.of(crmScopeRepository("primary", "whole-repository", List.of())))
        );
    }

    public static OperationalContextCatalog eligibleCrmPathPrefixCatalog() {
        return catalog(
                crmSystem("frontend"),
                crmRepository("frontend", "crm/agent-portal"),
                crmScope(List.of(crmScopeRepository(
                        "primary",
                        "path-prefixes",
                        List.of("apps/crm-agent", "libs/crm-ui")
                )))
        );
    }

    public static OperationalContextCatalog crmCatalogWithoutPrimary() {
        return catalog(
                crmSystem("frontend"),
                crmRepository("frontend", "crm/agent-portal"),
                crmScope(List.of(crmScopeRepository("support", "whole-repository", List.of())))
        );
    }

    public static OperationalContextCatalog crmCatalogWithUnknownSubtype() {
        return catalog(
                crmSystem("unknown"),
                crmRepository("frontend", "crm/agent-portal"),
                crmScope(List.of(crmScopeRepository("primary", "whole-repository", List.of())))
        );
    }

    public static OperationalContextCatalog crmCatalogWithoutProjectPath() {
        return catalog(
                crmSystem("frontend"),
                crmRepository("frontend", null),
                crmScope(List.of(crmScopeRepository("primary", "whole-repository", List.of())))
        );
    }

    public static OperationalContextPort port(OperationalContextCatalog catalog) {
        return new OperationalContextPort() {
            @Override
            public OperationalContextCatalog loadContext(OperationalContextQuery query) {
                return catalog;
            }

            @Override
            public OperationalContextSnapshot currentSnapshot() {
                return new OperationalContextSnapshot("crm-catalog-digest", "test", catalog);
            }
        };
    }

    private static OperationalContextCatalog catalog(
            OperationalContextSystem system,
            OperationalContextRepository repository,
            OperationalContextRepositorySearchScope scope
    ) {
        return new OperationalContextCatalog(
                List.of(),
                List.of(),
                List.of(system),
                List.of(),
                List.of(repository),
                List.of(scope),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Synthetic CRM operational context"
        );
    }

    private static OperationalContextSystem crmSystem(String subtype) {
        return new OperationalContextSystem(
                "crm-agent-portal",
                "CRM Agent Portal",
                "Agent Portal",
                "internal-service",
                subtype,
                "active",
                "available",
                "medium",
                "Strongly anonymized CRM frontend used only by tests.",
                "Handle synthetic CRM contact preferences.",
                List.of("crm-agent-ui"),
                List.of("Inspect a synthetic CRM contact screen."),
                null,
                null,
                null,
                null,
                List.of(),
                Map.of()
        );
    }

    private static OperationalContextRepository crmRepository(String repositoryType, String projectPath) {
        return new OperationalContextRepository(
                "crm-agent-portal-repository",
                "CRM Agent Portal Repository",
                "Agent Portal Repository",
                repositoryType,
                "active",
                "medium",
                "Strongly anonymized CRM frontend repository.",
                "Source for the synthetic CRM contact screen.",
                List.of("crm-agent-ui-repository"),
                List.of("Inspect synthetic CRM UI behavior."),
                new OperationalContextGit(
                        "gitlab",
                        "crm",
                        "agent-portal",
                        projectPath,
                        "main",
                        "https://gitlab.example.com/crm/agent-portal",
                        List.of(),
                        false
                ),
                null,
                null,
                List.of(),
                Map.of()
        );
    }

    private static OperationalContextRepositorySearchScope crmScope(
            List<OperationalContextRepositorySearchRepository> repositories
    ) {
        return new OperationalContextRepositorySearchScope(
                "crm-agent-portal-code-scope",
                "CRM Agent Portal code scope",
                "system",
                "active",
                "Strongly anonymized CRM UI code scope.",
                new OperationalContextRepositorySearchTarget("system", "crm-agent-portal"),
                List.of("UI Explorer tests"),
                repositories,
                List.of(),
                Map.of()
        );
    }

    private static OperationalContextRepositorySearchRepository crmScopeRepository(
            String role,
            String searchMode,
            List<String> pathPrefixes
    ) {
        return new OperationalContextRepositorySearchRepository(
                "crm-agent-portal-repository",
                role,
                1,
                "Primary strongly anonymized CRM UI source.",
                List.of("Synthetic CRM screen behavior"),
                searchMode,
                pathPrefixes
        );
    }
}
