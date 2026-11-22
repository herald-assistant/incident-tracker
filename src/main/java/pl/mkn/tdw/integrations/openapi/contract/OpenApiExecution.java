package pl.mkn.tdw.integrations.openapi.contract;

import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;

/** Captured execution binding, kept outside tool descriptions and input schemas. */
public record OpenApiExecution(OpenApiOperation operation, HttpAuthProfile authentication) { }
