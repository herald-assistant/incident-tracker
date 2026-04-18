# AGENTS

## Zakres

Ten katalog odpowiada za integracje z systemami zewnetrznymi i pomocnicze
endpointy testowe tych integracji.

Obejmuje:

- `elasticsearch/`
  properties, porty, adapter REST, modele logow i helper endpoint do recznego
  log search po `correlationId`,
- `dynatrace/`
  query model, properties i adapter REST do runtime signals,
- `gitlab/`
  properties, porty, adapter REST, repository search, source resolve i helper
  endpointy GitLaba.

Nie obejmuje:

- krokow pipeline evidence z `../evidence/provider`,
- MCP tools z `../mcp`,
- glownej orkiestracji `/analysis` z `../flow`, `../sync` i `../job`,
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
  evidence ani z requestu glownego flow `/analysis`.
- Dla source resolve cache drzewa repozytorium moze byc tylko request-scoped.

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
- `docs/18-real-gitlab-rest-integration.md`
