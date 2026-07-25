package pl.mkn.tdw.features.changeverification.ai.copilot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationChangedFileSnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationRepositorySnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;

import java.util.LinkedHashMap;
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
        context.put(ChangeVerificationCopilotToolContextKeys.DATABASE_READONLY_ONLY, true);
        putIfText(context, AgentToolContextKeys.ENVIRONMENT, request != null ? request.environment() : null);
        putIfText(context, ChangeVerificationCopilotToolContextKeys.DATABASE_APPLICATION,
                request != null ? request.databaseApplication() : null);
        context.put(ChangeVerificationCopilotToolContextKeys.REPOSITORY_SCOPE_RESOLVED, !repositories.isEmpty());
        context.put(
                ChangeVerificationCopilotToolContextKeys.ALLOWED_REPOSITORIES,
                repositories.stream()
                        .map(this::repositoryScope)
                        .toList()
        );
        return context;
    }

    private Map<String, Object> repositoryScope(ChangeVerificationRepositorySnapshot repository) {
        var scope = new LinkedHashMap<String, Object>();
        putIfText(scope, "repositoryKey", repository.repositoryKey());
        putIfText(scope, "projectPath", repository.projectPath());
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
