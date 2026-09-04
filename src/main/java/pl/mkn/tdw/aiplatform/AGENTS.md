# AGENTS

## Zakres

Ten katalog zawiera neutralna platforme uruchamiania AI, niezalezna od
konkretnego feature'a analitycznego.

Obecnie obejmuje:

- `copilot/runtime/`
  techniczny runtime Copilot SDK: properties, model listing, skill runtime
  loading, `CopilotRunRequest`, `CopilotPreparedSession`,
  `CopilotSessionConfigRequest`, rendered artifacts oraz factory budujace
  `SessionConfig` i `MessageOptions`. Build przypina Java SDK 1.0.11 oraz
  minimalny Copilot CLI 1.0.57. Po `client.start()` runtime pobiera rzeczywista
  wersje CLI i protokolu przez `status.get`, publikuje
  `platform.copilot_runtime` i nie otwiera sesji dla starszego CLI.
- `copilot/runtime/options/`
  platformowy katalog modeli Copilota: provider, neutralne DTO i cache/fallback
  dla typed RPC `models.list`; pelne metadata capability/billing sa wewnetrznym
  source of truth takze dla polityki context tier, a publiczne API mapuje tylko
  pola potrzebne UI.
- `copilot/runtime/context/`
  neutralna polityka wyboru `long_context` przed create/resume: estymowany tryb
  `AUTO` korzysta z dynamicznych metadanych `runtime/options`, a feature moze
  zadeklarowac `LONG_CONTEXT_REQUIRED`. Platforma potwierdza efektywny tier
  przez typed RPC przed pierwszym `sendAndWait`, a user-visible lifecycle
  koreluje zazadanie i stan modelu z pierwszym rzeczywistym `tokenLimit` z
  `session.usage_info`. Po przekroczeniu runtime threshold platforma moze
  wykonac najwyzej jeden upgrade `abort -> close handle -> resume same
  sessionId` z `long_context`; initial prompt nie jest wysylany drugi raz, a
  report/tool evidence/tool budget stores pozostaja wspolne. Dla runtime
  resume w `AUTO` wartosc `contextTier=null` z `model.getCurrent` oznacza stan
  niepotwierdzony: continuation jest wysylane raz, a pierwszy
  `session.usage_info` potwierdza upgrade wzrostem `tokenLimit` albo publikuje
  ostrzezenie i pozostawia dokonczenie mechanizmowi compaction SDK. Nie
  podejmuj drugiej proby resume. Nie przywracaj
  blednego wariantu przez `setModel`. Progi, wykonanie SDK i rollback pozostaja
  mechanika platformy, a semantyczna potrzeba nalezy do feature'a.
- `copilot/runtime/execution/`
  platformowe uruchamianie `CopilotPreparedSession`: lifecycle klienta/sesji,
  event logging, controlled invocation exception oraz `CopilotExecutionResult`
  z trescia odpowiedzi i user-visible `AnalysisAiUsage`.
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
  tool invocation przez sink przekazany przez feature; przechowuje tez krotka
  historie invocation uzywana przez feedback capture.
- `copilot/tools/feedback/`
  platformowy tool `record_tool_feedback` oraz listener eventow invocation,
  ktory publikuje feedback jako sekcje `ai/tool-feedback` przez evidence
  store; nie dodawaj tu osobnego session store ani osobnego sinka runtime.
- `copilot/tools/report/`
  platformowe report tools i session-bound store generycznego raportu
  analitycznego; trzyma tylko ostatni snapshot per `reportId` i nie zawiera
  semantyki konkretnego feature'a ani promptu.

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
- Feature ma dostarczac prompt, guidance do uzycia skilli, available tools,
  hidden context, evidence sink i response handling jako parametry
  uruchomienia.
- `CopilotSkillRuntimeLoader` traktuje packaged resources jako immutable seed i
  przy starcie dopisuje do persistent effective katalogu pod
  `${analysis.ai.copilot.copilot-home}/skills` tylko brakujace pliki. Save i
  restore atomowo podmieniaja pojedynczy effective `SKILL.md`.
  `SessionConfig.skillDirectories` i `ResumeSessionConfig.skillDirectories`
  domyslnie dostaja ten sam pojedynczy root. Feature nie przekazuje katalogow
  ani list wybranych skilli; nie przywracaj selected roots ani selekcji per
  run. `skill` pozostaje domyslnie dostepny, ale feature moze jawnie wylaczyc
  skills wraz z katalogami dla sesji one-shot, jesli osadza effective tresc
  skilla w jedynym prompcie i konfiguruje pusta allowliste tools.
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
- Rzeczywisty test pary SDK/CLI uruchamia sie jawnie przez
  `COPILOT_SDK_LIVE_TEST=true` oraz opcjonalne `COPILOT_CLI_PATH`,
  `COPILOT_GITHUB_TOKEN` i `COPILOT_TEST_MODEL`; pokrywa sekwencje
  `create -> abort -> resume(long_context) -> model.getCurrent`.
