package pl.mkn.tdw.features.flowexplorer.job.export;

import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FlowExplorerExportEnvelope(
        String schema,
        int version,
        Instant exportedAt,
        Payload payload
) {
    public static final String SCHEMA = "tdw.flow-explorer-export";
    public static final int VERSION = 2;
    public static final String PAYLOAD_TYPE = "flow-explorer-analysis";
    public static final String RESULT_CONTRACT = "flow-explorer-goal-result-v1";

    public static FlowExplorerExportEnvelope from(FlowExplorerJobStateSnapshot snapshot, Instant exportedAt) {
        return new FlowExplorerExportEnvelope(
                SCHEMA,
                VERSION,
                exportedAt != null ? exportedAt : Instant.now(),
                new Payload(
                        PAYLOAD_TYPE,
                        RESULT_CONTRACT,
                        FlowExplorerExportDiagnostics.from(snapshot),
                        snapshot
                )
        );
    }

    public record Payload(
            String type,
            String resultContract,
            FlowExplorerExportDiagnostics diagnostics,
            FlowExplorerJobStateSnapshot job
    ) {
    }

    public record FlowExplorerExportDiagnostics(
            String resultContract,
            Target target,
            Request request,
            Result result,
            Context context,
            Workflow workflow,
            List<DiagnosticArtifactSummary> artifacts,
            String resultMarkdown
    ) {

        static FlowExplorerExportDiagnostics from(FlowExplorerJobStateSnapshot snapshot) {
            var resultMarkdown = renderResultMarkdown(snapshot);
            return new FlowExplorerExportDiagnostics(
                    RESULT_CONTRACT,
                    FlowExplorerExportEnvelope.target(snapshot),
                    FlowExplorerExportEnvelope.request(snapshot),
                    FlowExplorerExportEnvelope.result(snapshot),
                    FlowExplorerExportEnvelope.context(snapshot),
                    FlowExplorerExportEnvelope.workflow(snapshot),
                    FlowExplorerExportEnvelope.artifacts(snapshot, resultMarkdown),
                    resultMarkdown
            );
        }
    }

    public record Target(
            String systemId,
            String endpointId,
            String httpMethod,
            String endpointPath,
            String branch
    ) {
    }

    public record Request(
            FlowExplorerAnalysisGoal goal,
            List<FlowExplorerFocusArea> focusAreas,
            List<FlowExplorerResultSectionModeAssignment> sectionModes,
            String aiModel,
            String reasoningEffort
    ) {
    }

    public record Result(
            FlowExplorerAnalysisGoal goal,
            String confidence,
            Map<String, String> sectionModes,
            int sourceReferenceCount,
            int visibilityLimitCount,
            int openQuestionCount,
            int followUpPromptCount
    ) {
    }

    public record Context(
            boolean contextSnapshotIncluded,
            int repositoryCount,
            int flowNodeCount,
            int relationCount,
            int snippetCardCount,
            int snippetCharacterCount,
            boolean snippetBudgetReached,
            List<String> clippingNotes,
            int limitationCount
    ) {
    }

    public record Workflow(
            int stepCount,
            int contextEvidenceItemCount,
            int toolEvidenceItemCount,
            int aiActivityEventCount,
            int toolFeedbackCount,
            int chatMessageCount,
            boolean usageIncluded
    ) {
    }

    public record DiagnosticArtifactSummary(
            String name,
            String kind,
            boolean included,
            Integer itemCount,
            Integer characterCount
    ) {
    }

    private static Target target(FlowExplorerJobStateSnapshot snapshot) {
        return new Target(
                text(snapshot != null ? snapshot.systemId() : null),
                text(snapshot != null ? snapshot.endpointId() : null),
                text(snapshot != null ? snapshot.httpMethod() : null),
                text(snapshot != null ? snapshot.endpointPath() : null),
                text(snapshot != null ? snapshot.branch() : null)
        );
    }

    private static Request request(FlowExplorerJobStateSnapshot snapshot) {
        return new Request(
                snapshot != null ? snapshot.goal() : null,
                snapshot != null ? safeList(snapshot.focusAreas()) : List.of(),
                snapshot != null ? safeList(snapshot.sectionModes()) : List.of(),
                text(snapshot != null ? snapshot.aiModel() : null),
                text(snapshot != null ? snapshot.reasoningEffort() : null)
        );
    }

    private static Result result(FlowExplorerJobStateSnapshot snapshot) {
        var aiResponse = aiResponse(snapshot);
        return new Result(
                aiResponse != null ? aiResponse.goal() : (snapshot != null ? snapshot.goal() : null),
                confidence(aiResponse),
                sectionModes(aiResponse),
                aiResponse != null ? safeList(aiResponse.sourceReferences()).size() : 0,
                visibilityLimitCount(aiResponse),
                openQuestionCount(aiResponse),
                aiResponse != null ? safeList(aiResponse.followUpPrompts()).size() : 0
        );
    }

    private static Context context(FlowExplorerJobStateSnapshot snapshot) {
        var contextSnapshot = snapshot != null ? snapshot.contextSnapshot() : null;
        var coverage = contextSnapshot != null ? contextSnapshot.coverage() : null;
        return new Context(
                contextSnapshot != null,
                contextSnapshot != null ? safeList(contextSnapshot.repositories()).size() : 0,
                countOrCoverage(coverage != null ? coverage.flowNodeCount() : 0,
                        contextSnapshot != null ? safeList(contextSnapshot.flowNodes()).size() : 0),
                countOrCoverage(coverage != null ? coverage.relationCount() : 0,
                        contextSnapshot != null ? safeList(contextSnapshot.relations()).size() : 0),
                countOrCoverage(coverage != null ? coverage.snippetCardCount() : 0,
                        contextSnapshot != null ? safeList(contextSnapshot.snippetCards()).size() : 0),
                coverage != null ? coverage.snippetCharacterCount() : 0,
                coverage != null && coverage.snippetBudgetReached(),
                clippingNotes(contextSnapshot, coverage),
                contextSnapshot != null ? safeList(contextSnapshot.limitations()).size() : 0
        );
    }

    private static Workflow workflow(FlowExplorerJobStateSnapshot snapshot) {
        return new Workflow(
                snapshot != null ? safeList(snapshot.steps()).size() : 0,
                snapshot != null ? evidenceItemCount(snapshot.contextSections()) : 0,
                snapshot != null ? evidenceItemCount(snapshot.toolEvidenceSections()) : 0,
                snapshot != null ? safeList(snapshot.aiActivityEvents()).size() : 0,
                snapshot != null ? safeList(snapshot.toolFeedback()).size() : 0,
                snapshot != null ? safeList(snapshot.chatMessages()).size() : 0,
                snapshot != null && snapshot.result() != null && snapshot.result().usage() != null
        );
    }

    private static List<DiagnosticArtifactSummary> artifacts(
            FlowExplorerJobStateSnapshot snapshot,
            String resultMarkdown
    ) {
        var contextSnapshot = snapshot != null ? snapshot.contextSnapshot() : null;
        var snippetCharacterCount = contextSnapshot != null && contextSnapshot.coverage() != null
                ? contextSnapshot.coverage().snippetCharacterCount()
                : 0;
        var aiResponse = aiResponse(snapshot);
        return List.of(
                new DiagnosticArtifactSummary(
                        "flow-explorer-result.md",
                        "user-facing-markdown",
                        !resultMarkdown.isBlank(),
                        aiResponse != null ? 1 + safeList(aiResponse.sections()).size() : 0,
                        resultMarkdown.length()
                ),
                new DiagnosticArtifactSummary("jobSnapshot", "diagnostic-json", snapshot != null, 1, null),
                new DiagnosticArtifactSummary(
                        "contextSnapshot",
                        "deterministic-context-json",
                        contextSnapshot != null,
                        contextSnapshot != null ? safeList(contextSnapshot.flowNodes()).size() : 0,
                        snippetCharacterCount > 0 ? snippetCharacterCount : null
                ),
                new DiagnosticArtifactSummary(
                        "preparedPrompt",
                        "canonical-prompt",
                        snapshot != null && hasText(snapshot.preparedPrompt()),
                        null,
                        snapshot != null && hasText(snapshot.preparedPrompt()) ? snapshot.preparedPrompt().length() : null
                ),
                new DiagnosticArtifactSummary(
                        "contextSections",
                        "workflow-evidence",
                        snapshot != null && !safeList(snapshot.contextSections()).isEmpty(),
                        snapshot != null ? evidenceItemCount(snapshot.contextSections()) : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "toolEvidenceSections",
                        "tool-evidence",
                        snapshot != null && !safeList(snapshot.toolEvidenceSections()).isEmpty(),
                        snapshot != null ? evidenceItemCount(snapshot.toolEvidenceSections()) : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "aiActivityEvents",
                        "ai-activity",
                        snapshot != null && !safeList(snapshot.aiActivityEvents()).isEmpty(),
                        snapshot != null ? safeList(snapshot.aiActivityEvents()).size() : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "toolFeedback",
                        "tool-quality-feedback",
                        snapshot != null && !safeList(snapshot.toolFeedback()).isEmpty(),
                        snapshot != null ? safeList(snapshot.toolFeedback()).size() : 0,
                        null
                ),
                new DiagnosticArtifactSummary(
                        "usage",
                        "token-and-cost-usage",
                        snapshot != null && snapshot.result() != null && snapshot.result().usage() != null,
                        snapshot != null && snapshot.result() != null && snapshot.result().usage() != null ? 1 : 0,
                        null
                )
        );
    }

    private static String renderResultMarkdown(FlowExplorerJobStateSnapshot snapshot) {
        var result = snapshot != null ? snapshot.result() : null;
        var aiResponse = aiResponse(snapshot);
        if (result == null || aiResponse == null) {
            return "";
        }

        var sections = safeList(aiResponse.sections()).stream()
                .filter(java.util.Objects::nonNull)
                .map(section -> String.join(
                        "\n\n",
                        "## " + text(section.title()),
                        "Mode: " + section.mode(),
                        text(section.markdown()),
                        listMarkdown("Source refs", section.sourceRefs()),
                        listMarkdown("Visibility limits", section.visibilityLimits()),
                        listMarkdown("Open questions", section.openQuestions())
                ).trim())
                .filter(FlowExplorerExportEnvelope::hasText)
                .toList();

        return String.join(
                "\n\n",
                "# Flow Explorer result",
                "Target: " + text(result.httpMethod()) + " " + text(result.endpointPath()),
                "System: " + text(result.systemId()),
                "Goal: " + result.goal(),
                "Confidence: " + confidence(aiResponse),
                "## Overview",
                text(aiResponse.overview() != null ? aiResponse.overview().markdown() : null),
                listMarkdown("Source refs", aiResponse.overview() != null ? aiResponse.overview().sourceRefs() : List.of()),
                String.join("\n\n", sections),
                listMarkdown("Recommended follow-up prompts", aiResponse.followUpPrompts()),
                listMarkdown("Global visibility limits", aiResponse.globalVisibilityLimits()),
                listMarkdown("Global open questions", aiResponse.globalOpenQuestions()),
                listMarkdown("Source refs", aiResponse.sourceReferences())
        ).trim();
    }

    private static String listMarkdown(String title, List<String> values) {
        var normalized = safeList(values).stream()
                .filter(FlowExplorerExportEnvelope::hasText)
                .toList();
        if (normalized.isEmpty()) {
            return "";
        }

        return "### " + title + "\n" + String.join("\n", normalized.stream()
                .map(value -> "- " + value)
                .toList());
    }

    private static FlowExplorerAiResponse aiResponse(FlowExplorerJobStateSnapshot snapshot) {
        return snapshot != null && snapshot.result() != null ? snapshot.result().aiResponse() : null;
    }

    private static String confidence(FlowExplorerAiResponse aiResponse) {
        if (aiResponse == null) {
            return "";
        }
        if (hasText(aiResponse.confidence())) {
            return aiResponse.confidence();
        }
        return aiResponse.overview() != null ? text(aiResponse.overview().confidence()) : "";
    }

    private static Map<String, String> sectionModes(FlowExplorerAiResponse aiResponse) {
        var modes = new LinkedHashMap<String, String>();
        if (aiResponse == null) {
            return modes;
        }
        for (var section : safeList(aiResponse.sections())) {
            if (section != null && section.id() != null && section.mode() != null) {
                modes.put(section.id().name(), section.mode().name().toLowerCase());
            }
        }
        return modes;
    }

    private static int visibilityLimitCount(FlowExplorerAiResponse aiResponse) {
        if (aiResponse == null) {
            return 0;
        }
        return safeList(aiResponse.globalVisibilityLimits()).size()
                + safeList(aiResponse.sections()).stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(section -> safeList(section.visibilityLimits()).size())
                .sum();
    }

    private static int openQuestionCount(FlowExplorerAiResponse aiResponse) {
        if (aiResponse == null) {
            return 0;
        }
        return safeList(aiResponse.globalOpenQuestions()).size()
                + safeList(aiResponse.sections()).stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(section -> safeList(section.openQuestions()).size())
                .sum();
    }

    private static List<String> clippingNotes(
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerContextCoverage coverage
    ) {
        var notes = new java.util.ArrayList<String>();
        if (coverage != null) {
            if (coverage.snippetBudgetReached()) {
                notes.add("Snippet budget reached.");
            }
            if (coverage.maxDepthReached()) {
                notes.add("Max flow depth reached.");
            }
            if (coverage.maxFilesReached()) {
                notes.add("Max file scan limit reached.");
            }
            if (coverage.readFileLimitReached()) {
                notes.add("Read file limit reached.");
            }
        }
        if (contextSnapshot != null) {
            notes.addAll(safeList(contextSnapshot.limitations()));
        }
        return notes.isEmpty() ? List.of("No clipping reported by deterministic context.") : List.copyOf(notes);
    }

    private static int evidenceItemCount(List<AnalysisEvidenceSection> sections) {
        return safeList(sections).stream()
                .mapToInt(section -> safeList(section.items()).size())
                .sum();
    }

    private static int countOrCoverage(int coverageCount, int fallbackCount) {
        return coverageCount > 0 ? coverageCount : fallbackCount;
    }

    private static String text(String value) {
        return value != null ? value : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }
}
