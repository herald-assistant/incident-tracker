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
5. `docs/onboarding/README.md`

## Najwazniejsze niezmienniki

- `POST /analysis` przyjmuje tylko `correlationId`.
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

## Gdzie czego szukac

- `src/main/java/pl/mkn/incidenttracker/analysis/flow`
  Glowna orkiestracja runtime analizy, request/response i listenery flow.
- `src/main/java/pl/mkn/incidenttracker/analysis/sync`
  Synchroniczny feature `POST /analysis`.
- `src/main/java/pl/mkn/incidenttracker/analysis/job`
  Asynchroniczny feature `POST /analysis/jobs` i `GET /analysis/jobs/{analysisId}`.
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence`
  Deterministyczne zbieranie evidence, `AnalysisContext` i jawny collector
  krokow, z rownoleglym fan-outem Dynatrace + GitLab po deployment context.
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider`
  Konkretne kroki pipeline evidence oparte o adaptery i wczesniej zebrany
  `AnalysisContext`.
- `src/main/java/pl/mkn/incidenttracker/analysis/ai`
  Generyczny kontrakt AI i aktualna integracja Copilot SDK.
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter`
  Integracje zewnetrzne, reuse'owalne capability adapters i helper endpointy:
  Elasticsearch, Dynatrace, GitLab, Database oraz operational context.
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp`
  MCP tools i ich konfiguracja rejestracji, delegujace do adapterow albo use
  case'ow.
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

- Dodaj typowany adapter i modele w pakiecie adaptera.
- Dodaj `AnalysisEvidenceProvider` w `analysis.evidence.provider`.
- Provider powinien zwracac `AnalysisEvidenceSection`.
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
- Nie przywracac `branch` jako pola requestu `/analysis`.
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

W `analysis` utrzymujemy lokalne instrukcje na poziomie bezposrednich
podkatalogow, zeby granice modulow byly czytelne i stabilne po refaktorach.

- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/sync/AGENTS.md`
