package pl.mkn.tdw.features.changeverification.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationAiResponse;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysisProvider;
import pl.mkn.tdw.features.changeverification.ai.preparation.ChangeVerificationPromptPreparationService;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingSeverity;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationVerificationCheckResponse;
import pl.mkn.tdw.features.changeverification.job.error.ChangeVerificationJobNotFoundException;
import pl.mkn.tdw.features.changeverification.job.localworkspace.ChangeVerificationLocalRunPersistence;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationOperationalContextMatcher;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestCommit;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionDiscoveryProperties;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryFileRequest;
import pl.mkn.tdw.integrations.jira.JiraIssueComment;
import pl.mkn.tdw.integrations.jira.JiraIssueLink;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.JiraIssuePort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeVerificationJobServiceTest {

    @Test
    void shouldReturnLiveSnapshotBeforeBackgroundAnalysisCompletes() {
        var taskExecutor = new CapturingTaskExecutor();
        var service = service(taskExecutor);

        var snapshot = service.startJob(new ChangeVerificationJobStartRequest(
                "CRM-123",
                null,
                true,
                true,
                null,
                "gpt-5.4",
                "medium"
        ));

        assertThat(snapshot.jobId()).isNotBlank();
        assertThat(snapshot.status()).isEqualTo("COLLECTING_CONTEXT");
        assertThat(snapshot.currentStepCode()).isEqualTo("JIRA_MATERIAL");
        assertThat(snapshot.result()).isNull();
        assertThat(snapshot.steps())
                .filteredOn(step -> "JIRA_MATERIAL".equals(step.code()))
                .singleElement()
                .extracting("status")
                .isEqualTo("RUNNING");

        taskExecutor.runNext();

        var completed = service.getJob(snapshot.jobId());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.result()).isNotNull();
    }

    @Test
    void shouldCreateCompletedSkeletonJob() {
        var service = service();
        var snapshot = service.startJob(new ChangeVerificationJobStartRequest(
                "CRM-123",
                null,
                true,
                true,
                null,
                "gpt-5.4",
                "medium"
        ));

        assertThat(snapshot.jobId()).isNotBlank();
        assertThat(snapshot.status()).isEqualTo("COMPLETED");
        assertThat(snapshot.steps()).extracting("code").containsExactly(
                "JIRA_MATERIAL",
                "MERGE_REQUEST_DISCOVERY",
                "CHANGED_FILES",
                "INSTRUCTION_CONTEXT",
                "INITIAL_SOURCE_SNAPSHOT",
                "AI_VERIFICATION"
        );
        assertThat(snapshot.contextSections())
                .extracting("category")
                .contains(
                        "change-source",
                        "jira-issue",
                        "merge-requests",
                        "instruction-context",
                        "source-discovery-limits"
                );
        assertThat(snapshot.toolEvidenceSections())
                .filteredOn(section -> "tool-discovery".equals(section.category()))
                .singleElement()
                .extracting(section -> section.items().size())
                .isEqualTo(1);
        assertThat(snapshot.aiActivityEvents()).hasSize(1);
        assertThat(snapshot.steps())
                .filteredOn(step -> "INSTRUCTION_CONTEXT".equals(step.code()))
                .singleElement()
                .extracting("itemCount")
                .isEqualTo(4);
        assertThat(snapshot.steps())
                .filteredOn(step -> "INITIAL_SOURCE_SNAPSHOT".equals(step.code()))
                .singleElement()
                .extracting("label")
                .isEqualTo("Source context ready");
        assertThat(snapshot.preparedPrompt()).isEqualTo("Change Verification test prompt");
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().compliance().status()).isEqualTo("PASSED_WITH_WARNINGS");
        assertThat(snapshot.result().compliance().findings()).singleElement()
                .extracting(ChangeVerificationFindingResponse::source)
                .isEqualTo("INSTRUCTIONS");
        assertThat(service.getJob(snapshot.jobId())).isEqualTo(snapshot);
    }

    @Test
    void shouldThrowWhenJobIsMissing() {
        var service = service();
        assertThatThrownBy(() -> service.getJob("missing-job"))
                .isInstanceOf(ChangeVerificationJobNotFoundException.class);
    }

    private static ChangeVerificationJobService service() {
        return service(new org.springframework.core.task.SyncTaskExecutor());
    }

    private static ChangeVerificationJobService service(org.springframework.core.task.TaskExecutor taskExecutor) {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("CRM/runtime");
        var gitLabRepositoryPort = new TestGitLabRepositoryPort();
        return new ChangeVerificationJobService(
                new ChangeVerificationSourceDiscoveryService(
                        new TestJiraIssuePort(),
                        gitLabRepositoryPort,
                        gitLabProperties,
                        new InstructionContextDiscoveryService(
                                gitLabRepositoryPort,
                                new InstructionDiscoveryProperties()
                        ),
                        new ChangeVerificationOperationalContextMatcher(ignored -> OperationalContextCatalog.empty())
                ),
                new ChangeVerificationPromptPreparationService(),
                new TestComplianceAnalysisProvider(),
                ChangeVerificationLocalRunPersistence.NO_OP,
                taskExecutor
        );
    }

    private static final class CapturingTaskExecutor implements org.springframework.core.task.TaskExecutor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        private void runNext() {
            var task = tasks.poll();
            if (task == null) {
                throw new AssertionError("Expected a captured background task.");
            }
            task.run();
        }
    }

    private static final class TestComplianceAnalysisProvider implements ChangeVerificationComplianceAnalysisProvider {

        @Override
        public ChangeVerificationComplianceAnalysis analyze(
                String jobId,
                ChangeVerificationJobStartRequest request,
                pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult sourceDiscovery,
                AnalysisAiToolEvidenceListener toolEvidenceListener,
                AnalysisAiActivityListener activityListener
        ) {
            toolEvidenceListener.onToolEvidenceUpdated(toolEvidence("gitlab_search_repository_candidates", "COMPLETED"));
            activityListener.onAiActivity(aiActivity("TOOL", "COMPLETED", "gitlab_read_file"));
            return new ChangeVerificationComplianceAnalysis(
                    new ChangeVerificationAiResponse(
                            "PASSED_WITH_WARNINGS",
                            List.of(new ChangeVerificationVerificationCheckResponse(
                                    "instruction-001",
                                    "DEFINED",
                                    "INSTRUCTION_COMPLIANCE",
                                    "AGENTS.md",
                                    "Review local instructions.",
                                    "explicit",
                                    null,
                                    null,
                                    List.of(),
                                    null,
                                    null,
                                    "Local instructions apply to the changed files.",
                                    "WARNING",
                                    "AGENTS.md and changed files",
                                    "Instruction context is available but requires manual confirmation.",
                                    List.of("change-verification/instruction-context"),
                                    List.of(),
                                    "Review local AGENTS.md before approving the change."
                            )),
                            List.of(new ChangeVerificationFindingResponse(
                                    "cv-001",
                                    ChangeVerificationFindingSeverity.LOW,
                                    "INSTRUCTIONS",
                                    "Instruction context was considered.",
                                    "The change has instruction evidence available for AI verification.",
                                    List.of("change-verification/instruction-context"),
                                    "Use instruction evidence in detailed review."
                            )),
                            List.of("Review local AGENTS.md before approving the change."),
                            List.of("No diff content was available in this stage."),
                            "medium"
                    ),
                    null,
                    "Change Verification test prompt",
                    "session-test",
                    null
            );
        }
    }

    private static AnalysisEvidenceSection toolEvidence(String toolName, String outcome) {
        return new AnalysisEvidenceSection(
                "gitlab",
                "tool-discovery",
                List.of(new AnalysisEvidenceItem(
                        "GitLab tool: " + toolName,
                        List.of(
                                new AnalysisEvidenceAttribute("toolName", toolName),
                                new AnalysisEvidenceAttribute("toolCallId", "call-" + toolName),
                                new AnalysisEvidenceAttribute("status", outcome)
                        )
                ))
        );
    }

    private static AnalysisAiActivityEvent aiActivity(String category, String status, String toolName) {
        return new AnalysisAiActivityEvent(
                "event-" + category + "-" + status + "-" + (toolName != null ? toolName : "model"),
                null,
                "test-event",
                category,
                status,
                "AI activity",
                "Synthetic AI activity.",
                null,
                null,
                toolName != null ? "call-" + toolName : null,
                toolName,
                java.time.Instant.parse("2026-07-25T10:00:00Z"),
                Map.of()
        );
    }

    private static final class TestJiraIssuePort implements JiraIssuePort {

        @Override
        public JiraIssueMaterial getIssueMaterial(String issueKey) {
            return new JiraIssueMaterial(
                    issueKey,
                    "https://jira.example.com/browse/" + issueKey,
                    "Customer profile verification",
                    "As a release owner I want the profile endpoint to expose new status.",
                    "Story",
                    "Ready for Test",
                    List.of("release"),
                    List.of("Status is returned for active customer."),
                    List.of(new JiraIssueLink(
                            "remote-link",
                            "Functional design",
                            "https://confluence.example.com/customer-profile"
                    )),
                    List.of(new JiraIssueComment(
                            "Anna Kowalska",
                            "2026-07-24T10:00:00.000Z",
                            "Remember migrated customers."
                    )),
                    List.of("Acceptance criteria field config was synthetic in test.")
            );
        }
    }

    private static final class TestGitLabRepositoryPort implements GitLabRepositoryPort {

        @Override
        public List<pl.mkn.tdw.integrations.gitlab.GitLabRepositoryProjectCandidate> searchProjects(
                String group,
                List<String> projectHints
        ) {
            return List.of();
        }

        @Override
        public List<pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate> searchCandidateFiles(
                pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery query
        ) {
            return List.of();
        }

        @Override
        public List<pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile> listRepositoryFiles(
                String group,
                String projectName,
                String branch,
                String pathPrefix
        ) {
            return List.of();
        }

        @Override
        public pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent readFile(
                String group,
                String projectName,
                String branch,
                String filePath,
                int maxCharacters
        ) {
            var content = switch (filePath) {
                case "AGENTS.md" -> "Root repository rules.";
                case ".github/copilot-instructions.md" -> "Follow `docs/architecture-instructions.md`.";
                case "src/main/java/AGENTS.md" -> "Java source rules.";
                case "docs/architecture-instructions.md" -> "Architecture rules.";
                default -> null;
            };
            if (content == null) {
                throw new IllegalArgumentException("Missing test instruction file: " + filePath);
            }
            return new pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent(
                    group,
                    projectName,
                    branch,
                    filePath,
                    content,
                    false
            );
        }

        @Override
        public InstructionRepositoryFile readFile(InstructionRepositoryFileRequest request) {
            try {
                var file = readFile("CRM/runtime", "customer-api", request.ref(), request.path(), request.maxCharacters());
                return new InstructionRepositoryFile(
                        request.repositoryKey(),
                        request.ref(),
                        request.path(),
                        true,
                        file.content(),
                        file.truncated(),
                        null
                );
            } catch (RuntimeException exception) {
                return InstructionRepositoryFile.missing(request.repositoryKey(), request.ref(), request.path());
            }
        }

        @Override
        public pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryInventory loadFileInventory(
                pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryInventoryRequest request
        ) {
            return pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryInventory.available(List.of(
                    "AGENTS.md",
                    ".github/copilot-instructions.md",
                    "src/main/java/AGENTS.md",
                    "docs/architecture-instructions.md"
            ));
        }

        @Override
        public pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileChunk readFileChunk(
                String group,
                String projectName,
                String branch,
                String filePath,
                int startLine,
                int endLine,
                int maxCharacters
        ) {
            return null;
        }

        @Override
        public GitLabMergeRequestSearchResult findMergeRequestsByIssueKey(
                String group,
                String issueKey,
                int maxResults
        ) {
            return new GitLabMergeRequestSearchResult(
                    issueKey,
                    group,
                    List.of(new GitLabMergeRequest(
                            1001L,
                            7L,
                            77L,
                            "CRM/runtime/customer-api",
                            "CRM-123 customer status",
                            "merged",
                            "https://gitlab.example.com/CRM/runtime/customer-api/-/merge_requests/7",
                            "feature/CRM-123-customer-status",
                            "release/2026.08",
                            "Jan Nowak",
                            "2026-07-20T10:00:00.000Z",
                            "2026-07-21T10:00:00.000Z",
                            "2026-07-21T11:00:00.000Z",
                            "4",
                            List.of(new GitLabMergeRequestCommit(
                                    "abcdef123456",
                                    "abcdef12",
                                    "CRM-123 add status",
                                    "Jan Nowak",
                                    "2026-07-20T10:00:00.000Z"
                            )),
                            List.of(new GitLabMergeRequestChangedFile(
                                    "src/main/java/CustomerController.java",
                                    "src/main/java/CustomerController.java",
                                    false,
                                    false,
                                    false
                            )),
                            List.of()
                    )),
                    List.of("MR search synthetic test limit.")
            );
        }
    }
}
