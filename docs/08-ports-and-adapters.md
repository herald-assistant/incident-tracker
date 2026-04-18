# Krok 8: Porty I Adaptery

> Uwaga: ten krok opisuje snapshot-based wersje przejsciowa. Aktualny model
> rozszerzalny jest opisany w `13-evidence-provider-registry.md`.

W tym kroku przestajemy opierac analize bezposrednio na samym `correlationId`.
Zamiast tego serwis pobiera dane przez porty przygotowane pod przyszle systemy
zewnetrzne.

## Co dodalismy

- port `ElasticLogPort`,
- port `DynatraceTracePort`,
- port `GitLabChangePort`,
- syntetyczne adaptery dla Dynatrace i GitLaba oraz testowy fake dla Elastica,
- `DiagnosticDataSnapshot`,
- refaktor `AnalysisService`, aby analizowal zebrane dane zamiast samego
  identyfikatora.

## Po co robimy ten krok

Chcemy przygotowac architekture pod przyszle zrodla danych:

- Elasticsearch dla logow,
- Dynatrace dla trace i telemetrii,
- GitLab dla wskazowek o zmianach w kodzie.

Na razie nie laczymy sie jeszcze z prawdziwymi systemami.
Ale od teraz `AnalysisService` nie zalezy od konkretnej implementacji pobierania
danych.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/port/ElasticLogPort.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/port/DynatraceTracePort.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/port/GitLabChangePort.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/elasticsearch/TestElasticLogPort.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/SyntheticDynatraceTraceAdapter.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/SyntheticGitLabChangeAdapter.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/DiagnosticDataSnapshot.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisService.java`

## Jak teraz dziala przeplyw

1. kontroler przyjmuje request,
2. serwis pyta porty o dane diagnostyczne,
3. adaptery lub testowe fake'i zwracaja dane kontrolowane,
4. serwis sklada `DiagnosticDataSnapshot`,
5. analiza dziala na danych, a nie na samym `correlationId`.

## Jakie sa scenariusze syntetyczne

- `timeout-*`
  porty zwracaja dane wskazujace na timeout downstream
- `db-lock-*`
  porty zwracaja dane wskazujace na blokade bazy
- `not-found`
  wszystkie porty zwracaja pusty wynik
- inne wartosci
  porty zwracaja dane, ale bez znanego wzorca bledu

## Co uruchomic

```powershell
mvn test
```

Test reczny dla timeoutu:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{"correlationId":"timeout-123"}'
```

Test reczny dla blokady bazy:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{"correlationId":"db-lock-123"}'
```

## Jak debugowac

Ustaw breakpointy w:

- `AnalysisService.analyze(...)`
- `TestElasticLogPort.findLogEntries(...)`
- `SyntheticDynatraceTraceAdapter.findTraceEntries(...)`
- `SyntheticGitLabChangeAdapter.findChangeHints(...)`

Potem wyslij request i zobacz kolejnosc:

1. serwis wywoluje porty,
2. adaptery zwracaja dane,
3. serwis wybiera regule na podstawie snapshotu.

## Co warto zrozumiec po tym kroku

1. Czym rozni sie port od adaptera?
2. Dlaczego serwis powinien zalezec od interfejsow, a nie od konkretnej integracji?
3. Dlaczego to przygotowuje nas pod prawdziwe API GitLaba, Elasticsearch i Dynatrace?

## Co dalej

Kolejny krok to rozbudowanie atrap integracji tak, aby zwracaly bardziej
ustrukturyzowane dane zamiast prostych list tekstow.
