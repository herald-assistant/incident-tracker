package pl.mkn.tdw.features.flowexplorer.ai.preparation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerOpenApiEndpointContract;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlowExplorerArtifactService {

    static final String CONTEXT_SNAPSHOT_ARTIFACT = "flow-explorer/context-snapshot.json";
    static final String CANONICAL_TOOL_INPUTS_ARTIFACT = "flow-explorer/canonical-tool-inputs.md";
    static final String COMPACT_FLOW_MANIFEST_ARTIFACT = "flow-explorer/compact-flow-manifest.md";
    static final String SNIPPET_CARDS_ARTIFACT = "flow-explorer/snippet-cards.md";
    static final String OPENAPI_ENDPOINT_CONTRACT_ARTIFACT = "flow-explorer/openapi-endpoint-contract.md";
    static final String COVERAGE_ARTIFACT = "flow-explorer/coverage.json";
    static final String RESPONSE_CONTRACT_ARTIFACT = "flow-explorer/response-contract.json";

    private static final int COMPACT_FLOW_MANIFEST_MAX_NODES = GitLabEndpointUseCaseContextRequest.MAX_MAX_FILES;

    private final ObjectMapper objectMapper;

    public List<CopilotRenderedArtifact> renderArtifacts(
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot
    ) {
        var artifacts = new ArrayList<CopilotRenderedArtifact>();
        artifacts.add(artifact(
                CONTEXT_SNAPSHOT_ARTIFACT,
                "Flow Explorer deterministic context snapshot",
                "context-snapshot",
                contextSnapshot != null ? contextSnapshot.flowNodes().size() : 0,
                "application/json",
                renderContextSnapshot(request, contextSnapshot)
        ));
        artifacts.add(artifact(
                CANONICAL_TOOL_INPUTS_ARTIFACT,
                "Canonical model-facing inputs for Flow Explorer tools",
                "canonical-tool-inputs",
                canonicalToolInputCount(contextSnapshot),
                "text/markdown",
                renderCanonicalToolInputs(request, contextSnapshot)
        ));
        artifacts.add(artifact(
                COMPACT_FLOW_MANIFEST_ARTIFACT,
                "Compact flow manifest for prompt grounding",
                "compact-flow-manifest",
                compactFlowManifestNodeCount(contextSnapshot),
                "text/markdown",
                renderCompactFlowManifest(contextSnapshot)
        ));
        artifacts.add(artifact(
                SNIPPET_CARDS_ARTIFACT,
                "Endpoint method snippet cards embedded in the initial prompt",
                "snippet-cards",
                contextSnapshot != null ? contextSnapshot.snippetCards().size() : 0,
                "text/markdown",
                renderSnippetCards(contextSnapshot)
        ));
        if (hasOpenApiEndpointContract(contextSnapshot)) {
            artifacts.add(artifact(
                    OPENAPI_ENDPOINT_CONTRACT_ARTIFACT,
                    "OpenAPI endpoint contract slice embedded in the initial prompt",
                    "openapi-endpoint-contract",
                    contextSnapshot.openApiEndpointContracts().size(),
                    "text/markdown",
                    renderOpenApiEndpointContracts(contextSnapshot)
            ));
        }
        artifacts.add(artifact(
                COVERAGE_ARTIFACT,
                "Flow Explorer deterministic context coverage",
                "coverage",
                null,
                "application/json",
                renderCoverage(contextSnapshot)
        ));
        artifacts.add(artifact(
                RESPONSE_CONTRACT_ARTIFACT,
                "Fallback Flow Explorer JSON response contract",
                "response-contract",
                null,
                "application/json",
                responseContract()
        ));
        return List.copyOf(artifacts);
    }

    public Map<String, String> toArtifactContentMap(List<CopilotRenderedArtifact> artifacts) {
        var artifactContents = new LinkedHashMap<String, String>();
        for (var artifact : artifacts != null ? artifacts : List.<CopilotRenderedArtifact>of()) {
            artifactContents.put(artifact.displayName(), artifact.content());
        }
        return Collections.unmodifiableMap(artifactContents);
    }

    public String renderCompactFlowManifest(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null || contextSnapshot.flowNodes().isEmpty()) {
            return "- endpoint flow not resolved";
        }
        var nodes = contextSnapshot.flowNodes();
        var embeddedSnippetFilePaths = embeddedSnippetFilePaths(contextSnapshot);
        var renderedManifest = nodes.stream()
                .limit(COMPACT_FLOW_MANIFEST_MAX_NODES)
                .map(node -> flowNodeLine(node, embeddedSnippetFilePaths))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- endpoint flow not resolved");
        if (nodes.size() <= COMPACT_FLOW_MANIFEST_MAX_NODES) {
            return renderedManifest;
        }
        return renderedManifest + System.lineSeparator()
                + "- compact flow manifest truncated from " + nodes.size()
                + " to maxFlowNodes=" + COMPACT_FLOW_MANIFEST_MAX_NODES + ".";
    }

    public String renderSnippetCards(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null || contextSnapshot.snippetCards().isEmpty()) {
            return "- no snippet cards collected";
        }
        return contextSnapshot.snippetCards().stream()
                .map(this::snippetCard)
                .reduce((left, right) -> left + System.lineSeparator() + System.lineSeparator() + right)
                .orElse("- no snippet cards collected");
    }

    public String renderOpenApiEndpointContracts(FlowExplorerContextSnapshot contextSnapshot) {
        if (!hasOpenApiEndpointContract(contextSnapshot)) {
            return "- no OpenAPI endpoint contract collected";
        }
        return contextSnapshot.openApiEndpointContracts().stream()
                .map(this::openApiEndpointContract)
                .reduce((left, right) -> left + System.lineSeparator() + System.lineSeparator() + right)
                .orElse("- no OpenAPI endpoint contract collected");
    }

    public String renderCanonicalToolInputs(
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot
    ) {
        var branchRef = contextSnapshot != null && StringUtils.hasText(contextSnapshot.resolvedRef())
                ? contextSnapshot.resolvedRef()
                : request.branch();
        var builder = new StringBuilder();
        builder.append("# Canonical Tool Inputs").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Use these values exactly when a Flow Explorer GitLab or operational context tool needs scope.")
                .append(System.lineSeparator());
        builder.append("Do not rediscover repository scope or rebuild endpoint use-case context unless a required value below is missing.")
                .append(System.lineSeparator()).append(System.lineSeparator());

        builder.append("## Request Scope").append(System.lineSeparator());
        appendKeyValue(builder, "applicationName", request.systemId());
        appendKeyValue(builder, "systemId", request.systemId());
        appendKeyValue(builder, "endpointId", contextSnapshot != null ? contextSnapshot.endpointId() : request.endpointId());
        appendKeyValue(builder, "httpMethod", contextSnapshot != null ? contextSnapshot.httpMethod() : request.httpMethod());
        appendKeyValue(builder, "endpointPath", contextSnapshot != null ? contextSnapshot.endpointPath() : request.endpointPath());
        appendKeyValue(builder, "branchRef", branchRef);
        builder.append(System.lineSeparator());

        builder.append("## GitLab Repository Scope").append(System.lineSeparator());
        var repositories = contextSnapshot != null ? contextSnapshot.repositories() : List.<FlowExplorerRepositoryContext>of();
        if (repositories.isEmpty()) {
            builder.append("- repository scope not resolved; use `applicationName` for narrow discovery only if code evidence is required")
                    .append(System.lineSeparator());
        } else {
            repositories.stream()
                    .filter(FlowExplorerArtifactService::hasRepositoryIdentity)
                    .forEach(repository -> builder.append("- ")
                            .append(repository.selected() ? "selected " : "")
                            .append("projectName: `").append(safe(repository.projectName())).append("`")
                            .append(", projectPath: `").append(safe(repository.projectPath())).append("`")
                            .append(", branchRef: `").append(safe(repository.resolvedRef())).append("`")
                            .append(", searchMode: `").append(discoverySearchMode(repository)).append("`")
                            .append(", pathPrefixes: ").append(discoveryPathPrefixes(repository.pathPrefixes()))
                            .append(repository.attempted() ? ", attempted" : ", not-attempted")
                            .append(System.lineSeparator()));
        }
        builder.append(System.lineSeparator());

        builder.append("## Discovery Boundary Policy").append(System.lineSeparator());
        builder.append("- Treat `searchMode/pathPrefixes` as the default discovery scope for endpoint inventory, repository tree, content search, flow context and class-reference tools.")
                .append(System.lineSeparator());
        builder.append("- For `searchMode=path-prefixes`, pass listed `pathPrefixes` to discovery/search tools for that repository; for `whole-repository`, do not invent prefixes.")
                .append(System.lineSeparator());
        builder.append("- Explicit focused reads by concrete `filePath`, class, method selector, OpenAPI file or user/tool-provided prefix may go outside the default discovery scope.")
                .append(System.lineSeparator());
        builder.append("- When a focused read goes outside the default discovery scope, use the concrete target and report it as a visibility note instead of broad rediscovery.")
                .append(System.lineSeparator()).append(System.lineSeparator());

        builder.append("## File And Method Scope").append(System.lineSeparator());
        builder.append("- Use `").append(COMPACT_FLOW_MANIFEST_ARTIFACT)
                .append("` as the canonical filePath + methodSelector list for endpoint flow reads.")
                .append(System.lineSeparator());
        builder.append("- Combine manifest `filePath` and methods with `projectName` and `branchRef` from this artifact.")
                .append(System.lineSeparator());
        builder.append("- Prefer code already marked as embedded in `").append(SNIPPET_CARDS_ARTIFACT)
                .append("`; read GitLab only for concrete gaps.")
                .append(System.lineSeparator()).append(System.lineSeparator());

        var additionalFileInputs = additionalArtifactFileInputs(contextSnapshot);
        if (!additionalFileInputs.isEmpty()) {
            builder.append("## Additional Artifact File Paths").append(System.lineSeparator());
            additionalFileInputs.forEach(fileInput -> builder.append("- ")
                    .append(StringUtils.hasText(fileInput.projectName())
                            ? "`" + fileInput.projectName() + "` "
                            : "")
                    .append("`").append(fileInput.filePath()).append("`")
                    .append(StringUtils.hasText(fileInput.embeddedArtifact())
                            ? " (already embedded in " + fileInput.embeddedArtifact() + ")"
                            : "")
                    .append(System.lineSeparator()));
            builder.append(System.lineSeparator());
        }

        builder.append("## Canonical Scope Values").append(System.lineSeparator());
        builder.append("- Use these exact values when a runtime skill selects a GitLab tool.")
                .append(System.lineSeparator());
        builder.append("- Do not derive `projectName`, `branchRef` or `filePath` from application labels.")
                .append(System.lineSeparator());
        builder.append("- Do not pass `gitLabGroup`; backend resolves it from configuration or operational context.")
                .append(System.lineSeparator());
        return builder.toString().trim();
    }

    public String responseContract() {
        return """
                {
                  "goal": "DEEP_DISCOVERY|TEST_SCENARIOS|RISK_DETECTION",
                  "audience": "business_or_system_analyst_tester",
                  "overview": {
                    "markdown": "string",
                    "confidence": "high|medium|low",
                    "sourceRefs": ["string"]
                  },
                  "sections": [
                    {
                      "id": "only active sectionModes ids: FUNCTIONAL_FLOW|VALIDATIONS|PERSISTENCE|INTEGRATIONS",
                      "title": "string",
                      "mode": "compact|deep, never off",
                      "markdown": "string",
                      "sourceRefs": ["string"],
                      "visibilityLimits": ["string"],
                      "openQuestions": ["string"]
                    }
                  ],
                  "globalVisibilityLimits": ["string"],
                  "globalOpenQuestions": ["string"],
                  "sourceReferences": ["string"],
                  "confidence": "high|medium|low",
                  "followUpPrompts": ["string"]
                }
                """.trim();
    }

    public String renderLimitations(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null || contextSnapshot.limitations().isEmpty()) {
            return "- none";
        }
        return contextSnapshot.limitations().stream()
                .map(limitation -> "- " + limitation)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
    }

    public String renderContextClippingNotes(FlowExplorerContextSnapshot contextSnapshot) {
        var notes = contextClippingNoteList(contextSnapshot);
        if (notes.isEmpty()) {
            return "- none";
        }
        return notes.stream()
                .map(note -> "- " + note)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
    }

    private CopilotRenderedArtifact artifact(
            String displayName,
            String role,
            String category,
            Integer itemCount,
            String mimeType,
            String content
    ) {
        return new CopilotRenderedArtifact(
                displayName,
                role,
                "flow-explorer",
                category,
                itemCount,
                mimeType,
                content
        );
    }

    private String renderContextSnapshot(
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("artifactFormatVersion", "flow-explorer-artifacts-v1");
        payload.put("generatedAt", Instant.now().toString());
        payload.put("applicationName", request.systemId());
        payload.put("systemId", request.systemId());
        payload.put("endpointId", request.endpointId());
        payload.put("httpMethod", request.httpMethod());
        payload.put("endpointPath", request.endpointPath());
        payload.put("branchRef", contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch());
        payload.put("goal", request.goal());
        payload.put("focusAreas", request.focusAreas());
        payload.put("sectionModes", request.resolvedSectionModes());
        payload.put("userInstructionsPresent", StringUtils.hasText(request.userInstructions()));
        payload.put("contextSnapshot", contextSnapshotManifest(contextSnapshot));
        return renderJson(payload);
    }

    private Object contextSnapshotManifest(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null) {
            return null;
        }

        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("applicationName", contextSnapshot.systemId());
        manifest.put("systemId", contextSnapshot.systemId());
        manifest.put("systemName", contextSnapshot.systemName());
        manifest.put("requestedBranch", contextSnapshot.requestedBranch());
        manifest.put("branchRef", contextSnapshot.resolvedRef());
        manifest.put("endpointId", contextSnapshot.endpointId());
        manifest.put("httpMethod", contextSnapshot.httpMethod());
        manifest.put("endpointPath", contextSnapshot.endpointPath());
        manifest.put("endpoint", contextSnapshot.endpoint());
        manifest.put("repositories", contextSnapshot.repositories());
        manifest.put("flowNodes", contextSnapshot.flowNodes());
        manifest.put("relations", contextSnapshot.relations());
        manifest.put("snippetCards", contextSnapshot.snippetCards().stream()
                .map(this::snippetCardManifest)
                .toList());
        manifest.put("openApiEndpointContracts", contextSnapshot.openApiEndpointContracts().stream()
                .map(this::openApiEndpointContractManifest)
                .toList());
        manifest.put("limitations", contextSnapshot.limitations());
        manifest.put("suggestedNextReads", contextSnapshot.suggestedNextReads());
        manifest.put("coverage", contextSnapshot.coverage());
        return manifest;
    }

    private Map<String, Object> openApiEndpointContractManifest(FlowExplorerOpenApiEndpointContract contract) {
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("projectName", contract.projectName());
        manifest.put("filePath", contract.filePath());
        manifest.put("httpMethod", contract.httpMethod());
        manifest.put("endpointPath", contract.endpointPath());
        manifest.put("matchedPath", contract.matchedPath());
        manifest.put("operationId", contract.operationId());
        manifest.put("summary", contract.summary());
        manifest.put("description", contract.description());
        manifest.put("tags", contract.tags());
        manifest.put("sourceRef", contract.sourceRef());
        manifest.put("truncated", contract.truncated());
        manifest.put("characterCount", contract.characterCount());
        manifest.put("limitations", contract.limitations());
        manifest.put("contentArtifact", OPENAPI_ENDPOINT_CONTRACT_ARTIFACT);
        return manifest;
    }

    private Map<String, Object> snippetCardManifest(FlowExplorerSnippetCard card) {
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("id", card.id());
        manifest.put("projectName", card.projectName());
        manifest.put("filePath", card.filePath());
        manifest.put("role", card.role());
        manifest.put("methods", card.methods());
        manifest.put("requestedStartLine", card.requestedStartLine());
        manifest.put("requestedEndLine", card.requestedEndLine());
        manifest.put("returnedStartLine", card.returnedStartLine());
        manifest.put("returnedEndLine", card.returnedEndLine());
        manifest.put("totalLines", card.totalLines());
        manifest.put("truncated", card.truncated());
        manifest.put("reason", card.reason());
        manifest.put("characterCount", card.characterCount());
        manifest.put("limitations", card.limitations());
        manifest.put("contentArtifact", SNIPPET_CARDS_ARTIFACT);
        return manifest;
    }

    private String renderCoverage(FlowExplorerContextSnapshot contextSnapshot) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("artifactFormatVersion", "flow-explorer-artifacts-v1");
        payload.put("generatedAt", Instant.now().toString());
        payload.put("coverage", contextSnapshot != null ? contextSnapshot.coverage() : null);
        payload.put("limitations", contextSnapshot != null ? contextSnapshot.limitations() : List.of());
        payload.put("suggestedNextReads", contextSnapshot != null ? contextSnapshot.suggestedNextReads() : List.of());
        payload.put("contextClippingNotes", contextClippingNoteList(contextSnapshot));
        return renderJson(payload);
    }

    private List<String> contextClippingNoteList(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null) {
            return List.of("Flow Explorer did not receive a deterministic context snapshot.");
        }

        var notes = new ArrayList<String>();
        var flowNodeCount = contextSnapshot.flowNodes().size();
        if (flowNodeCount > COMPACT_FLOW_MANIFEST_MAX_NODES) {
            notes.add("compact-flow-manifest.md was truncated from " + flowNodeCount
                    + " to maxFlowNodes=" + COMPACT_FLOW_MANIFEST_MAX_NODES + ".");
        }

        var coverage = contextSnapshot.coverage();
        if (coverage != null && coverage.maxFilesReached()) {
            notes.add("GitLab endpoint use-case context reached maxFiles="
                    + GitLabEndpointUseCaseContextRequest.MAX_MAX_FILES
                    + "; deterministic flow manifest may omit lower-priority files.");
        }
        if (coverage != null && coverage.snippetBudgetReached()) {
            notes.add("snippet-cards.md was truncated to maxCards=20 before all eligible flow nodes were embedded.");
        }

        var truncatedCardCount = contextSnapshot.snippetCards().stream()
                .filter(FlowExplorerSnippetCard::truncated)
                .count();
        if (truncatedCardCount > 0) {
            notes.add(truncatedCardCount
                    + " snippet card(s) were truncated by GitLab read output limits; treat affected snippets as partial evidence.");
        }
        var truncatedOpenApiContractCount = contextSnapshot.openApiEndpointContracts().stream()
                .filter(FlowExplorerOpenApiEndpointContract::truncated)
                .count();
        if (truncatedOpenApiContractCount > 0) {
            notes.add(truncatedOpenApiContractCount
                    + " OpenAPI endpoint contract slice(s) were truncated; request/response schema evidence may be partial.");
        }
        return List.copyOf(notes);
    }

    private String renderJson(Object payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to render Flow Explorer artifact.", exception);
        }
    }

    private int canonicalToolInputCount(FlowExplorerContextSnapshot contextSnapshot) {
        return contextSnapshot != null
                ? contextSnapshot.repositories().size() + additionalArtifactFileInputs(contextSnapshot).size()
                : 0;
    }

    private int compactFlowManifestNodeCount(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null) {
            return 0;
        }
        return Math.min(contextSnapshot.flowNodes().size(), COMPACT_FLOW_MANIFEST_MAX_NODES);
    }

    private List<CanonicalFileInput> additionalArtifactFileInputs(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null) {
            return List.of();
        }

        var selectedProjectName = contextSnapshot.repositories().stream()
                .filter(repository -> repository.selected() && StringUtils.hasText(repository.projectName()))
                .map(FlowExplorerRepositoryContext::projectName)
                .findFirst()
                .orElse(null);
        var manifestFilePaths = contextSnapshot.flowNodes().stream()
                .map(FlowExplorerFlowNode::filePath)
                .filter(StringUtils::hasText)
                .map(FlowExplorerArtifactService::filePathKey)
                .collect(java.util.stream.Collectors.toSet());
        var inputs = new LinkedHashMap<String, CanonicalFileInput>();

        for (var contract : contextSnapshot.openApiEndpointContracts()) {
            if (!StringUtils.hasText(contract.filePath())) {
                continue;
            }
            if (manifestFilePaths.contains(filePathKey(contract.filePath()))) {
                continue;
            }
            var projectName = StringUtils.hasText(contract.projectName()) ? contract.projectName().trim() : selectedProjectName;
            var key = canonicalFileKey(projectName, contract.filePath());
            inputs.putIfAbsent(key, new CanonicalFileInput(
                    projectName,
                    contract.filePath().trim(),
                    OPENAPI_ENDPOINT_CONTRACT_ARTIFACT
            ));
        }

        for (var card : contextSnapshot.snippetCards()) {
            if (!StringUtils.hasText(card.filePath())) {
                continue;
            }
            if (manifestFilePaths.contains(filePathKey(card.filePath()))) {
                continue;
            }
            var projectName = StringUtils.hasText(card.projectName()) ? card.projectName().trim() : selectedProjectName;
            var key = canonicalFileKey(projectName, card.filePath());
            inputs.put(key, new CanonicalFileInput(
                    projectName,
                    card.filePath().trim(),
                    SNIPPET_CARDS_ARTIFACT
            ));
        }

        return List.copyOf(inputs.values());
    }

    private Set<String> embeddedSnippetFilePaths(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null) {
            return Set.of();
        }
        return contextSnapshot.snippetCards().stream()
                .map(FlowExplorerSnippetCard::filePath)
                .filter(StringUtils::hasText)
                .map(FlowExplorerArtifactService::filePathKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static boolean hasRepositoryIdentity(FlowExplorerRepositoryContext repository) {
        return repository != null
                && (StringUtils.hasText(repository.projectName()) || StringUtils.hasText(repository.projectPath()));
    }

    private String canonicalFileKey(String projectName, String filePath) {
        return safe(projectName) + ":" + safe(filePath);
    }

    private static String filePathKey(String filePath) {
        return StringUtils.hasText(filePath) ? filePath.trim() : "";
    }

    private void appendKeyValue(StringBuilder builder, String key, String value) {
        builder.append("- ").append(key).append(": `").append(safe(value)).append("`")
                .append(System.lineSeparator());
    }

    private String discoverySearchMode(FlowExplorerRepositoryContext repository) {
        return StringUtils.hasText(repository.searchMode())
                ? repository.searchMode().trim()
                : "whole-repository";
    }

    private String discoveryPathPrefixes(List<String> pathPrefixes) {
        if (pathPrefixes == null || pathPrefixes.isEmpty()) {
            return "[]";
        }
        return pathPrefixes.stream()
                .filter(StringUtils::hasText)
                .map(pathPrefix -> "`" + pathPrefix.trim() + "`")
                .reduce((left, right) -> left + ", " + right)
                .orElse("[]");
    }

    private static String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "<missing>";
    }

    private String flowNodeLine(FlowExplorerFlowNode node, Set<String> embeddedSnippetFilePaths) {
        var methods = node.methods().isEmpty()
                ? "methods: none"
                : "methods: " + node.methods().stream()
                .map(method -> method.methodName() + lineRange(method.lineStart(), method.lineEnd()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        var embedded = embeddedSnippetFilePaths.contains(filePathKey(node.filePath()))
                ? " embedded: " + SNIPPET_CARDS_ARTIFACT
                : "";
        var reason = StringUtils.hasText(node.reason()) ? " reason: " + node.reason() : "";
        return "- [%s] %s %s%s%s".formatted(node.role(), node.filePath(), methods, embedded, reason);
    }

    private String snippetCard(FlowExplorerSnippetCard card) {
        var methods = card.methods().isEmpty()
                ? "methods: none"
                : "methods: " + card.methods().stream()
                .map(method -> method.methodName() + lineRange(method.lineStart(), method.lineEnd()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        var reason = StringUtils.hasText(card.reason()) ? System.lineSeparator() + "reason: " + card.reason() : "";
        return """
                - [%s] %s:%s L%d-L%d %s%s
                ```java
                %s
                ```
                """.formatted(
                card.role(),
                card.projectName(),
                card.filePath(),
                card.returnedStartLine(),
                card.returnedEndLine(),
                methods,
                reason,
                card.content()
        ).trim();
    }

    private String openApiEndpointContract(FlowExplorerOpenApiEndpointContract contract) {
        var summary = StringUtils.hasText(contract.summary())
                ? System.lineSeparator() + "summary: " + contract.summary()
                : "";
        var description = StringUtils.hasText(contract.description())
                ? System.lineSeparator() + "description: " + contract.description()
                : "";
        return """
                - OPENAPI_CONTRACT %s `%s %s` matched `%s`%s%s
                %s
                """.formatted(
                contract.sourceRef(),
                contract.httpMethod(),
                contract.endpointPath(),
                safe(contract.matchedPath()),
                summary,
                description,
                contract.content()
        ).trim();
    }

    private boolean hasOpenApiEndpointContract(FlowExplorerContextSnapshot contextSnapshot) {
        return contextSnapshot != null && !contextSnapshot.openApiEndpointContracts().isEmpty();
    }

    private String lineRange(int lineStart, int lineEnd) {
        if (lineStart <= 0) {
            return "";
        }
        return lineEnd > lineStart ? " L" + lineStart + "-L" + lineEnd : " L" + lineStart;
    }

    private record CanonicalFileInput(
            String projectName,
            String filePath,
            String embeddedArtifact
    ) {
    }
}
