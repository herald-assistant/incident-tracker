package pl.mkn.tdw.integrations.httpauth.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class HttpAuthConfiguration {
    @Bean("httpAuthClock") Clock httpAuthClock() { return Clock.systemUTC(); }
}
