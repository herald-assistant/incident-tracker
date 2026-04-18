# Krok 9: Ustrukturyzowane Atrapy Integracji

> Uwaga: ten krok opisuje stan przejsciowy oparty o `DiagnosticDataSnapshot`.
> Aktualna architektura evidence jest opisana w `13-evidence-provider-registry.md`.

W tym kroku rozwijamy atrapy integracji tak, aby zwracaly nie tylko teksty, ale
bogatsze modele danych.

## Co zmienilismy

- dodalismy `ElasticLogEntry`,
- dodalismy `DynatraceTraceRecord`,
- dodalismy `GitLabChangeHint`,
- porty zwracaja teraz listy ustrukturyzowanych rekordow,
- adaptery syntetyczne i testowe fake'i buduja bardziej realistyczne dane,
- `AnalysisService` podejmuje decyzje na podstawie pol, a nie przeszukiwania
  przypadkowych stringow.

## Po co robimy ten krok

Prawdziwe integracje z Elasticsearch, Dynatrace i GitLabem nie beda zwracaly
jednego stringa na rekord. Beda mialy pola takie jak:

- `serviceName`,
- `level`,
- `durationMs`,
- flagi typu `timeoutDetected`,
- informacje o projekcie i pliku w GitLabie.

Im szybciej zaczniemy pracowac na takich modelach, tym mniej refaktoru bedzie
pozniej przy podpinaniu prawdziwych API.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/ElasticLogEntry.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/DynatraceTraceRecord.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/GitLabChangeHint.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/DiagnosticDataSnapshot.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisService.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/elasticsearch/TestElasticLogPort.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/SyntheticDynatraceTraceAdapter.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/SyntheticGitLabChangeAdapter.java`

## Jak teraz dziala analiza

### Timeout

Serwis wykrywa timeout na podstawie:

- logu z poziomem `ERROR` i komunikatem o timeout,
- albo flagi `timeoutDetected` w rekordzie z Dynatrace.

### Database lock

Serwis wykrywa blokade bazy na podstawie:

- logu z poziomem `ERROR` i komunikatem o deadlock,
- albo flagi `databaseLockDetected` w rekordzie z Dynatrace.

### GitLab

Serwis bierze pierwszy hint z GitLaba i wlacza go do podsumowania wyniku.

## Jak testowac

```powershell
mvn test
```

Szczegolnie warto spojrzec na:

- `AnalysisServiceTest`
- `SyntheticAdaptersTest`

## Jak debugowac

Ustaw breakpointy w:

- `TestElasticLogPort.findLogEntries(...)`
- `SyntheticDynatraceTraceAdapter.findTraceEntries(...)`
- `SyntheticGitLabChangeAdapter.findChangeHints(...)`
- `AnalysisService.analyze(...)`

Potem przejdz request `timeout-123` i zobacz:

1. jakie obiekty zwracaja adaptery,
2. jakie pola trafiaja do `DiagnosticDataSnapshot`,
3. jak serwis wybiera regule na podstawie struktury danych.

## Co warto zrozumiec po tym kroku

1. Dlaczego model danych jest lepszy od surowego tekstu?
2. Ktore pola z tych rekordow prawdopodobnie zostana tez w prawdziwych integracjach?
3. Jak ta zmiana zmniejsza ryzyko kruchej logiki opartej o dopasowanie tekstu?

## Co dalej

Kolejny dobry krok to zrobienie pierwszego bardzo malego kontraktu providera AI,
zeby przygotowac miejsce na Copilot SDK bez podlaczania go jeszcze do glownego flow.
