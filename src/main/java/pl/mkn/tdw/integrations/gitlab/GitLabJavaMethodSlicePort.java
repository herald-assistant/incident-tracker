package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabJavaMethodSliceRequest;
import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabJavaMethodSliceResponse;

public interface GitLabJavaMethodSlicePort {

     int DEFAULT_OUTPUT_CHARACTERS = 8_000;
     int MAX_OUTPUT_CHARACTERS = 40_000;
    GitLabJavaMethodSliceResponse readMethodSlice(GitLabJavaMethodSliceRequest request);
}
