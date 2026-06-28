package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowExplorerAiResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeLowercaseSectionModeFromAiResponseJson() throws Exception {
        var response = objectMapper.readValue("""
                {
                  "goal": "DEEP_DISCOVERY",
                  "audience": "business_or_system_analyst_tester",
                  "overview": {
                    "markdown": "Overview",
                    "confidence": "medium",
                    "sourceRefs": []
                  },
                  "sections": [
                    {
                      "id": "FUNCTIONAL_FLOW",
                      "title": "Functional flow",
                      "mode": "deep",
                      "markdown": "Flow details",
                      "sourceRefs": [],
                      "visibilityLimits": [],
                      "openQuestions": []
                    }
                  ],
                  "globalVisibilityLimits": [],
                  "globalOpenQuestions": [],
                  "sourceReferences": [],
                  "confidence": "medium",
                  "followUpPrompts": []
                }
                """, FlowExplorerAiResponse.class);

        assertEquals(FlowExplorerResultSectionMode.DEEP, response.sections().get(0).mode());
    }
}
