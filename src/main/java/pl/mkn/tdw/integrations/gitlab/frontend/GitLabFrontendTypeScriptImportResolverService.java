package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabFrontendTypeScriptImportResolverService {

    private static final Pattern STATIC_IMPORT = Pattern.compile(
            "(?ms)^\\s*import\\s+(?!\\()(?:(?:type\\s+)?(.+?)\\s+from\\s+)?['\"]([^'\"]+)['\"]\\s*;?");

    private final GitLabRepositoryPort repositoryPort;

    public ResolvedImport resolve(
            GitLabFrontendRepositoryScope scope,
            String consumerFilePath,
            String moduleSpecifier,
            String importedSymbol
    ) {
        if (scope == null || !StringUtils.hasText(consumerFilePath)
                || !StringUtils.hasText(moduleSpecifier) || !StringUtils.hasText(importedSymbol)) {
            throw new IllegalArgumentException(
                    "Import TypeScript mode requires repository scope, consumerFilePath, moduleSpecifier and importedSymbol");
        }
        var session = new GitLabFrontendTargetedSourceSession(
                repositoryPort, scope, GitLabFrontendGraphLimits.defaults(), false);
        var consumer = session.readRequired(consumerFilePath);
        if (!StringUtils.hasText(consumer)) {
            throw new IllegalArgumentException("TypeScript import consumer could not be read from the pinned repository");
        }
        var targetPaths = new GitLabFrontendTargetedImportResolver(session, consumerFilePath)
                .resolve(consumerFilePath, moduleSpecifier.trim());
        if (targetPaths.isEmpty()) {
            throw new IllegalArgumentException("TypeScript import could not be resolved in the pinned repository");
        }
        return new ResolvedImport(
                targetPaths.get(0),
                exportedSymbol(consumer, moduleSpecifier.trim(), importedSymbol.trim())
        );
    }

    private String exportedSymbol(String source, String moduleSpecifier, String importedSymbol) {
        var matcher = STATIC_IMPORT.matcher(source);
        while (matcher.find()) {
            if (!moduleSpecifier.equals(matcher.group(2)) || !StringUtils.hasText(matcher.group(1))) {
                continue;
            }
            var clause = matcher.group(1).trim();
            var namedStart = clause.indexOf('{');
            var namedEnd = clause.lastIndexOf('}');
            if (namedStart >= 0 && namedEnd > namedStart) {
                for (var item : clause.substring(namedStart + 1, namedEnd).split(",")) {
                    var names = item.trim().replaceFirst("^type\\s+", "").split("\\s+as\\s+");
                    if (names.length == 1 && importedSymbol.equals(names[0].trim())) {
                        return names[0].trim();
                    }
                    if (names.length == 2 && importedSymbol.equals(names[1].trim())) {
                        return names[0].trim();
                    }
                }
            }
            var defaultImport = clause.split(",", 2)[0].trim();
            if (importedSymbol.equals(defaultImport)) {
                return importedSymbol;
            }
        }
        return importedSymbol;
    }

    public record ResolvedImport(String filePath, String declaringTypeName) {
    }
}
