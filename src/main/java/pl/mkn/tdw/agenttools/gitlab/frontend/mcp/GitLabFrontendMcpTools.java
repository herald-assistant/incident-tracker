package pl.mkn.tdw.agenttools.gitlab.frontend.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendTypeScriptSliceTarget;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceService;

import java.util.List;
import java.util.Map;

import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE;

@Component
@Slf4j
@RequiredArgsConstructor
public class GitLabFrontendMcpTools {

    private static final int MAX_REASON_CHARACTERS = 500;

    private final GitLabAngularRouteBranchSliceService routeBranchSliceService;
    private final GitLabTypeScriptSymbolSliceService typeScriptSymbolSliceService;

    @Tool(
            name = READ_FRONTEND_ROUTE_BRANCH_SLICE,
            description = """
                    Reads the focused Angular route branch identified by a safe sliceRef prepared for the current session.
                    The repository, ref, source revision and path boundary come exclusively from hidden runtime context.
                    Use this to inspect selected-route configuration, guards, resolvers, providers and child-route frontier
                    without reading unrelated route siblings or repository files.
                    """
    )
    public GitLabFrontendToolDtos.RouteBranchSliceToolResponse readRouteBranchSlice(
            @ToolParam(description = "Exact selected-screen slice reference from UI Explorer artifacts or an earlier frontend tool result.")
            String sliceRef,
            @ToolParam(description = "Krotki powod po polsku: jaka konkretna luka funkcjonalna wymaga route slice.")
            String reason,
            ToolContext toolContext
    ) {
        var context = context(toolContext);
        requireReason(reason);
        context.requireScreenSliceRef(sliceRef);
        var response = routeBranchSliceService.readBranchSlice(new GitLabAngularRouteBranchSliceRequest(
                context.scope(),
                context.screenSliceRef(),
                context.sourceRevision(),
                true,
                GitLabAngularRouteBranchSliceService.DEFAULT_OUTPUT_CHARACTERS
        ));
        logResult(READ_FRONTEND_ROUTE_BRANCH_SLICE, sliceRef, response.status(), response.returnedCharacters());
        return GitLabFrontendToolDtos.RouteBranchSliceToolResponse.from(sliceRef, response);
    }

    @Tool(
            name = READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
            description = """
                    Reads a focused TypeScript symbol slice identified by a safe component/dependency sliceRef prepared for
                    the current session. The hidden target fixes file path, declaring type, template and entry symbols.
                    Use this before a full file read when a reachable component, service, facade, guard, validator, NgRx unit
                    or backend client needs deeper source grounding.
                    """
    )
    public GitLabFrontendToolDtos.TypeScriptSymbolSliceToolResponse readTypeScriptSymbolSlice(
            @ToolParam(description = "Exact component or dependency slice reference from UI Explorer reachability artifacts or an earlier frontend tool result.")
            String sliceRef,
            @ToolParam(description = "Krotki powod po polsku: jaka konkretna luka funkcjonalna wymaga symbol slice.")
            String reason,
            ToolContext toolContext
    ) {
        var context = context(toolContext);
        requireReason(reason);
        var target = context.requireTypeScriptTarget(sliceRef);
        var response = typeScriptSymbolSliceService.readSymbolSlice(new GitLabTypeScriptSymbolSliceRequest(
                context.scope(),
                target.filePath(),
                target.declaringTypeName(),
                target.templatePath(),
                true,
                target.symbolSelectors(),
                true,
                true,
                true,
                GitLabTypeScriptSymbolSliceService.DEFAULT_OUTPUT_CHARACTERS
        ));
        logResult(READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE, sliceRef, response.status(), response.returnedCharacters());
        return GitLabFrontendToolDtos.TypeScriptSymbolSliceToolResponse.from(sliceRef, response);
    }

    private FrontendToolContext context(ToolContext toolContext) {
        var values = toolContext != null && toolContext.getContext() != null
                ? toolContext.getContext()
                : Map.<String, Object>of();
        var group = requiredString(values, AgentToolContextKeys.GITLAB_GROUP);
        var projectName = requiredString(values, GitLabFrontendToolContextKeys.PROJECT_NAME);
        var ref = requiredString(values, AgentToolContextKeys.GITLAB_BRANCH);
        var sourceRevision = requiredString(values, GitLabFrontendToolContextKeys.SOURCE_REVISION);
        var screenSliceRef = requiredString(values, GitLabFrontendToolContextKeys.SCREEN_SLICE_REF);
        var pathPrefixes = stringList(values.get(GitLabFrontendToolContextKeys.PATH_PREFIXES));
        var targets = typeScriptTargets(values.get(GitLabFrontendToolContextKeys.TYPESCRIPT_SLICE_TARGETS));
        return new FrontendToolContext(
                new GitLabFrontendRepositoryScope(group, projectName, ref, pathPrefixes),
                sourceRevision,
                screenSliceRef,
                targets
        );
    }

    private void requireReason(String reason) {
        if (!StringUtils.hasText(reason) || reason.trim().length() > MAX_REASON_CHARACTERS) {
            throw new IllegalArgumentException("reason must contain between 1 and 500 characters");
        }
    }

    private String requiredString(Map<String, Object> values, String key) {
        var value = values.get(key);
        if (value instanceof String string && StringUtils.hasText(string)) {
            return string.trim();
        }
        throw new IllegalStateException("Required hidden frontend GitLab scope is unavailable: " + key);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private Map<String, GitLabFrontendTypeScriptSliceTarget> typeScriptTargets(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return Map.of();
        }
        var result = new java.util.LinkedHashMap<String, GitLabFrontendTypeScriptSliceTarget>();
        values.forEach((key, target) -> {
            if (key instanceof String sliceRef && target instanceof GitLabFrontendTypeScriptSliceTarget typedTarget) {
                result.put(sliceRef, typedTarget);
            }
        });
        return Map.copyOf(result);
    }

    private void logResult(String toolName, String sliceRef, String status, int returnedCharacters) {
        log.info(
                "Tool result [{}] sliceRef={} status={} returnedCharacters={}",
                toolName,
                sliceRef,
                status,
                returnedCharacters
        );
    }

    private record FrontendToolContext(
            GitLabFrontendRepositoryScope scope,
            String sourceRevision,
            String screenSliceRef,
            Map<String, GitLabFrontendTypeScriptSliceTarget> typeScriptTargets
    ) {
        private void requireScreenSliceRef(String requested) {
            if (!StringUtils.hasText(requested) || !screenSliceRef.equals(requested.trim())) {
                throw new IllegalArgumentException("sliceRef is not the selected screen reference for this session");
            }
        }

        private GitLabFrontendTypeScriptSliceTarget requireTypeScriptTarget(String requested) {
            var normalized = StringUtils.hasText(requested) ? requested.trim() : null;
            var target = normalized != null ? typeScriptTargets.get(normalized) : null;
            if (target == null) {
                throw new IllegalArgumentException("sliceRef is not an allowed TypeScript target for this session");
            }
            return target;
        }
    }
}
