# Krok 3: Mapa Pakietow

## Cel

Nauczyc sie czytac repo po granicach odpowiedzialnosci, a nie po nazwach klas.

## Po tym kroku rozumiesz

- za co odpowiada kazdy glowny podkatalog `analysis/*`,
- gdzie szukac zmian przy konkretnym typie feature'a,
- jak rozpoznac, ze klasa wyladowala za wysoko albo za nisko.

## Glowna mapa

### `analysis/flow`

Orkiestracja runtime analizy, request/response i listenery progresu.

### `analysis/sync`

Synchroniczny endpoint `POST /analysis`.

### `analysis/job`

Asynchroniczny flow z jobami i projekcja stanu dla UI.

### `analysis/evidence`

`AnalysisContext`, collector, kontrakt providera i jawne metadata krokow.

### `analysis/evidence/provider`

Konkretne kroki pipeline:

- Elasticsearch,
- deployment context,
- Dynatrace,
- GitLab deterministic,
- operational context.

### `analysis/adapter`

Integracje zewnetrzne i helper endpointy testowe.
Sa tu dzisiaj adaptery Elasticsearch, Dynatrace, GitLaba i Database
capability.

### `analysis/mcp`

Warstwa tools wystawianych przez Spring AI.
Sa tu dzisiaj tools Elastica, GitLaba i warunkowo Database.

### `analysis/ai`

Generyczny kontrakt AI i implementacja oparta o Copilot SDK.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/flow`
- `src/main/java/pl/mkn/incidenttracker/analysis/sync`
- `src/main/java/pl/mkn/incidenttracker/analysis/job`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai`

## Checkpoint

- Gdzie powinien trafic nowy krok evidence?
- Gdzie powinien trafic nowy helper endpoint do recznego testowania integracji?
- Gdzie powinien trafic nowy tool dla modelu?
