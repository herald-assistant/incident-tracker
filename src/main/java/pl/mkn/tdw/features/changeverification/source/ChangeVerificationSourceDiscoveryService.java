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
import java.util.List;
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

        if (!StringUtils.hasText(issueKey)) {
            limitations.add("Jira issue key could not be resolved from request.");
        } else {
            listener.onJiraMaterialStarted(issueKey);
            jiraIssue = fetchJiraIssue(issueKey, limitations);
            listener.onJiraMaterialCompleted(issueKey, jiraIssue, List.copyOf(limitations));
            listener.onMergeRequestDiscoveryStarted(issueKey);
            mergeRequests = fetchMergeRequests(issueKey, limitations);
            listener.onMergeRequestDiscoveryCompleted(issueKey, mergeRequests, List.copyOf(limitations));
            if (Boolean.TRUE.equals(request.checkInstructionCompliance())) {
                listener.onInstructionContextStarted(mergeRequests);
                instructionContext = fetchInstructionContext(mergeRequests, limitations);
                listener.onInstructionContextCompleted(mergeRequests, instructionContext, List.copyOf(limitations));
            }
        }

        return new ChangeVerificationSourceDiscoveryResult(
                issueKey,
                request.issueUrl(),
                jiraIssue,
                mergeRequests,
                instructionContext,
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

    private InstructionContextResult fetchInstructionContext(
            GitLabMergeRequestSearchResult mergeRequests,
            List<String> limitations
    ) {
        if (mergeRequests == null || mergeRequests.mergeRequests().isEmpty()) {
            limitations.add("Instruction context could not be discovered because no merge requests were found.");
            return null;
        }

        try {
            return instructionContextDiscoveryService.discover(new InstructionContextRequest(
                    mergeRequests.mergeRequests().stream()
                            .map(mergeRequest -> new InstructionRepositoryScope(
                                    mergeRequest.projectPath(),
                                    StringUtils.hasText(mergeRequest.sourceBranch())
                                            ? mergeRequest.sourceBranch()
                                            : mergeRequest.targetBranch(),
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
}
