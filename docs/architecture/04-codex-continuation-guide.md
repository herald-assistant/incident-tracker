# Codex Continuation Guide

## Cel tego dokumentu

Ten dokument ma pomagac w szybkim wznowieniu pracy bez odtwarzania calego
kontekstu z rozmow.

## Najpierw przeczytaj

Przy nowej sesji najlepiej zaczac od:

1. `docs/architecture/01-system-overview.md`
2. `docs/architecture/02-key-decisions.md`
3. `docs/architecture/03-runtime-flow.md`
4. `docs/learning-plan.md`

## Co jest "source of truth"

### Za architekture odpowiadaja glownie

- `analysis.flow`
- `analysis.sync`
- `analysis.job`
- `AnalysisEvidenceCollector`
- `AnalysisEvidenceProvider`
- `AnalysisAiProvider`
- `analysis.evidence.provider.deployment`
- `analysis.evidence.provider.elasticsearch`
- `analysis.evidence.provider.operationalcontext`
- `frontend/`
- `src/main/resources/static`

### Za GitHub Copilot SDK odpowiadaja glownie

- `analysis.ai.copilot.preparation`
- `analysis.ai.copilot.execution`
- `analysis.ai.copilot.tools`

### Za GitLaba odpowiadaja glownie

- `analysis.adapter.gitlab`
- `analysis.adapter.gitlab.source`
- `analysis.evidence.provider.gitlabdeterministic`
- `analysis.mcp.gitlab`

### Za Elastica odpowiadaja glownie

- `analysis.adapter.elasticsearch`
- `analysis.mcp.elasticsearch`

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

## Najbardziej naturalne kolejne kierunki

### 1. Prezentacja evidence Dynatrace w UI

Dynatrace jest juz realny po stronie backendu.
Naturalnym kolejnym krokiem jest czytelne pokazanie service matches, problems i
metryk na froncie obok logow z Elastica.

### 2. Lepsza praca Copilota z repo

Mozliwe kierunki:

- statyczna mapa `kubernetes.container.name -> project candidates`,
- bardziej precyzyjny ranking kandydatow,
- dodatkowe narzedzia GitLaba,
- twardsze limity eksploracji repo,
- lepszy skill operacyjny.

### 3. Wynik analizy bogatszy o uzasadnienie

Obecnie `rationale` jest parsowane po stronie AI, ale nie wraca do glownej
odpowiedzi API.
To moze byc sensowne pole diagnostyczne lub debug mode.

### 4. End-to-end i operational hardening

W dalszej kolejnosci:

- retry,
- timeouty,
- fallbacki,
- testy end-to-end,
- bardziej celowana obserwowalnosc.

### 5. Rozwoj frontendu operacyjnego

Obecny ekran jest starterem.
Naturalne kolejne kroki to:

- historia analiz,
- lepsza prezentacja evidence,
- widok szczegolow bledow i surowej odpowiedzi,
- bardziej rozbudowany workflow operatorski.

## Szybkie komendy robocze

- `cd frontend && npm start`
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

Dokumentow krokowych nie trzeba aktualizowac przy kazdej zmianie technicznej.
Dokumenty architektoniczne powinny pozostawac aktualne.
