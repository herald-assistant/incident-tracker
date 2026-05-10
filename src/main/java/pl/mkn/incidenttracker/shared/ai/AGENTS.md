# AGENTS

## Zakres

Ten katalog trzyma neutralne kontrakty AI uzywane przez kilka warstw albo
feature'ow.

Obecnie obejmuje:

- `AnalysisAiOptions` jako preferencje wykonania AI z requestow/operatora,
- `AnalysisAiUsage` jako generyczny usage/token/cost contract dla UI,
  runtime i feature'ow,
- `AnalysisAiAuthRef` i `AnalysisAiAuthRefResolver` jako non-secret kontrakt
  przekazywania odniesienia do auth bez tokenow w publicznych payloadach,
- `AnalysisAiActivityEvent` i `AnalysisAiActivityListener` jako neutralny,
  user-visible trace aktywnosci AI bez typow Copilot SDK,
- `AnalysisAiToolFeedback` oraz mapper `AnalysisAiToolFeedbackEvidenceMapper`
  jako user-visible kontrakt feedbacku tooli, transportowany przez neutralne
  `AnalysisEvidenceSection`.

## Zasady

- Nie importuj tutaj `analysis.*`, `api.*`, `features.*`, `aiplatform.*`,
  `agenttools.*` ani `integrations.*`.
- Trzymaj tu tylko neutralne dane, ktore nie zakladaja Copilota,
  `correlationId`, branchy, srodowiska ani konkretnego feature'a.
- Endpointy HTTP, DTO odpowiedzi API i mapowanie z platformowego katalogu
  modeli trzymaj w `api.*`, nie tutaj.
