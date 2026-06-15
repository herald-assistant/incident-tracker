package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Component
public class GitLabJavaInterfaceImplementorResolver {

    private static final int MAX_EXACT_CANDIDATE_FILES = 16;
    private static final int MAX_FALLBACK_CANDIDATE_FILES = 16;
    private static final int MAX_SUBTYPE_CANDIDATE_FILES = 32;
    private static final List<String> DOMAIN_ACTION_PREFIXES = List.of(
            "Calculate",
            "Create",
            "Delete",
            "Determine",
            "Fetch",
            "Find",
            "Get",
            "Init",
            "Load",
            "Refresh",
            "Remove",
            "Save",
            "Update",
            "Upsert",
            "Validate"
    );

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
        var exactCandidateFiles = exactCandidateFiles(session, normalizedInterfaceName);
        var scannedExactFiles = limitedCandidateFiles(
                session,
                exactCandidateFiles,
                MAX_EXACT_CANDIDATE_FILES,
                "Exact interface implementor lookup",
                limitations
        );
        var candidates = implementorsInFiles(session, scannedExactFiles, normalizedInterfaceName);

        if (!candidates.isEmpty()) {
            return resolutionFromCandidates(normalizedInterfaceName, searchKeywords, candidates, limitations);
        }

        var scannedExactFilePaths = scannedExactFiles.stream()
                .map(GitLabRepositoryFile::filePath)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        var fallbackCandidateFiles = candidateFiles(session, normalizedInterfaceName).stream()
                .filter(file -> !scannedExactFilePaths.contains(file.filePath()))
                .toList();
        var scannedFallbackFiles = limitedCandidateFiles(
                session,
                fallbackCandidateFiles,
                MAX_FALLBACK_CANDIDATE_FILES,
                "Interface implementor fallback lookup",
                limitations
        );
        candidates = implementorsInFiles(session, scannedFallbackFiles, normalizedInterfaceName);

        return resolutionFromCandidates(normalizedInterfaceName, searchKeywords, candidates, limitations);
    }

    public GitLabJavaImplementorResolution resolveSubtypes(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaImplementorCandidate baseType
    ) {
        if (baseType == null) {
            return invalid(null, "Base type is required.");
        }
        var baseTypeName = StringUtils.hasText(baseType.implementationQualifiedName())
                ? baseType.implementationQualifiedName()
                : baseType.implementationRelativeName();
        return resolveSubtypes(session, baseTypeName, baseType.filePath());
    }

    public GitLabJavaImplementorResolution resolveSubtypes(
            GitLabEndpointUseCaseSourceSession session,
            String baseTypeName
    ) {
        return resolveSubtypes(session, baseTypeName, null);
    }

    private GitLabJavaImplementorResolution resolveSubtypes(
            GitLabEndpointUseCaseSourceSession session,
            String baseTypeName,
            String baseTypePath
    ) {
        var normalizedBaseTypeName = normalizeTypeName(baseTypeName);
        if (session == null) {
            return invalid(normalizedBaseTypeName, "Source session is required.");
        }
        if (!StringUtils.hasText(normalizedBaseTypeName)) {
            return invalid(normalizedBaseTypeName, "Base type name is required.");
        }

        var limitations = new ArrayList<String>();
        var candidateFiles = subtypeCandidateFiles(session, normalizedBaseTypeName, baseTypePath);
        var scannedFiles = limitedCandidateFiles(
                session,
                candidateFiles,
                MAX_SUBTYPE_CANDIDATE_FILES,
                "Subtype lookup",
                limitations
        );
        var candidates = subtypesInFiles(session, scannedFiles, normalizedBaseTypeName);
        if (candidates.isEmpty()) {
            return new GitLabJavaImplementorResolution(
                    GitLabJavaImplementorResolutionStatus.NOT_FOUND,
                    normalizedBaseTypeName,
                    subtypeSearchKeywords(normalizedBaseTypeName),
                    List.of(),
                    limitations,
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }
        return new GitLabJavaImplementorResolution(
                candidates.size() == 1
                        ? GitLabJavaImplementorResolutionStatus.RESOLVED
                        : GitLabJavaImplementorResolutionStatus.AMBIGUOUS,
                normalizedBaseTypeName,
                subtypeSearchKeywords(normalizedBaseTypeName),
                candidates,
                limitations,
                candidates.size() == 1
                        ? candidates.get(0).confidence()
                        : GitLabEndpointUseCaseConfidence.MEDIUM
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
        return session.listRepositoryFiles().stream()
                .filter(file -> isJavaSource(file.filePath()))
                .filter(file -> !isTestSource(file.filePath()))
                .sorted(Comparator
                        .comparingInt((GitLabRepositoryFile file) -> candidateScore(file.filePath(), interfaceTokens)).reversed()
                        .thenComparing(GitLabRepositoryFile::filePath))
                .toList();
    }

    private List<GitLabRepositoryFile> exactCandidateFiles(
            GitLabEndpointUseCaseSourceSession session,
            String interfaceName
    ) {
        var expectedNames = expectedImplementationSimpleNames(interfaceName);
        if (expectedNames.isEmpty()) {
            return List.of();
        }
        return session.listRepositoryFiles().stream()
                .filter(file -> isJavaSource(file.filePath()))
                .filter(file -> !isTestSource(file.filePath()))
                .map(file -> new ImplementorFileCandidate(file, exactCandidateScore(file.filePath(), expectedNames)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingInt(ImplementorFileCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.file().filePath()))
                .map(ImplementorFileCandidate::file)
                .toList();
    }

    private List<GitLabRepositoryFile> subtypeCandidateFiles(
            GitLabEndpointUseCaseSourceSession session,
            String baseTypeName,
            String baseTypePath
    ) {
        var baseSimpleName = simpleName(baseTypeName);
        if (!StringUtils.hasText(baseSimpleName)) {
            return List.of();
        }
        var basePath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(baseTypePath);
        var searchCandidates = session.searchCandidateFiles(List.of("extends " + baseSimpleName)).stream()
                .map(candidate -> toRepositoryFile(session, candidate))
                .filter(file -> isJavaSource(file.filePath()))
                .filter(file -> !isTestSource(file.filePath()))
                .filter(file -> !file.filePath().equals(basePath))
                .distinct()
                .toList();
        if (!searchCandidates.isEmpty()) {
            return searchCandidates;
        }
        return session.listRepositoryFiles().stream()
                .filter(file -> isJavaSource(file.filePath()))
                .filter(file -> !isTestSource(file.filePath()))
                .filter(file -> !file.filePath().equals(basePath))
                .map(file -> new ImplementorFileCandidate(file, subtypeCandidateScore(file.filePath(), baseSimpleName)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingInt(ImplementorFileCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.file().filePath()))
                .map(ImplementorFileCandidate::file)
                .toList();
    }

    private GitLabRepositoryFile toRepositoryFile(
            GitLabEndpointUseCaseSourceSession session,
            GitLabRepositoryFileCandidate candidate
    ) {
        return new GitLabRepositoryFile(
                candidate.group() != null ? candidate.group() : session.repository().group(),
                candidate.projectName() != null ? candidate.projectName() : session.repository().projectName(),
                candidate.branch() != null ? candidate.branch() : session.repository().branch(),
                candidate.filePath()
        );
    }

    private List<GitLabRepositoryFile> limitedCandidateFiles(
            GitLabEndpointUseCaseSourceSession session,
            List<GitLabRepositoryFile> candidateFiles,
            int maxCandidateFiles,
            String lookupName,
            List<String> limitations
    ) {
        var availableReadBudget = Math.max(0, session.maxReadFiles() - session.readFileCount());
        var scanLimit = Math.min(maxCandidateFiles, availableReadBudget);
        var scannedFiles = candidateFiles.stream()
                .limit(scanLimit)
                .toList();

        if (candidateFiles.size() > scannedFiles.size()) {
            limitations.add("%s scanned the top %d of %d Java candidate files."
                    .formatted(lookupName, scannedFiles.size(), candidateFiles.size()));
        }
        if (scanLimit == 0 && !candidateFiles.isEmpty()) {
            limitations.add("Source read file limit was reached before " + lookupName.toLowerCase() + ".");
        }
        return scannedFiles;
    }

    private List<GitLabJavaImplementorCandidate> implementorsInFiles(
            GitLabEndpointUseCaseSourceSession session,
            List<GitLabRepositoryFile> files,
            String interfaceName
    ) {
        return files.stream()
                .flatMap(file -> implementorsInFile(session, file.filePath(), interfaceName).stream())
                .sorted(Comparator.comparingInt((GitLabJavaImplementorCandidate candidate) -> confidenceScore(candidate.confidence()))
                        .reversed()
                        .thenComparing(GitLabJavaImplementorCandidate::filePath)
                        .thenComparing(GitLabJavaImplementorCandidate::implementationRelativeName))
                .toList();
    }

    private List<GitLabJavaImplementorCandidate> subtypesInFiles(
            GitLabEndpointUseCaseSourceSession session,
            List<GitLabRepositoryFile> files,
            String baseTypeName
    ) {
        return files.stream()
                .flatMap(file -> subtypesInFile(session, file.filePath(), baseTypeName).stream())
                .sorted(Comparator.comparingInt((GitLabJavaImplementorCandidate candidate) -> confidenceScore(candidate.confidence()))
                        .reversed()
                        .thenComparing(GitLabJavaImplementorCandidate::filePath)
                        .thenComparing(GitLabJavaImplementorCandidate::implementationRelativeName))
                .toList();
    }

    private GitLabJavaImplementorResolution resolutionFromCandidates(
            String interfaceName,
            List<String> searchKeywords,
            List<GitLabJavaImplementorCandidate> candidates,
            List<String> limitations
    ) {
        if (candidates.isEmpty()) {
            limitations.add("No implementation was found for interface " + interfaceName + ".");
            return new GitLabJavaImplementorResolution(
                    GitLabJavaImplementorResolutionStatus.NOT_FOUND,
                    interfaceName,
                    searchKeywords,
                    List.of(),
                    limitations,
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }
        if (candidates.size() > 1) {
            limitations.add("More than one implementation matched interface " + interfaceName + ".");
            return new GitLabJavaImplementorResolution(
                    GitLabJavaImplementorResolutionStatus.AMBIGUOUS,
                    interfaceName,
                    searchKeywords,
                    candidates,
                    limitations,
                    GitLabEndpointUseCaseConfidence.MEDIUM
            );
        }

        return new GitLabJavaImplementorResolution(
                GitLabJavaImplementorResolutionStatus.RESOLVED,
                interfaceName,
                searchKeywords,
                candidates,
                limitations,
                candidates.get(0).confidence()
        );
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

    private List<GitLabJavaImplementorCandidate> subtypesInFile(
            GitLabEndpointUseCaseSourceSession session,
            String filePath,
            String baseTypeName
    ) {
        var astFile = sourceResolver.astFile(session, filePath);
        if (!astFile.parsed()) {
            return List.of();
        }

        var candidates = new ArrayList<GitLabJavaImplementorCandidate>();
        for (var declaration : astFile.compilationUnit().findAll(TypeDeclaration.class)) {
            var type = (TypeDeclaration<?>) declaration;
            var extendedTypes = extendedTypes(type);
            if (extendedTypes.isEmpty()) {
                continue;
            }
            var bestMatch = bestMatch(extendedTypes, baseTypeName);
            if (bestMatch == null) {
                continue;
            }
            candidates.add(new GitLabJavaImplementorCandidate(
                    baseTypeName,
                    type.getNameAsString(),
                    relativeTypeName(type),
                    qualifiedTypeName(astFile, type),
                    typeKind(type),
                    astFile.path(),
                    type.getBegin().map(position -> position.line).orElse(0),
                    type.getEnd().map(position -> position.line).orElse(0),
                    extendedTypes,
                    bestMatch.confidence(),
                    "Subtype extends matched base type: " + bestMatch.reason()
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

    private List<String> extendedTypes(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration
                && !classOrInterfaceDeclaration.isInterface()) {
            return classOrInterfaceDeclaration.getExtendedTypes().stream()
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

    private List<String> subtypeSearchKeywords(String baseTypeName) {
        var keywords = new LinkedHashSet<String>();
        var relativeName = relativeInterfaceName(baseTypeName);
        var simpleName = simpleName(baseTypeName);
        keywords.add("extends " + baseTypeName);
        keywords.add("extends " + relativeName);
        keywords.add("extends " + simpleName);
        keywords.add(baseTypeName);
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

    private Set<String> expectedImplementationSimpleNames(String interfaceName) {
        var names = new LinkedHashSet<String>();
        var parts = List.of(relativeInterfaceName(interfaceName).split("\\."));
        if (parts.isEmpty()) {
            return names;
        }

        var topLevelName = parts.get(0);
        var topLevelBaseName = removeInterfaceSuffix(topLevelName);
        if (parts.size() == 1) {
            addSingleInterfaceImplementationNames(names, topLevelBaseName);
            return names;
        }

        var nestedName = parts.get(parts.size() - 1);
        var nestedBaseName = removeInterfaceSuffix(nestedName);
        addNestedInterfaceImplementationNames(names, topLevelBaseName, nestedBaseName);
        return names;
    }

    private void addSingleInterfaceImplementationNames(Set<String> names, String baseName) {
        if (!StringUtils.hasText(baseName)) {
            return;
        }
        addActionDerivedImplementationNames(names, baseName);
        if (isBoundaryImplementationBaseName(baseName)) {
            names.add(baseName);
        }
        names.add(baseName + "Service");
        names.add(baseName + "UseCase");
        names.add(baseName + "Handler");
        names.add(baseName + "Processor");
        names.add(baseName + "Adapter");
        names.add(baseName + "Gateway");
        names.add(baseName + "Client");
        names.add(baseName + "Repository");
        names.add(baseName + "Facade");
        names.add(baseName + "Impl");
        names.add("Default" + baseName);
    }

    private void addActionDerivedImplementationNames(Set<String> names, String baseName) {
        var domainBaseName = removeActionPrefix(baseName);
        if (!StringUtils.hasText(domainBaseName) || domainBaseName.equals(baseName)) {
            return;
        }
        names.add(domainBaseName);
        names.add(domainBaseName + "Model");
        names.add("Default" + domainBaseName);
        names.add("Default" + domainBaseName + "Model");
    }

    private void addNestedInterfaceImplementationNames(
            Set<String> names,
            String topLevelBaseName,
            String nestedBaseName
    ) {
        if (!StringUtils.hasText(topLevelBaseName) || !StringUtils.hasText(nestedBaseName)) {
            return;
        }

        var domainBaseName = removeBoundarySuffix(topLevelBaseName);
        names.add(topLevelBaseName + nestedBaseName);
        names.add(topLevelBaseName + nestedBaseName + "Repository");
        names.add(topLevelBaseName + nestedBaseName + "Service");
        names.add(topLevelBaseName + nestedBaseName + "Adapter");
        names.add(topLevelBaseName + "Impl");
        names.add(topLevelBaseName + "Adapter");
        if (StringUtils.hasText(domainBaseName)) {
            names.add(domainBaseName + nestedBaseName + "Repository");
            names.add(domainBaseName + nestedBaseName + "Service");
            names.add(domainBaseName + nestedBaseName + "Adapter");
            names.add(nestedBaseName + domainBaseName + "Repository");
        }
    }

    private String removeInterfaceSuffix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("(Port|Interface|Api)$", "");
    }

    private String removeBoundarySuffix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("(Repository|Client|Gateway|Service|UseCase|Adapter)$", "");
    }

    private String removeActionPrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        for (var prefix : DOMAIN_ACTION_PREFIXES) {
            if (value.startsWith(prefix) && value.length() > prefix.length()) {
                var candidate = value.substring(prefix.length());
                if (Character.isUpperCase(candidate.charAt(0))) {
                    return candidate;
                }
            }
        }
        return value;
    }

    private boolean isBoundaryImplementationBaseName(String value) {
        return StringUtils.hasText(value)
                && (value.endsWith("Repository")
                || value.endsWith("Client")
                || value.endsWith("Gateway")
                || value.endsWith("Adapter")
                || value.endsWith("Service"));
    }

    private int exactCandidateScore(String filePath, Set<String> expectedNames) {
        var fileName = simpleFileNameWithoutExtension(filePath);
        if (!StringUtils.hasText(fileName)) {
            return 0;
        }
        var normalizedFileName = fileName.toLowerCase();
        var bestScore = 0;
        for (var expectedName : expectedNames) {
            var normalizedExpectedName = expectedName.toLowerCase();
            if (normalizedFileName.equals(normalizedExpectedName)) {
                bestScore = Math.max(bestScore, 1_000 + expectedName.length());
            } else if (normalizedFileName.endsWith(normalizedExpectedName)) {
                bestScore = Math.max(bestScore, 850 + expectedName.length());
            }
        }
        if (bestScore == 0) {
            return 0;
        }
        return bestScore + candidateScore(filePath, interfaceTokens(fileName));
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

    private int subtypeCandidateScore(String filePath, String baseSimpleName) {
        var fileName = simpleFileNameWithoutExtension(filePath);
        if (!StringUtils.hasText(fileName)) {
            return 0;
        }
        var normalizedFileName = fileName.toLowerCase();
        var normalizedBaseName = baseSimpleName.toLowerCase();
        if (normalizedFileName.equals(normalizedBaseName)) {
            return 0;
        }
        var score = 0;
        if (normalizedFileName.endsWith(normalizedBaseName)) {
            score += 1_000 + baseSimpleName.length();
        } else if (normalizedFileName.contains(normalizedBaseName)) {
            score += 600 + baseSimpleName.length();
        }
        if (score == 0 && normalizedBaseName.endsWith("model")) {
            var domainBaseName = normalizedBaseName.substring(0, normalizedBaseName.length() - "model".length());
            if (normalizedFileName.endsWith(domainBaseName + "model")) {
                score += 800 + domainBaseName.length();
            }
        }
        if (score == 0) {
            return 0;
        }
        var normalizedPath = filePath.toLowerCase().replace('\\', '/');
        if (normalizedPath.contains("/domain/") || normalizedPath.contains("/model/")) {
            score += 40;
        }
        if (normalizedPath.contains("/adapter/") || normalizedPath.contains("/application/")) {
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

    private String simpleFileNameWithoutExtension(String filePath) {
        var normalized = GitLabEndpointUseCaseModelSupport.normalizeFilePath(filePath);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        var slashIndex = normalized.lastIndexOf('/');
        var fileName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        return fileName.endsWith(".java")
                ? fileName.substring(0, fileName.length() - ".java".length())
                : fileName;
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

    private record ImplementorFileCandidate(
            GitLabRepositoryFile file,
            int score
    ) {
    }
}
