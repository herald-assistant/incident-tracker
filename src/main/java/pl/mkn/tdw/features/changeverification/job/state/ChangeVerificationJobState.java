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
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationSmokePackAnalysis;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestCommit;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;
import pl.mkn.tdw.integrations.jira.JiraIssueComment;
import pl.mkn.tdw.integrations.jira.JiraIssueLink;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ChangeVerificationJobState {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STEP_SOURCE_DISCOVERY = "SOURCE_DISCOVERY";
    private static final String STEP_INSTRUCTION_CONTEXT = "INSTRUCTION_CONTEXT";
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
    private String preparedPrompt;
    private ChangeVerificationResultResponse result;
    private List<AnalysisJobStepResponse> steps;
    private List<AnalysisEvidenceSection> contextSections;
    private ChangeVerificationSourceDiscoveryResult sourceDiscovery;
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
        this.steps = List.of();
        this.contextSections = List.of();
    }

    public synchronized void markSourceDiscoveryCompleted(
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis,
            ChangeVerificationSmokePackAnalysis smokePackAnalysis
    ) {
        var now = Instant.now();
        this.sourceDiscovery = sourceDiscovery;
        this.complianceAnalysis = complianceAnalysis;
        this.smokePackAnalysis = smokePackAnalysis;
        status = STATUS_COMPLETED;
        updatedAt = now;
        completedAt = now;
        preparedPrompt = preparedPrompt(complianceAnalysis, smokePackAnalysis);
        contextSections = contextSections(sourceDiscovery);
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
                null,
                null,
                null,
                null,
                createdAt,
                updatedAt,
                completedAt,
                steps,
                contextSections,
                preparedPrompt,
                result
        );
    }

    private List<AnalysisJobStepResponse> steps(Instant completedAt) {
        var startedAt = createdAt;
        var steps = new ArrayList<AnalysisJobStepResponse>();
        steps.add(step(
                STEP_SOURCE_DISCOVERY,
                "Source discovery",
                PHASE_CONTEXT,
                STATUS_COMPLETED,
                sourceDiscoveryMessage(),
                contextItemCount(),
                startedAt,
                completedAt,
                List.of(),
                producedSourceEvidence()
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
                STEP_AI_VERIFICATION,
                "AI verification",
                PHASE_AI,
                complianceRequested() ? STATUS_COMPLETED : "SKIPPED",
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
                smokePackRequested() ? STATUS_COMPLETED : "SKIPPED",
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
        return request.checkInstructionCompliance() ? STATUS_COMPLETED : "SKIPPED";
    }

    private String instructionStepMessage() {
        if (!request.checkInstructionCompliance()) {
            return "Instruction compliance was not requested.";
        }
        var sourceCount = sourceDiscovery != null && sourceDiscovery.instructionContext() != null
                ? sourceDiscovery.instructionContext().sources().size()
                : 0;
        return "Instruction context discovered repository instruction sources: " + sourceCount + ".";
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
        return request.issueKey();
    }

    private String resolvedIssueUrl() {
        if (sourceDiscovery != null && sourceDiscovery.jiraIssue() != null) {
            return sourceDiscovery.jiraIssue().issueUrl();
        }
        if (sourceDiscovery != null && sourceDiscovery.issueUrl() != null) {
            return sourceDiscovery.issueUrl();
        }
        return request.issueUrl();
    }

    private String sourceDiscoveryMessage() {
        var jiraFound = sourceDiscovery != null && sourceDiscovery.jiraIssue() != null;
        var mrCount = sourceDiscovery != null && sourceDiscovery.mergeRequests() != null
                ? sourceDiscovery.mergeRequests().mergeRequests().size()
                : 0;
        return "Source discovery collected Jira material: %s, GitLab merge requests: %d."
                .formatted(jiraFound ? "yes" : "no", mrCount);
    }

    private int contextItemCount() {
        return contextSections.stream().mapToInt(section -> section.items().size()).sum();
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
        return "Change Verification prompt was not prepared.";
    }

    private String fallback(String primary, String secondary) {
        return org.springframework.util.StringUtils.hasText(primary) ? primary : secondary;
    }

    private static AnalysisEvidenceAttribute attribute(String name, Object value) {
        return new AnalysisEvidenceAttribute(name, value != null ? String.valueOf(value) : "");
    }
}
