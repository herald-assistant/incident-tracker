package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.state;

import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAssessmentScore;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryAssessmentIssueResponse;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryAssessmentMergeRequestResponse;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryAssessmentResponse;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryAssessmentUnitResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

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
    private AnalysisAiUsage usage;
    private AnalysisReport report;

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

    void completed(DeliveryAssessmentScore score, AnalysisAiUsage usage, AnalysisReport report) {
        if (terminal()) {
            return;
        }
        status = "COMPLETED";
        this.score = score;
        this.usage = usage;
        this.report = report;
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
        excluded(textList(limitation), null, null);
    }

    void excluded(List<String> limitations, AnalysisAiUsage usage, AnalysisReport report) {
        if (terminal()) {
            return;
        }
        addVisibilityLimits(limitations);
        status = "EXCLUDED";
        this.usage = usage;
        this.report = report;
        completedAt = Instant.now();
    }

    void notScorable(String limitation) {
        notScorable(textList(limitation), null, null);
    }

    void notScorable(List<String> limitations, AnalysisAiUsage usage, AnalysisReport report) {
        if (terminal()) {
            return;
        }
        addVisibilityLimits(limitations);
        status = "NOT_SCORABLE";
        this.usage = usage;
        this.report = report;
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
                        issue.doneAt()
                ))
                .toList();
        var mergeRequests = unit.mergeRequests().stream()
                .map(mergeRequest -> new DeliveryAssessmentMergeRequestResponse(
                        pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnitBuilder.identity(mergeRequest),
                        mergeRequest.projectPath(),
                        mergeRequest.iid(),
                        mergeRequest.title(),
                        mergeRequest.webUrl(),
                        mergeRequest.mergedAt(),
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
                usage,
                report
        );
    }
}
