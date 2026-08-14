package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacket;

@Component
public class DeliveryPromptPreparationService {

    public DeliveryPromptPreparation prepare(DeliveryEvidencePacket packet) {
        var prompt = """
                Wykonaj ocene Delivery Effectiveness Assessment dla jednej Delivery Unit.

                Najpierw zaladuj skill `delivery-effectiveness-assessment-evaluator` i zastosuj jego rubryke.
                Korzystaj wylacznie z inline artifacts dolaczonych do tej sesji. Nie masz narzedzi do dalszego
                wyszukiwania Jira, GitLab ani Confluence. Nie estymuj czasu pracy i nie wnioskuj o osobach.

                Zaktualizuj sekcje `ASSESSMENT` raportu przez `report_upsert_section`, opisujac ocene,
                kotwice uzyte dla kazdego wymiaru, evidence, confidence oraz visibility limits.
                Nastepnie zwroc finalnie jeden obiekt JSON:

                {
                  "classification": "DELIVERY|EXCLUDED|INSUFFICIENT_EVIDENCE",
                  "dimensions": {
                    "outcomeBreadth": 0,
                    "domainDecisionComplexity": 0,
                    "applicationFlowComplexity": 0,
                    "boundaryAndDataComplexity": 0,
                    "verificationStateSpace": 0,
                    "implementedCompatibilityScope": 0
                  },
                  "confidence": 0.0,
                  "evidenceSummary": [
                    "outcomeBreadth | delivery-effectiveness/issues.md#ISSUE-KEY | obserwowany fakt"
                  ],
                  "qualityFlags": [],
                  "visibilityLimits": []
                }

                Dla `DELIVERY` wszystkie wymiary sa wymagane i maja wartosci calkowite 0-4. Dla kazdego
                niezerowego wymiaru dodaj osobny wpis `evidenceSummary` zgodny z formatem ze skilla.
                Wynik 0 musi oznaczac obserwowalny brak istotnej zmiany, nigdy brak danych. Jezeli evidence
                nie pozwala odroznic kotwic dla materialnego wymiaru, zwroc `INSUFFICIENT_EVIDENCE` bez
                syntetycznych wymiarow. Nie zwracaj Delivered Story Points ani score100; backend wylicza je
                deterministycznie.
                """;
        return new DeliveryPromptPreparation(prompt, packet.artifacts());
    }
}
