package pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.description;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.database.DatabaseToolNames;
import pl.mkn.tdw.agenttools.elasticsearch.ElasticToolNames;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CopilotIncidentToolGuidanceCatalog {

    private static final List<String> DATABASE_REASON_GUIDANCE = List.of(
            "For JPA/Hibernate, repository or data-access symptoms, first ground the entity/repository/table mapping from Java annotations, repository methods or queries; use DB discovery as fallback only when code grounding is unavailable.",
            "Use code-derived table, column and relation hints instead of guessing names from the exception label.",
            "Always provide reason as one short Polish sentence for the operator."
    );

    private static final List<String> OPERATIONAL_CONTEXT_REASON_GUIDANCE = List.of(
            "Use operational context as catalog grounding and scope guidance; do not treat it as standalone proof of the incident root cause.",
            "Use %s at most once when you need to discover available catalog types.".formatted(OperationalContextToolNames.GET_SCOPE),
            "Use %s for a table-of-contents style browse when you do not know the catalog term yet; browse one type at a time.".formatted(OperationalContextToolNames.LIST_ENTITIES),
            "Use %s when you have a concrete signal from logs, code, tool results or the user question.".formatted(OperationalContextToolNames.SEARCH),
            "Use %s before relying on ownership, handoff, process, bounded-context or code-search scope details.".formatted(OperationalContextToolNames.GET_ENTITY),
            "Always provide reason as one short Polish sentence for the operator."
    );

    private static final List<String> GITLAB_SCOPE_GUIDANCE = List.of(
            "Follow incident-analysis-gitlab-tools: use GitLab calls only to close a concrete incident evidence gap.",
            "Pass branchRef explicitly from the incident gitLabBranch or a previous GitLab tool result.",
            "Pass known projectName values from deterministic evidence, operational context or previous GitLab tool results.",
            "Do not pass gitLabGroup; the backend resolves GitLab group through operational context or configuration.",
            "Always provide reason as one short Polish sentence for the operator."
    );

    private static final Map<String, List<String>> GUIDANCE_BY_TOOL_NAME = Map.ofEntries(
            Map.entry(
                    ElasticToolNames.SUMMARIZE_HTTP_CALLS_BY_PATH,
                    List.of(
                            "Use for opaque downstream/external HTTP failures when a grounded path or stable path prefix is known.",
                            "Use before fetching comparison logs; let the returned status buckets and samples decide which concrete calls are worth inspecting.",
                            "Do not invent paths. Use paths from logs, user input, artifacts or prior tool results.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    ElasticToolNames.FETCH_HTTP_CALL_LOGS,
                    List.of(
                            "Use after path summary or when the user asks for current incident log details.",
                            "If path is omitted, the current hidden incident correlationId is used.",
                            "If path is provided, the tool searches by that path without forcing the current incident correlationId; this is intended for comparison calls.",
                            "Prefer SUMMARY or COMPACT first; use FULL only when truncated log details block the diagnosis.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.LIST_AVAILABLE_REPOSITORIES,
                    List.of(
                            "Use before GitLab search/read tools when projectName or GitLab path is not known from evidence.",
                            "Use returned projectName values as inputs for GitLab search, flow context and read tools.",
                            "When codeSearchScopes are returned, prefer the matching semantic target scope and pass all its projectNames together.",
                            "Call once per investigation unless new evidence clearly points to another repository.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.READ_REPOSITORY_FILE,
                    List.of(
                            "Expensive. Use only when outline/chunk tools are insufficient.",
                            "Consider %s when a known Java method needs downstream use-case flow continuation.".formatted(
                                    GitLabToolNames.BUILD_JAVA_METHOD_USE_CASE_CONTEXT
                            ),
                            "Prefer %s for method body, predicates, mapper logic, validation or client calls; use %s/%s when method slicing does not fit.".formatted(
                                    GitLabToolNames.READ_JAVA_METHOD_SLICE,
                                    GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                                    GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS
                            ),
                            "Do not use for broad browsing.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH,
                    List.of(
                            "Use after a grounded file list, especially from gitlab_build_endpoint_use_case_context or gitlab_build_java_method_use_case_context.",
                            "Pass only files that are relevant to the current endpoint/use case question.",
                            "Prefer categories or roles from the prior context result instead of reading every returned file.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.BUILD_JAVA_METHOD_USE_CASE_CONTEXT,
                    List.of(
                            "Use when code evidence, endpoint context or flow context identifies a concrete Java class and method but downstream use-case flow, handoff, integration or persistence behavior is still missing.",
                            "Use this before repeated focused reads when the missing evidence is what happens after a known method.",
                            "Use filePath, lineNumber, parameterCount or parameterTypes only to disambiguate the entry method.",
                            "Use maxResults to cap returned context.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.LIST_REPOSITORY_ENDPOINTS,
                    List.of(
                            "Use when the user or follow-up asks about a concrete HTTP endpoint, endpoint family or whether a requirement belongs to an existing endpoint.",
                            "Prefer endpointPathPrefix/httpMethod filters when known; avoid broad repository inventories during incident root-cause work.",
                            "Use returned endpointId, controller file path and line range as grounded inputs for focused read tools.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                    List.of(
                            "Use when project or file is unclear.",
                            "When operational context lists codeSearchScopes or multiple codeSearchProjects for the matched semantic target, search them as one implementation scope, including supporting repositories.",
                            "Prefer focused terms from stacktrace, exception, class, entity, repository or service names.",
                            "Use to ground the Technical Handoff v1 location, flow and recommended action when TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED is listed.",
                            "Do not use repeatedly with broad generic terms.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE,
                    List.of(
                            "Use before full file reads to understand class role, annotations, inheritance, fields and available method signatures.",
                            "For JPA/Hibernate mapping, prefer entity/repository annotations before migration files unless mappings are incomplete or inconsistent.",
                            "Follow up with gitlab_build_java_method_use_case_context when downstream use-case flow is missing; use gitlab_read_java_method_slice when a specific method body matters.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.READ_JAVA_METHOD_SLICE,
                    List.of(
                            "Focused read for a known Java file and method body.",
                            "Use methodSelectors with methodName first; lineStart is optional and only needed to narrow a specific overload.",
                            "Use when the question is about a method body, predicate, mapper, validator, helper or client call.",
                            "If the missing evidence is downstream use-case flow from this method, use gitlab_build_java_method_use_case_context instead.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                    List.of(
                            "Use when method slicing does not fit, for example non-Java files, parser limits or a line range without a method name.",
                            "Keep line ranges tight and tied to a concrete evidence gap.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS,
                    List.of(
                            "Use for a small set of directly related files needed to explain the flow.",
                            "Avoid batch browsing unrelated candidates.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.FIND_FLOW_CONTEXT,
                    List.of(
                            "Use when evidence coverage says broader upstream/downstream flow context is missing.",
                            "Use when TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED is listed to identify the smallest executable flow for technicalAnalysis.",
                            "Expect common Spring integration patterns such as Feign, RestClient, WebClient, RestTemplate, StreamBridge and Consumer/Function/Supplier bindings.",
                            "If operational context points to a codeSearchScope with supporting repositories for the semantic target, include those projects when they may contain the collaborator deciding the flow.",
                            "Use focused keywords grounded in logs, stacktrace, code evidence or current tool results.",
                            "Use recommended next reads rather than launching broad searches.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    GitLabToolNames.FIND_CLASS_REFERENCES,
                    List.of(
                            "Use when a concrete class from stacktrace or code evidence needs ownership or caller context.",
                            "If the class is not found in the main repository, make one focused retry across the matching operational-context codeSearchScope or codeSearchProjects before declaring it missing.",
                            "Prefer class names and related hints from existing evidence.",
                            "Always provide reason as one short Polish sentence for the operator."
                    )
            ),
            Map.entry(
                    DatabaseToolNames.SAMPLE_ROWS,
                    List.of(
                            "Use only after exact count/group checks and only for minimal technical projections.",
                            "Do not use to browse business data."
                    )
            ),
            Map.entry(
                    DatabaseToolNames.JOIN_SAMPLE,
                    List.of(
                            "Use only after exact join counts and explicit projected columns are known.",
                            "Do not use to browse business data."
                    )
            ),
            Map.entry(
                    DatabaseToolNames.EXECUTE_READONLY_SQL,
                    List.of(
                            "Last resort only.",
                            "Use typed DB tools first.",
                            "May be disabled by runtime policy and budget."
                    )
            ),
            Map.entry(
                    OperationalContextToolNames.GET_SCOPE,
                    List.of(
                            "Use to discover which neutral operational context entity types are available; do not repeat in the same investigation."
                    )
            ),
            Map.entry(
                    OperationalContextToolNames.LIST_ENTITIES,
                    List.of(
                            "Use when the model does not yet know the relevant catalog term, process, bounded context, integration or glossary term.",
                            "Prefer a filter when any clue is available and avoid paging through the whole catalog."
                    )
            ),
            Map.entry(
                    OperationalContextToolNames.SEARCH,
                    List.of(
                            "Use for grounded signals such as system names, endpoints, queues, repositories, classes, domain terms or handoff clues.",
                            "Follow promising search results with opctx_get_entity before making final claims."
                    )
            ),
            Map.entry(
                    OperationalContextToolNames.GET_ENTITY,
                    List.of(
                            "Use after list or search results to confirm relations, recognition signals, code-search hints, handoff hints and source limitations.",
                            "Do not name affectedProcess, affectedBoundedContext or affectedTeam unless the entity also fits incident evidence."
                    )
            )
    );

    public List<String> guidanceFor(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return List.of();
        }

        var normalizedToolName = toolName.trim();
        var guidance = GUIDANCE_BY_TOOL_NAME.getOrDefault(normalizedToolName, List.of());
        if (normalizedToolName.startsWith(GitLabToolNames.PREFIX)) {
            var gitLabGuidance = new ArrayList<>(guidance);
            gitLabGuidance.addAll(GITLAB_SCOPE_GUIDANCE);
            return List.copyOf(gitLabGuidance);
        }
        if (!normalizedToolName.startsWith(DatabaseToolNames.PREFIX)) {
            if (normalizedToolName.startsWith(OperationalContextToolNames.PREFIX)) {
                var operationalContextGuidance = new ArrayList<>(guidance);
                operationalContextGuidance.addAll(OPERATIONAL_CONTEXT_REASON_GUIDANCE);
                return List.copyOf(operationalContextGuidance);
            }
            return guidance;
        }

        var databaseGuidance = new ArrayList<>(guidance);
        databaseGuidance.addAll(DATABASE_REASON_GUIDANCE);
        return List.copyOf(databaseGuidance);
    }
}
