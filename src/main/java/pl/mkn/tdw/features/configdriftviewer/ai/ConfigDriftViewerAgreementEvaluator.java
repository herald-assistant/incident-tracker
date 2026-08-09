package pl.mkn.tdw.features.configdriftviewer.ai;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAgreement;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAgreementStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConclusion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiExecutionStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiSecondOpinion;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class ConfigDriftViewerAgreementEvaluator {

    public ConfigDriftViewerAgreement evaluate(
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerAiSecondOpinion opinion
    ) {
        if (deterministic == null || opinion == null
                || opinion.executionStatus() != ConfigDriftViewerAiExecutionStatus.COMPLETED
                || opinion.conclusion() == ConfigDriftViewerAiConclusion.INCONCLUSIVE) {
            return new ConfigDriftViewerAgreement(
                    ConfigDriftViewerAgreementStatus.NOT_ASSESSED,
                    "AI nie dostarczyło kompletnej oceny porównawczej.",
                    List.of(),
                    List.of()
            );
        }

        var allFindingIds = deterministic.findings().stream()
                .map(finding -> finding.findingId())
                .toList();
        var referencedFindingIds = new LinkedHashSet<String>();
        opinion.observations().forEach(observation -> referencedFindingIds.addAll(observation.findingIds()));
        var deterministicConcern = deterministic.status() != ConfigDriftViewerDeterministicStatus.NO_BLOCKING_ANOMALIES
                || !allFindingIds.isEmpty();
        var aiConcern = opinion.conclusion() == ConfigDriftViewerAiConclusion.REVIEW_REQUIRED
                || opinion.conclusion() == ConfigDriftViewerAiConclusion.LIKELY_CONFIGURATION_ERROR;

        if (deterministicConcern != aiConcern) {
            return new ConfigDriftViewerAgreement(
                    ConfigDriftViewerAgreementStatus.DISAGREEMENT,
                    "AI i wynik deterministyczny różnią się w ocenie potrzeby interwencji.",
                    List.of(),
                    allFindingIds
            );
        }
        if (deterministic.status() == ConfigDriftViewerDeterministicStatus.INCOMPLETE) {
            return new ConfigDriftViewerAgreement(
                    ConfigDriftViewerAgreementStatus.PARTIAL_AGREEMENT,
                    "AI rozpoznaje ryzyko, ale niekompletnego wyniku deterministycznego nie można domknąć interpretacją.",
                    List.copyOf(referencedFindingIds),
                    List.of()
            );
        }
        if (!allFindingIds.isEmpty() && !referencedFindingIds.containsAll(allFindingIds)) {
            return new ConfigDriftViewerAgreement(
                    ConfigDriftViewerAgreementStatus.PARTIAL_AGREEMENT,
                    "AI zgadza się z kierunkiem oceny, ale nie odniosło się do wszystkich findingów.",
                    List.copyOf(referencedFindingIds),
                    allFindingIds.stream().filter(id -> !referencedFindingIds.contains(id)).toList()
            );
        }
        return new ConfigDriftViewerAgreement(
                ConfigDriftViewerAgreementStatus.AGREEMENT,
                "AI i wynik deterministyczny są zgodne w ocenie potrzeby interwencji.",
                allFindingIds,
                List.of()
        );
    }
}
