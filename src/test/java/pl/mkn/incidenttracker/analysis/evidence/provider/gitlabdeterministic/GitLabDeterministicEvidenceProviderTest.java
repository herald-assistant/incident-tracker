package pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEntry;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveMatch;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveRequest;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabDeterministicEvidenceProviderTest {

    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();

    @Test
    void shouldResolveBranchEnvironmentAndCodeChunkFromEarlierElasticEvidence() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        properties.setGroup("sample/runtime");

        var repositoryPort = mock(GitLabRepositoryPort.class);
        var sourceResolveService = mock(GitLabSourceResolveService.class);
        var provider = new GitLabDeterministicEvidenceProvider(
                repositoryPort,
                properties,
                sourceResolveService,
                deploymentContextResolver
        );
        var elasticProvider = new ElasticLogEvidenceProvider(new TestElasticLogPort());
        var baseContext = AnalysisContext.initialize("db-lock-123");
        var context = baseContext.withSection(elasticProvider.collect(baseContext));

        when(sourceResolveService.resolveMatch(argThat((GitLabSourceResolveRequest request) ->
                request.projectPath().equals("backend")
                        && request.effectiveRef().equals("dev/zephyr")
                        && request.symbol().equals("com.example.synthetic.workflowstate.domain.core.ActiveCaseRecordDomainRepository")
        ), any())).thenReturn(new GitLabSourceResolveMatch(
                "src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java",
                130,
                java.util.List.of("src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java")
        ));

        when(repositoryPort.readFileChunk(
                "sample/runtime",
                "backend",
                "dev/zephyr",
                "src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java",
                54,
                94,
                4_000
        )).thenReturn(new GitLabRepositoryFileChunk(
                "sample/runtime",
                "backend",
                "dev/zephyr",
                "src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java",
                54,
                94,
                54,
                94,
                140,
                "public class ActiveCaseRecordDomainRepository {\n    Optional<ActiveCaseRecord> getLatest() { return Optional.empty(); }\n}",
                false
        ));

        var section = provider.collect(context);

        assertEquals("gitlab", section.provider());
        assertEquals("resolved-code", section.category());
        assertEquals(1, section.items().size());

        var codeItem = section.items().get(0);
        assertTrue(codeItem.title().contains("ActiveCaseRecordDomainRepository.java"));
        assertEquals("environment", codeItem.attributes().get(0).name());
        assertEquals("dev1", codeItem.attributes().get(0).value());
        assertEquals("branch", codeItem.attributes().get(1).name());
        assertEquals("dev/zephyr", codeItem.attributes().get(1).value());
        assertEquals("group", codeItem.attributes().get(2).name());
        assertEquals("sample/runtime", codeItem.attributes().get(2).value());
        assertEquals("projectName", codeItem.attributes().get(3).name());
        assertEquals("backend", codeItem.attributes().get(3).value());
        assertTrue(codeItem.attributes().stream().anyMatch(attribute ->
                attribute.name().equals("lineNumber") && attribute.value().equals("74")
        ));
        assertTrue(codeItem.attributes().stream().anyMatch(attribute ->
                attribute.name().equals("content") && attribute.value().contains("ActiveCaseRecordDomainRepository")
        ));

        var readableView = GitLabResolvedCodeEvidenceReadableView.from(section);
        assertEquals(1, readableView.items().size());
        assertEquals("backend", readableView.items().get(0).projectName());
        assertEquals(
                "src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java",
                readableView.items().get(0).filePath()
        );
        var markdown = readableView.toMarkdown();
        assertTrue(markdown.contains("GitLab resolved code references"));
        assertTrue(markdown.contains("- repository: `backend`"));
        assertTrue(markdown.contains("- returned lines: `54-94` of `140`"));
        assertTrue(markdown.contains("```java"));
        assertTrue(markdown.contains("ActiveCaseRecordDomainRepository"));
    }

    @Test
    void shouldFallbackToNamespaceBasedDeploymentForUatEnvironment() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        properties.setGroup("sample/runtime");

        var repositoryPort = mock(GitLabRepositoryPort.class);
        var sourceResolveService = mock(GitLabSourceResolveService.class);
        var provider = new GitLabDeterministicEvidenceProvider(
                repositoryPort,
                properties,
                sourceResolveService,
                deploymentContextResolver
        );
        var elasticProvider = new ElasticLogEvidenceProvider(uat2ElasticPort());
        var baseContext = AnalysisContext.initialize("uat2-like");
        var context = baseContext.withSection(elasticProvider.collect(baseContext));

        when(sourceResolveService.resolveMatch(argThat((GitLabSourceResolveRequest request) ->
                request.projectPath().equals("backend")
                        && request.effectiveRef().equals("release-candidate")
                        && request.symbol().equals("com.example.synthetic.workflowstate.domain.core.ActiveCaseRecordDomainRepository")
        ), any())).thenReturn(new GitLabSourceResolveMatch(
                "src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java",
                130,
                java.util.List.of("src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java")
        ));

        when(repositoryPort.readFileChunk(
                "sample/runtime",
                "backend",
                "release-candidate",
                "src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java",
                54,
                94,
                4_000
        )).thenReturn(new GitLabRepositoryFileChunk(
                "sample/runtime",
                "backend",
                "release-candidate",
                "src/main/java/com/example/synthetic/workflowstate/domain/core/ActiveCaseRecordDomainRepository.java",
                54,
                94,
                54,
                94,
                140,
                "public class ActiveCaseRecordDomainRepository {\n    Optional<ActiveCaseRecord> getLatest() { return Optional.empty(); }\n}",
                false
        ));

        var section = provider.collect(context);

        assertEquals("gitlab", section.provider());
        assertEquals("resolved-code", section.category());
        assertEquals(1, section.items().size());

        var codeItem = section.items().get(0);
        assertTrue(codeItem.title().contains("ActiveCaseRecordDomainRepository.java"));
        assertEquals("uat2", codeItem.attributes().get(0).value());
        assertEquals("release-candidate", codeItem.attributes().get(1).value());
        assertTrue(codeItem.attributes().stream().anyMatch(attribute ->
                attribute.name().equals("content") && attribute.value().contains("ActiveCaseRecordDomainRepository")
        ));
    }

    @Test
    void shouldResolveNestedGitLabProjectFromContainerNameHintForDeterministicMode() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        properties.setGroup("TENANT-ALPHA");

        var repositoryPort = mock(GitLabRepositoryPort.class);
        var sourceResolveService = mock(GitLabSourceResolveService.class);
        var provider = new GitLabDeterministicEvidenceProvider(
                repositoryPort,
                properties,
                sourceResolveService,
                deploymentContextResolver
        );
        var elasticProvider = new ElasticLogEvidenceProvider(agreementProcessElasticPort());
        var baseContext = AnalysisContext.initialize("agreement-123");
        var context = baseContext.withSection(elasticProvider.collect(baseContext));

        when(repositoryPort.searchProjects(
                argThat("TENANT-ALPHA"::equals),
                argThat(projectHints -> projectHints.contains("document-workflow")
                        && projectHints.contains("document_workflow"))
        )).thenReturn(List.of(new GitLabRepositoryProjectCandidate(
                "TENANT-ALPHA",
                "WORKFLOWS/DOCUMENT_WORKFLOW",
                "Matched TENANT-ALPHA project path from document_workflow.",
                120
        )));

        when(sourceResolveService.resolveMatch(argThat((GitLabSourceResolveRequest request) ->
                request.groupPath().equals("TENANT-ALPHA")
                        && request.projectPath().equals("WORKFLOWS/DOCUMENT_WORKFLOW")
                        && request.effectiveRef().equals("release-candidate")
                        && request.symbol().equals("com.example.synthetic.workflow.DocumentArchiveService")
        ), any())).thenReturn(new GitLabSourceResolveMatch(
                "src/main/java/com/example/synthetic/workflow/DocumentArchiveService.java",
                140,
                java.util.List.of("src/main/java/com/example/synthetic/workflow/DocumentArchiveService.java")
        ));

        when(repositoryPort.readFileChunk(
                "TENANT-ALPHA",
                "WORKFLOWS/DOCUMENT_WORKFLOW",
                "release-candidate",
                "src/main/java/com/example/synthetic/workflow/DocumentArchiveService.java",
                37,
                77,
                4_000
        )).thenReturn(new GitLabRepositoryFileChunk(
                "TENANT-ALPHA",
                "WORKFLOWS/DOCUMENT_WORKFLOW",
                "release-candidate",
                "src/main/java/com/example/synthetic/workflow/DocumentArchiveService.java",
                37,
                77,
                37,
                77,
                210,
                "public class DocumentArchiveService {\n    void removeDocuments() {}\n}",
                false
        ));

        var section = provider.collect(context);

        assertEquals("gitlab", section.provider());
        assertEquals("resolved-code", section.category());
        assertEquals(1, section.items().size());

        var codeItem = section.items().get(0);
        assertTrue(codeItem.title().contains("WORKFLOWS/DOCUMENT_WORKFLOW"));
        assertTrue(codeItem.attributes().stream().anyMatch(attribute ->
                attribute.name().equals("projectName")
                        && attribute.value().equals("WORKFLOWS/DOCUMENT_WORKFLOW")
        ));
        assertTrue(codeItem.attributes().stream().anyMatch(attribute ->
                attribute.name().equals("content") && attribute.value().contains("DocumentArchiveService")
        ));

        verify(sourceResolveService).resolveMatch(
                argThat((GitLabSourceResolveRequest request) ->
                        request.projectPath().equals("WORKFLOWS/DOCUMENT_WORKFLOW")),
                any()
        );
    }

    @Test
    void shouldIgnoreFrameworkStacktraceFramesAndMicroserviceNameAsProjectCandidate() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        properties.setGroup("TENANT-ALPHA");
        properties.setMaxCandidateCount(20);

        var repositoryPort = mock(GitLabRepositoryPort.class);
        var sourceResolveService = mock(GitLabSourceResolveService.class);
        var provider = new GitLabDeterministicEvidenceProvider(
                repositoryPort,
                properties,
                sourceResolveService,
                deploymentContextResolver
        );
        var elasticProvider = new ElasticLogEvidenceProvider(frameworkHeavyElasticPort());
        var baseContext = AnalysisContext.initialize("resp4-like");
        var context = baseContext.withSection(elasticProvider.collect(baseContext));

        when(sourceResolveService.resolveMatch(any(GitLabSourceResolveRequest.class), any()))
                .thenThrow(new RuntimeException("No source match in test"));

        var section = provider.collect(context);

        assertEquals("gitlab", section.provider());
        assertEquals("resolved-code", section.category());
        assertEquals(0, section.items().size());

        verify(sourceResolveService, never()).resolveMatch(
                argThat((GitLabSourceResolveRequest request) ->
                        request.projectPath().equals("case-evaluation-service")),
                any()
        );
        verify(sourceResolveService, never()).resolveMatch(
                argThat((GitLabSourceResolveRequest request) ->
                        request.symbol().startsWith("org.springframework.")),
                any()
        );
        verify(sourceResolveService).resolveMatch(
                argThat((GitLabSourceResolveRequest request) ->
                        request.projectPath().equals("backend")
                                && request.symbol().equals("com.example.synthetic.workflowstate.domain.core.ActiveCaseRecordDomainRepository")),
                any()
        );
        verify(sourceResolveService).resolveMatch(
                argThat((GitLabSourceResolveRequest request) ->
                        request.projectPath().equals("backend")
                                && request.symbol().equals("com.example.synthetic.workflowstate.services.core.ActiveCaseRecordQueryService")),
                any()
        );
    }

    private static ElasticLogPort frameworkHeavyElasticPort() {
        return new ElasticLogPort() {
            @Override
            public java.util.List<ElasticLogEntry> findLogEntries(String correlationId) {
                return java.util.List.of(new ElasticLogEntry(
                        "2026-04-11T20:57:33.285Z",
                        "ERROR",
                        "case-evaluation-service",
                        "c.e.synthetic.workflow.WorkflowApiExceptionHandler",
                        "Loan processing exception",
                        """
                                com.example.synthetic.workflowstate.services.common.exception.EntityNotFoundException: ActiveCaseRecord with caseId 7001234567 not found
                                \tat com.example.synthetic.workflowstate.domain.core.ActiveCaseRecordDomainRepository.getLatestActiveCaseRecordByCaseIdAndStatuses(ActiveCaseRecordDomainRepository.java:74)
                                \tat jdk.internal.reflect.GeneratedMethodAccessor3854.invoke(Unknown Source)
                                \tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
                                \tat org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:360)
                                \tat org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
                                \tat org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
                                \tat org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
                                \tat org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
                                \tat com.example.synthetic.workflowstate.domain.core.ActiveCaseRecordDomainRepository$$SpringCGLIB$$0.getLatestActiveCaseRecordByCaseIdAndStatuses(<generated>)
                                \tat com.example.synthetic.workflowstate.services.core.ActiveCaseRecordQueryService.getActiveCaseRecordByCaseIdAndStatuses(ActiveCaseRecordQueryService.java:69)
                                \tat com.example.synthetic.workflowstate.services.core.ActiveCaseRecordController.getActiveCaseRecordForCaseId(ActiveCaseRecordController.java:32)
                                """,
                        "main",
                        null,
                        "tenant-alpha-main-dev1",
                        "backend-pod",
                        "backend",
                        "reg.local/tenant-alpha-main-dev1/backend:20260409-113641-60-dev-zephyr-7fcd60f3b2a0660dc94a039ef4a98e430dd0b597",
                        "test-index",
                        "resp4-like",
                        false,
                        false
                ));
            }

            @Override
            public pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult searchLogsByCorrelationId(
                    String correlationId
            ) {
                var entries = findLogEntries(correlationId);
                return new pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult(
                        correlationId,
                        "test",
                        entries.size(),
                        entries.size(),
                        entries.size(),
                        0,
                        false,
                        entries,
                        "OK"
                );
            }
        };
    }

    private static ElasticLogPort uat2ElasticPort() {
        return new ElasticLogPort() {
            @Override
            public java.util.List<ElasticLogEntry> findLogEntries(String correlationId) {
                return java.util.List.of(new ElasticLogEntry(
                        "2026-04-14T10:50:18.475Z",
                        "ERROR",
                        "case-evaluation-service",
                        "c.e.synthetic.workflow.WorkflowApiExceptionHandler",
                        "Loan processing exception",
                        """
                                com.example.synthetic.workflowstate.services.common.exception.EntityNotFoundException: ActiveCaseRecord with caseId 7007654321 not found
                                \tat com.example.synthetic.workflowstate.domain.core.ActiveCaseRecordDomainRepository.getLatestActiveCaseRecordByCaseIdAndStatuses(ActiveCaseRecordDomainRepository.java:74)
                                """,
                        "https-jsse-nio-8443-exec-8",
                        "996cb413cc0154a6",
                        "tenant-alpha-main-uat2",
                        "backend-7d547497bf-j44wj",
                        "backend",
                        "registry.example.internal:9999/TENANT-ALPHA-main/backend:3.11.3",
                        "test-index",
                        "uat2-like",
                        false,
                        false
                ));
            }

            @Override
            public pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult searchLogsByCorrelationId(
                    String correlationId
            ) {
                var entries = findLogEntries(correlationId);
                return new pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult(
                        correlationId,
                        "test",
                        entries.size(),
                        entries.size(),
                        entries.size(),
                        0,
                        false,
                        entries,
                        "OK"
                );
            }
        };
    }

    private static ElasticLogPort agreementProcessElasticPort() {
        return new ElasticLogPort() {
            @Override
            public java.util.List<ElasticLogEntry> findLogEntries(String correlationId) {
                return java.util.List.of(new ElasticLogEntry(
                        "2026-04-17T05:26:42.917Z",
                        "ERROR",
                        "document-workflow",
                        "c.e.synthetic.workflow.DocumentArchiveService",
                        "Document cleanup failed",
                        """
                                java.lang.IllegalStateException: cleanup failed
                                \tat com.example.synthetic.workflow.DocumentArchiveService.removeDocuments(DocumentArchiveService.java:57)
                                """,
                        "https-jsse-nio-8443-exec-3",
                        "51214b5e131d8e00",
                        "tenant-alpha-main-uat2",
                        "document-workflow-75f8546d4f-gxw9s",
                        "document-workflow",
                        "registry.example.internal:9999/TENANT-ALPHA-main/document-workflow:3.8.0",
                        "test-index",
                        "agreement-123",
                        false,
                        false
                ));
            }

            @Override
            public pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult searchLogsByCorrelationId(
                    String correlationId
            ) {
                var entries = findLogEntries(correlationId);
                return new pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult(
                        correlationId,
                        "test",
                        entries.size(),
                        entries.size(),
                        entries.size(),
                        0,
                        false,
                        entries,
                        "OK"
                );
            }
        };
    }
}


