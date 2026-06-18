package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlowExplorerArtifactService {

    static final String CONTEXT_SNAPSHOT_ARTIFACT = "flow-explorer/context-snapshot.json";
    static final String COMPACT_FLOW_MANIFEST_ARTIFACT = "flow-explorer/compact-flow-manifest.md";
    static final String SNIPPET_CARDS_ARTIFACT = "flow-explorer/snippet-cards.md";
    static final String COVERAGE_ARTIFACT = "flow-explorer/coverage.json";
    static final String RESPONSE_CONTRACT_ARTIFACT = "flow-explorer/response-contract.json";

    private final ObjectMapper objectMapper;

    public List<CopilotRenderedArtifact> renderArtifacts(
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot
    ) {
        return List.of(
                artifact(
                        CONTEXT_SNAPSHOT_ARTIFACT,
                        "Flow Explorer deterministic context snapshot",
                        "context-snapshot",
                        contextSnapshot != null ? contextSnapshot.flowNodes().size() : 0,
                        "application/json",
                        renderContextSnapshot(request, contextSnapshot)
                ),
                artifact(
                        COMPACT_FLOW_MANIFEST_ARTIFACT,
                        "Compact flow manifest for prompt grounding",
                        "compact-flow-manifest",
                        contextSnapshot != null ? contextSnapshot.flowNodes().size() : 0,
                        "text/markdown",
                        renderCompactFlowManifest(contextSnapshot)
                ),
                artifact(
                        SNIPPET_CARDS_ARTIFACT,
                        "Budgeted snippet cards embedded in the initial prompt",
                        "snippet-cards",
                        contextSnapshot != null ? contextSnapshot.snippetCards().size() : 0,
                        "text/markdown",
                        renderSnippetCards(contextSnapshot)
                ),
                artifact(
                        COVERAGE_ARTIFACT,
                        "Flow Explorer deterministic context coverage",
                        "coverage",
                        null,
                        "application/json",
                        renderCoverage(contextSnapshot)
                ),
                artifact(
                        RESPONSE_CONTRACT_ARTIFACT,
                        "Required Flow Explorer JSON response contract",
                        "response-contract",
                        null,
                        "application/json",
                        responseContract()
                )
        );
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
        return contextSnapshot.flowNodes().stream()
                .map(this::flowNodeLine)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- endpoint flow not resolved");
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

    public String responseContract() {
        return """
                {
                  "userIntentSummary": "string",
                  "audienceSummary": "string",
                  "endpointContract": {
                    "method": "string",
                    "path": "string",
                    "purpose": "string",
                    "request": ["string"],
                    "response": ["string"],
                    "parameters": ["string"]
                  },
                  "flowSteps": [
                    {
                      "order": 1,
                      "title": "string",
                      "plainLanguage": "string",
                      "technicalGrounding": "string",
                      "sourceRefs": ["string"]
                    }
                  ],
                  "businessRules": ["string"],
                  "validations": ["string"],
                  "persistence": ["string"],
                  "externalIntegrations": ["string"],
                  "testScenarios": ["string"],
                  "risksAndEdgeCases": ["string"],
                  "openQuestions": ["string"],
                  "visibilityLimits": ["string"],
                  "sourceReferences": ["string"],
                  "confidence": "high|medium|low"
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
        payload.put("systemId", request.systemId());
        payload.put("endpointId", request.endpointId());
        payload.put("httpMethod", request.httpMethod());
        payload.put("endpointPath", request.endpointPath());
        payload.put("branchOrRef", contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch());
        payload.put("documentationPreset", request.documentationPreset());
        payload.put("focusAreas", request.focusAreas());
        payload.put("userInstructionsPresent", StringUtils.hasText(request.userInstructions()));
        payload.put("contextSnapshot", contextSnapshot);
        return renderJson(payload);
    }

    private String renderCoverage(FlowExplorerContextSnapshot contextSnapshot) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("artifactFormatVersion", "flow-explorer-artifacts-v1");
        payload.put("generatedAt", Instant.now().toString());
        payload.put("coverage", contextSnapshot != null ? contextSnapshot.coverage() : null);
        payload.put("limitations", contextSnapshot != null ? contextSnapshot.limitations() : List.of());
        payload.put("suggestedNextReads", contextSnapshot != null ? contextSnapshot.suggestedNextReads() : List.of());
        return renderJson(payload);
    }

    private String renderJson(Object payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to render Flow Explorer artifact.", exception);
        }
    }

    private String flowNodeLine(FlowExplorerFlowNode node) {
        var methods = node.methods().isEmpty()
                ? "methods: none"
                : "methods: " + node.methods().stream()
                .map(method -> method.methodName() + lineRange(method.lineStart(), method.lineEnd()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        var reason = StringUtils.hasText(node.reason()) ? " reason: " + node.reason() : "";
        return "- [%s] %s %s%s".formatted(node.role(), node.filePath(), methods, reason);
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

    private String lineRange(int lineStart, int lineEnd) {
        if (lineStart <= 0) {
            return "";
        }
        return lineEnd > lineStart ? " L" + lineStart + "-L" + lineEnd : " L" + lineStart;
    }
}
