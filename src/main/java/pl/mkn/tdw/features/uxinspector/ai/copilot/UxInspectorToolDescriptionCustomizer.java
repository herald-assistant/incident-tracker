package pl.mkn.tdw.features.uxinspector.ai.copilot;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer;

import java.util.Set;
import java.util.Map;

@Component
public class UxInspectorToolDescriptionCustomizer implements CopilotToolDescriptionCustomizer {
    private static final Map<String, String> GUIDANCE_BY_TOOL_NAME = Map.of(
            GitLabToolNames.LIST_REPOSITORY_COMMITS,
            "UX Inspector: sprawdź historię kodu powiązanego z wybranym elementem i pytaniem operatora; użyj projektu z sourceToolScope oraz plików potwierdzonych w kodzie lub discovery.",
            GitLabToolNames.READ_COMMIT_DIFF,
            "UX Inspector: sprawdź wpływ potwierdzonego commita na zachowanie wybranego elementu. Podaj źródło i luki; odróżnij zmianę kodu od hipotezy o regresji.");
    private static final Set<String> FRONTEND_TOOLS = Set.of(GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
            GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE);
    private static final Set<String> REPOSITORY_NAVIGATION_TOOLS = Set.of(
            GitLabToolNames.LIST_REPOSITORY_TREE,
            GitLabToolNames.LIST_REPOSITORY_FILES,
            GitLabToolNames.SEARCH_REPOSITORY_FILES
    );
    private static final Set<String> REPOSITORY_READ_TOOLS = Set.of(
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
    );
    @Override
    public String customize(CopilotToolDescriptionContext context, String toolName, String description) {
        if (context == null || !context.matchesProfile("ux-inspector")) return description;
        var guidance = GUIDANCE_BY_TOOL_NAME.get(toolName);
        if (guidance != null) return description + "\n" + guidance;
        if (FRONTEND_TOOLS.contains(toolName)) {
            if (GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE.equals(toolName)) {
                return description + "\nUX Inspector: use only for a concrete unresolved code link required by the single "
                        + "operator question. Copy direct file/type or consumer import coordinates from visible original code; "
                        + "optionally narrow memberNames. Repository and branch come from hidden context.";
            }
            return description + "\nUX Inspector: use only for a concrete unresolved route link required by the single "
                    + "operator question. Pass the selected route sliceRef and reason; repository and branch come "
                    + "from hidden context.";
        }
        if (REPOSITORY_NAVIGATION_TOOLS.contains(toolName)) {
            return description + "\nUX Inspector: navigate only the repository selected by the operator. "
                    + "Use projectName and branchRef from sourceToolScope, omit applicationNames when present, and provide a short reason. "
                    + "Returned paths are navigation hints only; read a file before using its contents as evidence.";
        }
        if (REPOSITORY_READ_TOOLS.contains(toolName)) {
            return description + "\nUX Inspector: read any safe path in the repository selected by the operator. "
                    + "Use projectName and branchRef from sourceToolScope, omit applicationNames, and provide a short reason. "
                    + "For historical reads, branchRef may instead be any requested commit SHA without an earlier history lookup. "
                    + "Use only successfully read content as evidence.";
        }
        if (GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE.equals(toolName)) {
            return description + "\nUX Inspector: when source evidence identifies an OpenAPI/Swagger file and either METHOD path "
                    + "or operationId, use this tool instead of reading the contract with full-file or chunk tools. "
                    + "Use projectName and branchRef from sourceToolScope, omit applicationNames, and provide a short reason. "
                    + "The selected repository and branch are enforced by hidden session scope.";
        }
        return description;
    }
}
