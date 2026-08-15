package pl.mkn.tdw.common;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "platform.source-code")
public class PlatformSourceCodeProperties {

    @NotBlank
    private String defaultBranch;
}
