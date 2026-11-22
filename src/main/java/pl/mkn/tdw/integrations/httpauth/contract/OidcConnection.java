package pl.mkn.tdw.integrations.httpauth.contract;

import java.time.Instant;

public record OidcConnection(boolean connected, String username, Instant expiresAt, String message) { }
