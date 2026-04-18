# Krok 4: Kontrakt Bledow API

W tym kroku przestajemy polegac na domyslnej odpowiedzi Springa dla bledow
walidacji i wprowadzamy nasz wlasny kontrakt odpowiedzi.

## Po co robimy ten krok

Domyslne `400 Bad Request` od Springa jest poprawne technicznie, ale malo wygodne
dla klienta API. Chcemy zwracac blad w przewidywalnym formacie, ktory latwo
parsowac i testowac.

## Co zmienilismy

- dodalismy `ApiErrorResponse`,
- dodalismy `ApiFieldError`,
- dodalismy `ApiExceptionHandler` z `@RestControllerAdvice`,
- zaktualizowalismy testy, aby sprawdzaly tresc bledu.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/api/ApiErrorResponse.java`
- `src/main/java/pl/mkn/incidenttracker/api/ApiFieldError.java`
- `src/main/java/pl/mkn/incidenttracker/api/ApiExceptionHandler.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisControllerTest.java`

## Jaki blad zwracamy teraz

Dla niepoprawnego requestu odpowiedz ma postac:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "fieldErrors": [
    {
      "field": "correlationId",
      "message": "correlationId must not be blank"
    }
  ]
}
```

## Jak to dziala

1. Walidacja rzuca `MethodArgumentNotValidException`.
2. `@RestControllerAdvice` przechwytuje ten wyjatek.
3. Handler buduje nasze DTO odpowiedzi bledu.
4. Klient dostaje stabilny JSON zamiast domyslnej odpowiedzi frameworka.

## Co uruchomic

```powershell
mvn test
```

Potem recznie:

```powershell
Invoke-WebRequest -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{}' -SkipHttpErrorCheck
```

## Jak debugowac

Ustaw breakpoint w:

- `ApiExceptionHandler.handleMethodArgumentNotValid(...)`

Potem wyslij niepoprawny request i zobacz:

- jaki wyjatek trafia do handlera,
- jakie bledy walidacji sa w `BindingResult`,
- jak skladany jest finalny JSON odpowiedzi.

## Co warto zrozumiec po tym kroku

1. Czym rozni sie walidacja od obslugi wyjatku?
2. Po co `@RestControllerAdvice`, skoro Spring juz zwraca `400`?
3. Dlaczego wlasny kontrakt bledu jest lepszy dla klienta API?

## Co dalej

Kolejny dobry krok to wydzielenie warstwy serwisowej, aby kontroler przestal
zawierac logike nawet tak prosta jak teraz.
