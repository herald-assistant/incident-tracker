package pl.mkn.tdw.agenttools.gitlab;

import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Session-bound group boundary and immutable revisions for GitLab repository tools. */
public final class GitLabRepositoryToolScope {

    private static final Pattern PROJECT_PATH = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,511}");
    private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}");
    private static final Pattern COMMIT_ID = Pattern.compile("(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})");

    private final String group;
    private final String selectedProject;
    private final String selectedBranch;
    private final String selectedCommit;
    private final ConcurrentHashMap<String, String> revisions = new ConcurrentHashMap<>();
    private final Set<String> readSourceRefs = ConcurrentHashMap.newKeySet();

    public GitLabRepositoryToolScope(
            String group, String selectedProject, String selectedBranch, String selectedCommit
    ) {
        this.group = checkedPath(group, "group");
        this.selectedProject = relativeProject(selectedProject);
        this.selectedBranch = checkedBranch(selectedBranch);
        if (selectedCommit == null || !COMMIT_ID.matcher(selectedCommit).matches()) {
            throw new IllegalArgumentException("Selected GitLab commit is invalid.");
        }
        this.selectedCommit = selectedCommit;
        revisions.put(this.selectedProject + "@" + this.selectedBranch, selectedCommit);
    }

    public String group() {
        return group;
    }

    public String selectedProject() {
        return selectedProject;
    }

    public String selectedBranch() {
        return selectedBranch;
    }

    public String selectedProjectPath() {
        return group + "/" + selectedProject;
    }

    public String projectPath(String projectName) {
        return projectPathInGroup(group, projectName);
    }

    public static String projectPathInGroup(String group, String projectName) {
        var checkedGroup = checkedPath(group, "group");
        var path = checkedPath(projectName, "projectName");
        if (path.startsWith(checkedGroup + "/")) {
            path = path.substring(checkedGroup.length() + 1);
        }
        if (path.equals(checkedGroup) || path.isBlank()) {
            throw new IllegalArgumentException("A GitLab project inside the configured group is required.");
        }
        return checkedGroup + "/" + path;
    }

    public String selectedCommit() {
        return selectedCommit;
    }

    public Target resolve(String projectName, String branchRef, GitLabRepositoryPort port) {
        var project = relativeProject(projectName);
        var branch = checkedBranch(branchRef);
        if (selectedProject.equals(project) && !selectedBranch.equals(branch)) {
            throw new IllegalArgumentException("The selected GitLab project is pinned to the operator's branch.");
        }
        var commit = revisions.computeIfAbsent(project + "@" + branch, ignored -> {
            var revision = port.resolveRevision(group, project, branch);
            if (revision == null || !group.equals(revision.group())
                    || !project.equals(revision.projectName()) || !branch.equals(revision.ref())
                    || revision.commitId() == null || !COMMIT_ID.matcher(revision.commitId()).matches()) {
                throw new IllegalStateException("GitLab branch could not be pinned to a commit.");
            }
            return revision.commitId();
        });
        return new Target(group, project, branch, commit);
    }

    public String recordRead(Target target, String filePath) {
        var sourceRef = "gitlab:" + target.projectPath() + "@" + target.commitId() + ":" + filePath;
        readSourceRefs.add(sourceRef);
        return sourceRef;
    }

    public Set<String> readSourceRefs() {
        return Set.copyOf(readSourceRefs);
    }

    private String relativeProject(String value) {
        return projectPathInGroup(group, value).substring(group.length() + 1);
    }

    private static String checkedBranch(String value) {
        if (value == null || !BRANCH.matcher(value).matches() || value.endsWith("/")
                || value.contains("//") || value.contains("..")) {
            throw new IllegalArgumentException("GitLab branch is invalid.");
        }
        return value;
    }

    private static String checkedPath(String value, String field) {
        if (value == null || !PROJECT_PATH.matcher(value).matches() || value.endsWith("/")
                || value.contains("//") || value.contains("..")) {
            throw new IllegalArgumentException("GitLab " + field + " is invalid.");
        }
        return value;
    }

    public record Target(String group, String projectName, String branch, String commitId) {
        public String projectPath() {
            return group + "/" + projectName;
        }
    }
}
