# Krok 6: Blad Domenowy I Mapowanie HTTP

W tym kroku rozniamy dwa typy problemow:

- blad walidacji requestu,
- blad domenowy z logiki aplikacji.

## Co dodalismy

- `AnalysisDataNotFoundException`,
- mapowanie tego wyjatku na `404 Not Found`,
- test serwisu dla wyjatku domenowego,
- test kontrolera dla odpowiedzi HTTP z tego scenariusza.

## Po co robimy ten krok

Do tej pory umielismy obsluzyc niepoprawne dane wejsciowe.
Teraz uczymy sie sytuacji, w ktorej request jest poprawny, ale aplikacja nie moze
wykonac operacji biznesowej.

To jest inny rodzaj bledu niz walidacja.

## Jak dziala scenariusz edukacyjny

Na razie nie mamy jeszcze prawdziwych integracji z logami, trace ani zewnetrznymi
systemami.

Dlatego wprowadzamy mala, sztuczna regule:

- jesli `correlationId = "not-found"`, serwis rzuca
  `AnalysisDataNotFoundException`.

To tylko tymczasowy mechanizm dydaktyczny, ktory pozniej zastapimy realnym
sprawdzeniem danych.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisDataNotFoundException.java`
- `src/main/java/pl/mkn/incidenttracker/api/ApiExceptionHandler.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisServiceTest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisControllerTest.java`

## Jaki blad zwracamy teraz

```json
{
  "code": "ANALYSIS_DATA_NOT_FOUND",
  "message": "No diagnostic data found for correlationId: not-found",
  "fieldErrors": []
}
```

## Co uruchomic

```powershell
mvn test
```

Test reczny:

```powershell
Invoke-WebRequest -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{"correlationId":"not-found"}' -SkipHttpErrorCheck
```

## Jak debugowac

Ustaw breakpointy w:

- `AnalysisService.analyze(...)`
- `ApiExceptionHandler.handleAnalysisDataNotFound(...)`

Potem wyslij request z `correlationId = "not-found"` i zobacz:

1. request przechodzi walidacje,
2. kontroler wywoluje serwis,
3. serwis rzuca wyjatek domenowy,
4. handler mapuje go na `404`.

## Co warto zrozumiec po tym kroku

1. Czym rozni sie blad walidacji od bledu biznesowego?
2. Dlaczego poprawny request moze skonczyc sie `404`?
3. Po co mapowac wyjatki domenowe na konkretne statusy HTTP?

## Co dalej

Kolejny sensowny krok to wprowadzenie logowania `correlationId` do logow tej
aplikacji, zeby zaczac budowac obserwowalnosc po stronie naszego systemu
analitycznego.
