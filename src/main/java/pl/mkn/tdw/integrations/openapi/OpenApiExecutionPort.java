package pl.mkn.tdw.integrations.openapi;

import pl.mkn.tdw.integrations.openapi.contract.OpenApiArguments;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiResult;

public interface OpenApiExecutionPort {
    OpenApiResult execute(OpenApiOperation operation, OpenApiArguments arguments, pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile authentication);
}
