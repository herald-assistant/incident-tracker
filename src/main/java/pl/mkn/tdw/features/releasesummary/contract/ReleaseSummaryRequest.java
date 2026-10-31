package pl.mkn.tdw.features.releasesummary.contract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;
public record ReleaseSummaryRequest(
        @NotBlank @Size(max=160) String systemId,
        @NotNull Mode mode,
        @NotNull @Valid GitLabRef baselineRef,
        @NotNull @Valid GitLabRef targetRef,
        @Size(max=120) String model,
        @Size(max=60) String reasoningEffort) {
    public enum Mode { LIQUIBASE }
    public AnalysisAiOptions aiOptions() { return new AnalysisAiOptions(model, reasoningEffort); }
}

