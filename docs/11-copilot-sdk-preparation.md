# Krok 11: Pierwszy Kontakt Z GitHub Copilot Java SDK

W tym kroku po raz pierwszy weszlismy w prawdziwe klasy z `copilot-sdk-java`,
ale jeszcze bez uruchamiania CLI i bez podpinania tej warstwy do endpointu
`/analysis`.

## Cel tego kroku

Celem nie bylo jeszcze "odpalic Copilota", tylko:

- zobaczyc, jak wygladaja realne typy z SDK,
- przygotowac konfiguracje klienta i sesji w naszej aplikacji,
- nauczyc sie, jak evidence naszej analizy zamienia sie na prompt dla SDK,
- zrobic to w bezpiecznej, izolowanej warstwie bez psucia glownego flow.

To jest bardzo wazny etap przejsciowy.

Gdybysmy od razu zaczeli uruchamiac klienta Copilota w srodku glownej logiki,
to naraz mieszałyby sie:

- konfiguracja SDK,
- prompt engineering,
- logika domenowa,
- problemy srodowiskowe typu brak CLI albo brak logowania.

Tutaj celowo rozdzielamy te rzeczy.

## Co dokladnie zrobilismy

Dodane zostaly klasy:

- `CopilotSdkProperties`
- `CopilotSdkPreparationService`
- `CopilotSdkPreparedRequest`

W tej warstwie korzystamy juz z prawdziwych klas SDK:

- `CopilotClientOptions`
- `SessionConfig`
- `MessageOptions`

Czyli to nie jest juz "udawanie" API Copilota po naszej stronie. To jest realne
przygotowanie obiektow, ktore pozniej beda mogly trafic do `CopilotClient`.

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSdkProperties.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSdkPreparationService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSdkPreparedRequest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSdkPreparationServiceTest.java`
- `src/main/resources/application.properties`

## Co ten kod robi teraz

`CopilotSdkPreparationService` bierze nasze wejscie:

- `correlationId`
- `evidenceSections`

i zamienia je na trzy obiekty SDK:

### 1. `CopilotClientOptions`

Tu przygotowujemy konfiguracje klienta, np.:

- `cliPath`
- `cwd`
- `githubToken`
- `useLoggedInUser`

### 2. `SessionConfig`

Tu przygotowujemy konfiguracje sesji, np.:

- `clientName`
- `workingDirectory`
- `model`
- `reasoningEffort`
- `streaming`

### 3. `MessageOptions`

Tu budujemy prompt, ktory w przyszlosci zostanie wyslany do Copilota.

## Dlaczego to ma sens juz teraz

Bo dzieki temu mozemy osobno zrozumiec:

1. co pochodzi z naszej domeny,
2. co jest konfiguracja SDK,
3. co jest promptem do modelu.

To bardzo obniza ryzyko kolejnego kroku.

Gdy bedziemy podlaczac prawdziwy `CopilotClient`, nie bedziemy juz zastanawiac sie:

- skad wziac dane do promptu,
- jak budowac `SessionConfig`,
- gdzie trzymac `CopilotClientOptions`.

To juz bedzie gotowe.

## Jak ustawiamy autoryzacje

Warstwa przygotowania wspiera teraz dwa tryby:

### 1. PAT w properties

Mozesz podac token w:

```properties
analysis.ai.copilot.github-token=ghp_xxx
```

Zakladamy, ze jest to PAT z uprawnieniem `Copilot Requests`.

Wtedy:

- token trafia do `CopilotClientOptions`
- `useLoggedInUser` jest ustawiane na `false`

### 2. Fallback do logged-in user

Jesli `analysis.ai.copilot.github-token` nie jest ustawione, to:

- nie przekazujemy tokenu do klienta
- `useLoggedInUser` jest ustawiane na `true`

To daje prosty i przewidywalny model:

- jest token -> uzyj tokenu
- nie ma tokenu -> uzyj zalogowanego uzytkownika

## Jak zamierzamy to pozniej wykorzystac

W kolejnym etapie chcemy dojsc do sytuacji:

1. `AnalysisEvidenceCollector` zbierze sekcje evidence od wszystkich providerow
2. glowny serwis lub nowy adapter Copilota zbuduje
   `AnalysisAiAnalysisRequest`
3. `CopilotSdkPreparationService` przygotuje:
   - `CopilotClientOptions`
   - `SessionConfig`
   - `MessageOptions`
4. adapter Copilot SDK utworzy `CopilotClient`
5. adapter otworzy sesje `CopilotSession`
6. adapter wysle prompt do modelu
7. odpowiedz SDK zostanie zamieniona na nasz
   `AnalysisAiAnalysisResponse`

Czyli ten krok jest warstwa przygotowawcza miedzy:

- naszym modelem domenowym
- a realnym uruchomieniem Copilot SDK

## Czego jeszcze tu nie robimy

Na razie:

- nie startujemy `CopilotClient`
- nie tworzymy realnej sesji
- nie wysylamy promptu do modelu
- nie wymagamy lokalnie zainstalowanego CLI Copilota
- nie podpinamy tej warstwy do endpointu `/analysis`

To jest celowe.

## Jak testowac

```powershell
mvn test
```

Najwazniejszy test:

- `CopilotSdkPreparationServiceTest`

On potwierdza, ze:

- poprawnie skladamy `CopilotClientOptions`
- poprawnie skladamy `SessionConfig`
- poprawnie skladamy `MessageOptions`
- poprawnie wybieramy PAT albo fallback do logged-in user
- prompt zawiera generyczne sekcje evidence zamiast twardego kodowania provider-specific klas

## Jak debugowac

Ustaw breakpointy w:

- `CopilotSdkPreparationService.prepare(...)`
- `CopilotSdkPreparationService.buildPrompt(...)`

Potem uruchom test:

- `CopilotSdkPreparationServiceTest`

I zobacz:

1. jakie dane wchodza z warstwy domenowej,
2. jak zamieniaja sie na obiekty SDK,
3. jak skladany jest prompt.

## Co warto zrozumiec po tym kroku

1. Dlaczego nie laczymy od razu warstwy domenowej z `CopilotClient`?
2. Po co osobno trzymac `CopilotClientOptions`, `SessionConfig` i `MessageOptions`?
3. Dlaczego budowanie promptu warto testowac zanim uruchomimy prawdziwy model?

## Co dalej

Nastepny krok to:

- pierwszy realny adapter Copilot SDK,
- utworzenie `CopilotClient`,
- przygotowanie sesji,
- i dopiero potem pierwszy kontrolowany eksperyment z wywolaniem SDK w izolacji.
