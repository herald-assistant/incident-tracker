# AGENTS

## Zakres

Ten katalog zawiera neutralna platforme uruchamiania AI, niezalezna od
konkretnego feature'a analitycznego.

Obecnie obejmuje:

- `copilot/runtime/`
  techniczny runtime Copilot SDK: properties, model listing, skill runtime
  loading, `CopilotRunRequest`, `CopilotPreparedSession`,
  `CopilotSessionConfigRequest`, rendered artifacts oraz factory budujace
  `SessionConfig` i `MessageOptions`.

Nie obejmuje:

- incident promptu, digestu, coverage heurystyk ani response contract,
- job flow, follow-up API ani UI,
- evidence pipeline konkretnego feature'a,
- implementacji tools ani adapterow integracyjnych.

## Zasady

- `aiplatform.*` nie moze importowac `analysis.*`, `features.*` ani
  `integrations.*`.
- Platforma moze zalezec od malych neutralnych kontraktow `shared.*`,
  `common.*` oraz bibliotek SDK/technicznych.
- Feature ma dostarczac prompt, skill resources, available tools, hidden
  context, evidence sink i response handling jako parametry uruchomienia.
- Platforma nie moze zakladac `correlationId`, GitLaba, Database ani
  semantyki incident analysis jako stalego wymogu runtime.
- Jesli przenosisz kolejne klasy z `analysis.ai.copilot`, najpierw upewnij sie,
  ze nie wnosza incident-specific policy, coverage, promptu albo evidence
  mappingu.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `aiplatform.*` nie importowalo
  warstw aplikacyjnych ani feature'ow.
