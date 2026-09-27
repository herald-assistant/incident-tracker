package pl.mkn.tdw.integrations.support.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.confluence.config.ConfluenceProperties;
import pl.mkn.tdw.integrations.confluence.config.ConfluenceRestClientFactory;
import pl.mkn.tdw.integrations.dynatrace.config.DynatraceProperties;
import pl.mkn.tdw.integrations.dynatrace.config.DynatraceRestClientFactory;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.integrations.jira.config.JiraProperties;
import pl.mkn.tdw.integrations.jira.config.JiraRestClientFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationRestClientPolicyConsumersTest {

    @Test
    void shouldUseTheSharedTlsPolicyForAllFourIntegrations() {
        var rootBuilder = mock(RestClient.Builder.class);
        var clonedBuilder = mock(RestClient.Builder.class);
        when(rootBuilder.clone()).thenReturn(clonedBuilder);
        when(clonedBuilder.defaultHeader(anyString(), any(String[].class))).thenReturn(clonedBuilder);
        when(clonedBuilder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(clonedBuilder);
        when(clonedBuilder.build()).thenReturn(mock(RestClient.class));

        var properties = new IntegrationHttpProperties();
        properties.setIgnoreSslErrors(true);
        var sharedBuilderFactory = new IntegrationRestClientBuilderFactory(properties, rootBuilder);

        new GitLabRestClientFactory(new GitLabProperties(), sharedBuilderFactory).create();
        new JiraRestClientFactory(new JiraProperties(), sharedBuilderFactory).create();
        new ConfluenceRestClientFactory(new ConfluenceProperties(), sharedBuilderFactory).create();
        new DynatraceRestClientFactory(sharedBuilderFactory).create(new DynatraceProperties());

        verify(rootBuilder, times(4)).clone();
        verify(clonedBuilder, times(4)).requestFactory(any(ClientHttpRequestFactory.class));
        verify(rootBuilder, never()).requestFactory(any(ClientHttpRequestFactory.class));
    }
}
