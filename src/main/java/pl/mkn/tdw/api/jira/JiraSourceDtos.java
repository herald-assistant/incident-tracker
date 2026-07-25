package pl.mkn.tdw.api.jira;

import jakarta.validation.constraints.NotBlank;

public final class JiraSourceDtos {

    private JiraSourceDtos() {
    }

    public record JiraIssueMaterialRequest(
            @NotBlank String issueRef
    ) {
    }
}
