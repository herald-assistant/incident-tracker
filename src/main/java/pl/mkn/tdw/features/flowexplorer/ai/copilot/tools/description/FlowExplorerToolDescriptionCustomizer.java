package pl.mkn.tdw.features.flowexplorer.ai.copilot.tools.description;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer;

import java.util.ArrayList;
import java.util.List;

@Component
public class FlowExplorerToolDescriptionCustomizer implements CopilotToolDescriptionCustomizer {

    private static final String FLOW_EXPLORER_PROFILE_ID = "flow-explorer";
    private static final String GUIDANCE_HEADER = "Flow Explorer guidance:";

    @Override
    public String customize(CopilotToolDescriptionContext descriptionContext, String toolName, String description) {
        var baseDescription = StringUtils.hasText(description) ? description.trim() : "";
        if (!supports(descriptionContext)) {
            return baseDescription;
        }

        var guidance = guidanceFor(toolName);
        if (guidance.isEmpty() || baseDescription.contains(GUIDANCE_HEADER)) {
            return baseDescription;
        }

        var builder = new StringBuilder(baseDescription);
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(GUIDANCE_HEADER);
        for (var line : guidance) {
            builder.append("\n- ").append(line);
        }
        return builder.toString();
    }

    private boolean supports(CopilotToolDescriptionContext descriptionContext) {
        return descriptionContext != null && descriptionContext.matchesProfile(FLOW_EXPLORER_PROFILE_ID);
    }

    private List<String> guidanceFor(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return List.of();
        }

        var normalizedToolName = toolName.trim();
        if (GitLabToolNames.READ_JAVA_METHOD_SLICE.equals(normalizedToolName)) {
            return List.of(
                    "Use when a concrete class and method are known and you need the method body, conditions, helper logic or mapper details.",
                    "If you need to continue the downstream use-case flow from that method, use gitlab_build_java_method_use_case_context instead.",
                    "Check snippet-cards.md first and do not repeat methods already visible there unless a different overload, helper or related file is needed.",
                    "Use branchRef/projectName from canonical-tool-inputs.md and filePath/methods from compact-flow-manifest.md when available.",
                    "Pass minimal methodSelectors; usually methodName is enough and lineStart is optional.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (GitLabToolNames.BUILD_JAVA_METHOD_USE_CASE_CONTEXT.equals(normalizedToolName)) {
            return List.of(
                    "Use when compact-flow-manifest.md or a prior tool result identifies a concrete Java class and method and the downstream use-case path is still incomplete.",
                    "Use this to continue flow from the known method before issuing repeated focused reads.",
                    "Use maxResults to cap returned context.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE.equals(normalizedToolName)) {
            return List.of(
                    "Use when an OpenAPI/Swagger YAML file is known and endpoint contract details are needed.",
                    "Prefer this over gitlab_read_repository_file, file_chunk or file_chunks for OpenAPI YAML.",
                    "Use httpMethod, endpointPath, branchRef, projectName and filePath exactly from Flow Explorer artifacts when available.",
                    "Keep schemaDepth low; increase it only when request/response schemas are still unclear.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (GitLabToolNames.READ_REPOSITORY_FILE.equals(normalizedToolName)) {
            return List.of(
                    "Expensive in Flow Explorer. Use only when method context, method slice, outline or focused chunks are insufficient.",
                    "maxCharacters returns a prefix of the file; if truncated=true, continue with gitlab_read_repository_file_chunk instead of inferring the rest.",
                    "Do not use for OpenAPI YAML endpoint contracts; use gitlab_read_openapi_endpoint_slice instead.",
                    "Do not use to browse a full bean when snippet-cards.md already contains the endpoint method.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE.equals(normalizedToolName)) {
            return List.of(
                    "Use when you know a file but need class structure, annotations, inheritance, fields or method names before choosing the next focused read.",
                    "Use typeSummaries, constructorSummaries, methodSummaries and fieldSummaries because annotations are attached to the Java element where they appear.",
                    "For Flow Explorer PERSISTENCE section, use this only for concrete ORM gaps requested by flow-explorer-map-persistence-section; deep mode may require full table/column/source closure.",
                    "For Flow Explorer persistence, do not read DDL/Liquibase/Flyway/changelog files; infer table and column names from Java implementation, JPA/Hibernate annotations and ORM naming conventions.",
                    "Treat @Column as optional on @Entity fields/properties; do not report standard ORM naming inference as a visibility limit or report meta.",
                    "Follow with gitlab_build_java_method_use_case_context when continuing flow, or gitlab_read_java_method_slice when method body is needed.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (GitLabToolNames.READ_REPOSITORY_FILE_CHUNK.equals(normalizedToolName)
                || GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS.equals(normalizedToolName)) {
            return List.of(
                    "Fallback when method context, method slice or outline does not fit, parser context is missing or a line range is the only grounded input.",
                    "For Flow Explorer PERSISTENCE section, use chunks only to close exact ORM annotation gaps requested by flow-explorer-map-persistence-section; keep the range narrow.",
                    "Do not use chunks to read DDL/Liquibase/Flyway/changelog files for persistence mapping.",
                    "Do not use for OpenAPI YAML endpoint contracts; use gitlab_read_openapi_endpoint_slice instead.",
                    "Keep ranges tight and tied to a specific goal or sectionModes gap.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH.equals(normalizedToolName)) {
            return List.of(
                    "Use only for a small grounded set of files from endpoint use-case context or a focused follow-up question.",
                    "Prefer reading roles that change the requested documentation scope; skip unrelated models and mappers.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT.equals(normalizedToolName)) {
            return List.of(
                    "Use when initial Flow Explorer artifacts did not resolve the endpoint flow or a follow-up asks to rebuild it.",
                    "Do not call just to confirm a flow that compact-flow-manifest.md already provides.",
                    "Do not call to rediscover branchRef/projectName from canonical-tool-inputs.md or filePath from compact-flow-manifest.md.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (normalizedToolName.startsWith(GitLabToolNames.PREFIX)) {
            return List.of(
                    "Use only to close a concrete gap after checking canonical-tool-inputs.md, compact-flow-manifest.md and snippet-cards.md.",
                    "Use branchRef/projectName from canonical-tool-inputs.md and filePath/methods from compact-flow-manifest.md when available.",
                    "Follow flow-explorer-code-grounding for tool selection; let flow-explorer-map-persistence-section and flow-explorer-map-integrations-section define section-specific mapping gaps.",
                    "Always provide reason as one short Polish sentence for the operator."
            );
        }
        if (normalizedToolName.startsWith(OperationalContextToolNames.PREFIX)) {
            var guidance = new ArrayList<String>();
            guidance.add("Use operational context for catalog grounding: system, process, ownership, glossary, code-search scope or handoff.");
            guidance.add("Do not treat operational context as proof of Java runtime behavior; code evidence wins for endpoint flow.");
            guidance.add("Always provide reason as one short Polish sentence for the operator.");
            return List.copyOf(guidance);
        }
        return List.of();
    }
}
