package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.job.report.ChangeVerificationReportSectionIds;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationChangedFileSnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationRepositorySnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ChangeVerificationCopilotToolSessionContextFactory {

    private static final String SESSION_ID_PREFIX = "change-verification-";

    public CopilotToolSessionContext create(
            String runReference,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            String runKind
    ) {
        var normalizedRunReference = normalizeRunReference(runReference);

        return new CopilotToolSessionContext(
                normalizedRunReference,
                SESSION_ID_PREFIX + normalizedRunReference,
                hiddenContext(request, sourceDiscovery, runKind)
        );
    }

    private Map<String, Object> hiddenContext(
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            String runKind
    ) {
        var context = new LinkedHashMap<String, Object>();
        var repositories = sourceDiscovery != null ? sourceDiscovery.repositories() : List.<ChangeVerificationRepositorySnapshot>of();

        context.put(ChangeVerificationCopilotToolContextKeys.FEATURE, ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE);
        context.put(ChangeVerificationCopilotToolContextKeys.RUN_KIND, normalizeRunKind(runKind));
        context.put(ChangeVerificationCopilotToolContextKeys.REPOSITORY_SCOPE_RESOLVED, !repositories.isEmpty());
        context.put(
                ChangeVerificationCopilotToolContextKeys.ALLOWED_REPOSITORIES,
                repositories.stream()
                        .map(this::repositoryScope)
                        .toList()
        );
        var allowedApplicationNames = allowedApplicationNames(repositories);
        if (!allowedApplicationNames.isEmpty()) {
            context.put(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES, allowedApplicationNames);
        }
        if (ChangeVerificationCopilotToolContextKeys.RUN_KIND_COMPLIANCE.equals(normalizeRunKind(runKind))) {
            context.put(AgentToolContextKeys.REPORT_ID, "report-" + UUID.randomUUID());
            context.put(AgentToolContextKeys.REPORT_FEATURE, ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE);
            context.put(
                    AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS,
                    ChangeVerificationReportSectionIds.activeComplianceSectionIds(request)
            );
        }
        return context;
    }

    private List<String> allowedApplicationNames(List<ChangeVerificationRepositorySnapshot> repositories) {
        var systemIds = new LinkedHashSet<String>();
        repositories.stream()
                .flatMap(repository -> repository.operationalContextMatches().stream())
                .filter(match -> "system".equalsIgnoreCase(match.targetType()))
                .map(match -> match.targetId())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(systemIds::add);
        return List.copyOf(systemIds);
    }

    private Map<String, Object> repositoryScope(ChangeVerificationRepositorySnapshot repository) {
        var scope = new LinkedHashMap<String, Object>();
        putIfText(scope, "repositoryKey", repository.repositoryKey());
        putIfText(scope, "projectPath", repository.projectPath());
        putIfText(scope, "rootGroup", repository.rootGroup());
        putIfText(scope, "groupPath", repository.groupPath());
        putIfText(scope, "repositoryName", repository.repositoryName());
        putIfText(scope, "projectName", repository.projectName());
        putIfText(scope, "sourceRef", repository.sourceRef());
        putIfText(scope, "targetRef", repository.targetRef());
        scope.put(
                "mergeRequestRefs",
                repository.mergeRequests().stream()
                        .map(mergeRequest -> "!" + mergeRequest.iid())
                        .toList()
        );
        scope.put(
                "changedFiles",
                repository.changedFiles().stream()
                        .map(this::changedFileScope)
                        .toList()
        );
        scope.put(
                "instructionSources",
                repository.instructionSources().stream()
                        .map(this::instructionSourceScope)
                        .toList()
        );
        scope.put(
                "operationalContextMatches",
                repository.operationalContextMatches().stream()
                        .map(this::operationalContextMatchScope)
                        .toList()
        );
        return scope;
    }

    private Map<String, Object> operationalContextMatchScope(
            pl.mkn.tdw.features.changeverification.source.ChangeVerificationOperationalContextMatch match
    ) {
        var scope = new LinkedHashMap<String, Object>();
        putIfText(scope, "repositoryId", match.repositoryId());
        putIfText(scope, "codeSearchScopeId", match.codeSearchScopeId());
        putIfText(scope, "codeSearchScopeName", match.codeSearchScopeName());
        putIfText(scope, "scopeType", match.scopeType());
        putIfText(scope, "targetType", match.targetType());
        putIfText(scope, "targetId", match.targetId());
        putIfText(scope, "relation", "repo->code-search-scope->target");
        putIfText(scope, "repositoryRole", match.repositoryRole());
        if (match.priority() != null) {
            scope.put("priority", match.priority());
        }
        putIfText(scope, "reason", match.reason());
        scope.put("readFor", match.readFor());
        putIfText(scope, "searchMode", match.searchMode());
        scope.put("pathPrefixes", match.pathPrefixes());
        scope.put("limitations", match.limitations());
        return scope;
    }

    private Map<String, Object> changedFileScope(ChangeVerificationChangedFileSnapshot file) {
        var scope = new LinkedHashMap<String, Object>();
        putIfText(scope, "path", file.path());
        putIfText(scope, "oldPath", file.oldPath());
        putIfText(scope, "newPath", file.newPath());
        scope.put("newFile", file.newFile());
        scope.put("renamedFile", file.renamedFile());
        scope.put("deletedFile", file.deletedFile());
        scope.put("mergeRequestRefs", file.mergeRequestRefs());
        return scope;
    }

    private Map<String, Object> instructionSourceScope(InstructionSource source) {
        var scope = new LinkedHashMap<String, Object>();
        putIfText(scope, "repositoryKey", source.repositoryKey());
        putIfText(scope, "ref", source.ref());
        putIfText(scope, "path", source.path());
        putIfText(scope, "kind", source.kind());
        putIfText(scope, "referencedBy", source.referencedBy());
        scope.put("applicableChangedFiles", source.applicableChangedFiles());
        return scope;
    }

    private static String normalizeRunKind(String runKind) {
        return StringUtils.hasText(runKind)
                ? runKind.trim()
                : ChangeVerificationCopilotToolContextKeys.RUN_KIND_COMPLIANCE;
    }

    private static String normalizeRunReference(String runReference) {
        if (!StringUtils.hasText(runReference)) {
            return UUID.randomUUID().toString();
        }
        return runReference.trim();
    }

    private static void putIfText(Map<String, Object> scope, String key, String value) {
        if (StringUtils.hasText(value)) {
            scope.put(key, value.trim());
        }
    }
}
