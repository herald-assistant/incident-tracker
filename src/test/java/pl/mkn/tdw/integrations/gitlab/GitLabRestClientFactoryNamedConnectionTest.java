package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabRestClientFactoryNamedConnectionTest {

    @Test
    void shouldApplySslPolicyPerNamedConnection() {
        var rootBuilder = mock(RestClient.Builder.class);
        var secureBuilder = mock(RestClient.Builder.class);
        var insecureBuilder = mock(RestClient.Builder.class);
        when(rootBuilder.clone()).thenReturn(secureBuilder, insecureBuilder);
        stub(secureBuilder);
        stub(insecureBuilder);

        var factory = new GitLabRestClientFactory(new GitLabProperties(), rootBuilder);

        factory.create(new GitLabConnectionDetails(
                "secure",
                "https://secure.example.com",
                "secure-token",
                false
        ));
        factory.create(new GitLabConnectionDetails(
                "internal",
                "https://internal.example.com",
                "internal-token",
                true
        ));

        verify(secureBuilder, never()).requestFactory(any(ClientHttpRequestFactory.class));
        verify(insecureBuilder).requestFactory(any(ClientHttpRequestFactory.class));
    }

    private static void stub(RestClient.Builder builder) {
        when(builder.defaultHeader(anyString(), any(String[].class))).thenReturn(builder);
        when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
        when(builder.build()).thenReturn(mock(RestClient.class));
    }
}
