package pl.mkn.tdw.features.flowexplorer.job.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

public record FlowExplorerSectionRefineRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message
) {

    public FlowExplorerSectionRefineRequest {
        message = StringUtils.hasText(message) ? message.trim() : null;
    }
}
