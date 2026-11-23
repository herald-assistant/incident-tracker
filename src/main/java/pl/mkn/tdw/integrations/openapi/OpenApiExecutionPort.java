package pl.mkn.tdw.integrations.openapi;

import pl.mkn.tdw.integrations.openapi.contract.OpenApiArguments;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiExecution;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiResult;

public interface OpenApiExecutionPort {
    OpenApiResult execute(OpenApiExecution execution, OpenApiArguments arguments);
}
