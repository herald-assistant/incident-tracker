# AGENTS

## Zakres

Ten katalog trzyma male, neutralne kontrakty uzywane przez kilka warstw
aplikacji.

Obecnie obejmuje:

- `ai/`
  generyczny kontrakt token/cost/usage dla flow, job UI, telemetryki i
  feature'ow: `AnalysisAiUsage`.
- `evidence/`
  generyczny model evidence wspolny dla evidence pipeline, flow, job UI i AI:
  `AnalysisEvidenceSection`, `AnalysisEvidenceItem`,
  `AnalysisEvidenceAttribute`; zawiera tez neutralny
  `AnalysisAiToolEvidenceListener` dla aktualizacji tool evidence.

## Zasady modyfikacji

- `shared` nie moze importowac warstw aplikacyjnych: `analysis.*`,
  `agenttools.*`, `api.*`, przyszlych `integrations.*`, `aiplatform.*` ani
  `features.*`.
- Dodawaj tu tylko stabilne kontrakty wspolne dla kilku warstw albo feature'ow.
  Nie przenos tu klas tylko po to, zeby ukryc zla zaleznosc.
- Modele w `shared.evidence` maja pozostac neutralne wzgledem konkretnego
  providera, adaptera, promptu i runtime AI.
- Jesli potrzebujesz typowanego widoku konkretnej capability, trzymaj go blisko
  wlasciciela capability lub feature'a, a nie w `shared`.
