package pl.mkn.incidenttracker.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageDependencyGuardTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([^;]+);");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(?:static\\s+)?([^;]+);");

    @Test
    void shouldNotReintroduceClosedPackageDependencyEdges() throws IOException {
        var rules = List.of(
                Rule.closed("analysis.adapter must stay reusable and independent from evidence pipeline",
                        "pl.mkn.incidenttracker.analysis.adapter",
                        "pl.mkn.incidenttracker.analysis.evidence"),
                Rule.closed("analysis.adapter must stay independent from MCP/tools exposure",
                        "pl.mkn.incidenttracker.analysis.adapter",
                        "pl.mkn.incidenttracker.analysis.mcp"),
                Rule.closed("analysis.adapter must stay independent from AI runtime",
                        "pl.mkn.incidenttracker.analysis.adapter",
                        "pl.mkn.incidenttracker.analysis.ai"),
                Rule.closed("analysis.adapter must stay below reusable agent tools",
                        "pl.mkn.incidenttracker.analysis.adapter",
                        "pl.mkn.incidenttracker.agenttools"),
                Rule.closed("integrations must stay independent from analysis application layers",
                        "pl.mkn.incidenttracker.integrations",
                        "pl.mkn.incidenttracker.analysis"),
                Rule.closed("integrations must stay independent from reusable agent tools",
                        "pl.mkn.incidenttracker.integrations",
                        "pl.mkn.incidenttracker.agenttools"),
                Rule.closed("integrations must stay independent from future feature packages",
                        "pl.mkn.incidenttracker.integrations",
                        "pl.mkn.incidenttracker.features"),
                Rule.closed("integrations must stay independent from future AI platform packages",
                        "pl.mkn.incidenttracker.integrations",
                        "pl.mkn.incidenttracker.aiplatform"),
                Rule.closed("analysis.mcp must not depend on AI/Copilot runtime",
                        "pl.mkn.incidenttracker.analysis.mcp",
                        "pl.mkn.incidenttracker.analysis.ai"),
                Rule.closed("analysis.ai must not depend on historical MCP packages",
                        "pl.mkn.incidenttracker.analysis.ai",
                        "pl.mkn.incidenttracker.analysis.mcp"),
                Rule.closed("analysis.evidence must publish shared evidence DTOs without importing AI",
                        "pl.mkn.incidenttracker.analysis.evidence",
                        "pl.mkn.incidenttracker.analysis.ai"),
                Rule.closed("agenttools must stay reusable outside incident analysis",
                        "pl.mkn.incidenttracker.agenttools",
                        "pl.mkn.incidenttracker.analysis"),
                Rule.closed("agenttools must stay independent from future feature packages",
                        "pl.mkn.incidenttracker.agenttools",
                        "pl.mkn.incidenttracker.features"),
                Rule.closed("agenttools must stay independent from AI platform runtime",
                        "pl.mkn.incidenttracker.agenttools",
                        "pl.mkn.incidenttracker.aiplatform"),
                Rule.closed("shared must stay below application layers",
                        "pl.mkn.incidenttracker.shared",
                        "pl.mkn.incidenttracker.analysis"),
                Rule.closed("shared must stay below agent tools",
                        "pl.mkn.incidenttracker.shared",
                        "pl.mkn.incidenttracker.agenttools"),
                Rule.closed("shared must stay below integrations",
                        "pl.mkn.incidenttracker.shared",
                        "pl.mkn.incidenttracker.integrations"),
                Rule.closed("common must stay below application layers",
                        "pl.mkn.incidenttracker.common",
                        "pl.mkn.incidenttracker.analysis"),
                Rule.closed("common must stay below integrations",
                        "pl.mkn.incidenttracker.common",
                        "pl.mkn.incidenttracker.integrations")
        );

        var violations = findViolations(rules);

        assertTrue(violations.isEmpty(), () -> "Closed package dependency edge(s) were reintroduced:\n"
                + String.join("\n", violations));
    }

    private static List<String> findViolations(List<Rule> rules) throws IOException {
        var violations = new ArrayList<String>();

        try (var files = Files.walk(MAIN_JAVA)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                scanFile(file, rules, violations);
            }
        }

        return violations;
    }

    private static void scanFile(Path file, List<Rule> rules, List<String> violations) throws IOException {
        String sourcePackage = null;
        var lines = Files.readAllLines(file);

        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var packageMatcher = PACKAGE_PATTERN.matcher(line);
            if (packageMatcher.matches()) {
                sourcePackage = packageMatcher.group(1);
                continue;
            }

            var importMatcher = IMPORT_PATTERN.matcher(line);
            if (sourcePackage == null || !importMatcher.matches()) {
                continue;
            }

            var importedType = importMatcher.group(1);
            for (var rule : rules) {
                if (rule.matches(sourcePackage, importedType)) {
                    violations.add(formatViolation(file, i + 1, sourcePackage, importedType, rule));
                }
            }
        }
    }

    private static String formatViolation(
            Path file,
            int line,
            String sourcePackage,
            String importedType,
            Rule rule
    ) {
        return "- " + MAIN_JAVA.relativize(file).toString().replace('\\', '/')
                + ":" + line
                + " package " + sourcePackage
                + " imports " + importedType
                + " (" + rule.reason() + ")";
    }

    private record Rule(String reason, String sourcePackage, String forbiddenPackage) {

        static Rule closed(String reason, String sourcePackage, String forbiddenPackage) {
            return new Rule(reason, sourcePackage, forbiddenPackage);
        }

        boolean matches(String sourcePackage, String importedType) {
            return startsWithPackage(sourcePackage, this.sourcePackage)
                    && startsWithPackage(importedType, forbiddenPackage);
        }

        private static boolean startsWithPackage(String value, String packageName) {
            return value.equals(packageName) || value.startsWith(packageName + ".");
        }
    }
}
