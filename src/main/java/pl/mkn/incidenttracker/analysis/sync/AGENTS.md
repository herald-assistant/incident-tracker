# AGENTS

## Zakres

Ten katalog odpowiada za synchroniczny feature `POST /analysis`.

Obejmuje:

- kontroler glownego endpointu analizy,
- cienki serwis delegujacy do wspolnej orkiestracji flow.

Nie obejmuje:

- asynchronicznego job flow z `../job`,
- orkiestracji runtime z `../flow`,
- adapterow, providerow evidence i implementacji AI.

## Zasady modyfikacji

- `POST /analysis` pozostaje glownym kontraktem API i przyjmuje tylko
  `correlationId`.
- Kontroler i serwis maja pozostac cienkie: walidacja requestu, delegacja do
  `AnalysisOrchestrator`, zwrocenie wyniku.
- Nie przenos tu logiki job progress, evidence collection ani mapowania
  adapter-specific danych.
- Obsluge bledow trzymaj w globalnym `ApiExceptionHandler`, a nie lokalnie w
  kontrolerze.
- Jesli pojawi sie potrzeba nowego wariantu analizy, najpierw sprawdz czy to
  nadal jest sync endpoint, czy raczej osobny flow w `job` albo pomocniczy
  endpoint adaptera.

## Testy

- Dla kontrolera preferuj `MockMvc`.
- Dla serwisu utrzymuj proste testy delegacji do wspolnej orkiestracji.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/03-runtime-flow.md`
