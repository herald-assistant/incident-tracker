package pl.mkn.tdw.integrations.gitlab.contract;
import java.util.List;
public record GitLabRefPage(List<GitLabRefOption> refs, Integer nextPage) {
    public GitLabRefPage { refs = List.copyOf(refs); }
}

