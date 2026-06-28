package pl.mkn.tdw.features.flowexplorer.job.api;

import jakarta.validation.constraints.NotNull;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;

public record FlowExplorerResultUpdateDecisionRequest(
        @NotNull FlowExplorerAiResponse aiResponse
) {
}
