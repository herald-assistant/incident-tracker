package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.report.RuntimeConfigurationReportSectionIds;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationAffectedEntity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepRepositoryScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RuntimeConfigurationCopilotToolSessionContextFactory {

    private static final String SESSION_PREFIX = "runtime-config-";

    private final GitLabProperties gitLabProperties;

    public CopilotToolSessionContext create(
            String runReference,
            RuntimeConfigurationVerificationJobStartRequest request,
            RuntimeConfigurationDeepContext deepContext
    ) {
        var runId = StringUtils.hasText(runReference) ? runReference.trim() : UUID.randomUUID().toString();
        var mode = request != null && request.mode() != null
                ? request.mode()
                : RuntimeConfigurationVerificationMode.BASIC;
        var hidden = new LinkedHashMap<String, Object>();
        hidden.put(RuntimeConfigurationCopilotToolContextKeys.FEATURE,
                RuntimeConfigurationCopilotToolContextKeys.FEATURE_VALUE);
        hidden.put(RuntimeConfigurationCopilotToolContextKeys.MODE, mode.name());
        if (request != null && StringUtils.hasText(request.systemId())) {
            hidden.put(RuntimeConfigurationCopilotToolContextKeys.SYSTEM_ID, request.systemId());
        }
        hidden.put(AgentToolContextKeys.REPORT_ID, "report-" + UUID.randomUUID());
        hidden.put(AgentToolContextKeys.REPORT_FEATURE,
                RuntimeConfigurationCopilotToolContextKeys.FEATURE_VALUE);
        hidden.put(
                AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS,
                RuntimeConfigurationReportSectionIds.aiWritable(mode)
        );

        if (mode == RuntimeConfigurationVerificationMode.DEEP && deepContext != null) {
            hidden.put(
                    RuntimeConfigurationCopilotToolContextKeys.ALLOWED_REPOSITORIES,
                    repositoryScopes(deepContext)
            );
            hidden.put(
                    RuntimeConfigurationCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS,
                    operationalEntityIds(deepContext)
            );
            if (StringUtils.hasText(gitLabProperties.getGroup())) {
                hidden.put(AgentToolContextKeys.GITLAB_GROUP, gitLabProperties.getGroup().trim());
            }
        } else {
            hidden.put(RuntimeConfigurationCopilotToolContextKeys.ALLOWED_REPOSITORIES, List.of());
            hidden.put(RuntimeConfigurationCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS, List.of());
        }
        return new CopilotToolSessionContext(runId, SESSION_PREFIX + runId, hidden);
    }

    private List<Map<String, Object>> repositoryScopes(RuntimeConfigurationDeepContext deepContext) {
        if (deepContext.preflight() == null) {
            return List.of();
        }
        return deepContext.preflight().repositories().stream()
                .filter(RuntimeConfigurationDeepRepositoryScope::ready)
                .map(repository -> {
                    var scope = new LinkedHashMap<String, Object>();
                    putIfText(scope, "repositoryId", repository.repositoryId());
                    putIfText(scope, "projectName", repository.projectName());
                    putIfText(scope, "projectPath", repository.projectPath());
                    putIfText(scope, "branchRef", repository.usedRef());
                    putIfText(scope, "searchMode", repository.searchMode());
                    scope.put("pathPrefixes", repository.pathPrefixes());
                    return Collections.unmodifiableMap(scope);
                })
                .toList();
    }

    private List<String> operationalEntityIds(RuntimeConfigurationDeepContext deepContext) {
        var ids = new LinkedHashSet<String>();
        if (deepContext.primarySystem() != null) {
            ids.add(deepContext.primarySystem().systemId());
        }
        var entities = new ArrayList<RuntimeConfigurationAffectedEntity>();
        entities.addAll(deepContext.affectedSystems());
        entities.addAll(deepContext.integrations());
        entities.addAll(deepContext.processes());
        entities.addAll(deepContext.boundedContexts());
        entities.forEach(entity -> ids.add(entity.entityId()));
        if (deepContext.preflight() != null) {
            deepContext.preflight().repositories().forEach(repository -> {
                ids.add(repository.repositoryId());
                ids.add(repository.scopeId());
            });
        }
        ids.removeIf(value -> !StringUtils.hasText(value));
        return List.copyOf(ids);
    }

    private static void putIfText(Map<String, Object> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value.trim());
        }
    }
}
