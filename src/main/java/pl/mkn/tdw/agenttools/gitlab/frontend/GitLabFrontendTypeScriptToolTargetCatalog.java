package pl.mkn.tdw.agenttools.gitlab.frontend;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record GitLabFrontendTypeScriptToolTargetCatalog(
        Map<String, GitLabFrontendTypeScriptSliceTarget> directTargets,
        Map<String, GitLabFrontendTypeScriptImportTarget> importTargets
) {
    private static final Pattern STATIC_IMPORT = Pattern.compile(
            "(?ms)^\\s*import\\s+(?!\\()(?:(?:type\\s+)?(.+?)\\s+from\\s+)?['\"]([^'\"]+)['\"]\\s*;?");

    public GitLabFrontendTypeScriptToolTargetCatalog {
        directTargets = directTargets != null ? Map.copyOf(directTargets) : Map.of();
        importTargets = importTargets != null ? Map.copyOf(importTargets) : Map.of();
    }

    public static GitLabFrontendTypeScriptToolTargetCatalog from(GitLabFrontendScreenReachabilityGraph graph) {
        if (graph == null) return new GitLabFrontendTypeScriptToolTargetCatalog(Map.of(), Map.of());
        var direct = new LinkedHashMap<String, GitLabFrontendTypeScriptSliceTarget>();
        var ownerPaths = new LinkedHashMap<String, String>();
        var ownerContents = new LinkedHashMap<String, String>();
        graph.componentLevels().stream().flatMap(level -> level.components().stream())
                .filter(component -> StringUtils.hasText(component.sourcePath()))
                .forEach(component -> {
                    var target = new GitLabFrontendTypeScriptSliceTarget(
                            component.sourcePath(), component.symbol(), component.templatePath(),
                            component.entrySymbols().stream()
                                    .map(symbol -> new GitLabTypeScriptSymbolSelector(
                                            symbol.symbolName(), symbol.kind(), symbol.lineStart()))
                                    .toList()
                    );
                    direct.putIfAbsent(target.targetKey(), target);
                    ownerPaths.put(component.componentId(), component.sourcePath());
                    ownerContents.put(component.componentId(), component.sliceContent());
                });
        graph.dependencies().stream()
                .filter(dependency -> StringUtils.hasText(dependency.sourcePath()))
                .forEach(dependency -> {
                    var target = new GitLabFrontendTypeScriptSliceTarget(
                            dependency.sourcePath(), dependency.symbol(), null,
                            dependency.methods().stream().filter(StringUtils::hasText)
                                    .map(method -> new GitLabTypeScriptSymbolSelector(
                                            method, GitLabTypeScriptSymbolKind.AUTO, null))
                                    .toList()
                    );
                    direct.putIfAbsent(target.targetKey(), target);
                    ownerPaths.put(dependency.dependencyId(), dependency.sourcePath());
                    ownerContents.put(dependency.dependencyId(), dependency.sliceContent());
                });

        var imports = new LinkedHashMap<String, GitLabFrontendTypeScriptImportTarget>();
        graph.dependencies().stream()
                .filter(dependency -> StringUtils.hasText(dependency.sourcePath()))
                .filter(dependency -> StringUtils.hasText(dependency.moduleSpecifier()))
                .filter(dependency -> StringUtils.hasText(dependency.symbol()))
                .forEach(dependency -> {
                    var target = direct.get(GitLabFrontendTypeScriptSliceTarget.key(
                            dependency.sourcePath(), dependency.symbol()));
                    if (target == null) return;
                    dependency.usedBy().stream()
                            .filter(ownerPaths::containsKey)
                            .flatMap(ownerId -> {
                                var coordinates = importCoordinates(
                                        ownerContents.get(ownerId), dependency.symbol(), dependency.moduleSpecifier());
                                return coordinates.symbols().stream().map(symbol ->
                                        new GitLabFrontendTypeScriptImportTarget(
                                                ownerPaths.get(ownerId), coordinates.moduleSpecifier(), symbol, target));
                            })
                            .forEach(importTarget -> imports.putIfAbsent(importTarget.importKey(), importTarget));
                });
        return new GitLabFrontendTypeScriptToolTargetCatalog(direct, imports);
    }

    private static ImportCoordinates importCoordinates(String source, String symbol, String fallbackModule) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(symbol)) {
            return new ImportCoordinates(fallbackModule, List.of(symbol));
        }
        var symbolPattern = Pattern.compile("\\b" + Pattern.quote(symbol.trim()) + "\\b");
        var matcher = STATIC_IMPORT.matcher(source);
        while (matcher.find()) {
            var clause = matcher.group(1);
            if (StringUtils.hasText(clause) && symbolPattern.matcher(clause).find()) {
                var acceptedSymbols = new LinkedHashSet<String>();
                acceptedSymbols.add(symbol.trim());
                var namedStart = clause.indexOf('{');
                var namedEnd = clause.lastIndexOf('}');
                if (namedStart >= 0 && namedEnd > namedStart) {
                    for (var item : clause.substring(namedStart + 1, namedEnd).split(",")) {
                        var names = item.trim().replaceFirst("^type\\s+", "").split("\\s+as\\s+");
                        if (names.length > 0 && (symbol.equals(names[0].trim())
                                || names.length > 1 && symbol.equals(names[1].trim()))) {
                            for (var name : names) {
                                if (StringUtils.hasText(name)) acceptedSymbols.add(name.trim());
                            }
                        }
                    }
                }
                return new ImportCoordinates(matcher.group(2), List.copyOf(acceptedSymbols));
            }
        }
        return new ImportCoordinates(fallbackModule, List.of(symbol.trim()));
    }

    private record ImportCoordinates(String moduleSpecifier, List<String> symbols) {
    }
}
