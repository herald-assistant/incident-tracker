package pl.mkn.tdw.integrations.gitlab.adapter.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryComparisonPort;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryPath;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryTreeNode;
import pl.mkn.tdw.integrations.gitlab.internal.GitLabReadRetry;
import pl.mkn.tdw.integrations.gitlab.service.GitLabRepositoryTreeService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
public class GitLabRestRepositoryComparisonAdapter implements GitLabRepositoryComparisonPort {
    private final GitLabProperties properties;
    private final GitLabRestClientFactory clients;
    private final GitLabRepositoryTreeService trees;

    @Override
    public GitLabRepositoryComparison compare(String group, String project, String baselineCommit, String targetCommit) {
        if (group == null || group.isBlank() || !GitLabRepositoryPath.isSafePath(project, false)
                || !sha(baselineCommit) || !sha(targetCommit))
            throw new IllegalArgumentException("A project and two pinned commit SHAs are required.");
        if (baselineCommit.equals(targetCommit))
            return new GitLabRepositoryComparison(baselineCommit, targetCommit, List.of(), true, List.of());
        var before = inventory(group, project, baselineCommit);
        var after = inventory(group, project, targetCommit);
        var paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        var changed = new TreeSet<String>();
        for (var path : paths) if (!Objects.equals(before.get(path), after.get(path))) changed.add(path);
        if (changed.isEmpty())
            return new GitLabRepositoryComparison(baselineCommit, targetCommit, List.of(), true, List.of());
        var limits = new ArrayList<String>();
        var patches = patches(group, project, baselineCommit, targetCommit, limits);
        var result = new ArrayList<GitLabRepositoryComparison.Change>();
        var consumed = new HashSet<String>();
        for (var patch : patches) {
            if (patch == null || !Boolean.TRUE.equals(patch.renamed()) || patch.oldPath() == null || patch.newPath() == null
                    || patch.oldPath().equals(patch.newPath()) || !changed.contains(patch.oldPath())
                    || !changed.contains(patch.newPath()) || after.containsKey(patch.oldPath())
                    || before.containsKey(patch.newPath()) || consumed.contains(patch.oldPath()) || consumed.contains(patch.newPath())) continue;
            var oldNode = before.get(patch.oldPath());
            var newNode = after.get(patch.newPath());
            if (oldNode == null || newNode == null) continue;
            result.add(change(patch.oldPath(), patch.newPath(), oldNode, newNode, patch));
            consumed.add(patch.oldPath());
            consumed.add(patch.newPath());
        }
        for (var path : changed) {
            if (consumed.contains(path)) continue;
            var oldNode = before.get(path);
            var newNode = after.get(path);
            var matching = patches.stream().filter(Objects::nonNull).filter(patch ->
                    Objects.equals(path, patch.oldPath()) && Objects.equals(path, patch.newPath())
                            || oldNode == null && Boolean.TRUE.equals(patch.added()) && Objects.equals(path, patch.newPath())
                            || newNode == null && Boolean.TRUE.equals(patch.deleted()) && Objects.equals(path, patch.oldPath())).toList();
            result.add(change(oldNode == null ? null : path, newNode == null ? null : path,
                    oldNode, newNode, matching.size() == 1 ? matching.get(0) : null));
        }
        result.sort(Comparator.comparing(change -> Objects.toString(change.newPath(), change.oldPath())));
        return new GitLabRepositoryComparison(baselineCommit, targetCommit, result, true, limits);
    }

    private Map<String, ObjectId> inventory(String group, String project, String commit) {
        var inventory = new TreeMap<String, ObjectId>();
        for (var node : trees.fetchRepositoryNodes(properties.getBaseUrl(), group + "/" + project, commit, "", null)) {
            if (!GitLabRepositoryPath.isSafePath(node.path(), false) || !sha(node.id()))
                throw new IllegalStateException("Invalid repository tree entry.");
            if ("tree".equals(node.type()) && "040000".equals(node.mode())) continue;
            if (!("blob".equals(node.type()) && Set.of("100644", "100755", "120000").contains(node.mode())
                    || "commit".equals(node.type()) && "160000".equals(node.mode())))
                throw new IllegalStateException("Unsupported repository object mode.");
            if (inventory.putIfAbsent(node.path(), new ObjectId(node.id(), node.mode())) != null)
                throw new IllegalStateException("Duplicate repository tree path.");
        }
        return inventory;
    }

    private List<Patch> patches(String group, String project, String before, String after, List<String> limits) {
        var base = properties.getBaseUrl().replaceFirst("/+$", "");
        if (!base.endsWith("/api/v4")) base += "/api/v4";
        var uri = URI.create(base + "/projects/" + UriUtils.encodePathSegment(group + "/" + project, StandardCharsets.UTF_8)
                + "/repository/compare?from=" + before + "&to=" + after + "&straight=true");
        try {
            var response = GitLabReadRetry.execute("repository-compare", () -> clients.create().get().uri(uri)
                    .retrieve().body(CompareResponse.class));
            if (response == null || !Boolean.FALSE.equals(response.timeout()) || response.diffs() == null) {
                limits.add("Compare patches are unavailable or their computation was incomplete; read full files at the pinned SHAs.");
                return List.of();
            }
            return response.diffs();
        } catch (RestClientException exception) {
            limits.add("Compare patches could not be fetched; the complete change list was verified from Git trees.");
            return List.of();
        }
    }

    private GitLabRepositoryComparison.Change change(String before, String after, ObjectId oldNode, ObjectId newNode, Patch patch) {
        return new GitLabRepositoryComparison.Change(before, after, oldNode == null ? null : oldNode.id(),
                newNode == null ? null : newNode.id(), oldNode == null ? null : oldNode.mode(),
                newNode == null ? null : newNode.mode(), patch == null ? null : patch.diff(),
                patch != null && patch.diff() != null && !patch.diff().isBlank()
                        && !Boolean.TRUE.equals(patch.collapsed()) && !Boolean.TRUE.equals(patch.tooLarge()));
    }

    private boolean sha(String value) { return value != null && value.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"); }
    private record ObjectId(String id, String mode) {}
    private record CompareResponse(@JsonProperty("compare_timeout") Boolean timeout, List<Patch> diffs) {}
    private record Patch(@JsonProperty("old_path") String oldPath, @JsonProperty("new_path") String newPath,
            @JsonProperty("new_file") Boolean added, @JsonProperty("deleted_file") Boolean deleted,
            @JsonProperty("renamed_file") Boolean renamed, String diff, Boolean collapsed,
            @JsonProperty("too_large") Boolean tooLarge) {}
}