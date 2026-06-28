package pl.mkn.tdw.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabJavaMethodUseCaseEntryResolverTest {

    private static final String GROUP = "CRM";
    private static final String PROJECT = "crm-customer-service";
    private static final String BRANCH = "main";
    private static final String SERVICE_PATH = "src/main/java/com/example/crm/customer/application/CustomerCaseService.java";
    private static final String LEGACY_SERVICE_PATH = "src/main/java/com/example/crm/legacy/CustomerCaseService.java";
    private static final String POLICY_PATH = "src/main/java/com/example/crm/customer/domain/CustomerCasePolicy.java";

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabJavaMethodUseCaseEntryResolver resolver = new GitLabJavaMethodUseCaseEntryResolver();

    @Test
    void shouldResolveMethodByFullyQualifiedClassNameWithoutFilePath() {
        var session = session(Map.of(SERVICE_PATH, customerCaseServiceSource()));

        var entryMethod = resolver.resolve(session, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                null,
                "com.example.crm.customer.application.CustomerCaseService",
                "registerCase",
                null,
                null,
                List.of(),
                null,
                null
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.RESOLVED, entryMethod.status());
        assertEquals(SERVICE_PATH, entryMethod.filePath());
        assertEquals("com.example.crm.customer.application.CustomerCaseService",
                entryMethod.declaringTypeQualifiedName());
        assertEquals("CustomerCaseResult registerCase(CustomerCaseForm form)", entryMethod.signature());
        assertEquals(List.of("CustomerCaseForm"), entryMethod.parameterTypes());
        assertTrue(entryMethod.lineStart() > 0);
        assertTrue(entryMethod.lineEnd() >= entryMethod.lineStart());
    }

    @Test
    void shouldResolveMethodByFilePathAndSimpleClassName() {
        var session = session(Map.of(SERVICE_PATH, customerCaseServiceSource()));

        var entryMethod = resolver.resolve(session, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                SERVICE_PATH,
                "CustomerCaseService",
                "closeCase",
                null,
                1,
                List.of("String"),
                null,
                null
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.RESOLVED, entryMethod.status());
        assertEquals("void closeCase(String caseId)", entryMethod.signature());
        assertEquals(List.of("String"), entryMethod.parameterTypes());
        assertTrue(entryMethod.limitations().isEmpty());
    }

    @Test
    void shouldResolveOverloadByLineNumberInsideMethodBody() {
        var session = session(Map.of(POLICY_PATH, customerCasePolicySource()));

        var entryMethod = resolver.resolve(session, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                POLICY_PATH,
                "CustomerCasePolicy",
                "evaluate",
                5,
                null,
                List.of(),
                null,
                null
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.RESOLVED, entryMethod.status());
        assertEquals("Decision evaluate(CustomerCaseForm form)", entryMethod.signature());
        assertEquals(List.of("CustomerCaseForm"), entryMethod.parameterTypes());
        assertTrue(entryMethod.lineStart() <= 5);
        assertTrue(entryMethod.lineEnd() >= 5);
    }

    @Test
    void shouldReturnCandidatesWhenOverloadIsAmbiguous() {
        var session = session(Map.of(POLICY_PATH, customerCasePolicySource()));

        var entryMethod = resolver.resolve(session, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                POLICY_PATH,
                "CustomerCasePolicy",
                "evaluate",
                null,
                null,
                List.of(),
                null,
                null
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.AMBIGUOUS, entryMethod.status());
        assertEquals(2, entryMethod.candidates().size());
        assertTrue(entryMethod.candidates().stream()
                .anyMatch(candidate -> candidate.signature().equals("Decision evaluate(CustomerCaseForm form)")));
        assertTrue(entryMethod.candidates().stream()
                .anyMatch(candidate -> candidate.signature().equals("Decision evaluate(String customerId)")));
        assertTrue(entryMethod.limitations().stream()
                .anyMatch(limitation -> limitation.contains("argument count")));
    }

    @Test
    void shouldReturnCandidatesWhenSimpleClassNameMatchesMultipleSourceFiles() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(SERVICE_PATH, customerCaseServiceSource());
        sources.put(LEGACY_SERVICE_PATH, legacyCustomerCaseServiceSource());
        var session = session(sources);

        var entryMethod = resolver.resolve(session, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                null,
                "CustomerCaseService",
                "registerCase",
                null,
                null,
                List.of(),
                null,
                null
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.AMBIGUOUS, entryMethod.status());
        assertEquals(2, entryMethod.candidates().size());
        assertTrue(entryMethod.candidates().stream()
                .anyMatch(candidate -> SERVICE_PATH.equals(candidate.filePath())));
        assertTrue(entryMethod.candidates().stream()
                .anyMatch(candidate -> LEGACY_SERVICE_PATH.equals(candidate.filePath())));
    }

    @Test
    void shouldReturnInvalidRequestWhenMethodNameIsMissing() {
        var session = session(Map.of(SERVICE_PATH, customerCaseServiceSource()));

        var entryMethod = resolver.resolve(session, new GitLabJavaMethodUseCaseContextRequest(
                PROJECT,
                SERVICE_PATH,
                "CustomerCaseService",
                null,
                null,
                null,
                List.of(),
                null,
                null
        ));

        assertEquals(GitLabJavaMethodUseCaseEntryStatus.INVALID_REQUEST, entryMethod.status());
        assertTrue(entryMethod.limitations().contains("methodName is required."));
    }

    private GitLabEndpointUseCaseSourceSession session(Map<String, String> sources) {
        var repository = new GitLabEndpointUseCaseRepositoryContext(GROUP, PROJECT, BRANCH);
        when(repositoryPort.listRepositoryFiles(GROUP, PROJECT, BRANCH, null))
                .thenReturn(sources.keySet().stream()
                        .map(path -> new GitLabRepositoryFile(GROUP, PROJECT, BRANCH, path))
                        .toList());
        sources.forEach((path, content) -> when(repositoryPort.readFile(GROUP, PROJECT, BRANCH, path, 120_000))
                .thenReturn(new GitLabRepositoryFileContent(GROUP, PROJECT, BRANCH, path, content, false)));
        return new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);
    }

    private String customerCaseServiceSource() {
        return """
                package com.example.crm.customer.application;

                class CustomerCaseService {

                    CustomerCaseResult registerCase(CustomerCaseForm form) {
                        var customerId = form.customerId();
                        return new CustomerCaseResult(customerId);
                    }

                    void closeCase(String caseId) {
                        audit(caseId);
                    }

                    private void audit(String caseId) {
                    }
                }
                """;
    }

    private String legacyCustomerCaseServiceSource() {
        return """
                package com.example.crm.legacy;

                class CustomerCaseService {

                    CustomerCaseResult registerCase(CustomerCaseForm form) {
                        return new CustomerCaseResult(form.customerId());
                    }
                }
                """;
    }

    private String customerCasePolicySource() {
        return """
                package com.example.crm.customer.domain;

                class CustomerCasePolicy {
                    Decision evaluate(CustomerCaseForm form) {
                        var customerId = form.customerId();
                        return new Decision(customerId);
                    }

                    Decision evaluate(String customerId) {
                        return new Decision(customerId);
                    }
                }
                """;
    }
}
