# Krok 11: Frontend I Workflow Operatora

## Cel

Zobaczyc system oczami operatora i zrozumiec, jakie dane frontend potrzebuje od
job flow.

## Po tym kroku rozumiesz

- dlaczego frontend bazuje glownie na job API,
- jak prezentowany jest progres i evidence,
- po co UI pokazuje prepared prompt i dane diagnostyczne.

## Model pracy operatora

1. operator wpisuje `correlationId`,
2. frontend startuje job,
3. frontend polluje status,
4. pokazuje kroki evidence, prompt i finalny wynik,
5. w razie potrzeby operator uzywa tez widoku `GET /evidence`.

## Co jest wazne dla backend developera

- `AnalysisJobResponse` jest projekcja dla UI, nie nowy model domenowy,
- frontend potrzebuje aktualnego kroku, historii krokow, evidence i bledow,
- prepared prompt jest przydatny diagnostycznie nawet wtedy, gdy sesja AI
  zawiedzie.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobResponse.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobState.java`
- `frontend/src/app`

## Sprawdz lokalnie

- uruchom `GET /`,
- przejdz przez widok analizy i evidence,
- zobacz, ktore pola pochodza z job snapshotu.

## Checkpoint

- Kiedy zmiana backendowa wymaga tez zmiany UI?
- Jakie dane sa diagnostycznie wazniejsze w job flow niz w sync response?
