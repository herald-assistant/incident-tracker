package pl.mkn.tdw.features.changeverification.source;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGit;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchTarget;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeVerificationOperationalContextMatcherTest {

    @Test
    void shouldMatchRepositoryThroughCodeSearchScopeTarget() {
        var matcher = new ChangeVerificationOperationalContextMatcher(ignored -> catalog());
        var repository = new ChangeVerificationRepositorySnapshot(
                "CRM/PROCESSING/CRM_CASE_PROCESS",
                "CRM/PROCESSING/CRM_CASE_PROCESS",
                "CRM_CASE_PROCESS",
                "feature/CRM-123",
                "main",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var result = matcher.enrich(List.of(repository));

        assertThat(result).singleElement()
                .satisfies(enriched -> {
                    assertThat(enriched.rootGroup()).isEqualTo("CRM");
                    assertThat(enriched.groupPath()).isEqualTo("CRM/PROCESSING");
                    assertThat(enriched.repositoryName()).isEqualTo("CRM_CASE_PROCESS");
                    assertThat(enriched.operationalContextMatches()).singleElement()
                            .satisfies(match -> {
                                assertThat(match.repositoryId()).isEqualTo("crm-case-process-repo");
                                assertThat(match.codeSearchScopeId()).isEqualTo("crm-case-process-scope");
                                assertThat(match.targetType()).isEqualTo("bounded-context");
                                assertThat(match.targetId()).isEqualTo("case-process");
                                assertThat(match.pathPrefixes()).containsExactly("src/main/java");
                            });
                });
    }

    private static OperationalContextCatalog catalog() {
        return new OperationalContextCatalog(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(repository()),
                List.of(scope()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }

    private static OperationalContextRepository repository() {
        return new OperationalContextRepository(
                "crm-case-process-repo",
                "CRM case process repository",
                "case-process",
                "application",
                "active",
                "high",
                "CRM case process code",
                "Supports CRM case process",
                List.of(),
                List.of(),
                new OperationalContextGit(
                        "gitlab",
                        "CRM/PROCESSING",
                        "CRM_CASE_PROCESS",
                        "CRM/PROCESSING/CRM_CASE_PROCESS",
                        "main",
                        null,
                        List.of(),
                        false
                ),
                null,
                null,
                List.of(),
                Map.of()
        );
    }

    private static OperationalContextRepositorySearchScope scope() {
        return new OperationalContextRepositorySearchScope(
                "crm-case-process-scope",
                "CRM Case Process",
                "bounded-context",
                "active",
                "Repository scope for CRM case process",
                new OperationalContextRepositorySearchTarget("bounded-context", "case-process"),
                List.of("change verification"),
                List.of(new OperationalContextRepositorySearchRepository(
                        "crm-case-process-repo",
                        "primary",
                        1,
                        "Main implementation repository for the bounded context.",
                        List.of("endpoint-flow"),
                        "path-prefixes",
                        List.of("src/main/java")
                )),
                List.of(),
                Map.of()
        );
    }
}
