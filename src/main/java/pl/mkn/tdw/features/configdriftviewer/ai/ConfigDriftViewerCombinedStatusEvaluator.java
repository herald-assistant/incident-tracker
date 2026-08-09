package pl.mkn.tdw.features.configdriftviewer.ai;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConclusion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiExecutionStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiSecondOpinion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;

@Component
public class ConfigDriftViewerCombinedStatusEvaluator {

    public ConfigDriftViewerStatus evaluate(
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerAiSecondOpinion opinion
    ) {
        if (deterministic == null || deterministic.status() == ConfigDriftViewerDeterministicStatus.INCOMPLETE) {
            return ConfigDriftViewerStatus.INCOMPLETE;
        }
        if (opinion == null || opinion.executionStatus() != ConfigDriftViewerAiExecutionStatus.COMPLETED) {
            return ConfigDriftViewerStatus.INCOMPLETE;
        }
        if (opinion != null && opinion.conclusion() == ConfigDriftViewerAiConclusion.LIKELY_CONFIGURATION_ERROR) {
            return ConfigDriftViewerStatus.LIKELY_CONFIGURATION_ERROR;
        }
        if (deterministic.status() == ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
                || opinion != null && opinion.conclusion() == ConfigDriftViewerAiConclusion.REVIEW_REQUIRED) {
            return ConfigDriftViewerStatus.REVIEW_REQUIRED;
        }
        return ConfigDriftViewerStatus.NO_BLOCKING_ANOMALIES;
    }
}
