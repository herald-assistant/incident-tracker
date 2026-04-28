# Key Decisions

Ten dokument zbiera decyzje architektoniczne, ktore sa wazne przy
utrzymaniu flow analizy incydentu i integracji AI.

## 1. Publiczny request analizy pozostaje minimalny

`POST /analysis` przyjmuje tylko `correlationId`. `POST /analysis/jobs` dla UI
przyjmuje `correlationId` oraz opcjonalne preferencje wykonania AI:
`model` i `reasoningEffort`.

Lista dostepnych modeli dla UI pochodzi z `GET /analysis/ai/options`.
Endpoint mapuje metadane Copilot SDK na generyczny kontrakt aplikacji i zwraca
`reasoningEffort` tylko tam, gdzie SDK wystawia support albo domyslna wartosc
dla danego modelu.

Runtime nie przywraca `branch`, `environment`, `gitLabGroup` ani innych pol
sterujacych evidence scope'em do publicznego requestu.

Konsekwencje:

- `environment` jest wyprowadzany z evidence, przede wszystkim z logow
  Elasticsearch i deployment context.
- `gitLabBranch` jest wyprowadzany z evidence deployment/runtime.
- `gitLabGroup` pochodzi z konfiguracji aplikacji.
- uzytkownik nie moze recznie przesterowac zakresu GitLaba albo DB przez
  publiczne API analizy.
- wybor modelu i `reasoningEffort` dotyczy tylko konfiguracji sesji AI, nie
  zmienia deterministycznie zbieranego evidence ani ukrytych scope'ow tools.
- frontend nie hardcoduje mozliwosci modeli; backend pozostaje source of truth
  i moze uzyc fallbacku do skonfigurowanych domyslow, gdy SDK chwilowo nie
  zwroci katalogu.

## 2. Flow pozostaje AI-first

Evidence pipeline zbiera deterministyczny material, ale diagnoza i
rekomendacja sa wynikiem providera AI. Nie przenosimy diagnozowania do
centralnego rule engine.

Heurystyki sa dozwolone tylko jako:

- deterministyczne wzbogacanie `AnalysisContext`,
- ocena coverage i luk evidence,
- polityka dostepu do tools,
- walidacja shape/jakosci odpowiedzi AI,
- telemetryka i audyt.

Heurystyki nie powinny zastapic modelu w budowaniu diagnozy biznesowej.

## 3. Evidence pipeline jest deterministyczny na `AnalysisContext`

Kolejne kroki evidence providerow czytaja i aktualizuja `AnalysisContext`.
Po resolved deployment context kroki Dynatrace i GitLab deterministic moga
dzialac rownolegle z tego samego snapshotu contextu.

Provider evidence zwraca `AnalysisEvidenceSection`. AI layer nie powinien
czytac DTO adapterow bezposrednio.

## 4. GitLab ma trzy osobne capability

GitLab w systemie nie jest jedna abstrakcja:

- adapter i source resolve do ogolnego dostepu do GitLaba,
- deterministic evidence provider do deployment context/code references,
- AI-guided tools do dociagania kodu w sesji Copilota.

Te role nie powinny byc mieszane. Deterministic evidence ma przygotowac
najlepszy snapshot przed AI, a tools sa tylko do uzupelniania luk.

## 5. Skills Copilota sa runtime resource

Skille Copilota sa pakowane z aplikacja z `src/main/resources/copilot/skills`.
Nie traktujemy ich jako plikow `.github` repozytorium hosta.

Skill przechowuje stale zasady pracy modelu. Dane konkretnego incydentu
niesie prompt i artefakty przygotowane w runtime.

## 6. Granica AI pozostaje generyczna

Kontrakt wejscia do AI to `AnalysisAiAnalysisRequest` i lista
`AnalysisEvidenceSection`. Prompt builder i provider AI nie przyjmuja klas
adapter-specific.

Jesli AI layer potrzebuje typowego widoku evidence, powinien uzyc helperow
widoku nad generycznymi `AnalysisEvidenceSection`, np. widokow dla logow,
runtime signals albo resolved code evidence.

## 7. Prepared analysis jest budowane raz

Orchestrator nie buduje juz promptu debugowego osobno od requestu
wykonywanego przez AI.

Aktualny flow:

1. orchestrator buduje `AnalysisAiAnalysisRequest`,
2. wywoluje `AnalysisAiProvider.prepare(request)`,
3. zapisuje `prepared.prompt()` w stanie joba,
4. wykonuje `AnalysisAiProvider.analyze(prepared, listener)`,
5. zamyka `AnalysisAiPreparedAnalysis` w `finally`/try-with-resources.

Ownership prepared analysis jest jawny:

- wlasciciel obiektu zwroconego z `prepare(request)` zamyka go po uzyciu,
- `analyze(request)` zamyka prepared analysis, ktore sam przygotowal,
- `analyze(prepared, listener)` nie zamyka prepared analysis przekazanego
  przez caller,
- gateway wykonujacy SDK nie przejmuje ownership i nie zamyka prepared
  requestu.

`CopilotSdkPreparedRequest` implementuje generyczny
`AnalysisAiPreparedAnalysis`, ale typ SDK nie wycieka poza pakiet
`analysis.ai.copilot`.

`AnalysisAiProvider` nie ma produkcyjnych shortcutow dodanych tylko dla
testow, takich jak oddzielne `preparePrompt(...)` albo domyslne prepared
adaptery. Testy tworza wlasne prepared fixtures.

## 8. Artefakty Copilota sa inline w promptcie

Aktualny runtime nie uzywa SDK attachments jako zrodla evidence. Artefakty
incydentu sa renderowane jako logiczne pliki i osadzane inline w promptcie.
`MessageOptions` dostaje finalny prompt przez `setPrompt(prompt)`.

Nie zakladamy lokalnych sciezek plikowych dla artefaktow. Zmiana delivery
mode na SDK attachments bylaby jawna zmiana runtime wymagajaca testow,
dokumentacji i planu rollbacku.

## 9. Manifest i digest sa pierwszymi artefaktami

Kolejnosc artefaktow Copilota zaczyna sie od:

1. `00-incident-manifest.json`
2. `01-incident-digest.md`
3. artefakty raw evidence

Manifest zawiera indeks artefaktow, polityke tools, coverage report i
deklaruje `deliveryMode=embedded-prompt`. Digest kompresuje najwazniejsze
fakty sesji, logi, deployment/runtime, code highlights i znane luki evidence.

`AnalysisEvidenceItem` nie dostal publicznego pola `itemId`. Stabilne
`itemId` sa generowane tylko podczas renderowania artefaktow Copilota i
pojawiaja sie w manifest, JSON artifacts i markdown artifacts.

## 10. Kontrakt odpowiedzi AI jest JSON-only

Copilot ma zwracac tylko poprawny JSON, bez Markdown fence i bez prozy poza
JSON. Parser obsluguje:

- caly content jako JSON,
- fenced JSON block jako tolerancje dla modelu,
- fallback strukturalny, gdy wymaganych pol nie da sie sparsowac.

Legacy labeled response parser zostal usuniety. Brak wymaganych pol powoduje
fallback z `detectedProblem=AI_UNSTRUCTURED_RESPONSE`, ale pola juz
sparsowane z JSON sa zachowywane.

Wymagane pola dla obecnego publicznego response to:

- `detectedProblem`
- `summary`
- `recommendedAction`
- `affectedFunction`

JSON niesie tez pola pomocnicze: `rationale`, `affectedProcess`,
`affectedBoundedContext`, `affectedTeam`, `confidence`,
`evidenceReferences` i `visibilityLimits`.

## 11. Quality gate jest report-only

`CopilotResponseQualityGate` sprawdza uzytecznosc i ugruntowanie wyniku:

- zbyt plytkie `affectedFunction`,
- generyczne `recommendedAction`,
- data issue bez DB evidence albo visibility limit,
- ownership/context bez evidence,
- `confidence=high` przy slabym evidence,
- rationale bez rozdzielenia faktow, hipotez i ograniczen.

Domyslny tryb to `REPORT_ONLY`. Findings ida do logow/telemetryki, ale nie
zmieniaja runtime result.

## 12. Tool policy jest coverage-aware

Nie uzywamy juz zasady "sekcja GitLab/Elasticsearch istnieje, wiec wylacz
tools". `CopilotEvidenceCoverageEvaluator` ocenia coverage generycznych
evidence i tworzy `CopilotEvidenceCoverageReport`.

`CopilotToolAccessPolicyFactory` jest jedynym produkcyjnym miejscem, ktore
laczy request, evaluator coverage i zarejestrowane tool definitions. Sama
`CopilotToolAccessPolicy` jest budowana z gotowego coverage reportu i nie
tworzy recznie nowego evaluatora.

Polityka:

- Elasticsearch tools sa wlaczane przy braku logow, truncation albo braku
  stacktrace.
- GitLab tools sa wlaczane przy braku code evidence albo gdy jest tylko
  symbol, stack frame, failing method lub brakuje flow context.
- Przy resolved GitLab scope coverage dodaje luke
  `AFFECTED_FUNCTION_GITLAB_RECOMMENDED`; wtedy model ma wykonac focused
  przeszukanie GitLaba przez tools, zeby `affectedFunction` bylo szczegolowe,
  techniczno-funkcjonalne i napisane jezykiem niekodowym.
- Gdy GitLab zna projekt/plik, zostaje ograniczony focused toolset.
- Przy DB-related symptomach coverage moze dodac luke
  `DB_CODE_GROUNDING_NEEDED`. Wtedy focused GitLab tools pozostaja dostepne do
  proby ugruntowania encji, repozytorium, tabel i relacji przed DB discovery,
  nawet jesli ogolny flow context z GitLaba wyglada na wystarczajacy.
- DB tools sa wlaczane tylko przy resolved environment i
  `DataDiagnosticNeed=LIKELY/REQUIRED`.
- Dla `POSSIBLE` dostepne sa tylko discovery tools.
- `db_execute_readonly_sql` pozostaje domyslnie zablokowany przez tool policy.

Coverage i luki evidence sa widoczne w manifest/prompt.

## 13. Tool budget jest egzekwowany w backendzie

Budzet tools jest session-bound i dziala w `CopilotToolInvocationHandler`
przed i po wywolaniu callbacka.

Domyslnie `analysis.ai.copilot.tool-budget.mode=soft`, czyli przekroczenia sa
logowane i trafiaja do telemetryki, ale tool call nie jest blokowany. Tryb
`hard` zwraca kontrolowany wynik `denied_by_tool_budget`, zamiast zabijac cala
sesje wyjatkiem.

Budzet rozroznia m.in. total calls, grupy Elastic/GitLab/DB, GitLab search,
read file/chunk, returned characters oraz raw SQL attempts.

## 14. Tools sa session-bound i ukrywaja scope

GitLab, Elasticsearch i DB tools powinny dostawac scope przez ukryty
`ToolContext`. Model nie powinien podawac `correlationId`, `gitLabGroup`,
`gitLabBranch` ani `environment` jako jawnych argumentow dla tych scope'ow.

Stan kodu na dzisiaj: GitLab i DB spelniaja ten invariant; Elastic MCP tool
nadal ma jawny parametr `correlationId`. To jest znany drift implementacyjny,
nie nowy kontrakt do rozszerzania.

SessionConfig ma jawna allowliste tools, a `SessionHooks.onPreToolUse`
blokuje lokalny workspace/filesystem/shell/terminal w glownym flow analizy.
Konfiguracje klienta SDK, `SessionConfig`, hooks, permission handler,
skill directories i disabled skills buduje `CopilotSessionConfigFactory`.

## 15. Tool descriptions moga byc dekorowane dla Copilota

`CopilotToolDescriptionDecorator` dokleja krotkie guidance do opisow drogich
lub ryzykownych tools bez zmiany implementacji Spring tools. Przyklady:

- full file read jest expensive i preferuje chunks/outline,
- GitLab search/flow context powinien uzywac konkretnych, ugruntowanych
  keywordow,
- GitLab flow/search guidance przypomina, ze `AFFECTED_FUNCTION_GITLAB_RECOMMENDED`
  jest powodem do malego, focused GitLab lookupu pod opis funkcji,
- GitLab search/class/flow guidance przypomina, ze operational context moze
  wskazywac kilka repozytoriow jednego komponentu wdrozeniowego; biblioteki i
  shared modules z `codeSearchProjects` sa czescia scope'u szukania kodu,
- GitLab i DB tools powinny przekazywac krotki powod po polsku w `reason`,
- DB tools przypominaja modelowi, ze dla JPA/repository/data-access symptomow
  najpierw trzeba sprobowac ugruntowac encje, repozytorium, tabele i relacje z
  deterministic GitLab evidence albo focused GitLab tools; DB discovery jest
  fallbackiem, nie zgadywaniem tabel,
- DB sample rows nie sluzy do przegladania danych biznesowych,
- raw SQL jest last resort i moze byc zablokowany.

## 16. Tool evidence jest czescia audytu

`CopilotToolEvidenceCaptureRegistry` publikuje tool evidence przez
`AnalysisAiToolEvidenceListener`.

Capture obejmuje:

- GitLab file/chunk/chunks jako `gitlab/tool-fetched-code`,
- GitLab search, outline, flow context i class references jako
  `gitlab/tool-discovery`,
- DB tools jako `database/tool-results`.

Widok uzytkownika dla GitLaba nadal trzyma prosty kontrakt operatorski:
`reason` podany przez model jest naglowkiem wpisu, a szczegoly sa pokazane w
tresci. Dla pobranego kodu UI pokazuje nazwe/sciezke pliku, tresc kodu i
metadata linii. Dla discovery tools UI pokazuje uporzadkowane szczegoly lookupu:
kandydatow plikow, grupy flow/class references, outline pliku i rekomendowane
dalsze odczyty.

DB capture publikuje tylko prosty wynik i `reason` podany przez model. Nie
utrzymujemy juz osobnych pytan diagnostycznych, technicznych parametrow ani
dodatkowych streszczen wyniku w user-facing evidence.

Registry zarzadza sesja i routingiem capture, a szczegoly mapowania wynikow
GitLab/DB sa oddzielone od lifecycle sesji.

`CopilotSdkToolBridge` pozostaje warstwa rejestracji tools: zbiera Spring
`ToolCallback`, sortuje je, dekoruje opisy, parsuje input schema i tworzy
`ToolDefinition`. Wykonanie callbacka, walidacja session id, budget
before/after, telemetryka, evidence capture i parsowanie wyniku sa w
`CopilotToolInvocationHandler`.

## 17. Telemetry jest pierwsza warstwa optymalizacji

Copilot zbiera metryki per analiza/sesja:

- liczby sekcji/items/artifacts,
- rozmiary artifacts i promptu,
- duration preparation/client/session/sendAndWait/total,
- liczby tool calls wedlug grup,
- liczniki drogich tools,
- returned characters,
- parser/fallback/structured response,
- detected problem/confidence,
- quality findings,
- budget warnings/denials.

Celem jest porownywanie trafnosci, kosztu i latency przed wymuszaniem
kolejnych ograniczen.

## 18. Raw SQL jest oddzielnym ryzykiem

`db_execute_readonly_sql` jest traktowany osobno od typed DB tools.
Domyslnie tool policy go nie wlacza, a budzet ma osobny limit
`max-db-raw-sql-calls=0`.

Zmiana tej decyzji musi byc jawna i powinna obejmowac properties, testy,
metryki i audyt wyniku.

## 19. Frontend/job API nie powinny wymagac wiedzy o SDK

Job state moze przechowywac prepared prompt i `toolEvidenceSections`, ale UI
nie powinien zalezec od typow Copilot SDK. Publiczne API pozostaje w modelu
analizy aplikacji.

Refaktory w `analysis.ai` i `analysis.ai.copilot` nie powinny wymagac wiedzy o
typach SDK w UI: `POST /analysis` nadal przyjmuje tylko `correlationId`, a
`POST /analysis/jobs` moze przyjac tylko generyczne preferencje AI (`model`,
`reasoningEffort`). Response pozostaje mapowany do pol aplikacji, a artefakty
Copilota nadal sa embedded inline w promptcie.

Katalog modeli jest osobnym backendowym endpointem opcji AI. UI moze pokazac
model i `reasoningEffort`, ale same listy pochodza z Copilot SDK przez
`AnalysisAiModelOptionsProvider`, nie z kodu Angulara.

## 20. Follow-up chat jest kontynuacja joba

Po `COMPLETED` operator moze wyslac pytanie albo polecenie przez
`POST /analysis/jobs/{analysisId}/chat/messages`. To nie zmienia minimalnego
kontraktu `POST /analysis` ani nie dodaje recznego scope'u do publicznego
requestu.

Decyzje:

- wiadomosc chatu jest asynchroniczna i pollowana przez ten sam
  `GET /analysis/jobs/{analysisId}`,
- kazda odpowiedz chatu uruchamia nowa sesje AI, zamiast trzymac otwarta sesje
  SDK po finalnej analizie,
- follow-up prompt dostaje evidence, wynik koncowy, historie rozmowy i
  poprzednie tool evidence,
- GitLab/Elasticsearch/Database tools nadal sa session-bound przez hidden
  `ToolContext`,
- scope tools pochodzi z zakonczonej analizy: `correlationId`, `environment`,
  `gitLabBranch` i `gitLabGroup`,
- raw SQL pozostaje wylaczony domyslnie; chat preferuje typed DB tools,
- tool evidence pobrane w follow-up jest przypisane do odpowiedzi chatu, a nie
  do deterministycznego pipeline evidence.

Konsekwencje:

- importowany zapis analizy jest read-only dla UI chatu, bo backend nie ma
  pamieci tego joba,
- chat moze prosic AI o weryfikacje w repo, DB albo wygenerowanie raportu, ale
  model nie powinien wymyslac scope'u ani obchodzic blokady lokalnego workspace.

## 21. Optymalizacje Copilota prowadzimy inkrementalnie

Kolejnosc prac:

1. telemetry i baseline,
2. JSON response contract,
3. quality gate,
4. coverage-aware tool policy,
5. incident digest, item IDs i evidence references,
6. tool budget,
7. tool description decorators i audit capture,
8. single prepared analysis flow,
9. dokumentacja, pro context i decision records.

Dopiero po tych warstwach warto dodawac wieksze zmiany, np. soft repair,
multi-stage flow, routing modeli albo alternatywne delivery mode artefaktow.
