package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerClaimConfidence;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultSection;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobRequestSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailability;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerOutputAvailabilityStatus;
import pl.mkn.tdw.features.uiexplorer.report.DefaultUiExplorerResultReportAssembler;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

final class UiExplorerLocalRunTestFixture {

    static final Instant CREATED_AT = Instant.parse("2026-08-15T10:00:00Z");
    static final Instant UPDATED_AT = Instant.parse("2026-08-15T10:05:00Z");
    static final Instant COMPLETED_AT = Instant.parse("2026-08-15T10:06:00Z");
    static final String JOB_ID = "crm-ui-history-job-1";
    static final String SOURCE_PATH =
            "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts";
    static final String PREPARED_PROMPT = "# Synthetic CRM canonical UI Explorer prompt";

    private UiExplorerLocalRunTestFixture() {
    }

    static UiExplorerJobStateSnapshot snapshot(UiExplorerJobStatus status) {
        var hasOutput = status == UiExplorerJobStatus.COMPLETED || status == UiExplorerJobStatus.PARTIAL;
        var result = hasOutput ? result() : null;
        var report = hasOutput
                ? new DefaultUiExplorerResultReportAssembler().assemble("crm-ui-history-report-1", result).report()
                : null;
        var terminal = status == UiExplorerJobStatus.COMPLETED
                || status == UiExplorerJobStatus.PARTIAL
                || status == UiExplorerJobStatus.BLOCKED
                || status == UiExplorerJobStatus.FAILED;
        return new UiExplorerJobStateSnapshot(
                JOB_ID + "-" + status.name().toLowerCase(),
                new UiExplorerJobRequestSnapshot(
                        request().systemId(),
                        context().systemLabel(),
                        request().branch(),
                        request().screenId(),
                        request().sourceRevision(),
                        request().resolvedSectionModes(),
                        request().scenarioDescription(),
                        request().model(),
                        request().reasoningEffort()
                ),
                status,
                terminal ? null : "AI_ANALYSIS",
                terminal ? null : "Analyze the selected screen",
                status == UiExplorerJobStatus.FAILED ? "UI_EXPLORER_ANALYSIS_FAILED" : null,
                status == UiExplorerJobStatus.FAILED ? "Synthetic CRM analysis failed." : null,
                CREATED_AT,
                UPDATED_AT,
                terminal ? COMPLETED_AT : null,
                List.of(),
                List.of(new AnalysisEvidenceSection(
                        "ui-explorer",
                        "source-manifest",
                        List.of(new AnalysisEvidenceItem(
                                "Synthetic CRM component",
                                List.of(new AnalysisEvidenceAttribute("filePath", SOURCE_PATH))
                        ))
                )),
                List.of(rawToolEvidence()),
                List.of(rawActivity()),
                List.of(new AnalysisAiToolFeedback(
                        "crm-feedback-1",
                        "gitlab_read_repository_file",
                        "crm-tool-call-1",
                        "crm-feedback-call-1",
                        "PARTIAL",
                        "NO",
                        "SCOPE",
                        "CRM_HIDDEN_FEEDBACK_SECRET",
                        "HIGH",
                        "Synthetic CRM feedback.",
                        "Keep the CRM fixture bounded.",
                        UPDATED_AT
                )),
                PREPARED_PROMPT,
                result,
                report,
                hasOutput ? usage() : null,
                context().sourceRevision(),
                new UiExplorerOutputAvailability(
                        hasOutput ? UiExplorerOutputAvailabilityStatus.AVAILABLE
                                : UiExplorerOutputAvailabilityStatus.BLOCKED,
                        hasOutput ? "UI_EXPLORER_OUTPUT_AVAILABLE" : "UI_EXPLORER_ANALYSIS_BLOCKED",
                        hasOutput ? "Synthetic CRM output is available." : "Synthetic CRM output is blocked.",
                        hasOutput ? List.of() : List.of("AI_ANALYSIS")
                ),
                false
        );
    }

    static UiExplorerResultResponse result() {
        var hiddenRepository = "confidential-crm-group/internal-crm-ui-project";
        var reference = new UiExplorerSourceReference(
                hiddenRepository,
                SOURCE_PATH,
                "CrmContactPreferencesComponent",
                10,
                18
        );
        return new UiExplorerResultResponse(
                context().screen(),
                request().scenarioDescription(),
                context().sourceRevision(),
                "Doradca CRM utrzymuje dozwolony kanal kontaktu dla wybranego klienta.",
                List.of(new UiExplorerResultSection(
                        UiExplorerSectionId.OVERVIEW,
                        UiExplorerSectionMode.COMPACT,
                        UiExplorerCoverageStatus.READY,
                        UiExplorerClaimConfidence.CONFIRMED,
                        """
                                **Cel biznesowy**

                                Utrzymanie dozwolonego kanalu kontaktu.

                                **Uzytkownicy i kontekst**

                                Doradca pracuje na jednym wybranym kontakcie CRM.

                                **Przebieg w skrocie**

                                1. Doradca otwiera preferencje kontaktu.
                                2. System przypisuje zmiane do wybranego kontaktu.

                                **Rezultat**

                                Aktualna preferencja moze byc wykorzystana w kolejnej interakcji.
                                """.trim(),
                        List.of(),
                        List.of(reference),
                        context().visibilityLimits(),
                        List.of()
                )),
                List.of(),
                UiExplorerClaimConfidence.CONFIRMED,
                context().visibilityLimits(),
                List.of(),
                usage()
        );
    }

    private static AnalysisEvidenceSection rawToolEvidence() {
        return new AnalysisEvidenceSection(
                "gitlab",
                "tool-fetched-code",
                List.of(new AnalysisEvidenceItem(
                        "internal-crm-ui-project:" + SOURCE_PATH,
                        List.of(
                                new AnalysisEvidenceAttribute("filePath", SOURCE_PATH),
                                new AnalysisEvidenceAttribute("reason", "Verify synthetic CRM validation."),
                                new AnalysisEvidenceAttribute("toolCallId", "crm-tool-call-1"),
                                new AnalysisEvidenceAttribute("toolName", "gitlab_read_repository_file"),
                                new AnalysisEvidenceAttribute("content", "CRM_RAW_SOURCE_SECRET"),
                                new AnalysisEvidenceAttribute("toolArguments", "CRM_HIDDEN_SCOPE_SECRET"),
                                new AnalysisEvidenceAttribute("group", "confidential-crm-group"),
                                new AnalysisEvidenceAttribute("projectName", "internal-crm-ui-project"),
                                new AnalysisEvidenceAttribute("branch", "main")
                        )
                ))
        );
    }

    private static AnalysisAiActivityEvent rawActivity() {
        return new AnalysisAiActivityEvent(
                "crm-activity-1",
                null,
                "tool",
                "source",
                "COMPLETED",
                "Read synthetic CRM source",
                "A bounded CRM source file was read.",
                "crm-turn-1",
                "crm-interaction-1",
                "crm-tool-call-1",
                "gitlab_read_repository_file",
                UPDATED_AT,
                Map.of("rawArguments", "CRM_HIDDEN_ACTIVITY_SECRET")
        );
    }

    private static AnalysisAiUsage usage() {
        return new AnalysisAiUsage(
                100, 50, 10, 0, 160, 0.01, 200, 1,
                "gpt-5.4", 200_000L, 1_000L, 4L
        );
    }
}
