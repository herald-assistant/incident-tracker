package pl.mkn.tdw.integrations.gitlab.contract;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record GitLabRepositorySnapshot(String group, String project, String commitId, Map<String,String> blobs) {
    public GitLabRepositorySnapshot {
        blobs = Collections.unmodifiableMap(new TreeMap<>(blobs));
    }
}

