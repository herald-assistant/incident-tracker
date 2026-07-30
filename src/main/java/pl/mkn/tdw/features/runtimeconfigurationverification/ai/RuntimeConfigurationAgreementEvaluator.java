package pl.mkn.tdw.features.runtimeconfigurationverification.ai;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAgreement;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAgreementStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConclusion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiExecutionStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class RuntimeConfigurationAgreementEvaluator {

    public RuntimeConfigurationAgreement evaluate(
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationAiSecondOpinion opinion
    ) {
        if (deterministic == null || opinion == null
                || opinion.executionStatus() != RuntimeConfigurationAiExecutionStatus.COMPLETED
                || opinion.conclusion() == RuntimeConfigurationAiConclusion.INCONCLUSIVE) {
            return new RuntimeConfigurationAgreement(
                    RuntimeConfigurationAgreementStatus.NOT_ASSESSED,
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
        var deterministicConcern = deterministic.status() != RuntimeConfigurationDeterministicStatus.NO_BLOCKING_ANOMALIES
                || !allFindingIds.isEmpty();
        var aiConcern = opinion.conclusion() == RuntimeConfigurationAiConclusion.REVIEW_REQUIRED
                || opinion.conclusion() == RuntimeConfigurationAiConclusion.LIKELY_CONFIGURATION_ERROR;

        if (deterministicConcern != aiConcern) {
            return new RuntimeConfigurationAgreement(
                    RuntimeConfigurationAgreementStatus.DISAGREEMENT,
                    "AI i wynik deterministyczny różnią się w ocenie potrzeby interwencji.",
                    List.of(),
                    allFindingIds
            );
        }
        if (deterministic.status() == RuntimeConfigurationDeterministicStatus.INCOMPLETE) {
            return new RuntimeConfigurationAgreement(
                    RuntimeConfigurationAgreementStatus.PARTIAL_AGREEMENT,
                    "AI rozpoznaje ryzyko, ale niekompletnego wyniku deterministycznego nie można domknąć interpretacją.",
                    List.copyOf(referencedFindingIds),
                    List.of()
            );
        }
        if (!allFindingIds.isEmpty() && !referencedFindingIds.containsAll(allFindingIds)) {
            return new RuntimeConfigurationAgreement(
                    RuntimeConfigurationAgreementStatus.PARTIAL_AGREEMENT,
                    "AI zgadza się z kierunkiem oceny, ale nie odniosło się do wszystkich findingów.",
                    List.copyOf(referencedFindingIds),
                    allFindingIds.stream().filter(id -> !referencedFindingIds.contains(id)).toList()
            );
        }
        return new RuntimeConfigurationAgreement(
                RuntimeConfigurationAgreementStatus.AGREEMENT,
                "AI i wynik deterministyczny są zgodne w ocenie potrzeby interwencji.",
                allFindingIds,
                List.of()
        );
    }
}
