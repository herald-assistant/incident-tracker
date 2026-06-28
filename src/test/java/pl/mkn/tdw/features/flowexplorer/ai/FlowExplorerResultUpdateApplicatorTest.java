package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerResultUpdateApplicatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FlowExplorerResultUpdateApplicator applicator = new FlowExplorerResultUpdateApplicator();

    @Test
    void shouldUpdateOverviewMarkdownOnly() throws Exception {
        var result = applicator.apply(currentResult(), objectMapper.readTree("""
                {
                  "overview": {
                    "markdown": "Nowy overview."
                  }
                }
                """));

        assertEquals("Nowy overview.", result.overview().markdown());
        assertEquals("medium", result.overview().confidence());
        assertEquals(List.of("overview-ref"), result.overview().sourceRefs());
        assertEquals("Stary flow.", result.sections().get(0).markdown());
    }

    @Test
    void shouldMergeSingleSectionByIdAndKeepTitleAndModeWhenOmitted() throws Exception {
        var result = applicator.apply(currentResult(), objectMapper.readTree("""
                {
                  "sections": [
                    {
                      "id": "FUNCTIONAL_FLOW",
                      "markdown": "Nowy flow.",
                      "openQuestions": ["Czy status inactive jest widoczny?"]
                    }
                  ]
                }
                """));

        var flow = result.sections().get(0);
        assertEquals(FlowExplorerResultSectionId.FUNCTIONAL_FLOW, flow.id());
        assertEquals("Functional flow", flow.title());
        assertEquals(FlowExplorerResultSectionMode.DEEP, flow.mode());
        assertEquals("Nowy flow.", flow.markdown());
        assertEquals(List.of("flow-ref"), flow.sourceRefs());
        assertEquals(List.of("flow-limit"), flow.visibilityLimits());
        assertEquals(List.of("Czy status inactive jest widoczny?"), flow.openQuestions());
        assertEquals("Stara persistence.", result.sections().get(1).markdown());
    }

    @Test
    void shouldTreatEmptyListAsExplicitReplacement() throws Exception {
        var result = applicator.apply(currentResult(), objectMapper.readTree("""
                {
                  "globalVisibilityLimits": [],
                  "sections": [
                    {
                      "id": "FUNCTIONAL_FLOW",
                      "visibilityLimits": []
                    }
                  ]
                }
                """));

        assertEquals(List.of(), result.globalVisibilityLimits());
        assertEquals(List.of(), result.sections().get(0).visibilityLimits());
    }

    @Test
    void shouldTreatAbsentFieldAsNoOp() throws Exception {
        var current = currentResult();
        var result = applicator.apply(current, objectMapper.readTree("{}"));

        assertEquals(current, result);
    }

    @Test
    void shouldReturnCurrentResultForMissingUpdate() {
        var current = currentResult();
        var result = applicator.apply(current, null);

        assertSame(current, result);
    }

    @Test
    void shouldRejectExplicitNull() throws Exception {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.apply(currentResult(), objectMapper.readTree("""
                        {
                          "overview": {
                            "markdown": null
                          }
                        }
                        """))
        );

        assertTrue(exception.getMessage().contains("resultUpdate.overview.markdown must not be null"));
    }

    @Test
    void shouldRejectValidSectionThatIsNotPresentInCurrentResult() throws Exception {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.apply(currentResult(), objectMapper.readTree("""
                        {
                          "sections": [
                            {
                              "id": "VALIDATIONS",
                              "markdown": "Nowe walidacje."
                            }
                          ]
                        }
                        """))
        );

        assertTrue(exception.getMessage().contains("section VALIDATIONS is not present in the current result"));
    }

    @Test
    void shouldRejectUnknownTopLevelField() throws Exception {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.apply(currentResult(), objectMapper.readTree("""
                        {
                          "goal": "TEST_SCENARIOS"
                        }
                        """))
        );

        assertTrue(exception.getMessage().contains("Unexpected resultUpdate field: goal"));
    }

    private static FlowExplorerAiResponse currentResult() {
        return new FlowExplorerAiResponse(
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                "business_or_system_analyst_tester",
                new FlowExplorerResultOverview(
                        "Stary overview.",
                        "medium",
                        List.of("overview-ref")
                ),
                List.of(
                        new FlowExplorerResultSection(
                                FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                                "Functional flow",
                                FlowExplorerResultSectionMode.DEEP,
                                "Stary flow.",
                                List.of("flow-ref"),
                                List.of("flow-limit"),
                                List.of("flow-question")
                        ),
                        new FlowExplorerResultSection(
                                FlowExplorerResultSectionId.PERSISTENCE,
                                "Persistence",
                                FlowExplorerResultSectionMode.COMPACT,
                                "Stara persistence.",
                                List.of("persistence-ref"),
                                List.of("persistence-limit"),
                                List.of("persistence-question")
                        )
                ),
                List.of("global-limit"),
                List.of("global-question"),
                List.of("source-ref"),
                "medium",
                List.of("follow-up")
        );
    }
}
