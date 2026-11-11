package pl.mkn.tdw.features.releasesummary.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.context.event.EventListener;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.*;
import pl.mkn.tdw.aiplatform.copilot.tools.events.*;
import pl.mkn.tdw.aiplatform.copilot.tools.report.*;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService;
import java.util.Map;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+46)
@RequiredArgsConstructor
public class ReleaseSummaryToolPolicy implements CopilotToolInvocationPolicy {
    public static final Set<String> ALLOWED = Set.of(
            GitLabToolNames.LIST_REPOSITORY_BRANCHES,
            GitLabToolNames.LIST_REPOSITORY_TREE,
            GitLabToolNames.LIST_REPOSITORY_FILES,
            GitLabToolNames.SEARCH_REPOSITORY_FILES,
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.LIST_REPOSITORY_COMMITS,
            GitLabToolNames.READ_COMMIT_DIFF,
            OperationalContextToolNames.GET_SCOPE,
            OperationalContextToolNames.LIST_ENTITIES,
            OperationalContextToolNames.SEARCH,
            OperationalContextToolNames.GET_ENTITY,
            CopilotReportToolNames.GET_CURRENT,
            CopilotReportToolNames.READ_SECTION_CHUNK,
            CopilotReportToolNames.UPSERT_SECTION,
            CopilotReportToolNames.PATCH_SECTION,
            CopilotReportToolNames.UPDATE_HEADER,
            CopilotReportToolNames.UPDATE_META
    );
    private static final Set<String> MUTATIONS = Set.of(
            CopilotReportToolNames.UPSERT_SECTION,
            CopilotReportToolNames.PATCH_SECTION,
            CopilotReportToolNames.UPDATE_HEADER,
            CopilotReportToolNames.UPDATE_META
    );
    private final ObjectMapper mapper;
    private final ReleaseSummaryReportService reports;
    private final CopilotReportSessionStore reportStore;
    @Override public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        var raw=request.sessionContext().hiddenContext().get(ReleaseSummaryAiContext.KEY);
        if(!(raw instanceof ReleaseSummaryAiContext context)) return;
        try {
            if(!ALLOWED.contains(request.toolName())) throw new IllegalArgumentException("Narzędzie jest poza zakresem Release summary.");
            if(MUTATIONS.contains(request.toolName())&&!context.reportWritable) throw new IllegalArgumentException("Ta wiadomość nie zezwala na zmianę raportu.");
            var args=mapper.readTree(request.rawArguments());
            if(args==null||!args.isObject()) throw new IllegalArgumentException("Wymagane argumenty JSON.");
            if(request.toolName().equals(CopilotReportToolNames.UPSERT_SECTION)) {
                reports.validateOrder(args.path("id").asText(),args.path("order").asInt(),context.evidence);
                reports.validatePublication(args.path("id").asText(),args.path("markdown").asText(),
                        args.hasNonNull("meta")?mapper.treeToValue(args.get("meta"),AnalysisReportMeta.class):AnalysisReportMeta.empty(),context.evidence,context.sources());
                context.sectionIds.addIfAbsent(args.path("id").asText());
            }
            if(request.toolName().equals(CopilotReportToolNames.PATCH_SECTION)) {
                var reportId=(String)request.sessionContext().hiddenContext().get(AgentToolContextKeys.REPORT_ID);
                var sectionId=args.path("sectionId").asText();
                var section=reportStore.current(reportId).orElseThrow().sections().stream().filter(s->s.id().equals(sectionId)).findFirst().orElseThrow();
                var old=args.path("oldText").asText();var replacement=args.path("newText").asText();
                var candidate=CopilotReportTextPatch.apply(section.markdown(),old,replacement);
                reports.validatePublication(sectionId,candidate,section.meta(),context.evidence,context.sources());
            }
            if(args.hasNonNull("meta")) reports.validateMeta(mapper.treeToValue(args.get("meta"),AnalysisReportMeta.class),context.sources());
        } catch(Exception e) {
            throw new CopilotToolInvocationRejectedException(e.getMessage(),
                    Map.of("status","rejected","message",e.getMessage()!=null?e.getMessage():"Nieprawidłowe wywołanie."));
        }
    }
    @EventListener public void completed(CopilotToolInvocationFinishedEvent event) {
        var raw=event.sessionContext().hiddenContext().get(ReleaseSummaryAiContext.KEY);
        if(!(raw instanceof ReleaseSummaryAiContext context)||event.outcome()!=CopilotToolInvocationOutcome.COMPLETED) return;
        try {
            var result=mapper.readTree(event.rawResult());
            if(event.toolName().equals(OperationalContextToolNames.GET_ENTITY)&&result!=null&&result.hasNonNull("id")&&result.hasNonNull("type"))
                context.knownSources.add("opctx:"+result.path("type").asText()+":"+result.path("id").asText());
            context.privateCheckpoint.run();
            if(MUTATIONS.contains(event.toolName())&&result!=null&&result.path("status").asText().equals("ok")) {
                var id=event.sessionContext().hiddenContext().get(AgentToolContextKeys.REPORT_ID);
                reportStore.current((String)id).filter(report->!report.sections().isEmpty())
                        .ifPresent(report->{context.reportMutated=true;context.checkpoint.accept(report);});
            }
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {
            // Failed/unstructured output cannot register a citation or report checkpoint.
        }
    }
}

