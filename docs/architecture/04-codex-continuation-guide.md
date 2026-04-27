# Codex Continuation Guide

## Cel tego dokumentu

Ten dokument ma pomagac w szybkim wznowieniu pracy bez odtwarzania calego
kontekstu z rozmow.

## Najpierw przeczytaj

Przy nowej sesji najlepiej zaczac od:

1. `docs/architecture/01-system-overview.md`
2. `docs/architecture/02-key-decisions.md`
3. `docs/architecture/03-runtime-flow.md`
4. `docs/onboarding/README.md`

## Co jest "source of truth"

### Za architekture odpowiadaja glownie

- `analysis.flow`
- `analysis.sync`
- `analysis.job`
- `AnalysisEvidenceCollector`
- `AnalysisEvidenceProvider`
- `AnalysisAiProvider`
- `AnalysisAiChatProvider`
- `AnalysisAiModelOptionsProvider`
- `analysis.adapter.database`
- `analysis.evidence.provider.deployment`
- `analysis.evidence.provider.elasticsearch`
- `analysis.evidence.provider.operationalcontext`
- `analysis.mcp.database`
- `frontend/`
- `frontend/src/app`
- `src/main/resources/copilot/skills`
- `src/main/resources/operational-context`
- `src/main/resources/static`

### Za GitHub Copilot SDK odpowiadaja glownie

- `analysis.ai.copilot.preparation`
- `analysis.ai.copilot.execution`
- `analysis.ai.copilot.tools`
- `CopilotArtifactService`

### Za GitLaba odpowiadaja glownie

- `analysis.adapter.gitlab`
- `analysis.adapter.gitlab.source`
- `analysis.evidence.provider.gitlabdeterministic`
- `analysis.mcp.gitlab`

### Za Elastica odpowiadaja glownie

- `analysis.adapter.elasticsearch`
- `analysis.mcp.elasticsearch`

### Za operational context odpowiadaja glownie

- `analysis.adapter.operationalcontext`
- `analysis.evidence.provider.operationalcontext`

## Jak rozwijac projekt bez psucia kierunku

### Gdy dodajesz nowe zrodlo evidence

Zasada:

1. dodaj typowany adapter i modele w pakiecie adaptera,
2. dodaj `AnalysisEvidenceProvider`,
3. provider niech zwraca `AnalysisEvidenceSection`,
4. nie dopisuj centralnego mappera "if provider == X".

### Gdy dodajesz nowe narzedzia dla AI

Zasada:

1. capability trzymamy blisko adaptera,
2. tool powinien miec jasny, konkretny kontrakt,
3. jesli capability ma strategie uzycia, rozwaz dedykowany skill,
4. skill trzymaj jako resource runtime, nie w `.github`.

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
- nie przywracac `branch` jako pola requestu `/analysis`,
- nie dedukowac `gitLabGroup` z evidence,
- nie mieszac skilli z kodem Java,
- nie robic globalnego "trust all SSL" dla calej aplikacji,
- nie wciskac provider-specific klas bezposrednio do prompt buildera AI.

## Rzeczy, ktore warto wiedziec przed kolejna zmiana

### `correlationId`, `environment` i `gitLabBranch`

`POST /analysis` przyjmuje tylko `correlationId`.
`environment` i `gitLabBranch` sa rozwiazywane podczas zbierania evidence przez
osobny krok deployment context, a nie podawane przez klienta.

`POST /analysis/jobs` dla UI nadal nie przyjmuje scope'ow evidence, ale moze
przyjac opcjonalne preferencje AI: `model` i `reasoningEffort`. Te pola trafiaja
do `AnalysisAiOptions` i `SessionConfig`, nie do deployment/GitLab/DB scope'u.

Lista modeli i wspieranych `reasoningEffort` nie mieszka w frontendzie.
Frontend pobiera ja z `GET /analysis/ai/options`, a backend mapuje metadane
Copilot SDK przez `AnalysisAiModelOptionsProvider`.

### `gitLabGroup`

Pochodzi z konfiguracji aplikacji.
AI ma interpretowac projekt i pliki, nie grupe ani branch.

### Skill

Skill jest czescia runtime aplikacji.
Zmienia sie razem z capability i powinien byc traktowany jako zasob wdrozeniowy.

### Source resolver

Endpoint `resolve` jest pomocniczy.
To nie jest jeszcze centralna czesc orchestration flow `/analysis`, ale jest
reuse'owany przez deterministic provider do rozwiazywania symboli z logs.
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
sa pobierane z backendu przez `GET /analysis/ai/options`.
Polling joba zwraca tez `toolEvidenceSections`, czyli pliki GitLaba dociagniete
przez AI tools podczas kroku `AI_ANALYSIS`.
Po `COMPLETED` frontend pokazuje panel chatu. Wyslanie wiadomosci idzie przez
`POST /analysis/jobs/{analysisId}/chat/messages`, a odpowiedz jest pollowana
tym samym `GET /analysis/jobs/{analysisId}` w polu `chatMessages`.
Follow-up chat dziala tylko dla live joba w pamieci backendu; importowany
zapis JSON pozostaje read-only.
Frontend pozwala tez zaimportowac i wyeksportowac zakonczona analize jako JSON,
a route `/evidence` sluzy do recznego odpalania helper endpointow Elastica i
GitLaba.

### Follow-up chat

Kontynuacja analizy po finalnym wyniku ma osobny kontrakt AI:

- `AnalysisAiChatProvider`,
- `AnalysisAiChatRequest`,
- `AnalysisAiChatResponse`.

Nie rozszerzaj `POST /analysis` o tresc rozmowy ani dodatkowy scope. Chat ma
reuse'owac scope zakonczonego joba oraz hidden `ToolContext` dla GitLaba,
Elastica i Database. Kazda wiadomosc tworzy nowa sesje Copilota, zeby nie
utrzymywac sesji SDK po zakonczeniu finalnej analizy.

Tool evidence z follow-up powinno byc przypisane do konkretnej odpowiedzi
chatu, a nie mieszane z deterministycznym pipeline evidence.

### Elastic helper flow

Elastic ma dzisiaj jeden helper endpoint i jeden MCP tool:

- `POST /api/elasticsearch/logs/search`
- `elastic_search_logs_by_correlation_id`

Oba przyjmuja tylko `correlationId`.
`base-url`, auth, Kibana space, index pattern i limity odpowiedzi pochodza z
`analysis.elasticsearch.*`.

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
`OperationalContextEvidenceView` dla downstreamow.

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
  pytan, parametrow ani technicznych badge'y,
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
- podstawowe decyzje architektoniczne,
- preferowany sposob rozbudowy capability.

Dokumenty onboardingowe aktualizuj wtedy, gdy zmienia sie model systemu,
odpowiedzialnosc pakietow albo preferowany sposob rozwijania feature'ow.
Dokumenty architektoniczne powinny pozostawac aktualne.
