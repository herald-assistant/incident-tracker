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
        var nodes=treeService.fetchRepositoryBlobs(settings.getBaseUrl(),group+"/"+project,commitId,"",treeService.openSession());
        var blobs=new TreeMap<String,String>();
        for(var node:nodes) {
            if(!GitLabRepositoryPath.isSafePath(node.path(),false) || node.id()==null
                    || !node.id().matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")
                    || blobs.putIfAbsent(node.path(),node.id())!=null)
                throw new IllegalStateException("Invalid or duplicate repository tree entry.");
        }
        return new GitLabRepositorySnapshot(group,project,commitId,blobs);
    }
}

