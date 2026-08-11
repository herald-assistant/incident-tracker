package pl.mkn.tdw.features.configdriftviewer.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.configdriftviewer.ai.report.ConfigDriftViewerReportSectionIds;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerAffectedEntity;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepRepositoryScope;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
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
public class ConfigDriftViewerCopilotToolSessionContextFactory {

    private static final String SESSION_PREFIX = "runtime-config-";

    private final GitLabProperties gitLabProperties;

    public CopilotToolSessionContext create(
            String runReference,
            ConfigDriftViewerJobStartRequest request,
            ConfigDriftViewerDeepContext deepContext
    ) {
        if (request == null || request.mode() != ConfigDriftViewerMode.DEEP) {
            throw new IllegalArgumentException("Copilot tool context is available only for DEEP verification.");
        }
        var runId = StringUtils.hasText(runReference) ? runReference.trim() : UUID.randomUUID().toString();
        var hidden = new LinkedHashMap<String, Object>();
        hidden.put(ConfigDriftViewerCopilotToolContextKeys.FEATURE,
                ConfigDriftViewerCopilotToolContextKeys.FEATURE_VALUE);
        hidden.put(ConfigDriftViewerCopilotToolContextKeys.MODE, ConfigDriftViewerMode.DEEP.name());
        if (StringUtils.hasText(request.componentSystemId())) {
            hidden.put(ConfigDriftViewerCopilotToolContextKeys.SYSTEM_ID, request.componentSystemId());
            hidden.put(
                    AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES,
                    List.of(request.componentSystemId().trim())
            );
        }
        hidden.put(AgentToolContextKeys.REPORT_ID, "report-" + UUID.randomUUID());
        hidden.put(AgentToolContextKeys.REPORT_FEATURE,
                ConfigDriftViewerCopilotToolContextKeys.FEATURE_VALUE);
        hidden.put(
                AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS,
                ConfigDriftViewerReportSectionIds.aiWritable()
        );

        if (deepContext != null) {
            hidden.put(
                    ConfigDriftViewerCopilotToolContextKeys.ALLOWED_REPOSITORIES,
                    repositoryScopes(deepContext)
            );
            hidden.put(
                    ConfigDriftViewerCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS,
                    operationalEntityIds(deepContext)
            );
            if (StringUtils.hasText(gitLabProperties.getGroup())) {
                hidden.put(AgentToolContextKeys.GITLAB_GROUP, gitLabProperties.getGroup().trim());
            }
        } else {
            hidden.put(ConfigDriftViewerCopilotToolContextKeys.ALLOWED_REPOSITORIES, List.of());
            hidden.put(ConfigDriftViewerCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS, List.of());
        }
        return new CopilotToolSessionContext(runId, SESSION_PREFIX + runId, hidden);
    }

    private List<Map<String, Object>> repositoryScopes(ConfigDriftViewerDeepContext deepContext) {
        if (deepContext.preflight() == null) {
            return List.of();
        }
        return deepContext.preflight().repositories().stream()
                .filter(ConfigDriftViewerDeepRepositoryScope::ready)
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

    private List<String> operationalEntityIds(ConfigDriftViewerDeepContext deepContext) {
        var ids = new LinkedHashSet<String>();
        if (deepContext.primarySystem() != null) {
            ids.add(deepContext.primarySystem().systemId());
        }
        var entities = new ArrayList<ConfigDriftViewerAffectedEntity>();
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
