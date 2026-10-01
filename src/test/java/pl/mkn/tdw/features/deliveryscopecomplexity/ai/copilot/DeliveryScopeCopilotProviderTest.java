package pl.mkn.tdw.features.deliveryscopecomplexity.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.*;
import com.github.copilot.rpc.*;
import pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeComplexityProperties;
import pl.mkn.tdw.features.deliveryscopecomplexity.ai.*;
import pl.mkn.tdw.features.deliveryscopecomplexity.evidence.*;
import pl.mkn.tdw.shared.ai.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.*;

class DeliveryScopeCopilotProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CopilotRunPreparationService preparation = mock(CopilotRunPreparationService.class);
    private final CopilotSdkExecutionGateway execution = mock(CopilotSdkExecutionGateway.class);
    private final CopilotPromptBudgetService budgets = mock(CopilotPromptBudgetService.class);
    private final CopilotSkillRuntimeLoader skills = mock(CopilotSkillRuntimeLoader.class);
    private final DeliveryAiResponseParser parser = spy(new DeliveryAiResponseParser(mapper));
    private final DeliveryEvidencePacketBuilder packets = new DeliveryEvidencePacketBuilder();
    private final DeliveryScopeComplexityProperties properties = new DeliveryScopeComplexityProperties();
    private final List<CopilotPreparedSession> sent = new ArrayList<>();
    private final LinkedHashMap<String,AnalysisAiInvocation> ledger = new LinkedHashMap<>();
    private DeliveryPromptPreparationService prompts;
    private DeliveryScopeCopilotProvider provider;
    private long limit = 30_000;
    private boolean largeFindings;

    @BeforeEach
    void setup() {
        when(skills.availableSkills()).thenReturn(List.of(new CopilotRuntimeSkill("delivery-scope-complexity-evaluator", "CRM rubric", 1,
                "CRM rubric", "CRM rubric", CopilotRuntimeSkillState.DEFAULT, true)));
        prompts = new DeliveryPromptPreparationService(skills);
        when(preparation.prepare(any())).thenAnswer(call -> {
            CopilotRunRequest request=call.getArgument(0);
            return new CopilotPreparedSession(request.runReference(), new CopilotClientOptions(), new SessionConfig().setModel("crm-mini"),
                    new MessageOptions().setPrompt(request.prompt()), request.prompt(), request.artifactContents());
        });
        when(budgets.measure(any())).thenAnswer(call -> {
            CopilotPreparedSession session=call.getArgument(0);
            return new CopilotPromptBudget(session.prompt().length(), limit, limit, 1000, 0, 1.0, false);
        });
        when(execution.execute(any(), any(), any())).thenAnswer(call -> answer(call.getArgument(0)));
        provider = new DeliveryScopeCopilotProvider(new DeliveryScopeCopilotRunRequestAssembler(new CopilotRunAuthMapper()), preparation, execution, parser,
                budgets, prompts, packets, new DeliveryPartFindingsParser(mapper), mapper, properties);
    }

    private CopilotExecutionResult answer(CopilotPreparedSession session) throws Exception {
        sent.add(session);
        if (session.prompt().startsWith("TRYB SYNTEZY") || !session.artifactContents().containsKey("delivery-scope-complexity/manifest.md"))
            return result("{\"classification\":\"EXCLUDED\",\"confidence\":0.8}");
        var scopes = new ArrayList<String>();
        boolean reduction=session.artifactContents().containsKey("delivery-scope-complexity/findings.json");
        if (reduction) {
            var groups=mapper.readTree(session.artifactContents().get("delivery-scope-complexity/findings.json"));
            groups.forEach(group -> group.path("coverage").forEach(ref -> scopes.add(ref.asText())));
        } else mapper.readTree(session.artifactContents().get("delivery-scope-complexity/part-scope.json")).forEach(ref -> scopes.add(ref.asText()));
        var facts=List.of(new AnalysisAiFinding("customer-status", "novelty", largeFindings && !reduction ? "CRM status ".repeat(1600) : "Customer status rule with validation.", scopes, List.of()));
        return result(mapper.writeValueAsString(new DeliveryPartFindings(scopes, true, facts, 0.9, List.of())));
    }

    private CopilotExecutionResult result(String content) {
        return new CopilotExecutionResult(content, new AnalysisAiUsage(100,20,0L,0L,120,0.1,100,1,"crm-mini",30_000L,200L,1L,0L), "crm-session-"+sent.size());
    }

    private DeliveryUnitAiAnalysis analyze(DeliveryEvidencePacket packet) {
        return provider.analyze("crm-run", new AnalysisAiOptions("crm-mini","low"), AnalysisAiAuthRef.localToken("test"),
                packet, prompts.prepare(packet), AnalysisAiActivityListener.NO_OP, call -> ledger.put(call.invocationId(),call), Instant.now().plusSeconds(60));
    }

    private DeliveryEvidencePacket largePacket() {
        return packets.build(unit("CRM-1", mergeRequest(1,"src/Customer.java", "+CRM status first\n".repeat(1200)),
                mergeRequest(2,"src/Status.java", "+CRM status second\n".repeat(1200))));
    }

    @Test void shouldKeepSmallUnitAsOneAssessment() {
        var analysis=analyze(packets.build(unit("CRM-1",mergeRequest(1,"src/Customer.java","+status"))));
        assertThat(sent).hasSize(1);
        assertThat(ledger.values()).extracting(AnalysisAiInvocation::role).containsExactly("ASSESSMENT");
        assertThat(analysis.usage().apiCallCount()).isEqualTo(1);
    }

    @Test void shouldAnalyzeDisjointPartsWithFullJiraAndConfluenceAndSynthesizeOnce() {
        var packet=largePacket();
        var analysis=analyze(packet);
        var parts=ledger.values().stream().filter(c -> c.role().equals("EVIDENCE_PART")).toList();
        assertThat(parts).hasSize(2);
        assertThat(parts).extracting(AnalysisAiInvocation::partCount).containsExactly(2, 2);
        var covered=parts.stream().flatMap(c -> c.evidenceScope().stream()).toList();
        assertThat(covered).containsExactlyInAnyOrderElementsOf(DeliveryEvidenceSlice.whole(packet.unit()).coverage());
        assertThat(new HashSet<>(covered)).hasSize(covered.size());
        sent.forEach(session -> {
            assertThat(session.prompt().length()).isLessThanOrEqualTo((int)limit);
            assertThat(session.artifactContents().get("delivery-scope-complexity/issues.md")).isEqualTo(packet.artifacts().get("delivery-scope-complexity/issues.md"));
        });
        assertThat(sent.get(2).prompt()).startsWith("TRYB SYNTEZY");
        assertThat(sent.get(2).artifactContents()).doesNotContainKey("delivery-scope-complexity/diffs.md");
        assertThat(analysis.response().qualityFlags()).contains("MULTIPART_SYNTHESIS");
        assertThat(analysis.usage().apiCallCount()).isEqualTo(3);
        assertThat(analysis.usage().inputTokens()).isEqualTo(300);
        assertThat(analysis.usage().contextTokenLimit()).isEqualTo(30_000);
        verify(parser,times(1)).parse(anyString());
    }

    @Test void shouldSplitOneLargeMrByWholeFilesWithoutLosingDiffs() {
        var two=largePacket().unit().mergeRequests(); var mr=two.get(0);
        var files=List.of(mr.changedFiles().get(0),two.get(1).changedFiles().get(0));
        var combined=new pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequest(mr.id(),mr.iid(),mr.projectId(),mr.projectPath(),mr.title(),mr.state(),mr.webUrl(),mr.sourceBranch(),mr.targetBranch(),mr.authorName(),mr.authorId(),mr.createdAt(),mr.updatedAt(),mr.mergedAt(),mr.changesCount(),mr.commits(),files,mr.limitations());
        analyze(packets.build(unit("CRM-1",combined)));
        assertThat(ledger.values().stream().filter(c -> c.role().equals("EVIDENCE_PART"))).hasSize(2);
        assertThat(sent.get(0).artifactContents().get("delivery-scope-complexity/diffs.md")).contains(files.get(0).diff()).doesNotContain(files.get(1).diff());
        assertThat(sent.get(1).artifactContents().get("delivery-scope-complexity/diffs.md")).contains(files.get(1).diff());
    }

    @Test void shouldNotSendAnIndivisibleOversizedFileOrOversizedBaseContext() {
        limit=8_000;
        assertThatThrownBy(() -> analyze(packets.build(unit("CRM-1",mergeRequest(1,"src/Customer.java","+CRM\n".repeat(10000))))))
                .hasMessageContaining("EVIDENCE_ATOM_TOO_LARGE");
        assertThat(sent).isEmpty();
        limit=100;
        assertThatThrownBy(() -> analyze(largePacket())).hasMessageContaining("JIRA_CONTEXT_TOO_LARGE");
        assertThat(sent).isEmpty();
    }

    @Test void shouldRetainRawResponseBeforeParsingFails() {
        doReturn(result("invalid JSON")).when(execution).execute(any(),any(),any());
        doAnswer(call -> {
            assertThat(ledger.values()).singleElement().satisfies(i -> assertThat(i.rawResponse()).isEqualTo("invalid JSON"));
            throw new IllegalArgumentException("AI response did not contain JSON assessment.");
        }).when(parser).parse("invalid JSON");
        assertThatThrownBy(() -> analyze(packets.build(unit("CRM-1",mergeRequest(1,"src/Customer.java","+status")))))
                .hasMessage("AI response did not contain JSON assessment.");
        assertThat(ledger.values()).singleElement().satisfies(i -> {
            assertThat(i.status()).isEqualTo("FAILED"); assertThat(i.rawResponse()).isEqualTo("invalid JSON");
            assertThat(i.usage().apiCallCount()).isEqualTo(1);
        });
    }

    @Test void shouldCorrectOnlyContextOverflowAndPreserveFailedAttemptUsage() throws Exception {
        limit=100_000;
        var attempts=new AtomicInteger();
        doAnswer(call -> {
            if (attempts.getAndIncrement()==0) throw new CopilotPromptOverflowException(50_000,30_000,null,result("unused").usage());
            return answer(call.getArgument(0));
        }).when(execution).execute(any(),any(),any());
        var analysis=analyze(largePacket());
        assertThat(ledger.values()).filteredOn(i -> i.status().equals("FAILED")).hasSize(1);
        assertThat(analysis.usage().apiCallCount()).isEqualTo(4);
        assertThat(sent).hasSize(3);
    }

    @Test void shouldKeepPartialEvidenceWhenAnotherPartFailsAndNotSynthesize() throws Exception {
        var attempts=new AtomicInteger();
        doAnswer(call -> {
            if(attempts.getAndIncrement()==1) throw new CopilotSdkInvocationException("rate limit");
            return answer(call.getArgument(0));
        }).when(execution).execute(any(),any(),any());
        assertThatThrownBy(() -> analyze(largePacket())).hasMessage("rate limit");
        assertThat(ledger.values()).extracting(AnalysisAiInvocation::status).containsExactly("COMPLETED","FAILED");
        assertThat(ledger.values()).noneMatch(i -> i.role().equals("SYNTHESIS"));
        verify(execution,times(2)).execute(any(),any(),any());
    }

    @Test void shouldReduceLargeFindingsWithoutDroppingCoverageAndScoreOnlyAtTheEnd() {
        largeFindings=true;
        analyze(largePacket());
        assertThat(ledger.values()).filteredOn(i -> i.role().equals("REDUCTION")).hasSize(2);
        assertThat(ledger.values()).filteredOn(i -> i.role().equals("SYNTHESIS")).hasSize(1);
        verify(parser,times(1)).parse(anyString());
    }

    @Test void shouldPreserveMalformedPartialResponseAndStopBeforeSynthesis() {
        doReturn(result("invalid CRM partial JSON")).when(execution).execute(any(),any(),any());
        assertThatThrownBy(() -> analyze(largePacket())).hasMessageContaining("one valid JSON object");
        assertThat(ledger.values()).singleElement().satisfies(call -> {
            assertThat(call.role()).isEqualTo("EVIDENCE_PART");
            assertThat(call.status()).isEqualTo("FAILED");
            assertThat(call.rawResponse()).isEqualTo("invalid CRM partial JSON");
            assertThat(call.usage().apiCallCount()).isEqualTo(1);
        });
        verify(execution,times(1)).execute(any(),any(),any());
    }

    @Test void shouldBoundContextCorrectionsAndRetainEveryRejectedAttempt() {
        limit=100_000;
        properties.setMaxContextCorrections(1);
        doThrow(new CopilotPromptOverflowException(50_000,30_000,null,result("unused").usage()))
                .when(execution).execute(any(),any(),any());
        assertThatThrownBy(() -> analyze(largePacket())).isInstanceOf(CopilotPromptOverflowException.class);
        assertThat(ledger.values()).hasSize(2).allMatch(call -> call.status().equals("FAILED"));
        verify(execution,times(2)).execute(any(),any(),any());
    }

    @Test void shouldStopAfterInterruptWithoutStartingAnotherPart() throws Exception {
        doAnswer(call -> {
            var result=answer(call.getArgument(0));
            Thread.currentThread().interrupt();
            return result;
        }).when(execution).execute(any(),any(),any());
        try {
            assertThatThrownBy(() -> analyze(largePacket())).hasMessageContaining("deadline");
            verify(execution,times(1)).execute(any(),any(),any());
            assertThat(ledger.values()).singleElement().satisfies(call -> assertThat(call.rawResponse()).isNotBlank());
        } finally { Thread.interrupted(); }
    }

    @Test void shouldStopBeforeStartingARequestAfterDeadline() {
        var packet=largePacket();
        assertThatThrownBy(() -> provider.analyze("crm-run",null,AnalysisAiAuthRef.localToken("test"),packet,prompts.prepare(packet),
                AnalysisAiActivityListener.NO_OP, AnalysisAiInvocationListener.NO_OP, Instant.now().minusSeconds(1))).hasMessageContaining("deadline");
        verifyNoInteractions(execution);
    }

    @Test void shouldCompletePartsAndSynthesisWithWholeManifestCoverageAndPreserveDiagnostics() throws Exception {
        doAnswer(call -> {
            var session = (CopilotPreparedSession) call.getArgument(0);
            var normal = answer(session);
            if (session.prompt().startsWith("TRYB SYNTEZY")) {
                var finalTree = mapper.createObjectNode();
                finalTree.put("classification", "DELIVERY");
                var dimensions = finalTree.putObject("dimensions");
                for (var dimension : List.of("novelty","structuralAndLogic","businessAndInvariants","robustnessAndTests","refactorAndArchitecture","distribution")) { var value = dimensions.putObject(dimension); value.put("score", 10); value.put("scopeSignal", 0.1); value.putArray("evidence").add("CRM customer rule in verified findings"); }
                finalTree.put("confidence", "unknown");
                return new CopilotExecutionResult(finalTree.toString(), normal.usage(), normal.sessionId());
            }
            if (!session.artifactContents().containsKey("delivery-scope-complexity/part-scope.json")) return normal;
            var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(normal.content());
            var all = session.artifactContents().get("delivery-scope-complexity/manifest.md").lines()
                    .filter(line -> line.startsWith("id:")).toList();
            tree.set("coverage", mapper.valueToTree(all));
            return new CopilotExecutionResult(tree.toString(), normal.usage(), normal.sessionId());
        }).when(execution).execute(any(), any(), any());
        var packet = largePacket();
        var analysis = analyze(packet);
        assertThat(ledger.values()).hasSize(3).allMatch(call -> call.status().equals("COMPLETED"));
        var parts = ledger.values().stream().filter(call -> call.role().equals("EVIDENCE_PART")).toList();
        assertThat(parts).hasSize(2).allSatisfy(call -> {
            assertThat(call.visibilityLimits()).anyMatch(limit -> limit.contains("nadmiarowe="));
            assertThat(mapper.readTree(call.rawResponse()).path("coverage").size()).isGreaterThan(call.evidenceScope().size());
        });
        assertThat(analysis.response().visibilityLimits()).anyMatch(limit -> limit.contains("coverage:"));
        assertThat(analysis.response().classification()).isEqualTo("DELIVERY");
        assertThat(analysis.response().qualityFlags()).contains("AI_METADATA_WARNING");
        assertThat(new DeliveryScopeScoringService().score(analysis.response()).finalScore()).isGreaterThan(0);
        assertThat(analysis.usage().apiCallCount()).isEqualTo(3);
        assertThat(analysis.usage().inputTokens()).isEqualTo(300);
        verify(execution, times(3)).execute(any(), any(), any());
    }

    @Test void shouldPreservePartWarningsAndConfidenceAcrossReductionEvenWhenModelOmitsThem() throws Exception {
        largeFindings = true;
        doAnswer(call -> {
            var session = (CopilotPreparedSession) call.getArgument(0);
            var normal = answer(session);
            if (!session.prompt().startsWith("ETAP: EVIDENCE_PART")) return normal;
            var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(normal.content());
            tree.put("confidence", "unknown");
            tree.putArray("visibilityLimits").add("CRM runtime unavailable");
            return new CopilotExecutionResult(tree.toString(), normal.usage(), normal.sessionId());
        }).when(execution).execute(any(), any(), any());
        var analysis = analyze(largePacket());
        assertThat(ledger.values().stream().filter(call -> call.role().equals("REDUCTION"))).isNotEmpty()
                .allSatisfy(call -> assertThat(call.visibilityLimits()).contains("CRM runtime unavailable")
                        .anyMatch(limit -> limit.contains("confidence:")));
        assertThat(analysis.response().confidence()).isZero();
        assertThat(analysis.response().visibilityLimits()).contains("CRM runtime unavailable")
                .anyMatch(limit -> limit.contains("confidence:"));
        assertThat(analysis.usage().apiCallCount()).isEqualTo(ledger.size());
    }

    @Test void shouldReduceFactsWhenDiagnosticMetadataGrows() throws Exception {
        doAnswer(call -> {
            CopilotPreparedSession session = call.getArgument(0);
            var needsReduction = session.prompt().startsWith("TRYB SYNTEZY")
                    && ledger.values().stream().noneMatch(invocation -> invocation.role().equals("REDUCTION"));
            return new CopilotPromptBudget(needsReduction ? limit + 1 : session.prompt().length(), limit, limit, 1000, 0, 1.0, false);
        }).when(budgets).measure(any());
        doAnswer(call -> {
            var session = (CopilotPreparedSession) call.getArgument(0);
            var normal = answer(session);
            if (!session.prompt().startsWith("ETAP: REDUCTION")) return normal;
            var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(normal.content());
            tree.putArray("coverage").add("CRM/customer-api!99#metadata");
            return new CopilotExecutionResult(tree.toString(), normal.usage(), normal.sessionId());
        }).when(execution).execute(any(), any(), any());
        var analysis = analyze(largePacket());
        assertThat(ledger.values()).extracting(AnalysisAiInvocation::role)
                .containsExactly("EVIDENCE_PART", "EVIDENCE_PART", "REDUCTION", "SYNTHESIS");
        assertThat(analysis.response().visibilityLimits()).anyMatch(limit -> limit.contains("coverage:"));
        assertThat(analysis.usage().apiCallCount()).isEqualTo(4);
    }

    @Test void shouldFailOnInventedPartReferenceAndPreserveRawBeforeSynthesis() throws Exception {
        doAnswer(call -> {
            var normal = answer(call.getArgument(0));
            var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(normal.content());
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("findings").get(0)).putArray("references")
                    .add("CRM/customer-api!99#metadata");
            return new CopilotExecutionResult(tree.toString(), normal.usage(), normal.sessionId());
        }).when(execution).execute(any(), any(), any());
        assertThatThrownBy(() -> analyze(largePacket())).hasMessageContaining("references outside current evidence scope");
        assertThat(ledger.values()).singleElement().satisfies(call -> {
            assertThat(call.status()).isEqualTo("FAILED");
            assertThat(call.rawResponse()).contains("CRM/customer-api!99#metadata");
            assertThat(call.usage().apiCallCount()).isEqualTo(1);
        });
        verify(execution, times(1)).execute(any(), any(), any());
    }
}
