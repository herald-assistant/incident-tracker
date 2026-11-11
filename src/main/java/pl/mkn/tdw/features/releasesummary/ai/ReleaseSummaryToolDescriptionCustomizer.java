package pl.mkn.tdw.features.releasesummary.ai;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer;

import java.util.Map;

@Component
public class ReleaseSummaryToolDescriptionCustomizer implements CopilotToolDescriptionCustomizer {
    private static final Map<String, String> GUIDANCE_BY_TOOL_NAME = Map.of(
            GitLabToolNames.LIST_REPOSITORY_COMMITS,
            "Sprawdź historię implementacji istotnej dla porównania lub pytania operatora. Domyślnym punktem wyjścia jest target SHA, ale możesz badać dowolną inną rewizję; nie zastępuje manifestu zmian porównania.",
            GitLabToolNames.READ_COMMIT_DIFF,
            "Sprawdź znaczenie potwierdzonego commita dla badanego zachowania. Podaj źródło i luki; dodatkowy diff nie zmienia kompletności bazowego porównania.",
            GitLabToolNames.READ_REPOSITORY_FILE,
            "Dla kodu przed lub po zmianie branchRef może być dowolną badaną rewizją, bez wcześniejszego listowania historii. Zachowaj rozróżnienie między dodatkowym researchem a bazowym porównaniem.",
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            "Dla historycznego fragmentu branchRef może być dowolną badaną rewizją, bez wcześniejszego listowania historii. Zachowaj rozróżnienie między dodatkowym researchem a bazowym porównaniem.");

    @Override
    public String customize(CopilotToolDescriptionContext context, String toolName, String description) {
        if (context == null || !context.matchesProfile("release-summary")) return description;
        var guidance = GUIDANCE_BY_TOOL_NAME.get(toolName);
        return guidance == null ? description : description + "\nRelease summary: " + guidance;
    }
}
