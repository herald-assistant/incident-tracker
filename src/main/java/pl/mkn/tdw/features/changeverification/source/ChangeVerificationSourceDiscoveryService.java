package pl.mkn.tdw.features.changeverification.source;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextRequest;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryScope;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;
import pl.mkn.tdw.integrations.jira.JiraIssuePort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChangeVerificationSourceDiscoveryService {

    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("([A-Z][A-Z0-9]+-\\d+)");

    private final JiraIssuePort jiraIssuePort;
    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final GitLabProperties gitLabProperties;
    private final InstructionContextDiscoveryService instructionContextDiscoveryService;
    private final ChangeVerificationOperationalContextMatcher operationalContextMatcher;

    public ChangeVerificationSourceDiscoveryResult discover(ChangeVerificationJobStartRequest request) {
        return discover(request, ChangeVerificationSourceDiscoveryListener.NO_OP);
    }

    public ChangeVerificationSourceDiscoveryResult discover(
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryListener listener
    ) {
        var issueKey = resolveIssueKey(request);
        var limitations = new ArrayList<String>();
        JiraIssueMaterial jiraIssue = null;
        GitLabMergeRequestSearchResult mergeRequests = null;
        InstructionContextResult instructionContext = null;
        List<ChangeVerificationRepositoryRefSelection> refSelections = List.of();

        if (!StringUtils.hasText(issueKey)) {
            limitations.add("Jira issue key could not be resolved from request.");
        } else {
            listener.onJiraMaterialStarted(issueKey);
            jiraIssue = fetchJiraIssue(issueKey, limitations);
            listener.onJiraMaterialCompleted(issueKey, jiraIssue, List.copyOf(limitations));
            listener.onMergeRequestDiscoveryStarted(issueKey);
            mergeRequests = fetchMergeRequests(issueKeysForMergeRequests(issueKey, jiraIssue), limitations);
            listener.onMergeRequestDiscoveryCompleted(issueKey, mergeRequests, List.copyOf(limitations));
            refSelections = resolveRepositoryRefs(
                    ChangeVerificationRepositorySnapshotFactory.from(mergeRequests, null, gitLabProperties.getGroup()),
                    limitations
            );
            if (Boolean.TRUE.equals(request.checkInstructionCompliance())) {
                listener.onInstructionContextStarted(mergeRequests);
                instructionContext = fetchInstructionContext(mergeRequests, refSelections, limitations);
                listener.onInstructionContextCompleted(mergeRequests, instructionContext, List.copyOf(limitations));
            }
        }

        var repositories = ChangeVerificationRepositorySnapshotFactory.from(
                mergeRequests,
                instructionContext,
                gitLabProperties.getGroup(),
                refSelections
        );
        repositories = operationalContextMatcher.enrich(repositories);

        return new ChangeVerificationSourceDiscoveryResult(
                issueKey,
                request.issueUrl(),
                jiraIssue,
                mergeRequests,
                instructionContext,
                repositories,
                limitations
        );
    }

    private JiraIssueMaterial fetchJiraIssue(String issueKey, List<String> limitations) {
        try {
            return jiraIssuePort.getIssueMaterial(issueKey);
        } catch (RuntimeException exception) {
            log.warn("Change Verification Jira issue discovery failed issueKey={} reason={}", issueKey, exception.getMessage());
            limitations.add("Jira issue material could not be fetched: " + safeMessage(exception));
            return null;
        }
    }

    private GitLabMergeRequestSearchResult fetchMergeRequests(List<String> issueKeys, List<String> limitations) {
        if (issueKeys == null || issueKeys.isEmpty()) {
            return new GitLabMergeRequestSearchResult(null, gitLabProperties.getGroup(), List.of(), List.of());
        }

        var mergeRequestsByKey = new LinkedHashMap<String, pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest>();
        var collectedLimitations = new ArrayList<String>();
        for (var issueKey : issueKeys) {
            var result = fetchMergeRequests(issueKey, limitations);
            if (result == null) {
                continue;
            }
            result.mergeRequests().forEach(mergeRequest ->
                    mergeRequestsByKey.putIfAbsent(mergeRequestIdentity(mergeRequest), mergeRequest)
            );
            result.limitations().stream()
                    .filter(StringUtils::hasText)
                    .map(limitation -> issueKey + ": " + limitation)
                    .forEach(collectedLimitations::add);
        }

        return new GitLabMergeRequestSearchResult(
                String.join(",", issueKeys),
                gitLabProperties.getGroup(),
                List.copyOf(mergeRequestsByKey.values()),
                collectedLimitations
        );
    }

    private GitLabMergeRequestSearchResult fetchMergeRequests(String issueKey, List<String> limitations) {
        try {
            return gitLabRepositoryPort.findMergeRequestsByIssueKey(
                    gitLabProperties.getGroup(),
                    issueKey,
                    gitLabProperties.getMaxMergeRequests()
            );
        } catch (RuntimeException exception) {
            log.warn("Change Verification GitLab MR discovery failed issueKey={} reason={}", issueKey, exception.getMessage());
            limitations.add("GitLab merge requests could not be fetched: " + safeMessage(exception));
            return null;
        }
    }

    private List<String> issueKeysForMergeRequests(String issueKey, JiraIssueMaterial jiraIssue) {
        var issueKeys = new LinkedHashSet<String>();
        if (StringUtils.hasText(issueKey)) {
            issueKeys.add(issueKey.trim());
        }
        if (jiraIssue != null) {
            jiraIssue.subTasks().stream()
                    .map(JiraIssueMaterial::issueKey)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(issueKeys::add);
            if (jiraIssue.parentIssue() != null) {
                var parentIssue = jiraIssue.parentIssue();
                if (StringUtils.hasText(parentIssue.issueKey())) {
                    issueKeys.add(parentIssue.issueKey().trim());
                }
                parentIssue.subTasks().stream()
                        .map(JiraIssueMaterial::issueKey)
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(issueKeys::add);
            }
        }
        return List.copyOf(issueKeys);
    }

    private String mergeRequestIdentity(pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest mergeRequest) {
        if (mergeRequest.id() != null) {
            return "id:" + mergeRequest.id();
        }
        if (StringUtils.hasText(mergeRequest.webUrl())) {
            return "url:" + mergeRequest.webUrl().trim();
        }
        return "fallback:%s:%s:%s".formatted(
                mergeRequest.projectPath(),
                mergeRequest.iid(),
                mergeRequest.title()
        );
    }

    private InstructionContextResult fetchInstructionContext(
            GitLabMergeRequestSearchResult mergeRequests,
            List<ChangeVerificationRepositoryRefSelection> refSelections,
            List<String> limitations
    ) {
        if (mergeRequests == null || mergeRequests.mergeRequests().isEmpty()) {
            limitations.add("Instruction context could not be discovered because no merge requests were found.");
            return null;
        }

        try {
            var refsByKey = refSelectionsByKey(refSelections);
            return instructionContextDiscoveryService.discover(new InstructionContextRequest(
                    mergeRequests.mergeRequests().stream()
                            .map(mergeRequest -> new InstructionRepositoryScope(
                                    mergeRequest.projectPath(),
                                    analysisRef(mergeRequest, refsByKey),
                                    mergeRequest.changedFiles().stream()
                                            .map(file -> StringUtils.hasText(file.newPath()) ? file.newPath() : file.oldPath())
                                            .filter(StringUtils::hasText)
                                            .toList()
                            ))
                            .toList()
            ));
        } catch (RuntimeException exception) {
            log.warn("Change Verification instruction context discovery failed reason={}", exception.getMessage());
            limitations.add("Instruction context could not be fetched: " + safeMessage(exception));
            return null;
        }
    }

    private List<ChangeVerificationRepositoryRefSelection> resolveRepositoryRefs(
            List<ChangeVerificationRepositorySnapshot> repositories,
            List<String> limitations
    ) {
        if (repositories == null || repositories.isEmpty()) {
            return List.of();
        }
        return repositories.stream()
                .map(repository -> resolveRepositoryRef(repository, limitations))
                .toList();
    }

    private ChangeVerificationRepositoryRefSelection resolveRepositoryRef(
            ChangeVerificationRepositorySnapshot repository,
            List<String> limitations
    ) {
        var selectionLimitations = new ArrayList<String>();
        var sourceAvailable = branchAvailable(repository, repository.sourceRef(), selectionLimitations);
        var targetAvailable = branchAvailable(repository, repository.targetRef(), selectionLimitations);
        var analysisRef = repository.sourceRef();
        var analysisRefSource = ChangeVerificationRepositoryRefSelection.SOURCE_REF;

        if (!Boolean.TRUE.equals(sourceAvailable) && Boolean.TRUE.equals(targetAvailable)) {
            analysisRef = repository.targetRef();
            analysisRefSource = ChangeVerificationRepositoryRefSelection.TARGET_REF;
            selectionLimitations.add("Source branch '%s' is not available for %s; target branch '%s' will be used for GitLab analysis."
                    .formatted(repository.sourceRef(), repository.projectName(), repository.targetRef()));
        } else if (!StringUtils.hasText(analysisRef) && StringUtils.hasText(repository.targetRef())) {
            analysisRef = repository.targetRef();
            analysisRefSource = ChangeVerificationRepositoryRefSelection.TARGET_REF;
        } else if (!Boolean.TRUE.equals(sourceAvailable) && !Boolean.TRUE.equals(targetAvailable)) {
            selectionLimitations.add("Neither source branch '%s' nor target branch '%s' could be confirmed for %s; GitLab analysis will use '%s' as a best-effort ref."
                    .formatted(repository.sourceRef(), repository.targetRef(), repository.projectName(), analysisRef));
        }

        selectionLimitations.stream()
                .filter(StringUtils::hasText)
                .filter(limitation -> !limitations.contains(limitation))
                .forEach(limitations::add);
        return new ChangeVerificationRepositoryRefSelection(
                repository.repositoryKey(),
                repository.sourceRef(),
                repository.targetRef(),
                analysisRef,
                analysisRefSource,
                sourceAvailable,
                targetAvailable,
                List.copyOf(selectionLimitations)
        );
    }

    private Boolean branchAvailable(
            ChangeVerificationRepositorySnapshot repository,
            String branchRef,
            List<String> limitations
    ) {
        if (!StringUtils.hasText(branchRef)) {
            return false;
        }
        try {
            return gitLabRepositoryPort.branchExists(
                    gitLabProperties.getGroup(),
                    repository.projectName(),
                    branchRef
            );
        } catch (RuntimeException exception) {
            limitations.add("GitLab branch availability could not be checked for %s@%s: %s"
                    .formatted(repository.projectName(), branchRef, safeMessage(exception)));
            return null;
        }
    }

    private Map<String, ChangeVerificationRepositoryRefSelection> refSelectionsByKey(
            List<ChangeVerificationRepositoryRefSelection> refSelections
    ) {
        var result = new LinkedHashMap<String, ChangeVerificationRepositoryRefSelection>();
        if (refSelections == null) {
            return result;
        }
        refSelections.stream()
                .filter(selection -> selection != null && StringUtils.hasText(selection.key()))
                .forEach(selection -> result.putIfAbsent(selection.key(), selection));
        return result;
    }

    private String analysisRef(
            pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest mergeRequest,
            Map<String, ChangeVerificationRepositoryRefSelection> refsByKey
    ) {
        var sourceRef = StringUtils.hasText(mergeRequest.sourceBranch())
                ? mergeRequest.sourceBranch().trim()
                : value(mergeRequest.targetBranch());
        var targetRef = value(mergeRequest.targetBranch());
        var refSelection = refsByKey.get(ChangeVerificationRepositoryRefSelection.key(
                mergeRequest.projectPath(),
                sourceRef,
                targetRef
        ));
        return refSelection != null && StringUtils.hasText(refSelection.analysisRef())
                ? refSelection.analysisRef()
                : sourceRef;
    }

    private String resolveIssueKey(ChangeVerificationJobStartRequest request) {
        if (StringUtils.hasText(request.issueKey())) {
            return request.issueKey().trim();
        }
        if (!StringUtils.hasText(request.issueUrl())) {
            return null;
        }

        var matcher = ISSUE_KEY_PATTERN.matcher(request.issueUrl().toUpperCase());
        return matcher.find() ? matcher.group(1) : null;
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
