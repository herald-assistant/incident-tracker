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
evidence, prompt, wynik oraz import/eksport zakonczonej analizy.

### `POST /analysis`

Synchroniczny kontrakt API. Cienki wrapper na wspolny `AnalysisOrchestrator`.

### `POST /analysis/jobs`

Start asynchronicznej analizy dla UI. Request zawiera `correlationId` oraz
opcjonalne preferencje AI (`model`, `reasoningEffort`), ktore nie zmieniaja
scope'u evidence.

### `GET /analysis/jobs/{analysisId}`

Polling statusu, krokow, prepared promptu, evidence i wyniku.

## Wejscia pomocnicze

### `GET /evidence`

Widok diagnostyczny do recznego testowania helper endpointow adapterow.
To route Angulara forwardowana przez Spring Boot do `index.html`.

### Helper endpointy adapterow

- `POST /api/elasticsearch/logs/search`
- `POST /api/gitlab/repository/search`
- `POST /api/gitlab/source/resolve`
- `POST /api/gitlab/source/resolve/preview`

To nie jest glowny flow analizy. To narzedzia dla developera i operatora.
Nie ma osobnego helper endpointu Database capability.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/sync/AnalysisController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/elasticsearch/ElasticLogSearchController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRepositorySearchController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/source/GitLabSourceResolveController.java`

## Sprawdz lokalnie

- otworz `GET /`,
- otworz `GET /evidence`,
- porownaj, jakie dane zwraca `POST /analysis` i `GET /analysis/jobs/{analysisId}`,
- zobacz, ze job snapshot zwraca tez `preparedPrompt` i `toolEvidenceSections`.

## Checkpoint

- Dlaczego UI korzysta glownie z job flow zamiast z `POST /analysis`?
- Ktore endpointy mozna zmieniac tylko wtedy, gdy zmienia sie realna potrzeba
  operatorska?
- Ktore dane sa tylko projekcja UI joba, a nie osobnym modelem domenowym?
