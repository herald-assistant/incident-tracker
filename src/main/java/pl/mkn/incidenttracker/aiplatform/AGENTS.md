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
- `copilot/tools/context/`
  platformowa mechanika budowania hidden `ToolContext` oraz neutralny
  `CopilotToolSessionContext` przekazywany przez feature.
- `copilot/tools/CopilotToolInvocationHandler`
  neutralna granica wykonania Spring `ToolCallback`: policies, hidden context,
  eventy invocation, kontrolowany rejection i parsing wyniku dla SDK.
- `copilot/tools/events/`
  platformowe eventy invocation `Started`/`Finished`, outcome oraz publisher
  chroniacy runtime przed wyjatkami listenerow.
- `copilot/tools/policy/`
  neutralne kontrakty policy invocation, kontrolowany rejection oraz session
  validation.
- `copilot/tools/logging/`
  operacyjny listener logujacy request/result preview invocation.
- `copilot/tools/description/`
  neutralny kontrakt customizacji opisow tools; konkretne guidance dostarcza
  feature.
- `copilot/tools/evidence/`
  session-bound store publikujacy neutralne `AnalysisEvidenceSection` z wynikow
  tool invocation przez sink przekazany przez feature.

Nie obejmuje:

- incident promptu, digestu, coverage heurystyk ani response contract,
- job flow, follow-up API ani UI,
- evidence pipeline konkretnego feature'a,
- implementacji capability tools ani adapterow integracyjnych.

## Zasady

- `aiplatform.*` nie moze importowac `analysis.*`, `features.*` ani
  `integrations.*`.
- Platforma moze zalezec od malych neutralnych kontraktow `shared.*`,
  `common.*`, neutralnych keys/nazw z `agenttools.*` oraz bibliotek
  SDK/technicznych.
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
