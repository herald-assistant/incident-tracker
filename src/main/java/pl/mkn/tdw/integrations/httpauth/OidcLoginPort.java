package pl.mkn.tdw.integrations.httpauth;

import java.util.List;
import pl.mkn.tdw.integrations.httpauth.contract.*;

public interface OidcLoginPort {
    String begin(HttpAuthProfile profile, String browserBinding);
    void complete(List<HttpAuthProfile> currentProfiles, String state, String code, String error,
                  String browserBinding, String callbackUri);
    OidcConnection status(HttpAuthProfile profile);
    void disconnect(HttpAuthProfile profile);
    String authorization(HttpAuthProfile profile);
}
