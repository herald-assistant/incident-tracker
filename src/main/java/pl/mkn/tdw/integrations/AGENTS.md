# AGENTS

## Zakres

Ten katalog jest docelowa warstwa reusable integracji z systemami zewnetrznymi.
Pakiety pod `integrations.*` sa czystymi capability adapterami, ktore moga byc
uzywane przez evidence providers, tools/MCP, shared/operator endpointy REST
albo przyszle feature'y.

Obecnie obejmuje m.in.:

- `dynatrace/`
- `elasticsearch/`
- `gitlab/`
- `operationalcontext/`
- `database/`
- `support/http/` (wspolna infrastruktura REST)

Operational context jest tutaj query-based capability katalogu operacyjnego.
Incident-specific matching i mapowanie na evidence pozostaja w
`features.incidentanalysis.evidence.provider.operationalcontext`.
Neutralne maintenance zapewnia read-only preview pojedynczej encji i calego
wybranego zestawu oraz warunkowy zapis. Batch sklada wszystkie mutacje na
jednym snapshocie, dopuszcza referencje do pozniejszego `CREATE` z tego samego
zestawu, waliduje wynikowy katalog i publikuje dotkniete YAML jako jedna
logiczna decyzje z recovery po przerwaniu. Referencja do pominietej propozycji
pozostaje bledem. W asyscie AI feature wybiera propozycje i pola; integracja
nie przejmuje promptu, joba ani decyzji operatora. Przy zapisie zestawu
sprawdza digest oraz wartosci `before` wybranych pol pod tym samym lockiem co
publikacja. Bledy strukturalne batcha zachowuja wskaznik
`/mutations/{index}/payload/...`.
W modelu operational context `system` jest kanonicznym bytem dla aplikacji lub
uslugi. Nazwy deploymentu, kontenera, aplikacji i serwisu sa sygnalami albo
metadata systemu; nie dodawaj osobnych kontraktow referencyjnych
dla komponentu uruchomieniowego.

Database jest tutaj readonly capability diagnostyki danych: routing DataSource
per environment, metadata, SQL guard, masking/limiting wynikow oraz typed
request/result/scope/operator contracts. MCP mapuje hidden `ToolContext` na ten
scope, ale `integrations.database` nie importuje MCP ani `agenttools`.

## Zasady

- Nie importuj tutaj `analysis.*`, `agenttools.*`, `features.*`, `api.*` ani
  `aiplatform.*`.
- Trzymaj lokalnie properties, porty, modele request/result i adaptery REST dla
  danej capability.
- W `confluence/` bezposrednio w pakiecie znajduje sie tylko
  `ConfluencePagePort`. Publiczny model strony jest w `contract`, adapter w
  `adapter.rest`, a properties i fabryka klienta w `config`. Konsumenci
  korzystaja z portu i kontraktu; jedynym wyjatkiem jest
  `api.workspacesettings.WorkspaceSettingsService`, ktory aktualizuje
  `ConfluenceProperties` podczas pracy aplikacji.
- W `dynatrace/` bezposrednio w pakiecie znajduje sie tylko
  `DynatraceIncidentPort`. Query i evidence sa w `contract`, adapter w
  `adapter.rest`, a properties i fabryka klienta w `config`. Provider evidence
  sprawdza dostepnosc przez port; tylko
  `api.workspacesettings.WorkspaceSettingsService` aktualizuje properties.
- W `jira/` bezposrednio w pakiecie znajduja sie tylko trzy porty: issue,
  search i status history. Publiczne modele sa w `contract`, dwa adaptery w
  `adapter.rest`, a properties i fabryka klienta w `config`. Konsumenci
  korzystaja z portow i kontraktu; `api.workspacesettings.WorkspaceSettingsService`
  aktualizuje properties jako waski wyjatek.
- W `elasticsearch/` bezposrednio w pakiecie znajduja sie tylko porty logow,
  search, importu CSV i dostepnosci polaczenia. Publiczne modele i rozpoznawane
  przez konsumentow wyjatki sa w `contract`, klient REST w `adapter.rest`,
  parser CSV w `adapter.csv`, serwisy search i availability w `service`,
  techniczne kryteria w `internal`, a properties i fabryka klienta w `config`.
  Konsumenci korzystaja z portow i kontraktu; tylko
  `api.workspacesettings.WorkspaceSettingsService` aktualizuje properties.
- W `database/` bezposrednio w pakiecie znajduja sie tylko
  `DatabaseDiagnosticPort` i `DatabaseSqlPolicyPort`. Typowane requesty,
  wyniki, scope i operatory sa w `contract`; routing, metadata oraz wykonanie
  zapytan JDBC w `adapter.jdbc`; diagnostyka, SQL guard, masking i limiting w
  `service`; techniczne modele metadanych w `internal.metadata`, properties
  w `config`. API i MCP korzystaja z portu diagnostyki,
  a feature incidentowy odczytuje flage raw SQL przez port policy. Integracja
  pozostaje readonly i nie rejestruje globalnego `spring.datasource`.
- W `operationalcontext/` bezposrednio w pakiecie znajduja sie tylko porty
  odczytu katalogu, wyszukiwania, ownership, read models, walidacji,
  repository paths, ustawien i maintenance. Publiczne modele katalogu,
  snapshotu, query, mutation i bledow sa w `contract`; stateless query
  przechowywane w `contract` filtruje jeden captured snapshot. Neutralna
  logika wyszukiwania, ownership, read models, walidacji i maintenance jest
  w `service`, lokalny YAML/storage i atomic mover w `adapter.local`,
  techniczne typy snapshotu w `internal`, a properties w `config`.
  Konsumenci spoza integracji importuja tylko porty i `contract`.
- W `gitlab/` bezposrednio w pakiecie znajduja sie tylko porty. Publiczne
  modele sa w `contract`, adapter REST w `adapter.rest`, konfiguracja w
  `config`, a zlozone capability w `service`. Konsumenci importuja porty i
  kontrakty; Workspace Settings aktualizuje properties jako waski wyjatek.
- Stabilne endpointy FE/operatora trzymaj w `api.*`. Tutaj zostaw adapter,
  porty, modele request/result i service capability.
- Nietypowe zachowania HTTP izoluj lokalnie dla danej integracji.
- Nowe integracje REST, ktore maja podlegac wspolnej polityce weryfikacji TLS,
  buduj przez `integrations.support.http.IntegrationRestClientBuilderFactory`.
  Pakiet `support.http` jest wspolna infrastruktura, nie integracja systemowa,
  dlatego nie podlega regule portow w root. Fabryka uzywa
  `integrations.http.ignore-ssl-errors` bez zmiany globalnych ustawien JVM.
- Nie dodawaj tu `AnalysisEvidenceProvider`, klas `@Tool`, promptow, skilli ani
  heurystyk incidentowych.
- Dla Database capability nie wprowadzaj globalnego `spring.datasource`, nie
  zgaduj schematow domenowo w kodzie i trzymaj mapping application-to-schema w
  konfiguracji.
- Dla Operational Context nie rozbijaj relacji katalogowych na system i osobny
  byt runtime. Repozytoria, procesy, bounded contexts, integracje i code-search
  scopes powinny odnosic sie do `systems`.
- Jesli capability potrzebuje neutralnego kontraktu wspolnego z innym
  feature'em, preferuj maly typ w `shared` albo `common`, ale dopiero gdy realnie
  usuwa zaleznosc.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `integrations.*` nie zaczelo
  importowac warstw aplikacyjnych.
- `IntegrationPackageBoundaryTest` pilnuje struktury pakietow Confluence,
  Dynatrace, Jira, Elasticsearch, Database, Operational Context i GitLab,
  importow ich konsumentow oraz tego, by nowa integracja zostala objeta testem.
- Dla adapterow REST preferuj testy z `MockRestServiceServer`.
