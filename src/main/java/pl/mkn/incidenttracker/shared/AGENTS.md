# AGENTS

## Zakres

Ten katalog trzyma male, neutralne kontrakty uzywane przez kilka warstw
aplikacji.

Obecnie obejmuje:

- `ai/`
  neutralne preferencje wykonania AI oraz generyczny kontrakt token/cost/usage
  dla flow, job UI i feature'ow: `AnalysisAiOptions`, `AnalysisAiUsage`;
  non-secret referencje auth (`AnalysisAiAuthRef`,
  `AnalysisAiAuthRefResolver`) oraz user-visible activity trace
  (`AnalysisAiActivityEvent`, `AnalysisAiActivityListener`).
- `evidence/`
  generyczny model evidence wspolny dla evidence pipeline, flow, job UI i AI:
  `AnalysisEvidenceSection`, `AnalysisEvidenceItem`,
  `AnalysisEvidenceAttribute`; zawiera tez neutralny
  `AnalysisAiToolEvidenceListener` dla aktualizacji tool evidence.
- `error/`
  neutralny kontrakt user-facing bledow aplikacyjnych dla feature'ow i
  globalnego HTTP exception handlera, bez zaleznosci od `api.*`.

## Zasady modyfikacji

- `shared` nie moze importowac warstw aplikacyjnych: `analysis.*`,
  `agenttools.*`, `api.*`, `integrations.*`, `aiplatform.*` ani `features.*`.
- Dodawaj tu tylko stabilne kontrakty wspolne dla kilku warstw albo feature'ow.
  Nie przenos tu klas tylko po to, zeby ukryc zla zaleznosc.
- Modele w `shared.evidence` maja pozostac neutralne wzgledem konkretnego
  providera, adaptera, promptu i runtime AI.
- Jesli potrzebujesz typowanego widoku konkretnej capability, trzymaj go blisko
  wlasciciela capability lub feature'a, a nie w `shared`.
