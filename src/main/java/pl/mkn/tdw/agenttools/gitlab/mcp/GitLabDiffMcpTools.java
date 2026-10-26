package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabDiffToolScope;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot.FileDiff;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryPath;

import java.util.ArrayList;
import java.util.List;

@Component
public class GitLabDiffMcpTools {
    @Tool(name = GitLabToolNames.READ_MERGE_REQUEST_DIFF, description = "Read a prepared immutable MR diff version. "
            + "Use filePaths for focused reads. before=baseSha/oldPath; after=headSha/newPath; startSha is the target tip. "
            + "Continue with nextFileOffset/nextPatchOffset using the same filePaths. Missing patches are not proof of no change.")
    public DiffResult readMergeRequestDiff(
            @ToolParam(description = "Exact projectName from prepared repository scope.") String projectName,
            @ToolParam(description = "MR iid from prepared context.") long mergeRequestIid,
            @ToolParam(required = false, description = "Exact old or new paths; omitted means all files.") List<String> filePaths,
            @ToolParam(required = false, description = "Zero-based file offset in filtered files.") Integer fileOffset,
            @ToolParam(required = false, description = "Zero-based character offset inside the selected patch.") Integer patchOffset,
            @ToolParam(required = false, description = "Total returned patch characters, default 20000, maximum 60000.") Integer maxCharacters,
            @ToolParam(description = "One short Polish sentence explaining the purpose.") String reason,
            ToolContext toolContext
    ) {
        var bound = toolContext == null ? null : toolContext.getContext().get(AgentToolContextKeys.GITLAB_DIFF_SCOPE);
        if (!(bound instanceof GitLabDiffToolScope scope)) {
            throw new IllegalArgumentException("Prepared MR diff scope is unavailable.");
        }
        var snapshot = scope.requireMergeRequest(projectName, mergeRequestIid);
        var paths = filePaths == null ? List.<String>of() : List.copyOf(filePaths);
        if (paths.size() > 100 || paths.stream().anyMatch(p -> !GitLabRepositoryPath.isSafePath(p, false))) {
            throw new IllegalArgumentException("filePaths must contain at most 100 safe repository paths.");
        }
        var files = snapshot.files().stream().filter(f -> paths.isEmpty()
                || paths.contains(f.oldPath()) || paths.contains(f.newPath())).toList();
        int index = fileOffset == null ? 0 : fileOffset;
        int offset = patchOffset == null ? 0 : patchOffset;
        if (index < 0 || index > files.size() || offset < 0 || (index == files.size() && offset != 0)) {
            throw new IllegalArgumentException("Invalid diff continuation offsets.");
        }
        int remaining = maxCharacters == null ? 20_000 : Math.max(1, Math.min(maxCharacters, 60_000));
        var slices = new ArrayList<PatchSlice>();
        var limits = new ArrayList<>(snapshot.limitations());
        for (var path : paths) {
            if (files.stream().noneMatch(f -> path.equals(f.oldPath()) || path.equals(f.newPath()))) {
                limits.add("Requested path is absent from the available diff: " + path);
            }
        }
        while (index < files.size() && slices.size() < 20 && remaining > 0) {
            FileDiff file = files.get(index);
            String patch = file.patch() == null ? "" : file.patch();
            if (offset > patch.length()) {
                throw new IllegalArgumentException("patchOffset exceeds the patch size.");
            }
            int end = Math.min(patch.length(), offset + remaining);
            boolean unavailable = file.collapsed() || file.tooLarge() || patch.isEmpty();
            slices.add(new PatchSlice(file.oldPath(), file.newPath(), file.newFile(), file.renamedFile(), file.deletedFile(),
                    patch.substring(offset, end), offset, patch.length(), end < patch.length(), unavailable,
                    file.collapsed(), file.tooLarge()));
            if (unavailable) {
                limits.add("Patch content is unavailable or empty for " + file.newPath()
                        + "; inspect status/paths and read base/head files where available (binary or metadata-only changes are possible).");
            }
            remaining -= end - offset;
            if (end < patch.length()) {
                offset = end;
                break;
            }
            index++;
            offset = 0;
        }
        boolean more = index < files.size();
        boolean missingPatches = files.stream().anyMatch(f -> f.collapsed() || f.tooLarge() || f.patch() == null || f.patch().isEmpty());
        return new DiffResult(snapshot.projectPath(), projectName, mergeRequestIid, snapshot.versionId(), snapshot.baseSha(), snapshot.startSha(),
                snapshot.headSha(), files.size(), slices, more ? index : null, more ? offset : null,
                snapshot.hasRevisions() && !missingPatches && !more && limits.isEmpty(), limits);
    }

    public record PatchSlice(String oldPath, String newPath, boolean newFile, boolean renamedFile, boolean deletedFile,
                             String patch, int patchOffset, int patchCharacters, boolean truncated, boolean unavailable,
                             boolean collapsed, boolean tooLarge) {}

    public record DiffResult(String projectPath, String projectName, long mergeRequestIid, Long versionId, String baseSha, String startSha,
                             String headSha, int matchingFiles, List<PatchSlice> files, Integer nextFileOffset,
                             Integer nextPatchOffset, boolean complete, List<String> limitations) {}
}
