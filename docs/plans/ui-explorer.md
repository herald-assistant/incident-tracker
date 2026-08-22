# UI Explorer - MVP dokumentacji widoku

Status: in-progress

Source need: [UI Explorer - dokumentacja funkcjonalna widokow](../needs/ui-explorer.md)

Klasyfikacja: L2 - nowy feature analityczny L1, breaking evolution wspolnego
modelu Operational Context oraz nowa neutralna capability deterministycznego
rozpoznawania frontendu w integracji GitLab. Plan nie wprowadza nowego kierunku
zaleznosci, platformy runtime ani modelu bezpieczenstwa. Gdyby implementacja
wymagala wykonywania badanego frontendu, nowej zaleznosci parsera o istotnym
zasiegu, dostepu do sesji przegladarki lub nowego modelu credentiali, zakres
wymaga ponownej klasyfikacji przed ta zmiana.

Ten dokument jest propozycja wykonania. Status `draft` nie jest zgoda na
implementacje. Kazdy inkrement wymaga osobnej akceptacji zgodnie z
`docs/AGENTS.md`.

## Potrzeba i wynik produktu

UI Explorer ma zamieniac kod zlozonego frontendu w ustrukturyzowany raport o
jednym ekranie i scenariuszu. Uzytkownik wybiera system, wersje, ekran i
glebokosc sekcji. Platforma samodzielnie buduje ograniczony kontekst, pozwala
AI uzupelnic go przez read-only tools i zwraca funkcjonalny raport biznesowy
z dowodami, poziomami pewnosci oraz ograniczeniami widocznosci.

Feature jest rodzenstwem Incident Analysis, Flow Explorer, Config Drift Viewer,
Change Verification i Delivery Complexity Assessment. Nie zalezy od ich
pakietow, kontraktow joba, evidence pipeline ani skilli.

## Zakres MVP

- frontend jawnie zarejestrowany jako osobny system
  `systemType: internal-service` i `systemSubtype: frontend`,
- dokladnie jeden system-targeted code-search scope dla wybranego frontendu,
- jeden primary frontend repository source w tym scope; pozostale repozytoria
  scope nie sa traversowane w MVP,
- statyczna analiza kodu z wybranego branch/ref i przypisanie wyniku do
  rozpoznanej rewizji,
- katalog ekranow budowany z routingu i glownych komponentow widoku,
- jeden cel produktu: dokumentacja funkcjonalna bez publicznego pola profilu,
- osiem stabilnych sekcji raportu z trybami `OFF`, `COMPACT`, `DEEP`,
- deterministyczny pakiet route chain, component BFS i symbol slices przed
  uruchomieniem AI,
- poglebianie przez istniejace neutralne GitLab search/read tools,
- jawna ocena gotowosci i pokrycia sekcji przed zapisem raportu,
- asynchroniczny job, historia lokalna, import/export wersjonowanego JSON oraz
  eksport/copy raportu,
- nowy ekran Angular korzystajacy ze wspolnego shellu analizy, postepu, tool
  evidence, reportu, AI options i pollingu.

## Poza zakresem MVP

- uruchamianie badanego frontendu i automatyzacja przegladarki,
- logowanie do badanego systemu, przejmowanie sesji Keycloak lub credentiali,
- screenshot analysis i pixel-perfect dokumentacja,
- analiza implementacji backendu wykraczajaca poza kontrakty i wywolania
  widoczne z frontendu,
- automatyczna publikacja wyniku albo modyfikacja badanego repozytorium,
- trwaly globalny knowledge graph calego frontendu,
- multi-repository traversal w jednym runie,
- follow-up chat i continuation,
- porownanie dwoch wersji ekranu,
- generowanie albo wykonywanie testow badanego systemu.

## Baseline repozytorium

Platforma posiada juz:

- neutralny `CopilotRunRequest`, runtime session, hidden tool context,
  allowliste, polityki, budzety, eventy tool invocation i user-facing tool
  evidence,
- wspolne modele krokow, `contextSections`, activity, AI usage i reportu,
- `AnalysisReport` i renderer raportu uzywane przez kilka feature'ow,
- lokalna historie analiz oraz wzorce wersjonowanego import/export,
- `AiOptionsApiService` i `AnalysisJobPollingService` po stronie Angulara,
- wspolne komponenty przebiegu runu, evidence, raportu i shellu operatora,
- Operational Context wskazujacy systemy i repository scopes,
- integracje GitLab z odczytem plikow, wyszukiwaniem tresci, metadanymi branchy
  oraz genericznymi tools search/read.

Aktualna luka:

- `systemType` jest plaskim polem, a model nie ma `systemSubtype`,
- API, UI i maintenance Operational Context nie potrafia jawnie odroznic
  frontendu od innych `internal-service`,
- brak katalogu tras i ekranow frontendu,
- brak deterministycznego grafu laczacego route, template, komponenty,
  formularze, store i faktycznie uzywane operacje klientow API,
- wyspecjalizowane GitLab flow/context capability sa obecnie nastawione na
  kod Java i endpointy backendowe,
- brak kontraktu, skilli, policy, joba, historii i UI dla dokumentacji widoku,
- Operational Context celowo nie przechowuje zmiennego inwentarza tras,
  komponentow ani endpointow konkretnej rewizji.

Katalog ekranow i wynik screen reachability pozostaja danymi wyprowadzonymi z
konkretnej rewizji kodu. Nie sa nowymi bytami katalogowymi Operational Context.

## Rejestracja frontendu w Operational Context

Frontend jest osobnym, kanonicznym systemem, nawet gdy dzieli monorepo z
backendem. Minimalna kompletna rejestracja sklada sie z trzech bytow:

1. `systems.yml`: `systemType: internal-service` oraz
   `systemSubtype: frontend`,
2. `repo-map.yml`: repository z `repositoryType: frontend` i kanonicznym
   `git.projectPath`,
3. `code-search-scopes.yml`: dokladnie jeden scope targetujacy system, z jednym
   repository `role: primary` oraz jawnym `searchMode`.

UI Explorer nie przyjmuje surowego repository id. `input-options` pokazuje
tylko systemy spelniajace powyzszy kontrakt. System z brakujacym subtype,
scope, primary repository albo niejednoznacznym primary jest pomijany i
zwracany jako jawny validation/configuration finding dla operatora.

Dla `systemType: internal-service` pole `systemSubtype` staje sie wymagane.
Kanoniczne MVP vocabulary to `frontend`, `backend`, `worker`, `mixed` i
`unknown`. `unknown` oznacza jawnie nieustalona klasyfikacje i nie kwalifikuje
systemu do UI Explorer. Nie wolno automatycznie ustawic `backend` ani
`frontend` na podstawie nazwy repozytorium.

Maintenance obejmuje aktualizacje:

- `operational-context-field-guidance.md`,
- `systems-yml-update-prompt.md`,
- `repo-map-yml-update-prompt.md`,
- `code-search-scopes-yml-update-prompt.md`,
- `operational-context-fill-order.md`,
- PowerShellowych checkpointow validation/cleanup, jezeli ich zachowanie lub
  raport wymaga dostosowania do breaking schema.

Przyklady maintenance, testy i fixtures uzywaja wylacznie silnie
zanonimizowanej domeny CRM, np. `crm-agent-portal`; nie kopiuja prawdziwych
nazw systemow, repository paths, ekranow, rol, endpointow ani payloadow.

## Polityka braku kompatybilnosci wstecznej

W zakresie kontraktow zmienianych przez ten plan nie utrzymujemy warstwy
kompatybilnosci:

- brak dual-read/dual-write i legacy aliasu dla `systemSubtype`,
- wszystkie bundled katalogi oraz konsumenci API/read modelu sa migrowani
  atomowo do docelowego kontraktu,
- brak fallbacku wyznaczajacego frontend z `package.json`, nazwy repozytorium
  albo `systemType` bez subtype,
- UI Explorer nie publikuje legacy request/result/job endpointow,
- import nieznanej lub starszej wersji exportu jest jawnie odrzucany zamiast
  cicho mapowany do nowego kontraktu.

Ta zgoda nie obejmuje przypadkowego lamania niezaleznych publicznych API.
Jezeli wspolny kontrakt ma konsumentow poza zmienianym zakresem, sa oni
aktualizowani i testowani w tym samym inkremencie; zmiana nie pozostawia
czasowego stanu mieszanego.

## Docelowe granice ownership

```text
features.uiexplorer
  request/result/report/section contract
  screen analysis context pipeline
  prompt/artifacts/skills guidance/tool policy
  async job/history/import-export/API

integrations.gitlab
  neutralne, bounded rozpoznanie Angular/Nx source tree
  katalog route/view i screen source context
  istniejace search/read/branch/revision capability

integrations.operationalcontext + api.operationalcontext
  kanoniczny systemType/systemSubtype
  repository i system-targeted code-search scope
  validation, maintenance API i operator-facing read models

agenttools.gitlab + aiplatform.copilot
  istniejace read-only search/read tools i neutralna mechanika runtime

shared + api + frontend shared components
  stabilne modele przebiegu, raport, usage, polling i wspolne UI
```

`features.uiexplorer` moze zalezec od integracji, agenttools, aiplatform,
shared i common. Zaden z tych pakietow nie moze importowac feature'a. Nowy
feature nie importuje klas z `features.flowexplorer` ani
`features.incidentanalysis`.

## Publiczny kontrakt MVP

### Identyfikacja

- feature id i slug: `ui-explorer`,
- backend package: `pl.mkn.tdw.features.uiexplorer`,
- route UI: `/ui-explorer`,
- lokalna historia i export maja wlasny typ feature'a, bez podszywania sie pod
  Flow Explorer.

### Input options i katalog ekranow

```text
GET /api/ui-explorer/input-options
GET /api/ui-explorer/screens?systemId={systemId}&branch={branch}
```

`input-options` zwraca tylko systemy posiadajace
`systemType: internal-service`, `systemSubtype: frontend`, dokladnie jeden
system-targeted code-search scope i jednoznaczne primary repository. Odpowiedz
zawiera tez sekcje, ich tryby oraz jedna domyslna konfiguracje funkcjonalna.

Katalog ekranow zwraca co najmniej:

- `screenId` - stabilny w obrebie repository i rewizji identyfikator wyboru,
- business-friendly `label`,
- route pattern lub rodzaj punktu wejscia,
- parent/navigation context, jezeli jest rozpoznawalny,
- sygnaly lazy loading, guardow i parametrow,
- `sourceRevision`,
- status rozpoznania i ograniczenia katalogu.

`screenId` nie jest dowolna sciezka pliku podawana przez uzytkownika. Backend
waliduje, ze pochodzi z katalogu dla wskazanego systemu i branch/ref.

### Start i odczyt joba

```text
POST /api/ui-explorer/jobs
GET  /api/ui-explorer/jobs/{jobId}
```

Start zwraca `202 Accepted` oraz pierwszy snapshot. Request zawiera:

- `systemId`,
- `branch`,
- `screenId`,
- `sourceRevision` przekazana z odpowiedzi katalogu ekranow,
- mape `sectionModes`,
- opcjonalne `scenarioDescription`,
- opcjonalne neutralne preferencje AI `model` i `reasoningEffort`.

Walidacja:

- dokladnie jeden system, branch/ref i ekran,
- wybrany system nadal spelnia frontend registration contract,
- maksymalnie osiem sekcji innych niz `OFF`,
- co najmniej jedna sekcja aktywna,
- kontrolowany limit dlugosci opisu scenariusza,
- enumy i mapa bez nieznanych kluczy,
- branch/ref musi istniec, a `screenId` nalezec do zbudowanego katalogu,
- `sourceRevision` musi nadal odpowiadac rewizji branch/ref; zmiana rewizji
  albo znikniecie ekranu wymaga odswiezenia katalogu,
- brak model-facing repository id, grupy GitLab, tokenu albo sciezki pliku.

Snapshot joba reuse'uje wspolne modele, ale pozostaje kontraktem feature'a.
Zawiera co najmniej:

- identyfikator, status, timestamps i bezpieczny snapshot requestu,
- `steps`, `contextSections`, `toolEvidence`, `activity` i `toolFeedback`,
- publiczne `preparedPrompt` pozostaje `null`, dopoki prompt zawiera surowa
  tresc source evidence; wewnetrzny bundle nie jest kontraktem operatora,
- `result`, `report`, `usage`, blad oraz source revision,
- stan eksportowalnosci.

MVP nie wystawia endpointu chatu.

## Dokumentacja funkcjonalna i sekcje

UI Explorer ma jeden cel produktu i jeden result contract: dokumentacje
funkcjonalna. Domyslne tryby preferuja kontekst, strukture, akcje, formularze
i warianty, a techniczne sygnaly pozostaja dowodami pomocniczymi.

Kanoniczne sekcje i ich id:

1. `OVERVIEW`
2. `NAVIGATION_AND_ACCESS`
3. `SCREEN_STRUCTURE`
4. `ACTIONS_AND_OUTCOMES`
5. `FORMS_AND_RULES`
6. `DATA_AND_SERVICES`
7. `STATE_AND_SYNCHRONIZATION`
8. `VARIANTS_AND_FAILURES`

Kazda aktywna sekcja ma mikro-kontrakt:

- czytelne biznesowo podsumowanie,
- uporzadkowane findings zamiast swobodnego eseju,
- zaleznosci i warunki wykonania,
- source references do repozytorium i symbolu/obszaru, gdy sa dostepne,
- `CONFIRMED`, `INFERRED` albo `UNKNOWN` dla kluczowych twierdzen,
- visibility limits i otwarte pytania,
- coverage status `READY`, `PARTIAL` albo `BLOCKED`.

## Zasady analizy formularzy dynamicznych

Sekcja `FORMS_AND_RULES` rozroznia trzy poziomy precyzji:

1. **Jawna definicja w kodzie** - pola, reguly i zaleznosci moga zostac
   opisane jako potwierdzone z referencjami.
2. **Custom control lub builder dostepny w repozytorium** - raport opisuje
   semantyke wynikajaca z API i uzycia; zachowanie wspolne wymaga odczytu
   implementacji kontrolki/buildera.
3. **Definicja runtime albo niedostepna biblioteka** - raport opisuje miejsce
   pobrania, mapowanie i obslugiwane sygnaly, ale konkretne pola lub reguly sa
   `UNKNOWN`, dopoki ich definicja nie znajduje sie w evidence.

Dla harmonogramow i innych wartosci automatycznie wyliczanych raport osobno
pokazuje:

- trigger i wejscia wyliczenia,
- rezultat poczatkowy,
- zakres recznej edycji,
- walidacje wykonywane po korekcie,
- warunki ponownego przeliczenia albo utraty zmian,
- operacje zapisu i obsluge konfliktu/bledu.

Sam fakt wywolania backendu nie uprawnia AI do dopowiedzenia jego algorytmu.

## Deterministyczny context pipeline

Przed uruchomieniem AI feature buduje bounded snapshot:

1. Rozwiazuje system `internal-service/frontend`, jego pojedynczy systemowy
   code-search scope i primary repository z Operational Context.
2. Waliduje branch/ref i zapisuje source revision.
3. Rozpoznaje konfiguracje workspace, route tree, lazy routes, redirecty,
   guardy i route-to-view roots bez globalnego indeksowania calego kodu.
4. Waliduje `screenId` i zbiera ograniczony screen source context:
   komponenty wejscia, template, bezposrednie style i konfiguracje, jawne
   children, formularze, akcje, store/API/WebSocket/auth candidates oraz
   istotne importy.
5. Buduje neutralny manifest plikow/symboli i wstepne coverage dla kazdej
   aktywnej sekcji.
6. Renderuje logical artifacts dla sesji Copilota.
7. AI poglebia tylko luki przez dozwolone search/read tools w ramach ukrytego
   repository/branch contextu i budzetu.
8. Readiness gate rozstrzyga, czy sekcja jest `READY`, `PARTIAL` lub `BLOCKED`;
   dopiero potem writer tworzy report.

Minimalne logical artifacts:

- `ui-explorer/request.json`,
- `ui-explorer/screen-catalog-entry.json`,
- `ui-explorer/context-snapshot.json`,
- `ui-explorer/evidence-manifest.md`,
- `ui-explorer/coverage.json`,
- `ui-explorer/response-contract.json`.

Snapshot ma twarde limity liczby plikow, rozmiaru tresci, liczby kandydatow i
glebokosci zaleznosci. Przekroczenie limitu jest visibility limit, a nie
powodem do cichego obciecia udajacego kompletna analize.

## Neutralna capability rozpoznawania frontendu

Nowa capability w `integrations.gitlab` ma pozostac niezalezna od UI Explorer
i Copilot SDK. Przyjmuje repository scope i revision rozstrzygniete przez
aplikacje, a zwraca typowane modele:

- workspace/framework signals,
- route/view catalog,
- selected screen source context,
- source manifest i coverage/diagnostics.

Capability obsluguje inkrementalnie najczestsze, statycznie rozpoznawalne
wzorce nowszego Angulara:

- standalone i module-based routes,
- `loadComponent`, `loadChildren`, redirects, guards i route parameters,
- komponenty z template/style inline oraz przez `templateUrl`/`styleUrls`,
- Reactive Forms i sygnaly customowych builderow/kontrolek,
- jawne NgRx actions/selectors/effects/reducers oraz store dispatch/select,
- wygenerowane klienty REST i bezposrednie wywolania `HttpClient`,
- jawne WebSocket/RxJS stream sources,
- widoczne role/permission checks i auth guards.

Rozpoznawanie ma byc heurystyczne i bounded, nie pelnym kompilatorem
TypeScript. Parser oparty wylacznie na regexach nie moze byc traktowany jako
pewne zrodlo zlozonych zaleznosci. Jezeli potrzebna bedzie nowa biblioteka
parsera, przed jej dodaniem trzeba zaktualizowac klasyfikacje, dependencies,
licencje, wplyw na build i plan rollbacku.

MVP przekazuje deterministyczny snapshot AI i reuse'uje istniejace genericzne
GitLab search/read tools do poglebiania. Nie dodaje UI-Explorer-specific tooli
do `agenttools` ani nazw tooli zawierajacych semantyke feature'a.

## Workflow AI i skille

Runtime workflow:

1. `ui-explorer-orchestrator` - czyta request, artifacts i section modes,
   prowadzi readiness ledger oraz przekazuje prace pomiedzy pozostalymi
   skillami.
2. `ui-explorer-source-grounding` - potwierdza granice ekranu, source
   references i poziom pewnosci dla aktywnych sekcji, w tym formularzy,
   state management, API, WebSocket i autoryzacji.
3. `ui-explorer-write-report` - jako jedyny tworzy wynikowy JSON zgodny z
   jednym `UiExplorerResultResponse`, dopiero po readiness review.

Skille sa runtime resources pod `src/main/resources/copilot/skills`, napisane
po polsku z zachowaniem identyfikatorow technicznych. Kazdy ma jedna
odpowiedzialnosc; nie duplikuja pelnych procedur ani kontraktu odpowiedzi.

Feature dostarcza prompt, starter guidance, artifacts, available tools,
hidden context, tool description customizations, budget policy, evidence sink
i response parser przez `CopilotRunRequest`. Platforma nie wybiera skilli ani
semantyki UI Explorer.

Tool policy jest default-deny. Dozwolone sa tylko:

- wymagane skills/report tools,
- neutralne Operational Context tools, jezeli snapshot ma jawna luke,
- ograniczony zestaw GitLab search/read tools dla wybranego repository i
  source revision.

Nowy kontrakt nie powiela znanego driftu przez wystawienie `gitLabGroup`,
`branchRef` albo repository path jako model-facing scope. Scope pochodzi z
hidden `ToolContext`. Jesli istniejacy genericzny tool nadal ma legacy input,
feature izoluje jego uzycie i nie utrwala tego wzorca w nowych kontraktach.

## Result i report

Publiczny result jest typowanym kontraktem feature'a, a nie dowolnym Markdown.
Zawiera:

- screen identity, scenario i source revision,
- executive/functional overview,
- osiem sekcji z mode, coverage i typed findings,
- cross-section dependencies,
- overall confidence,
- visibility limits i unresolved questions,
- usage.

Assembler deterministycznie mapuje result na `AnalysisReport`. Glowne sekcje
raportu sa stabilne, a source references i evidence pozostaja w komponentach
raportowych mozliwych do zweryfikowania. Surowy wynik modelu nie omija
assemblera.

## Historia, portability i wersjonowanie

- Zakonczone i nieudane runy sa zapisywane w lokalnej historii zgodnie ze
  wspolnym wzorcem platformy.
- Export jest wersjonowanym, sanitizowanym JSON-em bez tokenow, hidden context,
  credentiali i surowych sekretow.
- Import odtwarza read-only snapshot/history detail; nie wznawia sesji AI.
- Raport mozna skopiowac lub wyeksportowac w formie czytelnej dla analityka.
- Schema exportu ma jawna wersje oraz testy poprawnego odrzucania starszej i
  nieznanej wersji bez fallbacku.
- Source revision jest czescia resultu, reportu i exportu.

## UI/UX

Nowa strona korzysta ze wzorca innych analysis workspaces:

- kompaktowy header i konfiguracja bez marketing hero,
- selektory systemu, branch/ref i ekranu,
- advanced section modes schowane za progresywnym ujawnieniem,
- opis scenariusza jako opcjonalne pole doprecyzowujace,
- wspolny aside dla modelu/reasoning effort,
- wspolne kroki, activity, tool evidence i report renderer,
- polling przez `AnalysisJobPollingService`,
- AI options przez `AiOptionsApiService`,
- loading/empty/error states dla katalogu ekranow i runu,
- nowe wpisy shell navigation, landing page i Analysis History.

Strona nie moze kopiowac monolitycznego wzorca komponentu Flow Explorer.
Stan orkiestracji trafia do feature facade/store, a konfiguracja, katalog,
postep i wynik do osobnych komponentow prezentacyjnych. Komponent strony
pozostaje kompozycja.

Nazwy biznesowe nie eksponuja `MCP`, `tool`, `NgRx`, `AST` ani nazw plikow w
podstawowym przebiegu. Techniczne evidence jest dostepne w warstwie szczegolow.

## Bezpieczenstwo i trust boundaries

- Kod, komentarze, README, template i dane z badanego repozytorium sa
  niezaufanym evidence, a nie instrukcjami dla modelu.
- Sesja pozostaje read-only, z zablokowanym lokalnym filesystemem, shellem i
  terminalem.
- Repository, branch/ref i system scope sa rozstrzygane oraz walidowane przed
  sesja i przekazywane jako hidden context.
- Wynik nie zawiera tokenow, credentiali, pelnego hidden context ani sekretow
  znalezionych w kodzie.
- Limity rozmiaru, timeouty i budzet tool calls chronia przed nieograniczona
  eksploracja monolitu.
- Widocznosc kontrolki dla roli jest oznaczana jako client-side behavior, nie
  backend authorization guarantee.
- API korzysta z istniejacej ochrony aplikacji i walidacji DTO; feature nie
  tworzy nowego modelu sesji ani uwierzytelnienia.

## Conformance delta

| Obszar | Baseline | Zmiana MVP | Docelowa zgodnosc |
| --- | --- | --- | --- |
| Feature ownership | brak UI Explorer | nowy `features.uiexplorer` | sibling bez importow innych feature'ow |
| Source context | generic GitLab read/search, Java-oriented deep context | bounded Angular route/screen capability | neutralna integracja bez zaleznosci od feature'a |
| Operational Context | plaski `systemType`, repository i code-search scopes | wymagany `systemSubtype`, migracja konsumentow i frontend eligibility | jawny `internal-service/frontend`, nadal bez route/component inventory |
| AI runtime | neutralny `CopilotRunRequest` | feature prompt, skills, artifacts i policy | platforma bez semantyki UI Explorer |
| Public API | brak | input options, screens, async jobs | thin controllers i typowane DTO |
| Result/report | shared report primitives | wlasny result + deterministic assembler | report-first i wspolny renderer |
| Historia | shared wzorce per feature | UI Explorer history/import/export | typowany, wersjonowany, sanitizowany format |
| Frontend | shared shell, polling, AI options, evidence, report | nowy workspace i katalog ekranow | reuse bez kopiowania wspolnych mechanizmow |

## Lista konsumentow i wplyw

### Integracja GitLab

Aktualni konsumenci obejmuja Incident Analysis, Flow Explorer, Change
Verification, Config Drift Viewer, Delivery Complexity Assessment,
Operational Context oraz Tool Workbench. Nowa capability jest addytywna i nie
zmienia semantyki istniejacego odczytu plikow, wyszukiwania, branch resolution
ani Java context builders. Testy regresji maja potwierdzic brak nowych importow
z `features.*` i brak zmiany istniejacych publicznych tools.

### Shared backend i runtime

UI Explorer konsumuje istniejace modele report/usage/evidence/activity i
mechanike Copilota. Plan nie rozszerza ich tylko dla wygody feature'a. Jezeli
ujawni sie faktycznie wspolny, maly kontrakt, ekstrakcja wymaga osobnego punktu
planu oraz audytu wszystkich konsumentow.

### Frontend

Konsumentami zmienionej nawigacji beda shell, landing page i Analysis History.
Istniejace feature pages pozostaja bez zmian kontraktowych. Wspolne
`AiOptionsApiService`, `AnalysisJobPollingService`, progress/evidence/report
components sa konsumowane, nie duplikowane.

### Operational Context

Zmiana dotyka loadera i DTO integracji, maintenance schema/service, walidatora,
shared/operator API, operational context tools/read models, Angularowego
edytora i wszystkich feature'ow filtrujacych dotychczas `system.kind()`. Audit obejmuje
rowniez bundled YAML, effective catalog seed, import/export maintenance oraz
procedury w `operational-context-maintenance/`. Wszyscy konsumenci sa
migrowani w jednym inkremencie; nie zostaje przejsciowy fallback bez subtype.

## Macierz weryfikacji

| Warstwa | Wymagana weryfikacja |
| --- | --- |
| Operational Context | wymagany subtype dla `internal-service`, vocabulary, CRUD/read models/tools, bundled migration, primary scope eligibility i maintenance checkpoints |
| Kontrakty/API | walidacja requestu, enumow, screen membership, `202`, snapshot i bledy przez MockMvc |
| GitLab capability | fixtures standalone/module routes, lazy loading, redirects, guards, inline/external template, limits i niejednoznacznosci |
| Context pipeline | source revision, bounded traversal, form/store/API/WS/auth signals, coverage i visibility limits |
| AI boundary | prompt/artifacts, allowlista, hidden scope, budget, readiness, parser i malformed response |
| Result/report | dokumentacja funkcjonalna, wszystkie modes, confidence, limits, deterministic assembly i brak surowego bypassu |
| Job/history | transitiony, failure/partial result, persistence, sanitizacja oraz versioned import/export |
| Architecture | package dependency guards i brak importow feature -> feature / integrations -> feature |
| Angular API/facade | input options, screen catalog, start/poll, terminal/error state i retry |
| Angular UI | konfiguracja, progressive disclosure, steps, evidence, report, history, loading/empty/error i accessibility |
| Security | prompt injection fixtures, secret redaction, path/scope validation, tool budget i read-only allowlista |

Globalny niezmiennik macierzy: kazdy test, fixture, snapshot, artifact i
przyklad domenowy dodany lub zmieniony przez ten plan jest silnie
zanonimizowany i dotyczy wylacznie CRM. Dozwolone sa syntetyczne nazwy w stylu
`crm-agent-portal`, `crm-contact-form` i `crm-contact-api`; zabronione jest
kopiowanie rzeczywistych nazw systemow, zespolow, repository paths, ekranow,
rol, endpointow, danych klienta i payloadow.

Docelowa weryfikacja zmiany wspolnej backend-frontend, po wykonaniu wszystkich
zatwierdzonych krokow:

```text
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
mvn -q -Pbackend-dev clean package
```

W petli implementacyjnej uruchamiane sa najpierw testy celowane do zmienionych
warstw. `npm ci` tylko przy braku zaleznosci albo zmianie lockfile/package.

## Plan inkrementalny

Kazdy punkt konczy sie osobno weryfikowalnym stanem. Nastepny punkt nie jest
autoryzowany przez zatwierdzenie poprzedniego.

### 1. Breaking foundation w Operational Context

- [x] Dodac kanoniczne `systemSubtype` do loadera, DTO, editable schema,
  maintenance CRUD, walidacji, operator API/read models, operational context
  tools i edytora Angular.
- [x] Wymagac `systemSubtype` dla kazdego `systemType: internal-service` z
  vocabulary `frontend`, `backend`, `worker`, `mixed`, `unknown`; nie
  inferowac wartosci z nazwy ani zawartosci repozytorium.
- [x] Zdefiniowac eligibility frontendu: `internal-service/frontend`, jeden
  system-targeted code-search scope, jedno primary repository oraz jawny
  `searchMode`.
- [x] Zmigrowac atomowo wszystkie bundled systemy `internal-service` i
  wszystkich konsumentow wspolnego kontraktu; nie pozostawiac dual-read,
  legacy aliasu ani fallbacku dla brakujacego subtype.
- [x] Zaktualizowac `operational-context-maintenance/`: field guidance,
  prompts dla system/repository/code-search scope, fill order oraz
  checkpointy PowerShell, ktorych zachowanie lub raport zalezy od schema.
- [x] Dodac finding dla brakujacego/nieznanego subtype, brakujacego scope,
  braku primary i wielu primary; `unknown` jest poprawne katalogowo, ale nie
  kwalifikuje systemu do UI Explorer.
- [ ] Potwierdzic pelna rejestracje co najmniej jednego frontendu w effective
  katalogu srodowiska odbiorowego; rzeczywistych identyfikatorow nie utrwalac
  w testach ani przykladach dokumentacji.
- [x] Pokryc wszystkich konsumentow Operational Context, CRUD/API/tools/UI,
  bundled seed oraz maintenance dry-run celowanymi testami i wykonac pelna
  weryfikacje zmiany wspolnej.
- [x] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

Checkpoint 2026-08-15: kontrakt, konsumenci, maintenance, migracja effective
katalogu do jawnego `unknown` oraz pelna weryfikacja sa zakonczone. Krok 1
pozostaje otwarty wylacznie na potwierdzenie i wpisanie jednego rzeczywistego
frontendu z primary repository i system-targeted code-search scope; danych
produkcyjnych nie inferowano ani nie utrwalono w testach.

Local development checkpoint 2026-08-15: effective katalog zawiera tymczasowy,
silnie zanonimizowany mock CRM `crm-agent-portal` z `internal-service/frontend`,
jednym primary repository oraz system-targeted code-search scope. Mock odblokowuje
prace nad kontraktem i UI kolejnych krokow, ale jego fikcyjny GitLab project nie
zastepuje powyzszej bramki rejestracji rzeczywistego frontendu w srodowisku
odbiorowym.

### 2. Kontrakt i szkielet feature'a

- [x] Dodac lokalne `AGENTS.md` dla `features.uiexplorer` z ownership,
  dozwolonymi zaleznosciami, kontraktem sekcji i non-goals.
- [x] Dodac enumy sekcji i mode, request/result oraz publiczne DTO
  snapshotu bez zaleznosci od innych feature'ow.
- [x] Dodac thin input-options i job controllers ze szkieletem serwisu,
  walidacja oraz kontraktem `202`, bez udawanego wyniku AI.
- [x] Dodac result-to-report assembler contract i puste, jawne stany
  niedostepnosci.
- [x] Dodac package dependency/architecture tests oraz testy publicznej
  walidacji.
- [x] Zweryfikowac celowanymi testami backendu i `mvn -q test`.
- [x] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

Checkpoint 2026-08-15: feature-owned kontrakt i szkielet API sa gotowe bez
legacy aliasow i bez zaleznosci od sibling feature'ow. `GET
/api/ui-explorer/input-options` filtruje effective Operational Context do
kwalifikujacych sie frontendow, a `POST /api/ui-explorer/jobs` zwraca `202` z
jawnym terminalnym stanem `BLOCKED`, dopoki nie istnieja screen catalog, source
context i AI analysis; prompt, result, report i export nie sa pozorowane.
Celowane testy kontraktu, walidacji, assemblera i granic pakietow oraz pelne
`mvn -q test` przeszly. Runtime smoke test potwierdzil lokalny mock
`crm-agent-portal`, osiem sekcji i trzy tryby. Wszystkie nowe dane
testowe i przyklady domenowe tego kroku sa silnie zanonimizowanym CRM.

### 3. Neutralny katalog ekranow i source context

- [x] Dodac typowane, neutralne modele route/view catalog i screen source
  context w `integrations.gitlab`.
- [x] Zaimplementowac bounded discovery dla uzgodnionych wzorcow Angular/Nx z
  source revision, diagnostics i limitami.
- [x] Dodac screen context builder dla wybranego katalogowego `screenId`, bez
  AI i bez importu `features.uiexplorer`.
- [x] Pokryc fixtures dla standalone/module routes, lazy loading, guards,
  template variants, formularzy, NgRx, REST, WebSocket i sygnalow auth.
- [x] Udokumentowac jawnie nieobslugiwane wzorce jako diagnostics/limits.
- [x] Przeprowadzic audit istniejacych konsumentow GitLab i test regresji
  niezmienionych capability.
- [x] Zweryfikowac celowanymi testami integracji i `mvn -q test`.
- [x] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

Checkpoint 2026-08-15: `integrations.gitlab.frontend` dostarcza neutralne,
read-only modele i serwis dla workspace signals, route/view catalog oraz
wybranego screen source context. Capability reuse'uje `GitLabRepositoryPort`,
nie zmienia istniejacych operacji GitLaba i nie importuje UI Explorer ani
platformy AI. Obslugiwane fixtures CRM obejmuja standalone i module routes,
`loadComponent`, `loadChildren`, children, redirecty, guardy, route parameters,
template/style inline i external, Reactive Forms, custom/dynamic controls,
NgRx store/actions/selectors/effects/reducers, generated REST client,
`HttpClient`, WebSocket/RxJS oraz client-side role/permission checks.

Dynamiczne fabryki route, spread route definitions, runtime JSON, nieosiagalne
lazy targets, brak source revision oraz przekroczenia inventory/route/context,
depth, file i character limits sa zwracane jako typowane diagnostics i
`PARTIAL`/`UNSUPPORTED`; nie sa cicho uzupelniane. Celowane testy nowej
capability, `PackageDependencyGuardTest`, regresje
`GitLabRestRepositoryAdapterTest` i `GitLabSourceResolveServiceTest` oraz pelne
`mvn -q test` przeszly. Wszystkie nowe fixtures i przyklady domenowe sa silnie
zanonimizowanym CRM.

### 3a. Frontend Discovery w GitLab Tool Workbench

- [x] Wystawic neutralna capability przez shared/operator API jako dwie
  read-only operacje: katalog ekranow i source context wybranego ekranu.
- [x] Narzucic limity discovery po stronie backendu; publiczny request nie
  pozwala operatorowi rozszerzac bounded scan.
- [x] Dodac do istniejacego GitLab Tool Workbench grupe `Frontend Discovery`,
  reuse'ujaca wspolny scope repository/ref.
- [x] Pokazac katalog tras/widokow, source revision, diagnostics i limity oraz
  umozliwic przeniesienie `screenId` do operacji screen source context.
- [x] Pokazac manifest plikow, technical signals, coverage i surowy JSON bez
  dodawania tej capability do MCP, `agenttools` ani allowlist AI.
- [x] Pokryc API i UI testami kontraktu, walidacji, handoffu katalog -> context,
  loading/error oraz bounded request.
- [x] Zweryfikowac zmiane wspolna przez testy Angulara, build Angulara i
  `mvn -q -Pbackend-dev clean package`.
- [x] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

Krok 3a jest diagnostycznym preview reusable capability przed wlaczeniem jej
do deterministycznego pipeline'u UI Explorer. Nie uruchamia AI, nie zapisuje
historii i nie stanowi publicznego kontraktu joba UI Explorer.

Checkpoint 2026-08-15: GitLab Tool Workbench ma grupe `Frontend Discovery`
z operacjami katalogu i screen source context. Shared/operator API narzuca
`GitLabFrontendDiscoveryLimits.defaults()` i nie przyjmuje limitow od
operatora. UI pokazuje rewizje, katalog, diagnostics, manifest plikow,
technical signals, coverage i bounded/truncated state oraz przenosi `screenId`
z katalogu do contextu. Capability nie zostala dodana do MCP, `agenttools` ani
allowlist AI. Cztery testy MockMvc i dwa testy komponentu uzywaja wylacznie
silnie zanonimizowanego CRM. Przeszly 370 testow Angulara, produkcyjny build
Angulara i `mvn -q -Pbackend-dev clean package`.

### 4. Deterministyczny context pipeline i katalog API

Zakres 4A, zatwierdzony jako osobny inkrement, obejmuje feature-owned katalog
ekranow bez source contextu i AI:

- [x] Przyjmowac publicznie tylko `systemId` i `branch`, bez repository,
  GitLab group, project path, tokenu ani limitow skanu.
- [x] Rozwiazywac wewnetrzny GitLab scope z kwalifikujacej rejestracji
  `internal-service/frontend`, primary repository i systemowego code-search
  scope.
- [x] Reuse'owac `integrations.gitlab.frontend` z defaultowymi bounded limits
  i zachowaniem `path-prefixes` z Operational Context.
- [x] Wystawic `GET /api/ui-explorer/screens` z business-friendly katalogiem,
  source revision, statusem `READY/PARTIAL/BLOCKED`, diagnostics, limitations
  i applied boundary.
- [x] Mapowac brak kwalifikacji i nieistniejacy ref na feature-owned publiczne
  bledy bez ujawniania wewnetrznego repository scope.
- [x] Pokryc scope, ref, partial/truncated result, publiczne DTO i brak wycieku
  repository danych silnie zanonimizowanymi testami CRM.

- [x] Rozwiazywac system/repository scope z Operational Context oraz
  walidowac branch/ref i rewizje.
- [x] Wystawic katalog ekranow z business-friendly labelami, source revision
  i diagnostics.
- [x] Zbudowac deterministyczny pakiet researchu i coverage dla aktywnych
  sekcji.
- [x] Nie dodawac cache w pierwszym MVP; przyszly cache moze powstac tylko jako
  bounded po repository+revision z jawna invalidacja.
- [x] Dodac limity rozmiaru/glebokosci/liczby plikow oraz jawne partial/blocked
  rezultaty.
- [x] Pokryc brak repo scope, nieistniejacy branch, stale `screenId`,
  niejednoznaczny route i dynamiczne definicje runtime.
- [x] Zweryfikowac celowanymi testami backendu i `mvn -q test`.
- [x] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

Checkpoint 4A 2026-08-15: feature-owned screen catalog jest dostepny bez AI i
MCP. Publiczny request zawiera wylacznie `systemId` i `branch`; GitLab group,
project oraz path prefixes sa rozstrzygane z Operational Context i nie wracaja
w odpowiedzi. Katalog zwraca tylko wybieralne ekrany oraz source revision,
status, diagnostics, limitations i twarde limity. `input-options` nie raportuje
juz braku `SCREEN_CATALOG`; job pozostaje jawnie `BLOCKED` na
`SOURCE_CONTEXT` i `AI_ANALYSIS`. Source context, walidacja stale `screenId`
i podlaczenie snapshotu do joba pozostaja zakresem 4B. Celowane testy UI
Explorer, neutralnego GitLab frontend discovery, parsera routingu i granic
pakietow oraz pelne `mvn -q test` przeszly. Wszystkie nowe testy, fixtures i
przyklady tego inkrementu sa silnie zanonimizowane i dotycza wylacznie CRM.

Zakres 4B, zatwierdzony jako osobny inkrement, obejmuje deterministyczny source
context wybranego ekranu i podlaczenie go do joba bez AI:

- [x] Wymagac przy starcie joba `sourceRevision` zwroconej przez katalog i
  walidowac ja przed `screenId`.
- [x] Zwracac feature-owned konflikt wymagajacy odswiezenia katalogu, gdy ref
  wskazuje juz inna rewizje albo `screenId` jest nieaktualny.
- [x] Budowac bounded snapshot route/view, template/style, formularzy, NgRx,
  REST/WebSocket, RxJS i auth przez neutralne `integrations.gitlab.frontend`.
- [x] Przechowywac tresc source contextu tylko wewnetrznie w jobie, a publicznie
  wystawiac selected screen, evidence manifest, technical signals, coverage,
  diagnostics i applied boundary bez GitLab scope i surowej tresci kodu.
- [x] Wyliczac `READY/PARTIAL/BLOCKED` dla aktywnych sekcji i zachowywac jawne
  visibility limits dla heurystyk, brakujacych bibliotek i twardych limitow.
- [x] Pozostawic job terminalnie `BLOCKED` tylko na `AI_ANALYSIS`, jezeli source
  context zostal zbudowany; nie tworzyc promptu, wyniku ani raportu.
- [x] Nie dodawac cache, MCP, toola ani AI w tym inkremencie.
- [x] Zweryfikowac testami celowanymi, granicami pakietow i pelnym
  `mvn -q test`.
- [x] Potwierdzic, ze wszystkie nowe testy, fixtures, snapshoty i przyklady 4B
  sa silnie zanonimizowane i dotycza wylacznie CRM.

Checkpoint 4B 2026-08-15: job wymaga source revision z katalogu, waliduje ja
przed ekranem i zwraca `409` wymagajace odswiezenia wyboru przy zmianie rewizji
albo stale `screenId`. Bounded source context jest przechowywany wewnetrznie,
a publiczne `contextSections` zawieraja selected screen, manifest bez tresci
kodu, technical signals, coverage aktywnych sekcji, diagnostics i hard
boundary. Po poprawnym zbudowaniu kontekstu job ma zakonczone kroki
`SCREEN_DISCOVERY` i `SOURCE_CONTEXT`, a pozostaje jawnie `BLOCKED` tylko na
`AI_ANALYSIS`; prompt, result i report nie sa tworzone. Nie dodano cache, MCP
ani AI. Przeszly testy celowane UI Explorer, neutralnego frontend discovery,
parsera, publicznych bledow i granic pakietow oraz pelne `mvn -q test`.

### 5. Report-first workflow Copilota

- [x] 5A: Dodac polskie skille runtime zgodnie z rozdzialem workflow, bez
  duplikowania instrukcji.
- [x] 5A: Dodac logical artifacts, prompt builder, starter guidance i response
  schema dla jednego result contract.
- [x] 5A: Jawnie sklasyfikowac opis uzytkownika i source code jako untrusted
  evidence, zneutralizowac delimitery promptu oraz zakazac wykonywania
  instrukcji znalezionych w kodzie, komentarzach i konfiguracji runtime.
- [x] 5A: Dodac wewnetrzny krok `AI_PREPARATION` tworzacy szesc logical
  artifacts i prompt bez uruchamiania sesji Copilota; nie publikowac promptu
  ani surowej tresci source files w job API.
- [x] 5A: Pokryc prompt injection, niedostepna biblioteke organizacyjna i brak
  definicji formularza runtime silnie zanonimizowanymi fixtures CRM.
- [x] 5B: Dodac feature-owned available tools, hidden context, description
  customizations, tool budget i coverage/readiness gate.
- [x] 5B: Reuse'owac istniejace genericzne GitLab search/read tools tylko do
  uzupelnienia luk po deterministycznym snapshotcie.
- [x] 5B: Dodac parser z obsluga malformed/partial response oraz deterministyczny
  report assembler.
- [x] 5B: Pokryc prompt injection w kodzie/komentarzu, budget exhaustion,
  niedostepna biblioteke i brak definicji formularza runtime.
- [x] 5B: Zweryfikowac celowanymi testami AI boundary i `mvn -q test`.
- [x] 5B: Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

Checkpoint 5A 2026-08-15: `features.uiexplorer.ai.preparation` przygotowuje
deterministycznie szesc logical artifacts, feature-owned prompt oraz starter
guidance dla trzech polskich skilli runtime. `request.json` i source files sa
jawnie untrusted evidence, a ich tresc nie moze zmieniac instrukcji promptu.
Job zapisuje bundle tylko wewnetrznie i ma zakonczony krok `AI_PREPARATION`,
po czym nadal konczy sie `BLOCKED` na `AI_ANALYSIS`. Ten inkrement nie tworzy
sesji Copilota, tools/MCP, usage, historii, parsera ani wyniku AI. Pelna bramka
testowa kroku 5 pozostawala otwarta do runtime 5B; testy celowane 5A,
`mvn -q -DskipTests compile` oraz pelne `mvn -q test` przeszly.

Checkpoint 5B 2026-08-15: izolowany `UiExplorerCopilotAnalysisProvider`
sklada feature-owned `CopilotRunRequest`, ale nie jest jeszcze podlaczony do
publicznego joba. Readiness gate nie uruchamia AI dla zablokowanego contextu.
Dla partial contextu allowlista obejmuje built-in `skill` oraz wylacznie
`gitlab_search_repository_candidates`, `gitlab_read_repository_file` i
`gitlab_read_repository_file_chunk`; limit to jeden search, dwa read calls i
trzy wywolania GitLaba lacznie. Group, repository i revision sa hidden,
path-prefix scope jest egzekwowany przez policy, a kompletny plik osadzony w
snapshotcie nie moze zostac pobrany ponownie. Strict parser przyjmuje source
reference tylko z deterministycznego manifestu albo przechwyconego tool
evidence, uzupelnia brakujace aktywne sekcje jako `BLOCKED` i zwraca bezpieczny
fallback dla malformed JSON. Sekcja z deterministycznym `PARTIAL` nie moze
stac sie `READY` bez przechwyconego targeted fallback source evidence.
Publiczny job nadal pozostaje `BLOCKED` na
`AI_ANALYSIS`; jego transitiony, usage i zapis wyniku sa zakresem kroku 6.
Testy celowane calego AI boundary, runtime skills, publicznego joba i granic
pakietow oraz pelne `mvn -q test` przeszly. Audit fixtures potwierdzil wylacznie
silnie zanonimizowana domene CRM.

### 6. Asynchroniczny job, historia i portability

Zakres 6A, zatwierdzony jako osobny inkrement, obejmuje publiczny runtime joba
bez historii i portability:

- [x] 6A: Podlaczyc izolowany provider 5B do asynchronicznego joba; `POST`
  zwraca natychmiast `202`, a praca odbywa sie poza watkiem HTTP.
- [x] 6A: Zaimplementowac atomowe transitiony `RUNNING` do
  `COMPLETED/PARTIAL/FAILED/BLOCKED`, kroki `AI_PREPARATION` i `AI_ANALYSIS`,
  context sections, activity, tool evidence, usage, result i report.
- [x] 6A: Kontrolowanie mapowac blad source, blad Copilota i malformed response
  oraz zabezpieczyc job przed podwojnym wykonaniem.
- [x] 6A: Nie publikowac prepared promptu, poniewaz zawiera surowa tresc source
  evidence; publiczne pole pozostaje `null` do czasu osobnej sanitizacji.
- [x] 6A: Zweryfikowac mocked providerem bez live Copilota, testami celowanymi
  oraz `mvn -q test`.
- [x] 6A: Wszystkie testy, fixtures, snapshoty i przyklady tego inkrementu maja
  byc silnie zanonimizowane i dotyczyc wylacznie CRM.

Checkpoint 6A 2026-08-15: `POST /api/ui-explorer/jobs` zwraca `202` ze
snapshotem `QUEUED`, a `applicationTaskExecutor` wykonuje poza watkiem HTTP
source context, jednokrotne przygotowanie szesciu artifacts oraz izolowany
provider 5B. Synchronizowany feature-owned state publikuje atomowe kroki,
context/tool evidence, activity, usage, source revision, result i report oraz
terminalne `COMPLETED`, `PARTIAL`, `BLOCKED` albo `FAILED`. Readiness failure
nie publikuje fallback resultu, malformed response udostepnia bezpieczny
partial result, a nieoczekiwany blad providera nie ujawnia szczegolow w API.
Powtorne wykonanie tego samego runnable jest ignorowane. `preparedPrompt`
pozostaje zawsze `null` w publicznym snapshotcie; bundle z surowym source
evidence jest wewnetrzny. `input-options` raportuje AI analysis jako
`AVAILABLE`. Celowane 56 testow UI Explorer, `PackageDependencyGuardTest` oraz
pelne `mvn -q test` przeszly bez live Copilota. Wszystkie nowe dane testowe sa
silnie zanonimizowanym CRM.

Zakres 6B.1, zatwierdzony jako osobny inkrement lokalnej historii:

- [x] 6B.1: Zapisywac terminalne `COMPLETED`, `PARTIAL`, `BLOCKED` i `FAILED`
  w neutralnym `LocalAnalysisRunStore`, bez tworzenia feature-specific History
  API.
- [x] 6B.1: Utrzymac feature-owned, wersjonowana koperte lokalnego runu i
  odczyt list/detail po restarcie przez shared `/api/analysis/runs`.
- [x] 6B.1: Przed zapisem usuwac prepared prompt, surowa tresc source evidence,
  tool arguments, hidden GitLab scope, repository z references, tool feedback i
  szczegoly activity; report skladac ponownie z oczyszczonego resultu.
- [x] 6B.1: Zachowac source revision, visibility limits, wynik, report, usage,
  steps oraz bezpieczne evidence; nie wlaczac continuation ani resume joba.
- [x] 6B.1: Obsluzyc uszkodzony plik historii jako jawny blad odczytu, a awarie
  zapisu odizolowac od terminalnego wyniku joba.
- [x] 6B.1: Zweryfikowac celowanymi testami oraz `mvn -q test`.
- [x] 6B.1: Wszystkie testy, fixtures, snapshoty i przyklady tego inkrementu sa
  silnie zanonimizowane i dotycza wylacznie CRM.

Checkpoint 6B.1 2026-08-15: UI Explorer zapisuje terminalny snapshot jako
`tdw.ui-explorer-local-run` version `1` pod feature key `ui-explorer`.
Sanitizer buduje oddzielny model zapisu: publiczny request/result, source
revision, visibility limits, steps, usage i bounded evidence pozostaja
dostepne, ale prompt, source content, tool arguments, hidden repository scope
i activity details nie trafiaja do `run.json`. Report jest skladany ponownie z
oczyszczonych source references. Shared Analysis History odczytuje list/detail
po utworzeniu nowej instancji store, continuation jest wylaczone, a lokalny
zapis nie jest durable job queue i nie wznawia runu. Blad persistence jest
best-effort i nie nadpisuje terminalnego stanu analizy. Celowane testy UI
Explorer/history/granic pakietow oraz pelne `mvn -q test` przeszly na silnie
zanonimizowanych danych CRM.

Zakres 6B.2, zatwierdzony jako osobny inkrement portability:

- [x] 6B.2: Zdefiniowac stabilny, wersjonowany i sanitizowany kontrakt exportu
  niezalezny od wewnetrznej koperty lokalnej historii.
- [x] 6B.2: Dodac read-only import z jawnym odrzucaniem starszej, nowszej i
  uszkodzonej wersji; result i report sa czescia payloadu gotowego do
  pobrania/kopiowania przez workspace Angular.
- [x] 6B.2: Pokryc round-trip, source revision, visibility limits, redakcje i
  brak hidden context w portable payloadzie.
- [x] 6B.2: Zweryfikowac celowanymi testami oraz `mvn -q test`; wszystkie dane
  testowe i przyklady maja byc silnie zanonimizowanym CRM.

Checkpoint 6B.2 2026-08-15: `GET
/api/ui-explorer/jobs/{jobId}/export` zwraca osobny
`tdw.ui-explorer-export` version `1` tylko dla `COMPLETED/PARTIAL` z resultem i
reportem. Export dziala dla joba w pamieci oraz lokalnego runu po restarcie,
ale nie ujawnia wewnetrznego `tdw.ui-explorer-local-run`. `POST
/api/ui-explorer/imports` przyjmuje wylacznie dokladny aktualny schema,
version, payload type i result contract; brak kompatybilnosci wstecznej,
migracji oraz akceptacji nowszych wersji. Import jest niezaufanym inputem:
waliduje spojny system, screen i source revision, ponownie sanitizuje evidence,
usuwa prompt, hidden scope, repository, activity details i tool feedback,
sklada report od nowa z resultu, nadaje nowy identyfikator i zapisuje read-only
snapshot do Analysis History bez continuation. Portable round-trip zachowuje
source revision, visibility limits, usage, wynik i report, a re-export dziala
po restarcie. Przyciski copy/download i renderowanie importu sa zakresem kroku
7. Celowane testy portability/API/granic oraz pelne `mvn -q test` przeszly na
silnie zanonimizowanych danych CRM.

### 7. Workspace Angular dla UI Explorer

Zakres 7A, zatwierdzony jako osobny inkrement fundamentu i konfiguracji, bez
uruchamiania joba:

- [x] 7A: Dodac route, navigation i landing card UI Explorer.
- [x] 7A: Dodac feature-owned typowane modele HTTP, API service i facade/state
  dla prawdziwych `input-options`, katalogu ekranow i wspolnego katalogu AI.
- [x] 7A: Dodac osobne komponenty konfiguracji i business-friendly katalogu
  ekranow z loading/empty/error/retry oraz automatyczna `sourceRevision`.
- [x] 7A: Zaimplementowac wybor systemu, branch/ref, ekranu i trybow
  osmiu sekcji, opisu scenariusza, modelu i reasoning effort. Zmiana systemu
  albo refa usuwa zalezne, potencjalnie nieaktualne wybory.
- [x] 7A: Nie uruchamiac joba, nie mockowac rezultatu i nie dodawac lokalnego
  kontraktu zastepujacego backend; ekran konczy sie kompletna konfiguracja
  gotowa do podlaczenia w 7B.
- [x] 7A: Pokryc API, facade, komponenty, shell/navigation, responsywnosc i
  podstawowa dostepnosc klawiatura testami Angulara.
- [x] 7A: Zweryfikowac `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build` oraz celowany `FrontendPageTest`, poniewaz
  route zmienia granice statycznego SPA.
- [x] 7A: Wszystkie testy, fixtures, snapshoty i przyklady sa silnie
  zanonimizowane i dotycza wylacznie CRM.

Zakres 7B, zatwierdzony jako osobny inkrement lifecycle runu bez renderowania
merytorycznego raportu i portability:

- [x] 7B: Rozszerzyc feature-owned modele i API service o dokladny kontrakt
  `POST /api/ui-explorer/jobs` oraz bezpiecznie kodowany
  `GET /api/ui-explorer/jobs/{jobId}`.
- [x] 7B: Zbudowac start request z aktualnego katalogu i konfiguracji,
  wymagajac zgodnego `screenId` oraz `sourceRevision`; nie uruchamiac joba dla
  stale albo niekompletnego wyboru.
- [x] 7B: Dodac jeden dominujacy `Run UI Explorer`, natychmiast zapisac
  snapshot zwrotny z `202` i zablokowac duplikat startu podczas aktywnego runu.
- [x] 7B: Reuse'owac `AnalysisJobPollingService`, bez overlap requestow;
  terminalne statusy to `COMPLETED`, `PARTIAL`, `BLOCKED` i `FAILED`, a blad
  pollingu zachowuje ostatni snapshot i udostepnia jawny retry.
- [x] 7B: Pokazac shared progress, AI activity, deterministic/tool evidence,
  tool feedback i usage/cost przez istniejace neutralne modele oraz komponenty;
  wynik merytoryczny pozostaje placeholderem do 7C.
- [x] 7B: Pokryc start request, immediate snapshot, polling, retry, stale
  revision, statusy terminalne i destroy lifecycle silnie zanonimizowanymi
  testami CRM.
- [x] 7B: Zweryfikowac pelne testy i build Angulara, architecture/import diff
  oraz zaktualizowac plan i kanoniczny opis aktualnego runtime UI.

Checkpoint 7B 2026-08-15: compact composer ma jeden dominujacy
`Run UI Explorer`, ktory wysyla feature-owned request z katalogowym
`screenId` i `sourceRevision`, aktywnymi trybami sekcji oraz tylko niepustymi
preferencjami opcjonalnymi. Snapshot zwrotny z `202` jest widoczny natychmiast,
a dalszy przebieg korzysta z neutralnego `AnalysisJobPollingService` bez
nakladania requestow. Konfiguracja jest zablokowana podczas aktywnego runu;
`COMPLETED`, `PARTIAL`, `BLOCKED` i `FAILED` zatrzymuja polling. Blad pollingu
zachowuje ostatni snapshot i udostepnia retry, a blad autoryzacji moze pokazac
bezpieczny `authStartUrl`. Shared aside i `analysis-steps-panel` prezentuja
kroki, deterministic/tool evidence, activity, feedback i usage; feature nie
duplikuje ich renderowania. Merytoryczny result/report, historia i
import/export pozostaja zakresem 7C. Przeszlo 19 celowanych testow UI Explorer
oraz pelne 390 testow Angulara w 53 plikach; produkcyjny build Angulara rowniez
przeszedl. Wszystkie nowe testy i przyklady sa silnie zanonimizowanym CRM.

Zakres 7C.1, zatwierdzony jako osobny inkrement prezentacji merytorycznego
wyniku, bez historii i portable JSON:

- [x] 7C.1: Zastapic terminalny placeholder raportem, dla ktorego
  `snapshot.report` jest glownym zrodlem naglowka, podsumowania, aktywnych
  sekcji, confidence, references, visibility limits i open questions.
- [x] 7C.1: Typowac feature-owned `snapshot.result` i pokazac z niego tylko
  biznesowo czytelne zaleznosci przekrojowe;
  nie renderowac raw JSON, prepared promptu ani surowej tresci source.
- [x] 7C.1: Reuse'owac shared result header, report section content, Markdown
  i report meta; zachowac shared aside dla przebiegu, AI i evidence.
- [x] 7C.1: Obsluzyc `COMPLETED`, `PARTIAL`, `BLOCKED` i `FAILED`, w tym jawny
  stan terminalny bez raportu oraz zachowanie poprawnych sekcji wyniku
  czastkowego.
- [x] 7C.1: Dodac copy calego raportu oraz download Markdown z nazwa pliku
  oparta o zanonimizowany identyfikator widoku i source revision.
- [x] 7C.1: Pokryc renderowanie raportu, osiem sekcji, wynik czastkowy,
  blocked/failed, copy i download silnie zanonimizowanymi testami CRM.
- [x] 7C.1: Zweryfikowac pelne testy i build Angulara, architecture/import diff
  oraz zaktualizowac plan i kanoniczny opis runtime UI.

Checkpoint 7C.1 2026-08-15: terminalny placeholder zostal zastapiony
feature-owned prezentacja, dla ktorej `snapshot.report` jest glownym zrodlem
naglowka, podsumowania, osmiu uporzadkowanych sekcji i metadanych
wiarygodnosci. `snapshot.result` ma dokladny kontrakt TypeScript i zasila tylko
zaleznosci przekrojowe. Shared result header
udostepnia teraz opcjonalna akcje download, a UI Explorer reuse'uje rowniez
shared Markdown, report section content, report meta oraz analysis aside.
Wynik `PARTIAL` zachowuje dostepne sekcje i jawnie pokazuje braki;
`BLOCKED`, `FAILED` i terminalny snapshot bez reportu nie tworza wyniku
zastepczego. Prepared prompt, raw JSON i surowa tresc source nie sa renderowane.
Copy oraz download tworza ten sam biznesowo czytelny Markdown, z nazwa pliku
oparta o screen id i source revision. Przeszlo 10 celowanych testow oraz pelne
395 testow Angulara w 54 plikach; produkcyjny build Angulara rowniez przeszedl.
Wszystkie nowe fixtures i przyklady sa silnie zanonimizowanym CRM. Route i
granica statycznego SPA nie zmienily sie, dlatego `FrontendPageTest` nie byl
ponawiany.

Zakres 7C.2, zatwierdzony jako osobny frontendowy inkrement historii i
portability, bez continuation i bez kompatybilnosci wstecznej:

- [x] 7C.2: Dodac mapowanie `ui-explorer` w Analysis History i otwierac run
  przez `/ui-explorer?localRunId=...`, bez pobierania pelnego JSON-a na ekranie
  listy.
- [x] 7C.2: Odtwarzac konfiguracje i raport z dokladnie wersjonowanej lokalnej
  koperty `tdw.ui-explorer-local-run` v1; odrzucac obcy feature, schema,
  version, result contract albo uszkodzony snapshot bez fallbacku.
- [x] 7C.2: Rozroznic pochodzenie `live`, `history` i `imported`; historia oraz
  import sa prezentowane tylko do odczytu i nie uruchamiaja pollingu,
  continuation, follow-up chatu ani wznowienia sesji.
- [x] 7C.2: Dodac eksport portable JSON przez kanoniczny backendowy
  `GET /api/ui-explorer/jobs/{jobId}/export` dla wyniku live, history i import.
- [x] 7C.2: Dodac import JSON przez `POST /api/ui-explorer/imports`, pozostawic
  serwer jako zrodlo walidacji niezaufanej koperty i pokazac zwrocony,
  sanitizowany snapshot tylko do odczytu.
- [x] 7C.2: Zachowac copy oraz download Markdown z 7C.1, a obok portability
  pokazac jawne komunikaty bledu i akcje rozpoczecia nowego runu.
- [x] 7C.2: Pokryc live export, odtworzenie po restarcie, read-only import,
  starsza/nowsza/obca/uszkodzona wersje oraz mapowanie Analysis History
  silnie zanonimizowanymi testami CRM.
- [x] 7C.2: Zweryfikowac pelne testy i build Angulara, architecture/import diff
  oraz zaktualizowac plan i kanoniczny opis runtime UI.

Checkpoint 7C.2 2026-08-15: Analysis History rozpoznaje `ui-explorer` i
przekazuje tylko `localRunId` do feature workspace. UI Explorer pobiera detail,
sprawdza feature, brak continuation oraz dokladna lokalna koperta
`tdw.ui-explorer-local-run` version `1`; nie ma fallbacku dla starszego,
nowszego, obcego ani uszkodzonego formatu. Odtworzony run pokazuje konfiguracje,
raport i shared evidence jako read-only bez pollingu, follow-up chatu, resume
oraz sesji AI. Importowany JSON jest niezaufanym dokumentem walidowanym przez
`POST /api/ui-explorer/imports`; frontend pokazuje dopiero zwrocony,
sanitizowany snapshot. Wynik live, history i imported korzysta z jednego
`GET /api/ui-explorer/jobs/{jobId}/export`, a copy i download Markdown pozostaja
dostepne obok portable JSON. UI pokazuje pochodzenie wyniku oraz akcje importu
kolejnego pliku i rozpoczecia nowego runu. Przeszlo 35 celowanych testow UI
Explorer, 16 testow Analysis History oraz pelne 409 testow Angulara w 55
plikach; produkcyjny build Angulara rowniez przeszedl. Architecture/import diff
nie dodal zaleznosci od sibling feature'ow, route SPA sie nie zmienil, wiec
`FrontendPageTest` nie byl ponawiany. Wszystkie nowe testy, fixtures i przyklady
sa silnie zanonimizowanym CRM.

Checkpoint 7A 2026-08-15: `/ui-explorer` jest lazy-loaded ekranem w sekcji
Analysis Features, dostepnym z sidebaru i landing page. Feature-owned modele,
API service i facade pobieraja prawdziwe `input-options`, katalog ekranow oraz
wspolny katalog modeli AI. Operator wybiera frontend, branch/ref, ekran,
tryby osmiu sekcji, opcjonalny scenariusz, model i reasoning effort;
source revision pochodzi wylacznie z katalogu. Zmiana systemu albo refa usuwa
screen i revision, a katalog ma osobne loading/empty/error/retry oraz jawne
ograniczenia. 7A nie wywoluje `POST /jobs`, nie renderuje mockowanego rezultatu
i nie dodaje kompatybilnosci wstecznej. Przeszlo 378 testow Angulara w 53
plikach, produkcyjny build Angulara oraz 12 testow `FrontendPageTest`. Wszystkie
nowe fixtures i przyklady sa silnie zanonimizowanym CRM.

Zakres 7A.1, zatwierdzony jako korekta spojnosci UX przed 7B:

- [x] 7A.1: Przebudowac konfigurator do tego samego wzorca compact analysis
  composer co Flow Explorer: zwijany naglowek, target row, scope row, opis i
  stopka stanu.
- [x] 7A.1: Przeniesc business-friendly katalog widokow z osobnej prawej
  kolumny do kontrolki targetu z dropdownem, wyszukiwaniem oraz
  loading/empty/error/retry.
- [x] 7A.1: Pokazywac section modes, model i reasoning jako zwarte
  kontrolki. Osiem sekcji pozostaje edytowalne w rozwijanym panelu bez stalego
  zajmowania calej wysokosci strony.
- [x] 7A.1: Dodac pusty obszar przyszlego wyniku zgodny z Flow Explorerem, ale
  nie dodawac niezaimplementowanych akcji start/import ani mockowanego wyniku.
- [x] 7A.1: Zachowac feature-owned facade, prawdziwe API, source revision,
  reset stale wyborow, responsywnosc i obsluge klawiatury.
- [x] 7A.1: Zaktualizowac testy DOM/interakcji na silnie zanonimizowanym CRM,
  uruchomic pelne testy i build Angulara oraz wykonac wizualna weryfikacje
  lokalnego widoku desktop/mobile.

Checkpoint 7A.1 2026-08-15: UI Explorer korzysta ze zwartego wzorca analysis
composer znanego z Flow Explorera: target row zawiera frontend, branch/ref,
widok i odswiezenie katalogu, a scope row tryby sekcji, model i
reasoning. Katalog widokow oraz osiem trybow sekcji sa rozwijanymi kontrolkami,
wiec konfiguracja nie tworzy osobnej kolumny ani stalego wielosekcyjnego
wizarda. Pod composerem znajduje sie pusty obszar przyszlego wyniku. Nie dodano
pozornych akcji start/import ani mockowanego raportu przed krokami 7B/7C.
Przeszlo 378 testow Angulara w 53 plikach i produkcyjny build Angulara, a
lokalny widok zostal sprawdzony wizualnie na desktopie oraz przy viewport
390x844. Testy i przyklady pozostaja silnie zanonimizowanym CRM.

### 8. Pilot i hardening MVP

Zakres 8A, zatwierdzony jako przekrojowy inkrement L2 poprzedzajacy pilot,
usuwa wykryta na testowym frontendzie luke statycznego route discovery bez
kopiowania jego nazw, sciezek ani danych do repozytorium:

- [x] 8A: Zachowac baseline literalnych standalone/module routes, children,
  redirectow, guardow, relative lazy imports, bounded diagnostics oraz
  publicznych kontraktow UI Explorer i GitLab Frontend Discovery API.
- [x] 8A: Rozszerzyc neutralny parser o bounded, statyczne rozwiazywanie
  importowanych `const` object literals i property chains uzywanych jako
  `path` albo `redirectTo`, bez wykonywania ani kompilowania TypeScriptu.
- [x] 8A: Rozwiazywac `baseUrl` i wildcard `compilerOptions.paths` z
  repozytoryjnych `tsconfig*.json` dla route model imports oraz
  `loadChildren`/`loadComponent`, z wykrywaniem cykli, niejednoznacznosci i
  przekroczen limitow.
- [x] 8A: Zachowac jawne partial/unsupported diagnostics dla wyrazen,
  aliasow i lazy targets, ktorych nie da sie bezpiecznie rozstrzygnac
  statycznie; nie dodawac fallbacku wykonujacego kod ani zaleznosci od jednego
  badanego frontendu.
- [x] 8A: Pokryc analogiczny wzorzec wylacznie silnie zanonimizowanymi
  fixtures CRM: cross-file route model, empty root path, nested property
  chains, Nx/TypeScript path alias, lazy child routes, guards i redirect.
- [x] 8A: Zweryfikowac wszystkich konsumentow zmienionej integracji:
  `GitLabFrontendSourceDiscoveryService`, shared GitLab Frontend Discovery API,
  UI Explorer screen catalog/source context oraz package dependency guard.
- [x] 8A: Uruchomic adekwatne testy celowane i pelne `mvn -q test`, wykonac
  architecture diff oraz zaktualizowac kanoniczny opis obslugiwanych i
  nieobslugiwanych wzorcow.

Baseline 8A: neutralny parser rozpoznaje literalne tablice `Routes`, children,
redirecty, guardy, component/loadComponent/loadChildren i relative imports.
Property chain w rodzaju `CRM_ROUTES.contacts.details.path` jest raportowany
jako dynamiczny i mapowany bez wartosci path, a importy zaczynajace sie od
aliasu TypeScript/Nx nie sa rozwiazywane do inventory. Powoduje to utrate
route pattern i brak traversal do aliasowego lazy route source. Publiczne DTO,
limity, statusy i kontrakty konsumentow pozostaja bez zmian. Wlascicielem delty
jest `integrations.gitlab.frontend`; konsumenci to shared GitLab Frontend
Discovery API/Tool Workbench oraz UI Explorer catalog i source context.

Checkpoint 8A 2026-08-16: neutralna integracja rozwiazuje teraz importowane
statyczne `const` object literals i zagniezdzone property chains dla `path` i
`redirectTo`, empty root routes, proste laczenie stringow oraz template
interpolation oparte o statyczne wartosci i string enums. Repozytoryjne
`tsconfig*.json` dostarczaja bounded `baseUrl` i exact/wildcard `paths` dla
route models, lazy targets, view roots i related source traversal. Resolver
nie wykonuje ani nie kompiluje TypeScriptu, ma ograniczona liczbe konfiguracji
i glebokosc, wykrywa cykle oraz odrzuca niejednoznaczne aliasy bez arbitralnego
wyboru. Runtime factories, spreads, dynamiczne wyrazenia oraz pliki poza
inventory/code-search scope pozostaja jawnymi diagnostics/visibility limits.
Publiczne DTO, endpointy, source revision, statusy i limity UI Explorer oraz
GitLab Frontend Discovery nie zostaly zmienione. Przeszly testy parsera,
resolvera, integracji, shared API, katalogu i source context UI Explorer,
package dependency guard oraz pelne `mvn -q test`. Architecture diff nie dodal
importow z integracji do `features`, `api`, `agenttools` ani `aiplatform`.
Wszystkie nowe testy i przyklady sa silnie zanonimizowanym CRM; diagnostyczne
pliki badanego frontendu nie zostaly utrwalone w repozytorium.

#### 8B. Route-root graph discovery bez repository inventory

Zakres 8B zastepuje wynikowe podejscie runtime z 8A. Implementacja 8A jest
baseline'em migracji i nie pozostaje fallbackiem. Nowy kontrakt zaklada nowszy
standalone Angular z dokladnie jednym produkcyjnym `provideRouter(...)`
osiagalnym z glownego `bootstrapApplication(...)`. `RouterModule.forRoot`,
wybor pierwszego z wielu rootow oraz globalne przegladanie wszystkich plikow
`.ts` nie sa obslugiwane. Brak albo niejednoznacznosc glownego root routera
konczy discovery jawnym `BLOCKED`, a nie przejsciem do poprzedniego algorytmu.

Potrzeba techniczna: obecny serwis najpierw sortuje cale repository inventory
i obcina je do `maxInventoryFiles`, a dopiero pozniej szuka konfiguracji,
aliasow oraz route sources. W duzym monorepo powoduje to zaleznosc wyniku od
alfabetycznej pozycji pliku: root `app.routes.ts` moze byc widoczny, podczas
gdy root `tsconfig.base.json`, importowany route model i lazy route sources sa
poza bounded working set. Zwiekszenie limitu tylko przesuwa ten problem i nie
jest rozwiazaniem uniwersalnym.

Docelowy deterministyczny flow:

1. Istniejacy GitLab content search znajduje bounded kandydatow
   `bootstrapApplication` i `provideRouter`; kandydat jest akceptowany tylko,
   gdy parser potwierdzi produkcyjny bootstrap chain, import z Angular Router
   i dokladnie jeden osiagalny root router.
2. Resolver odczytuje wymagane root/project `tsconfig*.json` oraz rozwiazuje
   argument `provideRouter`, importy, re-exporty, statyczne route arrays,
   property chains i aliasy TypeScript/Nx bez repository inventory.
3. Import target jest wyliczany do bounded zestawu konkretnych sciezek i
   odczytywany bezposrednio przez `GitLabRepositoryPort`; traversal zachowuje
   code-search scope, wykrywanie cykli, niejednoznacznosc i limity grafu.
4. Route graph przechodzi rekurencyjnie przez `children` i `loadChildren` oraz
   zapisuje source ownership dla `path`, `redirectTo`, `component`,
   `loadComponent`, `canActivate`, `canActivateChild`, `canDeactivate`,
   `canMatch`, `resolve`, `data`, `title`, `providers`, `pathMatch`, `outlet`
   i `runGuardsAndResolvers`. Konfiguracja rodzicow pozostaje osobnymi wezlami
   i jest wyprowadzana jako effective route chain dla potomka.
5. Katalog ekranow pozostaje lekki: zawiera route/view identity, hierarchy,
   wejscia, source references, coverage i diagnostics. Nie rozwija formularzy,
   NgRx, REST, WebSocket ani calego component graph dla kazdego ekranu.
6. Dopiero source context wybranego `screenId` rozwija route chain root ->
   screen, komponent wejscia i bounded powiazania istotne dla struktury,
   akcji, formularzy, stanu, danych, uslug i uprawnien.
7. Source revision jest rozstrzygana dla refa niezaleznie od metadanych
   przypadkowego pliku i pozostaje wspolna dla katalogu oraz source contextu.

Nowe limity sa semantyczne: liczba root candidates, route nodes, route files,
bezposrednich reads, alias resolutions, import/component depth oraz file/total
characters. Usuniete zostaja `maxInventoryFiles`, `repositoryFileCount`,
`inventoryTruncated` i diagnostyki sugerujace globalne inventory. Publiczny
operator nadal nie rozszerza limitow. Wynik pokazuje liczbe odwiedzonych
wezlow i plikow oraz konkretna nieodwiedzona krawedz po przekroczeniu budzetu.

Brak kompatybilnosci wstecznej oznacza jedna atomowa migracje neutralnych
modeli, shared/operator API, GitLab Tool Workbench, UI Explorer catalog/source
context i ich testow. Nie zostaja legacy DTO, dual-read, stary algorytm,
legacy diagnostics ani tlumaczenie starego `screenId`. Jezeli delta zmieni
serializowany kontrakt exportu UI Explorer, schema/version zostaje podniesiona,
a starsze eksporty sa jawnie odrzucane zamiast migrowane.

Conformance delta i konsumenci 8B:

| Obszar | Baseline 8A | Target 8B |
| --- | --- | --- |
| Root discovery | route candidates z obcietego inventory | jeden zweryfikowany `bootstrapApplication` -> `provideRouter` chain |
| Source traversal | membership w pierwszych `maxInventoryFiles` | targeted read grafu importow w hidden code-search scope |
| Route model | plaska lista odkrytych wpisow | hierarchiczny route graph i effective route chain |
| Limity | inventory/route/context | root/graph/read/depth/character budgets |
| Failure semantics | partial po przypadkowym obcieciu | jawne blocked/partial na konkretnej krawedzi grafu |
| Konsumenci | integration, shared API, Workbench, UI Explorer | atomowo zmienione bez legacy adaptera |

Odrzucone alternatywy: podniesienie `maxInventoryFiles` do rozmiaru badanego
repozytorium, utrzymanie inventory jako fallbacku, wymaganie od analityka
podania wszystkich lazy-library path prefixes oraz budowa pelnego component
graph dla wszystkich ekranow podczas ladowania katalogu.

- [x] 8B.1: Zdefiniowac breaking modele bootstrap root, route graph, effective
  route chain, graph coverage/diagnostics i semantyczne limity; przygotowac
  audit publicznych DTO, `screenId`, exportu oraz wszystkich konsumentow i
  potwierdzic compile-time dependency graph. Dowodem sa testy kontraktow oraz
  fixtures wylacznie silnie zanonimizowanego CRM; nie wolno utrwalac nazw,
  sciezek ani fragmentow badanego frontendu.
- [x] 8B.2: Zaimplementowac bounded root discovery przez GitLab content search
  oraz walidacje `bootstrapApplication` -> application config -> jednego
  `provideRouter`, z `BLOCKED` dla zera/wielu rootow i bez legacy fallbacku.
  Pokryc aliasy importu, komentarze, pliki testowe i niejednoznacznosc
  silnie zanonimizowanymi fixtures CRM oraz uruchomic celowane testy integracji.
- [x] 8B.3: Zastapic inventory resolverem targeted reads dla `tsconfig`,
  importow, re-exportow, route const/property chains, `children`,
  `loadChildren`, `component` i `loadComponent`; dodac cykle, depth/read/size
  budgets i destrukturyzowane `.then(({ ROUTES }) => ROUTES)`. Wszystkie testy
  oraz przyklady maja byc silnie zanonimizowanym CRM; rzeczywisty kod badanego
  frontendu pozostaje wylacznie materialem diagnostycznym poza repozytorium.
- [x] 8B.4: Zbudowac route graph z route-owned guards, resolvers, data,
  providers i pozostala konfiguracja oraz effective route chain dla potomkow;
  katalog ma zatrzymywac sie przed pelnym component-context traversal.
  Zweryfikowac empty paths, nested children, lazy boundaries, redirecty,
  parametry, auxiliary outlets i duplikaty route pattern wylacznie na silnie
  zanonimizowanych fixtures CRM.
- [x] 8B.5: Przebudowac source context wybranego ekranu tak, aby rozwijal tylko
  jego route chain i bounded component/behavior dependencies, a nastepnie
  atomowo zmigrowac shared API, Tool Workbench i UI Explorer bez legacy DTO,
  diagnostyk i `screenId`. Testy backendu i Angulara, snapshoty oraz przyklady
  maja pozostac silnie zanonimizowanym CRM.
- [x] 8B.6: Rozwiazac source revision bez anchor file metadata, usunac stary
  inventory runtime i dead code, zaktualizowac dokumentacje kanoniczna oraz
  wykonac macierz zmiany wspolnej: celowane testy integracji/API/feature,
  `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build` i `mvn -q -Pbackend-dev clean package`.
  Wszystkie nowe lub zmienione fixtures, snapshoty i przyklady musza byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

Checkpoint 8B.1 2026-08-16: w `integrations.gitlab.frontend` istnieje docelowy,
jeszcze niepodlaczony do publicznego runtime kontrakt `GitLabFrontendRouteGraph`.
Rozdziela zweryfikowany bootstrap root, route nodes, typed graph edges,
route-owned configuration, effective route chains, screen identity, typed
diagnostics, coverage oraz `GitLabFrontendGraphLimits` bez
`maxInventoryFiles`. Model dopuszcza brak root wyłącznie dla jawnego stanu
`BLOCKED`; `READY` wymaga root, screen node wymaga zgodnego `routeNodeId`, a
effective chain musi konczyc sie wezlem wybranego ekranu. Nie wlaczono nowego
traversal ani dualnego publicznego API przed 8B.2-8B.5.

Audit konsumentow i decyzje migracyjne 8B.1:

| Konsument | Obecna zaleznosc | Decyzja breaking migration |
| --- | --- | --- |
| `GitLabFrontendSourceDiscoveryService` i integration request/result | inventory limits, plaskie entries, hash `kind + route + viewPath` | 8B.2-8B.4 buduja graph; 8B.5 usuwa stare modele i generator bez fallbacku |
| shared GitLab Frontend Discovery API | zwraca integration DTO bez osobnej projekcji | 8B.5 atomowo wystawia graph catalog/context i usuwa inventory fields/diagnostics |
| GitLab Tool Workbench | TypeScript DTO oraz chipy repository/route inventory | 8B.5 pokazuje root/graph/read coverage i konkretne unresolved edges |
| UI Explorer screen catalog | `UiExplorerScreenCatalogBoundary` i partial status z inventory | 8B.5 mapuje screen nodes/effective chains oraz semantyczne graph coverage |
| UI Explorer source context i AI readiness | context boundary, evidence mapper i readiness gate czytaja inventory truncation | 8B.5 przechodzi na selected route chain, graph/context budgets i edge diagnostics |
| `screenId`, job, report i historia | request/result/import consistency oraz nazwy eksportu opieraja sie na starym ID | nowe ID wynika z route-node identity, outlet i view target; brak translacji starych ID |
| UI Explorer export/import i sanitizer | schema/result V1 oraz allowlista inventory fields | 8B.5 podnosi wersje po zmianie semantyki screen identity/boundary i jawnie odrzuca V1 |
| source revision | metadata anchor wybrany z inventory | 8B.6 rozstrzyga commit refa niezaleznie od pliku |

Kontrakt zostal pokryty czterema silnie zanonimizowanymi testami CRM:
poprawny inherited route chain, jawnie zablokowany brak root, odrzucenie
niespojnego screen/node/chain oraz brak inventory limits. Przeszly
`GitLabFrontendRouteGraphContractTest`, `PackageDependencyGuardTest` i pelne
`mvn -q test`.

Checkpoint 8B.2 2026-08-16: `GitLabFrontendBootstrapDiscoveryService` uzywa
istniejacego `GitLabRepositoryPort.searchRepositoryFilesByContent` tylko dla
bounded kandydatow `bootstrapApplication` i `provideRouter`, filtruje `.ts`
przez hidden code-search `pathPrefixes` oraz odrzuca pliki spec/test/story,
Storybook, fixtures i testing. Kandydaci sa czytani bez globalnego tree
inventory; brak odczytu, truncation, blad search oraz przekroczenie
`maxRootCandidates` blokuja potwierdzenie zamiast zmniejszac coverage po
cichu.

Nowy `AngularBootstrapSourceParser` nie wykonuje TypeScriptu. Maskuje komentarze
i stringi, potwierdza named imports z `@angular/platform-browser` oraz
`@angular/router`, obsluguje aliasy nazw importowanych, konfiguracje inline,
lokalny `const` i pojedynczy importowany exported config. Discovery akceptuje
tylko jeden lancuch bootstrap config -> jeden `provideRouter`; zero, wiele
rootow albo wiele providerow zwraca typed `BLOCKED` diagnostics. Wynik jest
niepublicznym `GitLabFrontendBootstrapDiscoveryResult` i nie przelacza jeszcze
starego catalog runtime ani API; nie istnieje fallback z nowego resolvera do
inventory.

Szesc nowych testow, wylacznie na silnie zanonimizowanym CRM, potwierdza
importowany config i aliasy Angulara, inline config, komentarze/string false
positives, ignorowanie `.spec.ts`, zero/wiele rootow, candidate limit oraz
code-search prefixes. Przeszly testy celowane
`GitLabFrontendBootstrapDiscoveryServiceTest`,
`GitLabFrontendRouteGraphContractTest`, `PackageDependencyGuardTest` oraz
pelne `mvn -q test`.

Checkpoint 8B.3 2026-08-16: nowa, nadal izolowana od publicznego runtime
sciezka uzywa `GitLabFrontendTargetedSourceSession` jako jedynej bramy odczytu
po root discovery. Sesja normalizuje i weryfikuje kazdy path wzgledem hidden
`pathPrefixes`, cache'uje odczyty i brakujace pliki oraz egzekwuje osobne
budzety source reads, route files, alias resolutions, import depth, file size,
total size i route nodes. Nie wywoluje `listRepositoryFiles` i nie ma fallbacku
do inventory.

`GitLabFrontendTargetedImportResolver` pobiera tylko przewidywalne kandydaty
`tsconfig.base.json`, `tsconfig.json`, konfiguracje przy bootstrap source oraz
ich statyczne `extends`. Rozwiazuje relative imports, `baseUrl`, exact/wildcard
`paths` i ograniczony zestaw kandydatow `.ts`; zero/wiele dopasowan pozostaje
jawna krawedzia nierozwiazana zamiast arbitralnego wyboru. Module traversal
obsluguje named imports, named/star re-exports, wykrywa cykle i respektuje
depth/alias budgets.

`GitLabFrontendRouteSourceTraversalService` zaczyna od symbolu przekazanego do
zweryfikowanego `provideRouter`, rozwija literalne route const arrays,
property-chain paths, zewnetrzne `children`, `loadChildren`, `component` i
`loadComponent`. Lazy targets wspieraja `.then(module => module.ROUTES)` oraz
`.then(({ ROUTES }) => ROUTES)`. Na tym etapie komponent jest tylko
rozstrzygnietym targetem; jego zaleznosci pozostaja poza katalogiem tras do
kroku 8B.5.

Osiem nowych testow, wylacznie na silnie zanonimizowanym syntetycznym CRM,
potwierdza aliasy i barrel re-exports, static property chains, children, oba
warianty lazy `.then`, komponenty, hard scope boundary, cykl, niejednoznaczny
modul oraz budzety read/depth/file-size/route-files. Kazdy scenariusz
potwierdza brak `listRepositoryFiles`. Przeszly testy calego pakietu
`integrations.gitlab.frontend`, `PackageDependencyGuardTest` oraz pelny
`mvn -q test`.

Checkpoint 8B.4 2026-08-16: `GitLabFrontendRouteGraphDiscoveryService` sklada
zweryfikowany bootstrap root i targeted traversal w docelowy
`GitLabFrontendRouteGraph`. Kazda deklaracja route zachowuje wewnetrzna
tozsamosc collection + source offset tylko do bezblednego laczenia rodzicow;
publiczne `routeNodeId` i `screenId` wynikaja z semantycznego lancucha rodzica,
path segmentu, outletu, view/lazy targetu, redirectu i sibling occurrence.
Dodanie komentarza lub zmiana formatowania przed deklaracja nie zmienia ID.

Parser zachowuje route-owned `canActivate`, `canActivateChild`, `canDeactivate`,
`canMatch`, `canLoad`, `resolve`, `data`, `title`, `providers`, `pathMatch`,
`outlet` i `runGuardsAndResolvers` jako typowane
`GitLabFrontendRouteConfiguration`. Builder nie kopiuje ich na potomkow;
effective route chain sklada konfiguracje przez jawne segmenty od root do
ekranu, lacznie z empty-path i lazy boundaries, oraz agreguje parametry route.

Graf rozroznia route, screen, redirect i unresolved node, zachowuje auxiliary
outlet, nie deduplikuje ekranow po samym route pattern i wystawia typed edges
dla root routes, children, loadChildren, component oraz loadComponent. Brak
view/lazy targetu pozostaje krawedzia `NOT_FOUND` i typed diagnostic zamiast
znikac z katalogu. Catalog zatrzymuje sie na rozstrzygnietym component target;
nie przeszukuje jego importow ani behavior dependencies przed 8B.5.

Nowe testy, wylacznie na silnie zanonimizowanym syntetycznym CRM, pokrywaja
route-owned guards/resolvers/data/providers, nested i empty paths, lazy
boundary, redirect, parametry, auxiliary outlet, duplikaty route pattern,
stabilnosc ID po formatowaniu, brak component-context traversal, blocked root
oraz unresolved typed edge. Przeszly testy calego pakietu
`integrations.gitlab.frontend`, `PackageDependencyGuardTest` oraz pelny
`mvn -q test`.

Checkpoint 8B.5 2026-08-16: graph-first discovery zaczelo przyjmowac wylacznie
`screenId` z aktualnego route graph oraz opcjonalna oczekiwana rewizje. Ten
checkpoint byl etapem posrednim i zostal w calosci zastapiony w 8L.6b:
aktualny runtime zachowuje tylko minimalny selected-screen seed, a route chain,
komponenty i behavior dependencies rozwija przez iteracyjny screen
reachability oraz symbol slices. Nadal nie uzywa `listRepositoryFiles` ani
fallbacku do inventory. Publiczny input UI Explorer nie ujawnia group, project
ani path prefixes.

Import/export oraz lokalny run UI Explorer maja wersje `2` i kontrakt
`ui-explorer-result-v2`; wersja `1` jest jawnie odrzucana bez translacji
starych screen ID i inventory boundary. Sanitizer dopuszcza tylko nowe liczniki
graph/context. Testy API, feature, portability i Angulara zostaly przepisane
na silnie zanonimizowany syntetyczny CRM. Przeszly celowane testy backendu,
test wykluczajacy repository inventory dla selected screen, pelne testy
Angulara (409/409), produkcyjny build UI oraz
`mvn -q -Pbackend-dev clean package`. Macierz zostanie powtorzona w 8B.6 po
usunieciu starego runtime i docelowym rozwiazaniu source revision.

Checkpoint 8B.6 2026-08-16: neutralny `GitLabRepositoryPort` udostepnia teraz
`resolveRevision(group, projectName, ref)`, a produkcyjny adapter rozstrzyga
branch, tag albo SHA przez GitLab `repository/commits/{ref}`. Route graph uzywa
wylacznie tej capability i zwraca typed `SOURCE_REVISION_UNRESOLVED`, gdy ref
nie moze zostac przypisany do immutable commit id. Ani poprawny, ani
zablokowany katalog nie odczytuje juz metadanych pliku bootstrap w celu
wyznaczenia rewizji.

Usunieto `GitLabFrontendSourceDiscoveryService`, stary inventory test adapter,
osiem legacy DTO/enumow katalogu i contextu oraz inventory-only konstruktor i
alias loader z `TypeScriptStaticRouteResolver`. Produkcyjny pakiet
`integrations.gitlab.frontend` nie ma wywolania `listRepositoryFiles`,
`readFileMetadata`, `maxInventoryFiles`, `repositoryFileCount` ani
`inventoryTruncated`; pozostaly jedynie testy negatywne blokujace ich powrot.
Dokumenty kanoniczne opisuja teraz jeden `bootstrapApplication ->
provideRouter` graph, targeted reads, ref-level revision i wersje V2 kopert UI
Explorer.

Weryfikacja 8B.6 przeszla na wylacznie silnie zanonimizowanych danych CRM:
celowane testy GitLab Frontend, REST adaptera, shared API, UI Explorer i
dependency guard; Angular 55 plikow/409 testow; produkcyjny build Angulara;
`mvn -q -Pbackend-dev clean package` z 1200 testami backendu, bez failures,
errors i skipped. Powstal aktualny JAR z wygenerowanym bundle UI.

Kryterium akceptacji 8B: repozytorium wieksze niz dowolny dawny limit
inventory daje kompletny, deterministyczny route catalog, jezeli caly statyczny
graf jest osiagalny od jednego `provideRouter` i miesci sie w semantycznych
budzetach. Liczba niepowiazanych plikow w repozytorium nie zmienia wyniku.
Zero/wiele rootow, dynamiczna krawedz, plik poza scope i przekroczenie budzetu
maja rozroznialne diagnostics. Wybor ekranu buduje source context tylko dla
jego route chain i powiazanych zaleznosci. Nie istnieje wykonanie starego
inventory discovery ani kontrakt kompatybilnosci.

#### 8C. Korekta targeted traversal po pilocie duzego monorepo

Zakres 8C jest zatwierdzonym korekcyjnym inkrementem L2 po analizie wyniku
GitLab Frontend Discovery z duzego Angular/Nx monorepo. Nie zmienia publicznych
endpointow, DTO, `screenId`, exportu ani UI; naprawia neutralna integracje,
ktora przedwczesnie zuzywa semantyczny budzet i nie obsluguje poprawnego
default-export `loadComponent`.

Baseline: root `bootstrapApplication -> provideRouter`, statyczne route arrays,
named `component`, `loadComponent`, `loadChildren`, aliasy `tsconfig`,
re-exporty, effective route chain i hard scope boundary pozostaja bez zmian.
Katalog publikuje tylko wezly `SCREEN` zakonczone konkretnym targetem widoku.
Obecny resolver sprawdza piec zgadywanych nazw dla kazdego extensionless
importu, liczy nietrafione kandydaty jako source reads, rozwiazuje komponenty
przed zakolejkowanymi lazy route collections i po wyczerpaniu budzetu emituje
kaskade mylacych `IMPORT_TARGET_NOT_FOUND`. Bezposredni
`loadComponent: () => import(...)` nie ma symbolu `.then(...)`, mimo ze Angular
poprawnie uzywa default exportu. Nierozwiazany zwykly `component` jest
klasyfikowany inaczej niz nierozwiazany `loadComponent`.

Conformance delta: wlascicielem pozostaje `integrations.gitlab.frontend`.
Resolver ma stosowac deterministyczna kolejnosc TypeScript `module.ts`, potem
`module/index.ts`, zatrzymywac sie po pierwszym trafieniu i cache'owac wynik.
Traversal ma najpierw domknac topologie `children/loadChildren`, a dopiero
potem rozwiazywac view targets. Bezposredni dynamic import ma oznaczac default
export i zachowywac rzeczywista nazwe eksportowanej klasy/funkcji/symbolu.
Po wyczerpaniu source-read budgetu pozostaje jedna diagnostyka przyczynowa,
bez wtornych `IMPORT_TARGET_NOT_FOUND`. Kazda deklaracja widoku bez targetu ma
spojny kind `UNRESOLVED`. Konsumenci publiczni - shared GitLab API, Tool
Workbench i UI Explorer - zachowuja kontrakty i automatycznie otrzymuja wiecej
wezelow `SCREEN`.

- [x] 8C.1: Zaimplementowac default-export lazy target, deterministyczne i
  cache'owane module resolution, route-topology-first traversal, diagnostyke
  bez kaskady po source-read limit oraz spojna klasyfikacje unresolved view.
- [x] 8C.2: Dodac regresje wylacznie na silnie zanonimizowanym CRM dla direct
  default `loadComponent`, duzego katalogu lazy components mieszczacego sie w
  budzecie, lazy route zachowanego przed view traversal, TypeScript file/index
  precedence i nierozwiazanego zwyklego componentu.
- [x] 8C.3: Zweryfikowac pakiet `integrations.gitlab.frontend`, katalog UI
  Explorer, shared GitLab API, `PackageDependencyGuardTest`, pelne
  `mvn -q test` oraz architecture diff. Frontend build nie jest wymagany,
  jezeli publiczny kontrakt backend-frontend i pliki Angulara pozostana bez
  zmian.
- [x] 8C.4: Zaktualizowac kanoniczny opis module resolution, default-export
  lazy targets, route-first traversal i failure semantics. Wszystkie fixtures,
  snapshoty i przyklady pozostaja silnie zanonimizowanym CRM.

Checkpoint 8C (2026-08-16): targeted traversal rozpoznaje bezposredni
`loadComponent: () => import(...)` jako default export i zachowuje nazwe
rzeczywistego symbolu widoku. Extensionless import stosuje kolejnosc
TypeScript `module.ts`, potem `module/index.ts`, zatrzymuje sie po pierwszym
trafieniu i korzysta z cache. Traversal domyka route topology przed view
targets, a wyczerpanie source-read budgetu nie generuje kaskady wtornych
`IMPORT_TARGET_NOT_FOUND`. Nierozwiazane deklaracje widoku maja spojny kind
`UNRESOLVED`. Publiczne endpointy, DTO, `screenId` i frontend pozostaly bez
zmian; UI Explorer automatycznie otrzymuje dodatkowe wezly `SCREEN` z tego
samego katalogu. Regresje konsumentow i `PackageDependencyGuardTest` przeszly,
a pelne `mvn -q test` zakonczylo sie wynikiem 1206 testow, 0 failures, 0 errors
i 0 skipped. Diff nie dodaje nowego kierunku zaleznosci. Wszystkie nowe
fixtures i przyklady sa silnie zanonimizowanym CRM.

#### 8D. Korekta local lazy factories i budzetu katalogu

Zakres 8D jest zatwierdzonym korekcyjnym inkrementem L2 na podstawie kolejnego
eksportu Tool Workbench oraz testowych plikow z tego samego refu. Publiczne
endpointy, DTO, `screenId` i UI pozostaja bez zmian.

Baseline: route topology odnajduje 303 wezly i 41 plikow routingu, ale katalog
zatrzymuje 99 wezlow jako `UNRESOLVED`. Parser nie rozpoznaje poprawnego
Angularowego `loadComponent: () => ImportedComponent`, a parser importow nie
obsluguje default importu. `children` zbudowane przez statyczne splaszczenie
`CONFIG.reduce(...cur.routes...)` jest oznaczane jako dynamiczne mimo ze
zrodlowe `routes` sa literalnymi tablicami. Domyslne 50 000 znakow na plik i
500 000 znakow lacznie sa za male dla poprawnie ograniczonego katalogu duzego
monorepo; po osiagnieciu limitu powstaje kaskada wtornych diagnostics.

Conformance delta: `integrations.gitlab.frontend` ma statycznie rozpoznawac
local lazy factory, named i default import oraz literalne `routes` splaszczane
z lokalnej albo importowanej tablicy konfiguracji, bez wykonywania TypeScriptu.
Domyslny budzet pozostaje twardo ograniczony, ale wykorzystuje zatwierdzone
gorne granice 200 000 znakow na plik i 2 000 000 lacznie. Po wyczerpaniu
budzetu lacznego traversal zatrzymuje kolejne odczyty i publikuje jedna
diagnostyke przyczynowa bez wtornych `IMPORT_TARGET_NOT_FOUND`.

- [x] 8D.1: Obsluzyc local lazy factories, default imports i statyczne
  splaszczenie tablic `routes` z konfiguracji.
- [x] 8D.2: Skalibrowac ograniczone budzety oraz zatrzymac kaskade diagnostics
  po wyczerpaniu limitu lacznego.
- [x] 8D.3: Dodac silnie zanonimizowane regresje CRM dla wszystkich nowych
  wariantow i konsumentow katalogu.
- [x] 8D.4: Zweryfikowac testy celowane, shared API, UI Explorer,
  `PackageDependencyGuardTest`, pelne `mvn -q test` i architecture diff.

Checkpoint 8D (2026-08-16): parser importow obsluguje default import, a local
lazy factory `() => ImportedComponent` jest rozwiazywana tym samym statycznym
grafem co zwykly `component`. `children` oparte o lokalna albo importowana
tablice konfiguracji i statyczne `reduce/flatMap(...routes...)` rozwijaja
literalne pola `routes`, lacznie z ich dalszymi `children`. Domyslne hard limits
wynosza 200 000 znakow na plik i 2 000 000 lacznie. Po wyczerpaniu limitu
lacznego kolejne odczyty sa zatrzymywane, pozostaje jedna diagnostyka
przyczynowa i nie powstaje kaskada `IMPORT_TARGET_NOT_FOUND`. Publiczne API,
DTO, `screenId` i frontend pozostaly bez zmian. Testy celowane i konsumenci
shared API/UI Explorer przeszli, `PackageDependencyGuardTest` nie wykazal
nowego kierunku zaleznosci, a pelne `mvn -q test` zakonczylo sie wynikiem 1209
testow, 0 failures, 0 errors i 0 skipped. Wszystkie nowe fixtures sa silnie
zanonimizowanym CRM.

#### 8E. Skupienie produktu wylacznie na dokumentacji funkcjonalnej

Baseline: publiczny request/result, input-options, prompt, skille, historia,
import/export i Angular obsluguja trzy profile, a wariant przygotowania zmiany
ma dodatkowy `changePreparationSummary`. Conformance delta usuwa caly koncept
profilu oraz change-preparation z pionowego slice'a. Dokumentacja funkcjonalna
staje sie jedynym znaczeniem runu. Nie powstaje alias, stale pole profilu,
migrator ani odczyt poprzedniego exportu.

Konsumenci: job request/snapshot, input-options, artifacts i parser AI,
result/report, local history, import/export, Angular facade/configuration/
result oraz dokumentacja kanoniczna. Export i local-run schema zostaja
podniesione, a starsze wersje sa jawnie odrzucane.

- [x] 8E.1: Usunac `UiExplorerProfile`, pole `profile` i wszystkie opcje celu z
  backendowego requestu, snapshotu, resultu, input-options i identity parsera.
- [x] 8E.2: Usunac `UiExplorerChangePreparationSummary`, jego parser, fallback,
  sanitizer, assembler oraz instrukcje z promptu i runtime skilla.
- [x] 8E.3: Usunac wybor goal/profile i material przygotowania zmiany z modeli,
  facade, konfiguratora, wyniku, copy/download i read-only UI Angulara.
- [x] 8E.4: Podniesc bezkompatybilnie local-run/export schema i pokryc jawne
  odrzucanie poprzedniej wersji.
- [x] 8E.5: Zaktualizowac potrzebe, plan, architecture i lokalne instrukcje do
  jednego celu: dokumentacji funkcjonalnej.
- [x] 8E.6: Zweryfikowac testy Angulara, build Angulara i
  `mvn -q -Pbackend-dev clean package`; wszystkie zmienione fixtures pozostaja
  silnie zanonimizowanym, syntetycznym CRM.

Checkpoint 8E 2026-08-16: UI Explorer ma jeden cel produktowy — dokumentacje
funkcjonalna — bez pola ani stalej `profile`. Usunieto warianty przygotowania
zmiany i dokumentacji technicznej, `changePreparationSummary`, `impactNotes`
oraz odpowiadajace im elementy promptu, skilli i UI. Input options publikuje
bezposrednio domyslne tryby sekcji. Publiczny start jawnie odrzuca usuniete i
inne nieznane pola, a local-run/export uzywaja wersji `3` i kontraktu
`ui-explorer-result-v3`; wersja `2` nie jest migrowana. Przeszly testy Angulara
(409/409), produkcyjny build Angulara oraz `mvn -q -Pbackend-dev clean package`
(1210 testow, 0 failures, 0 errors, 0 skipped). Zmienione fixtures pozostaja
silnie zanonimizowanym, syntetycznym CRM.

#### 8F. Biznesowy kontrakt dokumentacji funkcjonalnej

Baseline: `UiExplorerResultSection` przechowuje generyczne `findings`, a
assembler renderuje kazda sekcje jako techniczna liste "Ustalenia" z confidence
przy kazdym punkcie. Runtime skill opisuje osiem sekcji jednym zdaniem. W
kontrolowanych eksportach prowadzi to do powtarzalnych zestawow 2-4 obserwacji,
tytulow rozpoczynanych od route, guarda, komponentu albo NgRx i przenoszenia
luk do glownej narracji zamiast samowystarczalnego opisu pracy uzytkownika.

Conformance delta: sekcja wyniku staje sie business-first Markdownem o
kanonicznej strukturze zależnej od `sectionId` i `mode`. Nazwy klas, metod,
plikow, operatorow RxJS i linii kodu pozostaja w source references, chyba ze
techniczny identyfikator ma bezposrednie znaczenie funkcjonalne. `DEEP` wymaga
pelnego katalogu widocznych regul, warunkow, wariantow i skutkow, a nie stalej
liczby punktow. Braki trafiaja do metadata, nie zastepuja opisu. Generyczny
`UiExplorerFinding` zostaje usuniety zamiast adaptowany.

Konsumenci: response contract i strict parser AI, packaged runtime skille,
prompt/artifacts, result/report assembler, sanitizowana historia, import/export,
Angular models/result/copy-download oraz dokumentacja. Jest to breaking zmiana:
local-run/export przechodza na wersje `4` i `ui-explorer-result-v4`; wersja `3`
nie jest migrowana ani odczytywana.

- [x] 8F.1: Zastapic `findings` polem `markdown` w sekcji wyniku i usunac
  `UiExplorerFinding` z kontraktu, parsera, sanitizera i fixtures.
- [x] 8F.2: Rozbudowac canonical prompt oraz trzy skille UI Explorera o
  business-first language policy, density/completeness gate i osobny kontrakt
  tresci dla kazdej z osmiu sekcji.
- [x] 8F.3: Uproscic assembler i UI do renderowania gotowej dokumentacji
  Markdown bez technicznego naglowka "Ustalenia" i confidence przy kazdym
  punkcie; evidence pozostaje w zwijanym meta.
- [x] 8F.4: Podniesc bezkompatybilnie artifact, local-run, export i result
  contract oraz pokryc odrzucenie wersji `3`.
- [x] 8F.5: Dodac silnie zanonimizowane testy CRM wymagajace biznesowych
  struktur overview, akcji, formularzy, danych i wariantow oraz zakazujace
  class-first narracji w assemblerze.
- [x] 8F.6: Zaktualizowac need, architecture i lokalne instrukcje, a nastepnie
  wykonac testy Angulara, produkcyjny build i
  `mvn -q -Pbackend-dev clean package`.

Checkpoint 8F (2026-08-16): kontrakt `UiExplorerFinding`/`summary` zostal
usuniety bez adaptera i migracji. Aktywna sekcja publikuje gotowy biznesowy
Markdown, confidence i source references jako osobne metadata. Canonical
artifact `functional-writing-contract.md` definiuje odrebna strukture tresci
dla osmiu sekcji, a `DEEP` nie ma sztucznego limitu liczby faktow. Local run i
export uzywaja wersji `4` oraz `ui-explorer-result-v4` i jawnie odrzucaja
wersje `3`. Przeszly testy Angulara (409/409), produkcyjny build Angulara oraz
`mvn -q -Pbackend-dev clean package` (1212 testow, 0 failures, 0 errors,
0 skipped). Wszystkie nowe i zmienione fixtures sa silnie zanonimizowanym,
syntetycznym CRM.

#### 8G. Evidence per krok i inicjalny prompt w aside

Baseline: `UiExplorerJobStateSnapshot` ma pola `contextSections`,
`toolEvidenceSections` i `preparedPrompt`, a ekran korzysta ze wspolnego
`AnalysisFeatureAsideComponent` oraz `AnalysisStepsPanelComponent`. Mimo tego
`MutableStep` publikuje puste `consumesEvidence`/`producesEvidence`, job zawsze
zwraca `preparedPrompt = null`, a UI nie przekazuje promptu do panelu. W efekcie
zebrany context nie jest przypisany do krokow i aside nie pokazuje finalnego
inputu przed wyslaniem do Copilota.

Conformance delta: zachowac istniejacy publiczny shape i shared UX, ale
uzupelnic feature-owned state. `SCREEN_DISCOVERY` publikuje dane wybranego
widoku, `SOURCE_CONTEXT` publikuje manifest, sygnaly, coverage, boundary i
diagnostyke, `AI_PREPARATION` publikuje bezpieczne podsumowanie przygotowanych
artefaktow oraz dokladny `preparedPrompt`, a `AI_ANALYSIS` jawnie konsumuje
przygotowany context. Prompt jest ustawiany po deterministycznym przygotowaniu,
przed startem sesji AI, wiec pozostaje widoczny rowniez przy pozniejszym
niepowodzeniu Copilota. Nie powstaje drugi komponent aside ani endpoint
diagnostyczny.

Konsumenci: UI Explorer job state i polling API, local-run/export v4, wspolny
`AnalysisStepsPanelComponent`, strona UI Explorera oraz testy joba, API,
persistence/import-export i Angulara. Flow Explorer pozostaje niezmienionym
konsumentem shared panelu. Brak warstwy kompatybilnosci: nie dodajemy aliasow,
fallbacku ani starego sposobu prezentacji; istniejace nullable pole
`preparedPrompt` zaczyna przenosic wartosc zgodna z jego kontraktem.

- [x] 8G.1: Przypisac kazdej sekcji deterministic evidence wlascicielski krok
  przez `producesEvidence`/`consumesEvidence` i dodac bezpieczna sekcje
  podsumowujaca artefakty przygotowane dla AI.
- [x] 8G.2: Zapisac kanoniczny prompt w job state bezposrednio po
  `UiExplorerPromptPreparationService.prepare`, przed uruchomieniem providera,
  oraz zachowac go w terminalnym snapshotcie, local history i eksporcie v4.
- [x] 8G.3: Przekazac `snapshot.preparedPrompt` do wspolnego panelu aside i
  rozszerzyc jego neutralne mapowanie o krok `AI_PREPARATION`, bez kopiowania
  komponentu Flow Explorera.
- [x] 8G.4: Dodac silnie zanonimizowane testy CRM potwierdzajace evidence na
  kazdym kroku, prompt widoczny przed AI i po kontrolowanym failure oraz brak
  regresji Flow Explorera.
- [x] 8G.5: Zaktualizowac need i architecture, wykonac testy Angulara,
  produkcyjny build Angulara oraz `mvn -q -Pbackend-dev clean package`.

Checkpoint 8G 2026-08-16: kroki UI Explorera publikuja jawne
`consumesEvidence`/`producesEvidence` dla wybranego ekranu, bounded source
contextu i przygotowanych artifacts. `AI_PREPARATION` zapisuje dokladny
`preparedPrompt` przed providerem AI oraz bezpieczna sekcje metadanych
`ui-explorer/ai-artifacts`; prompt pozostaje dostepny po pozniejszym bledzie
AI, w lokalnej historii i eksporcie v4. Niezaufany import nadal usuwa
dostarczony prompt. Wspolny aside pokazuje evidence i prompt bez osobnej
implementacji UI Explorera, uzywa czytelnych nazw sekcji oraz pozwala otwierac
kroki `COMPLETED`, `PARTIAL`, `BLOCKED` i `FAILED`. Przeszly testy Angulara
(411/411), produkcyjny build Angulara oraz
`mvn -q -Pbackend-dev clean package` (1213 testow, 0 failures, 0 errors,
0 skipped). Wszystkie nowe fixtures i przyklady sa silnie zanonimizowanym,
syntetycznym CRM.

#### 8H. Kompletne zrodla wybranego widoku i uproszczenie raportu

Zakres 8H jest zatwierdzonym przez uzytkownika inkrementem L2. Dotyka
reusable traversal frontendu w `integrations.gitlab`, feature-owned promptu,
skilli i tool policy, breaking kontraktu wyniku oraz prezentacji Angulara.
Source need pozostaje `../needs/ui-explorer.md`; bezposrednim dowodem sa dwa
kontrolowane eksporty runow z 2026-08-17. Wszystkie testy, fixture'y i
przyklady tego inkrementu sa silnie zanonimizowanym, syntetycznym CRM.

Baseline: analiza `/admin` osiagnela `maxContextFiles=40` po pobraniu wielu
ogolnych modeli i zaleznosci infrastrukturalnych, zanim zebrala istotne
komponenty potomne, modale i serwisy. Analiza `/wallet` zebrala 35 plikow i
nie osiagnela limitu, ale zatrzymala sie na komponencie-kontenerze, poniewaz
context traversal nie wlaczal routowanego poddrzewa wybranego ekranu. Oba runy
zakonczyly sie bez wywolan fallbackowych GitLab tools. Wynik przeniosl te
unikalne braki do ogolnych `visibilityLimits`, zamiast domknac material
zrodlowy. Dodatkowo kontrakt wymusza osobne zaleznosci sekcji, ktore assembler
dopisuje jako `Powiazane warunki i zaleznosci`, a UI powtarza je jako
`Zaleznosci przekrojowe`. Metadata sekcji sa wyrównane do lewej, a naglowek
wyniku zawiera dwie zbedne akcje rozpoczecia/importu.

Conformance delta: deterministic context zaczyna od wybranego widoku i jego
routowanych potomkow, a dopiero pozniej poglebia zaleznosci konfiguracji.
Budzet plikow i odczytow wykorzystuje istniejace twarde gorne granice, lecz
nie moze byc jedynym mechanizmem kompletności. Materialna luka w komponencie,
formularzu, modalu, serwisie albo child route z analizowanego repozytorium
obliguje AI do kontrolowanego search/read przed finalizacja; ograniczenie jest
dopuszczalne dopiero po bezskutecznym lub wyczerpanym dozwolonym fallbacku.
Feature usuwa caly kontrakt zaleznosci przekrojowych bez aliasu i migracji.
Local-run/export przechodza na wersje 5 i `ui-explorer-result-v5`, a wersja 4
jest jawnie odrzucana. Metadata sekcji korzystaja z prawego wyrownania shared
reportu, a akcje `Import another` i `New UI Explorer run` znikaja z wyniku.

Konsumenci: GitLab frontend screen context API i UI Explorer, result DTO i
strict parser, prompt/artifacts, trzy runtime skille, budget/scope policy,
report assembler, sanitizer, local history/import-export oraz Angular models,
result, report export i page. Inne feature'y nie konsumuja kontraktu wyniku UI
Explorera; katalog ekranow zachowuje publiczny shape. Reusable traversal nie
otrzymuje semantyki feature'a ani nowego kierunku zaleznosci.

- [x] 8H.1: Rozszerzyc bounded deterministic context o routowane poddrzewo
  wybranego widoku, nadac priorytet komponentom i zaleznosciom funkcjonalnym
  przed ogolna konfiguracja oraz skalibrowac limity w istniejacych hard caps.
- [x] 8H.2: Rozszerzyc feature-owned GitLab tool budget i completeness gate w
  prompcie oraz skillach tak, aby materialne braki kodu z repozytorium
  wymuszaly probe search/read przed publikacja visibility limit.
- [x] 8H.3: Usunac `dependencies` i `crossSectionDependencies` z calego pionu
  bez kompatybilnosci oraz podniesc local-run/export/result schema do v5.
- [x] 8H.4: Wyrownac metadata sekcji do prawej i usunac akcje `Import another`
  oraz `New UI Explorer run` wraz z martwa logika i stylami.
- [x] 8H.5: Dodac silnie zanonimizowane regresje CRM dla kontenera z child
  routes, priorytetu modalu/serwisu, wymaganego fallbacku, breaking importu i
  uproszczonego UI; zaktualizowac architecture oraz lokalne instrukcje.
- [x] 8H.6: Wykonac testy celowane, testy Angulara, produkcyjny build Angulara
  i `mvn -q -Pbackend-dev clean package`, a nastepnie przeprowadzic
  architecture diff wszystkich konsumentow.

Checkpoint 8H (2026-08-17): deterministic context obejmuje routowane poddrzewo
wybranego widoku i nadaje pierwszenstwo korzeniom komponentow przed mniej
istotnymi zaleznosciami. UI Explorer ma stale dostepny, scope-bound fallback do
GitLab; wynik deklarujacy mozliwa do uzupelnienia luke kodu bez udokumentowanej
proby fallbacku jest odrzucany. Usunieto przekrojowe zaleznosci z kontraktu,
promptu, raportu i UI, a local-run/export/result uzywaja breaking schema v5.
Metadata sekcji sa wyrownane do prawej, a zbedne akcje wyniku zostaly usuniete.
Regresje uzywaja wylacznie silnie zanonimizowanego, syntetycznego CRM. Kontrole:
celowane testy traversal/provider — PASS; `npm --prefix frontend test --
--watch=false` — 55 plikow i 411 testow PASS; `npm --prefix frontend run build`
— PASS; `mvn -q -Pbackend-dev clean package` — PASS. Architecture diff nie
dodal nowego kierunku zaleznosci: traversal pozostaje neutralny w integracji,
a semantyka kompletności pozostaje w feature UI Explorer.

#### 8I. Eksploracja sterowana celem bez feature'owego budzetu wywolan

Zakres 8I jest zatwierdzona przez uzytkownika korekta L1 do 8H. Source need
pozostaje `../needs/ui-explorer.md`. Baseline: trzy runtime skille deklaruja
limit trzech search calls i dwunastu read calls, a
`UiExplorerCopilotBudgetPolicy` twardo odrzuca kolejne wywolanie nawet wtedy,
gdy aktywna sekcja nadal ma rozstrzygalna luke w kodzie badanego repozytorium.
To przeczy goal-driven completeness i pozwala licznikowi wywolan zastapic cel
analizy.

Conformance delta: UI Explorer nie posiada feature'owego limitu liczby search
ani read calls. Source grounding iteruje po konkretnych lukach do chwili, gdy
aktywne sekcje sa gotowe albo konkretne zrodlo zostalo bezskutecznie wyszukane
lub potwierdzone jako runtime/zewnetrzne/poza zatwierdzonym repository scope.
Pozostaja scope/ref/path validation, read-only allowlista, zakaz broad browse,
limit pojedynczego transferu, ochrona runtime przed cyklem i timeout sesji.
Bounded deterministic snapshot jest materialem startowym i nie stanowi limitu
dalszej eksploracji AI.

Konsumenci: feature policy i wiring providera, prompt, trzy runtime skille,
testy polityk/providera oraz dokumentacja architektury. Kontrakt HTTP, result
schema v5, import/export, katalog ekranow i frontend nie zmieniaja sie.
Wszystkie nowe testy i przyklady pozostaja silnie zanonimizowanym,
syntetycznym CRM.

- [x] 8I.1: Usunac `UiExplorerCopilotBudgetPolicy` oraz jego lifecycle z
  providera bez pozostawienia aliasu ani nieaktywnej konfiguracji.
- [x] 8I.2: Usunac liczby i wyczerpanie budzetu z promptu i skilli; zapisac
  petle `needs_deeper_evidence -> targeted search/read -> readiness` az do celu
  albo potwierdzonej granicy widocznosci.
- [x] 8I.3: Dodac silnie zanonimizowana regresje CRM potwierdzajaca brak
  feature'owego limitu liczby poprawnych, scope-bound wywolan.
- [x] 8I.4: Zaktualizowac need, architecture i lokalne instrukcje, wykonac
  testy celowane oraz pelna macierz backend-frontend adekwatna dla zmiany
  wspolnego runtime wiring.

Checkpoint 8I (2026-08-17): feature'owa polityka call-count zostala usunieta
w calosci, a provider nie utrzymuje jej stanu. Prompt i trzy skille prowadza
petle po konkretnych lukach do readiness albo potwierdzonej granicy
runtime/zewnetrznego repository scope; nie zawieraja liczbowego budzetu ani
wyczerpania budzetu jako warunku finalizacji. Regresja syntetycznego CRM
potwierdza, ze 50 kolejnych poprawnych search i 50 read calls nie jest
odrzucanych przez polityke feature'a. Scope/ref/path validation, read-only
allowlista, limit pojedynczego transferu i timeout runtime pozostaly bez zmian.
Testy celowane — PASS; `mvn -q test` — PASS. Frontend i kontrakt HTTP/result
nie zmienily sie, dlatego zgodnie z macierza weryfikacji nie powtarzano builda
Angulara. Architecture diff nie dodal nowego kierunku zaleznosci.

#### 8J. Nawigowalny krok przygotowania promptu przy czastkowym context

Zakres 8J jest zatwierdzona korekta L1 wspolnego UX przebiegu analizy. Baseline:
`AnalysisStepsPanelComponent` uzywa liniowego Material steppera. Gdy
`SOURCE_CONTEXT` konczy sie poprawnym statusem `PARTIAL`, Material uznaje go za
nieukonczony i blokuje klikniecie nastepnego, zakonczonego `AI_PREPARATION`,
przez co operator nie moze podejrzec zapisanego `preparedPrompt`.

Conformance delta: stepper przebiegu jest read-only inspektorem, a nie wizardem
wejsciowym, dlatego nie wymusza liniowej progresji. Lokalna polityka
`canOpenStep` nadal nie pozwala otwierac krokow `PENDING`/`IN_PROGRESS`, lecz
kazdy zakonczony `COMPLETED`/`PARTIAL`/`BLOCKED`/`FAILED` pozostaje klikalny
niezaleznie od statusu poprzednika. Konsumentami sa wszystkie feature'y
reuse'ujace shared panel; nie zmienia sie API ani model kroku. Regresja uzywa
wylacznie silnie zanonimizowanego, syntetycznego CRM.

- [x] 8J.1: Usunac liniowa blokade nawigacji ze shared steppera bez kopiowania
  komponentu do UI Explorera.
- [x] 8J.2: Dodac regresje `PARTIAL source context -> COMPLETED AI preparation`
  potwierdzajaca klikniecie kroku i podglad dokladnego promptu.
- [x] 8J.3: Wykonac testy Angulara, produkcyjny build i celowany test granicy
  statycznych zasobow backendu.

Checkpoint 8J (2026-08-17): shared Material stepper nie wymusza juz liniowej
progresji, natomiast lokalne `canOpenStep` nadal blokuje kroki nierozstrzygniete.
Regresja syntetycznego CRM rozpoczyna panel na aktywnym source context,
aktualizuje go do `PARTIAL`, konczy `AI_PREPARATION`, klika ten krok i
potwierdza dokladna tresc `preparedPrompt`. `npm --prefix frontend test --
--watch=false` — 55 plikow i 412 testow PASS; `npm --prefix frontend run
build` — PASS; `mvn -q -Dtest=FrontendPageTest test` — PASS. Zmiana reuse'uje
shared komponent bez nowego kierunku zaleznosci i bez zmiany API.

#### 8K. Business-first identyfikacja widoku

Zakres 8K jest zatwierdzona przez uzytkownika korekta L1 prezentacji katalogu
i raportu. Baseline: selektor `View` pokazuje nazwe komponentu jako glowna
etykiete, a route jako detal. Kanoniczny assembler raportu powtarza komponent
w `h3`, natomiast pod nim eksponuje `branch @ commit`. Taka hierarchia wymaga
od analityka rozumienia technicznego nazewnictwa Angulara, zanim rozpozna
badany ekran.

Conformance delta: `routePattern` staje sie glowna identyfikacja w kontrolce,
opcjach selektora i naglowku raportu. Nazwa komponentu jest informacja
pomocnicza w `small` oraz podtytulem raportu. Rewizja pozostaje w typed result,
job state i eksporcie, ale nie zajmuje miejsca przeznaczonego na identyfikacje
widoku. Zmiana powstaje w kanonicznym assemblerze, wiec obejmuje live, historie
i import bez frontendowego adaptera ani alternatywnego renderera.

Konsumenci: screen catalog Angulara, report assembler, live/history/import,
copy/download Markdown oraz testy prezentacji i portability. Publiczny shape
DTO nie zmienia sie. Wszystkie nowe i zmienione fixture'y sa silnie
zanonimizowanym, syntetycznym CRM.

- [x] 8K.1: Zamienic hierarchie `routePattern` i komponentu w wybranej wartosci
  oraz opcjach selektora `View`.
- [x] 8K.2: Ustawic path jako header raportu, a komponent jako subheader zamiast
  `branch @ commit`, bez osobnego mapowania w Angularze.
- [x] 8K.3: Dodac silnie zanonimizowane regresje CRM dla selektora, renderera,
  Markdown oraz kanonicznego assemblera.
- [x] 8K.4: Wykonac testy Angulara, produkcyjny build i adekwatne testy
  backendowej granicy raportu oraz statycznych zasobow.

Checkpoint 8K (2026-08-18): selektor `View` pokazuje `routePattern` w
`strong`, a nazwe komponentu w `small`; ten sam porzadek obowiazuje w
kanonicznym naglowku raportu, copy/download Markdown, historii i imporcie.
`branch @ commit` nie jest juz podtytulem raportu, ale typed source revision
pozostaje bez zmian w job state i eksporcie. Regresje uzywaja wylacznie silnie
zanonimizowanego, syntetycznego CRM. Celowane testy assemblera i portability —
PASS; `npm --prefix frontend test -- --watch=false` — 56 plikow i 425 testow
PASS; `npm --prefix frontend run build` — PASS;
`mvn -q -Pbackend-dev clean package` — PASS.

#### 8L. Precyzyjne route i TypeScript symbol slices

Status kroku: completed. Uzytkownik zatwierdzil 2026-08-19 pierwszy zakres
wykonawczy: neutralne route/TypeScript slices oraz ich reczny preview w GitLab
Tool Workbench. Zmiana initial promptu i ekspozycja MCP pozostaja osobnymi,
niezatwierdzonymi jeszcze bramkami. Source need pozostaje
`../needs/ui-explorer.md`. Pilot artefaktow v5 zostal wycofany: dla tego samego
widoku `/wallet` zwiekszyl inicjalny prompt z 745 938 do 1 239 678 znakow
(+66,2%) i przekroczyl limit modelu wynikiem 318 391 tokenow. Pelny
`screen-use-case-manifest.json`, szczegolowy `screen-research-frontier.json`
oraz setki powtarzalnych diagnostics nie sa akceptowanym kierunkiem.

Baseline po wycofaniu: UI Explorer ponownie uzywa kontraktu artefaktow v4 i
`context-snapshot.json`. Goal-driven research nie jest zatrzymywany przez
platformowy call/character budget, ale pojedynczy request nadal musi miescic
sie w fizycznym limicie kontekstu modelu.

Proponowana conformance delta nie ogranicza researchu liczba plikow. Zamiast
tego buduje kodowe wycinki na podstawie osiagalnosci od wybranego widoku:

- route branch slice zachowuje przodkow, wybrana trase, istotne dzieci,
  guardy, resolvery, data/providers oraz tylko uzywane deklaracje i importy;
  rodzenstwo jest zastepowane pojedynczym markerem z liczba pominietych tras,
- TypeScript symbol slice zachowuje wskazana metode albo pole, lokalne helpery,
  uzywane pola oraz importy; pozostale elementy klasy sa zastapione jednym
  markerem z liczba pominietych elementow,
- service/facade/guard/validator/effect/reducer/selector sa analizowane przez
  ten sam neutralny mechanizm symbol slice zamiast przez odczyt calego pliku,
- relacje i frontier powstaja dopiero z zachowanych fragmentow; pelny graf
  pozostaje po stronie backendu i nie jest osadzany w inicjalnym prompcie,
- niejednoznaczna zaleznosc pozostaje recoverable przez stabilna referencje i
  targeted tool call; nie jest zgadywana ani usuwana jako szum.

Konsumenci: `integrations.gitlab.frontend`, GitLab tools i Tool Workbench, UI
Explorer source context, prompt/artifacts, runtime skille, readiness oraz
silnie zanonimizowane testy CRM. Zmiana internal artifact contract nie zachowuje
kompatybilnosci wstecznej, ale musi zostac dostarczona inkrementalnie z pomiarem
rozmiaru promptu po kazdym kroku.

- [x] 8L.1: Dodac silnie zanonimizowane fixture'y CRM i baseline rozmiaru
  promptu dla prostego widoku, glebokiej sciezki oraz kontenera z wieloma
  dziecmi; test nie moze akceptowac wzrostu initial context bez uzasadnionej
  nowej informacji funkcjonalnej. W pierwszym zakresie Workbench baseline
  obejmuje rowniez liczbe znakow pelnego pliku i odpowiadajacego mu slice'a.
- [x] 8L.2: Dodac neutralny Angular route branch slice z dokladnymi markerami
  pominietych sibling routes/imports oraz source references; nie wlaczac go
  jeszcze do promptu produkcyjnego.
- [x] 8L.3: Dodac neutralny TypeScript symbol slice wzorowany na
  `read_java_method_slice`: osiagalne lokalne helpery, pola, importy i
  downstream symbol references dla komponentow, serwisow, fasad i guardow.
- [x] 8L.4: Wystawic oba neutralne capability przez jawny operatorski scope w
  shared/operator API i istniejacej grupie `Frontend Discovery` w GitLab Tool
  Workbench. Preview ma pokazywac dokladny request, response, pominiete
  elementy, source references i roznice rozmiaru bez uruchamiania AI.
- [x] 8L.4a: Utwardzic capability po tescie na duzym froncie: route slice ma
  domykac uzywane lokalne deklaracje i jawnie raportowac nierozwiazane symbole,
  child frontier ma rozrozniac kontenery i dzieci tego samego path, a
  diagnostyczny screen context ma priorytetowo pobierac template wybranego
  komponentu, nie klasyfikowac nazw `*Client*` jako REST bez sygnalu wywolania
  backendu oraz agregowac powtarzalne diagnostics limitow. Wszystkie regresje
  maja uzywac wylacznie silnie zanonimizowanego, syntetycznego CRM.
- [x] 8L.5: Rozszerzyc symbol reachability o template bindings, formularze,
  RxJS, NgRx i operacje backendowe bez keywordowego dolaczania nieosiagalnych
  metod.
- [x] 8L.6a: Dodac neutralny `FrontendScreenReachabilityGraph` oraz czytelny
  renderer BFS do shared/operator API i GitLab Tool Workbench. Wynik ma zaczac
  od effective route chain, nastepnie pokazywac glowny komponent i kolejne
  poziomy dzieci, a faktycznie uzywane serwisy, fasady, state i operacje
  backendowe zapisywac raz w kanonicznym rejestrze z referencjami `usedBy`.
  Sam import nie jest krawedzia. Nie zmieniac jeszcze promptu, skilli ani MCP i
  nie uznawac redukcji tokenow za wazniejsza od kompletnosci grafu.
- [x] 8L.6b: Po recznej akceptacji grafu BFS zastapic v4 snapshot jego
  czytelnym pakietem route/component/dependency oraz iteracyjnymi symbol
  slices. Pelny frontier ma pozostac jawny i ekspandowalny; dodac preflight
  rozmiarow artefaktow widoczny w aside bez arbitralnego obcinania osiagalnej
  informacji. Usunac bez kompatybilnosci wstecznej publiczne
  `Screen Source Context` z shared/operator API i Tool Workbench oraz jego
  pelny traversal plikow. Minimalny resolver selected screen/route/view
  pozostaje wylacznie wewnetrznym seedem `Screen Reachability`; produkcyjny UI
  Explorer ma konsumowac graf BFS, a nie stary context snapshot. Research gaps
  sluza do dalszego deterministycznego dociagania kodu i nie moga automatycznie
  stawac sie biznesowymi `visibilityLimits` rezultatu.
- [x] 8L.7: Wystawic waskie route branch i TypeScript symbol slice jako MCP
  tools dla Copilota; model-facing input ma uzywac bezpiecznej referencji
  slice i `reason`, a repository/ref/path scope ma pochodzic z hidden session
  context. Pelny Screen Reachability pozostaje etapem initial preparation i
  Tool Workbench, nie MCP toolem, aby nie dublowac duzego initial context.
- [x] 8L.8: Wykonac silnie zanonimizowana macierz CRM dla routingu, formularzy,
  REST/WebSocket, NgRx, autoryzacji, dynamicznych granic runtime i braku
  utraty istotnych zachowan; wykonac adekwatne testy frontend/backend i build.

Baseline i conformance delta 8L.8 (2026-08-22): jest to zmiana L1 wylacznie
w feature-owned preparation initial runu. Publiczne request/result, siedem
logical artifacts v5, job state, report, persistence/export, frontend, MCP
schema, hidden scope i deterministyczny Screen Reachability pozostaja bez
zmian. Obecny renderer osadza wszystkie artefakty przez generyczny katalog i
powtarza przy kazdym z nich techniczne pola opakowania, przez co dokladny prompt
jest trudniejszy do review niz wynikowy porzadek route -> komponenty BFS ->
zaleznosci -> source slices -> coverage.

Delta ma renderowac te same tresci artefaktow dokladnie raz, pod stabilnymi,
funkcjonalnymi naglowkami w kolejnosci pracy. Nie wolno wprowadzic limitu
plikow, komponentow ani tool calls, usunac osiagalnego source evidence ani
zmienic trust classification. Silnie zanonimizowana macierz syntetycznego CRM
ma pokryc prosty ekran, gleboki routing/kontener, dynamiczny formularz,
REST/WebSocket, NgRx, guard/role oraz granice runtime. Quality gate mierzy
rozmiar promptu i artefaktow, kolejnosc, jednokrotne osadzenie markerow oraz
zachowanie `researchGaps` jako kolejki pracy, a nie automatycznego
`visibilityLimits`.

Checkpoint 8L.2-8L.4 (2026-08-19): neutralna integracja GitLaba udostepnia
`POST /api/gitlab/frontend/route-branch-slice` oraz
`POST /api/gitlab/frontend/typescript-symbol-slice`. Route slice zaczyna od
istniejacego graph discovery, zachowuje effective chain wybranego `screenId`,
uzyte importy i opcjonalne potomki, a pozostale route objects zastepuje
markerami z dokladnym licznikiem. Domyslnie potomkowie sa zwracani jako
`childRoutes` ze stabilnym `sliceRef`, a nie osadzani w kodzie. TypeScript
slice obsluguje metody, properties, getters/setters, konstruktory, funkcje i
top-level `const`; domyka bezposrednie lokalne helpery, uzyte pola/constructor
dependencies, importy oraz downstream service calls. Pozostale importy, pola
i symbole sa raportowane przez dokladne omission markers i liczniki.

Oba capability sa dostepne recznie w grupie `Frontend Discovery` ekranu
GitLab Source Tool Workbench. Operator widzi pelny request/response, kod
slice'a, `sourceCharacters`, `returnedCharacters`, `savedCharacters`, liczniki
pominietych elementow, candidates, limitations i frontier. Katalog pozwala
przeniesc ekran bezposrednio do route slice, a screen context przeniesc plik
TypeScript do symbol slice. Produkcyjny prompt UI Explorera, runtime skille i
allowlista MCP nie zostaly zmienione. Wszystkie nowe testy i przyklady sa
silnie zanonimizowanym, syntetycznym CRM. Pierwsza czesc baseline 8L.1 mierzy
pelny plik wzgledem slice'a i wymaga realnego spadku liczby znakow; pomiar
trzech initial promptow pozostaje otwarty przed 8L.6.

Weryfikacja: celowane testy integracji/API/route-graph — PASS;
`npm --prefix frontend test -- --watch=false` — 56 plikow i 430 testow PASS;
`npm --prefix frontend run build` — PASS;
`mvn -q -Pbackend-dev clean package` — PASS. Produkcyjny bundle Angulara
zostal odswiezony w `src/main/resources/static`.

Checkpoint 8L.4a (2026-08-19): route branch slice domyka tranzytywnie uzywane
top-level `const`/`let`/`var` i funkcje wraz z wymaganymi importami. Brakujacy
symbol nie jest juz cicho usuwany: plik zwraca `unresolvedSymbols`, odpowiedz
ma status `PARTIAL` oraz diagnostic `SYMBOL_DEPENDENCY_UNRESOLVED`. Child
frontier rozroznia `kind`, `status`, redirect, strukturalny route, dziecko tego
samego path i obecnosc dalszych dzieci. Screen context pobiera template/style
komponentu bezposrednio po jego TypeScript, agreguje diagnostics tego samego
limitu oraz rozpoznaje REST po konkretnym kliencie zamiast po samym tokenie
`Client`; bezposredni `HttpClient` pozostaje osobnym sygnalem. Prompt, skille i
MCP pozostaja bez zmian. Regresje sa silnie zanonimizowanym syntetycznym CRM:
14 celowanych testow backendu — PASS; 56 plikow i 430 testow Angulara — PASS;
produkcyjny build Angulara — PASS; `mvn -q -Pbackend-dev clean package` — PASS.

Checkpoint 8L.5 (2026-08-19): TypeScript symbol slice moze rozpoczac
reachability bez recznego wskazywania symboli, od zewnetrznego `templateUrl`,
jawnego `templatePath` albo inline template. Deterministyczny parser zachowuje
referencje z eventow, property/two-way/structural bindings, interpolacji,
Angular control flow oraz nazw kontrolek formularza. Entry roots obejmuja
symbole faktycznie uzyte przez template i lifecycle hooks analizowanego
komponentu. Dalej slice domyka wszystkie osiagalne lokalne i top-level helpery
bez arbitralnego limitu ich liczby, tranzytywnie uzyte pola, inicjalizatory i
constructor dependencies oraz tylko wymagane importy.

Downstream frontier rozroznia teraz wywolania metod, odczyty properties,
operacje backendowe, NgRx `dispatch`/`select`/actions, operatory RxJS i
importowane funkcje. Sygnal REST w diagnostycznym screen context wymaga
rzeczywistego wywolania metody klienta; sam import typu z wygenerowanego
Swagger/OpenAPI nie wystarcza. Operatorskie API i GitLab Tool Workbench
przyjmuja `templatePath`/`includeTemplateBindings` i pokazuja template
bindings, entry symbols, retained symbols, omission counters oraz skategoryzowany
downstream frontier. Produkcyjny prompt UI Explorera, skille i MCP pozostaja
bez zmian do kolejnych, osobno zatwierdzanych krokow 8L.6-8L.7. Regresje sa
silnie zanonimizowanym syntetycznym CRM: 14 celowanych testow backendu — PASS;
56 plikow i 430 testow Angulara — PASS; produkcyjny build Angulara — PASS;
pelny `mvn -q -Pbackend-dev clean package` — 1268 testow, 0 bledow — PASS.

Checkpoint 8L.6a (2026-08-21): po recznym tescie na duzym froncie reachability
nie buduje juz najpierw ogolnego snapshotu do `maxContextFiles=120`. Zaczyna od
lekkiego route/view seed i dociaga kod bez limitu liczby plikow wylacznie po
potwierdzonych importach, re-exportach, selectorach template oraz downstream
symbol references. BFS jest iteracyjny: komponent znaleziony dopiero w symbol
slice staje sie kolejnym wezlem i moze odkryc wlasne dzieci. Kontrakt nie
zwraca juz nieosiagalnych `unlinkedComponents`.

Selektory obsluguja elementy z atrybutami, klasy, listy selectorow oraz
`:not(...)`; `templateUrl` bez prefiksu `./` jest rozwiazywany wzgledem pliku
komponentu. Template parser odroznia custom pipes i lokale `let-*` od pol
komponentu, a statyczny komponent prezentacyjny otrzymuje kompletny status
`STATIC_PRESENTATIONAL`. Slice z czesciowo nierozwiazanym zestawem metod ma
status `PARTIAL`, a maksymalny output pojedynczego relewantnego slice'a jest
rownany z limitem parsera zrodla zamiast konczyc sie przy 40 000 znakow.

Resolution importu preferuje faktyczny binding z pliku wlasciciela, obsluguje
root tsconfig rowniez przy `pathPrefixes`, przechodzi przez barrel re-exports i
moze wykonac celowane wyszukanie deklaracji dla organizacyjnego serwisu albo
komponentu. Jawny import Angulara nie moze juz zostac polaczony z przypadkowa
lokalna deklaracja o tej samej nazwie. Kanoniczny rejestr dzieli zaleznosci na
`FUNCTIONAL`, `SUPPORTING_CODE`, `REACTIVE`, `FRAMEWORK` i `DATA_MODEL`;
czytelny outline oraz domyslnie otwarta sekcja Workbencha pokazuja pierwsze
dwie kategorie, a techniczny szum pozostaje w zwartej, zwinietej sekcji.
Effective route chain wyjasnia path segment, outlet i source kazdego poziomu.

Produkcyjny prompt UI Explorera, runtime skille i MCP nie zostaly zmienione;
8L.6b pozostaje bramka po ponownym recznym tescie rezultatu. Weryfikacja:
celowane testy integracji i API — PASS; `npm --prefix frontend test --
--watch=false` — 56 plikow i 431 testow PASS; produkcyjny build Angulara —
PASS; `mvn -q -Pbackend-dev clean package` — 1275 testow, 0 bledow — PASS.
Wszystkie nowe regresje i przyklady sa silnie zanonimizowanym, syntetycznym
CRM.

Checkpoint 8L.6b (2026-08-21): produkcyjny UI Explorer nie buduje juz ani nie
przekazuje do AI pelnego `Screen Source Context`. Publiczny endpoint
`/api/gitlab/frontend/screen-context`, jego request/response, pozycja w Tool
Workbench i pelny traversal importow zostaly usuniete bez warstwy
kompatybilnosci. Pozostal jedynie prywatny, minimalny resolver wybranego
route/view, ktory jest seedem neutralnego `Screen Reachability`.

Pakiet artefaktow v5 zaczyna sie od effective route chain, nastepnie pokazuje
komponenty w kolejnosci BFS i deduplikowany rejestr faktycznie uzytych
serwisow, fasad, state oraz operacji backendowych. Prompt, readiness i trzy
runtime skille pracuja na reachability outline oraz symbol slices. Jawne
`researchGaps` sa kolejka dalszego targeted research przez tools, a nie
automatycznym biznesowym `visibilityLimits`. Preflight w aside pokazuje
inicjalny prompt oraz liczbe znakow kazdego artefaktu przed uruchomieniem AI.
Policy pozwala ponownie pobrac plik wlasciciela slice'a, gdy konkretny brak
wymaga szerszego kontekstu; nie ma feature'owego limitu liczby wywolan.

Celowane testy integracji, API, preparation, policies, readiness, parsera,
joba i sanitizera — 35 testow PASS. `npm --prefix frontend test --
--watch=false` — 56 plikow i 430 testow PASS; produkcyjny build Angulara —
PASS; `mvn -q -Pbackend-dev clean package` — 1276 testow, 0 bledow i 0
pominietych — PASS. Produkcyjny bundle zostal odswiezony. Fixtures i przyklady
pozostaja silnie zanonimizowanym, syntetycznym CRM.

Baseline i conformance delta 8L.7 (2026-08-21): neutralne route branch slice,
TypeScript symbol slice i screen reachability sa dostepne przez shared/operator
API oraz Tool Workbench, ale sesja Copilota UI Explorera dopuszcza tylko
generyczne GitLab search/read. Ich model-facing schema nadal wymaga
`branchRef`, `projectName`, `pathPrefixes` i opcjonalnych application names,
co jest zastanym driftem i zmusza model do przenoszenia technicznego scope'u z
artefaktu.

Delta dodaje osobny, neutralny zestaw MCP w `agenttools.gitlab.frontend` nad
route branch i TypeScript symbol slice. Kazdy nowy tool przyjmuje wylacznie stabilny
`sliceRef` oraz krotki `reason`; group, project, ref, path prefixes, oczekiwana
source revision, wybrany screen oraz rejestr dozwolonych component/dependency
slice refs pochodza z hidden session context. UI Explorer wlacza te tools w
allowliscie i default-deny scope policy oraz preferuje je przed generycznym
search/read. Generyczny fallback zostaje dostepny tylko dla konkretnego braku,
ktory nie ma jeszcze bezpiecznego slice ref. Publiczne API UI Explorera,
result/report, job state, persistence i frontend pozostaja bez zmian. Tool
results sa mapowane na user-visible GitLab evidence, aby nowe source paths
mogly byc legalnymi referencjami finalnego raportu. Wszystkie nowe testy i
przyklady sa silnie zanonimizowanym, syntetycznym CRM. Pelny Screen
Reachability nie jest wystawiony przez MCP: jego outline i source slices sa
juz initial artifacts, wiec ponowne wywolanie duplikowaloby kontekst.

Checkpoint 8L.7 (2026-08-21): `agenttools.gitlab.frontend.mcp` wystawia
`gitlab_read_frontend_route_branch_slice`,
`gitlab_read_frontend_typescript_symbol_slice` nad istniejacymi neutralnymi
serwisami `integrations.gitlab.frontend`. Model-facing schema kazdego toola
zawiera dokladnie `sliceRef` i `reason`. Group, project, ref, path prefixes,
immutable source revision, wybrany screen oraz dozwolone targety TypeScript sa
session-bound hidden contextem; wymyslona albo pochodzaca z innej sesji
referencja jest odrzucana przed wywolaniem GitLaba.

Artefakty v5 publikuja bezpieczne referencje wybranego ekranu, komponentow BFS
i zaleznosci. Pelny graf powstaje raz przed AI i nie jest ponownie wystawiony
jako MCP result. UI Explorer preferuje dwa waskie deterministyczne tools; generyczne
GitLab search/read pozostaja fallbackiem tylko dla materialnej luki bez
gotowego `sliceRef`. Wyniki nowych tools trafiaja do user-visible GitLab code
evidence, wiec dociagniete pliki moga ugruntowac `sourceReferences` raportu.
Publiczne API, job/result/report, persistence i frontend nie zmienily
kontraktu. Testy kontraktu, hidden scope, rejestracji Spring AI, policy,
preparation i evidence uzywaja wylacznie silnie zanonimizowanego,
syntetycznego CRM. Celowane testy nowych tools, Spring AI registration,
policy, providera, preparation i evidence — PASS; `PackageDependencyGuardTest`
— PASS; pelny `mvn -q test` — 1283 testow, 0 bledow, 0 pominietych — PASS.

Checkpoint 8L.8 (2026-08-22): feature-owned renderer initial promptu osadza
niezmienione siedem logical artifacts v5 dokladnie raz i prezentuje je w
stabilnej kolejnosci: request/aktywne sekcje, wybrany ekran i rewizja,
effective route/component BFS/dependency map, source slices, coverage/research
queue, functional writing contract oraz response contract. Usunieto jedynie
powtarzane techniczne opakowanie `declaredTrust`/`mimeType`/`characterCount` z
tresci promptu; metadane i rozmiary artefaktow nadal sa dostepne w evidence
kroku `AI_PREPARATION`, a trust classification pozostaje w samych artefaktach.
Publiczne API, artifacts, skills, tools/policy, hidden scope, result/report,
job, persistence i frontend nie zmienily kontraktu.

Silnie zanonimizowana macierz syntetycznego CRM obejmuje prosty ekran, gleboki
routowany kontener z trzema poziomami komponentow, dynamiczny formularz z
reczna korekta/walidacja/runtime schema oraz widok laczacy guard/role,
REST, WebSocket i NgRx. Baseline `prompt/artifacts` wynosi odpowiednio:
`14 077/8 910`, `16 058/10 891`, `16 908/11 741` i
`15 905/10 738` znakow. Test wymaga jednokrotnego osadzenia kazdego artefaktu
i unikalnego faktu source, zachowuje maksymalne progi per przypadek oraz
potwierdza, ze recoverable `researchGap` nie staje sie automatycznie
`visibilityLimit`. Testy celowane preparation — PASS; caly pion UI Explorer,
route/symbol/reachability i `PackageDependencyGuardTest` — PASS; Angular
`56` plikow/`430` testow — PASS; produkcyjny build Angulara — PASS; pelny
`mvn -q test` — `1285` testow, 0 bledow i 0 pominietych — PASS.

Utwardzenie checkpointu 8L.6a po kolejnym tescie Workbencha (2026-08-21):
TypeScript slice zachowuje identyfikatory zakonczone `$`, rozpoznaje wszystkie
lokalne aliasy kontekstu `@for` oraz constructor parameter properties uzywane
przez template. Brakujacy lokalnie symbol komponentu dziedziczacego nie jest
oznaczany jako zgubiony: staje sie jawna referencja `INHERITED_MEMBER`, a graf
iteracyjnie pobiera i slicuje odpowiedni `INHERITED_TYPE`, rowniez przez
kolejne klasy bazowe. Nierozwiazana klasa bazowa nadal pozostawia graf
`PARTIAL`, zamiast pozornie kompletnego wyniku.

Rozwiazywanie aliasow Nx obsluguje deep import zawierajacy `/src/`, nawet gdy
kanoniczny target wildcarda konczy sie `/src/index.ts`; dzieki temu generowane
serwisy Swagger/OpenAPI moga zostac powiazane z plikiem i konkretna metoda.
Model/DTO z biblioteki Swaggera ani bezposrednio wywolana funkcja o nazwie
konczacej sie `Client` nie sa klasyfikowane jako backend client. Generyczne
prymitywy NgRx pozostaja techniczne, a nie funkcjonalne. `PENDING` jest wylacznie
stanem wewnetrznym budowy i nie wychodzi w odpowiedzi operatorskiej; nieslicowany
model otrzymuje `REFERENCE_ONLY`. Readable outline rozroznia prawdziwa statyczna
prezentacje od komponentu bez lokalnych entry points, ktory deleguje zachowanie
do klasy bazowej. Produkcyjny prompt, skille i MCP nadal nie zostaly zmienione.
Silnie zanonimizowane regresje CRM oraz operatorskie API — PASS; pelny
`mvn -q test` — 1279 testow, 0 bledow, 0 pominietych — PASS.

#### 8M. Trwaly cache katalogu widokow

Baseline i conformance delta 8M (2026-08-22): jest to zmiana L1 w publicznym
query API katalogu widokow oraz jego jedynym konsumencie we frontendzie. Flow
Explorer utrwala kosztowne endpoint inventory w local workspace, kluczuje je
pelny zakresem system/ref/repository/filtry, domyslnie zwraca cache hit, a
jawne `refresh=true` usuwa wpis i ponownie wykonuje discovery. UI Explorer
dotychczas wykonywal pelny route graph discovery przy kazdym wejsciu, zmianie
aplikacji i ponownym zaladowaniu tego samego refa.

Delta reuse'uje ten sam wzorzec bez zmiany odpowiedzi katalogu ani kontraktu
joba: feature-owned cache jest kluczowany `systemId`, znormalizowanym refem,
GitLab group/project, repository/search scope oraz limitami traversal. Zwykle
zaladowanie uzywa cache, natomiast Enter w polu ref i przycisk `Load views`
wysylaja jawne `refresh=true`, usuwaja tylko dokladnie dopasowany wpis i
ponownie wykonuja discovery. Nieudane discovery nie zapisuje ani nie nadpisuje
poprawnego wpisu. Konsumenci: `UiExplorerScreenCatalogController`,
`UiExplorerApiService`, `UiExplorerFacade` i konfigurator ekranu. Wszystkie
nowe testy i przyklady pozostaja silnie zanonimizowanym, syntetycznym CRM.

- [x] 8M.1: Dodac trwaly feature-owned cache katalogu widokow wzorowany na
  `FlowExplorerEndpointInventoryCache`, z pelnym cache key, bezpiecznym
  odczytem/zapisem, precyzyjna invalidacja i testem odtworzenia po restarcie.
- [x] 8M.2: Rozszerzyc `/api/ui-explorer/screens` o opcjonalne
  `refresh=false`, uzyc cache hit przed route discovery oraz wymusic ponowne
  discovery po `refresh=true`; testy maja potwierdzic hit, rozne refy/scope,
  refresh i brak zatrucia cache po bledzie.
- [x] 8M.3: Przekazac semantyke do Angulara: automatyczne ladowanie korzysta z
  cache, a Enter i `Load views` jawnie odswiezaja; zweryfikowac kontrakt HTTP,
  fasade, testy Angulara, produkcyjny build i adekwatny pion backendu.

Checkpoint 8M (2026-08-22): UI Explorer utrwala publiczny screen catalog w
`tdw-data/ui-explorer/screen-catalog-cache`. Cache key obejmuje identyfikator i
etykiete systemu, znormalizowany ref, GitLab group/project, repository,
search mode, path prefixes oraz wszystkie limity route graph traversal.
Automatyczne ladowanie po wejsciu lub wyborze frontendu wysyla request bez
`refresh`, wiec kolejny odczyt tego samego zakresu nie uruchamia GitLaba.
Enter w polu ref oraz `Load views` wysylaja `refresh=true`, usuwaja dokladnie
jeden wpis i ponownie wykonuja discovery. Cache przezywa restart aplikacji,
nie zapisuje nieudanego discovery i jest izolowany pomiedzy refami oraz
repository scopes. Odpowiedz katalogu, source revision i kontrakt joba nie
zostaly zmienione.

Silnie zanonimizowane testy syntetycznego CRM potwierdzaja cache hit,
odtworzenie po restarcie, precyzyjna invalidacje, rozne refy i scopes, blad
discovery, query API oraz semantyke fasady i Enter. Celowane testy backendu —
PASS; Angular `56` plikow/`432` testy — PASS; produkcyjny build Angulara —
PASS; `mvn -q -Pbackend-dev clean package` — `1291` testow, 0 bledow i 0
pominietych — PASS. Produkcyjny bundle zostal odswiezony.

### 9. Dokumentacja kanoniczna po wdrozeniu

- [ ] Dodac `ui-explorer-runtime-flow.md` dopiero po potwierdzeniu wynikowego
  kontraktu i zachowania.
- [ ] Zaktualizowac product direction, system overview, key decisions,
  package dependencies, continuation guide i katalog dokumentacji zgodnie z
  faktycznie wdrozonym stanem.
- [ ] Usunac z dokumentow obietnice, ktore nie weszly do MVP, albo przeniesc je
  do jawnego backlogu z nowa bramka akceptacji.
- [ ] Po zamknieciu planu przeniesc trwale decyzje do `architecture/` i nie
  traktowac planu jako kanonicznej dokumentacji runtime.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

## Bramka akceptacji

Przed implementacja punktu 1 uzytkownik powinien zatwierdzic co najmniej:

- jednostke analizy „widok w scenariuszu”,
- jeden cel dokumentacji funkcjonalnej i osiem sekcji,
- statyczny, single-repository zakres MVP,
- brak follow-up chat i runtime browser w MVP,
- publiczne endpointy i request shape,
- breaking `systemSubtype` i brak warstwy kompatybilnosci dla zmienianych
  kontraktow,
- eligibility `internal-service/frontend` przez systemowy code-search scope i
  primary repository,
- klasyfikacje L2 oraz nowa neutralna GitLab frontend discovery capability,
- silnie zanonimizowane, CRM-only testy i przyklady w kazdym kroku,
- podzial na dziewiec osobno akceptowanych inkrementow.

Zmiana tych decyzji aktualizuje najpierw source need albo ten plan; nie jest
implementowana przez domyslne rozszerzenie zakresu.
