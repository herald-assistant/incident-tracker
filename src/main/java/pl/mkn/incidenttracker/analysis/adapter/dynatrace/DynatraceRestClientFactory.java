package pl.mkn.incidenttracker.analysis.adapter.dynatrace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

@Component
@Slf4j
public class DynatraceRestClientFactory {

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

    private final RestClient.Builder restClientBuilder;
    private final ClientHttpRequestFactory requestFactory;

    @Autowired
    public DynatraceRestClientFactory(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, insecureRequestFactory());
        log.warn("Dynatrace REST client is configured to ignore SSL certificate and hostname validation errors. Use only in trusted internal environments.");
    }

    DynatraceRestClientFactory(RestClient.Builder restClientBuilder, ClientHttpRequestFactory requestFactory) {
        this.restClientBuilder = restClientBuilder;
        this.requestFactory = requestFactory;
    }

    static DynatraceRestClientFactory forMockServer(RestClient.Builder restClientBuilder) {
        return new DynatraceRestClientFactory(restClientBuilder, null);
    }

    public RestClient create(DynatraceProperties properties) {
        var builder = restClientBuilder.clone()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(properties.getApiToken())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Api-Token " + properties.getApiToken().trim());
        }

        if (requestFactory != null) {
            builder.requestFactory(requestFactory);
        }

        return builder.build();
    }

    private static ClientHttpRequestFactory insecureRequestFactory() {
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

    private static SSLContext insecureSslContext() {
        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, TRUST_ALL_MANAGERS, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create insecure SSL context for Dynatrace REST client.", exception);
        }
    }

}
