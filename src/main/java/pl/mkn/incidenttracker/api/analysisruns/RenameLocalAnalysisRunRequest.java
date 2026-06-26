package pl.mkn.incidenttracker.api.analysisruns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameLocalAnalysisRunRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 200, message = "name must not exceed 200 characters")
        String name
) {
}
