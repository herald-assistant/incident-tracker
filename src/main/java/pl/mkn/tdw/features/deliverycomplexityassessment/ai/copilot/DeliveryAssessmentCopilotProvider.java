package pl.mkn.tdw.features.deliverycomplexityassessment.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.*;
import pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryComplexityAssessmentProperties;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.*;
import pl.mkn.tdw.features.deliverycomplexityassessment.evidence.*;
import pl.mkn.tdw.shared.ai.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class DeliveryAssessmentCopilotProvider implements DeliveryUnitAssessmentProvider {
    private final DeliveryAssessmentCopilotRunRequestAssembler runRequestAssembler;
    private final CopilotRunPreparationService runPreparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final DeliveryAiResponseParser responseParser;
    private final CopilotPromptBudgetService budgetService;
    private final DeliveryPromptPreparationService prompts;
    private final DeliveryEvidencePacketBuilder packets;
    private final DeliveryPartFindingsParser findingsParser;
    private final ObjectMapper objectMapper;
    private final DeliveryComplexityAssessmentProperties properties;

    @Override
    public DeliveryUnitAiAnalysis analyze(String runReference, AnalysisAiOptions options, AnalysisAiAuthRef auth,
            DeliveryEvidencePacket packet, DeliveryPromptPreparation preparation, AnalysisAiActivityListener activity,
            AnalysisAiInvocationListener listener, Instant deadline) {
        var state = new RunState(runReference, options, auth, activity, listener, deadline);
        var whole = DeliveryEvidenceSlice.whole(packet.unit());
        var initialBudget = measure(state, preparation);
        if (!initialBudget.known() || initialBudget.fits()) {
            try {
                var response = call(state, preparation, "ASSESSMENT", whole.coverage(), 1, 1, responseParser::parse);
                return new DeliveryUnitAiAnalysis(response, state.usage());
            } catch (CopilotPromptOverflowException overflow) {
                correct(state, overflow, initialBudget);
            }
        }
        if (!properties.isOversizedEvidenceEnabled()) throw oversized(initialBudget, "Podział evidence jest wyłączony.");

        var rubric = java.util.Objects.requireNonNull(preparation.effectiveSkill(), "Missing effective rubric snapshot.");
        var manifest = "# Manifest całej dostawy\n\n" + String.join("\n", whole.coverage());
        var empty = packets.build(new pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnit(packet.unit().unitId(), packet.unit().issues(), List.of(), packet.unit().limitations()));
        var base = prompts.prepareFindings(empty, manifest, List.of(), rubric);
        var baseBudget = measure(state, base);
        if (!baseBudget.fits()) throw oversized(baseBudget, "JIRA_CONTEXT_TOO_LARGE: pełny Jira/Confluence, rubryka i manifest przekraczają budżet.");

        var pending = planSlices(state, packet, manifest, rubric, List.of(whole));
        var findings = new ArrayList<DeliveryPartFindings>();
        while (!pending.isEmpty()) {
            checkDeadline(state);
            var slice = pending.removeFirst();
            var part = prompts.prepareFindings(packets.build(slice.unit(packet.unit())), manifest, slice.coverage(), rubric);
            var budget = measure(state, part);
            if (!budget.fits()) {
                prepend(pending, planSlices(state, packet, manifest, rubric, split(slice, budget)).stream().toList());
                updatePartCounts(state, findings.size() + pending.size());
                continue;
            }
            try {
                var facts = call(state, part, "EVIDENCE_PART", slice.coverage(), findings.size() + 1,
                        findings.size() + pending.size() + 1, content -> findingsParser.parse(content, slice.coverage(), null));
                findings.add(facts);
            } catch (CopilotPromptOverflowException overflow) {
                correct(state, overflow, budget);
                prepend(pending, planSlices(state, packet, manifest, rubric, split(slice, budget)).stream().toList());
                updatePartCounts(state, findings.size() + pending.size());
            }
        }
        verifyCoverage(whole.coverage(), findings);
        if (findings.stream().anyMatch(f -> !f.sufficientEvidence())) return new DeliveryUnitAiAnalysis(
                new DeliveryAiResponse("INSUFFICIENT_EVIDENCE", null, minimumConfidence(findings), List.of(), List.of(), limits(findings)), state.usage());

        for (var level = 0; level <= properties.getMaxReductionLevels(); level++) {
            var synthesis = prompts.prepareSynthesis(packet, manifest, json(findings), rubric);
            var budget = measure(state, synthesis);
            if (budget.fits()) {
                try {
                    var response = call(state, synthesis, "SYNTHESIS", whole.coverage(), 1, 1, responseParser::parse);
                    var allLimits = new LinkedHashSet<>(packet.visibilityLimits());
                    allLimits.addAll(limits(findings)); allLimits.addAll(response.visibilityLimits());
                    allLimits.add("Analiza wieloczęściowa: finalna ocena opiera się na zweryfikowanych ustaleniach części, bez bezpośredniego odczytu całego surowego diffu w syntezie.");
                    var quality = new LinkedHashSet<>(response.qualityFlags()); quality.add("MULTIPART_SYNTHESIS");
                    return new DeliveryUnitAiAnalysis(new DeliveryAiResponse(response.classification(), response.dimensions(),
                            Math.min(response.confidence(), minimumConfidence(findings)), response.evidenceSummary(), List.copyOf(quality), List.copyOf(allLimits)), state.usage());
                } catch (CopilotPromptOverflowException overflow) { correct(state, overflow, budget); }
            }
            if (level == properties.getMaxReductionLevels()) throw oversized(budget, "SYNTHESIS_TOO_LARGE: osiągnięto limit redukcji ustaleń.");
            findings = reduce(state, packet, manifest, findings, rubric);
            verifyCoverage(whole.coverage(), findings);
        }
        throw new IllegalStateException("Synthesis did not complete.");
    }

    private ArrayDeque<DeliveryEvidenceSlice> planSlices(RunState state, DeliveryEvidencePacket packet, String manifest, String rubric, List<DeliveryEvidenceSlice> input) {
        var pending = new ArrayDeque<>(input);
        var planned = new ArrayDeque<DeliveryEvidenceSlice>();
        while (!pending.isEmpty()) {
            checkDeadline(state);
            var slice = pending.removeFirst();
            var prep = prompts.prepareFindings(packets.build(slice.unit(packet.unit())), manifest, slice.coverage(), rubric);
            var budget = measure(state, prep);
            if (budget.fits()) planned.addLast(slice);
            else prepend(pending, split(slice, budget));
            if (planned.size() + pending.size() + state.calls + 1 > properties.getMaxAiInvocationsPerUnit())
                throw new IllegalArgumentException("AI invocation limit is insufficient for all evidence parts and synthesis.");
        }
        return planned;
    }
    private List<DeliveryEvidenceSlice> split(DeliveryEvidenceSlice slice, CopilotPromptBudget budget) {
        try { return slice.split(); }
        catch (IllegalArgumentException failure) { throw oversized(budget, failure.getMessage()); }
    }
    private void updatePartCounts(RunState state, int total) {
        state.invocations.values().stream().filter(call -> "EVIDENCE_PART".equals(call.role()) && call.partCount() != total)
                .toList().forEach(call -> publish(state, call.withPartCount(total)));
    }

    private ArrayList<DeliveryPartFindings> reduce(RunState state, DeliveryEvidencePacket packet, String manifest, List<DeliveryPartFindings> input, String rubric) {
        var output = new ArrayList<DeliveryPartFindings>();
        var pending = new ArrayDeque<List<DeliveryPartFindings>>();
        pending.add(List.copyOf(input));
        while (!pending.isEmpty()) {
            checkDeadline(state);
            var group = pending.removeFirst();
            var prep = prompts.prepareReduction(packet, manifest, json(group), rubric);
            var budget = measure(state, prep);
            if (!budget.fits()) {
                if (group.size() <= 1) throw oversized(budget, "SYNTHESIS_ATOM_TOO_LARGE: pojedyncze ustalenia nie mieszczą się z kontekstem.");
                var cut = group.size() / 2;
                pending.addFirst(List.copyOf(group.subList(cut, group.size())));
                pending.addFirst(List.copyOf(group.subList(0, cut))); continue;
            }
            var coverage = group.stream().flatMap(f -> f.coverage().stream()).toList();
            var original = group.stream().flatMap(f -> f.findings().stream()).toList();
            try {
                var reduced = call(state, prep, "REDUCTION", coverage, output.size() + 1, output.size() + pending.size() + 1,
                        content -> findingsParser.parse(content, coverage, original));
                if (!reduced.sufficientEvidence()) throw new IllegalArgumentException("Reduction could not preserve sufficient evidence.");
                var preserved = new LinkedHashSet<>(limits(group)); preserved.addAll(reduced.visibilityLimits());
                output.add(new DeliveryPartFindings(coverage, true, reduced.findings(), Math.min(minimumConfidence(group), reduced.confidence()), List.copyOf(preserved)));
            } catch (CopilotPromptOverflowException overflow) {
                correct(state, overflow, budget); pending.addFirst(group);
            }
        }
        if (json(output).length() >= json(input).length()) throw new IllegalArgumentException("SYNTHESIS_REDUCTION_NO_PROGRESS: reduction did not reduce findings without losing evidence.");
        return output;
    }

    private <T> T call(RunState state, DeliveryPromptPreparation prep, String role, List<String> scope, int number, int count, Function<String,T> parser) {
        checkDeadline(state);
        if (++state.calls > properties.getMaxAiInvocationsPerUnit()) throw new IllegalArgumentException("AI invocation limit for this Delivery Unit was exceeded.");
        var session = prepare(state, prep);
        var budget = measurePrepared(state, session);
        var invocation = new AnalysisAiInvocation("call-" + state.calls, role, "RUNNING", number, count, scope,
                budget.estimatedInputTokens(), budget.promptTokenLimit(), budget.reservedTokens(), prep.prompt(), null,
                List.of(), List.of(), null, null, null, Instant.now(), null);
        publish(state, invocation);
        if (state.activity != null) state.activity.onAiActivity(new AnalysisAiActivityEvent(
                state.reference + ":" + invocation.invocationId(), null, "STATUS", "ASSESSMENT", "STARTED",
                "EVIDENCE_PART".equals(role) ? "Analiza części " + number + "/" + count : "SYNTHESIS".equals(role) ? "Łączenie wyników" : "REDUCTION".equals(role) ? "Porządkowanie ustaleń" : "Ocena dostawy",
                "Zakres evidence: " + scope.size() + " elementów.", null, null, null, null, Instant.now(), Map.of("role", role, "invocationId", invocation.invocationId())));
        try (var preparedSession = session) {
            var activeSession = state.activity != null && state.activity != AnalysisAiActivityListener.NO_OP
                    ? preparedSession.withActivitySink(state.activity::onAiActivity) : preparedSession;
            var result = executionGateway.execute(activeSession, budget, remaining(state));
            invocation = invocation.response(result.content() != null ? result.content() : "", result.sessionId(), result.usage());
            publish(state, invocation);
            var value = parser.apply(invocation.rawResponse());
            var facts = value instanceof DeliveryPartFindings f ? f.findings() : List.<AnalysisAiFinding>of();
            var limits = value instanceof DeliveryPartFindings f ? f.visibilityLimits() : value instanceof DeliveryAiResponse r ? r.visibilityLimits() : List.<String>of();
            invocation = invocation.completed(facts, limits); publish(state, invocation);
            checkDeadline(state);
            return value;
        } catch (RuntimeException failure) {
            publish(state, invocation.failed(failure.getMessage(), failure instanceof CopilotSdkInvocationException sdk ? sdk.usage() : null));
            throw failure;
        }
    }

    private CopilotPreparedSession prepare(RunState state, DeliveryPromptPreparation prep) {
        return runPreparationService.prepare(runRequestAssembler.assemble(state.reference, state.options, state.auth, prep));
    }
    private CopilotPromptBudget measure(RunState state, DeliveryPromptPreparation prep) {
        checkDeadline(state);
        try (var session = prepare(state, prep)) { return measurePrepared(state, session); }
    }
    private CopilotPromptBudget measurePrepared(RunState state, CopilotPreparedSession session) {
        var budget = budgetService.measure(session);
        if (state.limitOverride > 0) budget = budget.withLimit(state.limitOverride);
        return new CopilotPromptBudget((long) Math.ceil(budget.estimatedInputTokens() * state.tokenScale), budget.promptTokenLimit(),
                budget.defaultPromptTokenLimit(), budget.maxOutputTokens(), budget.reservedTokens(), budget.safetyRatio(), budget.requiresLongContext());
    }
    private void correct(RunState state, CopilotPromptOverflowException overflow, CopilotPromptBudget previous) {
        if (++state.corrections > properties.getMaxContextCorrections()) throw overflow;
        state.limitOverride = state.limitOverride > 0 ? Math.min(state.limitOverride, overflow.promptTokenLimit()) : overflow.promptTokenLimit();
        state.tokenScale *= Math.max(1.0, (double) overflow.inputTokens() / Math.max(1L, previous.estimatedInputTokens())) * 1.10;
        checkDeadline(state);
    }
    private void publish(RunState state, AnalysisAiInvocation invocation) {
        state.invocations.put(invocation.invocationId(), invocation);
        if (state.listener != null) state.listener.onInvocation(invocation);
    }
    private void prepend(ArrayDeque<DeliveryEvidenceSlice> pending, List<DeliveryEvidenceSlice> slices) {
        for (var i = slices.size() - 1; i >= 0; i--) pending.addFirst(slices.get(i));
    }
    private void verifyCoverage(List<String> expected, List<DeliveryPartFindings> groups) {
        var observed = groups.stream().flatMap(g -> g.coverage().stream()).toList();
        if (observed.size() != new HashSet<>(observed).size() || !new HashSet<>(expected).equals(new HashSet<>(observed)))
            throw new IllegalArgumentException("Partial analyses do not cover the entire Delivery Unit exactly once.");
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalArgumentException("Could not render typed findings.", failure); }
    }
    private List<String> limits(List<DeliveryPartFindings> findings) { return findings.stream().flatMap(f -> f.visibilityLimits().stream()).distinct().toList(); }
    private double minimumConfidence(List<DeliveryPartFindings> findings) { return findings.stream().mapToDouble(DeliveryPartFindings::confidence).min().orElse(0); }
    private RuntimeException oversized(CopilotPromptBudget budget, String reason) {
        return new IllegalArgumentException(reason + " Estimated tokens=" + budget.estimatedInputTokens() + ", prompt limit=" + budget.promptTokenLimit() + ", usable budget=" + budget.usableTokens() + ".");
    }
    private void checkDeadline(RunState state) {
        if (Thread.currentThread().isInterrupted() || !Instant.now().isBefore(state.deadline)) throw new IllegalArgumentException("Delivery Unit AI deadline exceeded.");
    }
    private Duration remaining(RunState state) { checkDeadline(state); return Duration.between(Instant.now(), state.deadline); }

    private static final class RunState {
        final String reference; final AnalysisAiOptions options; final AnalysisAiAuthRef auth;
        final AnalysisAiActivityListener activity; final AnalysisAiInvocationListener listener; final Instant deadline;
        final Map<String,AnalysisAiInvocation> invocations = new LinkedHashMap<>();
        long limitOverride; double tokenScale = 1.0; int calls; int corrections;
        RunState(String reference, AnalysisAiOptions options, AnalysisAiAuthRef auth, AnalysisAiActivityListener activity, AnalysisAiInvocationListener listener, Instant deadline) {
            this.reference=reference; this.options=options; this.auth=auth; this.activity=activity; this.listener=listener; this.deadline=deadline;
        }
        AnalysisAiUsage usage() { return AnalysisAiUsageTotals.sum(invocations.values().stream().map(AnalysisAiInvocation::usage).toList()); }
    }
}
