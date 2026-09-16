package pl.mkn.tdw.features.uxinspector.ai.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetCandidate;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class UxInspectorTargetTools {
    private final String runId;
    private final UxInspectorTargetContext context;
    private final Map<String, UxInspectorTargetCandidate> candidatesByRef;
    private final Set<String> consumedTargetRefs = ConcurrentHashMap.newKeySet();

    public UxInspectorTargetTools(String runId, UxInspectorTargetContext context) {
        this.runId = runId;
        this.context = context;
        var refs = new LinkedHashMap<String, UxInspectorTargetCandidate>();
        for (var candidate : context.candidates()) {
            refs.put("uxi_" + UUID.randomUUID().toString().replace("-", ""), candidate);
        }
        candidatesByRef = Map.copyOf(refs);
    }

    @Tool(name = UxInspectorToolNames.LIST_TARGET_CANDIDATES,
            description = "Zwraca ograniczona liste kandydatow targetu z przypietego scope UX Inspectora. Scope pochodzi z hidden context; podaj tylko powod.")
    public CandidateListResult listTargetCandidates(
            @ToolParam(required = false, description = "Krotki powod potrzebny do audytu wywolania.") String reason,
            ToolContext toolContext
    ) {
        validateSession(toolContext);
        var entries = candidatesByRef.entrySet().stream().map(entry -> candidate(entry.getKey(), entry.getValue())).toList();
        return new CandidateListResult("ok", context.status().name(), entries, context.limitations());
    }

    @Tool(name = UxInspectorToolNames.READ_TARGET_SLICE,
            description = "Czyta zweryfikowany source slice kandydata zwroconego przez uxi_list_target_candidates w tej samej sesji. Nie przyjmuje repository, ref ani path.")
    public TargetSliceResult readTargetSlice(
            @ToolParam(description = "Opaque targetRef zwrocony przez uxi_list_target_candidates w tej samej sesji.") String targetRef,
            @ToolParam(required = false, description = "Krotki powod potrzebny do audytu wywolania.") String reason,
            ToolContext toolContext
    ) {
        validateSession(toolContext);
        var candidate = StringUtils.hasText(targetRef) ? candidatesByRef.get(targetRef.trim()) : null;
        if (candidate == null || !consumedTargetRefs.add(targetRef.trim())) {
            return new TargetSliceResult("rejected", "Unknown, replayed or out-of-scope targetRef.", targetRef,
                    null, null, null, List.of(), "");
        }
        return new TargetSliceResult("ok", "Verified target slice returned.", targetRef, candidate.sourcePath(),
                candidate.templatePath(), candidate.symbol(), candidate.matchReasons(), candidate.sourceSlice());
    }

    Map<String, UxInspectorTargetCandidate> candidatesByRef() {
        return candidatesByRef;
    }

    private Candidate candidate(String ref, UxInspectorTargetCandidate value) {
        return new Candidate(ref, value.score(), value.matchReasons(), value.symbol(), value.selector(),
                value.sourcePath(), value.templatePath(), value.templateLine());
    }

    private void validateSession(ToolContext toolContext) {
        var value = toolContext != null && toolContext.getContext() != null
                ? toolContext.getContext().get(AgentToolContextKeys.ANALYSIS_RUN_ID) : null;
        if (!(value instanceof String actual) || !runId.equals(actual)) {
            throw new IllegalStateException("UX Inspector target tool run scope mismatch.");
        }
    }

    public record CandidateListResult(String status, String resolutionStatus, List<Candidate> candidates,
                                      List<String> limitations) {}
    public record Candidate(String targetRef, int score, List<String> matchReasons, String symbol, String selector,
                            String sourcePath, String templatePath, Integer templateLine) {}
    public record TargetSliceResult(String status, String message, String targetRef, String sourcePath,
                                    String templatePath, String symbol, List<String> matchReasons, String sourceSlice) {}
}
