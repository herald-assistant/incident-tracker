# Krok 4: Entry Pointy I Flow Uzytkownika

## Cel

Zrozumiec, jak operator albo developer wchodzi do systemu i ktore endpointy sa
glowne, a ktore tylko pomocnicze.

## Po tym kroku rozumiesz

- roznice miedzy `GET /`, `POST /analysis` i job flow,
- po co istnieje `GET /evidence`,
- ktore endpointy sa czescia produktu, a ktore diagnostyki integracji.

## Glowne wejscia

### `GET /`

Frontend operatorski, ktory uruchamia job-based flow i pokazuje postep,
evidence, prompt i wynik.

### `POST /analysis`

Synchroniczny kontrakt API. Cienki wrapper na wspolny `AnalysisOrchestrator`.

### `POST /analysis/jobs`

Start asynchronicznej analizy dla UI.

### `GET /analysis/jobs/{analysisId}`

Polling statusu, krokow i wyniku.

## Wejscia pomocnicze

### `GET /evidence`

Widok diagnostyczny do recznego testowania helper endpointow adapterow.

### Helper endpointy adapterow

- `POST /api/elasticsearch/logs/search`
- `POST /api/gitlab/repository/search`
- `POST /api/gitlab/source/resolve`
- `POST /api/gitlab/source/resolve/preview`

To nie jest glowny flow analizy. To narzedzia dla developera i operatora.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/sync/AnalysisController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/elasticsearch/ElasticLogSearchController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRepositorySearchController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/source/GitLabSourceResolveController.java`

## Sprawdz lokalnie

- otworz `GET /`,
- otworz `GET /evidence`,
- porownaj, jakie dane zwraca `POST /analysis` i `GET /analysis/jobs/{analysisId}`.

## Checkpoint

- Dlaczego UI korzysta glownie z job flow zamiast z `POST /analysis`?
- Ktore endpointy mozna zmieniac tylko wtedy, gdy zmienia sie realna potrzeba
  operatorska?
