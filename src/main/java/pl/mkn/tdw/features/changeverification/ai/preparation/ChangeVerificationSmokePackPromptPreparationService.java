package pl.mkn.tdw.features.changeverification.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
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
                - Nie proponuj zapisu do DB jako akcji wykonywanej przez AI. DB assertions sa tylko readonly checkami.
                - Cleanup moze byc endpointem aplikacyjnym albo manualSql jako fallback dla operatora.
                - Jezeli endpoint, request body albo cleanup sa niepewne, ustaw testowi `reviewStatus` = `NEEDS_REVIEW`.
                - Odpowiedz musi byc jednym obiektem JSON zgodnym z `change-verification/smoke-response-contract.md`.

                ## User request
                issueKey: %s
                issueUrl: %s
                modes: %s
                userInstructions:
                %s

                ## Prepared artifact contents
                %s
                """.formatted(
                value(sourceDiscovery != null ? sourceDiscovery.issueKey() : request.issueKey()),
                value(sourceDiscovery != null ? sourceDiscovery.issueUrl() : request.issueUrl()),
                request.modes(),
                StringUtils.hasText(request.userInstructions()) ? request.userInstructions().trim() : "(none)",
                artifactIndex(artifacts)
        ).trim();

        return new ChangeVerificationPromptPreparation(prompt, artifacts);
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

                ## Story Material
                %s

                ## Merge Requests
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
                bulletList(sourceDiscovery != null ? sourceDiscovery.limitations() : List.of())
        ).trim();
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
                """.formatted(
                value(issue.summary()),
                value(issue.description()),
                bulletList(issue.acceptanceCriteria())
        ).trim();
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
                      "dbAssertions": ["legacy readonly SQL/check description"],
                      "dbAssertionSpecs": [
                        {
                          "id": "db-001",
                          "sql": "select * from CUSTOMER where ID = '{{customerId}}'",
                          "operator": "EXISTS | NOT_EXISTS | ROW_COUNT_EQ | ROW_COUNT_GT | ROW_COUNT_GTE | ROW_COUNT_LT | ROW_COUNT_LTE",
                          "expectedValue": "1",
                          "description": "what persisted effect this verifies"
                        }
                      ],
                      "cleanup": {
                        "strategy": "NONE | ENDPOINT | MANUAL_SQL | NEEDS_REVIEW",
                        "method": "DELETE",
                        "path": "/api/example/{{id}}",
                        "requestBody": "",
                        "manualSql": "delete statement for operator, never executed by AI",
                        "hints": ["cleanup note"]
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
