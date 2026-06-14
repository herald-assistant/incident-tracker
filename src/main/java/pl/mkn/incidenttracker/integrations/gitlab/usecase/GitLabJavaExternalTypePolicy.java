package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class GitLabJavaExternalTypePolicy {

    private static final List<String> SKIP_SOURCE_LOOKUP_PREFIXES = List.of(
            "java.",
            "javax.",
            "jakarta.",
            "jdk.",
            "sun.",
            "com.sun.",
            "org.apache.",
            "org.hibernate.",
            "org.slf4j.",
            "ch.qos.logback.",
            "io.micrometer.",
            "reactor.",
            "kotlin.",
            "groovy.",
            "com.fasterxml.",
            "io.swagger.",
            "org.openapitools."
    );

    public GitLabJavaExternalTypeDecision classify(String typeName) {
        return classify(typeName, null);
    }

    public GitLabJavaExternalTypeDecision classify(
            String typeName,
            GitLabJavaAstFile context
    ) {
        var normalizedTypeName = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalizedTypeName)) {
            return localLookup(typeName, null, List.of(), "Type name is blank.");
        }

        if (context != null) {
            var imported = importedDecision(normalizedTypeName, context);
            if (imported != null) {
                return imported;
            }
        }

        return classifyQualified(normalizedTypeName, normalizedTypeName, List.of());
    }

    public GitLabJavaExternalTypeDecision localLookupMiss(String requestedName, String qualifiedName) {
        var normalizedQualifiedName = normalizeTypeName(qualifiedName);
        var normalizedRequestedName = normalizeTypeName(requestedName);
        if (looksLikeQualifiedName(normalizedQualifiedName)) {
            return new GitLabJavaExternalTypeDecision(
                    normalizedRequestedName,
                    normalizedQualifiedName,
                    GitLabJavaExternalTypeClassification.TERMINAL_BOUNDARY,
                    "INTERNAL_SHARED_LIBRARY_BOUNDARY",
                    "Type looks like internal/shared library class, but no matching source file was found in the selected repository tree.",
                    List.of()
            );
        }
        return localLookup(
                normalizedRequestedName,
                normalizedQualifiedName,
                List.of(),
                "Type should be looked up in repository sources first."
        );
    }

    private GitLabJavaExternalTypeDecision importedDecision(
            String typeName,
            GitLabJavaAstFile context
    ) {
        var matchingImports = context.imports().stream()
                .filter(importName -> !importName.endsWith(".*"))
                .filter(importName -> importMatches(importName, typeName))
                .toList();
        if (matchingImports.isEmpty()) {
            return null;
        }

        var importName = matchingImports.get(0);
        return classifyQualified(typeName, importName, matchingImports);
    }

    private GitLabJavaExternalTypeDecision classifyQualified(
            String requestedName,
            String qualifiedName,
            List<String> matchedImports
    ) {
        var normalizedQualifiedName = normalizeTypeName(qualifiedName);
        var simpleName = simpleName(normalizedQualifiedName);
        var semantic = semanticSignal(requestedName, normalizedQualifiedName, matchedImports);
        if (semantic != null) {
            return semantic;
        }

        if (startsWithAny(normalizedQualifiedName, SKIP_SOURCE_LOOKUP_PREFIXES)
                || normalizedQualifiedName.startsWith("org.springframework.")
                || normalizedQualifiedName.startsWith("lombok.")
                || normalizedQualifiedName.startsWith("org.mapstruct.")) {
            return new GitLabJavaExternalTypeDecision(
                    requestedName,
                    normalizedQualifiedName,
                    GitLabJavaExternalTypeClassification.SKIP_SOURCE_LOOKUP,
                    "FRAMEWORK_LIBRARY_TYPE",
                    "Framework/library type is outside repository source lookup scope: " + simpleName + ".",
                    matchedImports
            );
        }

        return localLookup(
                requestedName,
                normalizedQualifiedName,
                matchedImports,
                "Type should be looked up in repository sources first."
        );
    }

    private GitLabJavaExternalTypeDecision semanticSignal(
            String requestedName,
            String qualifiedName,
            List<String> matchedImports
    ) {
        var simpleName = simpleName(qualifiedName);
        return switch (simpleName) {
            case "RequiredArgsConstructor" -> isUnqualifiedOrStartsWith(qualifiedName, "lombok.") ? semantic(
                    requestedName,
                    qualifiedName,
                    "LOMBOK_REQUIRED_ARGS_CONSTRUCTOR",
                    "Lombok @RequiredArgsConstructor is a dependency-injection signal for final fields.",
                    matchedImports
            ) : null;
            case "Getter", "Data", "Value" -> isUnqualifiedOrStartsWith(qualifiedName, "lombok.") ? semantic(
                    requestedName,
                    qualifiedName,
                    "LOMBOK_ACCESSORS",
                    "Lombok accessor annotation is metadata; generated getters/setters are not source lookup targets.",
                    matchedImports
            ) : null;
            case "Mapper" -> isUnqualifiedOrStartsWith(qualifiedName, "org.mapstruct.") ? semantic(
                    requestedName,
                    qualifiedName,
                    "MAPSTRUCT_MAPPER",
                    "MapStruct @Mapper is a mapper signal; generated implementation is not a source lookup target.",
                    matchedImports
            ) : null;
            case "Transactional" -> isUnqualifiedOrStartsWith(qualifiedName, "org.springframework.") ? semantic(
                    requestedName,
                    qualifiedName,
                    "SPRING_TRANSACTION_METADATA",
                    "Spring @Transactional is endpoint flow metadata, not a source lookup target.",
                    matchedImports
            ) : null;
            case "EventListener" -> isUnqualifiedOrStartsWith(qualifiedName, "org.springframework.") ? semantic(
                    requestedName,
                    qualifiedName,
                    "SPRING_EVENT_BOUNDARY",
                    "Spring @EventListener marks an event boundary outside the synchronous endpoint traversal MVP.",
                    matchedImports
            ) : null;
            case "RolesAllowed" -> isUnqualifiedOrStartsWith(qualifiedName, "javax.", "jakarta.") ? semantic(
                    requestedName,
                    qualifiedName,
                    "SECURITY_METADATA",
                    "Security annotation is endpoint metadata, not a source lookup target.",
                    matchedImports
            ) : null;
            case "JpaRepository", "CrudRepository", "PagingAndSortingRepository" -> isUnqualifiedOrStartsWith(
                    qualifiedName,
                    "org.springframework.data."
            ) ? semantic(
                    requestedName,
                    qualifiedName,
                    "SPRING_DATA_REPOSITORY_BOUNDARY",
                    "Spring Data repository base interface is a terminal repository boundary.",
                    matchedImports
            ) : null;
            case "ResponseEntity" -> isUnqualifiedOrStartsWith(qualifiedName, "org.springframework.") ? semantic(
                    requestedName,
                    qualifiedName,
                    "SPRING_RESPONSE_WRAPPER",
                    "ResponseEntity is a response wrapper, not a source lookup target.",
                    matchedImports
            ) : null;
            case "Optional" -> isUnqualifiedOrStartsWith(qualifiedName, "java.util.") ? semantic(
                    requestedName,
                    qualifiedName,
                    "JAVA_OPTIONAL_WRAPPER",
                    "Optional is a value wrapper, not a source lookup target.",
                    matchedImports
            ) : null;
            case "FeignClient" -> isUnqualifiedOrStartsWith(
                    qualifiedName,
                    "org.springframework.cloud.openfeign."
            ) ? semantic(
                    requestedName,
                    qualifiedName,
                    "SPRING_FEIGN_EXTERNAL_CLIENT",
                    "Feign client marks an external integration boundary.",
                    matchedImports
            ) : null;
            default -> null;
        };
    }

    private GitLabJavaExternalTypeDecision semantic(
            String requestedName,
            String qualifiedName,
            String signal,
            String reason,
            List<String> matchedImports
    ) {
        return new GitLabJavaExternalTypeDecision(
                requestedName,
                qualifiedName,
                GitLabJavaExternalTypeClassification.SEMANTIC_SIGNAL,
                signal,
                reason,
                matchedImports
        );
    }

    private GitLabJavaExternalTypeDecision localLookup(
            String requestedName,
            String qualifiedName,
            List<String> matchedImports,
            String reason
    ) {
        return new GitLabJavaExternalTypeDecision(
                requestedName,
                qualifiedName,
                GitLabJavaExternalTypeClassification.LOCAL_LOOKUP_FIRST,
                "LOCAL_LOOKUP_FIRST",
                reason,
                matchedImports
        );
    }

    private boolean importMatches(String importName, String typeName) {
        return importName.equals(typeName)
                || importName.endsWith("." + typeName)
                || simpleName(importName).equals(simpleName(typeName));
    }

    private boolean startsWithAny(String value, List<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    private boolean isUnqualifiedOrStartsWith(String value, String... prefixes) {
        if (!StringUtils.hasText(value) || !value.contains(".")) {
            return true;
        }
        return List.of(prefixes).stream().anyMatch(value::startsWith);
    }

    private boolean looksLikeQualifiedName(String typeName) {
        if (!StringUtils.hasText(typeName) || !typeName.contains(".")) {
            return false;
        }
        var firstSegment = typeName.substring(0, typeName.indexOf('.'));
        return !firstSegment.isEmpty() && Character.isLowerCase(firstSegment.charAt(0));
    }

    private String simpleName(String typeName) {
        var normalized = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }

    private String normalizeTypeName(String typeName) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        if (normalized == null) {
            return null;
        }
        while (normalized.startsWith("? extends ")) {
            normalized = normalized.substring("? extends ".length()).trim();
        }
        while (normalized.startsWith("? super ")) {
            normalized = normalized.substring("? super ".length()).trim();
        }
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - ".class".length());
        }
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex).trim();
        }
        var whitespaceIndex = normalized.lastIndexOf(' ');
        if (whitespaceIndex >= 0) {
            normalized = normalized.substring(whitespaceIndex + 1).trim();
        }
        if (normalized.startsWith("class ")) {
            normalized = normalized.substring("class ".length()).trim();
        }
        return normalized.replace('$', '.');
    }
}
