package pl.mkn.tdw.integrations.gitlab.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySnapshotPort;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySnapshot;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryPath;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class GitLabRepositorySnapshotService implements GitLabRepositorySnapshotPort {
    private final GitLabSettingsPort settings;
    private final GitLabRepositoryTreeService treeService;

    @Override public GitLabRepositorySnapshot snapshot(String group,String project,String commitId) {
        if(commitId == null || !commitId.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("A complete commit SHA is required.");
        var nodes=treeService.fetchRepositoryNodes(settings.getBaseUrl(),group+"/"+project,commitId,"",treeService.openSession());
        var blobs=new TreeMap<String,String>();
        var gitlinks=new TreeMap<String,String>();var symlinks=new TreeMap<String,String>();var paths=new java.util.HashSet<String>();
        for(var node:nodes) {
            if(!GitLabRepositoryPath.isSafePath(node.path(),false) || node.id()==null
                    || !node.id().matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")
                    || !validMode(node) || !paths.add(node.path()))
                throw new IllegalStateException("Invalid or duplicate repository tree entry.");
            if("blob".equals(node.type()))blobs.put(node.path(),node.id());
            if("commit".equals(node.type()))gitlinks.put(node.path(),node.id());
            if("120000".equals(node.mode()))symlinks.put(node.path(),node.id());
        }
        return new GitLabRepositorySnapshot(group,project,commitId,blobs,gitlinks,symlinks);
    }
    private boolean validMode(pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryTreeNode node) {
        if(node.mode()==null||node.type()==null)return false;
        return switch(node.type()) {
            case "blob"->java.util.Set.of("100644","100755","120000").contains(node.mode());
            case "tree"->"040000".equals(node.mode());
            case "commit"->"160000".equals(node.mode());
            default->false;
        };
    }
}

