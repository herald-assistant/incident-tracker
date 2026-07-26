package pl.mkn.tdw.features.changeverification.job.state;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationComplianceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationExecutionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingSeverity;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobMode;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStateSnapshot;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;
import pl.mkn.tdw.features.changeverification.job.report.ChangeVerificationReportMapper;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationSmokePackAnalysis;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationRepositorySnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestCommit;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;
import pl.mkn.tdw.integrations.jira.JiraIssueComment;
import pl.mkn.tdw.integrations.jira.JiraIssueLink;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ChangeVerificationJobState {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STEP_JIRA_MATERIAL = "JIRA_MATERIAL";
    private static final String STEP_MERGE_REQUEST_DISCOVERY = "MERGE_REQUEST_DISCOVERY";
    private static final String STEP_CHANGED_FILES = "CHANGED_FILES";
    private static final String STEP_INSTRUCTION_CONTEXT = "INSTRUCTION_CONTEXT";
    private static final String STEP_INITIAL_SOURCE_SNAPSHOT = "INITIAL_SOURCE_SNAPSHOT";
    private static final String STEP_SOURCE_CONTEXT_READY_LABEL = "Source context ready";
    private static final String STEP_AI_VERIFICATION = "AI_VERIFICATION";
    private static final String STEP_SMOKE_PACK_GENERATION = "SMOKE_PACK_GENERATION";
    private static final String STEP_EXECUTION = "EXECUTION";
    private static final String PHASE_CONTEXT = "CONTEXT";
    private static final String PHASE_AI = "AI";
    private static final String PHASE_EXECUTION = "EXECUTION";
    private static final AnalysisEvidenceReference CHANGE_CONTEXT_EVIDENCE =
            new AnalysisEvidenceReference("change-verification", "change-source");
    private static final AnalysisEvidenceReference JIRA_EVIDENCE =
            new AnalysisEvidenceReference("change-verification", "jira-issue");
    private static final AnalysisEvidenceReference MERGE_REQUEST_EVIDENCE =
            new AnalysisEvidenceReference("change-verification", "merge-requests");
    private static final AnalysisEvidenceReference REPOSITORY_SCOPE_EVIDENCE =
            new AnalysisEvidenceReference("change-verification", "repository-scope");
    private static final AnalysisEvidenceReference SOURCE_LIMITS_EVIDENCE =
            new AnalysisEvidenceReference("change-verification", "source-discovery-limits");
    private static final AnalysisEvidenceReference INSTRUCTION_CONTEXT_EVIDENCE =
            new AnalysisEvidenceReference("change-verification", "instruction-context");
    private static final AnalysisEvidenceReference INSTRUCTION_LIMITS_EVIDENCE =
            new AnalysisEvidenceReference("change-verification", "instruction-source-limits");

    private final String jobId;
    private final ChangeVerificationJobStartRequest request;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String status;
    private String currentStepCode;
    private String currentStepLabel;
    private String errorCode;
    private String errorMessage;
    private String preparedPrompt;
    private ChangeVerificationResultResponse result;
    private AnalysisReport report;
    private List<AnalysisJobStepResponse> steps;
    private List<AnalysisEvidenceSection> contextSections;
    private List<AnalysisEvidenceSection> toolEvidenceSections;
    private List<AnalysisAiActivityEvent> aiActivityEvents;
    private ChangeVerificationSourceDiscoveryResult sourceDiscovery;
    private JiraIssueMaterial jiraIssue;
    private GitLabMergeRequestSearchResult mergeRequests;
    private InstructionContextResult instructionContext;
    private List<String> sourceDiscoveryLimitations;
    private ChangeVerificationComplianceAnalysis complianceAnalysis;
    private ChangeVerificationSmokePackAnalysis smokePackAnalysis;
    private ChangeVerificationSmokePackResponse smokePack;
    private ChangeVerificationExecutionResponse execution;

    public ChangeVerificationJobState(String jobId, ChangeVerificationJobStartRequest request) {
        this.jobId = jobId;
        this.request = request;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = "QUEUED";
        this.currentStepCode = null;
        this.currentStepLabel = null;
        this.steps = List.of();
        this.contextSections = List.of();
        this.toolEvidenceSections = List.of();
        this.aiActivityEvents = List.of();
        this.sourceDiscoveryLimitations = List.of();
    }

    public synchronized void markSourceDiscoveryStarted() {
        var now = Instant.now();
        status = "COLLECTING_CONTEXT";
        currentStepCode = STEP_JIRA_MATERIAL;
        currentStepLabel = "Jira material";
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markJiraMaterialStarted(String issueKey) {
        var now = Instant.now();
        status = "COLLECTING_CONTEXT";
        currentStepCode = STEP_JIRA_MATERIAL;
        currentStepLabel = "Jira material";
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markJiraMaterialCompleted(
            String issueKey,
            JiraIssueMaterial jiraIssue,
            List<String> limitations
    ) {
        var now = Instant.now();
        this.jiraIssue = jiraIssue;
        this.sourceDiscoveryLimitations = limitations != null ? List.copyOf(limitations) : List.of();
        currentStepCode = STEP_MERGE_REQUEST_DISCOVERY;
        currentStepLabel = "Merge request discovery";
        updatedAt = now;
        contextSections = contextSections(partialSourceDiscovery(issueKey));
        steps = steps(now);
    }

    public synchronized void markMergeRequestDiscoveryStarted(String issueKey) {
        var now = Instant.now();
        currentStepCode = STEP_MERGE_REQUEST_DISCOVERY;
        currentStepLabel = "Merge request discovery";
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markMergeRequestDiscoveryCompleted(
            String issueKey,
            GitLabMergeRequestSearchResult mergeRequests,
            List<String> limitations
    ) {
        var now = Instant.now();
        this.mergeRequests = mergeRequests;
        this.sourceDiscoveryLimitations = limitations != null ? List.copyOf(limitations) : List.of();
        currentStepCode = STEP_CHANGED_FILES;
        currentStepLabel = "Changed files";
        updatedAt = now;
        contextSections = contextSections(partialSourceDiscovery(issueKey));
        steps = steps(now);
    }

    public synchronized void markInstructionContextStarted(GitLabMergeRequestSearchResult mergeRequests) {
        var now = Instant.now();
        currentStepCode = STEP_INSTRUCTION_CONTEXT;
        currentStepLabel = "Instruction context";
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markInstructionContextCompleted(
            GitLabMergeRequestSearchResult mergeRequests,
            InstructionContextResult instructionContext,
            List<String> limitations
    ) {
        var now = Instant.now();
        this.mergeRequests = mergeRequests;
        this.instructionContext = instructionContext;
        this.sourceDiscoveryLimitations = limitations != null ? List.copyOf(limitations) : List.of();
        currentStepCode = STEP_INITIAL_SOURCE_SNAPSHOT;
        currentStepLabel = STEP_SOURCE_CONTEXT_READY_LABEL;
        updatedAt = now;
        contextSections = contextSections(partialSourceDiscovery(resolvedIssueKey()));
        steps = steps(now);
    }

    public synchronized void markSourceDiscoveryCompleted(ChangeVerificationSourceDiscoveryResult sourceDiscovery) {
        var now = Instant.now();
        this.sourceDiscovery = sourceDiscovery;
        if (sourceDiscovery != null) {
            this.jiraIssue = sourceDiscovery.jiraIssue();
            this.mergeRequests = sourceDiscovery.mergeRequests();
            this.instructionContext = sourceDiscovery.instructionContext();
            this.sourceDiscoveryLimitations = sourceDiscovery.limitations();
        }
        contextSections = contextSections(sourceDiscovery);
        currentStepCode = nextCurrentStepAfterSourceDiscovery();
        currentStepLabel = nextCurrentStepLabelAfterSourceDiscovery();
        status = currentStepCode != null ? "AWAITING_AI" : STATUS_COMPLETED;
        updatedAt = now;
        steps = steps(now);
        if (currentStepCode == null) {
            markCompleted(null, null);
        }
    }

    public synchronized void markChangedFilesCompleted(String issueKey) {
        var now = Instant.now();
        currentStepCode = request.checkInstructionCompliance()
                ? STEP_INSTRUCTION_CONTEXT
                : STEP_INITIAL_SOURCE_SNAPSHOT;
        currentStepLabel = request.checkInstructionCompliance()
                ? "Instruction context"
                : STEP_SOURCE_CONTEXT_READY_LABEL;
        updatedAt = now;
        contextSections = contextSections(partialSourceDiscovery(issueKey));
        steps = steps(now);
    }

    public synchronized void markPreparedPrompt(String preparedPrompt) {
        if (!org.springframework.util.StringUtils.hasText(preparedPrompt)) {
            return;
        }
        var now = Instant.now();
        this.preparedPrompt = preparedPrompt;
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markAiVerificationStarted() {
        var now = Instant.now();
        status = "ANALYZING";
        currentStepCode = STEP_AI_VERIFICATION;
        currentStepLabel = "AI verification";
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markAiVerificationCompleted(ChangeVerificationComplianceAnalysis complianceAnalysis) {
        var now = Instant.now();
        this.complianceAnalysis = complianceAnalysis;
        preparedPrompt = preparedPrompt(complianceAnalysis, smokePackAnalysis);
        currentStepCode = smokePackRequested() ? STEP_SMOKE_PACK_GENERATION : null;
        currentStepLabel = smokePackRequested() ? "Smoke pack generation" : null;
        status = "ANALYZING";
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markSmokePackGenerationStarted() {
        var now = Instant.now();
        status = "ANALYZING";
        currentStepCode = STEP_SMOKE_PACK_GENERATION;
        currentStepLabel = "Smoke pack generation";
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markAiToolEvidenceUpdated(AnalysisEvidenceSection section) {
        if (section == null || !section.hasItems()) {
            return;
        }
        var now = Instant.now();
        toolEvidenceSections = upsertEvidenceSection(toolEvidenceSections, section);
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markAiActivity(AnalysisAiActivityEvent event) {
        if (event == null) {
            return;
        }
        var now = Instant.now();
        var events = new ArrayList<>(aiActivityEvents);
        events.add(event);
        aiActivityEvents = List.copyOf(events);
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markSmokePackGenerationCompleted(ChangeVerificationSmokePackAnalysis smokePackAnalysis) {
        var now = Instant.now();
        this.smokePackAnalysis = smokePackAnalysis;
        smokePack = smokePackResult();
        preparedPrompt = preparedPrompt(complianceAnalysis, smokePackAnalysis);
        currentStepCode = null;
        currentStepLabel = null;
        status = "SMOKE_PACK_READY";
        updatedAt = now;
        steps = steps(now);
    }

    public synchronized void markCompleted(
            ChangeVerificationComplianceAnalysis complianceAnalysis,
            ChangeVerificationSmokePackAnalysis smokePackAnalysis
    ) {
        var now = Instant.now();
        if (complianceAnalysis != null) {
            this.complianceAnalysis = complianceAnalysis;
        }
        if (smokePackAnalysis != null) {
            this.smokePackAnalysis = smokePackAnalysis;
        }
        status = STATUS_COMPLETED;
        currentStepCode = null;
        currentStepLabel = null;
        updatedAt = now;
        completedAt = now;
        preparedPrompt = preparedPrompt(complianceAnalysis, smokePackAnalysis);
        if (contextSections.isEmpty()) {
            contextSections = contextSections(sourceDiscovery);
        }
        smokePack = smokePackResult();
        steps = steps(now);
        execution = executionResult();
        result = new ChangeVerificationResultResponse(
                STATUS_COMPLETED,
                resolvedIssueKey(),
                resolvedIssueUrl(),
                request.modes(),
                preparedPrompt,
                complianceResult(),
                smokePack,
                execution,
                complianceAnalysis != null ? complianceAnalysis.usage() : null
        );
        report = ChangeVerificationReportMapper.toReport(result);
    }

    public synchronized void markFailed(String errorCode, String errorMessage) {
        var now = Instant.now();
        status = STATUS_FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        updatedAt = now;
        completedAt = now;
        currentStepCode = null;
        currentStepLabel = null;
        steps = steps(now);
    }

    public synchronized ChangeVerificationSmokePackResponse smokePack() {
        return smokePack != null ? smokePack : smokePackResult();
    }

    public synchronized void updateSmokePack(ChangeVerificationSmokePackResponse smokePack) {
        this.smokePack = smokePack != null
                ? smokePack
                : smokePackResult();
        updatedAt = Instant.now();
        if (result != null) {
            result = new ChangeVerificationResultResponse(
                    result.status(),
                    result.issueKey(),
                    result.issueUrl(),
                    result.modes(),
                    result.prompt(),
                    result.compliance(),
                    this.smokePack,
                    result.execution(),
                    result.usage()
            );
            report = ChangeVerificationReportMapper.toReport(result);
        }
    }

    public synchronized void markExecutionCompleted(ChangeVerificationExecutionResponse execution) {
        this.execution = execution != null ? execution : executionResult();
        updatedAt = Instant.now();
        if (result != null) {
            result = new ChangeVerificationResultResponse(
                    result.status(),
                    result.issueKey(),
                    result.issueUrl(),
                    result.modes(),
                    result.prompt(),
                    result.compliance(),
                    result.smokePack(),
                    this.execution,
                    result.usage()
            );
            report = ChangeVerificationReportMapper.toReport(result);
        }
        steps = steps(updatedAt);
    }

    public synchronized ChangeVerificationJobStateSnapshot snapshot() {
        return new ChangeVerificationJobStateSnapshot(
                jobId,
                request.issueKey(),
                request.issueUrl(),
                request.modes(),
                request.checkStoryCompliance(),
                request.checkInstructionCompliance(),
                request.aiOptions().model(),
                request.aiOptions().reasoningEffort(),
                status,
                currentStepCode,
                currentStepLabel,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                steps,
                contextSections,
                toolEvidenceSections,
                aiActivityEvents,
                preparedPrompt,
                result,
                report
        );
    }

    private List<AnalysisJobStepResponse> steps(Instant completedAt) {
        var startedAt = createdAt;
        var steps = new ArrayList<AnalysisJobStepResponse>();
        steps.add(step(
                STEP_JIRA_MATERIAL,
                "Jira material",
                PHASE_CONTEXT,
                jiraMaterialStepStatus(),
                jiraMaterialStepMessage(),
                jiraIssue != null ? 1 : null,
                startedAt,
                completedAt,
                List.of(),
                jiraIssue != null ? List.of(JIRA_EVIDENCE) : List.of()
        ));
        steps.add(step(
                STEP_MERGE_REQUEST_DISCOVERY,
                "Merge request discovery",
                PHASE_CONTEXT,
                mergeRequestDiscoveryStepStatus(),
                mergeRequestDiscoveryStepMessage(),
                mergeRequestCount(),
                startedAt,
                completedAt,
                jiraIssue != null ? List.of(JIRA_EVIDENCE) : List.of(CHANGE_CONTEXT_EVIDENCE),
                mergeRequests != null ? List.of(MERGE_REQUEST_EVIDENCE) : List.of()
        ));
        steps.add(step(
                STEP_CHANGED_FILES,
                "Changed files",
                PHASE_CONTEXT,
                changedFilesStepStatus(),
                changedFilesStepMessage(),
                changedFileCount(),
                startedAt,
                completedAt,
                mergeRequests != null ? List.of(MERGE_REQUEST_EVIDENCE) : List.of(),
                mergeRequests != null ? List.of(MERGE_REQUEST_EVIDENCE) : List.of()
        ));
        steps.add(step(
                STEP_INSTRUCTION_CONTEXT,
                "Instruction context",
                PHASE_CONTEXT,
                instructionStepStatus(),
                instructionStepMessage(),
                instructionItemCount(),
                startedAt,
                completedAt,
                List.of(CHANGE_CONTEXT_EVIDENCE),
                producedInstructionEvidence()
        ));
        steps.add(step(
                STEP_INITIAL_SOURCE_SNAPSHOT,
                STEP_SOURCE_CONTEXT_READY_LABEL,
                PHASE_CONTEXT,
                initialSourceSnapshotStepStatus(),
                initialSourceSnapshotStepMessage(),
                contextItemCount(),
                startedAt,
                completedAt,
                producedSourceEvidence(),
                producedSourceEvidence()
        ));
        steps.add(step(
                STEP_AI_VERIFICATION,
                "AI verification",
                PHASE_AI,
                aiVerificationStepStatus(),
                aiVerificationMessage(),
                aiFindingCount(),
                startedAt,
                completedAt,
                List.of(CHANGE_CONTEXT_EVIDENCE),
                List.of(),
                complianceAnalysis != null ? complianceAnalysis.usage() : null
        ));
        steps.add(step(
                STEP_SMOKE_PACK_GENERATION,
                "Smoke pack generation",
                PHASE_AI,
                smokePackGenerationStepStatus(),
                smokePackStepMessage(),
                smokePackRequested() && smokePack != null ? smokePack.tests().size() : null,
                startedAt,
                completedAt,
                List.of(CHANGE_CONTEXT_EVIDENCE),
                List.of()
        ));
        steps.add(step(
                STEP_EXECUTION,
                "Smoke execution",
                PHASE_EXECUTION,
                executionStepStatus(),
                executionStepMessage(),
                execution != null ? execution.testResults().size() : null,
                startedAt,
                completedAt,
                List.of(CHANGE_CONTEXT_EVIDENCE),
                List.of()
        ));
        return steps;
    }

    private AnalysisJobStepResponse step(
            String code,
            String label,
            String phase,
            String status,
            String message,
            Integer itemCount,
            Instant startedAt,
            Instant completedAt,
            List<AnalysisEvidenceReference> consumesEvidence,
            List<AnalysisEvidenceReference> producesEvidence
    ) {
        return step(code, label, phase, status, message, itemCount, startedAt, completedAt, consumesEvidence, producesEvidence, null);
    }

    private AnalysisJobStepResponse step(
            String code,
            String label,
            String phase,
            String status,
            String message,
            Integer itemCount,
            Instant startedAt,
            Instant completedAt,
            List<AnalysisEvidenceReference> consumesEvidence,
            List<AnalysisEvidenceReference> producesEvidence,
            pl.mkn.tdw.shared.ai.AnalysisAiUsage usage
    ) {
        return new AnalysisJobStepResponse(
                code,
                label,
                phase,
                status,
                message,
                itemCount,
                startedAt,
                completedAt,
                consumesEvidence,
                producesEvidence,
                usage
        );
    }

    private List<AnalysisEvidenceSection> contextSections(ChangeVerificationSourceDiscoveryResult sourceDiscovery) {
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.add(new AnalysisEvidenceSection(
                CHANGE_CONTEXT_EVIDENCE.provider(),
                CHANGE_CONTEXT_EVIDENCE.category(),
                List.of(new AnalysisEvidenceItem(
                        "Change source",
                        List.of(
                                attribute("issueKey", resolvedIssueKey()),
                                attribute("issueUrl", resolvedIssueUrl()),
                                attribute("modes", request.modes().toString()),
                                attribute("checkStoryCompliance", request.checkStoryCompliance()),
                                attribute("checkInstructionCompliance", request.checkInstructionCompliance())
                        )
                ))
        ));

        if (sourceDiscovery != null && sourceDiscovery.jiraIssue() != null) {
            sections.add(jiraSection(sourceDiscovery.jiraIssue()));
        }

        if (sourceDiscovery != null && sourceDiscovery.mergeRequests() != null) {
            sections.add(mergeRequestSection(sourceDiscovery.mergeRequests().mergeRequests()));
        }

        if (sourceDiscovery != null && !sourceDiscovery.repositories().isEmpty()) {
            sections.add(repositoryScopeSection(sourceDiscovery.repositories()));
        }

        if (sourceDiscovery != null && sourceDiscovery.instructionContext() != null) {
            sections.add(instructionContextSection(sourceDiscovery.instructionContext().sources()));
        }

        var limitations = sourceLimitations(sourceDiscovery);
        if (!limitations.isEmpty()) {
            sections.add(new AnalysisEvidenceSection(
                    SOURCE_LIMITS_EVIDENCE.provider(),
                    SOURCE_LIMITS_EVIDENCE.category(),
                    List.of(new AnalysisEvidenceItem(
                            "Source discovery limits",
                            List.of(attribute("limitations", String.join("\n", limitations)))
                    ))
            ));
        }

        var instructionLimitations = instructionLimitations(sourceDiscovery);
        if (!instructionLimitations.isEmpty()) {
            sections.add(new AnalysisEvidenceSection(
                    INSTRUCTION_LIMITS_EVIDENCE.provider(),
                    INSTRUCTION_LIMITS_EVIDENCE.category(),
                    List.of(new AnalysisEvidenceItem(
                            "Instruction discovery limits",
                            List.of(attribute("limitations", String.join("\n", instructionLimitations)))
                    ))
            ));
        }

        return List.copyOf(sections);
    }

    private List<AnalysisEvidenceSection> upsertEvidenceSection(
            List<AnalysisEvidenceSection> sections,
            AnalysisEvidenceSection updatedSection
    ) {
        var merged = new ArrayList<>(sections != null ? sections : List.<AnalysisEvidenceSection>of());
        for (var index = 0; index < merged.size(); index++) {
            var current = merged.get(index);
            if (sameSection(current, updatedSection)) {
                merged.set(index, mergeSection(current, updatedSection));
                return List.copyOf(merged);
            }
        }
        merged.add(updatedSection);
        return List.copyOf(merged);
    }

    private AnalysisEvidenceSection mergeSection(
            AnalysisEvidenceSection current,
            AnalysisEvidenceSection updated
    ) {
        var items = new ArrayList<AnalysisEvidenceItem>();
        items.addAll(current.items());
        for (var updatedItem : updated.items()) {
            if (items.stream().noneMatch(existing -> sameItem(existing, updatedItem))) {
                items.add(updatedItem);
            }
        }
        return new AnalysisEvidenceSection(updated.provider(), updated.category(), List.copyOf(items));
    }

    private boolean sameItem(AnalysisEvidenceItem left, AnalysisEvidenceItem right) {
        return left != null
                && right != null
                && java.util.Objects.equals(left.title(), right.title())
                && java.util.Objects.equals(left.attributes(), right.attributes());
    }

    private boolean sameSection(AnalysisEvidenceSection left, AnalysisEvidenceSection right) {
        return left != null
                && right != null
                && java.util.Objects.equals(left.provider(), right.provider())
                && java.util.Objects.equals(left.category(), right.category());
    }

    private ChangeVerificationSourceDiscoveryResult partialSourceDiscovery(String issueKey) {
        return new ChangeVerificationSourceDiscoveryResult(
                issueKey,
                request.issueUrl(),
                jiraIssue,
                mergeRequests,
                instructionContext,
                sourceDiscoveryLimitations
        );
    }

    private AnalysisEvidenceSection jiraSection(JiraIssueMaterial issue) {
        var items = new ArrayList<AnalysisEvidenceItem>();
        items.add(new AnalysisEvidenceItem(
                "Jira issue: " + fallback(issue.issueKey(), resolvedIssueKey()),
                List.of(
                        attribute("issueKey", issue.issueKey()),
                        attribute("issueUrl", issue.issueUrl()),
                        attribute("summary", issue.summary()),
                        attribute("issueType", issue.issueType()),
                        attribute("status", issue.status()),
                        attribute("labels", issue.labels().toString()),
                        attribute("description", issue.description()),
                        attribute("acceptanceCriteria", String.join("\n\n", issue.acceptanceCriteria()))
                )
        ));
        issue.links().stream().map(this::jiraLinkItem).forEach(items::add);
        if (issue.parentIssue() != null) {
            items.add(jiraParentIssueItem(issue.parentIssue()));
        }
        issue.subTasks().stream().map(this::jiraSubTaskItem).forEach(items::add);
        issue.confluencePages().stream().map(this::jiraConfluencePageItem).forEach(items::add);
        issue.comments().stream().map(this::jiraCommentItem).forEach(items::add);
        return new AnalysisEvidenceSection(JIRA_EVIDENCE.provider(), JIRA_EVIDENCE.category(), items);
    }

    private AnalysisEvidenceItem jiraLinkItem(JiraIssueLink link) {
        return new AnalysisEvidenceItem(
                "Jira link: " + fallback(link.title(), link.url()),
                List.of(
                        attribute("type", link.type()),
                        attribute("title", link.title()),
                        attribute("url", link.url())
                )
        );
    }

    private AnalysisEvidenceItem jiraParentIssueItem(JiraIssueMaterial parentIssue) {
        return new AnalysisEvidenceItem(
                "Jira parent context: " + fallback(parentIssue.issueKey(), parentIssue.summary()),
                List.of(
                        attribute("relation", "PARENT_CONTEXT"),
                        attribute("issueKey", parentIssue.issueKey()),
                        attribute("issueUrl", parentIssue.issueUrl()),
                        attribute("summary", parentIssue.summary()),
                        attribute("issueType", parentIssue.issueType()),
                        attribute("status", parentIssue.status()),
                        attribute("description", parentIssue.description()),
                        attribute("acceptanceCriteria", String.join("\n\n", parentIssue.acceptanceCriteria())),
                        attribute("subTaskKeys", parentIssue.subTasks().stream()
                                .map(JiraIssueMaterial::issueKey)
                                .toList()
                                .toString())
                )
        );
    }

    private AnalysisEvidenceItem jiraSubTaskItem(JiraIssueMaterial subTask) {
        return new AnalysisEvidenceItem(
                "Jira subtask: " + fallback(subTask.issueKey(), subTask.summary()),
                List.of(
                        attribute("issueKey", subTask.issueKey()),
                        attribute("issueUrl", subTask.issueUrl()),
                        attribute("summary", subTask.summary()),
                        attribute("issueType", subTask.issueType()),
                        attribute("status", subTask.status()),
                        attribute("description", subTask.description()),
                        attribute("acceptanceCriteria", String.join("\n\n", subTask.acceptanceCriteria()))
                )
        );
    }

    private AnalysisEvidenceItem jiraConfluencePageItem(pl.mkn.tdw.integrations.jira.JiraConfluencePage page) {
        return new AnalysisEvidenceItem(
                "Confluence page: " + fallback(page.title(), page.url()),
                List.of(
                        attribute("pageId", page.pageId()),
                        attribute("title", page.title()),
                        attribute("url", page.url()),
                        attribute("version", page.version()),
                        attribute("content", page.content()),
                        attribute("limitations", page.limitations().toString())
                )
        );
    }

    private AnalysisEvidenceItem jiraCommentItem(JiraIssueComment comment) {
        return new AnalysisEvidenceItem(
                "Jira comment: " + fallback(comment.author(), comment.createdAt()),
                List.of(
                        attribute("author", comment.author()),
                        attribute("createdAt", comment.createdAt()),
                        attribute("body", comment.body())
                )
        );
    }

    private AnalysisEvidenceSection mergeRequestSection(List<GitLabMergeRequest> mergeRequests) {
        var items = mergeRequests.stream()
                .map(this::mergeRequestItem)
                .toList();
        return new AnalysisEvidenceSection(MERGE_REQUEST_EVIDENCE.provider(), MERGE_REQUEST_EVIDENCE.category(), items);
    }

    private AnalysisEvidenceSection repositoryScopeSection(List<ChangeVerificationRepositorySnapshot> repositories) {
        var items = repositories.stream()
                .map(this::repositoryScopeItem)
                .toList();
        return new AnalysisEvidenceSection(
                REPOSITORY_SCOPE_EVIDENCE.provider(),
                REPOSITORY_SCOPE_EVIDENCE.category(),
                items
        );
    }

    private AnalysisEvidenceItem repositoryScopeItem(ChangeVerificationRepositorySnapshot repository) {
        return new AnalysisEvidenceItem(
                "Repository: " + fallback(repository.projectPath(), repository.repositoryKey()),
                List.of(
                        attribute("repositoryKey", repository.repositoryKey()),
                        attribute("projectPath", repository.projectPath()),
                        attribute("rootGroup", repository.rootGroup()),
                        attribute("groupPath", repository.groupPath()),
                        attribute("repositoryName", repository.repositoryName()),
                        attribute("projectName", repository.projectName()),
                        attribute("sourceRef", repository.sourceRef()),
                        attribute("targetRef", repository.targetRef()),
                        attribute("mergeRequestCount", repository.mergeRequests().size()),
                        attribute("mergeRequests", repository.mergeRequests().stream()
                                .map(mr -> fallback(mr.webUrl(), "!" + mr.iid()))
                                .toList()
                                .toString()),
                        attribute("changedFileCount", repository.changedFiles().size()),
                        attribute("changedFiles", repository.changedFiles().stream()
                                .map(file -> file.path() + " " + file.mergeRequestRefs())
                                .toList()
                                .toString()),
                        attribute("instructionSourceCount", repository.instructionSources().size()),
                        attribute("instructionSources", repository.instructionSources().stream()
                                .map(source -> source.path() + "@" + source.ref())
                                .toList()
                                .toString()),
                        attribute("operationalContextMatchCount", repository.operationalContextMatches().size()),
                        attribute("operationalContextMatches", repository.operationalContextMatches().stream()
                                .map(match -> "%s -> %s -> %s:%s".formatted(
                                        match.repositoryId(),
                                        match.codeSearchScopeId(),
                                        match.targetType(),
                                        match.targetId()
                                ))
                                .toList()
                                .toString()),
                        attribute("limitations", String.join("\n", repository.limitations()))
                )
        );
    }

    private AnalysisEvidenceItem mergeRequestItem(GitLabMergeRequest mergeRequest) {
        return new AnalysisEvidenceItem(
                "MR: " + fallback(mergeRequest.title(), mergeRequest.webUrl()),
                List.of(
                        attribute("id", mergeRequest.id()),
                        attribute("iid", mergeRequest.iid()),
                        attribute("projectId", mergeRequest.projectId()),
                        attribute("projectPath", mergeRequest.projectPath()),
                        attribute("title", mergeRequest.title()),
                        attribute("state", mergeRequest.state()),
                        attribute("webUrl", mergeRequest.webUrl()),
                        attribute("sourceBranch", mergeRequest.sourceBranch()),
                        attribute("targetBranch", mergeRequest.targetBranch()),
                        attribute("authorName", mergeRequest.authorName()),
                        attribute("createdAt", mergeRequest.createdAt()),
                        attribute("updatedAt", mergeRequest.updatedAt()),
                        attribute("mergedAt", mergeRequest.mergedAt()),
                        attribute("changesCount", mergeRequest.changesCount()),
                        attribute("commitCount", mergeRequest.commits().size()),
                        attribute("commits", commits(mergeRequest.commits())),
                        attribute("changedFileCount", mergeRequest.changedFiles().size()),
                        attribute("changedFiles", changedFiles(mergeRequest.changedFiles())),
                        attribute("limitations", String.join("\n", mergeRequest.limitations()))
                )
        );
    }

    private String commits(List<GitLabMergeRequestCommit> commits) {
        return commits.stream()
                .map(commit -> "%s %s".formatted(fallback(commit.shortId(), commit.id()), fallback(commit.title(), "")))
                .toList()
                .toString();
    }

    private String changedFiles(List<GitLabMergeRequestChangedFile> changedFiles) {
        return changedFiles.stream()
                .map(file -> fallback(file.newPath(), file.oldPath()))
                .toList()
                .toString();
    }

    private AnalysisEvidenceSection instructionContextSection(List<InstructionSource> sources) {
        var items = sources.stream()
                .map(this::instructionSourceItem)
                .toList();
        return new AnalysisEvidenceSection(
                INSTRUCTION_CONTEXT_EVIDENCE.provider(),
                INSTRUCTION_CONTEXT_EVIDENCE.category(),
                items
        );
    }

    private AnalysisEvidenceItem instructionSourceItem(InstructionSource source) {
        return new AnalysisEvidenceItem(
                "Instruction: " + source.path(),
                List.of(
                        attribute("repositoryKey", source.repositoryKey()),
                        attribute("ref", source.ref()),
                        attribute("path", source.path()),
                        attribute("kind", source.kind()),
                        attribute("referencedBy", source.referencedBy()),
                        attribute("truncated", source.truncated()),
                        attribute("applicableChangedFiles", source.applicableChangedFiles().toString()),
                        attribute("content", source.content())
                )
        );
    }

    private List<String> sourceLimitations(ChangeVerificationSourceDiscoveryResult sourceDiscovery) {
        var limitations = new ArrayList<String>();
        if (sourceDiscovery == null) {
            limitations.add("Source discovery was not available.");
            return limitations;
        }
        limitations.addAll(sourceDiscovery.limitations());
        if (sourceDiscovery.jiraIssue() != null) {
            limitations.addAll(sourceDiscovery.jiraIssue().limitations());
        }
        if (sourceDiscovery.mergeRequests() != null) {
            limitations.addAll(sourceDiscovery.mergeRequests().limitations());
        }
        return limitations.stream()
                .filter(java.util.Objects::nonNull)
                .filter(org.springframework.util.StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> instructionLimitations(ChangeVerificationSourceDiscoveryResult sourceDiscovery) {
        if (sourceDiscovery == null || sourceDiscovery.instructionContext() == null) {
            return List.of();
        }
        return sourceDiscovery.instructionContext().limitations().stream()
                .filter(java.util.Objects::nonNull)
                .filter(org.springframework.util.StringUtils::hasText)
                .distinct()
                .toList();
    }

    private ChangeVerificationComplianceResponse complianceResult() {
        if (!complianceRequested()) {
            return new ChangeVerificationComplianceResponse(
                    request.checkStoryCompliance(),
                    request.checkInstructionCompliance(),
                    "SKIPPED",
                    List.of(),
                    List.of(),
                    List.of("Compliance check was not requested for this skeleton job.")
            );
        }

        if (complianceAnalysis != null && complianceAnalysis.response() != null) {
            var response = complianceAnalysis.response();
            return new ChangeVerificationComplianceResponse(
                    request.checkStoryCompliance(),
                    request.checkInstructionCompliance(),
                    response.status(),
                    response.findings(),
                    response.suggestedActions(),
                    combinedComplianceVisibilityLimits(response.visibilityLimits())
            );
        }

        return new ChangeVerificationComplianceResponse(
                request.checkStoryCompliance(),
                request.checkInstructionCompliance(),
                "INCONCLUSIVE",
                List.of(new ChangeVerificationFindingResponse(
                        "cv-ai-not-run",
                        ChangeVerificationFindingSeverity.MEDIUM,
                        "platform",
                        "AI compliance check did not run.",
                        "Source context is available, but no AI compliance response was produced.",
                        List.of("change-verification/jira-issue", "change-verification/merge-requests"),
                        "Retry verification or inspect source evidence manually."
                )),
                List.of("Run AI compliance against Jira material, MR metadata and repository instructions again."),
                combinedComplianceVisibilityLimits()
        );
    }

    private ChangeVerificationSmokePackResponse smokePackResult() {
        if (!smokePackRequested()) {
            return new ChangeVerificationSmokePackResponse(
                    false,
                    "SKIPPED",
                    null,
                    List.of(),
                    List.of("Smoke pack generation was not requested."),
                    List.of(),
                    null
            );
        }
        if (smokePackAnalysis != null && smokePackAnalysis.response() != null) {
            return smokePackAnalysis.response();
        }
        return new ChangeVerificationSmokePackResponse(
                true,
                "INCONCLUSIVE",
                collectionName(),
                List.of(),
                List.of("Smoke pack generation did not produce a result."),
                List.of("Review changed endpoints manually and create smoke tests from acceptance criteria."),
                "low"
        );
    }

    private ChangeVerificationExecutionResponse executionResult() {
        return new ChangeVerificationExecutionResponse(
                executionRequested(),
                executionRequested() ? "WAITING_FOR_APPROVAL" : "SKIPPED",
                List.of(),
                List.of(),
                List.of(),
                null,
                executionRequested()
                        ? List.of("Smoke execution requires explicit run request with baseUrl.")
                        : List.of()
        );
    }

    private String instructionStepStatus() {
        if (!request.checkInstructionCompliance()) {
            return STATUS_SKIPPED;
        }
        if (instructionContext != null) {
            return STATUS_COMPLETED;
        }
        if (STEP_INSTRUCTION_CONTEXT.equals(currentStepCode)) {
            return STATUS_RUNNING;
        }
        if (sourceDiscovery == null) {
            return STATUS_PENDING;
        }
        return STATUS_COMPLETED;
    }

    private String jiraMaterialStepStatus() {
        if (jiraIssue != null) {
            return STATUS_COMPLETED;
        }
        if (STEP_JIRA_MATERIAL.equals(currentStepCode)) {
            return STATUS_RUNNING;
        }
        if (STATUS_FAILED.equals(status)) {
            return STATUS_FAILED;
        }
        return STATUS_PENDING;
    }

    private String mergeRequestDiscoveryStepStatus() {
        if (mergeRequests != null) {
            return STATUS_COMPLETED;
        }
        if (STEP_MERGE_REQUEST_DISCOVERY.equals(currentStepCode)) {
            return STATUS_RUNNING;
        }
        if (STATUS_FAILED.equals(status)) {
            return STATUS_FAILED;
        }
        return STATUS_PENDING;
    }

    private String changedFilesStepStatus() {
        if (mergeRequests != null) {
            return STATUS_COMPLETED;
        }
        if (STEP_CHANGED_FILES.equals(currentStepCode)) {
            return STATUS_RUNNING;
        }
        if (STATUS_FAILED.equals(status)) {
            return STATUS_FAILED;
        }
        return STATUS_PENDING;
    }

    private String initialSourceSnapshotStepStatus() {
        if (sourceDiscovery != null) {
            return STATUS_COMPLETED;
        }
        if (STEP_INITIAL_SOURCE_SNAPSHOT.equals(currentStepCode)) {
            return STATUS_RUNNING;
        }
        if (STATUS_FAILED.equals(status)) {
            return STATUS_FAILED;
        }
        return STATUS_PENDING;
    }

    private String aiVerificationStepStatus() {
        if (!complianceRequested()) {
            return STATUS_SKIPPED;
        }
        if (complianceAnalysis != null) {
            return STATUS_COMPLETED;
        }
        if (STEP_AI_VERIFICATION.equals(currentStepCode)) {
            return STATUS_RUNNING;
        }
        if (STATUS_FAILED.equals(status)) {
            return STATUS_FAILED;
        }
        return STATUS_PENDING;
    }

    private String smokePackGenerationStepStatus() {
        if (!smokePackRequested()) {
            return STATUS_SKIPPED;
        }
        if (smokePackAnalysis != null || smokePack != null) {
            return STATUS_COMPLETED;
        }
        if (STEP_SMOKE_PACK_GENERATION.equals(currentStepCode)) {
            return STATUS_RUNNING;
        }
        if (STATUS_FAILED.equals(status)) {
            return STATUS_FAILED;
        }
        return STATUS_PENDING;
    }

    private String nextCurrentStepAfterSourceDiscovery() {
        if (complianceRequested()) {
            return STEP_AI_VERIFICATION;
        }
        if (smokePackRequested()) {
            return STEP_SMOKE_PACK_GENERATION;
        }
        return null;
    }

    private String nextCurrentStepLabelAfterSourceDiscovery() {
        if (complianceRequested()) {
            return "AI verification";
        }
        if (smokePackRequested()) {
            return "Smoke pack generation";
        }
        return null;
    }

    private String instructionStepMessage() {
        if (!request.checkInstructionCompliance()) {
            return "Instruction compliance was not requested.";
        }
        var sourceCount = instructionContext != null
                ? instructionContext.sources().size()
                : 0;
        return "Instruction context discovered repository instruction sources: " + sourceCount + ".";
    }

    private String jiraMaterialStepMessage() {
        if (jiraIssue != null) {
            return "Jira material loaded for issue " + fallback(jiraIssue.issueKey(), resolvedIssueKey()) + ".";
        }
        if (STEP_JIRA_MATERIAL.equals(currentStepCode)) {
            return "Loading Jira issue material.";
        }
        return "Waiting for Jira issue material.";
    }

    private String mergeRequestDiscoveryStepMessage() {
        if (mergeRequests != null) {
            return "Merge request discovery found " + mergeRequests.mergeRequests().size() + " MR(s).";
        }
        if (STEP_MERGE_REQUEST_DISCOVERY.equals(currentStepCode)) {
            return "Searching GitLab merge requests for Jira key.";
        }
        return "Waiting for merge request discovery.";
    }

    private String changedFilesStepMessage() {
        if (mergeRequests != null) {
            return "Changed file discovery collected " + changedFileCount() + " file path(s).";
        }
        if (STEP_CHANGED_FILES.equals(currentStepCode)) {
            return "Collecting changed files from merge requests.";
        }
        return "Waiting for merge request changed files.";
    }

    private String initialSourceSnapshotStepMessage() {
        if (sourceDiscovery != null) {
            return "Source context and initial AI prompt are ready.";
        }
        if (STEP_INITIAL_SOURCE_SNAPSHOT.equals(currentStepCode)) {
            return "Preparing source context for AI.";
        }
        return "Waiting for deterministic source discovery.";
    }

    private boolean complianceRequested() {
        return request.modes().contains(ChangeVerificationJobMode.CHECK_COMPLIANCE);
    }

    private boolean smokePackRequested() {
        return request.modes().contains(ChangeVerificationJobMode.GENERATE_SMOKE_PACK)
                || request.modes().contains(ChangeVerificationJobMode.EXECUTE_SMOKE_PACK);
    }

    private boolean executionRequested() {
        return request.modes().contains(ChangeVerificationJobMode.EXECUTE_SMOKE_PACK);
    }

    private String collectionName() {
        var source = resolvedIssueKey() != null ? resolvedIssueKey() : "change-verification";
        return source + " smoke verification";
    }

    private String resolvedIssueKey() {
        if (sourceDiscovery != null && sourceDiscovery.issueKey() != null) {
            return sourceDiscovery.issueKey();
        }
        if (jiraIssue != null && jiraIssue.issueKey() != null) {
            return jiraIssue.issueKey();
        }
        return request.issueKey();
    }

    private String resolvedIssueUrl() {
        if (sourceDiscovery != null && sourceDiscovery.jiraIssue() != null) {
            return sourceDiscovery.jiraIssue().issueUrl();
        }
        if (jiraIssue != null && jiraIssue.issueUrl() != null) {
            return jiraIssue.issueUrl();
        }
        if (sourceDiscovery != null && sourceDiscovery.issueUrl() != null) {
            return sourceDiscovery.issueUrl();
        }
        return request.issueUrl();
    }

    private int contextItemCount() {
        return contextSections.stream().mapToInt(section -> section.items().size()).sum();
    }

    private Integer mergeRequestCount() {
        return mergeRequests != null ? mergeRequests.mergeRequests().size() : null;
    }

    private Integer changedFileCount() {
        if (mergeRequests == null) {
            return null;
        }
        return mergeRequests.mergeRequests().stream()
                .mapToInt(mergeRequest -> mergeRequest.changedFiles().size())
                .sum();
    }

    private List<AnalysisEvidenceReference> producedSourceEvidence() {
        return contextSections.stream()
                .map(section -> new AnalysisEvidenceReference(section.provider(), section.category()))
                .distinct()
                .toList();
    }

    private List<AnalysisEvidenceReference> producedInstructionEvidence() {
        if (!request.checkInstructionCompliance()) {
            return List.of();
        }
        var evidence = new ArrayList<AnalysisEvidenceReference>();
        if (sourceDiscovery != null && sourceDiscovery.instructionContext() != null
                && !sourceDiscovery.instructionContext().sources().isEmpty()) {
            evidence.add(INSTRUCTION_CONTEXT_EVIDENCE);
        }
        if (!instructionLimitations(sourceDiscovery).isEmpty()) {
            evidence.add(INSTRUCTION_LIMITS_EVIDENCE);
        }
        return evidence;
    }

    private int instructionItemCount() {
        if (sourceDiscovery == null || sourceDiscovery.instructionContext() == null) {
            return 0;
        }
        return sourceDiscovery.instructionContext().sources().size();
    }

    private List<String> combinedComplianceVisibilityLimits() {
        return combinedComplianceVisibilityLimits(List.of());
    }

    private List<String> combinedComplianceVisibilityLimits(List<String> aiVisibilityLimits) {
        var limitations = new ArrayList<String>();
        limitations.addAll(sourceLimitations(sourceDiscovery));
        limitations.addAll(instructionLimitations(sourceDiscovery));
        limitations.addAll(aiVisibilityLimits != null ? aiVisibilityLimits : List.of());
        return limitations.stream()
                .filter(java.util.Objects::nonNull)
                .filter(org.springframework.util.StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String aiVerificationMessage() {
        if (!complianceRequested()) {
            return "Compliance verification was not requested.";
        }
        if (complianceAnalysis != null && complianceAnalysis.response() != null) {
            return "AI compliance check completed with status " + complianceAnalysis.response().status() + ".";
        }
        return "AI compliance check did not produce a result.";
    }

    private Integer aiFindingCount() {
        if (complianceAnalysis == null || complianceAnalysis.response() == null) {
            return null;
        }
        return complianceAnalysis.response().findings().size();
    }

    private String smokePackStepMessage() {
        if (!smokePackRequested()) {
            return "Smoke pack generation was not requested.";
        }
        if (smokePack != null) {
            return "Smoke pack generated with status " + smokePack.status() + ".";
        }
        return "Smoke pack generation did not produce a result.";
    }

    private String executionStepStatus() {
        if (!executionRequested() && execution == null) {
            return "SKIPPED";
        }
        if (execution != null && !execution.testResults().isEmpty()) {
            return STATUS_COMPLETED;
        }
        return executionRequested() ? "WAITING_FOR_APPROVAL" : "SKIPPED";
    }

    private String executionStepMessage() {
        if (execution != null && !execution.testResults().isEmpty()) {
            return "Smoke execution completed with status " + execution.status() + ".";
        }
        if (executionRequested()) {
            return "Smoke execution is waiting for explicit run request with baseUrl.";
        }
        return "Smoke execution was not requested.";
    }

    private String preparedPrompt(
            ChangeVerificationComplianceAnalysis complianceAnalysis,
            ChangeVerificationSmokePackAnalysis smokePackAnalysis
    ) {
        if (complianceAnalysis != null && complianceAnalysis.prompt() != null) {
            return complianceAnalysis.prompt();
        }
        if (smokePackAnalysis != null && smokePackAnalysis.prompt() != null) {
            return smokePackAnalysis.prompt();
        }
        if (org.springframework.util.StringUtils.hasText(preparedPrompt)) {
            return preparedPrompt;
        }
        return "Change Verification prompt was not prepared.";
    }

    private String fallback(String primary, String secondary) {
        return org.springframework.util.StringUtils.hasText(primary) ? primary : secondary;
    }

    private static AnalysisEvidenceAttribute attribute(String name, Object value) {
        return new AnalysisEvidenceAttribute(name, value != null ? String.valueOf(value) : "");
    }
}
