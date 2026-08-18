package pl.mkn.tdw.features.deliverycomplexityassessment.job.state;

import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryAssessmentScore;
import pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryAssessmentIssueResponse;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryAssessmentMergeRequestResponse;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryAssessmentResponse;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryAssessmentTeamResponse;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryAssessmentUnitResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class DeliveryAssessmentUnitState {

    private final DeliveryUnit unit;
    private String status = "PENDING";
    private DeliveryAssessmentScore score;
    private final LinkedHashSet<String> visibilityLimits = new LinkedHashSet<>();
    private String errorCode;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private String preparedPrompt;
    private Instant promptPreparedAt;
    private AnalysisAiUsage usage;

    DeliveryAssessmentUnitState(DeliveryUnit unit) {
        this.unit = unit;
        this.visibilityLimits.addAll(unit.limitations());
    }

    DeliveryUnit unit() {
        return unit;
    }

    void collecting() {
        if (terminal()) {
            return;
        }
        status = "COLLECTING_EVIDENCE";
        startedAt = startedAt != null ? startedAt : Instant.now();
    }

    void analyzing() {
        if (terminal()) {
            return;
        }
        status = "ANALYZING";
        startedAt = startedAt != null ? startedAt : Instant.now();
    }

    void preparedPrompt(String prompt) {
        if (terminal() || prompt == null || prompt.isBlank()) {
            return;
        }
        preparedPrompt = prompt;
        promptPreparedAt = Instant.now();
    }

    void completed(DeliveryAssessmentScore score, AnalysisAiUsage usage) {
        if (terminal()) {
            return;
        }
        status = "COMPLETED";
        this.score = score;
        this.usage = usage;
        visibilityLimits.addAll(score.visibilityLimits());
        completedAt = Instant.now();
    }

    void addVisibilityLimits(List<String> limitations) {
        if (terminal() || limitations == null) {
            return;
        }
        limitations.stream()
                .filter(limit -> limit != null && !limit.isBlank())
                .forEach(visibilityLimits::add);
    }

    void excluded(String limitation) {
        excluded(textList(limitation), null);
    }

    void excluded(List<String> limitations, AnalysisAiUsage usage) {
        if (terminal()) {
            return;
        }
        addVisibilityLimits(limitations);
        status = "EXCLUDED";
        this.usage = usage;
        completedAt = Instant.now();
    }

    void notScorable(String limitation) {
        notScorable(textList(limitation), null);
    }

    void notScorable(List<String> limitations, AnalysisAiUsage usage) {
        if (terminal()) {
            return;
        }
        addVisibilityLimits(limitations);
        status = "NOT_SCORABLE";
        this.usage = usage;
        completedAt = Instant.now();
    }

    void failed(String code, String message) {
        if (terminal()) {
            return;
        }
        status = "FAILED";
        errorCode = code;
        errorMessage = message;
        completedAt = Instant.now();
    }

    boolean terminal() {
        return switch (status) {
            case "COMPLETED", "EXCLUDED", "NOT_SCORABLE", "FAILED" -> true;
            default -> false;
        };
    }

    String status() {
        return status;
    }

    DeliveryAssessmentScore score() {
        return score;
    }

    AnalysisAiUsage usage() {
        return usage;
    }

    private List<String> textList(String value) {
        return value != null && !value.isBlank() ? List.of(value) : List.of();
    }

    DeliveryAssessmentUnitResponse snapshot() {
        var issues = unit.issues().stream()
                .map(issue -> new DeliveryAssessmentIssueResponse(
                        issue.issueKey(),
                        issue.material().issueUrl(),
                        issue.material().summary(),
                        issue.material().issueType(),
                        issue.doneAt(),
                        issue.team() != null
                                ? new DeliveryAssessmentTeamResponse(
                                issue.team().id(),
                                issue.team().name(),
                                issue.team().fieldId()
                        )
                                : null
                ))
                .toList();
        var mergeRequests = unit.mergeRequests().stream()
                .map(mergeRequest -> new DeliveryAssessmentMergeRequestResponse(
                        pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnitBuilder.identity(mergeRequest),
                        mergeRequest.projectPath(),
                        mergeRequest.iid(),
                        mergeRequest.title(),
                        mergeRequest.webUrl(),
                        mergeRequest.mergedAt(),
                        mergeRequest.authorId(),
                        mergeRequest.authorName(),
                        mergeRequest.changedFiles().stream()
                                .map(file -> file.newPath() != null && !file.newPath().isBlank()
                                        ? file.newPath()
                                        : file.oldPath())
                                .toList()
                ))
                .toList();
        return new DeliveryAssessmentUnitResponse(
                unit.unitId(),
                status,
                issues,
                mergeRequests,
                score != null ? new DeliveryAssessmentResponse(
                        score.deliveredStoryPoints(),
                        score.score100(),
                        score.dimensions(),
                        score.confidence(),
                        score.evidenceSummary(),
                        score.qualityFlags()
                ) : null,
                new ArrayList<>(visibilityLimits),
                errorCode,
                errorMessage,
                startedAt,
                completedAt,
                preparedPrompt,
                promptPreparedAt,
                usage
        );
    }
}
