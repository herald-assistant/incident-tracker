package pl.mkn.tdw.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabJavaMethodUseCaseContextServiceTest {

    private static final String GROUP = "CRM";
    private static final String PROJECT = "crm-customer-service";
    private static final String BRANCH = "main";
    private static final String SOURCE_PREFIX = "src/main/java/com/example/crm/customer";

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabJavaMethodUseCaseContextService service =
            GitLabJavaMethodUseCaseContextService.createDefault(repositoryPort);

    @Test
    void shouldBuildUseCaseContextStartingFromResolvedJavaMethod() {
        stubRepository(customerCaseSources());

        var result = service.buildContext(GROUP, BRANCH, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                null,
                "com.example.crm.customer.application.CustomerCaseService",
                "registerCase",
                null,
                null,
                List.of(),
                5,
                30
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.RESOLVED, result.entryMethod().status());
        assertEquals("com.example.crm.customer.application.CustomerCaseService",
                result.entryMethod().declaringTypeQualifiedName());
        assertEquals("CustomerCaseResult registerCase(CustomerCaseForm form)", result.entryMethod().signature());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, result.confidence());

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                byPath.get(path("application/CustomerCaseService.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_PORT,
                byPath.get(path("domain/CustomerCaseRepositoryPort.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION,
                byPath.get(path("adapter/out/CustomerCaseRepository.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/CustomerCaseMapper.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                byPath.get(path("domain/CustomerCase.java")).role());
        assertTrue(byPath.get(path("application/CustomerCaseService.java")).symbols().contains("registerCase"));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INJECTED_PORT_CALL
                        && relation.to().contains("CustomerCaseRepositoryPort#save")));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION
                        && relation.to().contains("CustomerCaseRepository#save")));
        assertFalse(result.limits().maxResultsReached());
    }

    @Test
    void shouldReturnAmbiguousEntryWithoutTraversingWhenOverloadIsNotDisambiguated() {
        stubRepository(policySources());

        var result = service.buildContext(GROUP, BRANCH, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                path("domain/CustomerCasePolicy.java"),
                "CustomerCasePolicy",
                "evaluate",
                null,
                null,
                List.of(),
                5,
                30
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.AMBIGUOUS, result.entryMethod().status());
        assertEquals(GitLabEndpointUseCaseConfidence.LOW, result.confidence());
        assertEquals(2, result.entryMethod().candidates().size());
        assertEquals(1, result.unresolved().size());
        assertTrue(result.unresolved().get(0).reason().contains("AMBIGUOUS"));
        assertTrue(result.relations().isEmpty());
    }

    @Test
    void shouldRespectMaxResultsAfterTraversalCompression() {
        stubRepository(customerCaseSources());

        var result = service.buildContext(GROUP, BRANCH, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                path("application/CustomerCaseService.java"),
                "CustomerCaseService",
                "registerCase",
                null,
                null,
                List.of(),
                5,
                2
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.RESOLVED, result.entryMethod().status());
        assertEquals(2, result.files().size());
        assertTrue(result.limits().maxResultsReached());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("Result file list was truncated")));
    }

    @Test
    void shouldCarryMaxDepthReachedFromMethodTraversal() {
        stubRepository(customerCaseSources());

        var result = service.buildContext(GROUP, BRANCH, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                path("application/CustomerCaseService.java"),
                "CustomerCaseService",
                "registerCase",
                null,
                null,
                List.of(),
                1,
                30
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.RESOLVED, result.entryMethod().status());
        assertTrue(result.limits().maxDepthReached());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("maxDepth")));
    }

    private void stubRepository(Map<String, String> sources) {
        when(repositoryPort.listRepositoryFiles(GROUP, PROJECT, BRANCH, null))
                .thenReturn(sources.keySet().stream()
                        .map(filePath -> new GitLabRepositoryFile(GROUP, PROJECT, BRANCH, filePath))
                        .toList());
        sources.forEach((filePath, content) -> when(repositoryPort.readFile(
                GROUP,
                PROJECT,
                BRANCH,
                filePath,
                120_000
        )).thenReturn(new GitLabRepositoryFileContent(
                GROUP,
                PROJECT,
                BRANCH,
                filePath,
                content,
                false
        )));
        when(repositoryPort.searchCandidateFiles(any(GitLabRepositorySearchQuery.class)))
                .thenAnswer(invocation -> {
                    GitLabRepositorySearchQuery query = invocation.getArgument(0);
                    var keywords = query.keywords() != null ? query.keywords() : List.<String>of();
                    if (keywords.isEmpty()) {
                        return List.of();
                    }
                    return sources.entrySet().stream()
                            .filter(entry -> keywords.stream().anyMatch(keyword -> entry.getValue().contains(keyword)))
                            .map(entry -> new GitLabRepositoryFileCandidate(
                                    query.group(),
                                    PROJECT,
                                    query.branch(),
                                    entry.getKey(),
                                    "matched CRM fixture keyword",
                                    100
                            ))
                            .toList();
                });
    }

    private Map<String, GitLabEndpointUseCaseFileCandidate> filesByPath(
            GitLabJavaMethodUseCaseContextResult result
    ) {
        return result.files().stream()
                .collect(Collectors.toMap(
                        GitLabEndpointUseCaseFileCandidate::path,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, String> customerCaseSources() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(path("application/CustomerCaseService.java"), """
                package com.example.crm.customer.application;

                import com.example.crm.customer.api.CustomerCaseForm;
                import com.example.crm.customer.api.CustomerCaseMapper;
                import com.example.crm.customer.domain.CustomerCase;
                import com.example.crm.customer.domain.CustomerCaseRepositoryPort;
                import lombok.RequiredArgsConstructor;

                @RequiredArgsConstructor
                class CustomerCaseService {

                    private final CustomerCaseRepositoryPort repositoryPort;
                    private final CustomerCaseMapper mapper;

                    CustomerCaseResult registerCase(CustomerCaseForm form) {
                        var customerCase = mapper.toDomain(form);
                        repositoryPort.save(customerCase);
                        return new CustomerCaseResult(customerCase.id());
                    }
                }
                """);
        sources.put(path("application/CustomerCaseResult.java"), """
                package com.example.crm.customer.application;

                record CustomerCaseResult(String caseId) {
                }
                """);
        sources.put(path("api/CustomerCaseForm.java"), """
                package com.example.crm.customer.api;

                public record CustomerCaseForm(String customerId, String description) {
                }
                """);
        sources.put(path("api/CustomerCaseMapper.java"), """
                package com.example.crm.customer.api;

                import com.example.crm.customer.domain.CustomerCase;

                interface CustomerCaseMapper {
                    CustomerCase toDomain(CustomerCaseForm form);
                }
                """);
        sources.put(path("domain/CustomerCase.java"), """
                package com.example.crm.customer.domain;

                class CustomerCase {

                    private final String id;

                    CustomerCase(String id) {
                        this.id = id;
                    }

                    String id() {
                        return id;
                    }

                    void markRegistered() {
                    }
                }
                """);
        sources.put(path("domain/CustomerCaseRepositoryPort.java"), """
                package com.example.crm.customer.domain;

                interface CustomerCaseRepositoryPort {
                    void save(CustomerCase customerCase);
                }
                """);
        sources.put(path("adapter/out/CustomerCaseRepository.java"), """
                package com.example.crm.customer.adapter.out;

                import com.example.crm.customer.domain.CustomerCase;
                import com.example.crm.customer.domain.CustomerCaseRepositoryPort;

                class CustomerCaseRepository implements CustomerCaseRepositoryPort {
                    public void save(CustomerCase customerCase) {
                        customerCase.markRegistered();
                    }
                }
                """);
        return sources;
    }

    private Map<String, String> policySources() {
        return Map.of(path("domain/CustomerCasePolicy.java"), """
                package com.example.crm.customer.domain;

                class CustomerCasePolicy {
                    Decision evaluate(CustomerCaseForm form) {
                        return new Decision(form.customerId());
                    }

                    Decision evaluate(String customerId) {
                        return new Decision(customerId);
                    }
                }
                """);
    }

    private String path(String suffix) {
        return SOURCE_PREFIX + "/" + suffix;
    }
}
