package pl.mkn.tdw.integrations.gitlab.usecase;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@Component
public class GitLabJavaSourceResolver {

    private final GitLabJavaExternalTypePolicy externalTypePolicy;

    public GitLabJavaSourceResolver(GitLabJavaExternalTypePolicy externalTypePolicy) {
        this.externalTypePolicy = externalTypePolicy;
    }

    GitLabJavaSourceResolver() {
        this(new GitLabJavaExternalTypePolicy());
    }

    public GitLabJavaAstFile astFile(
            GitLabEndpointUseCaseSourceSession session,
            String filePath
    ) {
        var parsedSource = session.parseJava(filePath);
        var sourceFile = parsedSource.sourceFile();
        if (!parsedSource.hasCompilationUnit()) {
            return new GitLabJavaAstFile(
                    sourceFile != null ? sourceFile.path() : filePath,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    parsedSource.limitations()
            );
        }

        var compilationUnit = parsedSource.compilationUnit();
        var packageName = compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getName().asString())
                .orElse(null);
        var imports = compilationUnit.getImports().stream()
                .filter(importDeclaration -> !importDeclaration.isStatic())
                .map(importDeclaration -> importDeclaration.isAsterisk()
                        ? importDeclaration.getName().asString() + ".*"
                        : importDeclaration.getName().asString())
                .toList();
        var staticImports = compilationUnit.getImports().stream()
                .filter(importDeclaration -> importDeclaration.isStatic())
                .map(importDeclaration -> importDeclaration.isAsterisk()
                        ? importDeclaration.getName().asString() + ".*"
                        : importDeclaration.getName().asString())
                .toList();

        @SuppressWarnings("rawtypes")
        List<TypeDeclaration> declarations = compilationUnit.findAll(TypeDeclaration.class);
        var types = declarations.stream()
                .map(declaration -> toTypeDeclaration((TypeDeclaration<?>) declaration, sourceFile.path(), packageName))
                .toList();

        return new GitLabJavaAstFile(
                sourceFile.path(),
                packageName,
                imports,
                staticImports,
                types,
                compilationUnit,
                parsedSource.limitations()
        );
    }

    public GitLabJavaResolvedType resolveType(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaAstFile context,
            String typeName
    ) {
        var normalizedTypeName = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalizedTypeName)) {
            return unresolved(typeName, List.of(), "Type name is required.");
        }
        if (context == null || !context.parsed()) {
            return new GitLabJavaResolvedType(
                    normalizedTypeName,
                    GitLabJavaTypeResolutionKind.PARSE_FAILED,
                    GitLabEndpointUseCaseConfidence.LOW,
                    null,
                    null,
                    null,
                    List.of(),
                    context != null ? context.limitations() : List.of("Context Java source was not parsed.")
            );
        }

        var sameFileType = findType(context, normalizedTypeName);
        if (sameFileType != null) {
            return resolved(
                    normalizedTypeName,
                    sameFileType.relativeName() != null && sameFileType.relativeName().contains(".")
                            ? GitLabJavaTypeResolutionKind.NESTED_TYPE
                            : GitLabJavaTypeResolutionKind.SAME_FILE,
                    GitLabEndpointUseCaseConfidence.HIGH,
                    sameFileType
            );
        }

        var externalTypeDecision = externalTypePolicy.classify(normalizedTypeName, context);
        if (!externalTypeDecision.sourceLookupAllowed()) {
            return externalBoundary(normalizedTypeName, externalTypeDecision);
        }

        var exactImportResolution = resolveExactImport(session, context, normalizedTypeName);
        if (exactImportResolution != null) {
            return exactImportResolution;
        }

        var samePackageResolution = resolveSamePackage(session, context, normalizedTypeName);
        if (samePackageResolution != null) {
            return samePackageResolution;
        }

        return resolveByRepositoryTree(session, context, normalizedTypeName);
    }

    private GitLabJavaResolvedType resolveExactImport(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaAstFile context,
            String typeName
    ) {
        var matchingImports = context.imports().stream()
                .filter(importName -> !importName.endsWith(".*"))
                .filter(importName -> matchesImport(importName, typeName))
                .toList();
        if (matchingImports.isEmpty()) {
            return null;
        }

        var candidates = matchingImports.stream()
                .map(importName -> importedTypePath(session, importName, typeName))
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
        if (candidates.size() > 1) {
            return ambiguous(typeName, candidates, "Exact imports resolved to more than one candidate file.");
        }
        if (candidates.isEmpty()) {
            var treeResolution = resolveExactImportByRepositoryTree(session, typeName, matchingImports);
            if (treeResolution != null) {
                return treeResolution;
            }
            var boundary = externalTypePolicy.localLookupMiss(typeName, matchingImports.get(0));
            if (boundary.classification() == GitLabJavaExternalTypeClassification.TERMINAL_BOUNDARY) {
                return externalBoundary(typeName, boundary);
            }
            return unresolved(typeName, matchingImports, "Exact import did not match a source file in repository tree.");
        }

        return resolveFromFile(
                session,
                candidates.get(0),
                typeName,
                GitLabJavaTypeResolutionKind.EXACT_IMPORT,
                GitLabEndpointUseCaseConfidence.HIGH,
                List.of("Exact import matched " + matchingImports.get(0) + ".")
        );
    }

    private GitLabJavaResolvedType resolveExactImportByRepositoryTree(
            GitLabEndpointUseCaseSourceSession session,
            String requestedTypeName,
            List<String> matchingImports
    ) {
        var resolvedPaths = new ArrayList<String>();
        var reasons = new ArrayList<String>();
        for (var importName : matchingImports) {
            var topLevelName = topLevelTypeName(importName, null);
            var suffix = "/" + topLevelName + ".java";
            var candidatePaths = session.listRepositoryFiles().stream()
                    .map(GitLabRepositoryFile::filePath)
                    .map(GitLabEndpointUseCaseModelSupport::normalizeFilePath)
                    .filter(path -> path != null && (path.endsWith(suffix) || path.equals(topLevelName + ".java")))
                    .distinct()
                    .sorted()
                    .toList();

            for (var candidatePath : candidatePaths) {
                var astFile = astFile(session, candidatePath);
                if (!astFile.parsed()) {
                    continue;
                }
                var importedType = findExactQualifiedType(astFile, importName);
                var requestedType = findType(astFile, requestedTypeName);
                if (importedType != null && requestedType != null) {
                    resolvedPaths.add(candidatePath);
                    reasons.add("Exact import " + importName + " matched repository tree file " + candidatePath + ".");
                }
            }
        }

        var distinctPaths = resolvedPaths.stream().distinct().sorted().toList();
        if (distinctPaths.isEmpty()) {
            return null;
        }
        if (distinctPaths.size() > 1) {
            return ambiguous(
                    requestedTypeName,
                    distinctPaths,
                    "Exact import matched more than one source file by repository tree validation."
            );
        }
        return resolveFromFile(
                session,
                distinctPaths.get(0),
                requestedTypeName,
                GitLabJavaTypeResolutionKind.EXACT_IMPORT,
                GitLabEndpointUseCaseConfidence.HIGH,
                reasons.stream().distinct().toList()
        );
    }

    private GitLabJavaResolvedType resolveSamePackage(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaAstFile context,
            String typeName
    ) {
        if (!StringUtils.hasText(context.packageName())) {
            return null;
        }
        if (looksLikeQualifiedName(typeName) && !typeName.startsWith(context.packageName() + ".")) {
            return null;
        }

        var packagePath = context.packageName().replace('.', '/');
        var topLevelName = topLevelTypeName(typeName, context.packageName());
        var suffix = "/" + packagePath + "/" + topLevelName + ".java";
        var candidates = session.listRepositoryFiles().stream()
                .map(GitLabRepositoryFile::filePath)
                .map(GitLabEndpointUseCaseModelSupport::normalizeFilePath)
                .filter(path -> path != null && (path.endsWith(suffix) || path.equals(packagePath + "/" + topLevelName + ".java")))
                .filter(path -> !path.equals(context.path()))
                .distinct()
                .sorted()
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            return ambiguous(typeName, candidates, "More than one same-package source file matched " + topLevelName + ".java.");
        }

        return resolveFromFile(
                session,
                candidates.get(0),
                typeName,
                GitLabJavaTypeResolutionKind.SAME_PACKAGE,
                GitLabEndpointUseCaseConfidence.MEDIUM,
                List.of("Type was resolved from the same Java package without explicit import.")
        );
    }

    private GitLabJavaResolvedType resolveByRepositoryTree(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaAstFile context,
            String typeName
    ) {
        var topLevelName = topLevelTypeName(typeName, context.packageName());
        var suffix = "/" + topLevelName + ".java";
        var candidates = session.listRepositoryFiles().stream()
                .map(GitLabRepositoryFile::filePath)
                .map(GitLabEndpointUseCaseModelSupport::normalizeFilePath)
                .filter(path -> path != null && (path.endsWith(suffix) || path.equals(topLevelName + ".java")))
                .distinct()
                .sorted()
                .toList();

        if (candidates.isEmpty()) {
            var boundary = externalTypePolicy.localLookupMiss(typeName, typeName);
            if (boundary.classification() == GitLabJavaExternalTypeClassification.TERMINAL_BOUNDARY) {
                return externalBoundary(typeName, boundary);
            }
            return unresolved(typeName, List.of(), "No source file named " + topLevelName + ".java was found in repository tree.");
        }
        if (candidates.size() > 1) {
            return ambiguous(typeName, candidates, "More than one source file matched " + topLevelName + ".java.");
        }

        return resolveFromFile(
                session,
                candidates.get(0),
                typeName,
                GitLabJavaTypeResolutionKind.TREE_LOOKUP,
                GitLabEndpointUseCaseConfidence.MEDIUM,
                List.of("Type was resolved by repository tree lookup.")
        );
    }

    private GitLabJavaResolvedType resolveFromFile(
            GitLabEndpointUseCaseSourceSession session,
            String filePath,
            String typeName,
            GitLabJavaTypeResolutionKind kind,
            GitLabEndpointUseCaseConfidence confidence,
            List<String> reasons
    ) {
        var astFile = astFile(session, filePath);
        if (!astFile.parsed()) {
            return new GitLabJavaResolvedType(
                    typeName,
                    GitLabJavaTypeResolutionKind.PARSE_FAILED,
                    GitLabEndpointUseCaseConfidence.LOW,
                    null,
                    filePath,
                    null,
                    List.of(filePath),
                    astFile.limitations()
            );
        }

        var type = findType(astFile, typeName);
        if (type == null) {
            var limitations = new ArrayList<String>(reasons);
            limitations.add("Source file was found, but AST did not contain requested type " + typeName + ".");
            return new GitLabJavaResolvedType(
                    typeName,
                    GitLabJavaTypeResolutionKind.UNRESOLVED,
                    GitLabEndpointUseCaseConfidence.LOW,
                    null,
                    filePath,
                    null,
                    List.of(filePath),
                    limitations
            );
        }

        return resolved(typeName, kind, confidence, type);
    }

    private GitLabJavaResolvedType externalBoundary(
            String requestedName,
            GitLabJavaExternalTypeDecision decision
    ) {
        return new GitLabJavaResolvedType(
                requestedName,
                GitLabJavaTypeResolutionKind.EXTERNAL_BOUNDARY,
                decision.classification() == GitLabJavaExternalTypeClassification.SEMANTIC_SIGNAL
                        ? GitLabEndpointUseCaseConfidence.MEDIUM
                        : GitLabEndpointUseCaseConfidence.LOW,
                null,
                null,
                decision.qualifiedName(),
                decision.matchedImports(),
                List.of(decision.reason())
        );
    }

    private GitLabJavaTypeDeclaration findType(GitLabJavaAstFile astFile, String typeName) {
        var normalizedTypeName = normalizeTypeName(typeName);
        var simpleName = simpleName(normalizedTypeName);
        return astFile.types().stream()
                .filter(type -> normalizedTypeName.equals(type.qualifiedName())
                        || normalizedTypeName.equals(type.relativeName())
                        || normalizedTypeName.equals(type.simpleName())
                        || (StringUtils.hasText(type.qualifiedName())
                        && type.qualifiedName().endsWith("." + normalizedTypeName))
                        || simpleName.equals(type.simpleName()))
                .sorted(Comparator.comparing(GitLabJavaTypeDeclaration::topLevel).reversed()
                        .thenComparing(GitLabJavaTypeDeclaration::relativeName, Comparator.nullsLast(String::compareTo)))
                .findFirst()
                .orElse(null);
    }

    private GitLabJavaTypeDeclaration findExactQualifiedType(GitLabJavaAstFile astFile, String typeName) {
        var normalizedTypeName = normalizeTypeName(typeName);
        return astFile.types().stream()
                .filter(type -> normalizedTypeName.equals(type.qualifiedName())
                        || normalizedTypeName.equals(type.relativeName()))
                .findFirst()
                .orElse(null);
    }

    private GitLabJavaResolvedType resolved(
            String requestedName,
            GitLabJavaTypeResolutionKind kind,
            GitLabEndpointUseCaseConfidence confidence,
            GitLabJavaTypeDeclaration type
    ) {
        return new GitLabJavaResolvedType(
                requestedName,
                kind,
                confidence,
                type,
                type.filePath(),
                type.qualifiedName(),
                List.of(type.filePath()),
                List.of()
        );
    }

    private GitLabJavaResolvedType ambiguous(
            String requestedName,
            List<String> candidates,
            String limitation
    ) {
        return new GitLabJavaResolvedType(
                requestedName,
                GitLabJavaTypeResolutionKind.AMBIGUOUS,
                GitLabEndpointUseCaseConfidence.LOW,
                null,
                null,
                null,
                candidates,
                List.of(limitation)
        );
    }

    private GitLabJavaResolvedType unresolved(
            String requestedName,
            List<String> candidates,
            String limitation
    ) {
        return new GitLabJavaResolvedType(
                requestedName,
                GitLabJavaTypeResolutionKind.UNRESOLVED,
                GitLabEndpointUseCaseConfidence.LOW,
                null,
                null,
                null,
                candidates,
                List.of(limitation)
        );
    }

    private GitLabJavaTypeDeclaration toTypeDeclaration(
            TypeDeclaration<?> declaration,
            String filePath,
            String packageName
    ) {
        var namePath = typeNamePath(declaration);
        var relativeName = String.join(".", namePath);
        var qualifiedName = StringUtils.hasText(packageName) ? packageName + "." + relativeName : relativeName;
        var parentType = parentType(declaration);
        return new GitLabJavaTypeDeclaration(
                declaration.getNameAsString(),
                relativeName,
                qualifiedName,
                typeKind(declaration),
                filePath,
                parentType == null,
                parentType != null ? parentType.getNameAsString() : null,
                declaration.getBegin().map(position -> position.line).orElse(0),
                declaration.getEnd().map(position -> position.line).orElse(0)
        );
    }

    private LinkedList<String> typeNamePath(TypeDeclaration<?> declaration) {
        var names = new LinkedList<String>();
        TypeDeclaration<?> current = declaration;
        while (current != null) {
            names.addFirst(current.getNameAsString());
            current = parentType(current);
        }
        return names;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private TypeDeclaration<?> parentType(TypeDeclaration<?> declaration) {
        return (TypeDeclaration<?>) declaration.findAncestor(TypeDeclaration.class).orElse(null);
    }

    private GitLabJavaTypeKind typeKind(TypeDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.isInterface()
                    ? GitLabJavaTypeKind.INTERFACE
                    : GitLabJavaTypeKind.CLASS;
        }
        if (declaration instanceof RecordDeclaration) {
            return GitLabJavaTypeKind.RECORD;
        }
        if (declaration instanceof EnumDeclaration) {
            return GitLabJavaTypeKind.ENUM;
        }
        if (declaration instanceof AnnotationDeclaration) {
            return GitLabJavaTypeKind.ANNOTATION;
        }
        return GitLabJavaTypeKind.UNKNOWN;
    }

    private boolean matchesImport(String importName, String typeName) {
        var topLevelName = topLevelTypeName(typeName, null);
        return importName.equals(typeName)
                || importName.endsWith("." + typeName)
                || simpleName(importName).equals(simpleName(typeName))
                || simpleName(importName).equals(topLevelName);
    }

    private String importedTypePath(
            GitLabEndpointUseCaseSourceSession session,
            String importName,
            String requestedTypeName
    ) {
        var topLevelName = topLevelTypeName(requestedTypeName, null);
        var importParts = List.of(importName.split("\\."));
        var typeIndex = firstTypeSegmentIndex(importParts);
        if (typeIndex < 0) {
            return null;
        }
        if (!importParts.get(typeIndex).equals(topLevelName) && !simpleName(importName).equals(topLevelName)) {
            typeIndex = Math.max(0, importParts.size() - 1);
        }
        var packageName = String.join(".", importParts.subList(0, typeIndex));
        var packagePath = packageName.replace('.', '/');
        var relativePath = packagePath + "/" + importParts.get(typeIndex) + ".java";
        var suffix = "/" + relativePath;
        return session.listRepositoryFiles().stream()
                .map(GitLabRepositoryFile::filePath)
                .map(GitLabEndpointUseCaseModelSupport::normalizeFilePath)
                .filter(path -> path != null && (path.endsWith(suffix) || path.equals(relativePath)))
                .distinct()
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private boolean repositoryContains(GitLabEndpointUseCaseSourceSession session, String path) {
        var normalizedPath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        return session.listRepositoryFiles().stream()
                .map(GitLabRepositoryFile::filePath)
                .map(GitLabEndpointUseCaseModelSupport::normalizeFilePath)
                .anyMatch(normalizedPath::equals);
    }

    private String topLevelTypeName(String typeName, String packageName) {
        var normalized = normalizeTypeName(typeName);
        if (StringUtils.hasText(packageName) && normalized.startsWith(packageName + ".")) {
            normalized = normalized.substring(packageName.length() + 1);
        }
        var parts = List.of(normalized.split("\\."));
        var firstTypeIndex = firstTypeSegmentIndex(parts);
        return firstTypeIndex >= 0 ? parts.get(firstTypeIndex) : parts.get(0);
    }

    private int firstTypeSegmentIndex(List<String> parts) {
        for (var index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            if (StringUtils.hasText(part) && Character.isUpperCase(part.charAt(0))) {
                return index;
            }
        }
        return -1;
    }

    private boolean looksLikeQualifiedName(String typeName) {
        var parts = List.of(typeName.split("\\."));
        return parts.size() > 1 && !Character.isUpperCase(parts.get(0).charAt(0));
    }

    private String simpleName(String typeName) {
        var normalized = normalizeTypeName(typeName);
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
        return normalized.replace('$', '.');
    }
}
