package pl.mkn.incidenttracker.analysis.ai.copilot.tools.budget;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.ai.copilot.tool-budget")
public class CopilotToolBudgetProperties {

    private boolean enabled = true;
    private BudgetMode mode = BudgetMode.SOFT;
    private int maxTotalCalls = 16;
    private int maxElasticCalls = 1;
    private int maxGitlabCalls = 8;
    private int maxGitlabSearchCalls = 3;
    private int maxGitlabReadFileCalls = 1;
    private int maxGitlabReadChunkCalls = 6;
    private long maxGitlabReturnedCharacters = 80_000L;
    private int maxDbCalls = 8;
    private int maxDbRawSqlCalls = 0;
    private long maxDbReturnedCharacters = 64_000L;

    public boolean active() {
        return enabled && mode != BudgetMode.OFF;
    }
}
