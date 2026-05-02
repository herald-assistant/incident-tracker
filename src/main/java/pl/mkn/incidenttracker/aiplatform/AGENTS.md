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
- `copilot/runtime/options/`
  platformowy katalog modeli Copilota: provider, neutralne DTO i cache/fallback
  dla `CopilotClient.listModels()`.
- `copilot/runtime/execution/`
  platformowe uruchamianie `CopilotPreparedSession`: lifecycle klienta/sesji,
  event logging, controlled invocation exception oraz `CopilotExecutionResult`
  z trescia odpowiedzi i user-visible `AnalysisAiUsage`.
- `copilot/runtime/quality/`
  neutralny payload raportu jakosci odpowiedzi. Reguly oceny
  odpowiedzi konkretnego feature'a nie mieszkaja w platformie.
- `copilot/tools/context/`
  platformowa mechanika budowania hidden `ToolContext` oraz neutralny
  `CopilotToolSessionContext` przekazywany przez feature.
- `copilot/tools/CopilotSdkToolFactory`
  platformowa rejestracja Spring `ToolCallback` jako Copilot `ToolDefinition`.
- `copilot/tools/CopilotToolInvocationHandler`
  neutralna granica wykonania Spring `ToolCallback`: policies, hidden context,
  eventy invocation, kontrolowany rejection i parsing wyniku dla SDK.
- `copilot/tools/events/`
  platformowe eventy invocation `Started`/`Finished`, outcome oraz publisher
  chroniacy runtime przed wyjatkami listenerow.
- `copilot/tools/policy/`
  neutralne kontrakty policy invocation, kontrolowany rejection oraz session
  validation.
- `copilot/tools/policy/budget/`
  platformowa budget policy, state, registry, properties oraz neutralny
  kontrakt decyzji.
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
- Platforma nie utrzymuje obecnie niewidocznej dla uzytkownika telemetryki
  sesji. Zdarzenia SDK usage sa agregowane tylko do `AnalysisAiUsage`, ktore
  trafia do job state/UI. Nowa telemetryka moze wrocic dopiero jako jawny,
  productized element z widocznym celem, testami i dokumentacja.
- Platforma nie moze zakladac `correlationId`, GitLaba, Database ani
  semantyki incident analysis jako stalego wymogu runtime.
- Jesli kiedys wydzielasz kolejny runtime element z dawnego obszaru Copilota,
  najpierw upewnij sie, ze nie wnosi incident-specific policy, coverage,
  promptu albo evidence mappingu.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `aiplatform.*` nie importowalo
  warstw aplikacyjnych ani feature'ow.
