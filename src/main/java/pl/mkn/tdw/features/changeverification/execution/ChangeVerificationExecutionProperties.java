package pl.mkn.tdw.features.changeverification.execution;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "features.change-verification.execution")
public class ChangeVerificationExecutionProperties {

    private int responseBodyExcerptCharacters = 2000;
    private List<String> cleanupEndpointAllowlist = new ArrayList<>();
}
