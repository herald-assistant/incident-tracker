# Krok 3: Walidacja `correlationId`

W tym kroku uczymy sie, jak Spring waliduje `request body`.

Naszym celem jest:

- zaakceptowac poprawny request,
- odrzucic request bez `correlationId`,
- odrzucic request z pustym `correlationId`.

## Co zmienilismy

- dodalismy `spring-boot-starter-validation`,
- oznaczylismy pole `correlationId` jako `@NotBlank`,
- dodalismy `@Valid` przy `@RequestBody`,
- dopisalismy testy dla przypadkow blednych.

## Gdzie patrzec w kodzie

- `pom.xml`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisRequest.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisController.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisControllerTest.java`

## Co uruchomic

### 1. Testy

```powershell
mvn test
```

Wazne przypadki:

- poprawne `correlationId` -> `202 Accepted`
- brak `correlationId` -> `400 Bad Request`
- puste `correlationId` -> `400 Bad Request`

### 2. Test reczny

Uruchom aplikacje:

```powershell
java -jar target/incident-tracker-0.0.1-SNAPSHOT.jar --server.port=8081
```

Poprawny request:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{"correlationId":"corr-123"}'
```

Brak `correlationId`:

```powershell
Invoke-WebRequest -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{}' -SkipHttpErrorCheck
```

Puste `correlationId`:

```powershell
Invoke-WebRequest -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{"correlationId":"   "}' -SkipHttpErrorCheck
```

## Jak to dziala

1. Jackson zamienia JSON na `AnalysisRequest`.
2. Spring uruchamia walidacje obiektu przez `@Valid`.
3. `@NotBlank` sprawdza, czy `correlationId` nie jest `null`, puste albo biale.
4. Jesli walidacja nie przejdzie, Spring zwraca `400 Bad Request`.

## Jak debugowac

Ustaw breakpoint w:

- `AnalysisController.analyze(...)`

Potem:

- wyslij poprawny request i zobacz, ze breakpoint zostanie osiagniety,
- wyslij niepoprawny request i zobacz, ze metoda kontrolera w ogole nie zostanie wywolana.

To jest bardzo wazna obserwacja:
walidacja dzieje sie zanim Twoja logika biznesowa zacznie pracowac.

## Co warto zrozumiec po tym kroku

1. Po co jest `spring-boot-starter-validation`?
2. Jaka jest roznica miedzy `@RequestBody` i `@Valid @RequestBody`?
3. Co dokladnie sprawdza `@NotBlank`?
4. Dlaczego dla niepoprawnego requestu kontroler nie wykonuje swojej logiki?

## Co dalej

Kolejny krok to uporzadkowanie kontraktu API tak, aby mozna bylo bezpiecznie
dodawac kolejne pola do requestu analizy.
