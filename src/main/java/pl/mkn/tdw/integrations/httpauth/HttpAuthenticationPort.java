package pl.mkn.tdw.integrations.httpauth;

import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;

public interface HttpAuthenticationPort {
    /** null profile means No Auth and returns null. */
    String authorization(HttpAuthProfile profile);
}
