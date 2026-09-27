package pl.mkn.tdw.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationPackageBoundaryTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path INTEGRATIONS_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations");
    private static final String CONFLUENCE_PACKAGE = "pl.mkn.tdw.integrations.confluence";
    private static final String DATABASE_PACKAGE = "pl.mkn.tdw.integrations.database";
    private static final String DYNATRACE_PACKAGE = "pl.mkn.tdw.integrations.dynatrace";
    private static final String ELASTICSEARCH_PACKAGE = "pl.mkn.tdw.integrations.elasticsearch";
    private static final String GITLAB_PACKAGE = "pl.mkn.tdw.integrations.gitlab";
    private static final String JIRA_PACKAGE = "pl.mkn.tdw.integrations.jira";
    private static final String OPERATIONAL_CONTEXT_PACKAGE = "pl.mkn.tdw.integrations.operationalcontext";
    private static final Path CONFLUENCE_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/confluence");
    private static final Path DATABASE_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/database");
    private static final Path DYNATRACE_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/dynatrace");
    private static final Path ELASTICSEARCH_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/elasticsearch");
    private static final Path GITLAB_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/gitlab");
    private static final Path JIRA_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/jira");
    private static final Path OPERATIONAL_CONTEXT_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/operationalcontext");
    private static final Path SETTINGS_SERVICE = MAIN_JAVA.resolve(
            "pl/mkn/tdw/api/workspacesettings/WorkspaceSettingsService.java");
    private static final Pattern PUBLIC_PORT = Pattern.compile("\\bpublic\\s+interface\\s+\\w+Port\\b");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([^;]+);");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([^;]+);");

    @Test
    void everyIntegrationSystemRootHasBoundaryCoverage() throws IOException {
        try (var entries = Files.list(INTEGRATIONS_ROOT)) {
            var directories = entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
            assertEquals(List.of("confluence", "database", "dynatrace", "elasticsearch",
                    "gitlab", "jira", "operationalcontext", "support"), directories);
        }
    }

    @Test
    void confluenceRootContainsOnlyPublicPorts() throws IOException {
        assertRootContainsOnlyPublicPorts(CONFLUENCE_ROOT, CONFLUENCE_PACKAGE, "ConfluencePagePort.java");
    }

    @Test
    void databaseRootContainsOnlyPublicPorts() throws IOException {
        assertRootContainsOnlyPublicPorts(DATABASE_ROOT, DATABASE_PACKAGE,
                "DatabaseDiagnosticPort.java", "DatabaseSqlPolicyPort.java");
    }

    @Test
    void dynatraceRootContainsOnlyPublicPorts() throws IOException {
        assertRootContainsOnlyPublicPorts(DYNATRACE_ROOT, DYNATRACE_PACKAGE, "DynatraceIncidentPort.java");
    }

    @Test
    void jiraRootContainsOnlyPublicPorts() throws IOException {
        assertRootContainsOnlyPublicPorts(JIRA_ROOT, JIRA_PACKAGE,
                "JiraIssuePort.java", "JiraIssueSearchPort.java", "JiraIssueStatusHistoryPort.java");
    }

    @Test
    void elasticsearchRootContainsOnlyPublicPorts() throws IOException {
        assertRootContainsOnlyPublicPorts(ELASTICSEARCH_ROOT, ELASTICSEARCH_PACKAGE,
                "ElasticConnectionAvailabilityPort.java", "ElasticLogCsvImportPort.java",
                "ElasticLogPort.java", "ElasticLogSearchPort.java");
    }

    @Test
    void operationalContextRootContainsOnlyPublicPorts() throws IOException {
        assertRootContainsOnlyPublicPorts(OPERATIONAL_CONTEXT_ROOT, OPERATIONAL_CONTEXT_PACKAGE,
                "OperationalContextCatalogMaintenancePort.java", "OperationalContextCatalogSearchPort.java",
                "OperationalContextCatalogValidationPort.java",
                "OperationalContextCodeSearchPort.java", "OperationalContextOwnershipPort.java",
                "OperationalContextPort.java", "OperationalContextReadModelValidationPort.java",
                "OperationalContextRelationIndexPort.java", "OperationalContextRepositoryPathPort.java",
                "OperationalContextSettingsPort.java");
    }

    @Test
    void gitLabRootContainsOnlyPublicPorts() throws IOException {
        assertRootContainsOnlyPublicPorts(GITLAB_ROOT, GITLAB_PACKAGE,
                "GitLabAngularRouteBranchSlicePort.java", "GitLabCodeSearchPort.java",
                "GitLabEndpointUseCaseContextPort.java", "GitLabExactRepositoryPort.java",
                "GitLabFrontendRouteGraphPort.java", "GitLabFrontendScreenReachabilityPort.java",
                "GitLabFrontendTypeScriptImportResolverPort.java", "GitLabInstructionContextPort.java",
                "GitLabInstructionRepositoryPort.java", "GitLabJavaMethodSlicePort.java",
                "GitLabJavaMethodUseCaseContextPort.java", "GitLabMergeRequestPort.java",
                "GitLabNamedConnectionPort.java", "GitLabOpenApiEndpointSlicePort.java",
                "GitLabProjectSearchPort.java", "GitLabRepositoryBranchPort.java",
                "GitLabRepositoryEndpointPort.java", "GitLabRepositoryPort.java",
                "GitLabRepositoryReadPort.java", "GitLabRepositoryRevisionPort.java",
                "GitLabRepositorySearchPort.java", "GitLabRepositoryTreeExplorerPort.java",
                "GitLabRepositoryTreePort.java", "GitLabSettingsPort.java",
                "GitLabSourceResolvePort.java", "GitLabSourceResolveSessionPort.java",
                "GitLabTypeScriptSymbolSlicePort.java", "GitLabVerifiedRepositoryFilePort.java");
    }

    @Test
    void gitLabNarrowPortsRequireExplicitImplementations() throws IOException {
        try (var files = Files.list(GITLAB_ROOT)) {
            for (var file : files.filter(path -> path.toString().endsWith("Port.java")).toList()) {
                if (file.getFileName().toString().equals("GitLabRepositoryPort.java")) {
                    continue; // Legacy composite port retains its compatibility defaults.
                }
                var body = Files.readString(file);
                assertTrue(!body.contains("default "),
                        () -> file + " contains a silent default operation");
            }
        }
    }

    private void assertRootContainsOnlyPublicPorts(
            Path root,
            String integrationPackage,
            String... expectedPorts
    ) throws IOException {
        var violations = new ArrayList<String>();
        try (var files = Files.list(root)) {
            var rootTypes = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertEquals(List.of(expectedPorts).stream().sorted().toList(), rootTypes.stream()
                    .map(path -> path.getFileName().toString()).sorted().toList());
            for (var file : rootTypes) {
                var content = Files.readString(file);
                if (!file.getFileName().toString().endsWith("Port.java")
                        || !PUBLIC_PORT.matcher(content).find()) {
                    violations.add(file.toString());
                }
                for (var line : content.lines().toList()) {
                    var imported = IMPORT.matcher(line);
                    if (imported.matches()
                            && !imported.group(1).startsWith("java.")
                            && !imported.group(1).startsWith(integrationPackage + ".contract.")) {
                        violations.add(file + " imports " + imported.group(1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Integration root contains non-port types: " + violations);
    }

    @Test
    void confluenceConsumersDependOnlyOnPortAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(CONFLUENCE_PACKAGE,
                "ConfluenceProperties", "ConfluencePagePort");
    }

    @Test
    void databaseConsumersDependOnlyOnPortsAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(DATABASE_PACKAGE,
                null, "DatabaseDiagnosticPort", "DatabaseSqlPolicyPort");
    }

    @Test
    void dynatraceConsumersDependOnlyOnPortAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(DYNATRACE_PACKAGE,
                "DynatraceProperties", "DynatraceIncidentPort");
    }

    @Test
    void jiraConsumersDependOnlyOnPortsAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(JIRA_PACKAGE,
                "JiraProperties", "JiraIssuePort", "JiraIssueSearchPort", "JiraIssueStatusHistoryPort");
    }

    @Test
    void elasticsearchConsumersDependOnlyOnPortsAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(ELASTICSEARCH_PACKAGE,
                "ElasticProperties", "ElasticConnectionAvailabilityPort", "ElasticLogCsvImportPort",
                "ElasticLogPort", "ElasticLogSearchPort");
    }

    @Test
    void operationalContextConsumersDependOnlyOnPortsAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(OPERATIONAL_CONTEXT_PACKAGE, null,
                "OperationalContextCatalogMaintenancePort", "OperationalContextCatalogSearchPort",
                "OperationalContextCatalogValidationPort",
                "OperationalContextCodeSearchPort", "OperationalContextOwnershipPort", "OperationalContextPort",
                "OperationalContextReadModelValidationPort", "OperationalContextRelationIndexPort",
                "OperationalContextRepositoryPathPort", "OperationalContextSettingsPort");
    }

    @Test
    void gitLabConsumersDependOnlyOnPortsAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(GITLAB_PACKAGE, null,
                "GitLabAngularRouteBranchSlicePort", "GitLabCodeSearchPort",
                "GitLabEndpointUseCaseContextPort", "GitLabExactRepositoryPort",
                "GitLabFrontendRouteGraphPort", "GitLabFrontendScreenReachabilityPort",
                "GitLabFrontendTypeScriptImportResolverPort", "GitLabInstructionContextPort",
                "GitLabInstructionRepositoryPort", "GitLabJavaMethodSlicePort",
                "GitLabJavaMethodUseCaseContextPort", "GitLabMergeRequestPort",
                "GitLabNamedConnectionPort", "GitLabOpenApiEndpointSlicePort",
                "GitLabProjectSearchPort", "GitLabRepositoryBranchPort",
                "GitLabRepositoryEndpointPort", "GitLabRepositoryPort",
                "GitLabRepositoryReadPort", "GitLabRepositoryRevisionPort",
                "GitLabRepositorySearchPort", "GitLabRepositoryTreeExplorerPort",
                "GitLabRepositoryTreePort", "GitLabSettingsPort", "GitLabSourceResolvePort",
                "GitLabSourceResolveSessionPort", "GitLabTypeScriptSymbolSlicePort",
                "GitLabVerifiedRepositoryFilePort");
    }

    private void assertConsumersDependOnlyOnPortAndContract(
            String integrationPackage,
            String settingsPropertiesName,
            String... portNames
    ) throws IOException {
        var violations = new ArrayList<String>();
        try (var files = Files.walk(MAIN_JAVA)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var lines = Files.readAllLines(file);
                var sourcePackage = lines.stream()
                        .map(PACKAGE::matcher)
                        .filter(java.util.regex.Matcher::matches)
                        .map(matcher -> matcher.group(1))
                        .findFirst()
                        .orElse("");
                if (sourcePackage.equals(integrationPackage)
                        || sourcePackage.startsWith(integrationPackage + ".")) {
                    continue;
                }
                for (var line : lines) {
                    var imported = IMPORT.matcher(line);
                    if (!imported.matches() || !imported.group(1).startsWith(integrationPackage + ".")) {
                        continue;
                    }
                    var type = imported.group(1);
                    var allowed = List.of(portNames).stream()
                            .anyMatch(portName -> type.equals(integrationPackage + "." + portName))
                            || type.startsWith(integrationPackage + ".contract.")
                            || (settingsPropertiesName != null && file.equals(SETTINGS_SERVICE)
                                    && type.equals(integrationPackage + ".config." + settingsPropertiesName))
                            || (integrationPackage.equals(GITLAB_PACKAGE) && file.equals(SETTINGS_SERVICE)
                                    && (type.equals(GITLAB_PACKAGE + ".config.GitLabProperties")
                                    || type.equals(GITLAB_PACKAGE + ".config.GitLabNamedConnectionsProperties")));
                    if (!allowed) {
                        violations.add(MAIN_JAVA.relativize(file) + " imports " + type);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Integration consumers import implementation details: " + violations);
    }
}
