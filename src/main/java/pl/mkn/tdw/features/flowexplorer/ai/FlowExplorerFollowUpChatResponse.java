package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record FlowExplorerFollowUpChatResponse(
        String message,
        JsonNode resultUpdate
) {

    public FlowExplorerFollowUpChatResponse {
        message = message != null ? message.trim() : "";
        resultUpdate = resultUpdate != null && !resultUpdate.isNull()
                ? resultUpdate.deepCopy()
                : null;
    }

    public boolean hasResultUpdate() {
        return resultUpdate != null;
    }
}
