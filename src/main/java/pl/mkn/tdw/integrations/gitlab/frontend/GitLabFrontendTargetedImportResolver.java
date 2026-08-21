package pl.mkn.tdw.integrations.gitlab.frontend;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GitLabFrontendTargetedImportResolver {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build();

    private final GitLabFrontendTargetedSourceSession session;
    private final List<PathAlias> aliases;
    private final Map<String, List<String>> resolutionCache = new LinkedHashMap<>();

    GitLabFrontendTargetedImportResolver(
            GitLabFrontendTargetedSourceSession session,
            String bootstrapSourcePath
    ) {
        this.session = session;
        this.aliases = loadAliases(bootstrapSourcePath);
    }

    List<String> resolve(String sourcePath, String importPath) {
        if (!StringUtils.hasText(importPath)) {
            return List.of();
        }
        var cacheKey = GitLabFrontendTargetedSourceSession.normalize(sourcePath) + "|" + importPath;
        var cached = resolutionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (!session.nextAliasResolution(sourcePath)) {
            return List.of();
        }
        var bases = new LinkedHashSet<String>();
        if (importPath.startsWith(".")) {
            bases.add(relative(parent(sourcePath), importPath));
        } else {
            var specificity = -1;
            for (var alias : aliases) {
                var targets = alias.resolve(importPath);
                if (targets.isEmpty()) {
                    continue;
                }
                if (alias.specificity() > specificity) {
                    bases.clear();
                    specificity = alias.specificity();
                }
                if (alias.specificity() == specificity) {
                    bases.addAll(targets);
                }
            }
        }
        for (var base : bases) {
            for (var candidate : candidates(base)) {
                if (session.readOptional(candidate) != null) {
                    var resolved = List.of(candidate);
                    resolutionCache.put(cacheKey, resolved);
                    return resolved;
                }
            }
        }
        var unresolved = List.<String>of();
        resolutionCache.put(cacheKey, unresolved);
        return unresolved;
    }

    private List<PathAlias> loadAliases(String bootstrapSourcePath) {
        var collected = new LinkedHashMap<String, PathAlias>();
        var visited = new LinkedHashSet<String>();
        for (var candidate : configurationCandidates(bootstrapSourcePath)) {
            loadConfiguration(candidate, visited, collected);
        }
        return List.copyOf(collected.values());
    }

    private void loadConfiguration(
            String configPath,
            Set<String> visited,
            LinkedHashMap<String, PathAlias> aliases
    ) {
        var normalized = GitLabFrontendTargetedSourceSession.normalize(configPath);
        if (!session.allows(normalized) || !visited.add(normalized)) {
            return;
        }
        var source = session.readOptional(normalized);
        if (source == null) {
            return;
        }
        try {
            var root = JSON_MAPPER.readTree(source);
            var extended = root.path("extends");
            if (extended.isTextual()) {
                for (var candidate : jsonCandidates(relative(parent(normalized), extended.asText()))) {
                    if (session.allows(candidate) && session.readOptional(candidate) != null) {
                        loadConfiguration(candidate, visited, aliases);
                        break;
                    }
                }
            }
            var compilerOptions = root.path("compilerOptions");
            var baseUrl = compilerOptions.path("baseUrl").isTextual()
                    ? compilerOptions.path("baseUrl").asText()
                    : ".";
            var mappingBase = relative(parent(normalized), baseUrl);
            var paths = compilerOptions.path("paths");
            if (!paths.isObject()) {
                return;
            }
            paths.fields().forEachRemaining(entry -> addAliases(entry, mappingBase, aliases));
        } catch (Exception exception) {
            session.diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    GitLabFrontendGraphDiagnosticCode.IMPORT_TARGET_NOT_FOUND,
                    "TypeScript path aliases could not be parsed from a targeted tsconfig.",
                    normalized
            );
        }
    }

    private void addAliases(
            java.util.Map.Entry<String, JsonNode> entry,
            String mappingBase,
            LinkedHashMap<String, PathAlias> aliases
    ) {
        if (!entry.getValue().isArray()) {
            return;
        }
        var targetIndex = 0;
        for (var target : entry.getValue()) {
            if (target.isTextual()) {
                var alias = new PathAlias(entry.getKey(), relative(mappingBase, target.asText()));
                aliases.put(entry.getKey() + "|" + targetIndex, alias);
            }
            targetIndex++;
        }
    }

    private List<String> configurationCandidates(String bootstrapSourcePath) {
        var result = new LinkedHashSet<String>();
        result.add("tsconfig.base.json");
        result.add("tsconfig.json");
        var directory = parent(bootstrapSourcePath);
        while (StringUtils.hasText(directory)) {
            result.add(directory + "/tsconfig.json");
            result.add(directory + "/tsconfig.app.json");
            directory = parent(directory);
        }
        return List.copyOf(result);
    }

    private List<String> candidates(String rawBase) {
        var base = GitLabFrontendTargetedSourceSession.normalize(rawBase);
        if (base.endsWith(".ts") || base.endsWith(".json")) {
            return List.of(base);
        }
        return List.of(
                base + ".ts",
                base + "/index.ts"
        );
    }

    private List<String> jsonCandidates(String base) {
        return base.endsWith(".json") ? List.of(base) : List.of(base, base + ".json");
    }

    private String relative(String base, String path) {
        if (!StringUtils.hasText(path)) {
            return GitLabFrontendTargetedSourceSession.normalize(base);
        }
        if (!path.startsWith(".")) {
            return GitLabFrontendTargetedSourceSession.normalize(
                    StringUtils.hasText(base) ? base + "/" + path : path
            );
        }
        return GitLabFrontendTargetedSourceSession.normalize(
                StringUtils.hasText(base) ? base + "/" + path : path
        );
    }

    private String parent(String path) {
        var normalized = GitLabFrontendTargetedSourceSession.normalize(path);
        var separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(0, separator) : "";
    }

    private record PathAlias(String pattern, String targetPattern) {

        private int specificity() {
            var wildcard = pattern.indexOf('*');
            return wildcard < 0 ? Integer.MAX_VALUE : pattern.length() - 1;
        }

        private List<String> resolve(String importPath) {
            var wildcard = pattern.indexOf('*');
            if (wildcard < 0) {
                return pattern.equals(importPath) ? List.of(targetPattern) : List.of();
            }
            var prefix = pattern.substring(0, wildcard);
            var suffix = pattern.substring(wildcard + 1);
            if (!importPath.startsWith(prefix) || !importPath.endsWith(suffix)
                    || importPath.length() < prefix.length() + suffix.length()) {
                return List.of();
            }
            var value = importPath.substring(prefix.length(), importPath.length() - suffix.length());
            var result = new LinkedHashSet<String>();
            result.add(targetPattern.replace("*", value));
            var targetWildcard = targetPattern.indexOf('*');
            if (targetWildcard >= 0 && value.contains("/src/")) {
                var targetSuffix = targetPattern.substring(targetWildcard + 1);
                if (targetSuffix.startsWith("/src/")) {
                    result.add(targetPattern.substring(0, targetWildcard) + value);
                }
            }
            return List.copyOf(result);
        }
    }
}
