# Codex Continuation Guide

## Cel tego dokumentu

Ten dokument ma pomagac w szybkim wznowieniu pracy bez odtwarzania calego
kontekstu z rozmow.

## Najpierw przeczytaj

Przy nowej sesji najlepiej zaczac od:

1. `docs/architecture/00-product-direction.md`
2. `docs/architecture/01-system-overview.md`
3. `docs/architecture/02-key-decisions.md`
4. `docs/architecture/03-runtime-flow.md`
5. `docs/architecture/05-package-dependencies.md`
6. `docs/architecture/06-modular-architecture-roadmap.md`

## Co jest "source of truth"

### Za architekture odpowiadaja glownie

- `docs/architecture/00-product-direction.md` jako source of truth dla
  kierunku: platforma AI-augmented system analysis, a nie tylko incident
  tracker,
- `features.incidentanalysis.flow`
- `features.incidentanalysis.job`
- `AnalysisEvidenceCollector`
- `AnalysisEvidenceProvider`
- `features.incidentanalysis.ai.initial.InitialAnalysisProvider`
- `features.incidentanalysis.ai.chat.AnalysisAiChatProvider`
- `api.aioptions.AnalysisAiModelOptionsProvider` jako shared/operator fasada
  opcji AI
- `api.githubauth` jako shared/operator fasada GitHub App OAuth i statusu
  autoryzacji Copilota
- `api.operationalcontext` jako shared/operator fasada katalogu operational
  context dla UI i utrzymania jakosci katalogu
- `api.workspacesettings` jako shared/operator fasada lokalnych ustawien
  workspace'u z `settings.json`
- `api.elasticsearch`, `api.gitlab`, `api.gitlab.source` jako shared/operator
  fasady helper endpointow nad integracjami
- `aiplatform.copilot.runtime.options`
- `aiplatform.copilot.runtime.auth`
- `shared.ai`
- `api` jako docelowe miejsce cross-screen FE/operator API
- `shared.evidence`
- `features.incidentanalysis.ai.initial`
- `features.incidentanalysis.ai.chat`
- `agenttools.context`
- `agenttools.database`, `agenttools.elasticsearch`, `agenttools.gitlab`
- `agenttools.operationalcontext`
- `agenttools.elasticsearch.mcp`
- `agenttools.gitlab.mcp`
- `agenttools.database.mcp`
- `agenttools.operationalcontext.mcp`
- `aiplatform.copilot.runtime`
- `aiplatform.copilot.tools`
- `aiplatform.copilot.tools.feedback`
- `integrations.elasticsearch`
- `integrations.dynatrace`
- `integrations.gitlab`
- `integrations.gitlab.source`
- `integrations.github.auth`
- `integrations.operationalcontext`
- `integrations.database`
- `features.incidentanalysis.evidence.provider.deployment`
- `features.incidentanalysis.evidence.provider.elasticsearch`
- `features.incidentanalysis.evidence.provider.dynatrace`
- `features.incidentanalysis.evidence.provider.gitlabdeterministic`
- `features.incidentanalysis.evidence.provider.operationalcontext`
- `ui`
- `frontend/`
- `frontend/src/app`
- `frontend/AGENTS.md`
- `frontend/src/app/AGENTS.md`
- lokalne `AGENTS.md` pod frontendowymi obszarami, gdy zmiana dotyczy
  shell'a, feature screens albo Operational Context Workbench
- `src/main/resources/copilot/skills`
- `src/main/resources/operational-context`
- `src/main/resources/static`
- produkcyjny i testowy root `pl.mkn.tdw.analysis` sa zamkniete;
  nie wznawiac tam klas ani testow

### Za GitHub Copilot SDK odpowiadaja glownie

- `aiplatform.copilot.runtime`
- `aiplatform.copilot.runtime.auth`
- `aiplatform.copilot.runtime.options`
- `aiplatform.copilot.tools`
- `aiplatform.copilot.tools.context`
- `aiplatform.copilot.tools.events`
- `aiplatform.copilot.tools.policy`
- `aiplatform.copilot.tools.policy.budget`
- `aiplatform.copilot.tools.logging`
- `aiplatform.copilot.tools.description`
- `aiplatform.copilot.tools.evidence`
- `aiplatform.copilot.tools.feedback`
- `features.incidentanalysis.ai.copilot`
- `aiplatform.copilot.runtime.execution`
- `features.incidentanalysis.ai.copilot.response`
- `features.incidentanalysis.ai.copilot.tools`
- `features.incidentanalysis.ai.copilot.tools.description`
- `CopilotIncidentArtifactService`
- `CopilotRenderedArtifact`
- `CopilotArtifactContentMapper`
- `CopilotIncidentFollowUpPreparationService`

Jesli Java SDK albo lokalny artefakt `copilot-sdk-java` pokazuje tylko ksztalt
API, ale nie tlumaczy semantyki, defaultow albo bezpiecznego sposobu uzycia,
obowiazkowo sprawdz upstream GitHub Copilot SDK:

- `https://github.com/github/copilot-sdk`
- `https://github.com/github/copilot-sdk/blob/main/nodejs/README.md`
- schemat/protokol z pakietu npm `@github/copilot`, z ktorego generowane sa
  kontrakty SDK.

To jest praktyczne zrodlo wiedzy o zachowaniu Copilot CLI/Node runtime. Tak
zostaly zweryfikowane m.in. `infiniteSessions`, domyslne progi
`backgroundCompactionThreshold=0.8`, `bufferExhaustionThreshold=0.95`,
workspace `~/.copilot/session-state/{sessionId}` oraz eventy compaction.
Projektujac uzycie SDK, nie zgaduj znaczenia parametrow z samych nazw metod w
Javie; potwierdz je w upstream docs/protocol i dopiero potem mapuj na
`aiplatform.copilot`.

### Za Copilot auth odpowiadaja glownie

- `api.githubauth`
- `integrations.github.auth`
- `aiplatform.copilot.runtime.auth`
- `shared.ai.AnalysisAiAuthRef`
- `shared.ai.AnalysisAiAuthRefResolver`

### Za GitLaba odpowiadaja glownie

- `integrations.gitlab`
- `integrations.gitlab.source`
- `features.incidentanalysis.evidence.provider.gitlabdeterministic`
- `agenttools.gitlab`
- `agenttools.gitlab.mcp`

### Za Elastica odpowiadaja glownie

- `integrations.elasticsearch`
- `agenttools.elasticsearch`
- `agenttools.elasticsearch.mcp`

### Za operational context odpowiadaja glownie

- `integrations.operationalcontext`
- `agenttools.operationalcontext`
- `agenttools.operationalcontext.mcp`
- `features.incidentanalysis.evidence.provider.operationalcontext`
- `src/main/resources/copilot/skills/incident-operational-grounding`

## Jak rozwijac projekt bez psucia kierunku

### Najpierw rozstrzygnij ownership zmiany

Projekt nie jest juz tylko incident trackerem. Przy kazdej wiekszej zmianie
najpierw nazwij, gdzie ona nalezy:

- reusable capability w `integrations`,
- reusable tool/MCP w `agenttools`,
- platform mechanics w `aiplatform`,
- shared/operator API w `api`,
- neutralny kontrakt w `shared` albo maly helper w `common`,
- dedykowany workflow w `features.<feature>`.

Jesli zmiana dotyczy flow explorera, logiki funkcjonalnej use case'u albo
natural-language data diagnostics, nie doklejaj jej do
`features.incidentanalysis` tylko dlatego, ze to obecnie jedyny feature. Nowy
feature ma miec wlasny prompt, policy, hidden context, result contract i
entrypointy, a reuse dotyczy platformy, tools i integracji.

### Gdy dodajesz nowe zrodlo evidence

Zasada:

1. dodaj typowany adapter i modele w pakiecie adaptera,
2. dodaj `AnalysisEvidenceProvider`,
3. provider niech zwraca `AnalysisEvidenceSection`,
4. nie dopisuj centralnego mappera "if provider == X".

### Gdy dodajesz nowe narzedzia dla AI

Zasada:

1. capability trzymamy w `integrations.*`, a ekspozycje tools w
   `agenttools.<capability>` / `agenttools.<capability>.mcp`,
2. tool powinien miec jasny, konkretny kontrakt,
3. tool description ma byc reusable i bez semantyki jednego feature'a,
4. jesli capability ma strategie uzycia, rozwaz dedykowany skill,
5. skill trzymaj jako resource runtime, nie w `.github`.

Wyjatek od "capability w integrations" dotyczy narzedzi czysto
platformowych, ktore nie czytaja zewnetrznych systemow. Obecny przyklad to
`record_tool_feedback` w `aiplatform.copilot.tools.feedback`: zapisuje jawny,
user-visible feedback modelu o wyniku poprzedniego toola w biezacej sesji.
Sam callback toola ma pozostac bezstanowy; zapis do analizy powinien isc przez
listener `CopilotToolInvocationFinishedEvent`, tak jak obecny capture
GitLab/DB, i przez ten sam `AnalysisAiToolEvidenceListener` jako sekcja
`ai/tool-feedback`. Nie dopisywac go do kazdego skillu; prompt renderer ma
dodawac centralna instrukcje tylko wtedy, gdy tool jest dostepny.

### Gdy dodajesz nowa integracje z zewnetrznym API

Zasada:

1. preferuj prosty adapter REST,
2. trzymaj konfiguracje w properties,
3. nie rozszerzaj globalnego HTTP klienta bardziej niz trzeba,
4. jesli wyjatkowe zachowanie dotyczy tylko jednej integracji, izoluj je lokalnie.

Przyklad:

- ignorowanie SSL utrzymujemy lokalnie dla konkretnej integracji, np. GitLaba
  albo Elasticsearch/Kibana proxy.

### Konstruktory i test creators

Implementacja ma byc czytelna z perspektywy runtime. Preferuj Lombokowe
`@RequiredArgsConstructor` na finalnych zaleznosciach i nie dodawaj recznych
konstruktorow tylko dla wygody testow.

Jezeli test potrzebuje krotszego setupu, testowych defaultow, no-op persistence
albo mockowanych adapterow, utworz creator/builder w odpowiadajacym pakiecie
`src/test/java`. Produkcyjna klasa nie powinna niesc testowego wiring ani
domyslnych zaleznosci.

## Czego teraz nie robic

- nie wracac do centralnego rule-based flow jako glownej sciezki analizy,
- nie przywracac `branch` jako pola requestu startu analizy,
- nie dedukowac `gitLabGroup` z evidence,
- nie mieszac skilli z kodem Java,
- nie robic globalnego "trust all SSL" dla calej aplikacji,
- nie wciskac provider-specific klas bezposrednio do prompt buildera AI.

## Rzeczy, ktore warto wiedziec przed kolejna zmiana

### Zrodlo logow, `correlationId`, `environment` i `gitLabBranch`

`POST /api/analysis/jobs` dla UI przyjmuje wybor zrodla logow oraz opcjonalne
preferencje AI: `model` i `reasoningEffort`. Dla
`source=ELASTICSEARCH` klient podaje `correlationId`; dla
`source=CSV_UPLOAD` klient podaje plik `logFile`, a `correlationId` jest
wyprowadzany z kolumn CSV podczas walidacji. Request nie przyjmuje scope'ow
evidence takich jak `environment`, `gitLabBranch` albo `gitLabGroup`.
`environment` i `gitLabBranch` sa rozwiazywane podczas zbierania evidence przez
osobny krok deployment context, a nie podawane przez klienta. Preferencje AI
trafiaja do `AnalysisAiOptions` i `SessionConfig`, nie do
deployment/GitLab/DB scope'u.

UI powinno pobierac `GET /api/analysis/jobs/input-options` przed startem
joba. Jezeli konfiguracja Elasticsearch/Kibana jest niekompletna, sciezka
`source=ELASTICSEARCH` i pole `correlationId` sa blokowane, ale upload CSV
pozostaje dostepny.

Lista modeli i wspieranych `reasoningEffort` nie mieszka w frontendzie.
Frontend najpierw pobiera status Copilot auth z `GET /api/auth/github/status`.
Jesli status jest connected, pobiera katalog z `GET /analysis/ai/options`, a
fasada `AnalysisAiModelOptionsProvider` mapuje platformowy katalog modeli
Copilota na kontrakt aplikacji. To jest shared/operator API dla UI, nie czesc
incident job flow. Controller/DTO mieszkaja w `api.aioptions`, a neutralne
preferencje wykonania AI i non-secret auth reference w `shared.ai`.

Tryby auth:

- `LOCAL_TOKEN`: backend uzywa skonfigurowanego GitHub tokena i nigdy nie
  fallbackuje do lokalnego uzytkownika CLI.
- `GITHUB_APP`: operator laczy konto GitHub przez OAuth GitHub App; backend
  trzyma zaszyfrowane user access/refresh tokeny powiazane z HttpOnly
  operator session cookie.

Publiczne requesty `POST /api/analysis/jobs` i
`POST /api/analysis/jobs/{analysisId}/chat/messages` nie przyjmuja tokenow,
OAuth code ani loginu GitHub. Job state moze przenosic tylko
`AnalysisAiAuthRef`; token jest rozwiazywany tuz przed utworzeniem
`CopilotClientOptions`, gdzie zawsze ustawiamy jawny `githubToken` oraz
`useLoggedInUser=false`.

### `gitLabGroup`

Pochodzi z konfiguracji aplikacji.
AI ma interpretowac projekt i pliki, nie grupe ani branch.

### Skill

Skill jest czescia runtime aplikacji.
Zmienia sie razem z capability i powinien byc traktowany jako zasob wdrozeniowy.

### Source resolver

Endpoint `resolve` jest pomocniczy.
To nie jest centralna czesc job flow analizy, ale jest reuse'owany przez
deterministic provider do rozwiazywania symboli z logs.
Jego cache drzewa repo ma pozostac request-scoped, nie globalny.

### Frontend starter

Startowy ekran jest dzisiaj zrodlowo utrzymywany w `frontend/` jako aplikacja
Angular.
Produkcjny build zapisuje `index.html`, `js`, `css` i assets do
`src/main/resources/static`, skad frontend jest serwowany przez Spring Boot.
Product-facing shell to `Team Delivery Workspace`. Przed wieksza zmiana UI
przeczytaj `frontend/AGENTS.md` i najblizszy lokalny `AGENTS.md` w
`frontend/src/app`. Widoczna nawigacja ma trzy grupy:

- `Analysis Features` dla dedykowanych feature'ow, na teraz
  `Incident Analysis` i `Flow Explorer`,
- `Tool Workbench` dla analysis-independent capability: Elastic Logs,
  GitLab Source, Database Tools i Operational Context,
- `Platform` dla startowego overview workspace'u oraz customizacji:
  settings, personalizacja, autentykacja i modele.

Aktualny ekran `GET /` jest overview `Team Delivery Workspace`: pokazuje
wartosc platformy jezykiem uzytkownika, aktywne feature'y i oszczednosc czasu
bez tlumaczenia AI, tools, runtime ani integracji. Codzienny ekran incydentowy
jest pod `/incident-analysis` i korzysta z `POST /api/analysis/jobs` oraz
`GET /api/analysis/jobs/{analysisId}`, zeby pokazywac postep analizy.
Przy starcie joba operator wybiera zrodlo logow: Elasticsearch po
`correlationId` albo upload CSV. UI pobiera
`GET /api/analysis/jobs/input-options` i blokuje sciezke Elasticsearch, gdy
brakuje wymaganej konfiguracji, bez blokowania uploadu CSV. Operator moze tez
zostawic domyslny backendowy model/reasoning albo wybrac `model` i dostepny
dla niego `reasoningEffort` dla sesji AI. Opcje sa pobierane z backendu przez
`GET /analysis/ai/options` dopiero po pozytywnym statusie auth.
Polling joba zwraca tez `toolEvidenceSections`, czyli pliki GitLaba,
kontekst lookupow GitLaba i wyniki DB dociagniete przez AI tools podczas kroku
`AI_ANALYSIS`.
Jezeli model wywola `record_tool_feedback`, polling zwraca tez `toolFeedback`:
jawne oceny jakosci wynikow tools zapisane w stanie konkretnej analizy.
Krok `AI_ANALYSIS` moze tez zawierac `usage`: sumaryczne tokeny oraz szczegoly
input/output/cache/context zebrane z eventow Copilot SDK i zmapowane na
generyczny kontrakt aplikacji.
Ten sam krok moze zawierac `aiActivityEvents`, czyli widoczny dla operatora
runtime trace komunikatow/rozumowania AI, usage/context/cache snapshots,
lifecycle tools i bledow sesji. UI merge'uje `aiActivityEvents` z
`toolEvidenceSections` w jedna plaska liste pracy: message/reasoning opisuje
tok AI, powiazane tools sa kolejnymi wierszami z loaderem/OK/error, a JSON
debug jest dostepny po rozwinieciu danego wiersza.
Po `COMPLETED` frontend pokazuje panel chatu. Wyslanie wiadomosci idzie przez
`POST /api/analysis/jobs/{analysisId}/chat/messages`, a odpowiedz jest pollowana
tym samym `GET /api/analysis/jobs/{analysisId}` w polu `chatMessages`.
Follow-up odpowiedz assistant moze miec wlasne `toolFeedback`, pokazywane
kompaktowo przy tej wiadomosci.
Follow-up chat dziala dla live joba oraz lokalnego runu z zapisanym
`copilotSessionId`; importowany zapis JSON pozostaje read-only.
Frontend pozwala tez zaimportowac i wyeksportowac zakonczona analize jako JSON,
wlacznie z `toolFeedback`; starsze eksporty bez tego pola sa normalizowane do
pustych list.
route `/elastic` sluzy do recznego odpalania helper endpointow Elastica,
route `/gitlab` sluzy do recznego odpalania helper endpointow GitLaba, route
`/database` sluzy do recznego testowania endpointow nad `DatabaseToolService`
z jawnym operatorskim `environment`, a route `/operational-context` sluzy do
utrzymania katalogu systemow, repozytoriow, procesow, integracji i handoffu.
Te route'y sa `Tool Workbench`, a nie feature'y produktowe ani element
`Platform`.
Route `/workspace-settings` jest elementem `Platform`: pokazuje efektywne
wartosci z `application.properties`, zapisuje lokalne override'y do
`${tdw.workspace.directory}/settings.json` przez
`GET/PUT /api/workspace/settings` i na starcie obejmuje `app.ui.title`,
lokalny token Copilota oraz connection settings GitLaba, Elasticsearch i
Dynatrace:
`analysis.ai.copilot.auth.local.github-token`,
`analysis.gitlab.base-url`, `analysis.gitlab.group`, `analysis.gitlab.token`,
`analysis.elasticsearch.base-url`, `analysis.elasticsearch.kibana-space-id`,
`analysis.elasticsearch.index-pattern`,
`analysis.elasticsearch.authorization-header`, `analysis.dynatrace.base-url`
oraz `analysis.dynatrace.api-token`. Nie dodawaj tam flag SSL, np.
`analysis.gitlab.ignore-ssl-errors`, auth mode/display-name Copilota ani
technicznych limitow odpowiedzi.
Takie endpointy traktuj jako shared/operator API nad adapterami:
cienkie diagnostyczne warianty moga zostac przy `integrations.<capability>`,
a stabilne powierzchnie dla wielu ekranow powinny trafic do `api.*`.

### Initial analysis result report-first

Initial analysis nie traktuje juz finalnej odpowiedzi tekstowej/JSON z
`sendAndWait` jako zrodla prawdy. Feature tworzy scaffold `AnalysisReport`,
backend przekazuje go jako `CopilotRunRequest.initialReport`, a runtime
rejestruje go w `CopilotReportSessionStore` na czas pojedynczego
`sendAndWait`.

Model ma zapisac wynik przez report tools:

- `report_update_header`,
- `report_upsert_section`,
- `report_update_meta`,
- `report_get_current`.

`reportId`, feature i dozwolone sekcje pochodza z hidden `ToolContext`; nie
dodawaj `reportId` do model-facing schema tooli. Po wykonaniu runtime zwraca
ostatni snapshot w `CopilotExecutionResult.report()`, a feature mapper buduje z
niego publiczny `result`. JSON parser zostaje tylko fallbackiem diagnostycznym,
gdy raportu nie ma albo jest niekompletny.

Incident Analysis ma sekcje `FUNCTIONAL_ANALYSIS` i `TECHNICAL_HANDOFF`.
Flow Explorer ma `OVERVIEW` oraz aktywne sekcje wynikajace z goal/mode
requestu. UI pokazuje dotychczasowy feature-specific result oraz wspolny
`AnalysisReportPanelComponent`, jezeli snapshot joba zawiera `report`.

### Follow-up chat

Kontynuacja po wyniku poczatkowej analizy ma osobny kontrakt AI:

- `AnalysisAiChatProvider`,
- `AnalysisAiChatRequest`,
- `AnalysisAiChatResponse`.

Nie doklejaj tresci rozmowy ani dodatkowego scope'u do startu joba. Chat ma
reuse'owac scope zakonczonego joba oraz hidden `ToolContext` dla GitLaba,
Elastica i Database. Operational Context tools nie potrzebuja incident scope'u
w inputach; follow-up wystawia je zawsze, jesli capability jest zarejestrowana.
Initial analysis tworzy nowa sesje Copilota, a follow-up kontynuuje zapisana
sesje przez `copilotSessionId`. Przy resume aplikacja ponownie przekazuje
aktualne tools, skille, hidden context, hooks, permission handler, model i
`reasoningEffort`.

Dla Incident Analysis sama wiadomosc operatora jest jedynym nowym promptem,
bo kontrakt rozmowy jest oddzielony od kontraktu initial result. Dla Flow
Explorera follow-up owija wiadomosc operatora w feature-owned follow-up chat
prompt i skill `flow-explorer-follow-up-chat`: domyslnie odpowiedz ma byc
Markdownem, nie pelnym `flow-explorer-write-report`, ma poglebiac odpowiedz
przez dostepne tools i utrzymywac jezyk domenowy czytelny dla analityka albo
testera.

Tool evidence z follow-up powinno byc przypisane do konkretnej odpowiedzi
chatu, a nie mieszane z deterministycznym pipeline evidence.
Tool feedback z follow-up powinien byc przypisany do tej samej konkretnej
odpowiedzi assistant. Nie traktuj go jako deterministic evidence ani inputu do
root cause diagnosis.

### Znany drift MCP Elasticsearch

Docelowy invariant mowi, ze scope tools ma przychodzic z hidden `ToolContext`.
GitLab i Database tools juz tak dzialaja. `ElasticMcpTools` w
`agenttools.elasticsearch.mcp` nadal ma model-facing parametr `correlationId`;
jesli zmieniasz kontrakt albo zachowanie MCP Elastica, pierwszym bezpiecznym
kierunkiem jest migracja tego parametru do `ToolContext` i aktualizacja testow
tool factory/schema.

### Copilot tools po refaktorze eventowym

Root `aiplatform.copilot.tools` jest platformowa bramka do runtime tools:

- `CopilotSdkToolFactory` tworzy `ToolDefinition` z istniejacych Spring
  `ToolCallback`.

Logika pomocnicza jest rozdzielona wedlug ownership: platformowy context,
handler invocation, eventy, policy contracts, session validation, logging i
description customization mieszkaja w `aiplatform.copilot.tools`, budget w
`aiplatform.copilot.tools.policy.budget`, a session evidence/feedback capture
state w `aiplatform.copilot.tools.evidence`. GitLab i Database maja wlasne listenery
oraz mappery evidence capture w feature. Przy kolejnych toolach unikaj dopisywania
specjalnych przypadkow do handlera; dodaj policy albo listener eventu w
odpowiednim pakiecie.

`record_tool_feedback` korzysta z eventow invocation do zapisu feedbacku i
rozwiazania targetu poprzedniego tool calla. To nie jest ukryta telemetryka,
quality gate ani przekrojowa historia jakosci tools. Nie wprowadzaj osobnego
listener contractu obok `AnalysisAiToolEvidenceListener` ani side effectow
wewnatrz callbacka toola.

Operational Context tools sa katalogowym browse/search/detail nad
`integrations.operationalcontext`: `opctx_get_scope`, `opctx_list_entities`,
`opctx_search`, `opctx_get_entity`. Incidentowe zasady korzystania z nich sa w
`incident-operational-grounding` i w
`features.incidentanalysis.ai.copilot` policy/guidance, a nie w neutralnym
MCP mapperze.

Generyczne helpery JSON nie naleza do root `tools`. Wspolny reader payloadow to
`pl.mkn.tdw.common.JsonPayloadReader`.

### Elastic helper flow

Elastic ma dzisiaj jeden helper endpoint i jeden MCP tool:

- `POST /api/elasticsearch/logs/search`
- `elastic_search_logs_by_correlation_id`

Oba przyjmuja tylko `correlationId`.
`base-url`, auth, Kibana space, index pattern i limity odpowiedzi pochodza z
`analysis.elasticsearch.*`; Workspace Settings moze lokalnie nadpisac tylko
connection/auth fields, a limity zostaja w `application.properties`.

Brak kompletnej konfiguracji Elasticsearch/Kibana blokuje start
`source=ELASTICSEARCH` i usuwa `elastic_*` tools z allowlisty Copilota w
initial analysis oraz follow-up chat. Nie blokuje `source=CSV_UPLOAD`; CSV jest
deterministycznie walidowany i mapowany do tego samego modelu logow, ktory
potem zasila evidence `elasticsearch/logs`.

### Database helper flow

Database ma shared/operator console endpointy pod `/api/database/*`, ktore
deleguja do `integrations.database.DatabaseToolService`. Request helpera niesie
tylko neutralny scope integracji (`environment`) oraz payload konkretnej
operacji. Nie przyjmuje `correlationId`, `analysisRunId` ani innych pol
incident/session scope'u; te wartosci istnieja tylko w feature-owned hidden
ToolContext dla sesji AI. DB service nadal egzekwuje configured environments,
allowliste schematow, typed filters, masking/limiting i blokade raw SQL.

### Pipeline metadata

Evidence pipeline nie opiera sie juz tylko na `@Order`.
Kazdy provider deklaruje tez:

- `phase`,
- `consumesEvidence`,
- `producesEvidence`.

Jesli dokladasz nowy krok, aktualizuj te metadata razem z implementacja.

Aktualnie collector wykonuje Elasticsearch i deployment context sekwencyjnie,
a potem fan-outuje Dynatrace i GitLab deterministic rownolegle na tym samym
snapshotcie `AnalysisContext`, zanim przejdzie do operational context.
Sam provider operational context nie laduje juz katalogu bezposrednio z
resources, tylko korzysta z query-based adaptera i wystawia typed
`OperationalContextEvidenceView` dla downstreamow. Widok systemu zawiera tez
`codeSearchScopeIds`, `codeSearchProjects`, role repozytoriow, `reason` i
`readFor` oraz `codeSearchRepositorySearchBoundaries`, czyli projekty GitLaba
i coarse `searchMode/pathPrefixes` skladajace sie na kod dopasowanego
komponentu. Konkretne klasy, endpointy i pliki sa odkrywane przez GitLab
tools, a nie utrzymywane jako operational context.

Operational context tools korzystaja z tego samego adaptera katalogu, ale nie
sa kolejnym deterministic evidence providerem. Daja agentowi paginowany index,
search ranking i kompaktowy detail encji wtedy, gdy prompt/evidence ma waski
input, a feature policy pozwala na dociagniecie szerszego kontekstu.

## Najbardziej naturalne kolejne kierunki

### 0. Drugi feature jako dowod platformy

Najwazniejszy produktowo-architektoniczny krok to feature, ktory nie jest
incydentem. Najbardziej naturalne kandydaty:

- flow explorer: opis end-to-end requestu/use case'u przez komponenty,
  integracje, dane i kod, z endpointami i kolejkami dociaganymi przez
  dedykowane tools wtedy, gdy sa potrzebne,
- functional logic explorer: pytania o reguly, warianty i implementacje
  konkretnego use case'u,
- natural-language data diagnostics: readonly pytania o dane systemu jezykiem
  naturalnym z typed DB tools, limitami i audytem.

Taki feature powinien uzyc `aiplatform`, `agenttools`, `integrations`,
`shared.ai` i operational context, ale nie powinien importowac
`features.incidentanalysis`.

### 1. Dalsze dopracowanie code-to-DB grounding

Policy zostawia focused GitLab tools przy luce `DB_CODE_GROUNDING_NEEDED`, zeby
model sprobowal znalezc encje/repozytorium/tabele/relacje w kodzie przed DB
discovery.
Policy zostawia tez focused GitLab tools przy
`TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED`, zeby model zrobil maly lookup pod
konkretny `technicalAnalysis` zgodny z Technical Handoff v1.
Operational context powinien byc uzywany przy
`FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED`, zeby `functionalAnalysis` tlumaczyl
system, proces, bounded context, reguly i handoff ownera jezykiem zrozumialym
dla junior analityka. Naturalnym kolejnym krokiem jest mierzenie, czy to
faktycznie zmniejsza liczbe zgadywanych tabel, poprawia jakosc obu sekcji
wyniku i czy nie zwieksza niepotrzebnie kosztu GitLab/opctx calls.

### 2. Utrzymanie prostego widoku AI tool evidence

Mozliwe kierunki:

- lepsze formatowanie prostych wynikow DB tools bez dodawania diagnostycznych
  pytan ani technicznych badge'y w glownym widoku; parametry wejscia toola
  zostaja w JSON tooltipie,
- pinowanie najwazniejszych wynikow AI tool calls,
- przeglad `toolFeedback` po analizach jako reczny sygnal do poprawy opisow
  tools, adapter results, policy i operational context,
- czytelniejsze laczenie promptu, evidence i kodu dociagnietego przez AI,
- lepsze porownanie "deterministic evidence" kontra "AI-guided reads".

### 3. Trwalsza historia analiz

Aktualny job flow trzyma stan w pamieci procesu i dobrze sluzy operatorowi
pracujacemu "tu i teraz".
Naturalnym kolejnym krokiem moze byc trwalsza historia analiz, katalog
eksportow albo retention wynikow do review po czasie.

### 4. End-to-end i operational hardening

W dalszej kolejnosci:

- retry,
- timeouty,
- fallbacki,
- testy end-to-end,
- bardziej celowana obserwowalnosc.

### 5. Rozwoj frontendu operacyjnego

Obecny ekran jest juz sensownym narzedziem operatorskim, ale nadal jest miejsce
na dalszy rozwoj.
Naturalne kolejne kroki to:

- historia analiz,
- lepsza prezentacja evidence,
- widok szczegolow bledow i surowej odpowiedzi,
- bardziej rozbudowany workflow operatorski.

## Szybkie komendy robocze

- `cd frontend && npm start`
- `cd frontend && npm test`
- `cd frontend && npm run build`
- `mvn -q clean test`
- `mvn -q -DskipTests package`

## Kiedy aktualizowac dokumentacje

Te dokumenty warto aktualizowac wtedy, gdy zmienia sie jedno z ponizszych:

- glowny runtime flow,
- source of truth dla konfiguracji,
- granice odpowiedzialnosci pakietow,
- kierunek zaleznosci pomiedzy glownymi pakietami,
- podstawowe decyzje architektoniczne,
- preferowany sposob rozbudowy capability.

Dokumenty architektoniczne powinny pozostawac aktualne.
