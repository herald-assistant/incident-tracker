# AGENTS

## Zakres

Ten katalog jest przejsciowym domem integracji z systemami zewnetrznymi,
reuse'owalnych capability adapters oraz pomocniczych endpointow testowych tam,
gdzie rzeczywiscie maja sens. Docelowy root dla przenoszonych i nowych
integracji to `pl.mkn.incidenttracker.integrations`.

Obejmuje:

- `database/`
  properties, routing polaczen, metadata Oracle, readonly query execution,
  SQL guard, masking i limiting wynikow DB capability.

Nie obejmuje:

- krokow pipeline evidence z `../evidence/provider`,
- MCP tools z `../mcp`,
- glownej orkiestracji analizy z `../flow` i `../job`,
- promptu, skilli i providera AI z `../ai`.

## Zasady modyfikacji

- Kazda integracja powinna miec lokalne properties, typowany port, adapter REST
  i modele DTO bez przeciekow z innych capability.
- Nietypowe zachowania HTTP, np. ignorowanie SSL, izoluj lokalnie dla danej
  integracji. Nie rozszerzaj globalnego klienta HTTP dla calej aplikacji.
- Helper endpointy diagnostyczne sa dozwolone tutaj tylko wtedy, gdy testuja
  bezposrednio capability adaptera.
- Nie dodawaj tu `AnalysisEvidenceProvider`, klas `@Tool` ani prompt-specific
  logiki.
- Nie przenos do adapterow heurystyk incidentowych typu logs -> deployment,
  logs -> repo albo evidence -> prompt. To nalezy do `evidence` albo `flow`.
- Kontrakty portow maja pozostac generyczne i reuse'owalne z evidence, MCP i
  helper endpointow.
- Dla GitLaba trzymaj `group` w konfiguracji aplikacji. Nie dedukuj go z
  evidence ani z requestu startu analizy.
- Dla source resolve cache drzewa repozytorium moze byc tylko request-scoped.
- Dla Database capability nie wprowadzaj globalnego `spring.datasource`.
  Routing DataSource ma pozostac lokalny i per environment.
- Dla Database capability nie zgaduj schematow domenowo w kodzie.
  Application-to-schema mapping ma pochodzic z konfiguracji.
- Dla Database capability typed request/result/scope/operator contracts sa w
  `pl.mkn.incidenttracker.analysis.adapter.database`. Adapter moze ich uzywac,
  ale nie powinien importowac `analysis.mcp.database` ani `agenttools`.
- Dynatrace zostal juz przeniesiony do
  `pl.mkn.incidenttracker.integrations.dynatrace`; nie przywracaj go do
  `analysis.adapter`.
- Elasticsearch zostal juz przeniesiony do
  `pl.mkn.incidenttracker.integrations.elasticsearch`; nie przywracaj go do
  `analysis.adapter`.
- GitLab i source resolve zostaly juz przeniesione do
  `pl.mkn.incidenttracker.integrations.gitlab`; nie przywracaj ich do
  `analysis.adapter`.
- Operational context zostal juz przeniesiony do
  `pl.mkn.incidenttracker.integrations.operationalcontext`; nie przywracaj go
  do `analysis.adapter`.

## Testy

- Dla adapterow REST preferuj `MockRestServiceServer`.
- Dla helper endpointow preferuj `MockMvc`.
- Gdy zmieniasz port lub model adaptera, sprawdz takze pakiety, ktore go
  reuse'uja: `../evidence/provider`, `../mcp` i `../ai`.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/onboarding/07-adapters-and-external-systems.md`
- `docs/onboarding/08-gitlab-capabilities.md`
