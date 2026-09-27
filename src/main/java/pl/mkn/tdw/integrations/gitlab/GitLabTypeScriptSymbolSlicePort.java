package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabTypeScriptSymbolSliceRequest;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabTypeScriptSymbolSliceResponse;

public interface GitLabTypeScriptSymbolSlicePort {

     int DEFAULT_OUTPUT_CHARACTERS = 12_000;
     int MAX_OUTPUT_CHARACTERS = 200_000;
    GitLabTypeScriptSymbolSliceResponse readSymbolSlice(GitLabTypeScriptSymbolSliceRequest request);
}
