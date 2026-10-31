package pl.mkn.tdw.features.releasesummary.contract;
import java.util.List;
import java.util.Map;
public record ReleaseSummaryScope(String systemId, String systemName, String group,
        String catalogDigest, List<Repository> repositories, List<Map<String,Object>> entities) {
    public ReleaseSummaryScope { repositories=List.copyOf(repositories); entities=List.copyOf(entities); }
    public record Repository(String repositoryId, String project, List<String> prefixes,
            String baselineRef, String baselineCommit, String targetRef, String targetCommit) {
        public Repository { prefixes=List.copyOf(prefixes); }
    }
}

