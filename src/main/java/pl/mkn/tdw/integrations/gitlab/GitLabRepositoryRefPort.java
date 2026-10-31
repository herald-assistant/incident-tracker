package pl.mkn.tdw.integrations.gitlab;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRefPage;
public interface GitLabRepositoryRefPort {
    GitLabRefPage listRefs(String projectPath, GitLabRef.Kind kind, String search, int page);
}

