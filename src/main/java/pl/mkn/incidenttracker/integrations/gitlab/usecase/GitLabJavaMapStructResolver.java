package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

@Component
public class GitLabJavaMapStructResolver {

    public GitLabJavaMapStructMapperDetection detectMapper(
            GitLabJavaAstFile astFile,
            String typeName
    ) {
        if (astFile == null || !astFile.parsed()) {
            return new GitLabJavaMapStructMapperDetection(
                    GitLabJavaMapStructMapperStatus.PARSE_FAILED,
                    null,
                    astFile != null ? astFile.limitations() : List.of("Context Java source was not parsed."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        var type = findType(astFile, typeName);
        if (type == null) {
            return new GitLabJavaMapStructMapperDetection(
                    GitLabJavaMapStructMapperStatus.TYPE_NOT_FOUND,
                    null,
                    List.of("Type was not found in parsed Java source: " + typeName + "."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        return detectMapper(astFile, type);
    }

    public List<GitLabJavaMapStructMapper> detectAllMappers(GitLabJavaAstFile astFile) {
        if (astFile == null || !astFile.parsed()) {
            return List.of();
        }
        return astFile.compilationUnit().findAll(TypeDeclaration.class).stream()
                .map(type -> detectMapper(astFile, (TypeDeclaration<?>) type))
                .filter(detection -> detection.status() == GitLabJavaMapStructMapperStatus.DETECTED)
                .map(GitLabJavaMapStructMapperDetection::mapper)
                .toList();
    }

    public List<GitLabJavaMapStructCall> findMapperCalls(GitLabJavaAstFile astFile) {
        if (astFile == null || !astFile.parsed()) {
            return List.of();
        }
        var calls = new ArrayList<GitLabJavaMapStructCall>();
        for (var methodCall : astFile.compilationUnit().findAll(MethodCallExpr.class)) {
            instanceMethodCall(astFile, methodCall).ifPresent(calls::add);
            getMapperCall(astFile, methodCall).ifPresent(calls::add);
        }
        return List.copyOf(calls);
    }

    private GitLabJavaMapStructMapperDetection detectMapper(
            GitLabJavaAstFile astFile,
            TypeDeclaration<?> type
    ) {
        var mapperAnnotation = type.getAnnotations().stream()
                .filter(this::isMapStructMapperAnnotation)
                .findFirst()
                .orElse(null);
        if (mapperAnnotation == null) {
            return new GitLabJavaMapStructMapperDetection(
                    GitLabJavaMapStructMapperStatus.NOT_MAPSTRUCT_MAPPER,
                    null,
                    List.of("Type is not annotated with MapStruct @Mapper. Type: " + type.getNameAsString() + "."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        var mapper = new GitLabJavaMapStructMapper(
                type.getNameAsString(),
                relativeTypeName(type),
                qualifiedTypeName(astFile, type),
                astFile.path(),
                type.getBegin().map(position -> position.line).orElse(0),
                type.getEnd().map(position -> position.line).orElse(0),
                usesTypes(mapperAnnotation),
                declaredMethodNames(type),
                defaultMethodNames(type),
                GitLabEndpointUseCaseConfidence.HIGH
        );
        return new GitLabJavaMapStructMapperDetection(
                GitLabJavaMapStructMapperStatus.DETECTED,
                mapper,
                List.of(),
                GitLabEndpointUseCaseConfidence.HIGH
        );
    }

    private java.util.Optional<GitLabJavaMapStructCall> instanceMethodCall(
            GitLabJavaAstFile astFile,
            MethodCallExpr methodCall
    ) {
        var scope = methodCall.getScope().orElse(null);
        if (scope == null) {
            return java.util.Optional.empty();
        }
        var scopeText = scope.toString();
        if (!scopeText.endsWith(".INSTANCE")) {
            return java.util.Optional.empty();
        }
        var mapperType = scopeText.substring(0, scopeText.length() - ".INSTANCE".length());
        if (!StringUtils.hasText(mapperType)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new GitLabJavaMapStructCall(
                GitLabJavaMapStructCallKind.INSTANCE_METHOD,
                mapperType,
                methodCall.getNameAsString(),
                methodCall.toString(),
                astFile.path(),
                methodCall.getBegin().map(position -> position.line).orElse(0),
                isSwitchBranchCandidate(methodCall),
                isSwitchBranchCandidate(methodCall)
                        ? GitLabEndpointUseCaseConfidence.MEDIUM
                        : GitLabEndpointUseCaseConfidence.HIGH
        ));
    }

    private java.util.Optional<GitLabJavaMapStructCall> getMapperCall(
            GitLabJavaAstFile astFile,
            MethodCallExpr methodCall
    ) {
        if (!"getMapper".equals(methodCall.getNameAsString())) {
            return java.util.Optional.empty();
        }
        var scopeText = methodCall.getScope().map(Expression::toString).orElse("");
        if (!"Mappers".equals(scopeText) && !"org.mapstruct.factory.Mappers".equals(scopeText)) {
            return java.util.Optional.empty();
        }
        if (methodCall.getArguments().isEmpty()
                || !(methodCall.getArgument(0) instanceof ClassExpr classExpr)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new GitLabJavaMapStructCall(
                GitLabJavaMapStructCallKind.GET_MAPPER,
                cleanClassExpr(classExpr),
                "getMapper",
                methodCall.toString(),
                astFile.path(),
                methodCall.getBegin().map(position -> position.line).orElse(0),
                isSwitchBranchCandidate(methodCall),
                GitLabEndpointUseCaseConfidence.MEDIUM
        ));
    }

    private boolean isSwitchBranchCandidate(MethodCallExpr methodCall) {
        return methodCall.findAncestor(SwitchEntry.class).isPresent();
    }

    private boolean isMapStructMapperAnnotation(AnnotationExpr annotation) {
        var name = annotation.getNameAsString();
        return "Mapper".equals(simpleName(name)) || "org.mapstruct.Mapper".equals(name);
    }

    private List<String> usesTypes(AnnotationExpr mapperAnnotation) {
        if (!mapperAnnotation.isNormalAnnotationExpr()) {
            return List.of();
        }
        return mapperAnnotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> "uses".equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> usesTypes(pair.getValue()))
                .orElse(List.of());
    }

    private List<String> usesTypes(Expression value) {
        var uses = new LinkedHashSet<String>();
        if (value.isArrayInitializerExpr()) {
            for (var expression : value.asArrayInitializerExpr().getValues()) {
                addUseType(uses, expression);
            }
        } else {
            addUseType(uses, value);
        }
        return List.copyOf(uses);
    }

    private void addUseType(LinkedHashSet<String> uses, Expression expression) {
        if (expression instanceof ClassExpr classExpr) {
            uses.add(cleanClassExpr(classExpr));
        } else if (StringUtils.hasText(expression.toString())) {
            uses.add(expression.toString().replace(".class", "").trim());
        }
    }

    private String cleanClassExpr(ClassExpr classExpr) {
        return normalizeTypeName(classExpr.getType().asString());
    }

    private List<String> declaredMethodNames(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(member -> member instanceof MethodDeclaration)
                .map(member -> ((MethodDeclaration) member).getNameAsString())
                .distinct()
                .toList();
    }

    private List<String> defaultMethodNames(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(member -> member instanceof MethodDeclaration)
                .map(member -> (MethodDeclaration) member)
                .filter(method -> method.getModifiers().stream()
                        .anyMatch(modifier -> modifier.getKeyword() == Modifier.Keyword.DEFAULT))
                .map(MethodDeclaration::getNameAsString)
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
        return normalized.replace('$', '.');
    }
}
