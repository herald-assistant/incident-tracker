package pl.mkn.tdw.features.changeverification.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationChangedFileSnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationRepositorySnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.jira.JiraIssueMaterial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChangeVerificationSmokePackPromptPreparationService {

    public ChangeVerificationPromptPreparation prepare(
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis
    ) {
        var artifacts = new LinkedHashMap<String, String>();
        artifacts.put("change-verification/smoke-source.md", renderSource(request, sourceDiscovery));
        artifacts.put("change-verification/smoke-compliance.md", renderCompliance(complianceAnalysis));
        artifacts.put("change-verification/smoke-response-contract.md", responseContract());

        var prompt = """
                # Change Verification smoke pack prompt

                ## Runtime envelope
                - Ten run projektuje edytowalny smoke pack dla zmiany.
                - Najpierw zaladuj skill `change-verification-smoke-pack-design` przez built-in tool `skill`.
                - Pracuj artifact-first. Nie probuj czytac lokalnego filesystemu ani wykonywac requestow.
                - Jezeli potrzebujesz poglbic analize endpointow albo payloadow, uzywaj GitLab tools tylko dla repozytoriow, refow i plikow z sekcji Repository Scope.
                - MVP Change Verification nie sprawdza bazy danych. Nie generuj DB assertions, SQL ani manualSql cleanup.
                - Cleanup moze byc endpointem aplikacyjnym albo instrukcja manualnej weryfikacji bez SQL.
                - Interpretuj zrodla zgodnie z `Source interpretation contract` ponizej.
                - Jezeli endpoint, request body albo cleanup sa niepewne, ustaw testowi `reviewStatus` = `NEEDS_REVIEW`.
                - Odpowiedz musi byc jednym obiektem JSON zgodnym z `change-verification/smoke-response-contract.md`.

                ## Source interpretation contract
                %s

                ## User request
                issueKey: %s
                issueUrl: %s
                modes: %s
                userInstructions:
                %s

                ## Prepared artifact contents
                %s
                """.formatted(
                sourceInterpretationContract(),
                value(sourceDiscovery != null ? sourceDiscovery.issueKey() : request.issueKey()),
                value(sourceDiscovery != null ? sourceDiscovery.issueUrl() : request.issueUrl()),
                request.modes(),
                StringUtils.hasText(request.userInstructions()) ? request.userInstructions().trim() : "(none)",
                artifactIndex(artifacts)
        ).trim();

        return new ChangeVerificationPromptPreparation(prompt, artifacts);
    }

    private String sourceInterpretationContract() {
        return """
                1. `target issue` podane przez uzytkownika jest glownym zakresem smoke packa. Projektuj testy pod najwazniejsze efekty target issue.
                2. Acceptance criteria target issue sa podstawowym materialem do wyboru ryzyk, asercji HTTP i oczekiwanego efektu.
                3. Opis target issue doprecyzowuje scenariusze i dane testowe, ale nie zastepuje acceptance criteria.
                4. Parent issue jest szerszym kontekstem. Gdy target issue jest subtaskiem, parent pomaga zrozumiec cel nadrzedny, ale nie oznacza, ze smoke pack ma pokryc wszystkie subtaski parenta.
                5. Sibling subtaski wykorzystuj tylko wtedy, gdy sa niezbedne do uruchomienia albo oceny target issue. W innych przypadkach nie projektuj dla nich osobnych testow.
                6. Confluence pages sa kontekstem domenowym i flow. Uzywaj ich do wyboru sensownego smoke scenariusza, ale testuj tylko fragment powiazany z target issue.
                7. Merge requests i changed files sa zrodlem endpointow, payloadow i widocznych zmian implementacji. Gdy MR pochodzi z parenta albo sibling subtaska, traktuj go jako kontekst zaleznosci.
                8. Jesli zrodla sa sprzeczne albo za szerokie, generuj mniejszy smoke pack dla target issue i wpisz pozostale ryzyka w `visibilityLimits` lub `suggestedActions`.
                """.trim();
    }

    private String renderSource(
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery
    ) {
        var issue = sourceDiscovery != null ? sourceDiscovery.jiraIssue() : null;
        var mergeRequests = sourceDiscovery != null && sourceDiscovery.mergeRequests() != null
                ? sourceDiscovery.mergeRequests().mergeRequests()
                : List.<GitLabMergeRequest>of();

        return """
                # Smoke Source

                issueKey: %s
                issueUrl: %s
                verificationScope: TARGET_ISSUE_FIRST

                ## Source Roles
                - targetIssue: glowny zakres smoke packa.
                - acceptanceCriteria: podstawowy material do wyboru ryzyk i oczekiwanych asercji.
                - parentContext: szerszy kontekst celu; nie rozszerza automatycznie smoke packa.
                - confluencePages: kontekst domenowy i flow; testuj tylko fragment powiazany z target issue.
                - mergeRequests: widoczna implementacja i potencjalne endpointy/payloady.

                ## Story Material
                %s

                ## Merge Requests
                %s

                ## Repository Scope
                %s

                ## Discovery Limitations
                %s
                """.formatted(
                value(sourceDiscovery != null ? sourceDiscovery.issueKey() : request.issueKey()),
                value(sourceDiscovery != null ? sourceDiscovery.issueUrl() : request.issueUrl()),
                renderIssue(issue),
                mergeRequests.stream()
                        .map(this::renderMergeRequest)
                        .reduce((left, right) -> left + "\n\n" + right)
                        .orElse("- none"),
                renderRepositoryScope(sourceDiscovery),
                bulletList(sourceDiscovery != null ? sourceDiscovery.limitations() : List.of())
        ).trim();
    }

    private String renderRepositoryScope(ChangeVerificationSourceDiscoveryResult sourceDiscovery) {
        if (sourceDiscovery == null || sourceDiscovery.repositories().isEmpty()) {
            return "- No repository scope discovered.";
        }
        return sourceDiscovery.repositories().stream()
                .map(this::renderRepository)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("- none");
    }

    private String renderRepository(ChangeVerificationRepositorySnapshot repository) {
        return """
                ### %s
                sourceRef: %s
                targetRef: %s
                changedFiles:
                %s
                instructionSources:
                %s
                limitations:
                %s
                """.formatted(
                value(repository.projectPath()),
                value(repository.sourceRef()),
                value(repository.targetRef()),
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
                bulletList(repository.limitations())
        ).trim();
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

    private String renderIssue(JiraIssueMaterial issue) {
        if (issue == null) {
            return "- Jira material unavailable.";
        }
        return """
                summary: %s
                description:
                %s

                acceptanceCriteria:
                %s

                parentContext:
                %s

                subtasks:
                %s

                confluencePages:
                %s
                """.formatted(
                value(issue.summary()),
                value(issue.description()),
                bulletList(issue.acceptanceCriteria()),
                renderParentIssue(issue.parentIssue()),
                issue.subTasks().stream()
                        .map(this::renderSubTask)
                        .reduce((left, right) -> left + "\n\n" + right)
                        .orElse("- none"),
                issue.confluencePages().stream()
                        .map(page -> "- %s | %s | %s\n  role: TARGET_CONTEXT_CONFLUENCE\n  interpretation: Kontekst target issue; testuj tylko fragment powiazany z target issue.\n  %s".formatted(
                                value(page.pageId()),
                                value(page.title()),
                                value(page.url()),
                                value(page.content())
                        ))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none")
        ).trim();
    }

    private String renderParentIssue(JiraIssueMaterial parentIssue) {
        if (parentIssue == null) {
            return "- none";
        }
        return """
                relation: PARENT_CONTEXT
                role: BROADER_CONTEXT
                interpretation: Uzywaj do zrozumienia celu nadrzednego i ryzyk. Nie projektuj smoke packa dla calego parenta, jesli target issue tego nie wymaga.
                issueKey: %s
                summary: %s
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
                value(parentIssue.summary()),
                value(parentIssue.description()),
                bulletList(parentIssue.acceptanceCriteria()),
                parentIssue.subTasks().stream()
                        .map(this::renderSubTask)
                        .reduce((left, right) -> left + "\n\n" + right)
                        .orElse("- none"),
                parentIssue.confluencePages().stream()
                        .map(page -> "- %s | %s | %s\n  role: PARENT_CONTEXT_CONFLUENCE\n  interpretation: Kontekst parenta; testuj tylko czesc powiazana z target issue.\n  %s".formatted(
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
        return "- %s | %s | %s\n  role: RELATED_SUBTASK_CONTEXT\n  interpretation: Kontekst powiazanej pracy; nie jest automatycznym zakresem smoke packa.\n  summary: %s\n  acceptanceCriteria:\n%s".formatted(
                value(subTask.issueKey()),
                value(subTask.issueType()),
                value(subTask.status()),
                value(subTask.summary()),
                bulletList(subTask.acceptanceCriteria())
        );
    }

    private String renderMergeRequest(GitLabMergeRequest mergeRequest) {
        return """
                ### %s
                projectPath: %s
                webUrl: %s
                sourceBranch: %s
                targetBranch: %s
                changedFiles:
                %s
                commits:
                %s
                limitations:
                %s
                """.formatted(
                value(mergeRequest.title()),
                value(mergeRequest.projectPath()),
                value(mergeRequest.webUrl()),
                value(mergeRequest.sourceBranch()),
                value(mergeRequest.targetBranch()),
                mergeRequest.changedFiles().stream()
                        .map(this::renderChangedFile)
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
                mergeRequest.commits().stream()
                        .map(commit -> "- %s %s".formatted(value(commit.shortId()), value(commit.title())))
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

    private String renderCompliance(ChangeVerificationComplianceAnalysis complianceAnalysis) {
        if (complianceAnalysis == null || complianceAnalysis.response() == null) {
            return "# Compliance Result\n\n- Compliance result unavailable or not requested.";
        }
        var response = complianceAnalysis.response();
        return """
                # Compliance Result

                status: %s
                confidence: %s

                findings:
                %s

                suggestedActions:
                %s

                visibilityLimits:
                %s
                """.formatted(
                value(response.status()),
                value(response.confidence()),
                response.findings().stream()
                        .map(this::renderFinding)
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("- none"),
                bulletList(response.suggestedActions()),
                bulletList(response.visibilityLimits())
        ).trim();
    }

    private String renderFinding(ChangeVerificationFindingResponse finding) {
        return "- [%s] %s: %s; action: %s".formatted(
                finding.severity(),
                value(finding.source()),
                value(finding.summary()),
                value(finding.suggestedAction())
        );
    }

    private String responseContract() {
        return """
                # Smoke Pack Response Contract

                Return exactly one JSON object:

                {
                  "requested": true,
                  "status": "READY | NEEDS_REVIEW | INCONCLUSIVE",
                  "postmanCollectionName": "CRM-123 smoke verification",
                  "tests": [
                    {
                      "id": "smoke-001",
                      "name": "short editable test name",
                      "method": "GET | POST | PUT | PATCH | DELETE",
                      "path": "/api/example",
                      "purpose": "what behavior this verifies",
                      "headers": [{"name": "Content-Type", "value": "application/json", "enabled": true}],
                      "queryParams": [{"name": "id", "value": "{{customerId}}", "enabled": true}],
                      "requestBody": "{\\"example\\": true}",
                      "responseAssertions": [
                        {"type": "STATUS", "target": "status", "operator": "EQUALS", "expectedValue": "200"},
                        {"type": "JSON_PATH", "target": "$.status", "operator": "EXISTS", "expectedValue": ""}
                      ],
                      "dbAssertions": [],
                      "dbAssertionSpecs": [],
                      "cleanup": {
                        "strategy": "NONE | ENDPOINT | NEEDS_REVIEW",
                        "method": "DELETE",
                        "path": "/api/example/{{id}}",
                        "requestBody": "",
                        "manualSql": null,
                        "hints": ["cleanup note without SQL"]
                      },
                      "cleanupHints": ["short cleanup fallback"],
                      "sourceRefs": ["change-verification/merge-requests.md"],
                      "riskCovered": "risk or acceptance criterion covered",
                      "reviewStatus": "READY | NEEDS_REVIEW"
                    }
                  ],
                  "visibilityLimits": ["what could not be inferred"],
                  "suggestedActions": ["what operator should review before running"],
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
