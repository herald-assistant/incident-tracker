# Ujednolicenie granic pakietow integracji

Status: in-progress

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

W `integrations.<system>` porty, publiczne modele, wyjatki, properties,
adaptery i serwisy leza obecnie na tym samym poziomie. Konsumenci czesto
wstrzykuja klase implementacyjna, a sama nazwa pakietu nie pokazuje, ktora
czesc jest stabilnym kontraktem. Utrudnia to podmiane transportu lub storage,
przeglad skutkow zmiany oraz egzekwowanie kierunku zaleznosci. Potrzebujemy
jednej formy pakietow, ktora zachowa obecne zachowanie i pozwoli rozwijac
integracje bez zaleznosci od feature'ow.

## Proponowane rozwiazanie

Dla kazdego systemu zewnetrznego stosujemy ponizszy schemat. Bezposrednio w
`integrations.<system>` znajduja sie wylacznie publiczne interfejsy portow;
liczba portow wynika z roznych capability, nie z liczby adapterow.

```text
integrations/<system>/
  <Capability>Port.java           # publiczny interfejs capability
  contract/                       # publiczne request/result, typed errors
  adapter/rest|jdbc|local/        # transport i storage konkretnej implementacji
  service/<capability>/           # neutralna logika zlozona, implementacje portow
  config/                         # properties, klient HTTP, Spring wiring
  internal/<capability>/          # parsery, mapery, cache, modele lokalne
```

Tworzymy tylko potrzebne podpakiety. W rozbudowanych integracjach `contract`,
`service` i `internal` moga miec podzial tematyczny (np. GitLab `source`,
`frontend`, `usecase`). Publiczny port uzywa typow z `contract`, JDK i
stabilnych neutralnych kontraktow; nie ujawnia `RestClient`, JDBC, konfiguracji
Spring ani transportowego payloadu. Wyjatki rozpoznawane przez konsumentow
naleza do `contract.error`; bledy wylacznie transportowe zostaja przy adapterze.

Zwykli konsumenci w `api`, `agenttools`, `features` i `frontendcatalog` importuja
porty oraz `contract`, a nie `adapter`, `service`, `config` lub `internal`.
Kompozycja Spring i operatorska prezentacja konfiguracji moga potrzebowac
waszych, jawnie udokumentowanych wyjatkow od reguly importow. Kazdy taki
wyjatek ma wskazany typ i konsumenta w tescie architektonicznym, bez ogolnej
zgody na import implementacji. Klasy implementacyjne sa beanami; konstruktory
konsumentow przyjmuja porty. Porty wymagaja rzeczywistej implementacji uzywanej
operacji i testow kontraktowych, zamiast domyslnych `null`, pustych wynikow lub
`UnsupportedOperationException` ukrywajacych brak capability.

Wykorzystujemy istniejace porty, `PackageDependencyGuardTest` i wspolna
fabryke klientow REST. Nowy `IntegrationPackageBoundaryTest` stopniowo obejmuje
systemy po migracji i blokuje regresje lokalizacji typow oraz importow.

`integrations.http` jest wspolna infrastruktura, a nie integracja jednego
systemu. Przenosimy ja do `integrations.support.http` w osobnym kroku. W tym
podpakiecie nie obowiazuje regula publicznych portow systemowych.

Alternatywy odrzucone: jeden szeroki port na system utrudnilby niezalezne
implementacje i testy capability; samo przeniesienie klas bez ograniczenia
importow pozostawiloby zaleznosci od implementacji.

## Baseline i conformance delta

Stan na przygotowanie planu (pliki Java bezposrednio w pakiecie / lacznie z
podpakietami):

| Integracja | Root / lacznie | Obecna granica i glowni konsumenci |
| --- | ---: | --- |
| Confluence | 5 / 5 | `ConfluencePagePort`; API, Jira, Workspace Settings |
| Dynatrace | 6 / 6 | `DynatraceIncidentPort`; incident evidence, Workspace Settings |
| Jira | 19 / 19 | trzy porty: issue, search, status history; API, Change Verification, dwa feature'y Delivery |
| Elasticsearch | 28 / 28 | `ElasticLogPort` oraz konkretne search/import/availability services; API, MCP, Incident Analysis |
| Database | 17 / 17 | brak publicznego portu; `DatabaseToolService` w API i MCP, scope w Incident Analysis |
| GitHub | 0 / 16 | kod w `github.auth`; API auth i token provider uzywaja konkretnych klientow |
| Operational Context | 55 / 55 | `OperationalContextPort` dla odczytu, konkretne maintenance/validation; API, tools i wiele feature'ow |
| GitLab | 47 / 209 | `GitLabRepositoryPort` i `GitLabExactRepositoryPort`, wiele konkretnych service; API, tools, katalog frontendow i wiele feature'ow |
| Wspolne HTTP | 2 / 2 | `integrations.http`; konfiguracja klientow REST, nie port systemowy |

Conformance delta: lokalizacja klas i compile-time wiring zmieniaja sie,
semantyka capability, publiczne HTTP/tool DTO, payload JSON, konfiguracja
properties, nazwy beanow, import/export, job state i runtime flow pozostaja
takie same. Przy kazdym systemie przed edycja aktualizujemy liste konsumentow,
publicznych typow, testow i ewentualnych refleksyjnych/FQCN referencji.
Nie zmieniamy nazwy pola JSON lub property przez samo przeniesienie klasy.

## Zakres

- Osobna migracja kazdego systemu w kolejnosci z checklisty; nastepny system
  zaczyna sie dopiero po zweryfikowaniu poprzedniego.
- Porty dla capability rzeczywiscie uzywanych poza dana integracja; modele
  publiczne w `contract`, techniczne implementacje w podpakietach.
- Aktualizacja importow produkcyjnych i testowych, Spring wiring, lokalnych
  `AGENTS.md`, docelowej architektury oraz testu granic pakietow.
- GitLab i Operational Context dopuszczaja kilka inkrementow wewnatrz jednego
  systemu; po kazdym inkremencie testy przechodza, a do innego systemu nie
  przechodzimy przed zamknieciem wszystkich jego inkrementow.

## Non-goals

- Zmiana dostawcow, endpointow zewnetrznych, strategii autoryzacji, konfiguracji
  TLS lub danych katalogu Operational Context.
- Zmiana publicznych request/result, tool schemas, promptow, skilli, evidence,
  reportow, import/export lub frontendu.
- Przenoszenie feature-owned orchestration do integracji i tworzenie
  generycznego portu `IntegrationPort`.
- Pisanie testow odtwarzajacych tylko nowa strukture plikow; test
  architektoniczny sprawdza realna granice importow, a testy kontraktowe
  zachowanie portu.

## Ograniczenia i ryzyka

- Jest to zmiana grafu pakietow co najmniej L1 wedlug playbooka. Zatwierdzenie
  tego planu nie jest zgoda na wszystkie systemy; kazdy krok wymaga osobnego
  zatwierdzenia i dowodu weryfikacji wedlug `docs/AGENTS.md`.
- Zachowujemy zakaz `integrations -> analysis/agenttools/aiplatform/api/features`.
  Port nie moze importowac feature'a ani tool runtime.
- Przeniesienie typu moze dotknac package-private dostepu, refleksji,
  serializacji nazw klas, Spring binding, mockow i test creators. Najpierw
  ustalamy konsumentow i baseline danego systemu, potem przenosimy typy.
- `GitLabRepositoryPort` i `OperationalContextPort` maja domyslne metody z
  pustym wynikiem, `null` albo `UnsupportedOperationException`; przy ich
  podziale nie wolno przypadkiem zmienic semantyki wywolan. Nowe waskie porty
  otrzymuja jawne wymagane operacje i testy kontraktowe.
- Settings UI czyta properties integracji. Wyjatki od importu `config` musza
  byc policzone i ograniczone do konkretnego miejsca albo zastapione neutralnym
  odczytem ustawien; nie robimy szerokiego allowlist dla calego `api`.
- Nowe i modyfikowane testy oraz przyklady musza uzywac w pelni fikcyjnej
  domeny CRM zgodnie z root `AGENTS.md`.

## Kryteria akceptacji

1. W kazdym zakonczonym `integrations.<system>` bezposrednie pliki Java
   deklaruja tylko publiczne interfejsy `*Port`; DTO, wyjatki, properties i
   implementacje sa w podpakietach.
2. Runtime konsumenci danego systemu zaleza od portow i publicznego
   `contract`; kazdy pozostaly import ma nazwany wyjatek i uzasadnienie.
3. Kontrakt HTTP/tool/JSON, zachowanie portu i Spring wiring sa zgodne z
   baseline; nie ma nowego cyklu ani importu w gore grafu zaleznosci.
4. `PackageDependencyGuardTest` pilnuje kierunku zaleznosci warstw, a nowy
   `IntegrationPackageBoundaryTest` blokuje regresje reguly root-only i
   importow dla juz zmigrowanych systemow.
5. Po kazdym kroku przechodza testy adaptera, konsumentow i `mvn -q clean test`
   (przeniesienie klas zmienia package structure); `git diff --check` jest
   czysty. Testy frontendu sa potrzebne tylko po jawnej zmianie kontraktu UI,
   ktora wymaga aktualizacji planu przed implementacja.

## Protokol kazdego kroku

Przed przeniesieniem: lista klas i importow poza integracja, portow,
kontraktow request/result/error, beanow i testow; wynik testow baseline;
potwierdzenie braku niezapisanych zmian w dotykanych plikach. Po
przeniesieniu: przeglad diffu (w tym ewentualnych realnych danych w testach),
testy celowane z kroku, test granic pakietow, `mvn -q clean test`,
`git diff --check` oraz krotki zapis wyniku i pozostalego driftu przy kroku.
Nie uruchamiamy frontendu dla czysto backendowej zmiany pakietow.

## Kroki

- [x] Krok 1 — Confluence: pozostawic `ConfluencePagePort` w root, przeniesc
  publiczny model do `contract`, adapter do `adapter.rest`, properties i
  fabryke klienta do `config`; przelaczyc API, Jire i Workspace Settings.
  Dodac ten system do `IntegrationPackageBoundaryTest`. Dowod: adapter, API,
  Jira, settings, oba testy architektoniczne i protokol kroku; root ma jeden
  port.
  Wynik: root zawiera tylko `ConfluencePagePort`; publiczny model jest w
  `contract`, adapter w `adapter.rest`, konfiguracja w `config`. Jedyny
  wyjatek importu konfiguracji ma `WorkspaceSettingsService`. Baseline testow
  celowanych, testy celowane po `clean`, pelne `mvn -q clean test` i ponowiony
  test adaptera oraz granicy pakietu przeszly. Brak pozostalego driftu
  Confluence.
- [x] Krok 2 — Dynatrace: pozostawic `DynatraceIncidentPort` w root, przeniesc
  query/evidence do `contract`, REST do `adapter.rest`, properties/fabryke do
  `config`; przelaczyc incident evidence i settings. Dowod: test adaptera,
  `DynatraceEvidenceProviderTest`, guard i protokol; root ma jeden port.
  Wynik: root zawiera tylko `DynatraceIncidentPort`; query i evidence sa w
  `contract`, adapter w `adapter.rest`, konfiguracja w `config`. Port udostepnia
  `isConfigured()`, dzieki czemu provider nie importuje properties; jedyny
  zewnetrzny odczyt konfiguracji ma Workspace Settings. Baseline, testy
  celowane po `clean`, pelne `mvn -q clean test` i `git diff --check` przeszly.
  Brak pozostalego driftu Dynatrace.
- [x] Krok 3 — Jira: zachowac trzy oddzielne porty w root, przeniesc publiczne
  modele do `contract`, oba adaptery do `adapter.rest`, properties/fabryke do
  `config`; przelaczyc API, Change Verification, Delivery Complexity i Delivery
  Scope. Dowod: oba testy adapterow, testy source discovery konsumentow,
  guard i protokol; root zawiera tylko trzy porty.
  Wynik: root zawiera trzy porty, 12 publicznych modeli jest w `contract`,
  dwa adaptery sa w `adapter.rest`, a properties i fabryka klienta w `config`.
  API, Change Verification, Delivery Complexity, Delivery Scope i Workspace
  Settings korzystaja z nowych lokalizacji; konfiguracje poza integracja
  importuje tylko Workspace Settings. Baseline, testy celowane po `clean`,
  pelne `mvn -q clean test` i `git diff --check` przeszly. Brak pozostalego
  driftu Jiry.
- [x] Krok 4 — Elasticsearch: sklasyfikowac obecny `ElasticLogPort` i publiczne
  search/import/availability capability; dodac waskie porty dla operacji
  wywolywanych poza integracja, przeniesc modele do `contract`, REST/CSV do
  `adapter`, serwisy do `service`, konfiguracje do `config`. Przelaczyc API,
  MCP i Incident Analysis. Dowod: testy adaptera, search, CSV, availability,
  konsumentow, guard i protokol; root zawiera tylko porty.
  Wynik: root zawiera cztery porty dla logow, search, importu CSV i
  dostepnosci. Publiczne modele i wyjatki sa w `contract`, REST i CSV w
  `adapter`, search i availability w `service`, kryteria techniczne w
  `internal`, konfiguracja w `config`. API, MCP i Incident Analysis zalezy od
  portow; tylko Workspace Settings aktualizuje properties. Baseline i testy
  celowane po zmianie przeszly. `mvn -q clean test` zakonczyl sie powodzeniem:
  1731 testow, 0 failures, 0 errors, 1 skipped. `git diff --check` jest czysty.
  Brak pozostalego driftu Elasticsearch.
- [x] Krok 5 — Database: zdefiniowac port(y) dla operator-facing readonly
  diagnostics uzywanych przez API i MCP; przeniesc publiczne typed
  request/result/scope do `contract`, JDBC/routing do `adapter.jdbc`, SQL
  guard/masking/limiting do `service` lub `internal`, properties do `config`.
  Dowod: `DatabaseToolServiceTest`, metadata/router/guard tests, testy API/MCP,
  guard i protokol; root zawiera tylko porty i nie pojawia sie globalny
  `spring.datasource`.
  Wynik: root zawiera `DatabaseDiagnosticPort` i `DatabaseSqlPolicyPort`.
  Publiczne typed request/result/scope/operator contracts sa w `contract`,
  routing, metadata i wykonanie zapytan JDBC w `adapter.jdbc`, diagnostyka,
  SQL guard, masking i limiting w `service`, techniczne modele metadanych w
  `internal.metadata`, a properties w `config`. API/MCP korzystaja z portu
  diagnostyki, Incident Analysis odczytuje flage raw SQL przez port policy.
  Baseline oraz testy celowane po zmianie przeszly. `mvn -q clean test`
  zakonczyl sie powodzeniem: 1733 testy, 0 failures, 0 errors, 1 skipped.
  `git diff --check` jest czysty; brak globalnego `spring.datasource`.
  Brak pozostalego driftu Database.
- [ ] Krok 6 — GitHub: utworzyc root `github` z waskimi portami OAuth, profile
  i token refresh zgodnie z faktycznymi konsumentami; rozdzielic publiczne
  modele/bledy do `contract`, klientow REST do `adapter.rest`, lokalny stan
  auth do `adapter.local`, properties do `config`, koordynacje do `service`.
  Przelaczyc `api.githubauth` i token provider. Dowod: testy OAuth/profile/
  refresh, `GitHubAuthServiceTest`, guard i protokol; root `github` zawiera
  tylko porty.
  Kolejnosc zmieniona na prosbe uzytkownika: GitHub pozostaje odlozony;
  Operational Context wykonujemy przed nim.
- [x] Krok 7a — Operational Context read: zachowac waski port odczytu,
  przeniesc publiczny katalog, snapshot i query do `contract`, adapter/local
  store do `adapter.local`, query/search/ownership/read models do `service`
  lub `internal`; przelaczyc API, tools i feature'y odczytujace katalog.
  Dowod: testy adaptera/store/query/search, API/MCP, guard i protokol; odczyt
  katalogu pozostaje z jednego snapshotu.
  Wynik: `OperationalContextPort` nadal udostepnia captured snapshot i
  dokumenty z jednego odczytu. Osobne porty udostepniaja search, ownership,
  relation index, code-search read model, read-model validation, repository
  paths i ustawienia. Publiczne modele oraz stateless query sa w `contract`,
  implementacje neutralnych odczytow w `service`, a lokalny adapter/store w
  `adapter.local`. API, MCP, katalog frontendow i feature'y importuja porty
  oraz kontrakty. Testy bazowe i celowane po zmianie przeszly; pelne
  `mvn -q clean test`: 1735 testow, 0 failures, 0 errors, 1 skipped.
- [x] Krok 7b — Operational Context maintenance/validation: wystawic waskie
  porty dla operacji wywolywanych poza integracja, przeniesc publiczne
  command/preview/result/error do `contract`, YAML/atomic storage do
  `adapter.local`, walidacje i maintenance do `service`; przelaczyc API i
  Operational Context Assistance. Dowod: testy batch/recovery, walidacji,
  asysty i guard oraz protokol; root zawiera tylko porty i zachowuje warunkowy
  batch write.
  Wynik: root Operational Context zawiera 10 publicznych portow. Maintenance
  i walidacja sa za osobnymi portami, publiczne command/preview/result/error
  oraz validation report sa w `contract`, a YAML, atomic mover i lokalny
  storage w `adapter.local`; techniczne typy snapshotu sa w `internal`.
  API i Operational Context Assistance korzystaja z portow. Testy batch,
  recovery, walidacji, asysty, API/MCP i oba guardy przeszly; pelne
  `mvn -q clean test`: 1735 testow, 0 failures, 0 errors, 1 skipped.
  `git diff --check` jest czysty. Brak pozostalego driftu Operational Context.
- [ ] Krok 8a — GitLab repository foundation: rozdzielic obecny szeroki port
  na waskie porty dla rzeczywistych konsumentow repository read, branch,
  endpoint inventory, search, tree, revision, verified reader i named exact
  read; przeniesc publiczne modele i bledy do `contract`, REST do
  `adapter.rest`, properties/fabryki do `config`, cache do `internal`, a
  zlozone operacje do `service`. Dowod: testy obu adapterow, tree/search,
  podstawowych API/MCP, guard i protokol; brak cichych defaultow dla
  wymaganych operacji.
- [ ] Krok 8b — GitLab source capabilities: przeniesc `source`, `openapi`,
  `instructions` i `usecase` do spojnych podpakietow `contract`/`service`/
  `internal`, wystawic root port dla kazdej capability wywolywanej z zewnatrz
  integracji i przelaczyc API, tools oraz feature'y. Dowod: targeted source,
  OpenAPI, instructions i use-case tests, konsumenci, guard i protokol;
  rezultaty i limity pozostaja bez zmian.
- [ ] Krok 8c — GitLab frontend: przeniesc publiczne kontrakty do
  `contract.frontend`, implementacje/parsing do `service.frontend` i
  `internal.frontend`, wystawic waskie root porty dla route, symbol i screen
  discovery, przelaczyc frontend catalog, API, tools, UI Explorer i UX
  Inspector. Dowod: testy frontend discovery/slices/reachability, konsumenci,
  guard i protokol; root GitLaba zawiera juz tylko porty.
- [ ] Krok 9 — Wspolne HTTP i finalna granica: przeniesc
  `integrations.http` do `integrations.support.http`, przelaczyc fabryki
  klientow i testy; rozszerzyc guard na wszystkie systemy i usunac tymczasowe
  allowlisty migracyjne. Zaktualizowac `docs/architecture/package-dependencies.md`,
  lokalne `AGENTS.md` i `docs/README.md`, jesli zmienia sie kolejnosc czytania.
  Dowod: `IntegrationRestClientBuilderFactoryTest`, guard, protokol i koncowy
  przeglad importow `integrations.*`.

Po kazdym wykonanym kroku oznaczamy tylko ten krok `[x]` z wynikiem testow.
Nastepny system wymaga osobnego zatwierdzenia. Gdy aktualny baseline pokaze,
ze port lub podpakiet wymaga innego zakresu, aktualizujemy ten plan przed
zatwierdzeniem dotknietego kroku.
