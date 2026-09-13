package pl.mkn.tdw.features.operationalcontextassistance.draft;

import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceRepositoryFacts;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;

import java.util.Set;

public record OperationalContextAssistanceDraftScope(
        OperationalContextAssistanceMode mode,
        String targetType,
        String targetId,
        Set<String> allowedSourceRefs,
        OperationalContextGitLabSourceSnapshot.RepositoryGit selectedRepositoryGit,
        OperationalContextAssistanceRepositoryFacts repositoryFacts,
        Set<String> selectedScopeIds
) {
    public OperationalContextAssistanceDraftScope(
            OperationalContextAssistanceMode mode, String targetType, String targetId,
            Set<String> allowedSourceRefs
    ) {
        this(mode, targetType, targetId, allowedSourceRefs, null, null, Set.of());
    }

    public OperationalContextAssistanceDraftScope(
            OperationalContextAssistanceMode mode, String targetType, String targetId,
            Set<String> allowedSourceRefs,
            OperationalContextGitLabSourceSnapshot.RepositoryGit selectedRepositoryGit
    ) {
        this(mode, targetType, targetId, allowedSourceRefs, selectedRepositoryGit, null, Set.of());
    }

    public OperationalContextAssistanceDraftScope {
        if (mode == null) {
            throw new IllegalArgumentException("Assistance mode is required.");
        }
        allowedSourceRefs = allowedSourceRefs == null ? Set.of() : Set.copyOf(allowedSourceRefs);
        selectedScopeIds = selectedScopeIds == null ? Set.of() : Set.copyOf(selectedScopeIds);
        if (!allowedSourceRefs.contains("operator:description")) {
            throw new IllegalArgumentException("Operator description must be an allowed source.");
        }
        if (mode != OperationalContextAssistanceMode.CREATE_AREA
                && (targetType == null || targetType.isBlank() || targetId == null || targetId.isBlank())) {
            throw new IllegalArgumentException("Existing entity target is required for this mode.");
        }
    }

    public boolean hasSelectedSource() {
        if (selectedRepositoryGit == null || selectedRepositoryGit.projectPath() == null) {
            return false;
        }
        return allowedSourceRefs.stream().anyMatch(this::isSelectedGitLabFileRef);
    }

    public boolean isSelectedGitLabFileRef(String ref) {
        if (ref == null || selectedRepositoryGit == null || selectedRepositoryGit.projectPath() == null) {
            return false;
        }
        var prefix = "gitlab:" + selectedRepositoryGit.projectPath() + "@";
        if (!allowedSourceRefs.contains(ref)) {
            return false;
        }
        if (!ref.startsWith(prefix)) {
            return false;
        }
        var commitEnd = ref.indexOf(':', prefix.length());
        return (commitEnd == prefix.length() + 40 || commitEnd == prefix.length() + 64)
                && ref.substring(prefix.length(), commitEnd).matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")
                && commitEnd < ref.length() - 1;
    }

    public OperationalContextAssistanceRepositoryFacts.Usage repositoryUsage() {
        return repositoryFacts != null ? repositoryFacts.usage()
                : selectedRepositoryGit != null ? OperationalContextAssistanceRepositoryFacts.Usage.UNKNOWN : null;
    }

    public Set<String> selectedSystemIds() {
        return repositoryFacts == null ? Set.of() : Set.copyOf(repositoryFacts.systemIds());
    }
}
