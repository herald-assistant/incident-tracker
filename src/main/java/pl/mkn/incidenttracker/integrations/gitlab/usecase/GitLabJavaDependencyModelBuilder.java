package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Component
public class GitLabJavaDependencyModelBuilder {

    private static final Set<String> BEAN_STEREOTYPES = Set.of(
            "Service",
            "Component",
            "Repository",
            "RestController",
            "Configuration",
            "AdapterBean",
            "UseCaseBean"
    );
    private static final Set<String> NON_BEAN_SIMPLE_TYPES = Set.of(
            "byte",
            "short",
            "int",
            "long",
            "float",
            "double",
            "boolean",
            "char",
            "Byte",
            "Short",
            "Integer",
            "Long",
            "Float",
            "Double",
            "Boolean",
            "Character",
            "String",
            "Object",
            "BigDecimal",
            "BigInteger",
            "UUID",
            "LocalDate",
            "LocalDateTime",
            "Instant",
            "Duration",
            "Clock",
            "List",
            "Set",
            "Map",
            "Collection",
            "Optional",
            "Logger"
    );

    private final GitLabJavaExternalTypePolicy externalTypePolicy;

    public GitLabJavaDependencyModelBuilder(GitLabJavaExternalTypePolicy externalTypePolicy) {
        this.externalTypePolicy = externalTypePolicy;
    }

    GitLabJavaDependencyModelBuilder() {
        this(new GitLabJavaExternalTypePolicy());
    }

    public GitLabJavaDependencyModel build(
            GitLabJavaAstFile astFile,
            String typeName
    ) {
        if (astFile == null || !astFile.parsed()) {
            return new GitLabJavaDependencyModel(
                    null,
                    List.of(),
                    astFile != null ? astFile.limitations() : List.of("Context Java source was not parsed."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        var type = findType(astFile, typeName);
        if (type == null) {
            return new GitLabJavaDependencyModel(
                    null,
                    List.of(),
                    List.of("Type was not found in parsed Java source: " + typeName + "."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        var bean = beanCandidate(astFile, type);
        var dependencies = new LinkedHashMap<String, GitLabJavaInjectedDependency>();
        if (hasAnnotation(type, "RequiredArgsConstructor")) {
            for (var dependency : lombokRequiredArgsDependencies(astFile, type)) {
                dependencies.putIfAbsent(dependencyKey(dependency), dependency);
            }
        }
        for (var dependency : autowiredFieldDependencies(astFile, type)) {
            dependencies.putIfAbsent(dependencyKey(dependency), dependency);
        }
        for (var dependency : constructorDependencies(astFile, type)) {
            dependencies.putIfAbsent(dependencyKey(dependency), dependency);
        }

        return new GitLabJavaDependencyModel(
                bean,
                List.copyOf(dependencies.values()),
                List.of(),
                bean.potentialBean() || !dependencies.isEmpty()
                        ? GitLabEndpointUseCaseConfidence.HIGH
                        : GitLabEndpointUseCaseConfidence.MEDIUM
        );
    }

    private List<GitLabJavaInjectedDependency> lombokRequiredArgsDependencies(
            GitLabJavaAstFile astFile,
            TypeDeclaration<?> type
    ) {
        return immediateFields(type).stream()
                .filter(field -> hasModifier(field, Modifier.Keyword.FINAL))
                .filter(field -> !hasModifier(field, Modifier.Keyword.STATIC))
                .flatMap(field -> field.getVariables().stream()
                        .filter(variable -> variable.getInitializer().isEmpty())
                        .map(variable -> fieldDependency(
                                astFile,
                                type,
                                field,
                                variable,
                                GitLabJavaInjectionSource.LOMBOK_REQUIRED_ARGS
                        )))
                .filter(dependency -> dependency != null)
                .toList();
    }

    private List<GitLabJavaInjectedDependency> autowiredFieldDependencies(
            GitLabJavaAstFile astFile,
            TypeDeclaration<?> type
    ) {
        return immediateFields(type).stream()
                .filter(field -> hasAnnotation(field, "Autowired"))
                .flatMap(field -> field.getVariables().stream()
                        .map(variable -> fieldDependency(
                                astFile,
                                type,
                                field,
                                variable,
                                GitLabJavaInjectionSource.AUTOWIRED_FIELD
                        )))
                .filter(dependency -> dependency != null)
                .toList();
    }

    private List<GitLabJavaInjectedDependency> constructorDependencies(
            GitLabJavaAstFile astFile,
            TypeDeclaration<?> type
    ) {
        return immediateConstructors(type).stream()
                .filter(constructor -> !constructor.getParameters().isEmpty())
                .flatMap(constructor -> constructor.getParameters().stream()
                        .map(parameter -> {
                            var source = hasAnnotation(constructor, "Autowired")
                                    ? GitLabJavaInjectionSource.AUTOWIRED_CONSTRUCTOR
                                    : GitLabJavaInjectionSource.CONSTRUCTOR;
                            var typeName = parameter.getType().asString();
                            if (skipDependencyType(astFile, typeName)) {
                                return null;
                            }
                            var annotations = joinAnnotationNames(constructor, parameter);
                            return new GitLabJavaInjectedDependency(
                                    parameter.getNameAsString(),
                                    typeName,
                                    source,
                                    qualifier(parameter.getAnnotations()),
                                    qualifiedTypeName(astFile, type),
                                    astFile.path(),
                                    parameter.getBegin().map(position -> position.line)
                                            .orElse(constructor.getBegin().map(position -> position.line).orElse(0)),
                                    parameter.getEnd().map(position -> position.line)
                                            .orElse(constructor.getEnd().map(position -> position.line).orElse(0)),
                                    annotations,
                                    source == GitLabJavaInjectionSource.AUTOWIRED_CONSTRUCTOR
                                            ? GitLabEndpointUseCaseConfidence.HIGH
                                            : GitLabEndpointUseCaseConfidence.MEDIUM
                            );
                        }))
                .filter(dependency -> dependency != null)
                .toList();
    }

    private GitLabJavaInjectedDependency fieldDependency(
            GitLabJavaAstFile astFile,
            TypeDeclaration<?> type,
            FieldDeclaration field,
            VariableDeclarator variable,
            GitLabJavaInjectionSource source
    ) {
        var typeName = variable.getType().asString();
        if (skipDependencyType(astFile, typeName)) {
            return null;
        }
        return new GitLabJavaInjectedDependency(
                variable.getNameAsString(),
                typeName,
                source,
                qualifier(field.getAnnotations()),
                qualifiedTypeName(astFile, type),
                astFile.path(),
                field.getBegin().map(position -> position.line).orElse(0),
                field.getEnd().map(position -> position.line).orElse(0),
                annotationNames(field),
                source == GitLabJavaInjectionSource.AUTOWIRED_FIELD
                        ? GitLabEndpointUseCaseConfidence.HIGH
                        : GitLabEndpointUseCaseConfidence.MEDIUM
        );
    }

    private GitLabJavaBeanCandidate beanCandidate(
            GitLabJavaAstFile astFile,
            TypeDeclaration<?> type
    ) {
        var stereotypes = annotationNames(type).stream()
                .filter(BEAN_STEREOTYPES::contains)
                .toList();
        return new GitLabJavaBeanCandidate(
                type.getNameAsString(),
                relativeTypeName(type),
                qualifiedTypeName(astFile, type),
                typeKind(type),
                astFile.path(),
                stereotypes,
                !stereotypes.isEmpty(),
                stereotypes.isEmpty() ? GitLabEndpointUseCaseConfidence.LOW : GitLabEndpointUseCaseConfidence.HIGH
        );
    }

    private boolean skipDependencyType(GitLabJavaAstFile astFile, String typeName) {
        var normalizedTypeName = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalizedTypeName)) {
            return true;
        }
        if (NON_BEAN_SIMPLE_TYPES.contains(simpleName(normalizedTypeName))) {
            return true;
        }
        return !externalTypePolicy.classify(normalizedTypeName, astFile).sourceLookupAllowed();
    }

    private TypeDeclaration<?> findType(GitLabJavaAstFile astFile, String typeName) {
        var normalizedTypeName = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalizedTypeName)) {
            return astFile.compilationUnit().findAll(TypeDeclaration.class).stream()
                    .map(declaration -> (TypeDeclaration<?>) declaration)
                    .filter(declaration -> parentType(declaration) == null)
                    .findFirst()
                    .orElse(null);
        }
        var simpleName = simpleName(normalizedTypeName);
        return astFile.compilationUnit().findAll(TypeDeclaration.class).stream()
                .map(declaration -> (TypeDeclaration<?>) declaration)
                .filter(declaration -> {
                    var relativeName = relativeTypeName(declaration);
                    var qualifiedName = qualifiedTypeName(astFile, declaration);
                    return normalizedTypeName.equals(declaration.getNameAsString())
                            || normalizedTypeName.equals(relativeName)
                            || normalizedTypeName.equals(qualifiedName)
                            || simpleName.equals(declaration.getNameAsString())
                            || (StringUtils.hasText(qualifiedName)
                            && qualifiedName.endsWith("." + normalizedTypeName));
                })
                .sorted(Comparator.comparing((TypeDeclaration<?> declaration) -> parentType(declaration) == null).reversed()
                        .thenComparing(this::relativeTypeName, Comparator.nullsLast(String::compareTo)))
                .findFirst()
                .orElse(null);
    }

    private List<FieldDeclaration> immediateFields(TypeDeclaration<?> type) {
        return type.findAll(FieldDeclaration.class).stream()
                .filter(field -> field.findAncestor(TypeDeclaration.class).orElse(null) == type)
                .toList();
    }

    private List<ConstructorDeclaration> immediateConstructors(TypeDeclaration<?> type) {
        return type.findAll(ConstructorDeclaration.class).stream()
                .filter(constructor -> constructor.findAncestor(TypeDeclaration.class).orElse(null) == type)
                .toList();
    }

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String annotationName) {
        return node.getAnnotations().stream()
                .map(annotation -> simpleName(annotation.getNameAsString()))
                .anyMatch(annotationName::equals);
    }

    private List<String> annotationNames(NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream()
                .map(annotation -> simpleName(annotation.getNameAsString()))
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> joinAnnotationNames(NodeWithAnnotations<?> left, NodeWithAnnotations<?> right) {
        var names = new ArrayList<String>();
        names.addAll(annotationNames(left));
        names.addAll(annotationNames(right));
        return names.stream()
                .distinct()
                .toList();
    }

    private String qualifier(List<AnnotationExpr> annotations) {
        return annotations.stream()
                .filter(annotation -> "Qualifier".equals(simpleName(annotation.getNameAsString())))
                .findFirst()
                .map(this::qualifierValue)
                .orElse(null);
    }

    private String qualifierValue(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return expressionValue(annotation.asSingleMemberAnnotationExpr().getMemberValue());
        }
        if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> "value".equals(pair.getNameAsString()))
                    .findFirst()
                    .map(pair -> expressionValue(pair.getValue()))
                    .orElse(null);
        }
        return null;
    }

    private String expressionValue(Expression expression) {
        if (expression.isStringLiteralExpr()) {
            return expression.asStringLiteralExpr().getValue();
        }
        return expression.toString();
    }

    private boolean hasModifier(FieldDeclaration field, Modifier.Keyword keyword) {
        return field.getModifiers().stream()
                .anyMatch(modifier -> modifier.getKeyword() == keyword);
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
        if (declaration instanceof AnnotationDeclaration) {
            return GitLabJavaTypeKind.ANNOTATION;
        }
        return GitLabJavaTypeKind.UNKNOWN;
    }

    private String dependencyKey(GitLabJavaInjectedDependency dependency) {
        return dependency.source() + "|" + dependency.typeName() + "|" + dependency.name();
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
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex).trim();
        }
        return normalized.replace('$', '.');
    }
}
