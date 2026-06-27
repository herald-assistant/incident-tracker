package pl.mkn.tdw.features.flowexplorer.job.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowExplorerSectionRefineRequestTest {

    @Test
    void shouldTrimMessageLikeFollowUpChatRequest() {
        var request = new FlowExplorerSectionRefineRequest("  Doprecyzuj persistence.  ");

        assertEquals("Doprecyzuj persistence.", request.message());
    }
}
