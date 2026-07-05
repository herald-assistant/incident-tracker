package pl.mkn.tdw.agenttools.operationalcontext.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.List;

import static pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames.GET_ENTITY;
import static pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames.GET_SCOPE;
import static pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames.LIST_ENTITIES;
import static pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames.SEARCH;
import static pl.mkn.tdw.agenttools.operationalcontext.mcp.OperationalContextToolDtos.OpctxEntityDetailResult;
import static pl.mkn.tdw.agenttools.operationalcontext.mcp.OperationalContextToolDtos.OpctxListEntitiesResult;
import static pl.mkn.tdw.agenttools.operationalcontext.mcp.OperationalContextToolDtos.OpctxScopeResult;
import static pl.mkn.tdw.agenttools.operationalcontext.mcp.OperationalContextToolDtos.OpctxSearchResult;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.operational-context", name = "enabled", havingValue = "true")
public class OperationalContextMcpTools {

    private static final String REASON_DESCRIPTION =
            "Short reason for the operator. Use one practical sentence.";

    private final OperationalContextPort operationalContextPort;
    private final OperationalContextToolMapper mapper;

    @Tool(
            name = GET_SCOPE,
            description = """
                    Returns available operational context catalog entity types and counts.
                    Use this to discover which catalog areas can be browsed or searched. It returns only catalog scope
                    metadata, not entity details.
                    """
    )
    public OpctxScopeResult getScope(
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var startedAt = System.nanoTime();
        logRequest(GET_SCOPE, toolContext, "request=no-args");
        var result = mapper.getScope(loadCatalog());
        logResult(
                GET_SCOPE,
                toolContext,
                startedAt,
                "entityTypes=%d".formatted(result.entityTypes().size())
        );
        return result;
    }

    @Tool(
            name = LIST_ENTITIES,
            description = """
                    Lists a paginated alphabetical index of operational context entities of one type.
                    Use this when the available catalog terms are not known yet and a table-of-contents style browse
                    is more useful than keyword search. Returns lightweight entity cards only.
                    """
    )
    public OpctxListEntitiesResult listEntities(
            @ToolParam(description = "Entity type: system, repository, codeSearchScope, process, integration, boundedContext, team, glossaryTerm or handoffRule.")
            String type,
            @ToolParam(required = false, description = "1-based page number. Defaults to 1.")
            Integer page,
            @ToolParam(required = false, description = "Page size from 1 to 50. Defaults to 20.")
            Integer pageSize,
            @ToolParam(required = false, description = "Optional simple text filter applied to id, name, summary, aliases and signals.")
            String filter,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var startedAt = System.nanoTime();
        logRequest(
                LIST_ENTITIES,
                toolContext,
                "type=%s page=%s pageSize=%s filter=%s".formatted(type, page, pageSize, filter)
        );
        var result = mapper.listEntities(loadCatalog(), type, page, pageSize, filter);
        logResult(
                LIST_ENTITIES,
                toolContext,
                startedAt,
                "type=%s page=%d items=%d totalItems=%d truncated=%s".formatted(
                        result.type(),
                        result.page(),
                        result.items().size(),
                        result.totalItems(),
                        result.truncated()
                )
        );
        return result;
    }

    @Tool(
            name = SEARCH,
            description = """
                    Searches operational context catalog entities by text and returns ranked candidates.
                    Use this when a system name, repository, process, bounded context, integration, team,
                    domain term or handoff clue is already known from another source.
                    """
    )
    public OpctxSearchResult search(
            @ToolParam(description = "Text query from evidence, user question or another tool result.")
            String query,
            @ToolParam(required = false, description = "Optional entity types to search. If omitted, all types are searched.")
            List<String> types,
            @ToolParam(required = false, description = "Maximum results to return from 1 to 20. Defaults to 8.")
            Integer limit,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var startedAt = System.nanoTime();
        logRequest(
                SEARCH,
                toolContext,
                "query=%s types=%s limit=%s".formatted(query, types, limit)
        );
        var result = mapper.search(loadCatalog(), query, types, limit);
        logResult(
                SEARCH,
                toolContext,
                startedAt,
                "query=%s results=%d truncated=%s".formatted(result.query(), result.results().size(), result.truncated())
        );
        return result;
    }

    @Tool(
            name = GET_ENTITY,
            description = """
                    Returns compact details for one operational context entity.
                    Use this after search or list results before relying on relationships, ownership, code-search scope,
                    handoff decision guidance, recognition signals or source limitations.
                    """
    )
    public OpctxEntityDetailResult getEntity(
            @ToolParam(description = "Entity type: system, repository, codeSearchScope, process, integration, boundedContext, team, glossaryTerm or handoffRule.")
            String type,
            @ToolParam(description = "Stable entity id returned by scope, list or search.")
            String id,
            @ToolParam(required = false, description = "Optional sections: overview, relations, signals, codeSearch, handoff, sourceCoverage, openQuestions.")
            List<String> include,
            @ToolParam(required = false, description = REASON_DESCRIPTION)
            String reason,
            ToolContext toolContext
    ) {
        var startedAt = System.nanoTime();
        logRequest(
                GET_ENTITY,
                toolContext,
                "type=%s id=%s include=%s".formatted(type, id, include)
        );
        var result = mapper.getEntity(loadCatalog(), type, id, include);
        logResult(
                GET_ENTITY,
                toolContext,
                startedAt,
                "type=%s id=%s sourceRefs=%d openQuestions=%d".formatted(
                        result.type(),
                        result.id(),
                        result.sourceRefs().size(),
                        result.openQuestions().size()
                )
        );
        return result;
    }

    private pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog loadCatalog() {
        return operationalContextPort.loadContext(new OperationalContextQuery(
                java.util.Set.of(),
                List.of(),
                false
        ));
    }

    private void logRequest(String toolName, ToolContext toolContext, String details) {
        log.info(
                "Tool request [{}] analysisRunId={} copilotSessionId={} toolCallId={} details={}",
                toolName,
                contextValue(toolContext, AgentToolContextKeys.ANALYSIS_RUN_ID),
                contextValue(toolContext, AgentToolContextKeys.COPILOT_SESSION_ID),
                contextValue(toolContext, AgentToolContextKeys.TOOL_CALL_ID),
                details
        );
    }

    private void logResult(String toolName, ToolContext toolContext, long startedAt, String details) {
        log.info(
                "Tool result [{}] analysisRunId={} copilotSessionId={} toolCallId={} durationMs={} details={}",
                toolName,
                contextValue(toolContext, AgentToolContextKeys.ANALYSIS_RUN_ID),
                contextValue(toolContext, AgentToolContextKeys.COPILOT_SESSION_ID),
                contextValue(toolContext, AgentToolContextKeys.TOOL_CALL_ID),
                (System.nanoTime() - startedAt) / 1_000_000,
                details
        );
    }

    private String contextValue(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        var value = toolContext.getContext().get(key);
        return value != null ? value.toString() : null;
    }
}
