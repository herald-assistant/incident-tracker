package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.operational-context")
public class OperationalContextProperties {

    private boolean enabled;
    private String resourceRoot = "operational-context";
    private int maxItemsPerType = 2;
    private int maxGlossaryTerms = 3;
    private int maxHandoffRules = 2;

}
