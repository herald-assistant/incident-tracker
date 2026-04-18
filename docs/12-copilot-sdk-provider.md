# Krok 12: Provider AI Oparty O Copilot SDK

W tym kroku podpinamy prawdziwa implementacje providera AI pod nasz interfejs
`AnalysisAiProvider`, ale nadal nie mieszamy tego z endpointem `/analysis`.

## Cel tego kroku

Do tej pory mielismy:

- kontrakt providera AI,
- warstwe przygotowujaca obiekty `copilot-sdk-java`.

Teraz skladamy to w jeden adapter, ktory:

1. przyjmuje nasz model domenowy,
2. przygotowuje obiekty SDK,
3. wywoluje Copilot SDK przez osobna warstwe wykonania,
4. mapuje wynik z powrotem na nasz model domenowy analizy AI.

## Co dodalismy

- `CopilotSdkAnalysisAiProvider`
- `CopilotSdkExecutionGateway`
- `CopilotSdkInvocationException`

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSdkAnalysisAiProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/execution/CopilotSdkExecutionGateway.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/execution/CopilotSdkInvocationException.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSdkAnalysisAiProviderTest.java`

## Jak to jest zorganizowane

### Warstwa domenowa

`AnalysisAiProvider`

To jest kontrakt, o ktorym wie reszta aplikacji.

### Warstwa przygotowania SDK

`CopilotSdkPreparationService`

Buduje:

- `CopilotClientOptions`
- `SessionConfig`
- `MessageOptions`

### Warstwa wykonania SDK

`CopilotSdkExecutionGateway`

To osobna granica odpowiedzialnosci, ktora:

- startuje `CopilotClient`
- tworzy `CopilotSession`
- wysyla prompt
- odbiera odpowiedz
- uzywa konfigurowalnego timeoutu `analysis.ai.copilot.send-and-wait-timeout`
  dla `sendAndWait`, domyslnie `5m`

### Warstwa adaptera

`CopilotSdkAnalysisAiProvider`

Spina wszystko razem i mapuje wynik z SDK na
`AnalysisAiAnalysisResponse`.

## Dlaczego to rozdzielamy

Bo teraz osobno mozemy testowac:

- budowanie obiektow SDK,
- wykonanie wywolania SDK,
- mapowanie na nasz model domenowy.

Dodatkowo provider nie zalezy juz od konkretnych klas typu
`ElasticLogEntry` czy `DynatraceTraceRecord`.
Na granicy AI dostaje generyczne sekcje evidence, co ulatwi dokladanie nowych
zrodel danych bez zmiany kontraktu providera.

To dobrze wspolgra z architektura `AnalysisEvidenceProvider`, gdzie kazde zrodlo
samo przygotowuje swoja sekcje evidence, a provider AI pozostaje od tego
oddzielony.

To jest o wiele czystsze niz jeden duzy serwis robiacy wszystko naraz.

## Jak wybieramy providera

Obecnie runtime ma jeden wspierany provider AI:

- `CopilotSdkAnalysisAiProvider`

Nie utrzymujemy juz produkcyjnego `stub` providera. W testach jednostkowych
mozemy dalej uzywac lekkich fake'ow testowych, ale runtime aplikacji zawsze
korzysta z GitHub Copilot SDK.

## Co jest wazne w tym kroku

Nadal:

- nie podpinamy providera do glownego endpointu `/analysis`
- nie wymagamy, zeby testy odpalaly prawdziwe CLI
- nie robimy jeszcze pelnego scenariusza end-to-end z Copilotem

Zmienil sie tylko poziom dojrzalosci architektury:
provider Copilota jest juz gotowy jako osobna implementacja.

## Jak testowac

```powershell
mvn test
```

Szczegolnie wazny test:

- `CopilotSdkAnalysisAiProviderTest`

Pokazuje on, ze:

1. provider bierze nasz request domenowy,
2. korzysta z przygotowania SDK,
3. wywoluje gateway,
4. mapuje wynik na `AnalysisAiAnalysisResponse`

## Jak debugowac

Ustaw breakpointy w:

- `CopilotSdkAnalysisAiProvider.analyze(...)`
- `CopilotSdkPreparationService.prepare(...)`
- `CopilotSdkExecutionGateway.execute(...)`

Na razie najlatwiej debugowac test providera i sam przeplyw obiektow.

## Co warto zrozumiec po tym kroku

1. Czym rozni sie kontrakt providera od adaptera SDK?
2. Po co osobno mamy `ExecutionGateway`?
3. Dlaczego wybieranie providera przez property jest wygodne?

## Co dalej

Nastepny krok to pierwszy kontrolowany eksperyment z realnym uruchomieniem
`CopilotClient` i `CopilotSession`, jesli srodowisko ma dostep do CLI i
konfiguracji autoryzacji.
