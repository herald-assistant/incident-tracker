package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class TestGitLabRepositoryPort implements GitLabRepositoryPort {

    private static final Map<String, String> FILE_CONTENTS = Map.of(
            repositoryKey("edge-client-service", "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java"),
            """
                    package com.example.synthetic.edge;

                    public class CatalogGatewayClient {

                        public CatalogResponse fetchCatalog(String sku) {
                            return catalogWebClient.get()
                                    .uri("/catalog/{sku}", sku)
                                    .retrieve()
                                    .bodyToMono(CatalogResponse.class)
                                    .timeout(Duration.ofSeconds(2))
                                    .block();
                        }

                    }
                    """,
            repositoryKey("ledger-write-service", "src/main/java/com/example/synthetic/ledger/LedgerTransactionService.java"),
            """
                    package com.example.synthetic.ledger;

                    public class LedgerTransactionService {

                        public void updateOrder(Order order) {
                            transactionTemplate.executeWithoutResult(status -> {
                                orderRepository.save(order);
                                auditRepository.save(new AuditEntry(order.id()));
                            });
                        }

                    }
                    """,
            repositoryKey("WORKFLOWS/DOCUMENT_WORKFLOW", "src/main/java/com/example/synthetic/workflow/DocumentWorkflowService.java"),
            """
                    package com.example.synthetic.workflow;

                    public class DocumentWorkflowService {

                        public void removeDocuments() {
                            // synthetic test content for deterministic project resolution
                        }

                    }
                    """
    );

    @Override
    public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
        var candidates = new ArrayList<GitLabRepositoryProjectCandidate>();
        var hints = new LinkedHashSet<>(projectHints);

        if ("TENANT-ALPHA".equals(group)
                && (hints.contains("document-workflow")
                || hints.contains("document_workflow")
                || hints.contains("DOCUMENT_WORKFLOW")
                || hints.contains("document-workflow"))) {
            candidates.add(new GitLabRepositoryProjectCandidate(
                    group,
                    "WORKFLOWS/DOCUMENT_WORKFLOW",
                    "Matched TENANT-ALPHA component hint document_workflow in subgroup project.",
                    120
            ));
        }

        for (var hint : hints) {
            if ("edge-client-service".equals(hint) || "ledger-write-service".equals(hint)) {
                candidates.add(new GitLabRepositoryProjectCandidate(
                        group,
                        hint,
                        "Matched direct test project name.",
                        100
                ));
            }
        }

        return List.copyOf(candidates);
    }

    @Override
    public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
        if ("not-found".equals(query.correlationId())) {
            return List.of();
        }

        var candidates = new ArrayList<GitLabRepositoryFileCandidate>();
        var projectNames = new LinkedHashSet<>(query.projectNames());
        var keywords = query.keywords();

        if (projectNames.contains("billing-service")
                || projectNames.contains("catalog-service")
                || projectNames.contains("edge-client-service")
                || keywords.contains("timeout")) {
            candidates.add(new GitLabRepositoryFileCandidate(
                    query.group(),
                    "edge-client-service",
                    query.branch(),
                    "src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java",
                    "Matched timeout-related service and log keywords.",
                    95
            ));
        }

        if (projectNames.contains("ledger-write-service") || keywords.contains("deadlock")) {
            candidates.add(new GitLabRepositoryFileCandidate(
                    query.group(),
                    "ledger-write-service",
                    query.branch(),
                    "src/main/java/com/example/synthetic/ledger/LedgerTransactionService.java",
                    "Matched deadlock-related service and log keywords.",
                    93
            ));
        }

        if ((projectNames.contains("document-workflow")
                || projectNames.contains("document_workflow")
                || projectNames.contains("WORKFLOWS/DOCUMENT_WORKFLOW"))
                && (keywords.contains("document") || keywords.contains("agreement"))) {
            candidates.add(new GitLabRepositoryFileCandidate(
                    query.group(),
                    "WORKFLOWS/DOCUMENT_WORKFLOW",
                    query.branch(),
                    "src/main/java/com/example/synthetic/workflow/DocumentWorkflowService.java",
                    "Matched document-workflow component to TENANT-ALPHA agreement repository.",
                    96
            ));
        }

        return List.copyOf(candidates);
    }

    @Override
    public GitLabRepositoryFileContent readFile(
            String group,
            String projectName,
            String branch,
            String filePath,
            int maxCharacters
    ) {
        var content = resolveContent(projectName, filePath);
        var safeLimit = safeCharacterLimit(maxCharacters);
        var truncated = content.length() > safeLimit;
        var limitedContent = truncated ? content.substring(0, safeLimit) : content;

        return new GitLabRepositoryFileContent(group, projectName, branch, filePath, limitedContent, truncated);
    }

    @Override
    public GitLabRepositoryFileChunk readFileChunk(
            String group,
            String projectName,
            String branch,
            String filePath,
            int startLine,
            int endLine,
            int maxCharacters
    ) {
        var content = resolveContent(projectName, filePath);
        var lines = content.lines().toList();
        var totalLines = lines.size();
        var requestedStartLine = Math.max(1, startLine);
        var requestedEndLine = Math.max(requestedStartLine, endLine);

        if (totalLines == 0 || requestedStartLine > totalLines) {
            return new GitLabRepositoryFileChunk(
                    group,
                    projectName,
                    branch,
                    filePath,
                    requestedStartLine,
                    requestedEndLine,
                    0,
                    0,
                    totalLines,
                    "",
                    false
            );
        }

        var returnedStartLine = requestedStartLine;
        var returnedEndLine = Math.min(requestedEndLine, totalLines);
        var chunkContent = String.join("\n", lines.subList(returnedStartLine - 1, returnedEndLine));
        var safeLimit = safeCharacterLimit(maxCharacters);
        var truncated = chunkContent.length() > safeLimit;
        var limitedContent = truncated ? chunkContent.substring(0, safeLimit) : chunkContent;

        return new GitLabRepositoryFileChunk(
                group,
                projectName,
                branch,
                filePath,
                requestedStartLine,
                requestedEndLine,
                returnedStartLine,
                returnedEndLine,
                totalLines,
                limitedContent,
                truncated
        );
    }

    private static String resolveContent(String projectName, String filePath) {
        return FILE_CONTENTS.getOrDefault(
                repositoryKey(projectName, filePath),
                "// No test content configured for " + projectName + " :: " + filePath
        );
    }

    private static int safeCharacterLimit(int maxCharacters) {
        return maxCharacters > 0 ? maxCharacters : 4_000;
    }

    private static String repositoryKey(String projectName, String filePath) {
        return projectName + "::" + filePath;
    }
}

