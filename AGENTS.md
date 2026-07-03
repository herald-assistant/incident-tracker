# AGENTS

## Cel repo

To repo rozwija aplikacje Spring Boot jako platforme do AI-augmented system
analysis. Incident tracker po `correlationId` jest pierwszym dedykowanym
feature'em, ale nie docelowa granica produktu.

Glowne zalozenie platformy:

1. feature zbiera albo przygotowuje kontekst z systemow zewnetrznych,
2. AI interpretuje ten kontekst w swoim kontrakcie odpowiedzi,
3. AI moze dociagac dodatkowy kod, operational context, logi albo dane przez
   reusable tools,
4. aplikacja zwraca wynik zrozumialy dla operatora/analityka oraz jawne
   ograniczenia widocznosci,
5. kolejne feature'y korzystaja z tych samych warstw platformy, tools i
   integracji bez zaleznosci od incident analysis.

Przyklady docelowych feature'ow poza incident analysis:

- flow explorer: jak request/use case przechodzi przez system, komponenty,
  endpointy, kolejki, bazy danych i integracje,
- functional logic explorer: pytania o logike funkcjonalna konkretnego use
  case'u,
- natural-language data diagnostics: bezpieczne pytania o dane systemu w DB
  jezykiem naturalnym.

## Najpierw przeczytaj

Przed wieksza zmiana zacznij od:

1. `docs/architecture/00-product-direction.md`
2. `docs/architecture/01-system-overview.md`
3. `docs/architecture/02-key-decisions.md`
4. `docs/architecture/03-runtime-flow.md`
5. `docs/architecture/04-codex-continuation-guide.md`
6. `docs/architecture/05-package-dependencies.md`
7. `docs/architecture/06-modular-architecture-roadmap.md`
8. `docs/architecture/07-open-work-plan.md`
9. `docs/architecture/08-operational-context-model-tools-and-usage.md`

## Najwazniejsze niezmienniki

- `POST /api/analysis/jobs` jest kanonicznym publicznym startem analizy;
  przyjmuje `correlationId` oraz opcjonalne preferencje AI (`model`,
  `reasoningEffort`). Legacy aliasy `/analysis/**` pozostaja dostepne tylko
  dla kompatybilnosci.
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
- Runtime skille Copilota w `src/main/resources/copilot/skills` utrzymuj po
  polsku. Nazwy skilli, nazwy tooli, pola JSON, klasy, endpointy i inne
  identyfikatory techniczne zostaja w oryginalnym brzmieniu, ale opisy,
  procedury, playbooki, przyklady i zasady pisz po polsku.
- Publiczny wynik initial incident analysis ma aktualny kontrakt:
  `detectedProblem`, `affectedProcess`, `affectedBoundedContext`,
  `affectedTeam`, `functionalAnalysis`, `technicalAnalysis`, `confidence`,
  `visibilityLimits`, `prompt`, `usage`. Nie przywracaj starych pol
  `summary`, `recommendedAction`, `rationale`, `affectedFunction` ani
  `evidenceReferences`.
- `functionalAnalysis` ma byc sekcja dla analityka biznesowo-systemowego i ma
  korzystac z operational context do osadzenia incydentu w systemie, procesie,
  bounded context, regule biznesowej, integracjach i handoffie.
- `technicalAnalysis` ma byc zgodne z runtime skillem
  `incident-technical-handoff` i zawierac konkretny material do naprawy,
  weryfikacji albo przekazania poza analizowany system.
- Operational context uzywa `system` jako kanonicznego bytu katalogowego.
  Dane deployment/runtime/service names sa wlasciwosciami i sygnalami systemu,
  a nie osobnym bytem referencyjnym. Nie przywracaj relacji ani DTO typu
  osobny komponent uruchomieniowy.
- Operational context tools sa neutralna capability pod prefixem `opctx_`.
  Definicje tooli nie moga niesc semantyki incident trackera; incidentowe
  zasady uzycia mieszkaja w feature policy/guidance oraz skillu Copilota.

## Turbo wazne: docelowy model rozszerzalnosci

Tego nie wolno zgubic przy refaktorach, nowych feature'ach ani pracy agentow:
incident analysis jest pierwszym dedykowanym feature'em, ale repo ma pozostac
otwarte na kolejne analizy i inne sposoby ekspozycji tych samych capability.

Docelowy kierunek warstw:

1. adaptery/integracje sa reusable capability do systemow zewnetrznych,
2. tools/MCP sa reusable warstwa narzedzi nad adapterami,
3. Copilot SDK jest aktualna platforma uruchamiania AI, ktora korzysta z tools,
4. analiza incydentow jest dedykowanym feature'em skonfigurowanym na platformie,
5. kolejne analizy, np. flow explorer, functional logic explorer,
   natural-language data diagnostics, dokumentacja albo generowanie
   scenariuszy, maja korzystac z tej samej platformy i shared capability bez
   zaleznosci od feature'u incydentowego.

Zasady granic:

- `integrations.*` nie moze zalezec od `analysis.*`, `agenttools.*`,
  `aiplatform.*` ani `features.*`. Adaptery
  maja byc mozliwe do reuse'u przez evidence pipeline, tools/MCP i zwykle
  endpointy REST. Produkcyjny i testowy root `analysis.*` jest zamkniety; nie
  dodawaj tam nowych klas, testow ani lokalnych instrukcji.
- Tools/MCP nie powinny zalezec od dedykowanej analizy incydentow ani od
  szczegolow providera Copilot. Maja byc mozliwe do podpiecia pod dowolny loop
  agenta albo inna platforme AI.
- Copilot SDK runtime jest platform adapterem AI: przygotowuje sesje,
  allowliste tools, hidden context, execution i capture jako
  mechanike runtime. Docelowo ma dostawac te parametry od feature'a, a nie
  sam wybierac incident prompt, skille albo tools.
- Obecnie nie utrzymujemy niewidocznej dla uzytkownika telemetryki sesji
  Copilota. Zostaje tylko usage/token/cost widoczny w job state/UI oraz
  user-facing tool evidence. Nowa telemetryka moze wrocic dopiero jako jawny,
  productized element z widocznym celem, testami i dokumentacja.
- Dedykowane feature'y analityczne dostarczaja prompt, evidence, skille,
  hidden tool context, polityke uzycia capability i kontrakt odpowiedzi.
  Feature moze zalezec od platformy, tools i adapterow; platforma, tools i
  adaptery nie moga zalezec od feature'a.
- Nie kazde HTTP API jest feature'em. Cross-screen endpointy dla frontendu i
  operatora, np. katalog opcji AI albo stabilne fasady nad adapterami, powinny
  byc traktowane jako shared/operator API pod `api.*`. Endpointy konkretnego
  use case'u zostaja przy `features.<feature>.api`.
- `common` i neutralne kontrakty nie sa miejscem na wszystko. Wyciagaj tam
  tylko male, stabilne elementy, ktore faktycznie sa wspolne dla kilku
  capability albo feature'ow.
- Widoki feature'ow oraz ich zasilanie powinny reuse'owac wspolne modele,
  komponenty i mechanizmy UI/UX dla powtarzalnych elementow pracy operatora:
  przebieg runu, tok pracy AI, tool evidence, follow-up chat, import/export,
  usage/cost i inne przekrojowe fragmenty analizy. Feature-specific pozostaje
  glowny request, prompt, policy i kontrakt merytorycznego rezultatu, ale
  drobniejsze elementy wyniku tez powinny korzystac ze spojnych komponentow i
  wzorcow prezentacji, zeby dodanie nowego feature'a nie wymagalo od
  uzytkownika nauki nowego interfejsu dla znanych czynnosci.
- Usuwajac cykle importow, preferuj przeniesienie kontraktu do warstwy, ktora
  jest jego wlascicielem. Nie lam granic tylko po to, zeby szybciej zamknac
  compile-time graph.

## Niezmienniki Copilot SDK i optymalizacji

- Granica AI obecnego incident flow pozostaje waska: flow przekazuje do
  providera AI tylko `InitialAnalysisRequest` oraz
  `shared.evidence.AnalysisEvidenceSection`;
  nie wciskaj klas adapter-specific do prompt buildera ani kontraktu AI.
- Gdy kod Java SDK, bytecode albo lokalny artefakt `copilot-sdk-java` nie
  wyjasnia semantyki opcji Copilota, obowiazkowo sprawdz upstream
  `github/copilot-sdk`, szczegolnie `nodejs/README.md` oraz schemat/protokol
  pakietu npm `@github/copilot`. Java SDK jest wrapperem nad Copilot CLI i
  czesc zachowan, np. `infiniteSessions`, progi kompaktowania, workspace
  sesji albo domyslne wartosci, bywa lepiej opisana w dokumentacji Node/CLI
  niz w klasach Javy. Nie zgaduj defaultow ani zakresow parametrow bez takiej
  weryfikacji.
- `SessionConfig.skillDirectories` musi dostawac katalogi-rooty zawierajace
  podkatalogi skilli z `SKILL.md`, a nie bezposrednie katalogi pojedynczych
  skilli. Gdy feature wybiera podzbior runtime skilli, platforma ma zbudowac
  techniczny selected root z tylko tymi skillami i przekazac ten root do SDK;
  nie wracaj do przekazywania listy bezposrednich katalogow skilli, bo
  wbudowany tool `skill` moze wtedy nie widziec siblingow i zwracac
  `Skill not found`.
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
- Operational context tools nie przyjmuja `correlationId`, `environment`,
  `gitLabGroup` ani `gitLabBranch` jako model-facing input. Moga miec tylko
  prosty operator-facing `reason`; scope katalogu pochodzi z konfiguracji
  aplikacji i adaptera `integrations.operationalcontext`.
- GitLab i Database tools moga miec tylko prosty operator-facing `reason` jako
  powod wywolania. Nie przywracaj dodatkowych model-facing parametrow
  eksploracyjnych, pytan diagnostycznych ani technicznych pseudo-heurystyk do
  user-facing evidence.
- `aiplatform.copilot.tools` jest platformowym rootem runtime tools:
  `CopilotSdkToolFactory`, invocation handler, hidden `ToolContext`, eventy
  invocation, policy contracts, session validation, logging invocation,
  description customization contract, session-bound tool evidence store,
  budget policy i budget state mieszkaja w platformie.
  Generyczne helpery aplikacyjne, np. `JsonPayloadReader`, trzymaj poza
  Copilotem w `pl.mkn.tdw.common`. Incident-specific GitLab/DB
  evidence mapping i Copilot-facing guidance opisow tools mieszkaja w
  `features.incidentanalysis.ai.copilot.tools`; podczas dalszej ekstrakcji do
  `aiplatform.copilot` zostawiaj w runtime tylko mechanike invocation.
- `aiplatform.copilot.tools.CopilotToolInvocationHandler` nie powinien
  zawierac logiki konkretnego toola. Walidacje i limity dodawaj jako
  `CopilotToolInvocationPolicy`, a logowanie i evidence capture
  jako listenery eventow invocation.
- GitLab i Elasticsearch tools sa fallback-only, gdy odpowiadajace evidence nie
  jest juz osadzone w artefaktach. Database tools sa opcjonalna capability
  AI-guided, nie providerem evidence.
- Optymalizacje Copilota prowadz inkrementalnie: najpierw pomiar i kontrakt
  wyniku, potem budzety eksploracji, dopiero pozniej wieksze zmiany flow.

## Gdzie czego szukac

- `src/main/java/pl/mkn/tdw/features/incidentanalysis/job`
  Jobowy feature `POST /api/analysis/jobs`,
  `GET /api/analysis/jobs/{analysisId}` i follow-up chat.
- `src/main/java/pl/mkn/tdw/api/aioptions`
  Shared/operator API dla katalogu modeli i endpointu
  `GET /api/analysis/ai/options`, mapujace platformowy katalog Copilota na
  kontrakt HTTP dla UI.
- `src/main/java/pl/mkn/tdw/api/operationalcontext`
  Shared/operator API dla katalogu operational context uzywanego przez UI,
  tools, GitLab repository discovery i feature'y.
- `src/main/java/pl/mkn/tdw/features/incidentanalysis/evidence`
  Deterministyczne zbieranie evidence, `AnalysisContext` i jawny collector
  krokow, z rownoleglym fan-outem Dynatrace + GitLab po deployment context.
- `src/main/java/pl/mkn/tdw/features/incidentanalysis/evidence/provider`
  Konkretne kroki pipeline evidence oparte o adaptery i wczesniej zebrany
  `AnalysisContext`.
- `src/main/java/pl/mkn/tdw/integrations`
  Docelowa reusable warstwa capability adapters. Dynatrace, Elasticsearch,
  GitLab, operational context i Database mieszkaja juz w `integrations`.
- `src/main/java/pl/mkn/tdw/agenttools`
  Reusable tools/capability wspolne dla MCP wrappers i platform AI, np. hidden
  tool context keys, nazwy tools oraz przenoszone wrappery MCP nad
  integracjami. Operational context tools mieszkaja tu jako neutralne
  `agenttools.operationalcontext` / `agenttools.operationalcontext.mcp`.
  Adaptery nie powinny importowac `agenttools`.
- `src/main/java/pl/mkn/tdw/aiplatform`
  Neutralna platforma uruchamiania AI. Pierwsze wydzielone slice'y to
  `aiplatform.copilot.runtime` oraz `aiplatform.copilot.tools` z
  handler/context/events/policy/logging/evidence; nie moze importowac incident
  analysis.
- `src/main/java/pl/mkn/tdw/features`
  Dedykowane feature'y analityczne. `features.incidentanalysis.ai.initial` i
  `chat` zawieraja kontrakty AI incident flow,
  `features.incidentanalysis.flow` zawiera orkiestracje runtime analizy,
  `features.incidentanalysis.job` zawiera job API/state/follow-up chat, a
  `features.incidentanalysis.ai.copilot` zawiera incident prompt/artifacts/tool
  policy, coverage heurystyki, providery Copilota oraz GitLab/DB tool evidence
  capture.
- `src/main/java/pl/mkn/tdw/shared/evidence`
  Neutralny model evidence wspolny dla pipeline, flow, job UI i AI:
  `AnalysisEvidenceSection`, `AnalysisEvidenceItem`, `AnalysisEvidenceAttribute`
  oraz listener aktualizacji tool evidence.
- `src/main/java/pl/mkn/tdw/shared/ai`
  Neutralne DTO preferencji wykonania AI, usage/token/cost oraz wspolne
  kontrakty zasilajace UI przebiegu pracy i follow-up chatu dla feature'ow.
- `src/main/java/pl/mkn/tdw/api`
  Globalny kontrakt bledow HTTP i docelowe miejsce na shared/operator API
  niezalezne od jednego feature'a. Nie przenos tu orchestration feature'a,
  promptow, evidence pipeline ani job state.
- `src/main/java/pl/mkn/tdw/common`
  Male helpery wspolne dla calej aplikacji.
- `frontend`
  Zrodlowy workspace Angular dla operatora: glowny incident analysis console
  oraz widoki pomocnicze `/evidence`, `/database` i `/operational-context`.
- `src/main/resources/static`
  Wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.
- `src/main/resources/copilot/skills`
  Skille Copilota pakowane do runtime. Incidentowe playbooki uzycia tools, np.
  operational context, sa zasobami tutaj, a nie logika neutralnych tooli.
- `src/main/resources/operational-context`
  Runtime catalog systemow, procesow, repozytoriow i regul handoffu. System
  jest tu kanonicznym targetem relacji i code-search scope'ow.

## Zasady rozwoju

### Gdy dodajesz nowe zrodlo evidence

- Dodaj typowany adapter i modele w `integrations.<system>`.
- Dodaj `AnalysisEvidenceProvider` w `features.incidentanalysis.evidence.provider`.
- Provider powinien zwracac `shared.evidence.AnalysisEvidenceSection`.
- Nie dopisuj centralnego mappera "provider == X".

### Gdy dodajesz nowe capability AI

- Rozdziel strategie od danych konkretnego feature'a.
- Prompt ma niesc dane danego feature'a, a nie zalozenia platformy.
- Skill ma niesc stale zasady pracy z tools i evidence.
- Skill runtime ma byc po polsku, z zachowaniem technicznych identyfikatorow
  kontraktow, tooli i kodu.

### Gdy dodajesz nowy feature analityczny

- Utworz dedykowany pakiet `features.<feature>`.
- Nie reuse'uj `features.incidentanalysis.flow/job/evidence` jako generycznego
  core.
- Dostarcz wlasny request/response, prompt, skille, tool policy, hidden
  context i result contract.
- Reuse'uj `aiplatform`, `agenttools`, `integrations`, `shared` i `common`.

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
- Gdy Spring ma rozstrzygac po nazwie beana, preferuj nazwe pola/parametru
  identyczna z nazwa beana zamiast lokalnego `@Qualifier`.
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

- `src/main/java/pl/mkn/tdw/integrations/AGENTS.md`
- `src/main/java/pl/mkn/tdw/features/incidentanalysis/evidence/AGENTS.md`
- `src/main/java/pl/mkn/tdw/features/incidentanalysis/job/AGENTS.md`
- `src/main/java/pl/mkn/tdw/agenttools/AGENTS.md`
- `src/main/java/pl/mkn/tdw/aiplatform/AGENTS.md`
- `src/main/java/pl/mkn/tdw/api/AGENTS.md`
- `src/main/java/pl/mkn/tdw/features/AGENTS.md`
- `src/main/java/pl/mkn/tdw/features/incidentanalysis/ai/AGENTS.md`
- `src/main/java/pl/mkn/tdw/features/incidentanalysis/flow/AGENTS.md`
- `src/main/java/pl/mkn/tdw/shared/AGENTS.md`
- `src/main/java/pl/mkn/tdw/shared/ai/AGENTS.md`
