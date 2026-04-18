# Krok 7: Pierwsza Analiza Bez AI

> Uwaga: ten krok ma juz charakter historyczny. Pozniejszy refaktor opisany w
> `13-evidence-provider-registry.md` usunal flow `rule-based` i zastapil go
> architektura `AnalysisEvidenceProvider` + AI-first flow.

W tym kroku endpoint przestaje zwracac samo techniczne `accepted` i zaczyna
oddawac pierwszy rzeczywisty wynik analizy.

Na razie analiza jest w pelni regulowa i syntetyczna.

## Po co robimy ten krok

Zanim podlaczymy Copilot SDK, MCP, GitLaba, Elastic i Dynatrace, chcemy
zrozumiec sam przeplyw biznesowy:

1. przychodzi request,
2. serwis wykonuje analize,
3. powstaje model wyniku,
4. klient dostaje diagnoze i rekomendacje.

To pozwala najpierw zbudowac szkielet rozwiazania, a dopiero potem podmienic
silnik analizy na bardziej zaawansowany.

## Co zmienilismy

- dodalismy `AnalysisResultResponse`,
- serwis zwraca teraz wynik analizy zamiast prostego `accepted`,
- dodalismy pierwsze reguly analizy bez AI,
- rozszerzylismy testy serwisu i kontrolera.

## Jak dzialaja reguly

Na razie to sa sztuczne scenariusze edukacyjne:

- `correlationId` zaczyna sie od `timeout-`
  wynik: `DOWNSTREAM_TIMEOUT`
- `correlationId` zaczyna sie od `db-lock-`
  wynik: `DATABASE_LOCK`
- `correlationId = not-found`
  wynik: wyjatek domenowy i `404`
- kazdy inny przypadek
  wynik: `UNKNOWN`

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisResultResponse.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisServiceTest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisControllerTest.java`

## Jaki wynik zwracamy

Przykladowy sukces:

```json
{
  "status": "COMPLETED",
  "correlationId": "timeout-123",
  "summary": "Synthetic analysis detected a probable timeout in downstream communication.",
  "detectedProblem": "DOWNSTREAM_TIMEOUT",
  "recommendedAction": "Check downstream latency, timeout configuration, and retry policy."
}
```

## Co uruchomic

```powershell
mvn test
```

Test reczny dla timeoutu:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{"correlationId":"timeout-123"}'
```

Test reczny dla braku dopasowania:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{"correlationId":"corr-123"}'
```

## Jak debugowac

Ustaw breakpoint w:

- `AnalysisService.analyze(...)`

Potem uruchom kilka scenariuszy i zobacz, ktora regula zostaje wybrana.

To jest wazny moment:
logika domenowa zaczyna juz podejmowac decyzje, a nie tylko przekazywac dane.

## Co warto zrozumiec po tym kroku

1. Po co zwracac model wyniku analizy jeszcze przed integracja z AI?
2. Dlaczego reguly syntetyczne sa dobrym etapem przejsciowym?
3. Co w przyszlosci bedzie latwo podmienic na Copilot SDK?

## Co dalej

Kolejny dobry krok to wydzielenie portow i adapterow dla zrodel danych, zeby
przygotowac aplikacje pod GitLaba, Elasticsearch i Dynatrace.
