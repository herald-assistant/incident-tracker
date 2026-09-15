package pl.mkn.tdw.features.changeverification;

import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationInterpretationType;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationReleaseImpact;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleEvidenceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleLedgerResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleOutcome;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleScope;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleSourceResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationRuleSourceType;

import java.util.List;

public final class ChangeVerificationTestFixtures {

    private ChangeVerificationTestFixtures() {
    }

    public static ChangeVerificationRuleResultResponse sourceRule(
            String id,
            ChangeVerificationRuleScope scope,
            ChangeVerificationRuleOutcome outcome
    ) {
        return new ChangeVerificationRuleResultResponse(
                id,
                scope,
                new ChangeVerificationRuleSourceResponse(
                        scope == ChangeVerificationRuleScope.INSTRUCTION
                                ? ChangeVerificationRuleSourceType.REPOSITORY_INSTRUCTION
                                : ChangeVerificationRuleSourceType.ACCEPTANCE_CRITERION,
                        scope == ChangeVerificationRuleScope.INSTRUCTION ? "AGENTS.md" : "CRM-123 AC",
                        scope == ChangeVerificationRuleScope.INSTRUCTION ? "AGENTS.md:12" : "CRM-123#ac-1",
                        scope == ChangeVerificationRuleScope.INSTRUCTION
                                ? "Kontrolery pozostaja cienkie."
                                : "Po zapisaniu klient jest widoczny na liscie."
                ),
                "Zmiana musi spelniac zdefiniowana regule CRM.",
                ChangeVerificationInterpretationType.EXPLICIT,
                outcome,
                outcome == ChangeVerificationRuleOutcome.SATISFIED
                        ? ChangeVerificationReleaseImpact.NONE
                        : ChangeVerificationReleaseImpact.REVIEW,
                outcome == ChangeVerificationRuleOutcome.SATISFIED
                        ? "Test potwierdza zachowanie."
                        : "Brakuje potwierdzenia zachowania.",
                outcome == ChangeVerificationRuleOutcome.NOT_VERIFIED
                        ? List.of()
                        : List.of(new ChangeVerificationRuleEvidenceResponse(
                                "Test scenariusza CRM",
                                "src/test/java/example/CustomerFlowTest.java"
                        )),
                outcome == ChangeVerificationRuleOutcome.NOT_VERIFIED
                        ? List.of("Brak testu integracyjnego.")
                        : List.of(),
                outcome == ChangeVerificationRuleOutcome.SATISFIED
                        ? null
                        : "Dodaj brakujacy test i zweryfikuj wynik.",
                null,
                null,
                List.of(),
                null
        );
    }

    public static ChangeVerificationRuleResultResponse additionalRule(String id) {
        return new ChangeVerificationRuleResultResponse(
                id,
                ChangeVerificationRuleScope.ADDITIONAL,
                new ChangeVerificationRuleSourceResponse(
                        ChangeVerificationRuleSourceType.AI_SUGGESTION,
                        "AI-suggested check",
                        "AI",
                        "Sprawdz idempotencje ponownego zapisu klienta."
                ),
                "Ponowny zapis klienta nie tworzy duplikatu.",
                ChangeVerificationInterpretationType.INFERRED,
                ChangeVerificationRuleOutcome.NOT_VERIFIED,
                ChangeVerificationReleaseImpact.REVIEW,
                "Brak testu idempotencji.",
                List.of(),
                List.of("Brak testu ponowienia."),
                "Dodaj test ponowienia.",
                "Zmiana dotyka operacji zapisu.",
                "Ponowienie moze utworzyc duplikat klienta.",
                List.of("Zmiana CustomerService.save"),
                "MEDIUM"
        );
    }

    public static ChangeVerificationRuleLedgerResponse ledger(
            List<ChangeVerificationRuleResultResponse> rules,
            List<ChangeVerificationRuleResultResponse> additionalChecks
    ) {
        return new ChangeVerificationRuleLedgerResponse(
                true,
                true,
                null,
                rules,
                additionalChecks,
                List.of()
        );
    }

    public static ChangeVerificationResultResponse result(ChangeVerificationRuleLedgerResponse ledger) {
        return new ChangeVerificationResultResponse(
                "COMPLETED",
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                "prompt",
                ledger,
                null
        );
    }
}
