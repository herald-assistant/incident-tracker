package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.openapi.GitLabOpenApiEndpointSliceRequest;
import pl.mkn.tdw.integrations.gitlab.contract.openapi.GitLabOpenApiEndpointSliceResponse;

public interface GitLabOpenApiEndpointSlicePort {

     String STATUS_OK = "OK";
     String STATUS_UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";
     String STATUS_PARSE_ERROR = "PARSE_ERROR";
     String STATUS_NOT_OPENAPI = "NOT_OPENAPI";
     String STATUS_UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION";
     String STATUS_ENDPOINT_NOT_FOUND = "ENDPOINT_NOT_FOUND";
     String STATUS_AMBIGUOUS_OPERATION = "AMBIGUOUS_OPERATION";
     int MIN_OUTPUT_CHARACTERS = 1_000;
     int MAX_OUTPUT_CHARACTERS = 50_000;
     int MAX_SCHEMA_DEPTH = 4;
    GitLabOpenApiEndpointSliceResponse readEndpointSlice(GitLabOpenApiEndpointSliceRequest request);
}
