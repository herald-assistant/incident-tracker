# AGENTS

## Cel repo

To repo buduje aplikacje Spring Boot do analizy incydentow na podstawie
`correlationId`.

Glowne zalozenie:

1. aplikacja zbiera evidence z systemow zewnetrznych,
2. AI interpretuje evidence,
3. AI moze dociagac dodatkowy kod z GitLaba przez tools,
4. AI moze opcjonalnie zweryfikowac hipotezy danych przez DB tools,
5. aplikacja zwraca diagnoze i rekomendowany kolejny krok.

## Najpierw przeczytaj

Przed wieksza zmiana zacznij od:

1. `docs/architecture/01-system-overview.md`
2. `docs/architecture/02-key-decisions.md`
3. `docs/architecture/03-runtime-flow.md`
4. `docs/architecture/04-codex-continuation-guide.md`
5. `docs/architecture/05-package-dependencies.md`
6. `docs/architecture/06-modular-architecture-roadmap.md`
7. `docs/onboarding/README.md`

## Najwazniejsze niezmienniki

- `POST /analysis/jobs` jest publicznym startem analizy; przyjmuje
  `correlationId` oraz opcjonalne preferencje AI (`model`, `reasoningEffort`).
- `gitLabBranch` i `environment` sa wyprowadzane z evidence, glownie z logow
  Elasticsearch.
- `gitLabGroup` pochodzi z konfiguracji aplikacji, nie z evidence.
- Glowny flow jest `AI-first`, nie `rule-based`.
- Evidence pipeline pozostaje deterministyczny na `AnalysisContext`, ale po
  kroku deployment context Dynatrace i GitLab deterministic moga byc pobierane
  rownolegle z tego samego snapshotu contextu.
- GitLab ma trzy rozne capability:
  - generyczny adapter i source resolve,
  - deterministic resolution deployment context i code references jako evidence
    provider,
  - AI-guided fetching przez tools.
- Skill Copilota jest runtime resource aplikacji, nie plikiem w `.github`.

## Turbo wazne: docelowy model rozszerzalnosci

Tego nie wolno zgubic przy refaktorach, nowych feature'ach ani pracy agentow:
incident analysis jest pierwszym dedykowanym feature'em, ale repo ma pozostac
otwarte na kolejne analizy i inne sposoby ekspozycji tych samych capability.

Docelowy kierunek warstw:

1. adaptery/integracje sa reusable capability do systemow zewnetrznych,
2. tools/MCP sa reusable warstwa narzedzi nad adapterami,
3. Copilot SDK jest aktualna platforma uruchamiania AI, ktora korzysta z tools,
4. analiza incydentow jest dedykowanym feature'em skonfigurowanym na platformie,
5. kolejne analizy, np. dokumentacji, chatboty albo generowanie scenariuszy,
   maja korzystac z tej samej platformy i shared capability bez zaleznosci od
   feature'u incydentowego.

Zasady granic:

- `integrations.*` nie moze zalezec od `analysis.evidence`, `analysis.mcp`,
  `analysis.ai`, `analysis.flow` ani `analysis.job`. Adaptery maja byc mozliwe
  do reuse'u przez evidence pipeline, tools/MCP i zwykle endpointy REST. Nie
  przywracaj nowych adapterow do historycznego `analysis.adapter`.
- Tools/MCP nie powinny zalezec od dedykowanej analizy incydentow ani od
  szczegolow providera Copilot. Maja byc mozliwe do podpiecia pod dowolny loop
  agenta albo inna platforme AI.
- Copilot SDK runtime jest platform adapterem AI: przygotowuje sesje,
  allowliste tools, hidden context, execution, telemetry i capture jako
  mechanike runtime. Docelowo ma dostawac te parametry od feature'a, a nie
  sam wybierac incident prompt, skille albo tools.
- Dedykowane feature'y analityczne dostarczaja prompt, evidence, skille,
  hidden tool context, polityke uzycia capability i kontrakt odpowiedzi.
  Feature moze zalezec od platformy, tools i adapterow; platforma, tools i
  adaptery nie moga zalezec od feature'a.
- `common` i neutralne kontrakty nie sa miejscem na wszystko. Wyciagaj tam
  tylko male, stabilne elementy, ktore faktycznie sa wspolne dla kilku
  capability albo feature'ow.
- Usuwajac cykle importow, preferuj przeniesienie kontraktu do warstwy, ktora
  jest jego wlascicielem. Nie lam granic tylko po to, zeby szybciej zamknac
  compile-time graph.

## Niezmienniki Copilot SDK i optymalizacji

- Granica AI pozostaje generyczna: flow przekazuje do providera AI tylko
  `InitialAnalysisRequest` oraz `shared.evidence.AnalysisEvidenceSection`;
  nie wciskaj klas adapter-specific do prompt buildera ani kontraktu AI.
- Docelowa platforma Copilot ma byc parametryzowana przez feature. Aktualnym
  pierwszym inputem runtime jest `CopilotRunRequest`; prompt, skille,
  available tools, hidden context, evidence sink i response parser maja
  przychodzic w inpucie uruchomienia, a nie byc zakodowane jako stale zalozenia
  platformy.
- Aktualny runtime nie uzywa SDK attachments jako zrodla evidence. Artefakty
  incydentu sa renderowane jako logiczne pliki i osadzane inline w promptcie,
  a `MessageOptions` dostaje tylko `setPrompt(prompt)`.
- Nie zakladaj lokalnych sciezek plikowych dla artefaktow Copilota. Jesli
  zmieniasz delivery mode na SDK attachments, traktuj to jako jawna zmiane
  runtime z testami, dokumentacja i planem rollbacku.
- Sesja Copilota ma jawna allowliste tools przez `SessionConfig.availableTools`
  i `SessionHooks.onPreToolUse`; lokalny workspace, filesystem, shell i terminal
  pozostaja zablokowane w glownym flow analizy.
- GitLab i Database tools pozostaja session-bound przez hidden `ToolContext`.
  Model nie powinien podawac `gitLabGroup`, `gitLabBranch` ani `environment`
  dla tych ukrytych scope'ow. Elasticsearch nadal ma zastany jawny
  `correlationId` w model-facing schema; traktuj to jako drift do migracji, a
  nie wzorzec dla nowych tools.
- GitLab i Database tools moga miec tylko prosty operator-facing `reason` jako
  powod wywolania. Nie przywracaj dodatkowych model-facing parametrow
  eksploracyjnych, pytan diagnostycznych ani technicznych pseudo-heurystyk do
  user-facing evidence.
- Obecnie `analysis.ai.copilot.tools` ma pozostac czytelnym rootem runtime
  tools dla `CopilotSdkToolFactory`. Platformowa mechanika invocation, hidden
  `ToolContext`, eventow invocation, policy contracts, session validation,
  loggingu invocation i session-bound tool evidence store mieszka juz w
  `aiplatform.copilot.tools`. W `analysis.ai.copilot.tools` zostaja
  przejsciowo `description` oraz `policy.budget`, dopoki budzet jest spiety z
  telemetryka analizy. Generyczne helpery aplikacyjne, np. `JsonPayloadReader`,
  trzymaj poza Copilotem w `pl.mkn.incidenttracker.common`. Incident-specific
  GitLab/DB evidence mapping mieszka w `features.incidentanalysis.ai.copilot.tools`;
  podczas dalszej ekstrakcji do `aiplatform.copilot` zostawiaj w runtime tylko
  mechanike invocation.
- `aiplatform.copilot.tools.CopilotToolInvocationHandler` nie powinien
  zawierac logiki konkretnego toola. Walidacje i limity dodawaj jako
  `CopilotToolInvocationPolicy`, a logowanie, telemetryke i evidence capture
  jako listenery eventow invocation.
- GitLab i Elasticsearch tools sa fallback-only, gdy odpowiadajace evidence nie
  jest juz osadzone w artefaktach. Database tools sa opcjonalna capability
  AI-guided, nie providerem evidence.
- Optymalizacje Copilota prowadz inkrementalnie: najpierw pomiar i kontrakt
  wyniku, potem budzety eksploracji, dopiero pozniej wieksze zmiany flow.

## Gdzie czego szukac

- `src/main/java/pl/mkn/incidenttracker/analysis/flow`
  Glowna orkiestracja runtime analizy, response i listenery flow.
- `src/main/java/pl/mkn/incidenttracker/analysis/job`
  Jobowy feature `POST /analysis/jobs`, `GET /analysis/jobs/{analysisId}` i
  follow-up chat.
- `src/main/java/pl/mkn/incidenttracker/analysis/options`
  Opcje wykonania AI, katalog modeli i endpoint `GET /analysis/ai/options`.
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence`
  Deterministyczne zbieranie evidence, `AnalysisContext` i jawny collector
  krokow, z rownoleglym fan-outem Dynatrace + GitLab po deployment context.
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider`
  Konkretne kroki pipeline evidence oparte o adaptery i wczesniej zebrany
  `AnalysisContext`.
- `src/main/java/pl/mkn/incidenttracker/integrations`
  Docelowa reusable warstwa capability adapters. Dynatrace, Elasticsearch,
  GitLab, operational context i Database mieszkaja juz w `integrations`.
- `src/main/java/pl/mkn/incidenttracker/analysis/ai`
  Generyczny kontrakt AI i aktualna integracja Copilot SDK.
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter`
  Historyczny katalog po ekstrakcji adapterow. Nie dodawaj tu nowego kodu;
  nowe integracje trafiaja do `integrations`.
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp`
  Historyczny katalog po ekstrakcji MCP tools. Nie dodawaj tu nowego kodu;
  nowe tools trafiaja do `agenttools.<capability>.mcp`.
- `src/main/java/pl/mkn/incidenttracker/agenttools`
  Reusable tools/capability wspolne dla MCP wrappers i platform AI, np. hidden
  tool context keys, nazwy tools oraz przenoszone wrappery MCP nad
  integracjami. Adaptery nie powinny importowac `agenttools`.
- `src/main/java/pl/mkn/incidenttracker/aiplatform`
  Neutralna platforma uruchamiania AI. Pierwsze wydzielone slice'y to
  `aiplatform.copilot.runtime` oraz `aiplatform.copilot.tools` z
  handler/context/events/policy/logging/evidence; nie moze importowac incident
  analysis.
- `src/main/java/pl/mkn/incidenttracker/features`
  Dedykowane feature'y analityczne. Pierwszy slice to
  `features.incidentanalysis.ai.copilot.preparation` i `coverage`, czyli
  incident prompt/artifacts/tool policy, coverage heurystyki oraz GitLab/DB
  tool evidence capture dla Copilota.
- `src/main/java/pl/mkn/incidenttracker/shared/evidence`
  Neutralny model evidence wspolny dla pipeline, flow, job UI i AI:
  `AnalysisEvidenceSection`, `AnalysisEvidenceItem`, `AnalysisEvidenceAttribute`.
- `src/main/java/pl/mkn/incidenttracker/common`
  Male helpery wspolne dla calej aplikacji.
- `frontend`
  Zrodlowy workspace Angular dla operatora i helper widoku `/evidence`.
- `src/main/resources/static`
  Wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.
- `src/main/resources/copilot/skills`
  Skille Copilota pakowane do runtime.
- `src/main/resources/operational-context`
  Runtime catalog systemow, procesow, repozytoriow i regul handoffu.

## Zasady rozwoju

### Gdy dodajesz nowe zrodlo evidence

- Dodaj typowany adapter i modele w `integrations.<system>`.
- Dodaj `AnalysisEvidenceProvider` w `analysis.evidence.provider`.
- Provider powinien zwracac `shared.evidence.AnalysisEvidenceSection`.
- Nie dopisuj centralnego mappera "provider == X".

### Gdy dodajesz nowe capability AI

- Rozdziel strategię od danych konkretnej analizy.
- Prompt ma niesc dane incydentu.
- Skill ma niesc stale zasady pracy z tools i evidence.

### Gdy dodajesz nowe integracje zewnetrzne

- Preferuj prosty adapter REST.
- Trzymaj konfiguracje w properties.
- Izoluj wyjatkowe zachowania lokalnie dla danej integracji.

## Czego nie robic

- Nie przenosic `correlationId` do headerow glownego flow.
- Nie przywracac `branch` jako pola requestu startu analizy.
- Nie dedukowac `gitLabGroup` z logs lub trace.
- Nie mieszac skilli z kodem Java.
- Nie robic globalnego "trust all SSL" dla calej aplikacji.
- Nie wciskac provider-specific klas adapterow bezposrednio do prompt buildera.

## Konwencje projektu

- Spring Boot `3.5.x`, Java `17`.
- DTO preferencyjnie jako `record`.
- Walidacja przez `@Valid` i `jakarta.validation`.
- Wstrzykiwanie zaleznosci przez Lombok `@RequiredArgsConstructor`.
- HTTP po stronie integracji przez `RestClient`.
- Wspolne bledy API przez `ApiExceptionHandler`.
- Testy web przez `MockMvc`, testy integracyjne HTTP przez
  `MockRestServiceServer`.

## Weryfikacja

Podstawowe komendy:

- `mvn -q clean test`
- `cd frontend && npm test`
- `mvn -q -DskipTests package`

## Lokalne AGENTS

Utrzymujemy lokalne instrukcje na poziomie bezposrednich podkatalogow, zeby
granice modulow byly czytelne i stabilne po refaktorach.

- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/integrations/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/options/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/agenttools/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/aiplatform/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/features/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/shared/AGENTS.md`
