package pl.mkn.tdw.features.changeverification.source;

import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestDiffPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabInstructionContextPort;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextRequest;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryScope;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterial;
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
    private final GitLabMergeRequestDiffPort gitLabMergeRequestDiffPort;
    private final GitLabSettingsPort gitLabProperties;
    private final GitLabInstructionContextPort instructionContextDiscoveryService;
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
        var diffSnapshots = new ArrayList<GitLabMergeRequestDiffSnapshot>();

        if (!StringUtils.hasText(issueKey)) {
            limitations.add("Jira issue key could not be resolved from request.");
        } else {
            listener.onJiraMaterialStarted(issueKey);
            jiraIssue = fetchJiraIssue(issueKey, limitations);
            listener.onJiraMaterialCompleted(issueKey, jiraIssue, List.copyOf(limitations));
            listener.onMergeRequestDiscoveryStarted(issueKey);
            mergeRequests = fetchMergeRequests(issueKeysForMergeRequests(issueKey, jiraIssue), limitations);
            if (mergeRequests != null) {
                for (var mr : mergeRequests.mergeRequests()) {
                    if (mr.iid() == null) {
                        limitations.add("MR without iid cannot be pinned to a diff version.");
                        continue;
                    }
                    var project = pl.mkn.tdw.common.GitLabPathUtils.relativeProjectPath(gitLabProperties.getGroup(), mr.projectPath());
                    try {
                        var diff = gitLabMergeRequestDiffPort.readMergeRequestDiff(gitLabProperties.getGroup(), project, mr.iid());
                        if (diff == null || !mr.projectPath().equals(diff.projectPath()) || mr.iid() != diff.mergeRequestIid()) {
                            throw new IllegalStateException("MR diff identity did not match discovery.");
                        }
                        diffSnapshots.add(diff);
                        diff.limitations().forEach(l -> limitations.add(mr.projectPath() + "!" + mr.iid() + ": " + l));
                    } catch (RuntimeException exception) {
                        limitations.add("MR diff unavailable for " + mr.projectPath() + "!" + mr.iid() + ": " + safeMessage(exception));
                    }
                }
            }
            listener.onMergeRequestDiscoveryCompleted(issueKey, mergeRequests, List.copyOf(limitations));
            refSelections = resolveRepositoryRefs(
                    ChangeVerificationRepositorySnapshotFactory.from(mergeRequests, null, gitLabProperties.getGroup()),
                    diffSnapshots,
                    limitations
            );
            if (Boolean.TRUE.equals(request.checkInstructionCompliance())) {
                listener.onInstructionContextStarted(mergeRequests);
                instructionContext = fetchInstructionContext(mergeRequests, diffSnapshots, limitations);
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
                diffSnapshots,
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

        var mergeRequestsByKey = new LinkedHashMap<String, pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequest>();
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
        }
        return List.copyOf(issueKeys);
    }

    private String mergeRequestIdentity(pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequest mergeRequest) {
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
            List<GitLabMergeRequestDiffSnapshot> diffs,
            List<String> limitations
    ) {
        if (mergeRequests == null || mergeRequests.mergeRequests().isEmpty()) {
            limitations.add("Instruction context could not be discovered because no merge requests were found.");
            return null;
        }

        try {
            return instructionContextDiscoveryService.discover(new InstructionContextRequest(
                    instructionRepositoryScopes(mergeRequests, diffs)
            ));
        } catch (RuntimeException exception) {
            log.warn("Change Verification instruction context discovery failed reason={}", exception.getMessage());
            limitations.add("Instruction context could not be fetched: " + safeMessage(exception));
            return null;
        }
    }

    private List<ChangeVerificationRepositoryRefSelection> resolveRepositoryRefs(
            List<ChangeVerificationRepositorySnapshot> repositories,
            List<GitLabMergeRequestDiffSnapshot> diffs,
            List<String> limitations
    ) {
        if (repositories == null || repositories.isEmpty()) {
            return List.of();
        }
        return repositories.stream()
                .map(repository -> resolveRepositoryRef(repository, diffs, limitations))
                .toList();
    }

    private ChangeVerificationRepositoryRefSelection resolveRepositoryRef(
            ChangeVerificationRepositorySnapshot repository,
            List<GitLabMergeRequestDiffSnapshot> diffs,
            List<String> limitations
    ) {
        var selectionLimitations = new ArrayList<String>();
        var matching = diffs.stream().filter(d -> d.hasRevisions()
                && repository.projectPath().equals(d.projectPath())
                && repository.mergeRequests().stream().anyMatch(mr -> mr.iid() != null && mr.iid() == d.mergeRequestIid()))
                .toList();
        var heads = matching.stream().map(d -> d.headSha()).distinct().toList();
        boolean singleCompleteHead = heads.size() == 1 && matching.size() == repository.mergeRequests().size();
        var analysisRef = singleCompleteHead ? heads.get(0) : "";
        var analysisRefSource = singleCompleteHead
                ? ChangeVerificationRepositoryRefSelection.MR_HEAD_SHA : ChangeVerificationRepositoryRefSelection.UNRESOLVED;
        if (!singleCompleteHead) {
            selectionLimitations.add("No single pinned MR head for " + repository.projectName()
                    + "; use the individual MR base/head SHAs, never a moving branch fallback.");
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
                List.copyOf(selectionLimitations)
        );
    }

    private List<InstructionRepositoryScope> instructionRepositoryScopes(
            GitLabMergeRequestSearchResult mergeRequests,
            List<GitLabMergeRequestDiffSnapshot> diffs
    ) {
        var scopes = new LinkedHashMap<String, InstructionScopeAccumulator>();
        for (var mergeRequest : mergeRequests.mergeRequests()) {
            var projectPath = value(mergeRequest.projectPath());
            var ref = diffs.stream().filter(d -> d.hasRevisions() && projectPath.equals(d.projectPath())
                    && mergeRequest.iid() != null && mergeRequest.iid() == d.mergeRequestIid())
                    .map(d -> d.headSha()).findFirst().orElse("");
            if (!StringUtils.hasText(ref)) {
                continue;
            }
            var key = projectPath + "|" + ref;
            var accumulator = scopes.computeIfAbsent(
                    key,
                    ignored -> new InstructionScopeAccumulator(projectPath, ref)
            );
            mergeRequest.changedFiles().stream()
                    .map(file -> StringUtils.hasText(file.newPath()) ? file.newPath() : file.oldPath())
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(accumulator.changedFilePaths::add);
        }
        return scopes.values().stream()
                .map(accumulator -> new InstructionRepositoryScope(
                        accumulator.projectPath,
                        accumulator.ref,
                        List.copyOf(accumulator.changedFilePaths)
                ))
                .toList();
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

    private static final class InstructionScopeAccumulator {
        private final String projectPath;
        private final String ref;
        private final LinkedHashSet<String> changedFilePaths = new LinkedHashSet<>();

        private InstructionScopeAccumulator(String projectPath, String ref) {
            this.projectPath = projectPath;
            this.ref = ref;
        }
    }
}
