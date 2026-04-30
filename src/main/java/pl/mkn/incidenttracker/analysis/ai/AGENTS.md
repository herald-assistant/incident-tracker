# AGENTS

## Zakres

Ten katalog odpowiada za generyczny kontrakt AI i aktualna implementacje oparta
o GitHub Copilot Java SDK.

Obejmuje:

- `initial/`
  kontrakt poczatkowej analizy joba: `InitialAnalysisProvider`,
  `InitialAnalysisRequest`, `InitialAnalysisPreparation` i
  `InitialAnalysisResponse`,
- `chat/`
  kontrakt follow-up chatu: `AnalysisAiChatProvider`, request/response, turny
  i snapshot poczatkowej analizy,
- `evidence/`
  AI-side `AnalysisAiToolEvidenceListener`; generyczne modele evidence
  mieszkaja w `pl.mkn.incidenttracker.shared.evidence`,
- `usage/`
  generyczny kontrakt zuzycia tokenow/cost/usage dla UI,
- `copilot/`
  root aktualnego providera AI: `CopilotInitialAnalysisProvider`,
  `CopilotSdkAnalysisChatProvider` i `CopilotSdkModelOptionsProvider`,
- `copilot/preparation/`
  obecna budowe incident promptu, tool policy, initial/follow-up run assembly i
  skill runtime loading. To jest stan przejsciowy: docelowo incident prompt, skill
  selection i tool policy powinny byc parametrami feature'a przekazanymi do
  platformy Copilot,
- `copilot/runtime/`
  neutralne elementy runtime SDK: `CopilotSdkProperties`, model listing,
  `CopilotModelSelection`, `CopilotPreparedSession`,
  `CopilotPreparedSessionRequest`, `CopilotSessionConfigRequest` oraz factory
  budujace `SessionConfig` i `MessageOptions` bez znajomosci incident policy
  ani pelnego tool/session contextu feature'a,
- `copilot/execution/`
  wykonanie sesji, lifecycle klienta i logowanie eventow,
- `copilot/tools/`
  obecny root runtime tools z klasami wejsciowymi: `CopilotSdkToolFactory`,
  `CopilotToolInvocationHandler` i `CopilotToolEvidenceSessionStore`.
  To jest stan przejsciowy przed rozdzieleniem platformowej mechaniki
  invocation od incident-specific policy/capture.
  Szczegoly trzymamy w podpakietach:
  - `context/` hidden `ToolContext` i session-bound scope,
  - `description/` dekorowanie opisow tools,
  - `events/` eventy invocation,
  - `policy/` session validation, budget i przyszle policies,
  - `logging/` operacyjne logowanie invocation,
  - `gitlab/` i `database/` capture evidence konkretnych capability.

Nie obejmuje:

- klas adapterow integracyjnych z `../adapter`,
- sekwencyjnego pipeline evidence z `../evidence`,
- opcji AI, katalogu modeli i endpointu `GET /analysis/ai/options` z
  `../options`,
- kontrolerow HTTP i job flow z `../job`.

## Zasady modyfikacji

- `InitialAnalysisProvider` ma pozostac stabilna granica miedzy flow a konkretnym
  SDK lub modelem i mieszka w `initial/`.
- `InitialAnalysisPreparation` jest kontraktem initial flow. Copilotowy wrapper
  `CopilotInitialAnalysisPreparation` moze go implementowac, ale neutralna
  sesja wykonania SDK (`CopilotPreparedSession`) nie powinna udawac initial
  analysis, bo jest uzywana takze przez follow-up chat.
- `AnalysisAiChatProvider` ma pozostac osobna granica kontynuacji joba; nie
  mieszaj jego tekstowej odpowiedzi z JSON-only kontraktem poczatkowej analizy.
  Kontrakty chatu trzymamy w `chat/`.
- AI dostaje tylko `InitialAnalysisRequest` i generyczne
  `shared.evidence.AnalysisEvidenceSection`. Nie wciskaj tu klas
  adapter-specific. Generyczne evidence models trzymamy w
  `pl.mkn.incidenttracker.shared.evidence`, a w `analysis.ai.evidence`
  zostaje listener tool evidence.
- Prompt ma niesc dane konkretnego incydentu. Stale zasady pracy z toolami i
  evidence powinny trafac do skilla albo jawnej konfiguracji preparation.
- Skill pozostaje runtime resource aplikacji w
  `src/main/resources/copilot/skills`, a nie plikiem w `.github`.
- Docelowo Copilot runtime nie powinien sam wybierac promptu, skilli,
  available tools, hidden contextu ani parsera odpowiedzi dla incydentu. Te
  elementy powinny przychodzic z feature'a w platformowym run request.
- Tool factory ma reuse'owac istniejace Spring tools z `../mcp`, a nie dublowac
  ich implementacje.
- `CopilotSdkToolFactory` ma tylko tworzyc `ToolDefinition`; wykonanie zostaje
  w `CopilotToolInvocationHandler`.
- `CopilotToolInvocationHandler` ma pozostac czysta granica invocation:
  policies, hidden context, callback, eventy i parsing wyniku. Nie dopisuj do
  niego logiki konkretnego toola, metryk, logowania ani mapowania evidence.
- Nowe walidacje i limity runtime dodawaj jako `CopilotToolInvocationPolicy`,
  a side-effecty jako listenery eventow invocation.
- Obecnie logike per capability, np. GitLab albo DB evidence capture, trzymaj
  w `copilot/tools/<capability>`. Przy ekstrakcji do `aiplatform.copilot`
  przenies incident-specific policy i mapping evidence do feature'a, a w
  platformie zostaw generyczna mechanike invocation.
- User-facing tool evidence ma pozostac proste: GitLab pokazuje plik, kod i
  `reason`, a Database pokazuje wynik i `reason`. Nie przywracaj dodatkowych
  pseudo-heurystyk ani technicznych pol do payloadu dla operatora.
- Permission handling musi byc jawnie ustawione. Nie zostawiaj domyslnego,
  nieczytelnego zachowania SDK.
- Parsing odpowiedzi initial analysis ma pozostac odporny na formatowanie, ale
  kontrakt pol `detectedProblem`, `summary`, `recommendedAction`, `rationale`,
  `affectedFunction`, `affectedProcess`, `affectedBoundedContext`,
  `affectedTeam` powinien pozostac stabilny dla flow i UI.
- Follow-up chat moze odpowiadac operatorskim tekstem, ale nadal ma reuse'owac
  snapshot poczatkowej analizy, historie rozmowy, hidden tool context i prosty
  user-facing tool evidence.
- Jesli kiedys dojda kolejne providery AI, trzymaj ich szczegoly lokalnie i nie
  rozlewaj zaleznosci SDK poza ten katalog.

## Testy

- Zmiany w `copilot/preparation` powinny miec testy promptu, incident run
  assembly i ladowania skilli.
- Zmiany w `copilot/runtime` powinny miec testy konfiguracji sesji, properties
  i model listing, jesli dotykaja tych mechanizmow.
- Zmiany w `copilot/tools` powinny miec testy mapowania Spring tools na tool
  definitions.
- Zmiany w `copilot/execution` powinny zachowac kontrakty i obserwowalnosc
  lifecycle klienta i sesji.
- Zmiany w providerze AI powinny miec testy parsowania odpowiedzi modelu.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/onboarding/09-spring-ai-and-mcp-tools.md`
- `docs/onboarding/10-copilot-sdk-analysis-runtime.md`
