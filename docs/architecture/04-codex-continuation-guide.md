# Codex Continuation Guide

## Cel tego dokumentu

Ten dokument ma pomagac w szybkim wznowieniu pracy bez odtwarzania calego
kontekstu z rozmow.

## Najpierw przeczytaj

Przy nowej sesji najlepiej zaczac od:

1. `docs/architecture/01-system-overview.md`
2. `docs/architecture/02-key-decisions.md`
3. `docs/architecture/03-runtime-flow.md`
4. `docs/architecture/05-package-dependencies.md`
5. `docs/architecture/06-modular-architecture-roadmap.md`
6. `docs/onboarding/README.md`

## Co jest "source of truth"

### Za architekture odpowiadaja glownie

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
- `src/main/resources/copilot/skills`
- `src/main/resources/operational-context`
- `src/main/resources/static`
- produkcyjny i testowy root `pl.mkn.incidenttracker.analysis` sa zamkniete;
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
- `features.incidentanalysis.ai.copilot`
- `aiplatform.copilot.runtime.execution`
- `features.incidentanalysis.ai.copilot.response`
- `features.incidentanalysis.ai.copilot.tools`
- `features.incidentanalysis.ai.copilot.tools.description`
- `CopilotIncidentArtifactService`
- `CopilotRenderedArtifact`
- `CopilotArtifactContentMapper`
- `CopilotIncidentFollowUpPreparationService`

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
- `src/main/resources/copilot/skills/incident-operational-context-tools`

## Jak rozwijac projekt bez psucia kierunku

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

### Gdy dodajesz nowa integracje z zewnetrznym API

Zasada:

1. preferuj prosty adapter REST,
2. trzymaj konfiguracje w properties,
3. nie rozszerzaj globalnego HTTP klienta bardziej niz trzeba,
4. jesli wyjatkowe zachowanie dotyczy tylko jednej integracji, izoluj je lokalnie.

Przyklad:

- ignorowanie SSL utrzymujemy lokalnie dla konkretnej integracji, np. GitLaba
  albo Elasticsearch/Kibana proxy.

## Czego teraz nie robic

- nie wracac do centralnego rule-based flow jako glownej sciezki analizy,
- nie przywracac `branch` jako pola requestu startu analizy,
- nie dedukowac `gitLabGroup` z evidence,
- nie mieszac skilli z kodem Java,
- nie robic globalnego "trust all SSL" dla calej aplikacji,
- nie wciskac provider-specific klas bezposrednio do prompt buildera AI.

## Rzeczy, ktore warto wiedziec przed kolejna zmiana

### `correlationId`, `environment` i `gitLabBranch`

`POST /analysis/jobs` dla UI przyjmuje `correlationId` oraz opcjonalne
preferencje AI: `model` i `reasoningEffort`. Nie przyjmuje scope'ow evidence.
`environment` i `gitLabBranch` sa rozwiazywane podczas zbierania evidence przez
osobny krok deployment context, a nie podawane przez klienta. Preferencje AI
trafiaja do `AnalysisAiOptions` i `SessionConfig`, nie do
deployment/GitLab/DB scope'u.

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

Publiczne requesty `POST /analysis/jobs` i
`POST /analysis/jobs/{analysisId}/chat/messages` nie przyjmuja tokenow,
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
Aktualny ekran `GET /` korzysta z `POST /analysis/jobs` i
`GET /analysis/jobs/{analysisId}`, zeby pokazywac postep analizy.
Przy starcie joba operator moze zostawic domyslny backendowy model/reasoning
albo wybrac `model` i dostepny dla niego `reasoningEffort` dla sesji AI. Opcje
sa pobierane z backendu przez `GET /analysis/ai/options` dopiero po pozytywnym
statusie auth.
Polling joba zwraca tez `toolEvidenceSections`, czyli pliki GitLaba,
kontekst lookupow GitLaba i wyniki DB dociagniete przez AI tools podczas kroku
`AI_ANALYSIS`.
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
`POST /analysis/jobs/{analysisId}/chat/messages`, a odpowiedz jest pollowana
tym samym `GET /analysis/jobs/{analysisId}` w polu `chatMessages`.
Follow-up chat dziala tylko dla live joba w pamieci backendu; importowany
zapis JSON pozostaje read-only.
Frontend pozwala tez zaimportowac i wyeksportowac zakonczona analize jako JSON,
route `/evidence` sluzy do recznego odpalania helper endpointow Elastica i
GitLaba, a route `/database` sluzy do recznego testowania endpointow nad
`DatabaseToolService` z jawnym operatorskim `environment`. Takie endpointy
traktuj jako shared/operator API nad adapterami:
cienkie diagnostyczne warianty moga zostac przy `integrations.<capability>`,
a stabilne powierzchnie dla wielu ekranow powinny trafic do `api.*`.

### Follow-up chat

Kontynuacja po wyniku poczatkowej analizy ma osobny kontrakt AI:

- `AnalysisAiChatProvider`,
- `AnalysisAiChatRequest`,
- `AnalysisAiChatResponse`.

Nie doklejaj tresci rozmowy ani dodatkowego scope'u do startu joba. Chat ma
reuse'owac scope zakonczonego joba oraz hidden `ToolContext` dla GitLaba,
Elastica i Database. Operational Context tools nie potrzebuja incident scope'u
w inputach; follow-up wystawia je zawsze, jesli capability jest zarejestrowana.
Kazda wiadomosc tworzy nowa sesje Copilota, zeby nie utrzymywac sesji SDK po
zakonczeniu poczatkowej analizy.

Tool evidence z follow-up powinno byc przypisane do konkretnej odpowiedzi
chatu, a nie mieszane z deterministycznym pipeline evidence.

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
`aiplatform.copilot.tools.policy.budget`, a session evidence store w
`aiplatform.copilot.tools.evidence`. GitLab i Database maja wlasne listenery
oraz mappery evidence capture w feature. Przy kolejnych toolach unikaj dopisywania
specjalnych przypadkow do handlera; dodaj policy albo listener eventu w
odpowiednim pakiecie.

Operational Context tools sa katalogowym browse/search/detail nad
`integrations.operationalcontext`: `opctx_get_scope`, `opctx_list_entities`,
`opctx_search`, `opctx_get_entity`. Incidentowe zasady korzystania z nich sa w
`incident-operational-context-tools` i w
`features.incidentanalysis.ai.copilot` policy/guidance, a nie w neutralnym
MCP mapperze.

Generyczne helpery JSON nie naleza do root `tools`. Wspolny reader payloadow to
`pl.mkn.incidenttracker.common.JsonPayloadReader`.

### Elastic helper flow

Elastic ma dzisiaj jeden helper endpoint i jeden MCP tool:

- `POST /api/elasticsearch/logs/search`
- `elastic_search_logs_by_correlation_id`

Oba przyjmuja tylko `correlationId`.
`base-url`, auth, Kibana space, index pattern i limity odpowiedzi pochodza z
`analysis.elasticsearch.*`.

### Database helper flow

Database ma shared/operator console endpointy pod `/api/database/*`, ktore
deleguja do `integrations.database.DatabaseToolService`. Request helpera niesie
manualny scope operatora (`environment`, opcjonalnie `correlationId`), ale
glowny publiczny start analizy nadal nie przyjmuje DB scope'u. DB service nadal
egzekwuje configured environments, allowliste schematow, typed filters,
masking/limiting i blokade raw SQL.

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
`codeSearchScopeIds`, `codeSearchProjects` i role repozytoriow, czyli projekty
GitLaba skladajace sie na kod dopasowanego komponentu, lacznie z bibliotekami
i shared modules.

Operational context tools korzystaja z tego samego adaptera katalogu, ale nie
sa kolejnym deterministic evidence providerem. Daja agentowi paginowany index,
search ranking i kompaktowy detail encji wtedy, gdy prompt/evidence ma waski
input, a feature policy pozwala na dociagniecie szerszego kontekstu.

## Najbardziej naturalne kolejne kierunki

### 1. Dalsze dopracowanie code-to-DB grounding

Policy zostawia focused GitLab tools przy luce `DB_CODE_GROUNDING_NEEDED`, zeby
model sprobowal znalezc encje/repozytorium/tabele/relacje w kodzie przed DB
discovery.
Policy zostawia tez focused GitLab tools przy
`AFFECTED_FUNCTION_GITLAB_RECOMMENDED`, zeby model zrobil maly lookup pod
szczegolowy, techniczno-funkcjonalny opis `affectedFunction`.
Naturalnym kolejnym krokiem jest mierzenie, czy to faktycznie zmniejsza liczbe
zgadywanych tabel, poprawia jakosc `affectedFunction` i czy nie zwieksza
niepotrzebnie kosztu GitLab calls.

### 2. Utrzymanie prostego widoku AI tool evidence

Mozliwe kierunki:

- lepsze formatowanie prostych wynikow DB tools bez dodawania diagnostycznych
  pytan ani technicznych badge'y w glownym widoku; parametry wejscia toola
  zostaja w JSON tooltipie,
- pinowanie najwazniejszych wynikow AI tool calls,
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

Dokumenty onboardingowe aktualizuj wtedy, gdy zmienia sie model systemu,
odpowiedzialnosc pakietow albo preferowany sposob rozwijania feature'ow.
Dokumenty architektoniczne powinny pozostawac aktualne.
