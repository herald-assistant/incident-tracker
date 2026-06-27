package pl.mkn.tdw.features.flowexplorer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "features.flow-explorer")
public class FlowExplorerProperties {

    private String defaultBranch = "main";
}
