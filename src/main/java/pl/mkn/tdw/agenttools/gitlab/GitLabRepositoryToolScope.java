package pl.mkn.tdw.agenttools.gitlab;

import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevisionPort;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Session-bound group boundary with either a pinned commit or a moving branch ref. */
public final class GitLabRepositoryToolScope {

    private static final Pattern PROJECT_PATH = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,511}");
    private static final Pattern COMMIT_ID = Pattern.compile("(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})");

    private final String group;
    private final String selectedProject;
    private final String selectedBranch;
    private final String selectedCommit;
    private final boolean branchScoped;
    private final ConcurrentHashMap<String, String> revisions = new ConcurrentHashMap<>();
    private final Set<String> readSourceRefs = ConcurrentHashMap.newKeySet();
    private final Set<String> comparedProjects = ConcurrentHashMap.newKeySet();

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
        this.branchScoped = false;
        revisions.put(this.selectedProject + "@" + this.selectedBranch, selectedCommit);
    }

    private GitLabRepositoryToolScope(String group, String selectedProject, String selectedBranch) {
        this.group = checkedPath(group, "group");
        this.selectedProject = relativeProject(selectedProject);
        this.selectedBranch = checkedBranch(selectedBranch);
        this.selectedCommit = null;
        this.branchScoped = true;
    }

    public static GitLabRepositoryToolScope forBranch(String group, String selectedProject, String selectedBranch) {
        return new GitLabRepositoryToolScope(group, selectedProject, selectedBranch);
    }

    public record PinnedRevision(String project, String ref, String commitId) {}

    /** Multiple immutable operator-selected revisions; other projects pin their first requested revision. */
    public static GitLabRepositoryToolScope forRevisions(String group, List<PinnedRevision> selected) {
        if(selected == null || selected.isEmpty()) throw new IllegalArgumentException("Pinned revisions are required.");
        var first=selected.get(0);
        var scope=new GitLabRepositoryToolScope(group,first.project(),first.ref(),first.commitId());
        for(var revision:selected) {
            var project=scope.relativeProject(revision.project()); var ref=checkedBranch(revision.ref());
            if(revision.commitId()==null||!COMMIT_ID.matcher(revision.commitId()).matches())
                throw new IllegalArgumentException("Invalid pinned commit.");
            var old=scope.revisions.put(project+"@"+ref,revision.commitId());
            if(old!=null&&!old.equals(revision.commitId())) throw new IllegalArgumentException("Conflicting pinned revisions.");
            scope.comparedProjects.add(project);
        }
        return scope;
    }
    public Map<String,String> pinnedRevisions() { return Map.copyOf(revisions); }
    public void restoreResearchRevisions(List<PinnedRevision> values) {
        for(var revision:values) {
            var project=relativeProject(revision.project()); var ref=checkedBranch(revision.ref());
            if(comparedProjects.contains(project) && !revisions.containsKey(project+"@"+ref)) throw new IllegalArgumentException("Unknown compared revision.");
            if(revision.commitId()==null||!COMMIT_ID.matcher(revision.commitId()).matches()) throw new IllegalArgumentException("Invalid commit.");
            var old=revisions.putIfAbsent(project+"@"+ref,revision.commitId());
            if(old!=null&&!old.equals(revision.commitId())) throw new IllegalArgumentException("Conflicting commit.");
        }
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

    public Target resolve(String projectName, String branchRef, GitLabRepositoryRevisionPort port) {
        var project = relativeProject(projectName);
        var branch = checkedBranch(branchRef);
        if (comparedProjects.contains(project)) {
            var commit = revisions.get(project + "@" + branch);
            if (commit == null && COMMIT_ID.matcher(branch).matches()) {
                commit = revisions.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith(project + "@") && entry.getValue().equalsIgnoreCase(branch))
                        .map(Map.Entry::getValue).findFirst().orElse(null);
            }
            if (commit == null) throw new IllegalArgumentException("Only the selected comparison revisions are available for this project.");
            return new Target(group, project, branch, commit);
        }
        if (comparedProjects.isEmpty() && selectedProject.equals(project) && !selectedBranch.equals(branch)) {
            throw new IllegalArgumentException("The selected GitLab project is pinned to the operator's branch.");
        }
        if (branchScoped) {
            if (!selectedProject.equals(project)) {
                throw new IllegalArgumentException("The branch-scoped GitLab session is limited to its selected project.");
            }
            return new Target(group, project, branch, branch, false);
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
        var sourceRef = "gitlab:" + target.projectPath() + "@" + target.readRef() + ":" + filePath;
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
        if(value == null) throw new IllegalArgumentException("GitLab ref is required.");
        var kind = value.startsWith("refs/tags/") ? pl.mkn.tdw.integrations.gitlab.contract.GitLabRef.Kind.TAG
                : pl.mkn.tdw.integrations.gitlab.contract.GitLabRef.Kind.BRANCH;
        var name=value.startsWith("refs/tags/")?value.substring(10):value.startsWith("refs/heads/")?value.substring(11):value;
        new pl.mkn.tdw.integrations.gitlab.contract.GitLabRef(kind,name);
        return value;
    }

    private static String checkedPath(String value, String field) {
        if (value == null || !PROJECT_PATH.matcher(value).matches() || value.endsWith("/")
                || value.contains("//") || value.contains("..")) {
            throw new IllegalArgumentException("GitLab " + field + " is invalid.");
        }
        return value;
    }

    public record Target(String group, String projectName, String branch, String readRef, boolean pinned) {
        public Target(String group, String projectName, String branch, String readRef) {
            this(group, projectName, branch, readRef, true);
        }

        public String responseCommitId() {
            return pinned ? readRef : null;
        }

        public String projectPath() {
            return group + "/" + projectName;
        }
    }
}
