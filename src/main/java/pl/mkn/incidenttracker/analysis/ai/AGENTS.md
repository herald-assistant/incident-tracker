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
  root aktualnej integracji Copilot SDK, m.in.
  `CopilotSdkModelOptionsProvider`; incident initial/chat providery mieszkaja
  juz w `features.incidentanalysis.ai.copilot`,
- `copilot/execution/`
  wykonanie sesji, lifecycle klienta i logowanie eventow,
- `copilot/tools/`
  przejsciowy `policy/budget/` runtime. Metryki budzetu sa juz odpiete przez
  platformowy `CopilotToolBudgetTelemetry` i adapter telemetryczny w
  `copilot/telemetry`.
  Factory tools, invocation handler, context, eventy, neutralne policy
  contracts, session validation, logging, description customization contract i
  session evidence store sa juz w `aiplatform.copilot.tools`.

Nie obejmuje:

- klas adapterow integracyjnych z `../adapter`,
- sekwencyjnego pipeline evidence z `../evidence`,
- incident promptu, digestu, coverage i tool policy; te klasy mieszkaja w
  `features.incidentanalysis.ai.copilot`,
- opcji AI, katalogu modeli i endpointu `GET /analysis/ai/options` z
  `../options`,
- kontrolerow HTTP i job flow z `../job`.

## Incident Preparation

Incident-specific preparation i coverage sa juz poza tym katalogiem:

- `features.incidentanalysis.ai.copilot.preparation`
- `features.incidentanalysis.ai.copilot.coverage`

Nie importuj `features.*` z `analysis.ai`. Obecny kierunek zaleznosci to
feature -> kontrakty/mechanika `analysis.ai`, dopoki kolejne elementy nie
trafia do `aiplatform`.

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
- Copilot runtime uzywa neutralnego `runReference` do logow i execution
  identity. Incident feature moze przekazac tam `correlationId`, ale runtime
  nie powinien miec pola ani kontraktu `correlationId`.
- `aiplatform.copilot.tools.CopilotSdkToolFactory` ma reuse'owac istniejace
  Spring tools z `agenttools.*.mcp`, a nie dublowac ich implementacje.
- `aiplatform.copilot.tools.CopilotSdkToolFactory` ma tylko tworzyc
  `ToolDefinition`; wykonanie zostaje w
  `aiplatform.copilot.tools.CopilotToolInvocationHandler`.
- `aiplatform.copilot.tools.CopilotToolInvocationHandler` ma pozostac czysta
  granica invocation:
  policies, hidden context, callback, eventy i parsing wyniku. Nie dopisuj do
  niego logiki konkretnego toola, metryk, logowania ani mapowania evidence.
- `aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore` ma
  publikowac evidence przez neutralny session-bound sink, a nie bezposrednio
  zalezec od `AnalysisAiToolEvidenceListener`.
- `CopilotSdkExecutionGateway` ma wykonywac neutralna `CopilotPreparedSession`.
  Evidence sink powinien przychodzic z platformowego run requestu albo
  przygotowanej sesji; adapter z `AnalysisAiToolEvidenceListener` trzymaj po
  stronie providera AI.
- Nowe neutralne walidacje runtime dodawaj jako
  `aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy`, a side-effecty
  jako listenery eventow invocation. Budget zostaje przejsciowo w
  `analysis.ai.copilot.tools.policy.budget`, ale nie powinien juz zalezec od
  `CopilotSessionMetricsRegistry`; metryki zapisuje
  `copilot.telemetry.CopilotToolBudgetMetricsListener`.
- Incident-specific GitLab/DB evidence capture mieszka w
  `features.incidentanalysis.ai.copilot.tools`. W `analysis.ai.copilot.tools`
  zostawiaj tylko przejsciowy budget. Incident-specific guidance opisow tools
  mieszka w
  `features.incidentanalysis.ai.copilot.tools.description`. Handler,
  factory, hidden context, eventy invocation, policy contracts, session
  validation, logging, description customization contract i session evidence
  store sa platformowe w `aiplatform.copilot.tools`.
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

- Zmiany w `features.incidentanalysis.ai.copilot.preparation` powinny miec
  testy promptu, incident run assembly i ladowania skilli.
- Zmiany w `aiplatform.copilot.runtime` lub `aiplatform.copilot.tools`
  powinny miec testy konfiguracji sesji, hidden contextu, eventow, properties
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
