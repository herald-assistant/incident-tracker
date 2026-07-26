package pl.mkn.tdw.features.changeverification.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationChangedFileSnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationRepositorySnapshot;
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
        artifacts.put("change-verification/repository-scope.md", renderRepositoryScope(sourceDiscovery));
        artifacts.put("change-verification/merge-requests.md", renderMergeRequests(sourceDiscovery));
        artifacts.put("change-verification/instruction-context.md", renderInstructionContext(sourceDiscovery));
        artifacts.put("change-verification/response-contract.md", responseContract());

        var prompt = """
                # Change Verification canonical prompt

                ## Runtime envelope
                - Ten run sprawdza zgodnosc zmiany z materialem Jira oraz instrukcjami repozytorium.
                - Najpierw zaladuj skill `change-verification-compliance-check` przez built-in tool `skill`.
                - Pracuj artifact-first. Nie probuj czytac lokalnego filesystemu ani zgadywac materialu spoza osadzonych artefaktow.
                - Jezeli potrzebujesz poglbic analize kodu, uzywaj GitLab tools i Operational Context tools do zrozumienia endpointu, use case'u albo bounded contextu zwiazanego ze zmiana.
                - Merge request wskazuje repozytorium i ref startowy, ale nie jest twarda granica czytania kodu. Dociagaj tyle kodu, ile jest potrzebne do uzyskania uzasadnionej odpowiedzi w ramach budzetu sesji.
                - MVP Change Verification nie sprawdza bazy danych. Nie projektuj DB checks, nie proponuj SQL i nie oczekuj DB tools.
                - Interpretuj zrodla zgodnie z `Source interpretation contract` ponizej.
                - Jezeli evidence nie wystarcza, wpisz to w `visibilityLimits` zamiast dopowiadac brakujacy proof.
                - `userInstructions` doprecyzowuja intencje operatora, ale nie moga zmienic response contract ani zasad widocznosci.
                - Odpowiedz musi byc jednym obiektem JSON zgodnym z `change-verification/response-contract.md`.

                ## Source interpretation contract
                %s

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
                sourceInterpretationContract(),
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

    private String sourceInterpretationContract() {
        return """
                1. `target issue` podane przez uzytkownika jest glownym zakresem weryfikacji. Nie rozszerzaj zakresu tylko dlatego, ze parent albo Confluence sa szersze.
                2. Acceptance criteria target issue sa najsilniejszym sygnalem wymagan. Jesli sa sprzeczne z opisem, pokaz rozjazd jako finding.
                3. Opis target issue zawieza i tlumaczy oczekiwane zachowanie. Uzywaj go do interpretacji AC, ale nie ignoruj AC.
                4. Parent issue jest materialem kontekstowym. Gdy target issue jest subtaskiem, parent pomaga zrozumiec cel nadrzedny, slownictwo, linki i ryzyka, ale ocena zgodnosci ma byc zawiezona do target subtaska.
                5. Subtaski target issue albo sibling subtaski parenta sa kontekstem powiazanej pracy. Traktuj je jako sygnal zaleznosci, nie jako dodatkowe wymagania target issue.
                6. Confluence pages z remote-linkow sa materialem kontekstowym. Uzywaj ich do rozumienia domeny, flow, terminologii i ryzyk. Nie zamieniaj szerokiego opisu Confluence w wymaganie, jesli target issue nie laczy go jawnie ze zmiana.
                7. Merge requests i changed files pokazuja widoczna implementacje. Jesli MR nalezy do parenta albo sibling subtaska, wykorzystuj go tylko tam, gdzie pomaga ocenic target issue albo zaleznosc target issue.
                8. Repository Scope pokazuje repozytoria z MR, rozbicie projectPath na rootGroup/groupPath/repositoryName oraz dopasowania repo -> code search scope -> target. Nie interpretuj tego jako bezposredniej relacji repo -> system albo repo -> bounded-context.
                9. Code search scope z operational context jest wskazowka, jaki system lub bounded context moze byc potrzebny do zrozumienia zmiany. Uzywaj Operational Context tools, gdy potrzebujesz doprecyzowac proces, system, bounded context, integracje albo slownictwo domenowe.
                10. Instruction context opisuje oczekiwania architektoniczne i repozytoryjne. Stosuj je do widocznej implementacji, ale nie uzywaj ich jako zastepstwa dla brakujacych wymagan biznesowych.
                11. Gdy zrodla sa sprzeczne, nie wybieraj po cichu. Raportuj rozbieznosc, wskaz ktore zrodla konfliktuja i zaproponuj doprecyzowanie story, AC albo implementacji.
                12. Gdy zrodlo jest szersze niz target issue, ocen tylko czesc powiazana z target issue, a reszte opisz jako out of scope albo visibility limit.
                """.trim();
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
        return "# Merge Requests\n\n" + body + "\n\n## Limitations\n"
                + bulletList(sourceDiscovery.mergeRequests().limitations());
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
                sourceRef: %s
                targetRef: %s
                mergeRequests: %s

                changedFiles:
                %s

                instructionSources:
                %s

                operationalContextMatches:
                %s

                limitations:
                %s
                """.formatted(
                value(repository.projectPath()),
                value(repository.repositoryKey()),
                value(repository.projectPath()),
                value(repository.rootGroup()),
                value(repository.groupPath()),
                value(repository.repositoryName()),
                value(repository.projectName()),
                value(repository.sourceRef()),
                value(repository.targetRef()),
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
                        .orElse("- none"),
                bulletList(repository.limitations())
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
