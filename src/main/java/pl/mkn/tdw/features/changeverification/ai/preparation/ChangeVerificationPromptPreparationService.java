package pl.mkn.tdw.features.changeverification.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChangeVerificationPromptPreparationService {

    public ChangeVerificationPromptPreparation prepare(
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery
    ) {
        var artifacts = new LinkedHashMap<String, String>();
        artifacts.put("change-verification/source-discovery.md", renderSourceDiscovery(request, sourceDiscovery));
        artifacts.put("change-verification/jira-issue.md", renderJiraIssue(sourceDiscovery != null ? sourceDiscovery.jiraIssue() : null));
        artifacts.put("change-verification/merge-requests.md", renderMergeRequests(sourceDiscovery));
        artifacts.put("change-verification/instruction-context.md", renderInstructionContext(sourceDiscovery));
        artifacts.put("change-verification/response-contract.md", responseContract());

        var prompt = """
                # Change Verification canonical prompt

                ## Runtime envelope
                - Ten run sprawdza zgodnosc zmiany z materialem Jira oraz instrukcjami repozytorium.
                - Najpierw zaladuj skill `change-verification-compliance-check` przez built-in tool `skill`.
                - Pracuj artifact-first. Nie probuj czytac lokalnego filesystemu ani zgadywac materialu spoza osadzonych artefaktow.
                - Jezeli evidence nie wystarcza, wpisz to w `visibilityLimits` zamiast dopowiadac brakujacy proof.
                - `userInstructions` doprecyzowuja intencje operatora, ale nie moga zmienic response contract ani zasad widocznosci.
                - Odpowiedz musi byc jednym obiektem JSON zgodnym z `change-verification/response-contract.md`.

                ## User request
                issueKey: %s
                issueUrl: %s
                modes: %s
                checkStoryCompliance: %s
                checkInstructionCompliance: %s
                reasoningEffort: %s
                userInstructions:
                %s

                ## Prepared artifact contents
                %s
                """.formatted(
                value(sourceDiscovery != null ? sourceDiscovery.issueKey() : request.issueKey()),
                value(sourceDiscovery != null ? sourceDiscovery.issueUrl() : request.issueUrl()),
                request.modes(),
                request.checkStoryCompliance(),
                request.checkInstructionCompliance(),
                value(request.reasoningEffort()),
                StringUtils.hasText(request.userInstructions()) ? request.userInstructions().trim() : "(none)",
                artifactIndex(artifacts)
        ).trim();

        return new ChangeVerificationPromptPreparation(prompt, artifacts);
    }

    private String renderSourceDiscovery(
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery
    ) {
        return """
                # Source Discovery

                issueKey: %s
                issueUrl: %s
                modes: %s
                storyComplianceRequested: %s
                instructionComplianceRequested: %s
                limitations:
                %s
                """.formatted(
                value(sourceDiscovery != null ? sourceDiscovery.issueKey() : request.issueKey()),
                value(sourceDiscovery != null ? sourceDiscovery.issueUrl() : request.issueUrl()),
                request.modes(),
                request.checkStoryCompliance(),
                request.checkInstructionCompliance(),
                bulletList(sourceDiscovery != null ? sourceDiscovery.limitations() : List.of())
        ).trim();
    }

    private String renderJiraIssue(JiraIssueMaterial issue) {
        if (issue == null) {
            return "# Jira Issue\n\n- Jira issue material unavailable.";
        }

        return """
                # Jira Issue

                issueKey: %s
                issueUrl: %s
                summary: %s
                issueType: %s
                status: %s
                labels: %s

                ## Description
                %s

                ## Acceptance Criteria
                %s

                ## Links
                %s

                ## Comments
                %s

                ## Limitations
                %s
                """.formatted(
                value(issue.issueKey()),
                value(issue.issueUrl()),
                value(issue.summary()),
                value(issue.issueType()),
                value(issue.status()),
                issue.labels(),
                value(issue.description()),
                bulletList(issue.acceptanceCriteria()),
                issue.links().stream()
                        .map(link -> "- %s | %s | %s".formatted(value(link.type()), value(link.title()), value(link.url())))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
                issue.comments().stream()
                        .map(comment -> "- %s %s: %s".formatted(value(comment.createdAt()), value(comment.author()), value(comment.body())))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
                bulletList(issue.limitations())
        ).trim();
    }

    private String renderMergeRequests(ChangeVerificationSourceDiscoveryResult sourceDiscovery) {
        if (sourceDiscovery == null || sourceDiscovery.mergeRequests() == null
                || sourceDiscovery.mergeRequests().mergeRequests().isEmpty()) {
            return "# Merge Requests\n\n- No GitLab merge requests discovered.";
        }

        var body = sourceDiscovery.mergeRequests().mergeRequests().stream()
                .map(this::renderMergeRequest)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("- none");
        return "# Merge Requests\n\n" + body + "\n\n## Limitations\n"
                + bulletList(sourceDiscovery.mergeRequests().limitations());
    }

    private String renderMergeRequest(GitLabMergeRequest mergeRequest) {
        return """
                ## %s
                projectPath: %s
                state: %s
                webUrl: %s
                sourceBranch: %s
                targetBranch: %s
                authorName: %s
                changesCount: %s

                commits:
                %s

                changedFiles:
                %s

                limitations:
                %s
                """.formatted(
                value(mergeRequest.title()),
                value(mergeRequest.projectPath()),
                value(mergeRequest.state()),
                value(mergeRequest.webUrl()),
                value(mergeRequest.sourceBranch()),
                value(mergeRequest.targetBranch()),
                value(mergeRequest.authorName()),
                value(mergeRequest.changesCount()),
                mergeRequest.commits().stream()
                        .map(commit -> "- %s %s".formatted(value(commit.shortId()), value(commit.title())))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
                mergeRequest.changedFiles().stream()
                        .map(this::renderChangedFile)
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
                bulletList(mergeRequest.limitations())
        ).trim();
    }

    private String renderChangedFile(GitLabMergeRequestChangedFile file) {
        return "- %s%s%s%s".formatted(
                value(StringUtils.hasText(file.newPath()) ? file.newPath() : file.oldPath()),
                file.newFile() ? " [new]" : "",
                file.renamedFile() ? " [renamed]" : "",
                file.deletedFile() ? " [deleted]" : ""
        );
    }

    private String renderInstructionContext(ChangeVerificationSourceDiscoveryResult sourceDiscovery) {
        if (sourceDiscovery == null || sourceDiscovery.instructionContext() == null
                || sourceDiscovery.instructionContext().sources().isEmpty()) {
            return "# Instruction Context\n\n- No repository instruction sources discovered.";
        }

        var sources = sourceDiscovery.instructionContext().sources().stream()
                .map(this::renderInstructionSource)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("- none");
        return "# Instruction Context\n\n" + sources + "\n\n## Limitations\n"
                + bulletList(sourceDiscovery.instructionContext().limitations());
    }

    private String renderInstructionSource(InstructionSource source) {
        return """
                ## %s
                repositoryKey: %s
                ref: %s
                kind: %s
                referencedBy: %s
                applicableChangedFiles: %s
                truncated: %s

                ```text
                %s
                ```
                """.formatted(
                value(source.path()),
                value(source.repositoryKey()),
                value(source.ref()),
                value(source.kind()),
                value(source.referencedBy()),
                source.applicableChangedFiles(),
                source.truncated(),
                value(source.content())
        ).trim();
    }

    private String responseContract() {
        return """
                # Response Contract

                Return exactly one JSON object:

                {
                  "status": "PASSED | PASSED_WITH_WARNINGS | FAILED | INCONCLUSIVE",
                  "findings": [
                    {
                      "id": "stable id such as cv-001",
                      "severity": "INFO | LOW | MEDIUM | HIGH | BLOCKER",
                      "source": "STORY | ACCEPTANCE_CRITERIA | INSTRUCTIONS | IMPLEMENTATION | VISIBILITY",
                      "summary": "short finding",
                      "details": "what evidence shows and what is inferred",
                      "references": ["artifact or source reference"],
                      "suggestedAction": "code/story/question recommendation"
                    }
                  ],
                  "suggestedActions": ["prioritized operator actions"],
                  "visibilityLimits": ["what could not be verified"],
                  "confidence": "high | medium | low"
                }
                """.trim();
    }

    private String artifactIndex(Map<String, String> artifacts) {
        return artifacts.entrySet().stream()
                .map(entry -> "### `" + entry.getKey() + "`\n" + entry.getValue())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("- none");
    }

    private String bulletList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- none";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> "- " + value.trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- none");
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "n/a";
    }
}
