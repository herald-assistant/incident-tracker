package pl.mkn.tdw.features.changeverification.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationChangedFileSnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationRepositorySnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
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
        artifacts.put("change-verification/repository-scope.md", renderRepositoryScope(sourceDiscovery));
        artifacts.put("change-verification/merge-requests.md", renderMergeRequests(sourceDiscovery));
        artifacts.put("change-verification/instruction-context.md", renderInstructionContext(sourceDiscovery));
        artifacts.put("change-verification/response-contract.md", responseContract());

        var prompt = """
                # Change Verification

                - Zaladuj skill `change-verification-orchestrator` i wykonaj jego workflow.
                - Jedynym wynikiem merytorycznym jest JSON zgodny z artefaktem `change-verification/response-contract.md`.
                - Kazda regula autora z Jira/Confluence lub instrukcji repozytorium ma wystapic dokladnie raz w `rules` z doslownym cytatem i referencja.
                - Normalizacja AI jest opisem pomocniczym; nie zastepuje tekstu autora.
                - Nie tworz osobnych findings, globalnych actions, statusu ani raportu Markdown.
                - Dodatkowe kontrole AI umieszczaj tylko w `additionalChecks`; nie zmieniaja one decyzji dla regul zrodlowych.
                - Pracuj artifact-first. GitLab i Operational Context tools wykorzystuj celowanie tylko wtedy, gdy konkretna regula wymaga glebszego dowodu.
                - Nie korzystaj z lokalnego filesystemu ani DB tools i nie zgaduj brakujacego evidence.
                - `userInstructions` moga doprecyzowac fokus, ale nie zmieniaja kontraktu i pochodzenia regul.

                ## User request
                issueKey: %s
                issueUrl: %s
                checkStoryCompliance: %s
                checkInstructionCompliance: %s
                reasoningEffort: %s
                userInstructions:
                %s

                ## Source artifacts
                %s
                """.formatted(
                value(sourceDiscovery != null ? sourceDiscovery.issueKey() : request.issueKey()),
                value(sourceDiscovery != null ? sourceDiscovery.issueUrl() : request.issueUrl()),
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
                storyComplianceRequested: %s
                instructionComplianceRequested: %s
                """.formatted(
                value(sourceDiscovery != null ? sourceDiscovery.issueKey() : request.issueKey()),
                value(sourceDiscovery != null ? sourceDiscovery.issueUrl() : request.issueUrl()),
                request.checkStoryCompliance(),
                request.checkInstructionCompliance()
        ).trim();
    }

    private String renderJiraIssue(JiraIssueMaterial issue) {
        if (issue == null) {
            return "# Jira Issue\n\n- Jira issue material unavailable.";
        }

        return """
                # Jira Target Issue

                issueKey: %s
                issueUrl: %s
                summary: %s
                issueType: %s
                status: %s
                labels: %s
                verificationScope: TARGET

                ## Source Roles
                - targetIssue: glowny zakres weryfikacji; implementacje oceniaj przede wszystkim wzgledem tego issue.
                - acceptanceCriteria: najsilniejszy sygnal wymagan w target issue.
                - description: doprecyzowanie celu i oczekiwanego zachowania target issue.
                - parentContext: kontekst interpretacyjny, uzywaj do zrozumienia celu, slownictwa, ryzyk i zaleznosci.
                - confluencePages: material kontekstowy z remote-linkow; nie rozszerza zakresu bez jawnego powiazania z target issue.
                - subtasks: sasiedni lub podrzedny kontekst pracy; nie traktuj jako automatyczny zakres target issue.

                ## Description
                %s

                ## Acceptance Criteria
                %s

                ## Parent Context
                %s

                ## Subtasks
                %s

                ## Confluence Pages
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
                renderParentIssue(issue.parentIssue()),
                issue.subTasks().stream()
                        .map(this::renderSubTask)
                        .reduce((left, right) -> left + "\n\n" + right)
                        .orElse("- none"),
                issue.confluencePages().stream()
                        .map(page -> """
                - %s | %s | %s
                  role: TARGET_CONTEXT_CONFLUENCE
                  interpretation: Doprecyzowuje target issue; nie dodaje samodzielnie nowego zakresu.
                  version: %s
                  content:
                  %s
                                  limitations: %s
                                """.formatted(
                                value(page.pageId()),
                                value(page.title()),
                                value(page.url()),
                                value(page.version()),
                                value(page.content()),
                                page.limitations()
                        ).trim())
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
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

    private String renderParentIssue(JiraIssueMaterial parentIssue) {
        if (parentIssue == null) {
            return "- none";
        }
        return """
                relation: PARENT_CONTEXT
                role: BROADER_CONTEXT
                interpretation: Uzywaj do zrozumienia celu nadrzednego, AC, linkow i slownictwa. Nie oceniaj calego parenta jako zakresu target issue.
                issueKey: %s
                issueUrl: %s
                summary: %s
                issueType: %s
                status: %s

                description:
                %s

                acceptanceCriteria:
                %s

                relatedSubtasks:
                %s

                confluencePages:
                %s
                """.formatted(
                value(parentIssue.issueKey()),
                value(parentIssue.issueUrl()),
                value(parentIssue.summary()),
                value(parentIssue.issueType()),
                value(parentIssue.status()),
                value(parentIssue.description()),
                bulletList(parentIssue.acceptanceCriteria()),
                parentIssue.subTasks().stream()
                        .map(this::renderSubTask)
                        .reduce((left, right) -> left + "\n\n" + right)
                        .orElse("- none"),
                parentIssue.confluencePages().stream()
                        .map(page -> "- %s | %s | %s\n  role: PARENT_CONTEXT_CONFLUENCE\n  interpretation: Kontekst parenta, nie samodzielny zakres target issue.\n  %s".formatted(
                                value(page.pageId()),
                                value(page.title()),
                                value(page.url()),
                                value(page.content())
                        ))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none")
        ).trim();
    }

    private String renderSubTask(JiraIssueMaterial subTask) {
        return """
                - %s | %s | %s
                  role: RELATED_SUBTASK_CONTEXT
                  interpretation: Uzywaj jako kontekstu powiazanej pracy; nie rozszerza automatycznie zakresu target issue.
                  summary: %s
                  description:
                  %s
                  acceptanceCriteria:
                  %s
                """.formatted(
                value(subTask.issueKey()),
                value(subTask.issueType()),
                value(subTask.status()),
                value(subTask.summary()),
                value(subTask.description()),
                bulletList(subTask.acceptanceCriteria())
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
        return "# Merge Requests\n\n" + body;
    }

    private String renderRepositoryScope(ChangeVerificationSourceDiscoveryResult sourceDiscovery) {
        if (sourceDiscovery == null || sourceDiscovery.repositories().isEmpty()) {
            return "# Repository Scope\n\n- No repository scope discovered.";
        }

        var body = sourceDiscovery.repositories().stream()
                .map(this::renderRepository)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("- none");
        return "# Repository Scope\n\n" + body;
    }

    private String renderRepository(ChangeVerificationRepositorySnapshot repository) {
        return """
                ## %s
                repositoryKey: %s
                projectPath: %s
                rootGroup: %s
                groupPath: %s
                repositoryName: %s
                projectName: %s
                GitLab tool input: projectName=%s, branchRef=%s
                GitLab path context: projectPath=%s
                sourceRef: %s
                sourceRefAvailable: %s
                targetRef: %s
                targetRefAvailable: %s
                analysisRef: %s
                analysisRefSource: %s
                mergeRequests: %s

                changedFiles:
                %s

                instructionSources:
                %s

                operationalContextMatches:
                %s

                """.formatted(
                value(repository.projectPath()),
                value(repository.repositoryKey()),
                value(repository.projectPath()),
                value(repository.rootGroup()),
                value(repository.groupPath()),
                value(repository.repositoryName()),
                value(repository.projectName()),
                value(repository.projectName()),
                value(repository.analysisRef()),
                value(repository.projectPath()),
                value(repository.sourceRef()),
                String.valueOf(repository.sourceRefAvailable()),
                value(repository.targetRef()),
                String.valueOf(repository.targetRefAvailable()),
                value(repository.analysisRef()),
                value(repository.analysisRefSource()),
                repository.mergeRequests().stream()
                        .map(mergeRequest -> value(mergeRequest.webUrl()))
                        .toList(),
                repository.changedFiles().stream()
                        .map(this::renderRepositoryChangedFile)
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
                repository.instructionSources().stream()
                        .map(source -> "- %s | %s | %s".formatted(
                                value(source.path()),
                                value(source.kind()),
                                value(source.ref())
                        ))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
                repository.operationalContextMatches().stream()
                        .map(this::renderOperationalContextMatch)
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none")
        ).trim();
    }

    private String renderOperationalContextMatch(pl.mkn.tdw.features.changeverification.source.ChangeVerificationOperationalContextMatch match) {
        return "- repoId=%s | codeSearchScope=%s (%s) | relation=repo->code-search-scope->%s:%s | role=%s | searchMode=%s | pathPrefixes=%s | readFor=%s | reason=%s | limitations=%s".formatted(
                value(match.repositoryId()),
                value(match.codeSearchScopeId()),
                value(match.codeSearchScopeName()),
                value(match.targetType()),
                value(match.targetId()),
                value(match.repositoryRole()),
                value(match.searchMode()),
                match.pathPrefixes(),
                match.readFor(),
                value(match.reason()),
                match.limitations()
        );
    }

    private String renderRepositoryChangedFile(ChangeVerificationChangedFileSnapshot file) {
        return "- %s%s%s%s | MRs: %s".formatted(
                value(file.path()),
                file.newFile() ? " [new]" : "",
                file.renamedFile() ? " [renamed]" : "",
                file.deletedFile() ? " [deleted]" : "",
                file.mergeRequestRefs()
        );
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
                        .orElse("- none")
        ).trim();
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
        return "# Instruction Context\n\n" + sources;
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
                  "rules": [
                    {
                      "id": "story-001 or instruction-001",
                      "scope": "STORY | INSTRUCTION",
                      "source": {
                        "type": "ACCEPTANCE_CRITERION | JIRA_DESCRIPTION | JIRA_COMMENT | CONFLUENCE | REPOSITORY_INSTRUCTION | OPERATOR_INSTRUCTION",
                        "label": "human-readable source",
                        "reference": "issue/page/file reference",
                        "quote": "exact author-written rule"
                      },
                      "normalizedRule": "precise interpretation without replacing the quote",
                      "interpretationType": "EXPLICIT | NORMALIZED | CONFLICTING | NOT_VERIFIABLE",
                      "outcome": "SATISFIED | NOT_SATISFIED | NOT_VERIFIED",
                      "releaseImpact": "NONE | REVIEW | BLOCKER",
                      "conclusion": "one evidence-based sentence",
                      "evidence": [{"summary": "confirmed fact", "reference": "MR/file/class/test"}],
                      "missingEvidence": [],
                      "action": null,
                      "rationale": null,
                      "riskIfOmitted": null,
                      "signals": [],
                      "confidence": null
                    }
                  ],
                  "additionalChecks": [
                    {
                      "id": "additional-001",
                      "scope": "ADDITIONAL",
                      "source": {"type": "AI_SUGGESTION", "label": "AI-suggested check", "reference": "AI", "quote": "the proposed check"},
                      "normalizedRule": "the proposed check",
                      "interpretationType": "INFERRED",
                      "outcome": "SATISFIED | NOT_SATISFIED | NOT_VERIFIED",
                      "releaseImpact": "NONE | REVIEW | BLOCKER",
                      "conclusion": "one evidence-based sentence",
                      "evidence": [{"summary": "confirmed fact", "reference": "MR/file/class/test"}],
                      "missingEvidence": [],
                      "action": "concrete next step",
                      "rationale": "why this is release-critical",
                      "riskIfOmitted": "concrete risk",
                      "signals": ["specific source signal"],
                      "confidence": "HIGH | MEDIUM | LOW"
                    }
                  ],
                  "visibilityLimits": [
                    {"message": "what could not be seen", "affectedRuleIds": ["story-001"]}
                  ]
                }

                `rules`, `additionalChecks`, `visibilityLimits`, a takze `evidence`,
                `missingEvidence` i `signals` w kazdym wpisie sa wymaganymi tablicami.
                Dla `SATISFIED` wymagany jest co najmniej jeden evidence i impact
                `NONE`. Dla `NOT_SATISFIED` wymagane jest action. Dla
                `NOT_VERIFIED` wymagane sa missingEvidence oraz action.
                Kazdy visibility limit musi wskazywac istniejace rule id. Maksymalnie
                piec additionalChecks. Nie zwracaj tekstu poza obiektem JSON.
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
