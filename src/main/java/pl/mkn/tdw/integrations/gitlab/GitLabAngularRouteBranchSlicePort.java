package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabAngularRouteBranchSliceRequest;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabAngularRouteBranchSliceResponse;

public interface GitLabAngularRouteBranchSlicePort {

     int DEFAULT_OUTPUT_CHARACTERS = 24_000;
     int MAX_OUTPUT_CHARACTERS = 80_000;
    GitLabAngularRouteBranchSliceResponse readBranchSlice(GitLabAngularRouteBranchSliceRequest request);
}
