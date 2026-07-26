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
                "CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                "CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                "CLP_AGREEMENT_PROCESS",
                "feature/CLP-123",
                "main",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var result = matcher.enrich(List.of(repository));

        assertThat(result).singleElement()
                .satisfies(enriched -> {
                    assertThat(enriched.rootGroup()).isEqualTo("CLP");
                    assertThat(enriched.groupPath()).isEqualTo("CLP/PROCESSES");
                    assertThat(enriched.repositoryName()).isEqualTo("CLP_AGREEMENT_PROCESS");
                    assertThat(enriched.operationalContextMatches()).singleElement()
                            .satisfies(match -> {
                                assertThat(match.repositoryId()).isEqualTo("clp-agreement-process-repo");
                                assertThat(match.codeSearchScopeId()).isEqualTo("clp-agreement-process-scope");
                                assertThat(match.targetType()).isEqualTo("bounded-context");
                                assertThat(match.targetId()).isEqualTo("agreement-process");
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
                "clp-agreement-process-repo",
                "CLP agreement process repository",
                "agreement-process",
                "application",
                "active",
                "high",
                "Agreement process code",
                "Supports agreement process",
                List.of(),
                List.of(),
                new OperationalContextGit(
                        "gitlab",
                        "CLP/PROCESSES",
                        "CLP_AGREEMENT_PROCESS",
                        "CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
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
                "clp-agreement-process-scope",
                "CLP Agreement Process",
                "bounded-context",
                "active",
                "Repository scope for agreement process",
                new OperationalContextRepositorySearchTarget("bounded-context", "agreement-process"),
                List.of("change verification"),
                List.of(new OperationalContextRepositorySearchRepository(
                        "clp-agreement-process-repo",
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
