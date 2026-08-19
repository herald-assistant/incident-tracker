# Key Decisions

Ten dokument zbiera decyzje architektoniczne, ktore sa wazne przy utrzymaniu
platformy AI-augmented system analysis oraz pierwszego feature'a, czyli
analizy incydentu.

## 0. Produkt jest platforma, incident analysis jest pierwszym feature'em

Repo wystartowalo jako incident tracker, ale docelowy produkt jest szerszy:
ma byc platforma do AI-augmented system analysis. Incydent po `correlationId`
jest pierwszym pionowym feature'em, ktory dowodzi integracji, tools, Copilot
runtime i operator workflow.

Konsekwencje:

- `features.incidentanalysis` nie jest generycznym core dla kolejnych analiz,
- `aiplatform`, `agenttools`, `integrations`, `shared`, `common` i
  shared/operator `api.*` musza pozostac reusable poza incydentami,
- kolejne feature'y, np. flow explorer, change verification albo
  natural-language data diagnostics, maja dostarczac wlasny request/result,
  prompt, skille, tool policy, hidden context i UI/API,
- publiczne URL-e moga historycznie zawierac `analysis`, ale pakiety Javy
  maja odzwierciedlac ownership warstw,
- gdy nowa potrzeba wyglada "wspolnie", najpierw trzeba ustalic, czy to
  naprawde platform mechanics, reusable capability, shared/operator API czy
  tylko logika konkretnego feature'a.

## 0a. UI jest Team Delivery Workspace

Product-facing UI nazywa workspace `Team Delivery Workspace`. Technicznie repo
nadal rozwija platforme do AI-augmented system analysis, ale UI nie powinien
brzmiec jak narzedzie tylko dla analitykow ani jak pojedynczy incident tracker.
Ma wspierac caly zespol wytworczy i sposob pracy oparty o skills/capabilities,
a nie role-based organization.

Decyzje:

- `app.ui.title` parametryzuje glowny tytul UI.
- `platform.source-code.default-branch` jest wymaganym, pojedynczym defaultem
  branch/ref dla feature'ow pracujacych ze zrodlem kodu. Backend i Angular
  czytaja go przez wspolna konfiguracje; feature'y nie utrzymuja lokalnego
  property, endpointu ani stalej fallbackowej.
- `app.ui.title` moze byc lokalnie nadpisane w Workspace Settings; override z
  `${tdw.workspace.directory}/settings.json` ma pierwszenstwo przed
  `application.properties`.
- Gdy `app.ui.title` nie ma tekstu, frontend pokazuje tylko
  `Team Delivery Workspace`.
- Gdy `app.ui.title` jest ustawione, jego wartosc jest tytulem, a
  `Team Delivery Workspace` podtytulem.
- Glowna nawigacja jest w lewym sidebarze, a topbar pozostaje kontekstowy.
- Sidebar ma grupy `Analysis Features`, `Tool Workbench` i `Platform`.
- `GET /` jest `Platform / Team Delivery Workspace`: startowym overview
  workspace'u z szybkim wejsciem do aktywnych feature'ow. Tresc jest
  customer-centric i tlumaczy oszczednosc czasu, nie mechanike AI/tools.
- `Analysis Features` zawiera dedykowane feature'y produktowe; na teraz
  aktywne sa `Incident Analysis` pod `/incident-analysis` oraz
  `Flow Explorer` pod `/flow-explorer`.
- `Tool Workbench` zawiera reusable capability do testow i debugowania:
  `Elastic Logs`, `GitLab Source`, `Jira Source`, `Confluence Source`,
  `Database Tools` i `Operational Context`.
- `GitLab Source` zawiera operatorska grupe `Frontend Discovery` dla bounded
  route graph Angular/Nx i screen source context. Discovery zaczyna od jednego
  zweryfikowanego `bootstrapApplication(...) -> provideRouter(...)`, wykonuje
  tylko targeted reads po osiagalnym grafie i nie listuje calego repository.
  Route topology ma pierwszenstwo przed view targets; extensionless importy
  zachowuja TypeScript file-before-index precedence, a direct dynamic import
  moze wskazywac default-export lazy component. Local lazy factory obsluguje
  named/default import, a statyczne `reduce/flatMap` literalnych pol `routes`
  jest rozwijane bez wykonywania badanego kodu. Bounded katalog ma domyslnie
  200 000 znakow na plik i 2 000 000 lacznie; wyczerpanie limitu lacznego nie
  moze generowac kaskady wtornych bledow importu.
  Source revision pochodzi z bezposredniego rozwiazania refa do commit id, nie
  z metadanych pliku bootstrap. Jest to shared/operator preview neutralnej
  integracji, bez MCP toola, AI, historii i joba UI Explorer.
- `Operational Context` pozostaje w `Tool Workbench` jako context/catalog
  capability, a nie jako element sekcji `Platform`.
- `Platform` dotyczy overview, customizacji i podgladu zasobow Team Delivery
  Workspace: workspace settings, effective katalogu `AI Skills`,
  personalizacji, autentykacji, konfiguracji modeli i podobnych ustawien.
- Zakres Workspace Settings obejmuje `app.ui.title`, podstawowe connection
  settings Jiry (`analysis.jira.base-url`, `analysis.jira.token`) i Confluence
  (`analysis.confluence.base-url`, `analysis.confluence.token`) oraz GitLaba
  (`analysis.gitlab.base-url`, `analysis.gitlab.group`,
  `analysis.gitlab.token`), a takze GitLaba konfiguracji runtime
  (`integrations.gitlab.named.connections.runtime-config.base-url`,
  `integrations.gitlab.named.connections.runtime-config.token`), Elasticsearch (`analysis.elasticsearch.base-url`,
  `analysis.elasticsearch.kibana-space-id`,
  `analysis.elasticsearch.index-pattern`,
  `analysis.elasticsearch.authorization-header`) oraz Dynatrace
  (`analysis.dynatrace.base-url`, `analysis.dynatrace.api-token`) oraz lokalny
  token Copilota (`analysis.ai.copilot.auth.local.github-token`). MVP nie
  wystawia `analysis.ai.copilot.auth.mode`,
  `analysis.ai.copilot.auth.local.display-name`, flag SSL ani technicznych
  limitow odpowiedzi integracji. W szczegolnosci Workspace Settings nie
  wystawia `analysis.confluence.url-pattern` ani
  `analysis.confluence.max-text-characters`.

Konsekwencje UI:

- Nie dodajemy marketingowych hero do narzedzi codziennej pracy.
- Workspace overview moze opisywac platforme, ale ma byc action-oriented,
  spokojny, przyjazny dla osob nietechnicznych i zgodny z roboczym charakterem
  UI. Nie opisuje tools, promptow, runtime ani integracji pod spodem.
- Jeden ekran ma miec jeden dominujacy primary action.
- Workbench nie dostaje stalego trzykolumnowego layoutu dla response; wynik
  toola jest renderowany szeroko pod formularzem.
- Workbench endpointy i payloady pozostaja analysis-independent. Nie eksponuja
  `analysisRunId` ani incidentowego session scope'u.
- Statyczny opis capability w Workbench mieszka w topbarze pod ikona info, a
  nie w lokalnych `workbench-header` cards.
- Jasny motyw jest defaultem V1; tokeny CSS maja pozwalac na przyszle style.

## 1. Publiczny request analizy pozostaje minimalny

`POST /api/analysis/jobs` jest kanonicznym publicznym startem analizy.
Przyjmuje wybor zrodla logow oraz opcjonalne preferencje wykonania AI:
`model` i `reasoningEffort`.

Obslugiwane zrodla logow:

- `source=ELASTICSEARCH` z `correlationId`,
- `source=CSV_UPLOAD` z plikiem `logFile` wyeksportowanym z Kibana/Elastic
  Discover.

`GET /api/analysis/jobs/input-options` jest feature-owned endpointem dla UI i
zwraca, czy start przez Elasticsearch jest dostepny. Jezeli efektywna
konfiguracja Elasticsearch/Kibana jest niekompletna, sciezka
`source=ELASTICSEARCH` jest blokowana, ale `source=CSV_UPLOAD` pozostaje
dostepne.

Lista dostepnych modeli dla UI pochodzi z shared/operator endpointu
`GET /analysis/ai/options`. Endpoint mapuje metadane Copilot SDK na generyczny
kontrakt aplikacji i zwraca `reasoningEffort` tylko tam, gdzie SDK wystawia
support albo domyslna wartosc dla danego modelu.

Runtime nie przywraca `branch`, `environment`, `gitLabGroup` ani innych pol
sterujacych evidence scope'em do publicznego requestu.

Konsekwencje:

- `environment` jest wyprowadzany z evidence, przede wszystkim z logow
  `elasticsearch/logs` i deployment context.
- `gitLabBranch` jest wyprowadzany z evidence deployment/runtime.
- `gitLabGroup` pochodzi z konfiguracji aplikacji.
- `correlationId` jest publicznym inputem tylko dla sciezki Elasticsearch; dla
  uploadu CSV jest wyprowadzany deterministycznie z kolumn logow.
- uzytkownik nie moze recznie przesterowac zakresu GitLaba albo DB przez
  publiczne API analizy.
- wybor modelu i `reasoningEffort` dotyczy tylko konfiguracji sesji AI, nie
  zmienia deterministycznie zbieranego evidence ani ukrytych scope'ow tools.
- frontend nie hardcoduje mozliwosci modeli; backend pozostaje source of truth
  i moze uzyc fallbacku do skonfigurowanych domyslow, gdy SDK chwilowo nie
  zwroci katalogu.

## 1a. Copilot authentication ma dwa tryby

Copilot authentication has two modes:

- `LOCAL_TOKEN` for local/dev runs, using a configured GitHub token from
  `analysis.ai.copilot.auth.local.github-token` albo `COPILOT_GITHUB_TOKEN`.
- `GITHUB_APP` for operator-facing runs, using a GitHub App user access token
  zwiazany z backendowa operator session cookie.

Public analysis and chat requests never carry GitHub tokens or OAuth codes.
The job flow carries only a non-secret AI auth reference. The actual token is
resolved inside `aiplatform.copilot.runtime.auth` immediately before
`CopilotClientOptions` are created.

`CopilotClientOptions` must always receive `githubToken` explicitly and
`useLoggedInUser=false`, so the backend never falls back to locally cached CLI
credentials. GitHub App installation tokens are not used for Copilot SDK,
because Copilot usage should belong to the GitHub user account in
operator-facing mode.

Konsekwencje:

- frontend pobiera status przez `GET /api/auth/github/status` zanim pobierze
  `GET /analysis/ai/options`,
- `GET /analysis/ai/options`, `POST /api/analysis/jobs` i follow-up chat sa
  auth-aware, ale ich publiczne payloady pozostaja minimalne,
- GitHub App access/refresh tokens pozostaja po stronie backendu, w store sa
  zaszyfrowane, a refresh token rotation jest zapisywana atomowo,
- missing local token, missing GitHub auth i reauth sa kontrolowanymi bledami
  API, nie fallbackiem do lokalnie zalogowanego uzytkownika.

## 2. Flow pozostaje AI-first

Evidence pipeline zbiera deterministyczny material, ale diagnoza i
rekomendacja sa wynikiem providera AI. Nie przenosimy diagnozowania do
centralnego rule engine.

Heurystyki sa dozwolone tylko jako:

- deterministyczne wzbogacanie `AnalysisContext`,
- ocena coverage i luk evidence,
- polityka dostepu do tools,
- walidacja shape odpowiedzi AI,
- logowanie i audyt evidence widoczny dla operatora.

Heurystyki nie powinny zastapic modelu w budowaniu diagnozy biznesowej.

## 3. Evidence pipeline jest deterministyczny na `AnalysisContext`

Kolejne kroki evidence providerow czytaja i aktualizuja `AnalysisContext`.
Po resolved deployment context kroki Dynatrace i GitLab deterministic moga
dzialac rownolegle z tego samego snapshotu contextu.

Provider evidence zwraca `shared.evidence.AnalysisEvidenceSection`. AI layer
nie powinien czytac DTO adapterow bezposrednio.

## 4. GitLab ma trzy osobne capability

GitLab w systemie nie jest jedna abstrakcja:

- adapter i source resolve do ogolnego dostepu do GitLaba, w tym neutralne
  bounded rozpoznawanie Angular/Nx route/view i screen source context,
- deterministic evidence provider do deployment context/code references,
- AI-guided tools do dociagania kodu w sesji Copilota.

Te role nie powinny byc mieszane. Deterministic evidence ma przygotowac
najlepszy snapshot przed AI, a tools sa tylko do uzupelniania luk.

## 5. Skills Copilota sa runtime resource

Skille Copilota sa pakowane z aplikacja z `src/main/resources/copilot/skills`.
Nie traktujemy ich jako plikow `.github` repozytorium hosta.

`src/main/resources/copilot/skills` jest immutable packaged seedem. Przy starcie
`CopilotSkillRuntimeLoader` waliduje seed i dopisuje tylko brakujace pliki pod
`${analysis.ai.copilot.copilot-home}/skills`, domyslnie
`tdw-data/copilot/skills`; istniejace effective pliki nie sa nadpisywane ani
usuwane. Ten sam pojedynczy root trafia do kazdego
`SessionConfig` i `ResumeSessionConfig`, a built-in tool `skill` jest domyslnie
w effective allowliscie. Jawna sesja one-shot moze wylaczyc skills wraz z
katalogami, gdy feature osadza effective tresc skilla w jedynym prompcie i
konfiguruje pusta allowliste tools. Nie tworzymy selected roots ani katalogow
per feature lub per analiza.

Skill przechowuje stale zasady pracy modelu. Dane konkretnego incydentu
niesie prompt i artefakty przygotowane w runtime. Feature posiada tresc i
workflow swoich skilli oraz wskazuje starter w prompcie; nie przekazuje
platformie katalogow ani listy wybranych nazw.

Platforma wystawia operatorowi projekcje tego samego zwalidowanego effective
katalogu przez `GET /api/ai/skills` i `GET /api/ai/skills/{skillName}` oraz
ekran `Platform / AI Skills`. `PUT /api/ai/skills/{skillName}` atomowo
nadpisuje jeden effective `SKILL.md`, a `POST .../restore-default` przywraca
packaged wersje. API nie uruchamia skilla, nie przypisuje go per feature i nie
ujawnia sciezki filesystemu. Stan `DEFAULT/CUSTOM` jest porownaniem effective
z aktualnym packaged seedem. Workflow i responsibility widoczne w UI sa
pomocnicza projekcja nawigacyjna, a nie runtime selection.

## 6. Granica AI pozostaje generyczna

Kontrakt wejscia do AI to `InitialAnalysisRequest` i lista neutralnych
`shared.evidence.AnalysisEvidenceSection`. Prompt builder i provider AI nie
przyjmuja klas adapter-specific.

Jesli AI layer potrzebuje typowego widoku evidence, powinien uzyc helperow
widoku nad generycznymi `AnalysisEvidenceSection`, np. widokow dla logow,
runtime signals albo resolved code evidence.

## 7. Prepared analysis jest budowane raz

Orchestrator nie buduje juz promptu debugowego osobno od requestu
wykonywanego przez AI.

Aktualny flow:

1. orchestrator buduje `InitialAnalysisRequest`,
2. wywoluje `InitialAnalysisProvider.prepare(request)`,
3. zapisuje `prepared.prompt()` w stanie joba,
4. wykonuje `InitialAnalysisProvider.analyze(prepared, listener)`,
5. zamyka `InitialAnalysisPreparation` w `finally`/try-with-resources.

Ownership prepared analysis jest jawny:

- wlasciciel obiektu zwroconego z `prepare(request)` zamyka go po uzyciu,
- `analyze(prepared, listener)` nie zamyka prepared analysis przekazanego
  przez caller,
- gateway wykonujacy SDK nie przejmuje ownership i nie zamyka przygotowanej
  sesji.

W Copilocie sa trzy jawne poziomy:

- `CopilotInitialAnalysisPreparation` implementuje initial-facing
  `InitialAnalysisPreparation` i niesie `InitialAnalysisRequest`,
- `CopilotRunRequest` jest platformowym inputem wykonania. Niesie neutralny
  `runReference`, prompt, parametry sesji, logiczne artefakty i evidence sink,
  czyli to, co feature przekazuje do runtime,
- `CopilotPreparedSession` jest neutralnym technicznym obiektem wykonania SDK,
  uzywanym przez execution gateway oraz follow-up chat. Powstaje z
  `CopilotRunRequest`; incident analysis moze mapowac `correlationId` na
  `runReference`, ale runtime nie traktuje `correlationId` jako wlasnego pola.

Follow-up chat nie implementuje ani nie reuse'uje `InitialAnalysisPreparation`.

`InitialAnalysisProvider` nie ma produkcyjnych shortcutow dodanych tylko dla
testow, takich jak `analyze(request)`, oddzielne `preparePrompt(...)` albo
domyslne prepared adaptery. Testy tworza wlasne prepared fixtures.

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

## 10. Initial result jest report-first

Kanonicznym wynikiem initial analysis jest generyczny `AnalysisReport` z
`shared.ai.report`, a nie finalna tresc odpowiedzi tekstowej Copilota.
Backend tworzy `reportId` przy skladaniu runu, przekazuje scaffold raportu do
`CopilotRunRequest.initialReport`, a runtime rejestruje go w
`CopilotReportSessionStore` na czas pojedynczego `sendAndWait`.

Model zapisuje wynik przez platformowe report tools:

- `report_update_header`,
- `report_upsert_section`,
- `report_update_meta`,
- `report_get_current`.

Report tools sa session-bound. Model-facing schema nie przyjmuje `reportId`;
scope pochodzi z hidden `ToolContext`, razem z feature name i lista
dozwolonych sekcji. Tool odrzuca sekcje spoza `allowedReportSectionIds`.

Po zakonczeniu `sendAndWait` execution gateway zwraca ostatni snapshot raportu
w `CopilotExecutionResult.report()`. Feature mapuje ten raport na swoj
publiczny kontrakt:

- Incident Analysis mapuje `header` na `detectedProblem`, sekcje
  `FUNCTIONAL_ANALYSIS` i `TECHNICAL_HANDOFF` na obecne pola
  `functionalAnalysis` i `technicalAnalysis`, a meta references na affected
  process/bounded context/team oraz visibility limits.
- Flow Explorer mapuje `OVERVIEW` i aktywne sekcje raportu na
  `FlowExplorerAiResponse`, zachowujac feature-specific widok dla UI.

JSON-only response contract pozostaje tylko fallbackiem diagnostycznym, gdy
raport nie zostal zapisany albo jest niekompletny. Legacy labeled response
parser pozostaje usuniety. Finalna proza z `sendAndWait` moze byc przydatna w
diagnostyce, ale nie jest zrodlem prawdy initial result.

`AnalysisReport` jest neutralny i nie zna semantyki incydentu ani Flow
Explorera. Feature decyduje o dozwolonych sekcjach, required sections,
promptach, skillach i mapowaniu raportu na publiczny response. W MVP nie
wersjonujemy raportu; job state, local workspace i export trzymaja ostatni
snapshot raportu obok feature-specific `result`.

`functionalAnalysis` nadal jest pisane dla analityka biznesowo-systemowego i
musi uzywac operational context do osadzenia incydentu w systemie, procesie,
bounded context, logice funkcjonalnej i regule handoffu. `technicalAnalysis`
nadal jest Technical Handoff v1 dla osoby lub zespolu, ktory ma problem
naprawic, zweryfikowac albo przekazac do innego Tribe/administracji/infra.

## 11. Ukryty quality gate jest usuniety

Nie utrzymujemy obecnie niewidocznego dla uzytkownika quality gate po
parsingu odpowiedzi. Jakosc odpowiedzi jest egzekwowana przez prompt, JSON
schema, parser/fallback i testy kontraktu. Jesli walidacja jakosci ma wrocic,
powinna byc jawna dla operatora albo realnie zmieniac runtime, np. przez
repair/retry z testami i dokumentacja.

## 12. Tool policy jest coverage-aware

Nie uzywamy juz zasady "sekcja GitLab/Elasticsearch istnieje, wiec wylacz
tools". `CopilotIncidentEvidenceCoverageEvaluator` ocenia coverage generycznych
evidence i tworzy `CopilotIncidentEvidenceCoverageReport`.

`CopilotIncidentToolAccessPolicyFactory` jest jedynym produkcyjnym miejscem, ktore
laczy request, evaluator coverage i zarejestrowane tool definitions. Sama
`CopilotIncidentToolAccessPolicy` jest budowana z gotowego coverage reportu i nie
tworzy recznie nowego evaluatora.

Polityka:

- Elasticsearch tools sa wlaczane tylko przy kompletnej konfiguracji
  Elasticsearch/Kibana i coverage gapach takich jak brak logow, truncation albo
  brak stacktrace. Przy brakujacej konfiguracji tools `elastic_*` nie sa
  wystawiane Copilotowi w initial analysis ani follow-up chat.
- GitLab tools sa wlaczane przy braku code evidence albo gdy jest tylko
  symbol, stack frame, failing method lub brakuje flow context.
- Przy resolved GitLab scope coverage dodaje luke
  `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED`; wtedy model ma wykonac focused
  przeszukanie GitLaba przez tools, zeby `technicalAnalysis` bylo konkretne
  na poziomie wejscia, przeplywu, miejsca przerwania i rekomendowanej poprawki.
- Operational Context tools sa wlaczane dla luki
  `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED`, zeby `functionalAnalysis` nie bylo
  oderwane od katalogu systemow, procesow, bounded contextow, integracji,
  glossary i reguly handoffu.
- Gdy GitLab zna projekt/plik, zostaje ograniczony focused toolset.
- Przy DB-related symptomach coverage moze dodac luke
  `DB_CODE_GROUNDING_NEEDED`. Wtedy focused GitLab tools pozostaja dostepne do
  proby ugruntowania encji, repozytorium, tabel i relacji przed DB discovery,
  nawet jesli ogolny flow context z GitLaba wyglada na wystarczajacy.
- `gitlab_list_available_repositories` jest lekkim discovery tool nad
  operational context. Pozostaje dostepny razem z focused GitLab tools, zeby
  model mogl odnalezc `projectName`/`gitLabPath` repozytorium po aliasie,
  systemie, bounded context, procesie, integracji albo code-search scope,
  zanim uzyje search/read tools.
- `gitlab_list_available_repositories` zwraca tez `codeSearchScopes` z
  `code-search-scopes.yml`: semantic target, role, priorytety i `projectName`
  repozytoriow oraz `searchMode/pathPrefixes`, ktore nalezy respektowac przy
  GitLab search/flow/class-reference dla dopasowanego zakresu.
- DB tools sa wlaczane tylko przy resolved environment i
  `IncidentDataDiagnosticNeed=LIKELY/REQUIRED`.
- Dla `POSSIBLE` dostepne sa tylko discovery tools.
- `db_execute_readonly_sql` pozostaje domyslnie zablokowany przez tool policy.

Coverage i luki evidence sa widoczne w manifest/prompt.

## 13. Tool budget jest egzekwowany w backendzie

Budzet tools jest session-bound i dziala jako generyczna
`CopilotToolInvocationPolicy` uruchamiana przez `CopilotToolInvocationHandler`
przed i po wywolaniu callbacka. Handler nie zna szczegolow budzetu ani
payloadu odmowy.

Domyslnie `analysis.ai.copilot.tool-budget.mode=soft`, czyli przekroczenia sa
logowane w backendzie, ale tool call nie jest blokowany. Tryb
`hard` zwraca kontrolowany wynik `denied_by_tool_budget`, zamiast zabijac cala
sesje wyjatkiem. Technicznie `CopilotToolBudgetPolicy` rzuca kontrolowany
`CopilotToolInvocationRejectedException`, a handler zamienia go na wynik dla
SDK i event terminalny `REJECTED`.

Budget policy mieszka w `aiplatform.copilot.tools.policy.budget` i utrzymuje
session-bound state tylko na czas sesji Copilota. Walidacja session id jest
takim samym mechanizmem policy w
`aiplatform.copilot.tools.policy.session`, dzieki czemu handler nie ma
osobnych warunkow dla konkretnych regul runtime.

Budzet rozroznia m.in. total calls, grupy Elastic/GitLab/DB, GitLab search,
read file/chunk, returned characters oraz raw SQL attempts.

## 14. Tools sa session-bound i ukrywaja scope

Docelowo wszystkie integracyjne tools powinny dostawac scope przez ukryty
`ToolContext`. Model nie powinien podawac `correlationId`, `gitLabGroup`,
`gitLabBranch` ani `environment` jako jawnych argumentow dla tych scope'ow.

Stan kodu na dzisiaj: GitLab i DB spelniaja ten invariant; Elastic MCP tool
nadal ma jawny parametr `correlationId`. To jest znany drift implementacyjny,
nie nowy kontrakt do rozszerzania.

SessionConfig ma jawna allowliste tools, a `SessionHooks.onPreToolUse`
blokuje lokalny workspace/filesystem/shell/terminal w glownym flow analizy.
Incident preparation sklada `CopilotSessionConfigRequest`: wybiera allowed
tools, model options i incidentowy komunikat odmowy toola. Wspolny katalog
skilli oraz built-in `skill` doklada platforma.
Incident preparation sklada tez `CopilotToolSessionContext`: tworzy
`analysis-*`/`analysis-chat-*` session id i hidden tool context ze scope'u
incydentu.
Initial i follow-up tool policy powstaja przez `CopilotIncidentToolAccessPolicyFactory`,
zeby decyzje o dostepnych capability byly lokalne dla incident preparation.
Follow-up nie buduje juz requestu artefaktow ani pelnego promptu
kontynuacyjnego; `CopilotIncidentFollowUpRunAssembler` wymaga
`copilotSessionId`, wybiera `sessionTarget=EXISTING` i wysyla sama wiadomosc
operatora.
`CopilotIncidentRunRequestFactory` sklada finalny `CopilotRunRequest`, zeby
mapowanie artifact contents na platformowy input runtime bylo w jednym miejscu.
`CopilotSessionConfigFactory` jest juz tylko runtime factory, ktora zamienia
ten request na konfiguracje klienta SDK, `SessionConfig`,
`ResumeSessionConfig`, hooks, permission handler i disabled skills.

## 15. Tool descriptions moga byc dekorowane dla Copilota

Platformowy kontrakt `CopilotToolDescriptionCustomizer` pozwala feature'owi
uzupelnic opisy tools bez zmiany implementacji Spring tools. Incident feature
dostarcza `CopilotIncidentToolDescriptionCustomizer`, ktory dokleja krotkie
guidance do opisow drogich lub ryzykownych tools. Przyklady:

- full file read jest expensive i preferuje chunks/outline,
- GitLab search/flow context powinien uzywac konkretnych, ugruntowanych
  keywordow,
- GitLab flow/search guidance przypomina, ze
  `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` jest powodem do malego, focused
  GitLab lookupu pod Technical Handoff v1,
- GitLab available-repositories/search/class/flow guidance przypomina, ze
  operational context moze wskazywac kilka repozytoriow jednego systemu;
  repozytoria pomocnicze z `codeSearchScopes` oraz kompatybilnych
  `codeSearchProjects` sa czescia scope'u szukania kodu, a
  `searchMode/pathPrefixes` zawieraja jawna granice searchu per repozytorium,
- GitLab i DB tools powinny przekazywac krotki powod po polsku w `reason`,
- DB tools przypominaja modelowi, ze dla JPA/repository/data-access symptomow
  najpierw trzeba sprobowac ugruntowac encje, repozytorium, tabele i relacje z
  deterministic GitLab evidence albo focused GitLab tools; DB discovery jest
  fallbackiem, nie zgadywaniem tabel,
- DB sample rows nie sluzy do przegladania danych biznesowych,
- raw SQL jest last resort i moze byc zablokowany.

## 16. Tool evidence jest czescia audytu

`aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore` publikuje
tool evidence przez neutralny session-bound sink
`Consumer<AnalysisEvidenceSection>`. Provider AI adaptuje
`shared.evidence.AnalysisAiToolEvidenceListener` do tego sinka przed
wywolaniem execution gatewaya.

Capture obejmuje:

- GitLab file/chunk/chunks jako `gitlab/tool-fetched-code`,
- GitLab available repositories, search, outline, flow context i class references jako
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

Session store zarzadza sesja i routingiem capture, a szczegoly mapowania wynikow
GitLab/DB sa oddzielone od lifecycle sesji.

`aiplatform.copilot.tools.CopilotSdkToolFactory` pozostaje warstwa rejestracji
tools: zbiera Spring `ToolCallback`, sortuje je, customizuje opisy, parsuje
input schema i tworzy `ToolDefinition`. Nie wykonuje tooli i nie interpretuje
wynikow.

`aiplatform.copilot.tools.CopilotToolInvocationHandler` pozostaje runtime
boundary: serializuje argumenty, uruchamia generyczne invocation policies,
buduje hidden `ToolContext`, wywoluje callback, publikuje wewnetrzne eventy
tool invocation i parsuje wynik dla SDK. Handler nie zna logiki GitLaba, DB,
metryk ani budget payloadu poza generycznym kontrolowanym rejection.

Event lifecycle:

1. policy before-invocation, w tym session validation i budget,
2. `Started` tylko po zaakceptowaniu invocation przez policies,
3. callback Spring tool,
4. policy after-invocation tylko po udanym callbacku,
5. terminalny `Finished(COMPLETED|REJECTED|FAILED)`.

Side-effecty sa subskrybowane przez dedykowane listenery: logging,
GitLab evidence capture i Database evidence capture. Publikator eventow izoluje
bledy listenerow, zeby awaria audytu albo logowania nie zmieniala wyniku toola.
`aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore` zarzadza
lifecycle sesji i publikacja zaktualizowanych sekcji, ale szczegoly mapowania
GitLab/DB pozostaja w odpowiednich pakietach tool capability.

## 17. Zostaje tylko usage widoczny dla uzytkownika

Na teraz nie utrzymujemy osobnej, niewidocznej dla operatora telemetryki
sesji Copilota. Runtime agreguje jedynie usage z eventow SDK
`assistant.usage` i `session.usage_info` do neutralnego
`shared.ai.AnalysisAiUsage`, bo ten kontrakt jest pokazany w job state/UI.

Tool evidence pobrane przez model nadal jest czescia audytu uzytkownika:
GitLab/DB capture publikuje `toolEvidenceSections`, a UI pokazuje je przy
analizie. Budzet tools pozostaje backendowym guardrailem i loguje
przekroczenia, ale jego liczniki nie sa osobnym feature'em telemetrycznym.

Jesli metryki optymalizacyjne wroca, powinny byc zaprojektowane jako jawny
productized element: z celem widocznym dla zespolu/operacji, testami,
dokumentacja i decyzja, gdzie uzytkownik lub operator ma do nich dostep.

## 17a. Tool feedback jest jawny i user-visible

Platforma Copilot udostepnia zawsze dostepny tool `record_tool_feedback`.
Model moze go uzyc, zeby zapisac widoczna dla operatora ocene wyniku
wczesniejszego toola: szczegolnie uzytecznego, czesciowego, pustego,
blednego, mylacego, zbyt szumnego albo zle scoped.

Decyzje:

- tool mieszka w `aiplatform.copilot.tools.feedback`, nie w incident feature,
- feedback nie przyjmuje `analysisId`, `correlationId`, `environment`,
  `gitLabGroup` ani `gitLabBranch`; scope pochodzi z biezacej sesji,
- sam callback toola pozostaje zwyklym wywolaniem Spring tool; zapis feedbacku
  do analizy robi listener zakonczonego invocation, publikujac neutralna
  sekcje przez ten sam `AnalysisAiToolEvidenceListener`, ktory obsluguje inne
  wyniki tools,
- feedback nie zuzywa exploration budgetu i nie jest targetem dla wlasnego
  feedbacku,
- wynik trafia do `shared.ai.AnalysisAiToolFeedback`, job state, UI oraz
  eksportu JSON konkretnej analizy,
- prompt renderer dodaje jedna centralna instrukcje uzycia feedbacku, gdy tool
  jest dostepny; nie dopisujemy tej samej wzmianki do kazdego skillu,
- feedback nie jest deterministic evidence i nie sluzy jako input do root
  cause diagnosis,
- feedback nie jest ukryta telemetryka, ukrytym quality gate'em ani
  automatyczna decyzja runtime.

W V1 feedback jest przechowywany tylko w stanie konkretnej analizy i
follow-up odpowiedzi chatu. Nie ma jeszcze trwalej historii ani agregacji
miedzy analizami.

## 18. Raw SQL jest oddzielnym ryzykiem

`db_execute_readonly_sql` jest traktowany osobno od typed DB tools.
Domyslnie tool policy go nie wlacza, a budzet ma osobny limit
`max-db-raw-sql-calls=0`.

Zmiana tej decyzji musi byc jawna i powinna obejmowac properties, testy oraz
audyt wyniku widoczny dla operatora.

## 19. Frontend/job API nie powinny wymagac wiedzy o SDK

Job state moze przechowywac prepared prompt i `toolEvidenceSections`, ale UI
nie powinien zalezec od typow Copilot SDK. Publiczne API pozostaje w modelu
analizy aplikacji.

Zuzycie tokenow jest wystawiane jako generyczne
`shared.ai.AnalysisAiUsage`, a nie jako event albo typ Copilot SDK. Dzieki
temu UI moze pokazac sumaryczne tokeny,
uproszczone GitHub AI Credits/USD oraz szczegoly sesji AI bez znajomosci
mechaniki event streamu. Estymacja kosztu jest liczona w frontendzie z tokenow
i tabeli stawek modelu, bo sluzy do pokazania rzedu wielkosci oplacalnosci
analizy, a nie do rozliczen finansowych.

Refaktory w `features.incidentanalysis`, `aiplatform.copilot` i obecnych
fasadach `features.incidentanalysis.job` / `api.aioptions` nie powinny
wymagac wiedzy o typach SDK w UI:
`POST /api/analysis/jobs` przyjmuje feature-owned wybor zrodla logow
(`ELASTICSEARCH` albo `CSV_UPLOAD`) oraz generyczne preferencje AI (`model`,
`reasoningEffort`). Response pozostaje mapowany do pol aplikacji, a artefakty
Copilota nadal sa embedded inline w promptcie.

Katalog modeli jest osobnym backendowym endpointem opcji AI. UI moze pokazac
model i `reasoningEffort`, ale same listy pochodza z Copilot SDK przez
`api.aioptions.AnalysisAiModelOptionsProvider`, nie z kodu Angulara.

## 20. Follow-up chat jest kontynuacja joba

Po `COMPLETED` operator moze wyslac pytanie albo polecenie przez
`POST /api/analysis/jobs/{analysisId}/chat/messages`. To nie dodaje recznego
scope'u do publicznego requestu startu analizy.

Decyzje:

- wiadomosc chatu jest asynchroniczna i pollowana przez ten sam
  `GET /api/analysis/jobs/{analysisId}`,
- initial analysis uruchamia `sessionTarget=NEW`, a follow-up kontynuuje
  zapisana sesje SDK przez `sessionTarget=EXISTING(copilotSessionId)`,
- Incident Analysis follow-up wysyla do SDK tylko tresc wiadomosci operatora;
  kontekst rozmowy, evidence i poprzednie tool evidence pochodza z historii
  sesji Copilota, a nie z ponownie renderowanego promptu,
- Flow Explorer follow-up ma feature-owned chat prompt i skill
  `flow-explorer-follow-up-chat`, zeby odpowiedz domyslnie byla Markdownem,
  nie initial JSON result contract, i zeby poglebianie przez tools oraz jezyk
  domenowy byly jawna czescia kontraktu rozmowy,
- przy resume backend ponownie przekazuje aktualne tools, hidden context,
  hooks, permission handler, model i `reasoningEffort`; platforma podpina ten
  sam wspolny katalog skilli co dla nowej sesji,
- GitLab i Database tools nadal sa session-bound przez hidden `ToolContext`;
  Elasticsearch korzysta z zakonczonej analizy jako scope'u sesji, ale ma
  jeszcze zastany jawny `correlationId` w schema toola i jest nadal blokowany,
  gdy konfiguracja Elasticsearch/Kibana jest niekompletna,
- scope tools pochodzi z zakonczonej analizy: `correlationId`, `environment`,
  `gitLabBranch` i `gitLabGroup`,
- raw SQL pozostaje wylaczony domyslnie; chat preferuje typed DB tools,
- tool evidence pobrane w follow-up jest przypisane do odpowiedzi chatu, a nie
  do deterministycznego pipeline evidence.

Konsekwencje:

- importowany zapis analizy jest read-only dla UI chatu, bo backend nie ma
  lokalnego uchwytu sesji SDK,
- lokalny zapis runu moze byc kontynuowany tylko wtedy, gdy ma
  `copilotSessionId`; brak tego id jest bledem kontynuacji, bez fallbacku do
  nowej sesji,
- chat moze prosic AI o weryfikacje w repo, DB albo wygenerowanie raportu, ale
  model nie powinien wymyslac scope'u ani obchodzic blokady lokalnego workspace.

## 21. Optymalizacje Copilota prowadzimy inkrementalnie

Kolejnosc prac:

1. user-visible usage i baseline jakosci wyniku,
2. JSON response contract,
3. testy kontraktu odpowiedzi,
4. coverage-aware tool policy,
5. incident digest, item IDs i evidence references,
6. tool budget,
7. tool description decorators i audit capture,
8. single prepared analysis flow,
9. dokumentacja, pro context i decision records.

Dopiero po tych warstwach warto dodawac wieksze zmiany, np. soft repair,
multi-stage flow, routing modeli albo alternatywne delivery mode artefaktow.

Przy projektowaniu uzycia Copilot SDK nie wolno opierac decyzji tylko na
publicznych metodach Javy, jesli Java SDK albo bytecode nie wyjasnia semantyki
opcji. Wtedy obowiazkowo sprawdzamy upstream `github/copilot-sdk`, szczegolnie
`nodejs/README.md` oraz schemat/protokol pakietu npm `@github/copilot`, z
ktorego generowane sa kontrakty runtime. To tam trzeba potwierdzac domyslne
wartosci, progi, workspace sesji, eventy i bezpieczny sposob uzycia mechanizmow
SDK, tak jak przy weryfikacji `infiniteSessions`.

## 22. Docelowo Copilot jest parametryzowana platforma runtime

Docelowy `aiplatform.copilot` nie jest wlascicielem analizy incydentu. To
warstwa, ktora zna Copilot SDK, lifecycle sesji, `SessionConfig`, allowliste
tools, hidden context jako mechanizm, invocation handler, policies i
techniczne eventy.

Feature ma przekazac platformie gotowa konfiguracje uruchomienia, np.:

- prompt albo gotowy input do modelu,
- model i `reasoningEffort`,
- promptowe guidance wskazujace starter i workflow skilli,
- tool definitions/callbacks oraz allowliste `availableTools`,
- hidden tool context jako mape danych sesji,
- evidence sink/listeners dla wynikow tooli,
- neutralny identyfikator runu do logow, np. `runReference`,
- parser albo handler odpowiedzi feature'a.

Platforma udostepnia pelny wspolny katalog skilli, ale nie powinna sama
wybierac incident promptu, startera/workflow skilli, GitLab/DB/Elastic tool
policy ani JSON response contractu incydentu. Nie powinna tez zakladac, ze
kazda sesja ma `correlationId`, `environment`, `gitLabBranch` albo
`gitLabGroup`.

To jest warunek dla kolejnych feature'ow. Flow explorer moze potrzebowac
requestu opisowego zamiast `correlationId`, change verification moze budowac
wynik zgodnosci zmiany i smoke pack, a natural-language data diagnostics moze
miec kontrakt nad readonly DB queries. Te roznice musza zostac w
`features.<feature>`, nie w `aiplatform.copilot`.

Obowiazujacy ownership: `features.incidentanalysis.ai.copilot` zawiera
incident-specific prompt, coverage, policy i GitLab/DB capture evidence, a
`aiplatform.copilot` posiada neutralna mechanike runtime i invocation. Nowa
klasa trafia do platformy tylko wtedy, gdy nie wymaga semantyki jednego
feature'a i ma stabilny kontrakt reusable.

Platforma posiada tez lifecycle procesu CLI. Na Windows
`analysis.ai.copilot.cli-path` jest rozwiazywany do istniejacego, bezwzglednego
pliku `.exe` z working directory, `PATH` albo lokalnej instalacji WinGet; shell
wrappers `.cmd`, `.bat` i `.ps1` nie sa dopuszczane. Dzieki
temu SDK przechowuje uchwyt do rzeczywistego procesu CLI zamiast do
krotkozyjacego `cmd.exe`. Kazdy klient utworzony przez aplikacje ma ograniczony
czasowo `stop()`, fallback `forceStop()` i cleanup wykonywany takze po bledzie
`start()`. Nie wolno stosowac globalnego zabijania procesow po nazwie, bo ten
sam host moze uruchamiac niezalezne procesy Copilota nalezace do IDE lub
operatora.

## 23. Shared/operator API jest osobna kategoria

Nie kazdy endpoint backendu dla frontendu jest czescia dedykowanego feature'a.
Endpointy wspolne dla wielu ekranow albo bedace cienka fasada nad platforma
lub integracjami traktujemy jako shared/operator API.

Zasady:

- `features.<feature>.api` posiada endpointy konkretnego use case'u, np.
  incident job API,
- `api.*` jest miejscem dla cross-screen endpointow FE/operatora,
  np. katalogu opcji AI albo stabilnych fasad nad adapterami,
- stabilne helper endpointy Elasticsearch/GitLab mieszkaja w `api.*`,
- adapter, porty, service i modele request/result zostaja w `integrations.*`.

Konsekwencja dla obecnego kodu: historyczne `analysis.options` jest zamkniete.
Neutralne `AnalysisAiOptions` mieszka w `shared.ai`, HTTP fasada
`GET /analysis/ai/options` w `api.aioptions`, a katalog modeli Copilota zostaje
w `aiplatform.copilot.runtime.options`.

## 24. Local workspace jest stanem kontynuowalnym, export jest read-only

Aplikacja uruchamiana jako lokalny JAR uzywa prywatnego katalogu workspace'u
przekazanego przez `tdw.workspace.directory`. Domyslny launcher ustawia
`tdw-data` obok skryptu/JAR-a.

Decyzje MVP:

- `index.json` jest lekkim read modelem dla ekranu `Analysis History`; lista
  historii nie laduje wszystkich `run.json` i niesie status ostatniego
  snapshotu runu,
- pelny lokalny rekord jest w `runs/<analysisId>/run.json` i jest ladowany
  dopiero przy otwarciu, eksporcie albo kontynuacji konkretnego runu,
- run moze byc zapisany juz po utworzeniu joba (`QUEUED`) i potem nadpisywany
  kolejnymi snapshotami progressu; jest to stan operator UI/history, a nie
  kolejka workerow ani gwarancja wznowienia po restarcie backendu,
- `tokens.json` lezy obok `index.json`, przechowuje lokalne access tokeny
  zapisane z UI i nie jest czescia exportu,
- `settings.json` lezy obok `index.json` i przechowuje lokalne override'y
  workspace'u; gdy pole jest ustawione w tym pliku, ma pierwszenstwo przed
  `application.properties`, a puste albo identyczne z bazowa konfiguracja pole
  usuwa override,
- stan Copilota jest pod `${tdw.workspace.directory}/copilot`, zeby
  `resumeSession` moglo korzystac z tego samego lokalnego workspace'u,
- historia lokalnych runow jest shared/operator API pod `/analysis/runs`, a
  live polling joba zostaje przy `GET /api/analysis/jobs/{analysisId}`,
- export lokalnego runu zwraca tylko sanitizowany `exportEnvelope`; import
  exportu jest read-only i nie tworzy kontynuowalnego runu,
- w V1 nie ma osobnego diagnostic exportu; ewentualny tryb diagnostyczny musi
  miec osobny kontrakt,
- w V1 retencja jest reczna: uzytkownik usuwa run w UI albo caly katalog
  `tdw-data`; automatyczna retencja nie jest czescia MVP,
- usuniecie pojedynczego runu probuje best-effort usunac powiazana sesje
  Copilota przez SDK `deleteSession` i lokalny katalog
  `copilot/session-state/<copilotSessionId>`, gdy rekord kontynuacji zawiera
  `copilotSessionId`; awaria cleanupu Copilota nie blokuje usuniecia historii.

Pelny backup kontynuowalnego workspace'u oznacza skopiowanie calego katalogu
`tdw-data`, najlepiej przy zatrzymanej aplikacji. Zwykly export JSON nie jest
backupem sesji ani tokenow.

## 25. Konstruktory produkcyjne sa dla runtime, nie dla testow

Klasy implementacyjne powinny pokazywac realny runtime wiring. Domyslnym
wzorcem jest `@RequiredArgsConstructor` na finalnych zaleznosciach i jeden
konstruktor uzywany przez Spring.

Nie dodajemy recznych konstruktorow do produkcyjnej klasy tylko po to, zeby
skrocic setup testow albo ukryc testowe defaulty. Takie konstruktory zaciemniaja
odpowiedzialnosc klasy i utrudniaja czytanie runtime flow.

Jesli test potrzebuje wygodniejszego skladania zaleznosci, uzywa creatora albo
buildera w odpowiadajacym pakiecie `src/test/java`. Creator moze ustawiac
testowe defaulty, mocki, no-op persistence albo synthetic adaptery, ale nie
zmienia kontraktu konstruktora klasy implementacyjnej.

Przyklad aktualnego wzorca: `AnalysisJobFacade` ma Lombokowy konstruktor
runtime, a testy skladaja ja przez `AnalysisJobFacadeTestCreator`.

## 26. Weryfikacja konfiguracji rozdziela fakty od drugiej opinii AI

Config Drift Viewer ma dwie niezalezne warstwy wyniku.
Deterministyczny parser/diff jest zrodlem faktow i pozostaje niemutowalny dla
AI. Jeden deterministic build tworzy sanitizowany context oraz operatorski
`configurationDiff` per plik. `BASIC` publikuje tylko wynik deterministyczny.
W `DEEP` AI otrzymuje sanitizowany manifest zachowujacy strukture YAML, typy,
sciezki i pseudonimy niesensytywnych wartosci; zwraca osobna interpretacje z
referencjami do diffow/findings oraz agreement/disagreement.

Granica bezpieczenstwa:

- dokladne wartosci odczytane z plikow moga trafic do operatorskiego UI,
  lokalnej historii, eksportu i projekcji Workbench; nie trafiaja do promptu,
  AI artifacts, activity, reportu, logow ani user-facing bledu,
- `configurationDiff` nie zawiera byte-identical plikow, komentarzy, tokenu
  GitLaba ani wartosci sekretow z Vault; DTO projekcji maja redacted
  `toString`,
- repozytorium konfiguracji uzywa allowlisty nazwanych polaczen GitLab i
  backendowego limitu rozmiaru pliku,
- `BASIC` nie rozwiazuje auth Copilota i nie uruchamia prompt preparation,
  runtime skilla, reportu, runnera ani tools,
- `DEEP` moze uzywac tylko code-search scope wybranego `internal-service`,
  potwierdzonego refu i feature-specific budgetu,
- brak lub awaria enrichmentu nie usuwa deterministic result; obniza
  kompletnosc i jest widoczna jako blocker albo visibility limit.

Zachowanie calego, sanitizowanego schematu po stronie `DEEP` jest celowe:
niezmienione parametry pomagaja rozpoznac funkcjonalny kontekst rozjazdu bez
przekazywania prawdziwej konfiguracji. Warstwa AI nie moze zalezec od pakietu
operatorskiej projekcji; te granice egzekwuje test architektoniczny.

Publiczny job Config Drift Viewer jest batchowy: przyjmuje uporzadkowane
`systemIds`, utrzymuje izolowany component snapshot dla kazdego systemu i
agreguje parent status bez zatrzymywania pozostalych komponentow po lokalnym
bledzie. Wykonanie ma konfigurowalny, ograniczony fan-out, a UI zachowuje
kolejnosc requestu w zakladkach.

Export/result contract oraz kompaktowy format AI/Workbench sa pierwsza
kanoniczna wersja V1. Poniewaz feature nie mial konsumentow wymagajacych
migracji, nie utrzymujemy aliasow, parserow ani fallbacku poprzednich wersji.
Tool Workbench reuse'uje produkcyjny pipeline w readonly preview pojedynczego
scope'u, ale nie tworzy joba, historii, eksportu ani sesji AI.

## 27. Operational Context MVP ma jedna lokalna, niewersjonowana kopie

`src/main/resources/operational-context` jest immutable seedem dystrybucyjnym.
Pierwszy start kopiuje komplet dokumentow do
`${tdw.workspace.directory}/operational-context`, domyslnie
`tdw-data/operational-context`. Runtime nie ma trybu classpath/workspace:
wszystkie odczyty, tools, feature'y i mutacje korzystaja z lokalnej kopii, a
restart lub aktualizacja JAR-a nie nadpisuje jej seedem.

MVP jest przeznaczony do lokalnej pracy uzytkownika na jego danych. Dlatego
maintenance API nie ma security/rollout gate, revision, ETag, `If-Match`,
manifestow, historii ani rollbacku katalogu. Pozostaja walidacje kontraktu,
referencji i spojnosci oraz `RESTRICT` delete. Jedna komenda zmienia dokladnie
jeden logiczny dokument, ktory jest podmieniany atomowo po walidacji. Backup i
odzyskanie poprzedniego stanu sa odpowiedzialnoscia uzytkownika. Deployment
wspoldzielony albo sieciowy wymaga osobnej decyzji obejmujacej security,
concurrency i persistence.

## 28. Delivery Complexity Assessment rozdziela discovery, interpretacje i scoring

Operator podaje typowane kryteria `projectKey`, `fromDate` i `toDate`, a nie
surowy JQL. Reusable adapter Jira mapuje je na ograniczone zapytanie JQL,
pobiera issues oraz historie statusow, natomiast feature rozstrzyga semantyke
okna czasowego. Do analizy trafia tylko issue, ktore nadal jest Done i ktorego
ostatnie przejscie do Done miesci sie w zadanym oknie w skonfigurowanej strefie
czasowej.
Pobieranie per-issue materialu Jiry i powiazanych MR-ek GitLaba jest
rownoleglone tylko przez feature-owned, ograniczony executor source discovery;
kolejnosc wyniku pozostaje deterministyczna wedlug Jira search.

Run jest zapisywany przed uruchomieniem pracy asynchronicznej, dzieki czemu od
razu pojawia sie w `Analysis History`. Kolejne snapshoty aktualizuja ten sam
rekord w shared `/analysis/runs`; restart UI odtwarza live job przez
`localRunId`, ale MVP nie wznawia przerwanej pracy backendu.

Delivery Unit jest deterministycznym komponentem spojnym grafu Jira issue i
merged Merge Requestow. AI ocenia jedna jednostke na podstawie ograniczonego
evidence packetu i zwraca tylko szesc wymiarow `0-4`, confidence, rationale
oraz gaps. Istniejace Story Points, worklogi, komentarze i dane osobowe nie sa
przekazywane do AI. Backend wylicza wynik na skali `0/1/2/3/5/8/13`, deduplikuje
jednostki i agreguje sume. Braki danych, limity i lokalne awarie pozostaja
jawne zamiast byc zastapione estymacja.

Znaczenie skali `0-4` nie jest pozostawione intuicji modelu. Kazdy wymiar ma
w runtime skillu osobne kotwice behawioralne `0/2/4`, a `1/3` sa poziomami
posrednimi. Kazdy niezerowy wynik wymaga referencji do artifactu i
obserwowanego faktu; parser odrzuca odpowiedz bez takiego pokrycia. Syntetyczne
przypadki kalibracyjne stabilizuja skale bez uczenia jej na historycznych Story
Points.

Assessment uzywa sesji one-shot bez tools. Effective tresc skilla, instrukcja,
Jira evidence, kod MR i kontrakt odpowiedzi sa splaszczone do jednej wiadomosci,
a built-in `skill` jest dla tej sesji wylaczony. Dokladny prompt jest zapisywany
na jednostce przed wyslaniem i pokazywany w kroku `AI_INPUT_PREPARATION`.
Raport jednostki powstaje deterministycznie z finalnego JSON-a. SDK `cost` jest
agregowany jako mnoznik rozliczeniowy i nie jest prezentowany jako USD; koszt
tokenow w UI pozostaje osobnym oszacowaniem.
