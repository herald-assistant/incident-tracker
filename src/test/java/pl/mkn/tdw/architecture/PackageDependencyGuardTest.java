package pl.mkn.tdw.architecture;

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
    private static final Path TEST_JAVA = Path.of("src/test/java");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([^;]+);");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(?:static\\s+)?([^;]+);");

    @Test
    void shouldNotReintroduceClosedPackageDependencyEdges() throws IOException {
        var rules = List.of(
                Rule.closed("analysis.adapter must stay reusable and independent from evidence pipeline",
                        "pl.mkn.tdw.analysis.adapter",
                        "pl.mkn.tdw.analysis.evidence"),
                Rule.closed("analysis.adapter must stay reusable and independent from incident evidence",
                        "pl.mkn.tdw.analysis.adapter",
                        "pl.mkn.tdw.features.incidentanalysis.evidence"),
                Rule.closed("analysis.adapter must stay independent from MCP/tools exposure",
                        "pl.mkn.tdw.analysis.adapter",
                        "pl.mkn.tdw.analysis.mcp"),
                Rule.closed("analysis.adapter must stay independent from AI runtime",
                        "pl.mkn.tdw.analysis.adapter",
                        "pl.mkn.tdw.analysis.ai"),
                Rule.closed("analysis.adapter must stay below reusable agent tools",
                        "pl.mkn.tdw.analysis.adapter",
                        "pl.mkn.tdw.agenttools"),
                Rule.closed("integrations must stay independent from analysis application layers",
                        "pl.mkn.tdw.integrations",
                        "pl.mkn.tdw.analysis"),
                Rule.closed("integrations must stay independent from reusable agent tools",
                        "pl.mkn.tdw.integrations",
                        "pl.mkn.tdw.agenttools"),
                Rule.closed("integrations must stay independent from future feature packages",
                        "pl.mkn.tdw.integrations",
                        "pl.mkn.tdw.features"),
                Rule.closed("integrations must stay independent from future AI platform packages",
                        "pl.mkn.tdw.integrations",
                        "pl.mkn.tdw.aiplatform"),
                Rule.closed("integrations must stay independent from shared/operator API",
                        "pl.mkn.tdw.integrations",
                        "pl.mkn.tdw.api"),
                Rule.closed("analysis.mcp must not depend on AI/Copilot runtime",
                        "pl.mkn.tdw.analysis.mcp",
                        "pl.mkn.tdw.analysis.ai"),
                Rule.closed("analysis.ai must not depend on historical MCP packages",
                        "pl.mkn.tdw.analysis.ai",
                        "pl.mkn.tdw.analysis.mcp"),
                Rule.closed("analysis.ai contracts/runtime must not depend on feature packages",
                        "pl.mkn.tdw.analysis.ai",
                        "pl.mkn.tdw.features"),
                Rule.closed("incident flow must not depend on historical analysis AI contracts",
                        "pl.mkn.tdw.features.incidentanalysis.flow",
                        "pl.mkn.tdw.analysis.ai"),
                Rule.closed("incident job must not depend on historical analysis AI contracts",
                        "pl.mkn.tdw.features.incidentanalysis.job",
                        "pl.mkn.tdw.analysis.ai"),
                Rule.closed("incident job must not depend on historical analysis flow",
                        "pl.mkn.tdw.features.incidentanalysis.job",
                        "pl.mkn.tdw.analysis.flow"),
                Rule.closed("api must not depend on historical analysis flow",
                        "pl.mkn.tdw.api",
                        "pl.mkn.tdw.analysis.flow"),
                Rule.closed("api must not depend on historical analysis job",
                        "pl.mkn.tdw.api",
                        "pl.mkn.tdw.analysis.job"),
                Rule.closed("features must not depend on historical analysis AI contracts",
                        "pl.mkn.tdw.features",
                        "pl.mkn.tdw.analysis.ai"),
                Rule.closed("features must not depend on historical analysis flow",
                        "pl.mkn.tdw.features",
                        "pl.mkn.tdw.analysis.flow"),
                Rule.closed("features must not depend on historical analysis job",
                        "pl.mkn.tdw.features",
                        "pl.mkn.tdw.analysis.job"),
                Rule.closed("features must not depend on historical analysis evidence",
                        "pl.mkn.tdw.features",
                        "pl.mkn.tdw.analysis.evidence"),
                Rule.closed("features must not depend on historical analysis options",
                        "pl.mkn.tdw.features",
                        "pl.mkn.tdw.analysis.options"),
                Rule.closed("flow explorer must not depend on incident analysis internals",
                        "pl.mkn.tdw.features.flowexplorer",
                        "pl.mkn.tdw.features.incidentanalysis"),
                Rule.closed("incident analysis must not depend on flow explorer internals",
                        "pl.mkn.tdw.features.incidentanalysis",
                        "pl.mkn.tdw.features.flowexplorer"),
                Rule.closed("api must not depend on historical analysis evidence",
                        "pl.mkn.tdw.api",
                        "pl.mkn.tdw.analysis.evidence"),
                Rule.closed("api must not depend on historical analysis options",
                        "pl.mkn.tdw.api",
                        "pl.mkn.tdw.analysis.options"),
                Rule.closed("shared/operator API must stay independent from reusable agent tools",
                        "pl.mkn.tdw.api",
                        "pl.mkn.tdw.agenttools"),
                Rule.closed("shared/operator API must stay independent from feature internals",
                        "pl.mkn.tdw.api",
                        "pl.mkn.tdw.features"),
                Rule.closed("local workspace must stay independent from shared/operator API",
                        "pl.mkn.tdw.localworkspace",
                        "pl.mkn.tdw.api"),
                Rule.closed("local workspace must stay independent from feature packages",
                        "pl.mkn.tdw.localworkspace",
                        "pl.mkn.tdw.features"),
                Rule.closed("local workspace must stay independent from reusable agent tools",
                        "pl.mkn.tdw.localworkspace",
                        "pl.mkn.tdw.agenttools"),
                Rule.closed("local workspace must stay independent from AI platform runtime",
                        "pl.mkn.tdw.localworkspace",
                        "pl.mkn.tdw.aiplatform"),
                Rule.closed("local workspace must stay independent from integrations",
                        "pl.mkn.tdw.localworkspace",
                        "pl.mkn.tdw.integrations"),
                Rule.closed("local workspace must stay independent from historical analysis packages",
                        "pl.mkn.tdw.localworkspace",
                        "pl.mkn.tdw.analysis"),
                Rule.closed("incident evidence must publish shared evidence DTOs without importing historical AI",
                        "pl.mkn.tdw.features.incidentanalysis.evidence",
                        "pl.mkn.tdw.analysis.ai"),
                Rule.closed("incident evidence must stay independent from incident AI",
                        "pl.mkn.tdw.features.incidentanalysis.evidence",
                        "pl.mkn.tdw.features.incidentanalysis.ai"),
                Rule.closed("incident evidence must stay independent from incident flow",
                        "pl.mkn.tdw.features.incidentanalysis.evidence",
                        "pl.mkn.tdw.features.incidentanalysis.flow"),
                Rule.closed("incident evidence must stay independent from incident job",
                        "pl.mkn.tdw.features.incidentanalysis.evidence",
                        "pl.mkn.tdw.features.incidentanalysis.job"),
                Rule.closed("agenttools must stay reusable outside incident analysis",
                        "pl.mkn.tdw.agenttools",
                        "pl.mkn.tdw.analysis"),
                Rule.closed("agenttools must stay independent from future feature packages",
                        "pl.mkn.tdw.agenttools",
                        "pl.mkn.tdw.features"),
                Rule.closed("agenttools must stay independent from AI platform runtime",
                        "pl.mkn.tdw.agenttools",
                        "pl.mkn.tdw.aiplatform"),
                Rule.closed("aiplatform must stay independent from analysis application layers",
                        "pl.mkn.tdw.aiplatform",
                        "pl.mkn.tdw.analysis"),
                Rule.closed("aiplatform must stay independent from future feature packages",
                        "pl.mkn.tdw.aiplatform",
                        "pl.mkn.tdw.features"),
                Rule.closed("aiplatform must not import integrations directly",
                        "pl.mkn.tdw.aiplatform",
                        "pl.mkn.tdw.integrations"),
                Rule.closed("shared must stay below application layers",
                        "pl.mkn.tdw.shared",
                        "pl.mkn.tdw.analysis"),
                Rule.closed("shared must stay below agent tools",
                        "pl.mkn.tdw.shared",
                        "pl.mkn.tdw.agenttools"),
                Rule.closed("shared must stay below integrations",
                        "pl.mkn.tdw.shared",
                        "pl.mkn.tdw.integrations"),
                Rule.closed("shared must stay below AI platform",
                        "pl.mkn.tdw.shared",
                        "pl.mkn.tdw.aiplatform"),
                Rule.closed("shared must stay below feature packages",
                        "pl.mkn.tdw.shared",
                        "pl.mkn.tdw.features"),
                Rule.closed("common must stay below application layers",
                        "pl.mkn.tdw.common",
                        "pl.mkn.tdw.analysis"),
                Rule.closed("common must stay below integrations",
                        "pl.mkn.tdw.common",
                        "pl.mkn.tdw.integrations"),
                Rule.closed("common must stay below AI platform",
                        "pl.mkn.tdw.common",
                        "pl.mkn.tdw.aiplatform"),
                Rule.closed("common must stay below feature packages",
                        "pl.mkn.tdw.common",
                        "pl.mkn.tdw.features")
        );

        var violations = findViolations(rules);

        assertTrue(violations.isEmpty(), () -> "Closed package dependency edge(s) were reintroduced:\n"
                + String.join("\n", violations));
    }

    @Test
    void shouldNotRecreateClosedProductionPackages() throws IOException {
        var closedPackages = List.of(
                "pl.mkn.tdw.analysis"
        );

        var violations = findClosedPackageDeclarations(MAIN_JAVA, closedPackages);

        assertTrue(violations.isEmpty(), () -> "Closed production package(s) were recreated:\n"
                + String.join("\n", violations));
    }

    @Test
    void shouldNotRecreateClosedTestPackages() throws IOException {
        var closedPackages = List.of(
                "pl.mkn.tdw.analysis"
        );

        var violations = findClosedPackageDeclarations(TEST_JAVA, closedPackages);

        assertTrue(violations.isEmpty(), () -> "Closed test package(s) were recreated:\n"
                + String.join("\n", violations));
    }

    private static List<String> findClosedPackageDeclarations(Path sourceRoot, List<String> closedPackages) throws IOException {
        var violations = new ArrayList<String>();
        try (var files = Files.walk(sourceRoot)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var lines = Files.readAllLines(file);
                for (var i = 0; i < lines.size(); i++) {
                    var packageMatcher = PACKAGE_PATTERN.matcher(lines.get(i));
                    if (!packageMatcher.matches()) {
                        continue;
                    }

                    var sourcePackage = packageMatcher.group(1);
                    for (var closedPackage : closedPackages) {
                        if (Rule.startsWithPackage(sourcePackage, closedPackage)) {
                            violations.add("- " + sourceRoot.relativize(file).toString().replace('\\', '/')
                                    + ":" + (i + 1)
                                    + " declares closed package " + sourcePackage);
                        }
                    }
                }
            }
        }
        return violations;
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

        static boolean startsWithPackage(String value, String packageName) {
            return value.equals(packageName) || value.startsWith(packageName + ".");
        }
    }
}
