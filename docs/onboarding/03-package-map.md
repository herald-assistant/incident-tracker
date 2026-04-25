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

Integracje zewnetrzne, reuse'owalne capability adapters i helper endpointy
testowe.
Sa tu dzisiaj adaptery Elasticsearch, Dynatrace, GitLaba, Database capability
i operational context.

### `analysis/mcp`

Warstwa tools wystawianych przez Spring AI.
Sa tu dzisiaj tools Elastica, GitLaba i warunkowo Database.

### `analysis/ai`

Generyczny kontrakt AI i implementacja oparta o Copilot SDK.

### `api`

Wspolny kontrakt bledow HTTP i walidacji dla endpointow backendu.

### `ui`

Cienki routing Spring MVC dla frontendowych route'ow Angulara.

### `frontend/`

Zrodlowa aplikacja Angular operatora: widok analizy, widok `/evidence`,
komponenty, modele i testy UI.

### `src/main/resources/copilot/skills`

Runtime skille ladowane do sesji Copilota.

### `src/main/resources/operational-context`

Katalog systemow, procesow, repozytoriow i regul routingu uzywany przez
operational context enrichment.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/flow`
- `src/main/java/pl/mkn/incidenttracker/analysis/sync`
- `src/main/java/pl/mkn/incidenttracker/analysis/job`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai`
- `src/main/java/pl/mkn/incidenttracker/api`
- `src/main/java/pl/mkn/incidenttracker/ui`
- `frontend/src/app`
- `src/main/resources/copilot/skills`
- `src/main/resources/operational-context`

## Checkpoint

- Gdzie powinien trafic nowy krok evidence?
- Gdzie powinien trafic nowy helper endpoint do recznego testowania integracji?
- Gdzie powinien trafic nowy tool dla modelu?
- Gdzie zmienisz stale zasady pracy modelu i gdzie runtime katalog routingu?
