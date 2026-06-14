package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Component
public class GitLabJavaInterfaceImplementorResolver {

    private static final int MAX_CANDIDATE_FILES = 80;

    private final GitLabJavaSourceResolver sourceResolver;

    public GitLabJavaInterfaceImplementorResolver(GitLabJavaSourceResolver sourceResolver) {
        this.sourceResolver = sourceResolver;
    }

    GitLabJavaInterfaceImplementorResolver() {
        this(new GitLabJavaSourceResolver());
    }

    public GitLabJavaImplementorResolution resolveImplementors(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaResolvedType interfaceType
    ) {
        if (interfaceType == null) {
            return resolveImplementors(session, (String) null);
        }
        var name = StringUtils.hasText(interfaceType.qualifiedName())
                ? interfaceType.qualifiedName()
                : interfaceType.requestedName();
        return resolveImplementors(session, name);
    }

    public GitLabJavaImplementorResolution resolveImplementors(
            GitLabEndpointUseCaseSourceSession session,
            String interfaceName
    ) {
        var normalizedInterfaceName = normalizeTypeName(interfaceName);
        if (session == null) {
            return invalid(normalizedInterfaceName, "Source session is required.");
        }
        if (!StringUtils.hasText(normalizedInterfaceName)) {
            return invalid(normalizedInterfaceName, "Interface name is required.");
        }

        var searchKeywords = searchKeywords(normalizedInterfaceName);
        var limitations = new ArrayList<String>();
        var candidateFiles = candidateFiles(session, normalizedInterfaceName);
        var availableReadBudget = Math.max(0, session.maxReadFiles() - session.readFileCount());
        var scanLimit = Math.min(MAX_CANDIDATE_FILES, availableReadBudget);
        var scannedFiles = candidateFiles.stream()
                .limit(scanLimit)
                .toList();

        if (candidateFiles.size() > scannedFiles.size()) {
            limitations.add("Interface implementor lookup scanned the top %d of %d Java candidate files."
                    .formatted(scannedFiles.size(), candidateFiles.size()));
        }
        if (scanLimit == 0 && !candidateFiles.isEmpty()) {
            limitations.add("Source read file limit was reached before interface implementor lookup.");
        }

        var candidates = scannedFiles.stream()
                .flatMap(file -> implementorsInFile(session, file.filePath(), normalizedInterfaceName).stream())
                .sorted(Comparator.comparingInt((GitLabJavaImplementorCandidate candidate) -> confidenceScore(candidate.confidence()))
                        .reversed()
                        .thenComparing(GitLabJavaImplementorCandidate::filePath)
                        .thenComparing(GitLabJavaImplementorCandidate::implementationRelativeName))
                .toList();

        if (candidates.isEmpty()) {
            limitations.add("No implementation was found for interface " + normalizedInterfaceName + ".");
            return new GitLabJavaImplementorResolution(
                    GitLabJavaImplementorResolutionStatus.NOT_FOUND,
                    normalizedInterfaceName,
                    searchKeywords,
                    List.of(),
                    limitations,
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }
        if (candidates.size() > 1) {
            limitations.add("More than one implementation matched interface " + normalizedInterfaceName + ".");
            return new GitLabJavaImplementorResolution(
                    GitLabJavaImplementorResolutionStatus.AMBIGUOUS,
                    normalizedInterfaceName,
                    searchKeywords,
                    candidates,
                    limitations,
                    GitLabEndpointUseCaseConfidence.MEDIUM
            );
        }

        return new GitLabJavaImplementorResolution(
                GitLabJavaImplementorResolutionStatus.RESOLVED,
                normalizedInterfaceName,
                searchKeywords,
                candidates,
                limitations,
                candidates.get(0).confidence()
        );
    }

    private GitLabJavaImplementorResolution invalid(String interfaceName, String limitation) {
        return new GitLabJavaImplementorResolution(
                GitLabJavaImplementorResolutionStatus.INVALID_REQUEST,
                interfaceName,
                List.of(),
                List.of(),
                List.of(limitation),
                GitLabEndpointUseCaseConfidence.LOW
        );
    }

    private List<GitLabRepositoryFile> candidateFiles(
            GitLabEndpointUseCaseSourceSession session,
            String interfaceName
    ) {
        var interfaceTokens = interfaceTokens(interfaceName);
        return session.listRepositoryFiles(session.repository().sourcePathPrefix()).stream()
                .filter(file -> isJavaSource(file.filePath()))
                .filter(file -> !isTestSource(file.filePath()))
                .sorted(Comparator
                        .comparingInt((GitLabRepositoryFile file) -> candidateScore(file.filePath(), interfaceTokens)).reversed()
                        .thenComparing(GitLabRepositoryFile::filePath))
                .toList();
    }

    private List<GitLabJavaImplementorCandidate> implementorsInFile(
            GitLabEndpointUseCaseSourceSession session,
            String filePath,
            String interfaceName
    ) {
        var astFile = sourceResolver.astFile(session, filePath);
        if (!astFile.parsed()) {
            return List.of();
        }

        var candidates = new ArrayList<GitLabJavaImplementorCandidate>();
        for (var declaration : astFile.compilationUnit().findAll(TypeDeclaration.class)) {
            var type = (TypeDeclaration<?>) declaration;
            var implementedTypes = implementedTypes(type);
            if (implementedTypes.isEmpty()) {
                continue;
            }
            var bestMatch = bestMatch(implementedTypes, interfaceName);
            if (bestMatch == null) {
                continue;
            }
            candidates.add(new GitLabJavaImplementorCandidate(
                    interfaceName,
                    type.getNameAsString(),
                    relativeTypeName(type),
                    qualifiedTypeName(astFile, type),
                    typeKind(type),
                    astFile.path(),
                    type.getBegin().map(position -> position.line).orElse(0),
                    type.getEnd().map(position -> position.line).orElse(0),
                    implementedTypes,
                    bestMatch.confidence(),
                    bestMatch.reason()
            ));
        }
        return List.copyOf(candidates);
    }

    private InterfaceMatch bestMatch(List<String> implementedTypes, String interfaceName) {
        return implementedTypes.stream()
                .map(implementedType -> match(implementedType, interfaceName))
                .filter(match -> match != null)
                .max(Comparator.comparing(InterfaceMatch::confidence))
                .orElse(null);
    }

    private InterfaceMatch match(String implementedType, String interfaceName) {
        var normalizedImplementedType = normalizeTypeName(implementedType);
        var normalizedInterfaceName = normalizeTypeName(interfaceName);
        var interfaceRelativeName = relativeInterfaceName(normalizedInterfaceName);
        var interfaceSimpleName = simpleName(normalizedInterfaceName);
        var implementedSimpleName = simpleName(normalizedImplementedType);
        var implementedTypeQualifiedOrNested = normalizedImplementedType.contains(".");

        if (normalizedImplementedType.equals(normalizedInterfaceName)
                || normalizedImplementedType.equals(interfaceRelativeName)
                || (implementedTypeQualifiedOrNested && normalizedInterfaceName.endsWith("." + normalizedImplementedType))
                || normalizedImplementedType.endsWith("." + interfaceRelativeName)) {
            return new InterfaceMatch(
                    GitLabEndpointUseCaseConfidence.HIGH,
                    "Implementation declares exact interface match: " + implementedType + "."
            );
        }
        if (implementedSimpleName.equals(interfaceSimpleName)) {
            return new InterfaceMatch(
                    GitLabEndpointUseCaseConfidence.MEDIUM,
                    "Implementation declares simple-name interface match: " + implementedType + "."
            );
        }
        return null;
    }

    private List<String> implementedTypes(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration
                && !classOrInterfaceDeclaration.isInterface()) {
            return classOrInterfaceDeclaration.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::asString)
                    .toList();
        }
        if (type instanceof RecordDeclaration recordDeclaration) {
            return recordDeclaration.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::asString)
                    .toList();
        }
        if (type instanceof EnumDeclaration enumDeclaration) {
            return enumDeclaration.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::asString)
                    .toList();
        }
        return List.of();
    }

    private List<String> searchKeywords(String interfaceName) {
        var keywords = new LinkedHashSet<String>();
        var relativeName = relativeInterfaceName(interfaceName);
        var simpleName = simpleName(interfaceName);
        keywords.add("implements " + interfaceName);
        keywords.add("implements " + relativeName);
        keywords.add("implements " + simpleName);
        keywords.add(interfaceName);
        keywords.add(relativeName);
        keywords.add(simpleName);
        return keywords.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private Set<String> interfaceTokens(String interfaceName) {
        var tokens = new LinkedHashSet<String>();
        for (var part : relativeInterfaceName(interfaceName).split("\\.")) {
            addTokenVariants(tokens, part);
        }
        addTokenVariants(tokens, simpleName(interfaceName));
        return tokens;
    }

    private void addTokenVariants(Set<String> tokens, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        var cleaned = value.replaceAll("(Port|Interface)$", "");
        tokens.add(cleaned.toLowerCase());
        for (var token : cleaned.split("(?=[A-Z])")) {
            if (token.length() >= 3) {
                tokens.add(token.toLowerCase());
            }
        }
    }

    private int candidateScore(String filePath, Set<String> interfaceTokens) {
        var normalized = filePath.toLowerCase().replace('\\', '/');
        var score = 0;
        for (var token : interfaceTokens) {
            if (StringUtils.hasText(token) && normalized.contains(token)) {
                score += token.length() >= 8 ? 20 : 8;
            }
        }
        if (normalized.endsWith("service.java")) {
            score += 30;
        }
        if (normalized.endsWith("repository.java")) {
            score += 30;
        }
        if (normalized.endsWith("adapter.java")) {
            score += 20;
        }
        if (normalized.endsWith("handler.java")) {
            score += 15;
        }
        if (normalized.contains("/application/") || normalized.contains("/service/")) {
            score += 12;
        }
        if (normalized.contains("/adapter/") || normalized.contains("/out/") || normalized.contains("/infrastructure/")) {
            score += 8;
        }
        if (normalized.endsWith("port.java") || normalized.endsWith("api.java")) {
            score -= 20;
        }
        return score;
    }

    private boolean isJavaSource(String filePath) {
        return StringUtils.hasText(filePath) && filePath.toLowerCase().endsWith(".java");
    }

    private boolean isTestSource(String filePath) {
        var normalized = filePath.toLowerCase().replace('\\', '/');
        return normalized.contains("/src/test/") || normalized.contains("/test/");
    }

    private String qualifiedTypeName(GitLabJavaAstFile astFile, TypeDeclaration<?> declaration) {
        var relativeName = relativeTypeName(declaration);
        return StringUtils.hasText(astFile.packageName())
                ? astFile.packageName() + "." + relativeName
                : relativeName;
    }

    private String relativeTypeName(TypeDeclaration<?> declaration) {
        return String.join(".", typeNamePath(declaration));
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
        return GitLabJavaTypeKind.UNKNOWN;
    }

    private String relativeInterfaceName(String interfaceName) {
        var normalized = normalizeTypeName(interfaceName);
        var parts = List.of(normalized.split("\\."));
        for (var index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            if (StringUtils.hasText(part) && Character.isUpperCase(part.charAt(0))) {
                return String.join(".", parts.subList(index, parts.size()));
            }
        }
        return normalized;
    }

    private String simpleName(String typeName) {
        var normalized = normalizeTypeName(typeName);
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }

    private String normalizeTypeName(String typeName) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        if (normalized == null) {
            return "";
        }
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex).trim();
        }
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        return normalized.replace('$', '.');
    }

    private int confidenceScore(GitLabEndpointUseCaseConfidence confidence) {
        return switch (confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    private record InterfaceMatch(
            GitLabEndpointUseCaseConfidence confidence,
            String reason
    ) {
    }
}
