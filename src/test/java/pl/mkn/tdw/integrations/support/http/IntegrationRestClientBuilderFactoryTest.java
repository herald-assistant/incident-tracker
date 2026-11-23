package pl.mkn.tdw.integrations.support.http;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationRestClientBuilderFactoryTest {

    @Test
    void shouldKeepDefaultTlsVerificationOnClonedBuilder() {
        var properties = new IntegrationHttpProperties();
        var rootBuilder = mock(RestClient.Builder.class);
        var clonedBuilder = mock(RestClient.Builder.class);
        when(rootBuilder.clone()).thenReturn(clonedBuilder);

        var result = new IntegrationRestClientBuilderFactory(properties, rootBuilder).newBuilder();

        assertFalse(properties.isIgnoreSslErrors());
        assertSame(clonedBuilder, result);
        verify(clonedBuilder, never()).requestFactory(any(ClientHttpRequestFactory.class));
        verify(rootBuilder, never()).requestFactory(any(ClientHttpRequestFactory.class));
    }

    @Test
    void shouldApplyLocalJdkTlsPolicyOnlyToTheClone() {
        var properties = new IntegrationHttpProperties();
        properties.setIgnoreSslErrors(true);
        var rootBuilder = mock(RestClient.Builder.class);
        var clonedBuilder = mock(RestClient.Builder.class);
        when(rootBuilder.clone()).thenReturn(clonedBuilder);

        new IntegrationRestClientBuilderFactory(properties, rootBuilder).newBuilder();

        var requestFactoryCaptor = ArgumentCaptor.forClass(ClientHttpRequestFactory.class);
        verify(clonedBuilder).requestFactory(requestFactoryCaptor.capture());
        verify(rootBuilder, never()).requestFactory(any(ClientHttpRequestFactory.class));

        assertInstanceOf(JdkClientHttpRequestFactory.class, requestFactoryCaptor.getValue());
    }
}
