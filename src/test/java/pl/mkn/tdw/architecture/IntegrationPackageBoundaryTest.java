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
    private static final Path CONFLUENCE_ROOT = MAIN_JAVA.resolve("pl/mkn/tdw/integrations/confluence");
    private static final Path SETTINGS_SERVICE = MAIN_JAVA.resolve(
            "pl/mkn/tdw/api/workspacesettings/WorkspaceSettingsService.java");
    private static final Pattern PUBLIC_PORT = Pattern.compile("\\bpublic\\s+interface\\s+\\w+Port\\b");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([^;]+);");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([^;]+);");

    @Test
    void confluenceRootContainsOnlyPublicPorts() throws IOException {
        var violations = new ArrayList<String>();
        try (var files = Files.list(CONFLUENCE_ROOT)) {
            var rootTypes = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertEquals(List.of("ConfluencePagePort.java"), rootTypes.stream()
                    .map(path -> path.getFileName().toString()).toList());
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
                            && !imported.group(1).startsWith(CONFLUENCE_PACKAGE + ".contract.")) {
                        violations.add(file + " imports " + imported.group(1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Confluence root contains non-port types: " + violations);
    }

    @Test
    void confluenceConsumersDependOnlyOnPortAndContract() throws IOException {
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
                if (sourcePackage.equals(CONFLUENCE_PACKAGE)
                        || sourcePackage.startsWith(CONFLUENCE_PACKAGE + ".")) {
                    continue;
                }
                for (var line : lines) {
                    var imported = IMPORT.matcher(line);
                    if (!imported.matches() || !imported.group(1).startsWith(CONFLUENCE_PACKAGE + ".")) {
                        continue;
                    }
                    var type = imported.group(1);
                    var allowed = type.equals(CONFLUENCE_PACKAGE + ".ConfluencePagePort")
                            || type.startsWith(CONFLUENCE_PACKAGE + ".contract.")
                            || (file.equals(SETTINGS_SERVICE)
                                    && type.equals(CONFLUENCE_PACKAGE + ".config.ConfluenceProperties"));
                    if (!allowed) {
                        violations.add(MAIN_JAVA.relativize(file) + " imports " + type);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Confluence consumers import implementation details: " + violations);
    }
}
