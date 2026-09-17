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
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendTypeScriptImportTarget;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendTypeScriptSliceTarget;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptDownstreamReference;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE;

@Component
@Slf4j
@RequiredArgsConstructor
public class GitLabFrontendMcpTools {

    private static final int MAX_REASON_CHARACTERS = 500;
    private static final int MAX_MEMBER_NAMES = 50;

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
                    Reads a focused TypeScript symbol slice without opaque references. Use direct mode with filePath and
                    declaringTypeName for a source target already shown in evidence. Use import mode with consumerFilePath,
                    moduleSpecifier and importedSymbol copied from the original code to follow an import. Optional memberNames
                    narrows the prepared allowed members. Hidden session context fixes the pinned repository, resolves imports
                    and rejects files, types, imports or members outside the prepared reachability graph. The result preserves
                    relevant original import lines, dependency injection fields, selected methods and required local helpers.
                    """
    )
    public GitLabFrontendToolDtos.TypeScriptSymbolSliceToolResponse readTypeScriptSymbolSlice(
            @ToolParam(required = false, description = "Direct mode: exact target TypeScript filePath shown in current evidence.")
            String filePath,
            @ToolParam(required = false, description = "Direct mode: exact declaring type exported by filePath.")
            String declaringTypeName,
            @ToolParam(required = false, description = "Import mode: exact current TypeScript file containing the visible import and call.")
            String consumerFilePath,
            @ToolParam(required = false, description = "Import mode: exact module specifier copied from the visible import statement.")
            String moduleSpecifier,
            @ToolParam(required = false, description = "Import mode: exact imported symbol used by the visible dependency injection or call.")
            String importedSymbol,
            @ToolParam(required = false, description = "Optional subset of exact member names already allowed for the resolved target.")
            List<String> memberNames,
            @ToolParam(description = "Krotki powod po polsku: jaka konkretna luka funkcjonalna wymaga symbol slice.")
            String reason,
            ToolContext toolContext
    ) {
        var context = context(toolContext);
        requireReason(reason);
        var target = context.requireTypeScriptTarget(
                filePath, declaringTypeName, consumerFilePath, moduleSpecifier, importedSymbol);
        var selectors = selectedMembers(target, memberNames);
        var response = typeScriptSymbolSliceService.readSymbolSlice(new GitLabTypeScriptSymbolSliceRequest(
                context.scope(),
                target.filePath(),
                target.declaringTypeName(),
                target.templatePath(),
                true,
                selectors,
                true,
                true,
                true,
                GitLabTypeScriptSymbolSliceService.DEFAULT_OUTPUT_CHARACTERS
        ));
        var downstream = response.downstreamReferences().stream()
                .map(reference -> context.withResolvedTargetPath(response.filePath(), reference))
                .toList();
        logResult(READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE, response.filePath(), response.status(), response.returnedCharacters());
        return GitLabFrontendToolDtos.TypeScriptSymbolSliceToolResponse.from(response, downstream);
    }

    private List<GitLabTypeScriptSymbolSelector> selectedMembers(
            GitLabFrontendTypeScriptSliceTarget target,
            List<String> requestedMembers
    ) {
        if (requestedMembers != null && requestedMembers.size() > MAX_MEMBER_NAMES) {
            throw new IllegalArgumentException("memberNames must contain at most 50 values");
        }
        var requested = new LinkedHashSet<String>();
        if (requestedMembers != null) {
            requestedMembers.stream().filter(StringUtils::hasText).map(String::trim).forEach(requested::add);
        }
        if (requested.isEmpty()) return target.symbolSelectors();
        var selected = target.symbolSelectors().stream()
                .filter(selector -> requested.contains(selector.name()))
                .toList();
        var selectedNames = selected.stream().map(selector -> selector.name())
                .collect(java.util.stream.Collectors.toSet());
        if (!selectedNames.containsAll(requested)) {
            throw new IllegalArgumentException("memberNames contains a symbol outside the allowed TypeScript target");
        }
        return selected;
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
        var importTargets = typeScriptImportTargets(values.get(GitLabFrontendToolContextKeys.TYPESCRIPT_IMPORT_TARGETS));
        return new FrontendToolContext(
                new GitLabFrontendRepositoryScope(group, projectName, ref, pathPrefixes),
                sourceRevision,
                screenSliceRef,
                targets,
                importTargets
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
            if (key instanceof String targetKey && target instanceof GitLabFrontendTypeScriptSliceTarget typedTarget) {
                result.put(targetKey, typedTarget);
            }
        });
        return Map.copyOf(result);
    }

    private Map<String, GitLabFrontendTypeScriptImportTarget> typeScriptImportTargets(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return Map.of();
        }
        var result = new java.util.LinkedHashMap<String, GitLabFrontendTypeScriptImportTarget>();
        values.forEach((key, target) -> {
            if (key instanceof String importKey && target instanceof GitLabFrontendTypeScriptImportTarget typedTarget) {
                result.put(importKey, typedTarget);
            }
        });
        return Map.copyOf(result);
    }

    private void logResult(String toolName, String target, String status, int returnedCharacters) {
        log.info(
                "Tool result [{}] target={} status={} returnedCharacters={}",
                toolName,
                target,
                status,
                returnedCharacters
        );
    }

    private record FrontendToolContext(
            GitLabFrontendRepositoryScope scope,
            String sourceRevision,
            String screenSliceRef,
            Map<String, GitLabFrontendTypeScriptSliceTarget> typeScriptTargets,
            Map<String, GitLabFrontendTypeScriptImportTarget> typeScriptImportTargets
    ) {
        private void requireScreenSliceRef(String requested) {
            if (!StringUtils.hasText(requested) || !screenSliceRef.equals(requested.trim())) {
                throw new IllegalArgumentException("sliceRef is not the selected screen reference for this session");
            }
        }

        private GitLabFrontendTypeScriptSliceTarget requireTypeScriptTarget(
                String filePath,
                String declaringTypeName,
                String consumerFilePath,
                String moduleSpecifier,
                String importedSymbol
        ) {
            var directMode = StringUtils.hasText(filePath) || StringUtils.hasText(declaringTypeName);
            var importMode = StringUtils.hasText(consumerFilePath)
                    || StringUtils.hasText(moduleSpecifier) || StringUtils.hasText(importedSymbol);
            if (directMode == importMode) {
                throw new IllegalArgumentException("Use exactly one TypeScript target mode: direct file/type or visible import");
            }
            GitLabFrontendTypeScriptSliceTarget target;
            if (directMode) {
                if (!StringUtils.hasText(filePath) || !StringUtils.hasText(declaringTypeName)) {
                    throw new IllegalArgumentException("Direct TypeScript mode requires filePath and declaringTypeName");
                }
                target = typeScriptTargets.get(GitLabFrontendTypeScriptSliceTarget.key(filePath, declaringTypeName));
            } else {
                if (!StringUtils.hasText(consumerFilePath)
                        || !StringUtils.hasText(moduleSpecifier) || !StringUtils.hasText(importedSymbol)) {
                    throw new IllegalArgumentException(
                            "Import TypeScript mode requires consumerFilePath, moduleSpecifier and importedSymbol");
                }
                var imported = typeScriptImportTargets.get(GitLabFrontendTypeScriptImportTarget.key(
                        consumerFilePath, moduleSpecifier, importedSymbol));
                target = imported != null ? imported.target() : null;
            }
            if (target == null) {
                throw new IllegalArgumentException("Requested code coordinates are not an allowed TypeScript target for this session");
            }
            return target;
        }

        private GitLabTypeScriptDownstreamReference withResolvedTargetPath(
                String consumerFilePath,
                GitLabTypeScriptDownstreamReference reference
        ) {
            if (reference == null || StringUtils.hasText(reference.targetSourcePath())) return reference;
            var imported = typeScriptImportTargets.get(GitLabFrontendTypeScriptImportTarget.key(
                    consumerFilePath, reference.moduleSpecifier(), reference.targetSymbol()));
            if (imported == null) return reference;
            return new GitLabTypeScriptDownstreamReference(
                    reference.kind(), reference.sourceSymbol(), reference.ownerSymbol(), reference.memberSymbol(),
                    reference.targetSymbol(), reference.moduleSpecifier(), imported.target().filePath());
        }
    }
}
