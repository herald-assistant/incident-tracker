package pl.mkn.tdw.features.changeverification.ai.copilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 90)
@RequiredArgsConstructor
public class ChangeVerificationCopilotGitLabScopePolicy implements CopilotToolInvocationPolicy {

    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
    };
    private static final Set<String> PROJECT_LIST_TOOLS = Set.of(
            GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
            GitLabToolNames.FIND_CLASS_REFERENCES,
            GitLabToolNames.FIND_FLOW_CONTEXT
    );
    private static final Set<String> SINGLE_FILE_TOOLS = Set.of(
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE,
            GitLabToolNames.READ_JAVA_METHOD_SLICE,
            GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE
    );

    private final ObjectMapper objectMapper;

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (!changeVerificationGitLabInvocation(request)) {
            return;
        }

        var scope = ChangeVerificationGitLabScope.from(hiddenContext(request));
        if (scope.repositories().isEmpty()) {
            reject(
                    request,
                    "No Change Verification repository scope is available for GitLab tool execution.",
                    "Nie uzywaj GitLab tools bez wykrytego scope repozytoriow. Oprzyj wynik na artifactach i wpisz limit widocznosci."
            );
        }

        var arguments = arguments(request);
        validateProjectScope(request, scope, arguments);
        validateBranchScope(request, scope, arguments);
        validateFileScope(request, scope, arguments);
    }

    private void validateProjectScope(
            CopilotToolInvocationPolicyRequest request,
            ChangeVerificationGitLabScope scope,
            Map<String, Object> arguments
    ) {
        var requestedProjects = requestedProjects(request.toolName(), arguments);
        if (requestedProjects.isEmpty()) {
            reject(
                    request,
                    "GitLab tool call does not declare a repository from the Change Verification scope.",
                    "Podaj `projectName` albo `projectNames` z `change-verification/repository-scope.md`; nie wykonuj broad discovery poza MR-kami tej story."
            );
        }
        var rejectedProjects = requestedProjects.stream()
                .filter(project -> !scope.projectAllowed(project))
                .toList();
        if (!rejectedProjects.isEmpty()) {
            reject(
                    request,
                    "GitLab tool call targets repositories outside the Change Verification scope: " + rejectedProjects,
                    "Uzyj wylacznie repozytoriow z `change-verification/repository-scope.md`: "
                            + String.join(", ", scope.allowedProjectLabels()) + "."
            );
        }
    }

    private void validateBranchScope(
            CopilotToolInvocationPolicyRequest request,
            ChangeVerificationGitLabScope scope,
            Map<String, Object> arguments
    ) {
        var branchRef = stringValue(arguments.get("branchRef"));
        if (!StringUtils.hasText(branchRef)) {
            reject(
                    request,
                    "GitLab tool call does not declare branchRef from the Change Verification scope.",
                    "Podaj `branchRef` rowny `sourceRef` albo `targetRef` z `change-verification/repository-scope.md`."
            );
        }
        if (!scope.branchAllowed(branchRef)) {
            reject(
                    request,
                    "GitLab tool call targets branch/ref outside the Change Verification scope: " + branchRef,
                    "Uzyj `sourceRef` albo `targetRef` z `change-verification/repository-scope.md`: "
                            + String.join(", ", scope.allowedBranches()) + "."
            );
        }
    }

    private void validateFileScope(
            CopilotToolInvocationPolicyRequest request,
            ChangeVerificationGitLabScope scope,
            Map<String, Object> arguments
    ) {
        if (SINGLE_FILE_TOOLS.contains(request.toolName())) {
            validateRequestedFiles(request, scope, List.of(stringValue(arguments.get("filePath"))));
            return;
        }
        if (GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH.equals(request.toolName())) {
            validateRequestedFiles(request, scope, stringList(arguments.get("filePaths")));
            return;
        }
        if (GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS.equals(request.toolName())) {
            validateRequestedFiles(request, scope, chunkFilePaths(arguments.get("chunks")));
        }
    }

    private void validateRequestedFiles(
            CopilotToolInvocationPolicyRequest request,
            ChangeVerificationGitLabScope scope,
            List<String> requestedFiles
    ) {
        var normalizedRequestedFiles = requestedFiles.stream()
                .filter(StringUtils::hasText)
                .map(ChangeVerificationGitLabScope::normalizePath)
                .toList();
        if (normalizedRequestedFiles.isEmpty()) {
            reject(
                    request,
                    "GitLab read tool call does not declare file path from the Change Verification scope.",
                    "Dla read toola podaj konkretna sciezke pliku z `changedFiles` albo `instructionSources` w `change-verification/repository-scope.md`."
            );
        }
        var rejectedFiles = normalizedRequestedFiles.stream()
                .filter(filePath -> !scope.fileAllowed(filePath))
                .toList();
        if (!rejectedFiles.isEmpty()) {
            reject(
                    request,
                    "GitLab read tool call targets files outside the Change Verification scope: " + rejectedFiles,
                    "Focused read jest ograniczony do plikow zmienionych w MR-kach oraz plikow instructions. Jezeli potrzebujesz innego pliku, najpierw opisz to jako limit albo zaproponuj rozszerzenie scope dla operatora."
            );
        }
    }

    private boolean changeVerificationGitLabInvocation(CopilotToolInvocationPolicyRequest request) {
        var context = hiddenContext(request);
        return request != null
                && request.toolName() != null
                && request.toolName().startsWith(GitLabToolNames.PREFIX)
                && ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE.equals(
                context.get(ChangeVerificationCopilotToolContextKeys.FEATURE)
        );
    }

    private Map<String, Object> arguments(CopilotToolInvocationPolicyRequest request) {
        if (!StringUtils.hasText(request.rawArguments())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(request.rawArguments(), ARGUMENTS_TYPE);
        }
        catch (Exception exception) {
            reject(
                    request,
                    "GitLab tool arguments could not be parsed for Change Verification scope validation.",
                    "Ponow wywolanie z poprawnym JSON argumentow toola i wartosciami z `change-verification/repository-scope.md`."
            );
            return Map.of();
        }
    }

    private List<String> requestedProjects(String toolName, Map<String, Object> arguments) {
        if (PROJECT_LIST_TOOLS.contains(toolName)) {
            return stringList(arguments.get("projectNames"));
        }
        if (GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS.equals(toolName)) {
            return chunkProjectNames(arguments.get("chunks"));
        }
        return List.of(stringValue(arguments.get("projectName")));
    }

    @SuppressWarnings("unchecked")
    private List<String> chunkProjectNames(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(entry -> stringValue(((Map<String, Object>) entry).get("projectName")))
                .filter(StringUtils::hasText)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> chunkFilePaths(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(entry -> stringValue(((Map<String, Object>) entry).get("filePath")))
                .filter(StringUtils::hasText)
                .toList();
    }

    private Map<String, Object> hiddenContext(CopilotToolInvocationPolicyRequest request) {
        return request != null && request.sessionContext() != null
                ? request.sessionContext().hiddenContext()
                : Map.of();
    }

    private void reject(CopilotToolInvocationPolicyRequest request, String reason, String instruction) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "denied_by_change_verification_scope_policy");
        result.put("toolName", request.toolName());
        result.put("toolCallId", request.toolCallId());
        result.put("reason", reason);
        result.put("instruction", instruction);
        result.put("groundingArtifact", "change-verification/repository-scope.md");
        result.put("retryableWithChangedArguments", true);
        throw new CopilotToolInvocationRejectedException(reason, result);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(ChangeVerificationCopilotGitLabScopePolicy::stringValue)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static String stringValue(Object value) {
        return value != null && StringUtils.hasText(value.toString()) ? value.toString().trim() : null;
    }

    private record ChangeVerificationGitLabScope(List<RepositoryScope> repositories) {

        static ChangeVerificationGitLabScope from(Map<String, Object> hiddenContext) {
            var value = hiddenContext.get(ChangeVerificationCopilotToolContextKeys.ALLOWED_REPOSITORIES);
            if (!(value instanceof Collection<?> repositories)) {
                return new ChangeVerificationGitLabScope(List.of());
            }
            return new ChangeVerificationGitLabScope(repositories.stream()
                    .filter(Map.class::isInstance)
                    .map(RepositoryScope::from)
                    .filter(Objects::nonNull)
                    .toList());
        }

        boolean projectAllowed(String requestedProject) {
            return StringUtils.hasText(requestedProject)
                    && repositories.stream().anyMatch(repository -> repository.matchesProject(requestedProject));
        }

        boolean branchAllowed(String branchRef) {
            return StringUtils.hasText(branchRef)
                    && repositories.stream().anyMatch(repository -> repository.matchesBranch(branchRef));
        }

        boolean fileAllowed(String filePath) {
            return StringUtils.hasText(filePath)
                    && repositories.stream().anyMatch(repository -> repository.matchesFile(filePath));
        }

        List<String> allowedProjectLabels() {
            return repositories.stream()
                    .flatMap(repository -> repository.projectLabels().stream())
                    .distinct()
                    .toList();
        }

        List<String> allowedBranches() {
            return repositories.stream()
                    .flatMap(repository -> repository.branches().stream())
                    .distinct()
                    .toList();
        }

        static String normalizePath(String value) {
            return normalize(value).replace('\\', '/');
        }
    }

    private record RepositoryScope(
            Set<String> projectAliases,
            Set<String> branches,
            Set<String> files
    ) {

        @SuppressWarnings("unchecked")
        static RepositoryScope from(Object value) {
            var source = (Map<String, Object>) value;
            var projectAliases = java.util.stream.Stream.of(
                    normalize(source.get("repositoryKey")),
                    normalize(source.get("projectPath")),
                    normalize(source.get("projectName"))
            ).filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            var branches = java.util.stream.Stream.of(
                    normalize(source.get("sourceRef")),
                    normalize(source.get("targetRef"))
            ).filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            var files = new java.util.LinkedHashSet<String>();
            addChangedFiles(files, source.get("changedFiles"));
            addInstructionFiles(files, source.get("instructionSources"));
            return new RepositoryScope(projectAliases, branches, Set.copyOf(files));
        }

        boolean matchesProject(String value) {
            var requested = normalize(value);
            if (!StringUtils.hasText(requested)) {
                return false;
            }
            return projectAliases.stream().anyMatch(alias -> alias.equals(requested)
                    || alias.endsWith("/" + requested)
                    || requested.endsWith("/" + alias));
        }

        boolean matchesBranch(String value) {
            return branches.contains(normalize(value));
        }

        boolean matchesFile(String value) {
            return files.contains(ChangeVerificationGitLabScope.normalizePath(value));
        }

        List<String> projectLabels() {
            return List.copyOf(projectAliases);
        }

        @SuppressWarnings("unchecked")
        private static void addChangedFiles(Set<String> files, Object value) {
            if (!(value instanceof Collection<?> changedFiles)) {
                return;
            }
            for (var changedFile : changedFiles) {
                if (changedFile instanceof Map<?, ?> map) {
                    addPath(files, ((Map<String, Object>) map).get("path"));
                    addPath(files, ((Map<String, Object>) map).get("oldPath"));
                    addPath(files, ((Map<String, Object>) map).get("newPath"));
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static void addInstructionFiles(Set<String> files, Object value) {
            if (!(value instanceof Collection<?> instructionSources)) {
                return;
            }
            for (var instructionSource : instructionSources) {
                if (instructionSource instanceof Map<?, ?> map) {
                    addPath(files, ((Map<String, Object>) map).get("path"));
                }
            }
        }

        private static void addPath(Set<String> files, Object value) {
            var path = ChangeVerificationGitLabScope.normalizePath(String.valueOf(value));
            if (StringUtils.hasText(path) && !"null".equals(path)) {
                files.add(path);
            }
        }
    }

    private static String normalize(Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return "";
        }
        return value.toString().trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
