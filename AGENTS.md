# AGENTS

## Cel repo

To repo buduje aplikacje Spring Boot do analizy incydentow na podstawie
`correlationId`.

Glowne zalozenie:

1. aplikacja zbiera evidence z systemow zewnetrznych,
2. AI interpretuje evidence,
3. AI moze dociagac dodatkowy kod z GitLaba przez tools,
4. aplikacja zwraca diagnoze i rekomendowany kolejny krok.

## Najpierw przeczytaj

Przed wieksza zmiana zacznij od:

1. `docs/architecture/01-system-overview.md`
2. `docs/architecture/02-key-decisions.md`
3. `docs/architecture/03-runtime-flow.md`
4. `docs/architecture/04-codex-continuation-guide.md`
5. `docs/learning-plan.md`

## Najwazniejsze niezmienniki

- `POST /analysis` przyjmuje tylko `correlationId`.
- `gitLabBranch` i `environment` sa wyprowadzane z evidence, glownie z logow
  Elasticsearch.
- `gitLabGroup` pochodzi z konfiguracji aplikacji, nie z evidence.
- Glowny flow jest `AI-first`, nie `rule-based`.
- Evidence providers pracuja sekwencyjnie na `AnalysisContext`.
- GitLab ma dwa rozne capability:
  - deterministic resolution deployment context i code references jako evidence
    provider,
  - AI-guided fetching przez tools.
- Skill Copilota jest runtime resource aplikacji, nie plikiem w `.github`.

## Gdzie czego szukac

- `src/main/java/pl/mkn/incidenttracker/analysis`
  Glowny flow analizy i kontrakt HTTP.
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence`
  Sekwencyjne zbieranie evidence.
- `src/main/java/pl/mkn/incidenttracker/analysis/ai`
  Generyczny kontrakt AI i model evidence.
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot`
  Integracja z GitHub Copilot Java SDK.
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab`
  Capability GitLaba: config, REST, deterministic provider, MCP tools, source
  resolver.
- `src/main/resources/static`
  Starter frontend serwowany bezposrednio przez Spring Boot jako `html + js +
  scss + css`.
- `src/main/resources/copilot/skills`
  Skille Copilota pakowane do runtime.

## Zasady rozwoju

### Gdy dodajesz nowe zrodlo evidence

- Dodaj typowany adapter i modele w pakiecie adaptera.
- Dodaj `AnalysisEvidenceProvider`.
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
- `mvn -q -DskipTests package`

## Lokalne AGENTS

W bardziej wrazliwych obszarach repo sa dodatkowe, bardziej szczegolowe pliki
`AGENTS.md`:

- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/AGENTS.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/AGENTS.md`
