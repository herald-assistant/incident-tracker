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
    private static final String CONFLUENCE_PACKAGE = "pl.mkn.tdw.integrations.confluence";
    private static final String DYNATRACE_PACKAGE = "pl.mkn.tdw.integrations.dynatrace";
    private static final String JIRA_PACKAGE = "pl.mkn.tdw.integrations.jira";
    private static final Path CONFLUENCE_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/confluence");
    private static final Path DYNATRACE_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/dynatrace");
    private static final Path JIRA_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/jira");
    private static final Path SETTINGS_SERVICE = MAIN_JAVA.resolve(
            "pl/mkn/tdw/api/workspacesettings/WorkspaceSettingsService.java");
    private static final Pattern PUBLIC_PORT = Pattern.compile("\\bpublic\\s+interface\\s+\\w+Port\\b");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([^;]+);");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([^;]+);");

    @Test
    void confluenceRootContainsOnlyPublicPorts() throws IOException {
        assertRootContainsOnlyPublicPorts(CONFLUENCE_ROOT, CONFLUENCE_PACKAGE, "ConfluencePagePort.java");
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
    void dynatraceConsumersDependOnlyOnPortAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(DYNATRACE_PACKAGE,
                "DynatraceProperties", "DynatraceIncidentPort");
    }

    @Test
    void jiraConsumersDependOnlyOnPortsAndContract() throws IOException {
        assertConsumersDependOnlyOnPortAndContract(JIRA_PACKAGE,
                "JiraProperties", "JiraIssuePort", "JiraIssueSearchPort", "JiraIssueStatusHistoryPort");
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
                            || (file.equals(SETTINGS_SERVICE)
                                    && type.equals(integrationPackage + ".config." + settingsPropertiesName));
                    if (!allowed) {
                        violations.add(MAIN_JAVA.relativize(file) + " imports " + type);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Integration consumers import implementation details: " + violations);
    }
}
