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
Change Verification i Delivery Effectiveness Assessment. Nie zalezy od ich
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
- deterministyczny context snapshot przed uruchomieniem AI,
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
- brak ograniczonego context snapshotu laczacego route, template, komponent,
  formularze, store i klientow API,
- wyspecjalizowane GitLab flow/context capability sa obecnie nastawione na
  kod Java i endpointy backendowe,
- brak kontraktu, skilli, policy, joba, historii i UI dla dokumentacji widoku,
- Operational Context celowo nie przechowuje zmiennego inwentarza tras,
  komponentow ani endpointow konkretnej rewizji.

Katalog ekranow i screen context pozostaja snapshotem wyprowadzonym z kodu.
Nie sa nowymi bytami katalogowymi Operational Context.

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
Verification, Config Drift Viewer, Delivery Effectiveness Assessment,
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
- [x] Zbudowac context snapshot, evidence manifest i coverage dla aktywnych
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

Checkpoint 8B.5 2026-08-16: `GitLabFrontendScreenGraphContextService` przyjmuje
wylacznie `screenId` z aktualnego route graph oraz opcjonalna oczekiwana
rewizje. Po wyborze rozwija effective route chain, importy konfiguracji
segmentow (`canActivate*`, `canDeactivate`, `canMatch`, `canLoad`, resolvers i
providers), komponent widoku, lokalne importy/re-exporty, template oraz style.
Kazdy odczyt przechodzi przez `GitLabFrontendTargetedSourceSession`; osobne
budzety graph i selected-screen context nie uzywaja `listRepositoryFiles` ani
fallbacku do inventory.

Shared `/api/gitlab/frontend/catalog` zwraca teraz route graph z typed nodes,
edges, effective chains i graph coverage, a `/screen-context` zwraca selected
screen graph context. Tool Workbench konsumuje te same breaking kontrakty i
pokazuje route files, targeted reads oraz graph/context limit zamiast liczby
plikow repozytorium. UI Explorer mapuje nowe semantyczne `screenId`, guards z
calego effective chain, route parameters, graph diagnostics i dwa niezalezne
stany limitow. Publiczny input UI Explorer nadal nie ujawnia group, project ani
path prefixes.

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

- [ ] Przygotowac zestaw co najmniej pieciu kontrolowanych fixture screens:
  prosty widok, lazy route z guardem, zlozony formularz, dynamiczny formularz
  runtime oraz cross-domain widok z NgRx/REST/WebSocket.
- [ ] Wykonac review raportow z analitykiem wedlug success criteria potrzeby,
  bez strojenia pod jedna aplikacje.
- [ ] Skalibrowac limity i domyslne section modes na podstawie usage,
  coverage i czasu runu.
- [ ] Potwierdzic brak sekretow, nieograniczonego traversal i instrukcji z
  niezaufanego kodu traktowanych jak prompt.
- [ ] Wykonac pelna macierz backend-frontend w kolejnosci wskazanej wyzej.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

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
