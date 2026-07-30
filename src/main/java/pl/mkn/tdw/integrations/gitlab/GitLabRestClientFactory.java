package pl.mkn.tdw.integrations.gitlab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
public class GitLabRestClientFactory {

    private static final TrustManager[] TRUST_ALL_MANAGERS = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };

    private final GitLabProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final AtomicBoolean insecureSslWarningLogged = new AtomicBoolean(false);

    public RestClient create() {
        return create(new GitLabConnectionDetails(
                "analysis.gitlab",
                properties.getBaseUrl(),
                properties.getToken(),
                properties.isIgnoreSslErrors()
        ));
    }

    public RestClient create(GitLabConnectionDetails connection) {
        var builder = restClientBuilder.clone()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(connection.token())) {
            builder.defaultHeader("PRIVATE-TOKEN", connection.token());
        }

        if (connection.ignoreSslErrors()) {
            if (insecureSslWarningLogged.compareAndSet(false, true)) {
                log.warn(
                        "GitLab REST client connection {} is configured to ignore SSL certificate and hostname validation errors. Use only in trusted internal environments.",
                        connection.id()
                );
            }
            builder.requestFactory(insecureRequestFactory());
        }

        return builder.build();
    }

    private ClientHttpRequestFactory insecureRequestFactory() {
        var sslContext = insecureSslContext();

        return new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);

                if (connection instanceof HttpsURLConnection httpsConnection) {
                    httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                    httpsConnection.setHostnameVerifier((hostname, session) -> true);
                }
            }
        };
    }

    private SSLContext insecureSslContext() {
        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, TRUST_ALL_MANAGERS, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create insecure SSL context for GitLab REST client.", exception);
        }
    }

}
