package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabRestClientFactoryNamedConnectionTest {

    @Test
    void shouldApplySharedSslPolicyToDefaultAndNamedConnections() {
        var rootBuilder = mock(RestClient.Builder.class);
        var defaultBuilder = mock(RestClient.Builder.class);
        var namedBuilder = mock(RestClient.Builder.class);
        when(rootBuilder.clone()).thenReturn(defaultBuilder, namedBuilder);
        stub(defaultBuilder);
        stub(namedBuilder);

        var properties = new GitLabProperties();
        properties.setToken("default-token");
        var factory = new GitLabRestClientFactory(
                properties,
                IntegrationRestClientBuilderFactoryTestCreator.create(rootBuilder, true)
        );

        factory.create();
        factory.create(new GitLabConnectionDetails(
                "internal",
                "https://internal.example.com",
                "internal-token"
        ));

        verify(defaultBuilder).requestFactory(any(ClientHttpRequestFactory.class));
        verify(namedBuilder).requestFactory(any(ClientHttpRequestFactory.class));
        verify(rootBuilder, never()).requestFactory(any(ClientHttpRequestFactory.class));
    }

    private static void stub(RestClient.Builder builder) {
        when(builder.defaultHeader(anyString(), any(String[].class))).thenReturn(builder);
        when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
        when(builder.build()).thenReturn(mock(RestClient.class));
    }
}
