package pl.mkn.incidenttracker.analysis.evidence;

import pl.mkn.incidenttracker.analysis.adapter.dynatrace.TestDynatraceIncidentPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEntry;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.TestElasticLogPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositorySearchQuery;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveMatch;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveSession;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabDeterministicEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalogLoader;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalogMatcher;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceMapper;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class AnalysisEvidenceCollectorTest {

    private final GitLabProperties gitLabProperties = gitLabProperties();
    private final DeploymentContextResolver deploymentContextResolver = new DeploymentContextResolver();

    private final AnalysisEvidenceCollector analysisEvidenceCollector = new AnalysisEvidenceCollector(
            new ElasticLogEvidenceProvider(new TestElasticLogPort()),
            new DeploymentContextEvidenceProvider(deploymentContextResolver),
            new DynatraceEvidenceProvider(new TestDynatraceIncidentPort(), deploymentContextResolver),
            new GitLabDeterministicEvidenceProvider(
                    mock(GitLabRepositoryPort.class),
                    gitLabProperties,
                    mock(GitLabSourceResolveService.class),
                    deploymentContextResolver
            ),
            disabledOperationalContextEvidenceProvider(),
            directTaskExecutor()
    );

    @Test
    void shouldSkipDynatraceEvidenceForDevEnvironment() {
        var context = analysisEvidenceCollector.collect("timeout-123", AnalysisEvidenceCollectionListener.NO_OP);
        var evidenceSections = context.evidenceSections();

        assertEquals(2, evidenceSections.size());
        assertEquals("timeout-123", context.correlationId());

        var elasticSection = evidenceSections.get(0);
        assertEquals("elasticsearch", elasticSection.provider());
        assertEquals("logs", elasticSection.category());
        assertEquals("ERROR svc log entry", elasticSection.items().get(0).title());
        assertEquals("timestamp", elasticSection.items().get(0).attributes().get(0).name());
        assertEquals("2026-04-11T20:57:33.285Z", elasticSection.items().get(0).attributes().get(0).value());
        assertEquals("level", elasticSection.items().get(0).attributes().get(1).name());
        assertEquals("ERROR", elasticSection.items().get(0).attributes().get(1).value());

        var deploymentSection = evidenceSections.get(1);
        assertEquals("deployment-context", deploymentSection.provider());
        assertEquals("resolved-deployment", deploymentSection.category());
        assertEquals("Wejście do lookupu Dynatrace", deploymentSection.items().get(0).title());
        var deploymentAttributes = attributesByName(deploymentSection.items().get(1));
        assertEquals("dev3", deploymentAttributes.get("environment"));
        assertEquals("dev/atlas", deploymentAttributes.get("branch"));
        assertEquals("backend", deploymentAttributes.get("projectNameHint"));
    }

    @Test
    void shouldReturnEmptyListWhenNoEvidenceProviderHasData() {
        var context = analysisEvidenceCollector.collect("not-found", AnalysisEvidenceCollectionListener.NO_OP);

        assertTrue(context.evidenceSections().isEmpty());
    }

    @Test
    void shouldOverlapDynatraceAndGitLabCollectionAfterDeploymentContext() throws Exception {
        var blockingDynatracePort = new BlockingDynatraceIncidentPort();
        var blockingGitLabPort = new BlockingGitLabRepositoryPort();
        var gitLabProperties = gitLabProperties();
        gitLabProperties.setBaseUrl("https://gitlab.example.test");

        var gitLabSourceResolveService = mock(GitLabSourceResolveService.class);
        doReturn(new GitLabSourceResolveSession()).when(gitLabSourceResolveService).openSession();
        doReturn(new GitLabSourceResolveMatch(
                "src/main/java/com/example/synthetic/response/TimeoutHandler.java",
                130,
                List.of("src/main/java/com/example/synthetic/response/TimeoutHandler.java")
        )).when(gitLabSourceResolveService).resolveMatch(any(), any());

        var taskExecutor = asyncTaskExecutor();
        try {
            var collector = new AnalysisEvidenceCollector(
                    new ElasticLogEvidenceProvider(new ProductionLikeElasticLogPort()),
                    new DeploymentContextEvidenceProvider(deploymentContextResolver),
                    new DynatraceEvidenceProvider(blockingDynatracePort, deploymentContextResolver),
                    new GitLabDeterministicEvidenceProvider(
                            blockingGitLabPort,
                            gitLabProperties,
                            gitLabSourceResolveService,
                            deploymentContextResolver
                    ),
                    disabledOperationalContextEvidenceProvider(),
                    taskExecutor
            );

            var collectedContext = new AtomicReference<AnalysisContext>();
            var failure = new AtomicReference<Throwable>();
            var worker = new Thread(() -> {
                try {
                    collectedContext.set(collector.collect("timeout-123", AnalysisEvidenceCollectionListener.NO_OP));
                } catch (Throwable exception) {
                    failure.set(exception);
                }
            });

            worker.start();

            assertTrue(blockingDynatracePort.awaitStarted(), "Dynatrace provider should start in background.");
            assertTrue(blockingGitLabPort.awaitStarted(), "GitLab provider should start in background.");

            blockingDynatracePort.release();
            blockingGitLabPort.release();
            worker.join(2_000L);

            assertFalse(worker.isAlive(), "Collector worker should finish after releasing both providers.");
            assertNull(failure.get());
            var context = collectedContext.get();
            assertNotNull(context);
            assertEquals(
                    List.of("elasticsearch", "deployment-context", "dynatrace", "gitlab"),
                    context.evidenceSections().stream().map(section -> section.provider()).toList()
            );
        } finally {
            taskExecutor.shutdown();
        }
    }

    @Test
    void shouldExposeProviderDescriptorsInExplicitPipelineOrder() {
        var descriptors = analysisEvidenceCollector.providerDescriptors();

        assertEquals(5, descriptors.size());
        assertEquals("ELASTICSEARCH_LOGS", descriptors.get(0).stepCode());
        assertEquals("DEPLOYMENT_CONTEXT", descriptors.get(1).stepCode());
        assertEquals("DYNATRACE_RUNTIME_SIGNALS", descriptors.get(2).stepCode());
        assertEquals("GITLAB_RESOLVED_CODE", descriptors.get(3).stepCode());
        assertEquals("OPERATIONAL_CONTEXT", descriptors.get(4).stepCode());
    }

    private static GitLabProperties gitLabProperties() {
        var properties = new GitLabProperties();
        properties.setGroup("sample/runtime");
        return properties;
    }

    private static OperationalContextEvidenceProvider disabledOperationalContextEvidenceProvider() {
        var properties = new OperationalContextProperties();
        properties.setEnabled(false);
        return new OperationalContextEvidenceProvider(
                properties,
                new OperationalContextCatalogLoader(properties),
                new OperationalContextCatalogMatcher(properties),
                new OperationalContextEvidenceMapper()
        );
    }

    private static TaskExecutor directTaskExecutor() {
        return Runnable::run;
    }

    private static ThreadPoolTaskExecutor asyncTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("analysis-evidence-test-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.initialize();
        return executor;
    }

    private static Map<String, String> attributesByName(AnalysisEvidenceItem item) {
        return item.attributes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        attribute -> attribute.name(),
                        attribute -> attribute.value(),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    private static final class ProductionLikeElasticLogPort implements ElasticLogPort {

        @Override
        public List<ElasticLogEntry> findLogEntries(String correlationId) {
            return List.of(new ElasticLogEntry(
                    "2026-04-11T20:57:33.285Z",
                    "ERROR",
                    "svc",
                    "c.e.s.response.TimeoutHandler",
                    "Catalog call timed out",
                    null,
                    "main",
                    null,
                    "tenant-alpha-main-uat1",
                    "pod",
                    "backend",
                    "registry/tenant-alpha-main-uat1/backend:20260411-205733-1-release-candidate-0123456789abcdef0123456789abcdef01234567",
                    "test-index",
                    "svc-ERROR",
                    false,
                    false
            ));
        }

        @Override
        public pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult searchLogsByCorrelationId(
                String correlationId
        ) {
            throw new UnsupportedOperationException("Not needed in this collector test.");
        }
    }

    private static final class BlockingDynatraceIncidentPort extends TestDynatraceIncidentPort {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentEvidence loadIncidentEvidence(
                pl.mkn.incidenttracker.analysis.adapter.dynatrace.DynatraceIncidentQuery query
        ) {
            started.countDown();
            awaitRelease();
            return super.loadIncidentEvidence(query);
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(2, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }

        private void awaitRelease() {
            try {
                assertTrue(release.await(2, TimeUnit.SECONDS), "Blocking Dynatrace port was not released.");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release Dynatrace evidence.", exception);
            }
        }
    }

    private static final class BlockingGitLabRepositoryPort implements GitLabRepositoryPort {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
            started.countDown();
            awaitRelease();
            return List.of(new GitLabRepositoryProjectCandidate(group, "backend", "container-name", 120));
        }

        @Override
        public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
            return List.of();
        }

        @Override
        public GitLabRepositoryFileContent readFile(
                String group,
                String projectName,
                String branch,
                String filePath,
                int maxCharacters
        ) {
            return new GitLabRepositoryFileContent(
                    group,
                    projectName,
                    branch,
                    filePath,
                    "class TimeoutHandler {}",
                    false
            );
        }

        @Override
        public GitLabRepositoryFileChunk readFileChunk(
                String group,
                String projectName,
                String branch,
                String filePath,
                int startLine,
                int endLine,
                int maxCharacters
        ) {
            return new GitLabRepositoryFileChunk(
                    group,
                    projectName,
                    branch,
                    filePath,
                    startLine,
                    endLine,
                    startLine,
                    endLine,
                    endLine - startLine + 1,
                    "class TimeoutHandler {}",
                    false
            );
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(2, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }

        private void awaitRelease() {
            try {
                assertTrue(release.await(2, TimeUnit.SECONDS), "Blocking GitLab port was not released.");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release GitLab evidence.", exception);
            }
        }
    }

}
