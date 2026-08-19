package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;

public record GitLabTypeScriptSymbolSelectorApiRequest(
        @NotBlank @Size(max = 200) String name,
        GitLabTypeScriptSymbolKind kind,
        @Min(1) Integer lineStart
) {
    GitLabTypeScriptSymbolSelector toIntegrationSelector() {
        return new GitLabTypeScriptSymbolSelector(name, kind, lineStart);
    }
}
