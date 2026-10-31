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
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService;
import java.util.Map;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+46)
@RequiredArgsConstructor
public class ReleaseSummaryToolPolicy implements CopilotToolInvocationPolicy {
    public static final Set<String> ALLOWED=Set.of("gitlab_list_repository_branches","gitlab_list_repository_tree",
            "gitlab_list_repository_files","gitlab_search_repository_files","gitlab_read_repository_file","gitlab_read_repository_file_chunk",
            "opctx_get_scope","opctx_list_entities","opctx_search","opctx_get_entity",
            "report_get_current","report_read_section_chunk","report_upsert_section","report_update_header","report_update_meta");
    private static final Set<String> MUTATIONS=Set.of("report_upsert_section","report_update_header","report_update_meta");
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
            if(request.toolName().equals("report_upsert_section"))
                reports.validateSection(args.path("id").asText(),args.path("markdown").asText(),context.evidence,context.sources());
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
            if(event.toolName().equals("opctx_get_entity")&&result!=null&&result.hasNonNull("id")&&result.hasNonNull("type"))
                context.knownSources.add("opctx:"+result.path("type").asText()+":"+result.path("id").asText());
            context.privateCheckpoint.run();
            if(MUTATIONS.contains(event.toolName())&&result!=null&&result.path("status").asText().equals("ok")) {
                var id=event.sessionContext().hiddenContext().get(AgentToolContextKeys.REPORT_ID);
                reportStore.current((String)id).ifPresent(report->{context.reportMutated=true;context.checkpoint.accept(report);});
            }
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {
            // Failed/unstructured output cannot register a citation or report checkpoint.
        }
    }
}

