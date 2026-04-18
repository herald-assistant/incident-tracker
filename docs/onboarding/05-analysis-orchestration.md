# Krok 5: Orkiestracja Analizy

## Cel

Zrozumiec, jak system sklada caly runtime flow od `correlationId` do wyniku.

## Po tym kroku rozumiesz

- role `AnalysisOrchestrator`,
- jak sync i job reuse'uja ten sam flow,
- gdzie wpiete sa listenery progresu,
- gdzie budowany jest request do AI.

## Glowna sekwencja

1. kontroler przyjmuje `AnalysisRequest`,
2. `sync` albo `job` deleguje do `AnalysisOrchestrator`,
3. orchestrator uruchamia `AnalysisEvidenceCollector`,
4. po zebraniu danych buduje `AnalysisAiAnalysisRequest`,
5. provider AI przygotowuje prompt i wykonuje analize,
6. orchestrator mapuje wynik na `AnalysisResultResponse`.

## Najwazniejsze klasy

- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisOrchestrator.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisExecution.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisExecutionListener.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/sync/AnalysisService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobService.java`

## Na co zwrocic uwage

- `AnalysisRequest` przyjmuje tylko `correlationId`,
- `gitLabGroup` pochodzi z konfiguracji, nie z requestu,
- `environment` i `gitLabBranch` sa rozwiazywane z evidence,
- job flow nie ma osobnego orchestratora, tylko projekcje postepu.

## Sprawdz lokalnie

- przejdz przez `AnalysisOrchestrator.analyze(...)`,
- porownaj, jak `sync` i `job` wchodza do tego samego flow,
- zobacz, gdzie job zapisuje `preparedPrompt`.

## Checkpoint

- Dlaczego `AnalysisJobService` nie powinien miec osobnej logiki analizy?
- W ktorym miejscu najlepiej dodać nowy listener progresu?
