package pl.mkn.tdw.testsupport.integrations;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.*;

/** Fikcyjny self-signed localhost; nie modyfikuje globalnego truststore ani TLS. */
public final class LocalHttpsServer implements AutoCloseable {
    public final HttpsServer server;
    private final SSLContext trustedContext;
    private final ExecutorService workers = Executors.newCachedThreadPool();

    public LocalHttpsServer() throws Exception {
        var password = "test-only".toCharArray();
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = LocalHttpsServer.class.getResourceAsStream("/tls/localhost.p12")) { keys.load(input, password); }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); managers.init(keys, password);
        var ssl = SSLContext.getInstance("TLS"); ssl.init(managers.getKeyManagers(), null, null);
        var trust = KeyStore.getInstance("PKCS12"); trust.load(null, null);
        trust.setCertificateEntry("localhost", keys.getCertificate("localhost"));
        var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trustManagers.init(trust);
        trustedContext = SSLContext.getInstance("TLS"); trustedContext.init(null, trustManagers.getTrustManagers(), null);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl)); server.setExecutor(workers);
    }

    public String url() { return "https://localhost:" + server.getAddress().getPort(); }
    public String wrongHostUrl() { return "https://127.0.0.1:" + server.getAddress().getPort(); }
    public SSLContext trustedContext() { return trustedContext; }
    @Override public void close() { server.stop(0); workers.shutdownNow(); }
}
