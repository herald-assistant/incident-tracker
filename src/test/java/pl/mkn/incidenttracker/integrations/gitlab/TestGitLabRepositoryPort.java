package pl.mkn.incidenttracker.integrations.gitlab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class TestGitLabRepositoryPort implements GitLabRepositoryPort {

    private static final Map<String, String> FILE_CONTENTS = Map.of(
            repositoryKey("crm-customer-api", "src/main/java/com/example/crm/customer/api/CustomerController.java"),
            """
                    package com.example.crm.customer.api;

                    import jakarta.validation.Valid;
                    import org.springframework.http.ResponseEntity;
                    import org.springframework.web.bind.annotation.GetMapping;
                    import org.springframework.web.bind.annotation.PathVariable;
                    import org.springframework.web.bind.annotation.PostMapping;
                    import org.springframework.web.bind.annotation.RequestBody;
                    import org.springframework.web.bind.annotation.RequestMapping;
                    import org.springframework.web.bind.annotation.RestController;

                    @RestController
                    @RequestMapping("/api/customers")
                    public class CustomerController {

                        @GetMapping("/{customerId}")
                        public ResponseEntity<OrderResponse> getCustomer(@PathVariable String customerId) {
                            return ResponseEntity.ok(new OrderResponse(customerId));
                        }

                        @PostMapping(path = "/")
                        public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
                            return new OrderResponse(request.customerId());
                        }
                    }
                    """,
            repositoryKey("crm-customer-api", "src/main/java/com/example/crm/customer/service/CustomerService.java"),
            """
                    package com.example.crm.customer.service;

                    public class CustomerService {
                    }
                    """,
            repositoryKey("crm-customer-client-service", "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java"),
            """
                    package com.example.synthetic.edge;

                    public class CustomerProfileClient {

                        public CustomerProfileResponse fetchCustomerProfile(String sku) {
                            return customerProfileWebClient.get()
                                    .uri("/customer-profile/{sku}", sku)
                                    .retrieve()
                                    .bodyToMono(CustomerProfileResponse.class)
                                    .timeout(Duration.ofSeconds(2))
                                    .block();
                        }

                    }
                    """,
            repositoryKey("crm-customer-account-service", "src/main/java/com/example/crm/customer/account/CustomerAccountService.java"),
            """
                    package com.example.crm.customer.account;

                    public class CustomerAccountService {

                        public void updateOrder(Customer customer) {
                            transactionTemplate.executeWithoutResult(status -> {
                                customerRepository.save(order);
                                auditRepository.save(new AuditEntry(order.id()));
                            });
                        }

                    }
                    """,
            repositoryKey("CRM_WORKFLOWS/CUSTOMER_WORKFLOW", "src/main/java/com/example/synthetic/workflow/CustomerWorkflowService.java"),
            """
                    package com.example.synthetic.workflow;

                    public class CustomerWorkflowService {

                        public void archiveInactiveProfiles() {
                            // synthetic test content for deterministic project resolution
                        }

                    }
                    """
    );

    @Override
    public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
        var candidates = new ArrayList<GitLabRepositoryProjectCandidate>();
        var hints = new LinkedHashSet<>(projectHints);

        if ("CRM".equals(group)
                && (hints.contains("crm-customer-workflow")
                || hints.contains("customer_workflow")
                || hints.contains("CUSTOMER_WORKFLOW")
                || hints.contains("crm-customer-workflow"))) {
            candidates.add(new GitLabRepositoryProjectCandidate(
                    group,
                    "CRM_WORKFLOWS/CUSTOMER_WORKFLOW",
                    "Matched CRM component hint customer_workflow in subgroup project.",
                    120
            ));
        }

        for (var hint : hints) {
            if ("crm-customer-client-service".equals(hint) || "crm-customer-account-service".equals(hint)) {
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

        if (projectNames.contains("crm-billing-service")
                || projectNames.contains("crm-customer-profile-service")
                || projectNames.contains("crm-customer-client-service")
                || keywords.contains("timeout")) {
            candidates.add(new GitLabRepositoryFileCandidate(
                    query.group(),
                    "crm-customer-client-service",
                    query.branch(),
                    "src/main/java/com/example/synthetic/edge/CustomerProfileClient.java",
                    "Matched timeout-related service and log keywords.",
                    95
            ));
        }

        if (projectNames.contains("crm-customer-account-service") || keywords.contains("deadlock")) {
            candidates.add(new GitLabRepositoryFileCandidate(
                    query.group(),
                    "crm-customer-account-service",
                    query.branch(),
                    "src/main/java/com/example/crm/customer/account/CustomerAccountService.java",
                    "Matched deadlock-related service and log keywords.",
                    93
            ));
        }

        if ((projectNames.contains("crm-customer-workflow")
                || projectNames.contains("customer_workflow")
                || projectNames.contains("CRM_WORKFLOWS/CUSTOMER_WORKFLOW"))
                && (keywords.contains("customer-profile") || keywords.contains("customer"))) {
            candidates.add(new GitLabRepositoryFileCandidate(
                    query.group(),
                    "CRM_WORKFLOWS/CUSTOMER_WORKFLOW",
                    query.branch(),
                    "src/main/java/com/example/synthetic/workflow/CustomerWorkflowService.java",
                    "Matched crm-customer-workflow component to CRM customer repository.",
                    96
            ));
        }

        return List.copyOf(candidates);
    }

    @Override
    public List<GitLabRepositoryFile> listRepositoryFiles(
            String group,
            String projectName,
            String branch,
            String pathPrefix
    ) {
        return FILE_CONTENTS.keySet().stream()
                .map(TestGitLabRepositoryPort::splitRepositoryKey)
                .filter(entry -> projectName.equals(entry.projectName()))
                .map(entry -> new GitLabRepositoryFile(group, projectName, branch, entry.filePath()))
                .filter(file -> pathPrefix == null || file.filePath().startsWith(pathPrefix))
                .toList();
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

    private static RepositoryFileKey splitRepositoryKey(String repositoryKey) {
        var separatorIndex = repositoryKey.indexOf("::");
        return new RepositoryFileKey(
                repositoryKey.substring(0, separatorIndex),
                repositoryKey.substring(separatorIndex + 2)
        );
    }

    private record RepositoryFileKey(
            String projectName,
            String filePath
    ) {
    }
}

