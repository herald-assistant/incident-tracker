package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.Providers;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
class GitLabEndpointUseCaseCodeIndexService {

    private final JavaParser javaParser;

    GitLabEndpointUseCaseCodeIndexService() {
        this(new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)));
    }

    GitLabEndpointUseCaseCodeIndexService(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    GitLabEndpointUseCaseCodeIndex buildIndex(GitLabEndpointUseCaseSourceSnapshot snapshot) {
        if (snapshot == null || snapshot.indexStatus() == GitLabEndpointUseCaseIndexStatus.NOT_BUILT) {
            return GitLabEndpointUseCaseCodeIndex.from(
                    snapshot,
                    GitLabEndpointUseCaseIndexStatus.NOT_BUILT,
                    List.of(),
                    List.of(),
                    snapshot != null ? snapshot.warnings() : List.of()
            );
        }

        var types = new ArrayList<GitLabEndpointUseCaseTypeInfo>();
        var calls = new ArrayList<GitLabEndpointUseCaseMethodCallInfo>();
        var warnings = new ArrayList<>(snapshot.warnings());
        var parseFailureDetected = false;

        for (var file : snapshot.files()) {
            var parseResult = parse(file);
            if (!parseResult.getProblems().isEmpty()) {
                parseFailureDetected = true;
                for (var problem : parseResult.getProblems()) {
                    warnings.add(parseWarning(file.path(), problem));
                }
            }
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                continue;
            }

            collectIndexFacts(file.path(), parseResult.getResult().get(), types, calls);
        }

        var indexStatus = parseFailureDetected || snapshot.indexStatus() == GitLabEndpointUseCaseIndexStatus.PARTIAL
                ? GitLabEndpointUseCaseIndexStatus.PARTIAL
                : GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL;

        return GitLabEndpointUseCaseCodeIndex.from(snapshot, indexStatus, types, calls, warnings);
    }

    private ParseResult<CompilationUnit> parse(GitLabEndpointUseCaseSourceFile file) {
        return javaParser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(file.content()));
    }

    private void collectIndexFacts(
            String sourcePath,
            CompilationUnit compilationUnit,
            List<GitLabEndpointUseCaseTypeInfo> types,
            List<GitLabEndpointUseCaseMethodCallInfo> calls
    ) {
        var packageName = compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getName().asString())
                .orElse("");

        for (var typeDeclaration : compilationUnit.findAll(TypeDeclaration.class)) {
            var typeInfo = typeInfo(sourcePath, packageName, typeDeclaration);
            types.add(typeInfo);
            for (var method : typeInfo.methods()) {
                calls.addAll(methodCalls(sourcePath, method.id(), findCallableNode(typeDeclaration, method)));
            }
        }
    }

    private GitLabEndpointUseCaseTypeInfo typeInfo(
            String sourcePath,
            String packageName,
            TypeDeclaration<?> typeDeclaration
    ) {
        var fqn = qualifiedTypeName(packageName, typeDeclaration);
        return new GitLabEndpointUseCaseTypeInfo(
                fqn,
                packageName,
                typeDeclaration.getNameAsString(),
                typeKind(typeDeclaration),
                sourcePath,
                lineStart(typeDeclaration),
                lineEnd(typeDeclaration),
                annotations(typeDeclaration),
                annotationDetails(typeDeclaration),
                modifiers(typeDeclaration),
                extendsTypes(typeDeclaration),
                implementsTypes(typeDeclaration),
                fields(typeDeclaration),
                methods(fqn, typeDeclaration)
        );
    }

    private List<GitLabEndpointUseCaseFieldInfo> fields(TypeDeclaration<?> typeDeclaration) {
        var fields = new ArrayList<GitLabEndpointUseCaseFieldInfo>();
        for (var member : typeDeclaration.getMembers()) {
            if (member instanceof FieldDeclaration fieldDeclaration) {
                for (var variable : fieldDeclaration.getVariables()) {
                    fields.add(new GitLabEndpointUseCaseFieldInfo(
                            variable.getNameAsString(),
                            variable.getType().asString(),
                            annotations(fieldDeclaration),
                            annotationDetails(fieldDeclaration),
                            modifiers(fieldDeclaration),
                            fieldDeclaration.isStatic(),
                            fieldDeclaration.isFinal(),
                            lineStart(variable)
                    ));
                }
            }
        }
        if (typeDeclaration instanceof RecordDeclaration recordDeclaration) {
            for (var parameter : recordDeclaration.getParameters()) {
                fields.add(new GitLabEndpointUseCaseFieldInfo(
                        parameter.getNameAsString(),
                        parameter.getType().asString(),
                        annotations(parameter),
                        annotationDetails(parameter),
                        List.of("private", "final"),
                        false,
                        true,
                        lineStart(parameter)
                ));
            }
        }
        return List.copyOf(fields);
    }

    private List<GitLabEndpointUseCaseMethodInfo> methods(String typeFqn, TypeDeclaration<?> typeDeclaration) {
        var methods = new ArrayList<GitLabEndpointUseCaseMethodInfo>();
        for (var member : typeDeclaration.getMembers()) {
            if (member instanceof ConstructorDeclaration constructorDeclaration) {
                methods.add(constructorInfo(typeFqn, constructorDeclaration));
            }
            if (member instanceof MethodDeclaration methodDeclaration) {
                methods.add(methodInfo(typeFqn, methodDeclaration));
            }
        }
        return List.copyOf(methods);
    }

    private GitLabEndpointUseCaseMethodInfo methodInfo(String typeFqn, MethodDeclaration methodDeclaration) {
        var parameters = parameters(methodDeclaration.getParameters());
        var signature = methodSignature(methodDeclaration.getNameAsString(), parameters);
        return new GitLabEndpointUseCaseMethodInfo(
                typeFqn + "#" + signature,
                methodDeclaration.getNameAsString(),
                signature,
                methodDeclaration.getType().asString(),
                parameters,
                annotations(methodDeclaration),
                annotationDetails(methodDeclaration),
                modifiers(methodDeclaration),
                localVariables(methodDeclaration),
                false,
                lineStart(methodDeclaration),
                lineEnd(methodDeclaration)
        );
    }

    private GitLabEndpointUseCaseMethodInfo constructorInfo(
            String typeFqn,
            ConstructorDeclaration constructorDeclaration
    ) {
        var parameters = parameters(constructorDeclaration.getParameters());
        var signature = methodSignature("<init>", parameters);
        return new GitLabEndpointUseCaseMethodInfo(
                typeFqn + "#" + signature,
                "<init>",
                signature,
                null,
                parameters,
                annotations(constructorDeclaration),
                annotationDetails(constructorDeclaration),
                modifiers(constructorDeclaration),
                localVariables(constructorDeclaration),
                true,
                lineStart(constructorDeclaration),
                lineEnd(constructorDeclaration)
        );
    }

    private List<GitLabEndpointUseCaseParameterInfo> parameters(List<Parameter> parameters) {
        return parameters.stream()
                .map(parameter -> new GitLabEndpointUseCaseParameterInfo(
                        parameter.getNameAsString(),
                        parameter.getType().asString(),
                        annotations(parameter),
                        annotationDetails(parameter)
                ))
                .toList();
    }

    private String methodSignature(String name, List<GitLabEndpointUseCaseParameterInfo> parameters) {
        return name + "(" + parameters.stream()
                .map(GitLabEndpointUseCaseParameterInfo::type)
                .reduce((left, right) -> left + "," + right)
                .orElse("") + ")";
    }

    private List<GitLabEndpointUseCaseLocalVariableInfo> localVariables(Node callableNode) {
        return callableNode.findAll(VariableDeclarationExpr.class).stream()
                .flatMap(variableDeclaration -> variableDeclaration.getVariables().stream()
                        .map(variable -> new GitLabEndpointUseCaseLocalVariableInfo(
                                variable.getNameAsString(),
                                inferredVariableType(variableDeclaration, variable.getInitializer().orElse(null)),
                                variable.getInitializer().map(Expression::toString).orElse(null),
                                lineStart(variable)
                        )))
                .toList();
    }

    private String inferredVariableType(VariableDeclarationExpr variableDeclaration, Expression initializer) {
        var declaredType = variableDeclaration.getElementType().asString();
        if (!"var".equals(declaredType) || initializer == null) {
            return declaredType;
        }
        if (initializer.isObjectCreationExpr()) {
            return initializer.asObjectCreationExpr().getType().asString();
        }
        if (initializer.isCastExpr()) {
            return initializer.asCastExpr().getType().asString();
        }
        return declaredType;
    }

    private List<GitLabEndpointUseCaseMethodCallInfo> methodCalls(
            String sourcePath,
            String callerMethodId,
            Node callableNode
    ) {
        if (callableNode == null) {
            return List.of();
        }

        var calls = new ArrayList<GitLabEndpointUseCaseMethodCallInfo>();
        for (var call : callableNode.findAll(MethodCallExpr.class)) {
            calls.add(new GitLabEndpointUseCaseMethodCallInfo(
                    callerMethodId,
                    sourcePath,
                    lineStart(call),
                    call.getScope().map(Expression::toString).orElse(null),
                    call.getNameAsString(),
                    call.getArguments().size(),
                    call.getArguments().stream().map(Expression::toString).toList(),
                    false,
                    call.toString()
            ));
        }
        for (var objectCreation : callableNode.findAll(ObjectCreationExpr.class)) {
            calls.add(new GitLabEndpointUseCaseMethodCallInfo(
                    callerMethodId,
                    sourcePath,
                    lineStart(objectCreation),
                    null,
                    objectCreation.getType().asString(),
                    objectCreation.getArguments().size(),
                    objectCreation.getArguments().stream().map(Expression::toString).toList(),
                    true,
                    objectCreation.toString()
            ));
        }
        return calls.stream()
                .sorted(Comparator.comparing(
                                GitLabEndpointUseCaseMethodCallInfo::line,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(GitLabEndpointUseCaseMethodCallInfo::expression))
                .toList();
    }

    private Node findCallableNode(TypeDeclaration<?> typeDeclaration, GitLabEndpointUseCaseMethodInfo methodInfo) {
        for (var member : typeDeclaration.getMembers()) {
            if (member instanceof ConstructorDeclaration constructorDeclaration
                    && methodInfo.constructor()
                    && methodInfo.signature().equals(methodSignature(
                    "<init>",
                    parameters(constructorDeclaration.getParameters())))) {
                return constructorDeclaration;
            }
            if (member instanceof MethodDeclaration methodDeclaration
                    && !methodInfo.constructor()
                    && methodInfo.signature().equals(methodSignature(
                    methodDeclaration.getNameAsString(),
                    parameters(methodDeclaration.getParameters())))) {
                return methodDeclaration;
            }
        }
        return null;
    }

    private GitLabEndpointUseCaseTypeKind typeKind(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.isInterface()
                    ? GitLabEndpointUseCaseTypeKind.INTERFACE
                    : GitLabEndpointUseCaseTypeKind.CLASS;
        }
        if (typeDeclaration instanceof EnumDeclaration) {
            return GitLabEndpointUseCaseTypeKind.ENUM;
        }
        if (typeDeclaration instanceof RecordDeclaration) {
            return GitLabEndpointUseCaseTypeKind.RECORD;
        }
        return GitLabEndpointUseCaseTypeKind.ANNOTATION;
    }

    private List<String> extendsTypes(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.getExtendedTypes().stream()
                    .map(type -> type.getNameWithScope())
                    .toList();
        }
        return List.of();
    }

    private List<String> implementsTypes(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.getImplementedTypes().stream()
                    .map(type -> type.getNameWithScope())
                    .toList();
        }
        if (typeDeclaration instanceof EnumDeclaration enumDeclaration) {
            return enumDeclaration.getImplementedTypes().stream()
                    .map(type -> type.getNameWithScope())
                    .toList();
        }
        if (typeDeclaration instanceof RecordDeclaration recordDeclaration) {
            return recordDeclaration.getImplementedTypes().stream()
                    .map(type -> type.getNameWithScope())
                    .toList();
        }
        return List.of();
    }

    private String qualifiedTypeName(String packageName, TypeDeclaration<?> typeDeclaration) {
        var names = new ArrayList<String>();
        TypeDeclaration<?> current = typeDeclaration;
        while (current != null) {
            names.add(current.getNameAsString());
            current = current.findAncestor(TypeDeclaration.class).orElse(null);
        }
        Collections.reverse(names);
        var nestedName = String.join(".", names);
        return packageName == null || packageName.isBlank() ? nestedName : packageName + "." + nestedName;
    }

    private List<String> annotations(NodeWithAnnotations<?> declaration) {
        return declaration.getAnnotations().stream()
                .map(annotation -> annotation.getName().asString())
                .toList();
    }

    private List<GitLabEndpointUseCaseAnnotationInfo> annotationDetails(NodeWithAnnotations<?> declaration) {
        return declaration.getAnnotations().stream()
                .map(annotation -> new GitLabEndpointUseCaseAnnotationInfo(
                        annotation.getName().asString(),
                        annotation.toString(),
                        lineStart(annotation)
                ))
                .toList();
    }

    private List<String> modifiers(NodeWithModifiers<?> declaration) {
        return declaration.getModifiers().stream()
                .map(modifier -> modifier.getKeyword().asString())
                .toList();
    }

    private Integer lineStart(Node node) {
        return node.getRange()
                .map(range -> range.begin.line)
                .orElse(null);
    }

    private Integer lineEnd(Node node) {
        return node.getRange()
                .map(range -> range.end.line)
                .orElse(null);
    }

    private GitLabEndpointUseCaseWarning parseWarning(String sourcePath, Problem problem) {
        return new GitLabEndpointUseCaseWarning(
                GitLabEndpointUseCaseWarningCodes.SOURCE_PARSE_FAILED,
                GitLabEndpointUseCaseWarningSeverity.WARNING,
                "Java source parse problem: " + problem.getMessage(),
                sourcePath,
                problemLine(problem),
                List.of()
        );
    }

    private Integer problemLine(Problem problem) {
        return problem.getLocation()
                .flatMap(TokenRange::toRange)
                .map(range -> range.begin.line)
                .orElse(null);
    }
}
