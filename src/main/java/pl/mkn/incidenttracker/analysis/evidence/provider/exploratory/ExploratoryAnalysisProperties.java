package pl.mkn.incidenttracker.analysis.evidence.provider.exploratory;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.exploratory")
public class ExploratoryAnalysisProperties {

    private boolean enabled;
    private int maxComponentCandidates = 5;
    private int maxRepositories = 4;
    private int maxSearchTermsPerComponent = 6;
    private int maxFileCandidatesPerRepository = 8;
    private int maxConfigReadsPerRepository = 2;
    private int maxCodeChunkReadsPerRepository = 4;
    private int maxFlowNodes = 12;
    private int maxFlowEdges = 16;
    private int minHypothesisSupportScore = 2;

}
