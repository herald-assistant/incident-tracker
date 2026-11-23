package pl.mkn.tdw.integrations.support.http;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
public class IntegrationRestClientBuilderFactory {
    private final IntegrationHttpProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final AtomicBoolean insecureSslWarningLogged = new AtomicBoolean(false);

    public RestClient.Builder newBuilder() {
        var builder = restClientBuilder.clone();
        if (properties.isIgnoreSslErrors()) {
            warnInsecureTls();
            builder.requestFactory(InsecureRequestFactoryHolder.INSTANCE);
        }
        return builder;
    }

    /** Bounded JDK transport for API/auth; redirects disabled, JDK's built-in GET retries remain. */
    public ClientHttpRequestFactory newRequestFactory(Duration connectTimeout, Duration readTimeout) {
        try {
            return newRequestFactory(connectTimeout, readTimeout,
                    properties.isIgnoreSslErrors() ? null : SSLContext.getDefault());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to obtain default SSL context.", ex);
        }
    }

    // Tests supply an isolated truststore without changing JVM defaults.
    ClientHttpRequestFactory newRequestFactory(Duration connectTimeout, Duration readTimeout, SSLContext verifiedContext) {
        var context = verifiedContext;
        if (properties.isIgnoreSslErrors()) {
            warnInsecureTls();
            context = InsecureRequestFactoryHolder.CONTEXT;
        }
        return jdkRequestFactory(connectTimeout, readTimeout, context);
    }

    private static ClientHttpRequestFactory jdkRequestFactory(Duration connectTimeout, Duration readTimeout, SSLContext context) {
        var client = HttpClient.newBuilder().sslContext(context).followRedirects(HttpClient.Redirect.NEVER);
        if (connectTimeout != null) client.connectTimeout(connectTimeout);
        var factory = new JdkClientHttpRequestFactory(client.build());
        if (readTimeout != null) factory.setReadTimeout(readTimeout);
        return factory;
    }

    private void warnInsecureTls() {
        if (insecureSslWarningLogged.compareAndSet(false, true))
            log.warn("Integration REST clients are configured to ignore SSL certificate and hostname validation errors. Use only in trusted internal environments.");
    }

    private static final class InsecureRequestFactoryHolder {
        private static final SSLContext CONTEXT = insecureSslContext();
        private static final ClientHttpRequestFactory INSTANCE = jdkRequestFactory(null, null, CONTEXT);
    }

    private static SSLContext insecureSslContext() {
        try {
            var context = SSLContext.getInstance("TLS");
            // Extended callbacks keep both certificate and endpoint checks local to this explicit policy.
            context.init(null, new TrustManager[]{new X509ExtendedTrustManager() {
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) { }
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
            }}, null);
            return context;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to create insecure SSL context for integration REST clients.", ex);
        }
    }
}
