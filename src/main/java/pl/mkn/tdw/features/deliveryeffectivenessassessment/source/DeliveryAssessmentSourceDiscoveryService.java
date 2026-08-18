package pl.mkn.tdw.features.deliveryeffectivenessassessment.source;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStartRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.jira.JiraIssueCustomField;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterialRequest;
import pl.mkn.tdw.integrations.jira.JiraIssuePort;
import pl.mkn.tdw.integrations.jira.JiraIssueSearchItem;
import pl.mkn.tdw.integrations.jira.JiraIssueSearchPort;
import pl.mkn.tdw.integrations.jira.JiraIssueSearchRequest;
import pl.mkn.tdw.integrations.jira.JiraIssueStatusHistoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveryAssessmentSourceDiscoveryService {

    private final JiraIssueSearchPort jiraIssueSearchPort;
    private final JiraIssuePort jiraIssuePort;
    private final JiraIssueStatusHistoryPort jiraIssueStatusHistoryPort;
    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final GitLabProperties gitLabProperties;
    private final DeliveryEffectivenessAssessmentProperties properties;
    private final DeliveryAssessmentSourceExecutor sourceExecutor;

    public DeliveryAssessmentSourceResult discover(
            DeliveryEffectivenessAssessmentJobStartRequest request,
            DeliveryAssessmentSourceListener listener
    ) {
        validateRange(request);
        var search = jiraIssueSearchPort.searchIssues(new JiraIssueSearchRequest(
                request.jiraProject(),
                properties.getJiraDoneStatusId(),
                request.fromDate(),
                request.toDate().plusDays(1),
                properties.getJiraPageSize(),
                properties.getMaxIssuesPerJob()
        ));
        listener.onSearchCompleted(search.issues().size(), search.total(), search.effectiveJql());

        var futures = new ArrayList<CompletableFuture<IssueDiscoveryOutcome>>();
        var processed = new AtomicInteger();
        var limitations = new ArrayList<>(search.limitations());
        for (var index = 0; index < search.issues().size(); index++) {
            var candidate = search.issues().get(index);
            futures.add(discoverIssueAsync(candidate, request)
                    .whenComplete((ignored, ignoredFailure) -> listener.onIssueProcessed(
                            processed.incrementAndGet(),
                            search.issues().size(),
                            candidate.issueKey()
                    )));
        }

        var issues = new ArrayList<DeliveryAssessmentIssueSource>();
        for (var future : futures) {
            var outcome = future.join();
            if (outcome.issue() != null) {
                issues.add(outcome.issue());
            }
            limitations.addAll(outcome.limitations());
        }
        return new DeliveryAssessmentSourceResult(
                search.effectiveJql(),
                search.total(),
                search.truncated(),
                issues,
                limitations
        );
    }

    private CompletableFuture<IssueDiscoveryOutcome> discoverIssueAsync(
            JiraIssueSearchItem candidate,
            DeliveryEffectivenessAssessmentJobStartRequest request
    ) {
        try {
            return sourceExecutor.supplyAsync(() -> discoverIssue(candidate, request))
                    .handle((outcome, failure) -> failure == null
                            ? outcome
                            : failedOutcome(candidate.issueKey(), failure));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(failedOutcome(candidate.issueKey(), exception));
        }
    }

    private IssueDiscoveryOutcome discoverIssue(
            JiraIssueSearchItem candidate,
            DeliveryEffectivenessAssessmentJobStartRequest request
    ) {
        var issueLimitations = new ArrayList<String>();
        try {
            if (!isDone(candidate.statusCategory())) {
                return IssueDiscoveryOutcome.skipped(
                        candidate.issueKey() + ": issue is no longer in Jira status category Done."
                );
            }
            var history = jiraIssueStatusHistoryPort.getStatusHistory(candidate.issueKey());
            issueLimitations.addAll(history.limitations());
            var doneAt = finalDoneAt(history);
            if (history.truncated() || doneAt == null || !inRange(doneAt, request.fromDate(), request.toDate())) {
                return IssueDiscoveryOutcome.skipped(candidate.issueKey()
                        + ": final Done transition could not be confirmed in the selected range.");
            }
            var material = jiraIssuePort.getIssueMaterial(JiraIssueMaterialRequest.assessment(
                    candidate.issueKey(),
                    teamFieldIds()
            ));
            issueLimitations.addAll(material.limitations());
            var team = team(material.customFields(), issueLimitations);
            var mergeRequests = mergedMergeRequests(candidate.issueKey(), issueLimitations);
            return IssueDiscoveryOutcome.discovered(new DeliveryAssessmentIssueSource(
                    new DeliveryAssessmentIssue(candidate.issueKey(), doneAt, material, team, issueLimitations),
                    mergeRequests,
                    issueLimitations
            ));
        } catch (RuntimeException exception) {
            return failedOutcome(candidate.issueKey(), exception);
        }
    }

    private List<GitLabMergeRequest> mergedMergeRequests(String issueKey, List<String> limitations) {
        var result = gitLabRepositoryPort.findMergeRequestsByIssueKey(
                gitLabProperties.getGroup(),
                issueKey,
                properties.getMaxMergeRequestsPerIssue()
        );
        limitations.addAll(result.limitations());
        if (result.mergeRequests().isEmpty()) {
            limitations.add(issueKey
                    + ": GitLab returned no merge request candidates for the configured group and issue-key search.");
            return List.of();
        }
        var merged = result.mergeRequests().stream()
                .filter(mergeRequest -> "merged".equalsIgnoreCase(mergeRequest.state())
                        && StringUtils.hasText(mergeRequest.mergedAt()))
                .toList();
        if (merged.isEmpty()) {
            limitations.add(issueKey
                    + ": GitLab returned merge request candidates, but none had state merged with mergedAt.");
        }
        return merged;
    }

    private List<String> teamFieldIds() {
        return StringUtils.hasText(properties.getJiraTeamFieldId())
                ? List.of(properties.getJiraTeamFieldId().trim())
                : List.of();
    }

    private DeliveryAssessmentTeam team(List<JiraIssueCustomField> customFields, List<String> limitations) {
        if (!StringUtils.hasText(properties.getJiraTeamFieldId())) {
            return null;
        }
        var fieldId = properties.getJiraTeamFieldId().trim();
        var field = customFields.stream()
                .filter(customField -> fieldId.equals(customField.fieldId()))
                .findFirst()
                .orElse(null);
        if (field == null || (!StringUtils.hasText(field.id()) && !StringUtils.hasText(field.name())
                && !StringUtils.hasText(field.value()))) {
            limitations.add("Jira team field " + fieldId + " was not available on the issue.");
            return null;
        }
        var name = StringUtils.hasText(field.name()) ? field.name() : field.value();
        return new DeliveryAssessmentTeam(field.id(), name, field.fieldId());
    }

    private Instant finalDoneAt(pl.mkn.tdw.integrations.jira.JiraIssueStatusHistory history) {
        return history.transitions().stream()
                .filter(transition -> isDone(transition.toStatusCategory()))
                .map(pl.mkn.tdw.integrations.jira.JiraIssueStatusTransition::changedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean inRange(Instant doneAt, LocalDate fromDate, LocalDate toDate) {
        var zone = ZoneId.of(properties.getTimeZone());
        var from = fromDate.atStartOfDay(zone).toInstant();
        var toExclusive = toDate.plusDays(1).atStartOfDay(zone).toInstant();
        return !doneAt.isBefore(from) && doneAt.isBefore(toExclusive);
    }

    private boolean isDone(String statusCategory) {
        if (!StringUtils.hasText(statusCategory)) {
            return false;
        }
        var normalized = statusCategory.trim().replace("_", "").replace("-", "");
        return "done".equalsIgnoreCase(normalized);
    }

    private void validateRange(DeliveryEffectivenessAssessmentJobStartRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Delivery Effectiveness Assessment is disabled.");
        }
        var days = ChronoUnit.DAYS.between(request.fromDate(), request.toDate()) + 1;
        if (days > properties.getMaxRangeDays()) {
            throw new IllegalArgumentException(
                    "Date range exceeds delivery-effectiveness-assessment.max-range-days="
                            + properties.getMaxRangeDays()
            );
        }
    }

    private IssueDiscoveryOutcome failedOutcome(String issueKey, Throwable failure) {
        var exception = rootCause(failure);
        log.warn("Delivery assessment issue discovery failed issueKey={} reason={}",
                issueKey, exception.getMessage(), exception);
        return IssueDiscoveryOutcome.skipped(issueKey + ": source material failed: " + safeMessage(exception));
    }

    private Throwable rootCause(Throwable exception) {
        var current = exception;
        while (current.getCause() != null && current != current.getCause()) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMessage(Throwable exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private record IssueDiscoveryOutcome(
            DeliveryAssessmentIssueSource issue,
            List<String> limitations
    ) {

        private IssueDiscoveryOutcome {
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }

        static IssueDiscoveryOutcome discovered(DeliveryAssessmentIssueSource issue) {
            return new IssueDiscoveryOutcome(issue, List.of());
        }

        static IssueDiscoveryOutcome skipped(String limitation) {
            return new IssueDiscoveryOutcome(null, List.of(limitation));
        }
    }
}
