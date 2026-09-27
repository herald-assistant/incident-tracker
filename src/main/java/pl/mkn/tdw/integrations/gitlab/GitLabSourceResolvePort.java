package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabSourceResolveMatch;
import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabSourceResolveRequest;
import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabSourceResolveResponse;

public interface GitLabSourceResolvePort {

    GitLabSourceResolveResponse resolve(GitLabSourceResolveRequest request);

    GitLabSourceResolveResponse resolve(GitLabSourceResolveRequest request, GitLabSourceResolveSessionPort session);

    GitLabSourceResolveResponse resolvePreview(GitLabSourceResolveRequest request);

    GitLabSourceResolveResponse resolvePreview(GitLabSourceResolveRequest request, GitLabSourceResolveSessionPort session);

    GitLabSourceResolveMatch resolveMatch(GitLabSourceResolveRequest request);

    GitLabSourceResolveMatch resolveMatch(GitLabSourceResolveRequest request, GitLabSourceResolveSessionPort session);

    GitLabSourceResolveSessionPort openSession();
}
