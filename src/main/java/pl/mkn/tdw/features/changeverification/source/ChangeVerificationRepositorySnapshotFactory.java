package pl.mkn.tdw.features.changeverification.source;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.common.GitLabPathUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestChangedFile;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChangeVerificationRepositorySnapshotFactory {

    private ChangeVerificationRepositorySnapshotFactory() {
    }

    static List<ChangeVerificationRepositorySnapshot> from(
            GitLabMergeRequestSearchResult mergeRequests,
            InstructionContextResult instructionContext
    ) {
        return from(mergeRequests, instructionContext, mergeRequests != null ? mergeRequests.group() : null);
    }

    static List<ChangeVerificationRepositorySnapshot> from(
            GitLabMergeRequestSearchResult mergeRequests,
            InstructionContextResult instructionContext,
            String configuredGitLabGroup
    ) {
        return from(mergeRequests, instructionContext, configuredGitLabGroup, List.of());
    }

    static List<ChangeVerificationRepositorySnapshot> from(
            GitLabMergeRequestSearchResult mergeRequests,
            InstructionContextResult instructionContext,
            String configuredGitLabGroup,
            List<ChangeVerificationRepositoryRefSelection> refSelections
    ) {
        if (mergeRequests == null || mergeRequests.mergeRequests().isEmpty()) {
            return List.of();
        }

        var grouped = new LinkedHashMap<String, List<GitLabMergeRequest>>();
        for (var mergeRequest : mergeRequests.mergeRequests()) {
            grouped.computeIfAbsent(groupKey(mergeRequest), ignored -> new ArrayList<>()).add(mergeRequest);
        }
        var refsByKey = refSelectionsByKey(refSelections);

        return grouped.values().stream()
                .map(group -> repositorySnapshot(group, instructionContext, configuredGitLabGroup, refsByKey))
                .toList();
    }

    private static ChangeVerificationRepositorySnapshot repositorySnapshot(
            List<GitLabMergeRequest> mergeRequests,
            InstructionContextResult instructionContext,
            String configuredGitLabGroup,
            Map<String, ChangeVerificationRepositoryRefSelection> refsByKey
    ) {
        var first = mergeRequests.get(0);
        var projectPath = value(first.projectPath());
        var sourceRef = ref(first.sourceBranch(), first.targetBranch());
        var targetRef = value(first.targetBranch());
        var refSelection = refsByKey.get(ChangeVerificationRepositoryRefSelection.key(projectPath, sourceRef, targetRef));
        var analysisRef = refSelection != null ? refSelection.analysisRef() : sourceRef;
        var instructionSources = instructionSources(projectPath, analysisRef, instructionContext);
        var snapshot = new ChangeVerificationRepositorySnapshot(
                projectPath,
                projectPath,
                projectName(configuredGitLabGroup, projectPath),
                sourceRef,
                targetRef,
                mergeRequests,
                changedFiles(mergeRequests),
                instructionSources,
                limitations(mergeRequests, instructionContext, instructionSources)
        );
        return snapshot.withRefSelection(refSelection);
    }

    private static List<ChangeVerificationChangedFileSnapshot> changedFiles(List<GitLabMergeRequest> mergeRequests) {
        var files = new LinkedHashMap<String, ChangeVerificationChangedFileBuilder>();
        for (var mergeRequest : mergeRequests) {
            for (var file : mergeRequest.changedFiles()) {
                var path = path(file);
                if (!StringUtils.hasText(path)) {
                    continue;
                }
                files.computeIfAbsent(path, ignored -> new ChangeVerificationChangedFileBuilder(file))
                        .addMergeRequestRef(mergeRequestRef(mergeRequest));
            }
        }
        return files.values().stream()
                .map(ChangeVerificationChangedFileBuilder::build)
                .toList();
    }

    private static List<InstructionSource> instructionSources(
            String projectPath,
            String sourceRef,
            InstructionContextResult instructionContext
    ) {
        if (instructionContext == null) {
            return List.of();
        }
        return instructionContext.sources().stream()
                .filter(source -> repositoryMatches(projectPath, source))
                .filter(source -> refMatches(sourceRef, source))
                .toList();
    }

    private static List<String> limitations(
            List<GitLabMergeRequest> mergeRequests,
            InstructionContextResult instructionContext,
            List<InstructionSource> instructionSources
    ) {
        var limitations = new ArrayList<String>();
        mergeRequests.stream()
                .flatMap(mergeRequest -> mergeRequest.limitations().stream())
                .filter(StringUtils::hasText)
                .forEach(limitations::add);
        if (instructionContext != null && instructionSources.isEmpty()) {
            limitations.add("No repository instruction sources matched this repository/ref.");
        }
        return limitations.stream().distinct().toList();
    }

    private static String groupKey(GitLabMergeRequest mergeRequest) {
        return value(mergeRequest.projectPath()) + "|" + ref(mergeRequest.sourceBranch(), mergeRequest.targetBranch())
                + "|" + value(mergeRequest.targetBranch());
    }

    private static Map<String, ChangeVerificationRepositoryRefSelection> refSelectionsByKey(
            List<ChangeVerificationRepositoryRefSelection> refSelections
    ) {
        var result = new LinkedHashMap<String, ChangeVerificationRepositoryRefSelection>();
        if (refSelections == null) {
            return result;
        }
        refSelections.stream()
                .filter(selection -> selection != null && StringUtils.hasText(selection.key()))
                .forEach(selection -> result.putIfAbsent(selection.key(), selection));
        return result;
    }

    private static boolean repositoryMatches(String projectPath, InstructionSource source) {
        return value(projectPath).equals(value(source.repositoryKey()));
    }

    private static boolean refMatches(String sourceRef, InstructionSource source) {
        return !StringUtils.hasText(source.ref()) || value(sourceRef).equals(value(source.ref()));
    }

    private static String ref(String sourceBranch, String targetBranch) {
        return StringUtils.hasText(sourceBranch) ? sourceBranch.trim() : value(targetBranch);
    }

    private static String projectName(String configuredGitLabGroup, String projectPath) {
        var value = value(projectPath);
        var relativeProjectPath = GitLabPathUtils.relativeProjectPath(configuredGitLabGroup, value);
        if (StringUtils.hasText(relativeProjectPath)) {
            return relativeProjectPath;
        }
        var index = value.lastIndexOf('/');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private static String path(GitLabMergeRequestChangedFile file) {
        return StringUtils.hasText(file.newPath()) ? file.newPath().trim() : value(file.oldPath());
    }

    private static String mergeRequestRef(GitLabMergeRequest mergeRequest) {
        if (mergeRequest.iid() != null) {
            return "!" + mergeRequest.iid();
        }
        return value(mergeRequest.webUrl());
    }

    private static String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static final class ChangeVerificationChangedFileBuilder {

        private final GitLabMergeRequestChangedFile file;
        private final List<String> mergeRequestRefs = new ArrayList<>();

        private ChangeVerificationChangedFileBuilder(GitLabMergeRequestChangedFile file) {
            this.file = file;
        }

        private ChangeVerificationChangedFileBuilder addMergeRequestRef(String mergeRequestRef) {
            if (StringUtils.hasText(mergeRequestRef) && !mergeRequestRefs.contains(mergeRequestRef)) {
                mergeRequestRefs.add(mergeRequestRef);
            }
            return this;
        }

        private ChangeVerificationChangedFileSnapshot build() {
            return new ChangeVerificationChangedFileSnapshot(
                    path(file),
                    file.oldPath(),
                    file.newPath(),
                    file.newFile(),
                    file.renamedFile(),
                    file.deletedFile(),
                    mergeRequestRefs
            );
        }
    }
}
