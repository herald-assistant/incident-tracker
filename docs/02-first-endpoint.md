# Krok 2: Pierwszy Endpoint REST

W tym kroku dodajemy pierwszy endpoint HTTP:

- `POST /analysis`

Na razie jego zachowanie jest celowo proste:

- przyjmuje JSON request body z `correlationId`,
- zwraca status `202 Accepted`,
- oddaje JSON z prostym potwierdzeniem.

## Po co robimy ten krok

Chcemy zrozumiec podstawowy przeplyw:

1. klient wysyla request HTTP,
2. Spring mapuje JSON do obiektu Java,
3. kontroler zwraca obiekt Java,
4. Spring serializuje go do JSON.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisController.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisRequest.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisAcceptedResponse.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/AnalysisControllerTest.java`

## Jak przetestowac automatycznie

```powershell
mvn test
```

Szczegolnie interesuje nas test:

- `AnalysisControllerTest`

## Jak przetestowac recznie

Najpierw uruchom aplikacje:

```powershell
java -jar target/incident-tracker-0.0.1-SNAPSHOT.jar --server.port=8081
```

Potem wyslij request:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/analysis -ContentType 'application/json' -Body '{"correlationId":"corr-123"}'
```

Oczekiwany wynik:

```json
{
  "status": "accepted",
  "correlationId": "corr-123"
}
```

## Jak debugowac

Ustaw breakpoint w metodzie:

`AnalysisController.analyze(...)`

Uruchom test `AnalysisControllerTest` albo wyslij request recznie.

W debugerze zobaczysz:

- obiekt `AnalysisRequest`,
- wejscie do metody kontrolera,
- obiekt odpowiedzi przed serializacja do JSON.

## Co warto zrozumiec po tym kroku

1. Co robi `@RestController`?
2. Co robi `@PostMapping("/analysis")`?
3. Skad Spring wie, ze ma zamienic JSON na `AnalysisRequest`?
4. Dlaczego zwracamy `202 Accepted`, a nie `200 OK`?
5. Jak `record` zamienia sie na JSON?

## Co dalej

W kolejnym kroku mozemy:

- dopracowac obsluge braku `correlationId`,
- wprowadzic bardziej formalny kontrakt API,
- albo wydzielic serwis, jesli bedziesz chcial juz rozdzielac odpowiedzialnosci.
