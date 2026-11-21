package pl.mkn.tdw.integrations.openapi.service;

import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class OpenApiTarget {
    private OpenApiTarget() {}
    public static URI uri(String value) {
        try {
            if (value == null) throw new IllegalArgumentException();
            var uri = URI.create(value);
            if (uri.getScheme() == null || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT)) || uri.getHost() == null ||
                    uri.getHost().isBlank() || uri.getPort() > 65535 ||
                    uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw new IllegalArgumentException();
            return URI.create(uri.getScheme().toLowerCase(Locale.ROOT) + ":" + uri.getRawSchemeSpecificPart());
        } catch (IllegalArgumentException ex) {
            throw new OpenApiException("Adres API musi być bezwzględnym URL HTTP(S) z poprawnym hostem i portem, bez credentials/query/hash.");
        }
    }
}
