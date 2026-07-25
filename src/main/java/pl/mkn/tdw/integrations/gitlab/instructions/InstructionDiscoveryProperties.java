package pl.mkn.tdw.integrations.gitlab.instructions;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.instructions")
public class InstructionDiscoveryProperties {

    private int maxInstructionFiles = 40;
    private int maxReferencedFiles = 20;
    private int maxFileCharacters = 12_000;
}
