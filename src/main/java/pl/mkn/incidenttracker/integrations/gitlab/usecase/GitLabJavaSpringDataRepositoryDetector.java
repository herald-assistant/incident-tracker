package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Component
public class GitLabJavaSpringDataRepositoryDetector {

    private static final Set<String> SPRING_DATA_BASE_INTERFACES = Set.of(
            "JpaRepository",
            "CrudRepository",
            "PagingAndSortingRepository"
    );

    public GitLabJavaSpringDataRepositoryDetection detect(
            GitLabJavaAstFile astFile,
            String typeName
    ) {
        if (astFile == null || !astFile.parsed()) {
            return new GitLabJavaSpringDataRepositoryDetection(
                    GitLabJavaSpringDataRepositoryStatus.PARSE_FAILED,
                    null,
                    astFile != null ? astFile.limitations() : List.of("Context Java source was not parsed."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        var type = findType(astFile, typeName);
        if (type == null) {
            return new GitLabJavaSpringDataRepositoryDetection(
                    GitLabJavaSpringDataRepositoryStatus.TYPE_NOT_FOUND,
                    null,
                    List.of("Type was not found in parsed Java source: " + typeName + "."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        return detect(astFile, type);
    }

    public List<GitLabJavaSpringDataRepository> detectAll(GitLabJavaAstFile astFile) {
        if (astFile == null || !astFile.parsed()) {
            return List.of();
        }
        return astFile.compilationUnit().findAll(TypeDeclaration.class).stream()
                .map(type -> detect(astFile, (TypeDeclaration<?>) type))
                .filter(detection -> detection.status() == GitLabJavaSpringDataRepositoryStatus.DETECTED)
                .map(GitLabJavaSpringDataRepositoryDetection::repository)
                .toList();
    }

    private GitLabJavaSpringDataRepositoryDetection detect(
            GitLabJavaAstFile astFile,
            TypeDeclaration<?> type
    ) {
        if (!(type instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration)
                || !classOrInterfaceDeclaration.isInterface()) {
            return notSpringData(type, "Type is not an interface.");
        }

        var springDataBase = classOrInterfaceDeclaration.getExtendedTypes().stream()
                .filter(this::isSpringDataBaseInterface)
                .findFirst()
                .orElse(null);
        if (springDataBase == null) {
            return notSpringData(type, "Interface does not extend a supported Spring Data repository base interface.");
        }

        var typeArguments = springDataBase.getTypeArguments()
                .map(arguments -> arguments.stream().toList())
                .orElse(List.of());
        var repository = new GitLabJavaSpringDataRepository(
                type.getNameAsString(),
                relativeTypeName(type),
                qualifiedTypeName(astFile, type),
                astFile.path(),
                type.getBegin().map(position -> position.line).orElse(0),
                type.getEnd().map(position -> position.line).orElse(0),
                simpleName(springDataBase.getNameWithScope()),
                typeArguments.size() > 0 ? cleanType(typeArguments.get(0)) : null,
                typeArguments.size() > 1 ? cleanType(typeArguments.get(1)) : null,
                declaredMethodNames(type),
                GitLabEndpointUseCaseConfidence.HIGH
        );
        return new GitLabJavaSpringDataRepositoryDetection(
                GitLabJavaSpringDataRepositoryStatus.DETECTED,
                repository,
                List.of(),
                GitLabEndpointUseCaseConfidence.HIGH
        );
    }

    private GitLabJavaSpringDataRepositoryDetection notSpringData(
            TypeDeclaration<?> type,
            String limitation
    ) {
        return new GitLabJavaSpringDataRepositoryDetection(
                GitLabJavaSpringDataRepositoryStatus.NOT_SPRING_DATA_REPOSITORY,
                null,
                List.of(limitation + " Type: " + type.getNameAsString() + "."),
                GitLabEndpointUseCaseConfidence.LOW
        );
    }

    private boolean isSpringDataBaseInterface(ClassOrInterfaceType type) {
        return SPRING_DATA_BASE_INTERFACES.contains(simpleName(type.getNameWithScope()));
    }

    private List<String> declaredMethodNames(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(member -> member instanceof MethodDeclaration)
                .map(member -> ((MethodDeclaration) member).getNameAsString())
                .distinct()
                .toList();
    }

    private TypeDeclaration<?> findType(GitLabJavaAstFile astFile, String typeName) {
        var normalizedTypeName = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalizedTypeName)) {
            return astFile.compilationUnit().findAll(TypeDeclaration.class).stream()
                    .map(type -> (TypeDeclaration<?>) type)
                    .filter(type -> parentType(type) == null)
                    .findFirst()
                    .orElse(null);
        }
        var simpleName = simpleName(normalizedTypeName);
        return astFile.compilationUnit().findAll(TypeDeclaration.class).stream()
                .map(type -> (TypeDeclaration<?>) type)
                .filter(type -> {
                    var relativeName = relativeTypeName(type);
                    var qualifiedName = qualifiedTypeName(astFile, type);
                    return normalizedTypeName.equals(type.getNameAsString())
                            || normalizedTypeName.equals(relativeName)
                            || normalizedTypeName.equals(qualifiedName)
                            || simpleName.equals(type.getNameAsString())
                            || (StringUtils.hasText(qualifiedName)
                            && qualifiedName.endsWith("." + normalizedTypeName));
                })
                .sorted(Comparator.comparing((TypeDeclaration<?> type) -> parentType(type) == null).reversed()
                        .thenComparing(this::relativeTypeName, Comparator.nullsLast(String::compareTo)))
                .findFirst()
                .orElse(null);
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

    private String cleanType(Type type) {
        return normalizeTypeName(type.asString());
    }

    private String simpleName(String typeName) {
        var normalized = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalized)) {
            return null;
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
        return normalized.replace('$', '.');
    }
}
