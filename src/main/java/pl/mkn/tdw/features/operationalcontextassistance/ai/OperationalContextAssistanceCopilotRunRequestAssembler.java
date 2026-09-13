package pl.mkn.tdw.features.operationalcontextassistance.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierPreference;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotSessionHardToolBudget;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.common.GitLabPathUtils;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_FILES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_BRANCHES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_TREE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.SEARCH_REPOSITORY_FILES;

@Component
@RequiredArgsConstructor
public class OperationalContextAssistanceCopilotRunRequestAssembler {

    private static final Set<String> SOURCE_TOOL_NAMES = Set.of(
            LIST_REPOSITORY_BRANCHES, LIST_REPOSITORY_TREE, LIST_REPOSITORY_FILES,
            SEARCH_REPOSITORY_FILES, READ_REPOSITORY_FILE
    );
    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("operational-context-assistance");
    private static final String DENIED_TOOL_MESSAGE =
            "Dozwolone są tylko narzędzia odczytu projektów i gałęzi w skonfigurowanej głównej grupie GitLab. "
                    + "Wybrany projekt musi używać gałęzi operatora i przypiętego commita. "
                    + "Filesystem, shell, terminal i mutation tools są zabronione.";

    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotSdkToolFactory toolFactory;
    private final OperationalContextAssistanceAiProperties aiProperties;
    private final GitLabProperties gitLabProperties;

    public OperationalContextAssistanceCopilotRunAssembly assemble(
            String runReference,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef,
            OperationalContextAssistancePromptPreparation preparation,
            OperationalContextGitLabSourceSnapshot source
    ) {
        if (runReference == null || runReference.isBlank() || preparation == null
                || preparation.prompt() == null || preparation.prompt().isBlank()) {
            throw new IllegalArgumentException("Assistance run reference and prepared prompt are required.");
        }
        var selection = new CopilotModelSelection(
                options != null && options.model() != null ? options.model() : aiProperties.getModel(),
                options != null && options.reasoningEffort() != null
                        ? options.reasoningEffort()
                        : options != null && options.model() != null ? null : aiProperties.getReasoningEffort()
        );
        var sessionId = "operational-context-assistance-" + UUID.randomUUID();
        var sourceScope = selectedScope(source);
        var hardBudget = sourceScope != null
                ? new CopilotSessionHardToolBudget(18, Map.of(
                        LIST_REPOSITORY_BRANCHES, 3,
                        LIST_REPOSITORY_TREE, 6,
                        LIST_REPOSITORY_FILES, 6,
                        SEARCH_REPOSITORY_FILES, 6,
                        READ_REPOSITORY_FILE, 4
                )) : null;
        var registeredTools = sourceScope != null
                ? toolFactory.createToolDefinitions(new CopilotToolSessionContext(
                        runReference,
                        sessionId,
                        Map.of(
                                AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE, sourceScope,
                                AgentToolContextKeys.TOOL_HARD_BUDGET, hardBudget
                        )
                ), DESCRIPTION_CONTEXT).stream()
                .filter(tool -> SOURCE_TOOL_NAMES.contains(tool.name()))
                .toList()
                : List.<com.github.copilot.rpc.ToolDefinition>of();
        if (sourceScope != null && registeredTools.size() != SOURCE_TOOL_NAMES.size()) {
            throw new IllegalStateException("GitLab repository read tools are not fully registered.");
        }
        var sessionConfig = new CopilotSessionConfigRequest(
                sessionId,
                registeredTools,
                registeredTools.stream().map(com.github.copilot.rpc.ToolDefinition::name).toList(),
                selection,
                DENIED_TOOL_MESSAGE,
                sourceScope != null
        ).withContextTierPreference(CopilotContextTierPreference.LONG_CONTEXT_REQUIRED);
        var runRequest = new CopilotRunRequest(
                runReference,
                runAuthMapper.toRunAuth(authRef),
                CopilotSessionTarget.newSession(),
                preparation.prompt(),
                sessionConfig,
                preparation.artifacts(),
                null
        );
        return new OperationalContextAssistanceCopilotRunAssembly(runRequest, sourceScope);
    }

    private GitLabRepositoryToolScope selectedScope(OperationalContextGitLabSourceSnapshot source) {
        if (source == null || source.commitId() == null || source.repositoryGit() == null) {
            return null;
        }
        var git = source.repositoryGit();
        if (!"gitlab".equals(git.provider()) || git.projectPath() == null
                || !git.projectPath().equals(git.group() + "/" + git.project())) {
            throw new IllegalArgumentException("Selected GitLab source identity is inconsistent.");
        }
        var configuredGroup = gitLabProperties.getGroup() != null
                ? GitLabPathUtils.trimSlashes(gitLabProperties.getGroup().trim()) : null;
        if (configuredGroup == null || configuredGroup.isBlank()
                || !git.projectPath().equals(configuredGroup + "/" + source.project())) {
            throw new IllegalArgumentException("Selected GitLab project is outside the configured group.");
        }
        return new GitLabRepositoryToolScope(configuredGroup, source.project(),
                source.requestedRef(), source.commitId());
    }
}
