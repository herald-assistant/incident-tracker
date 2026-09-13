# Operational Context Model, Tools And Usage

Ten dokument jest kanonicznym opisem `operational-context` w projekcie.
Opisuje aktualny model katalogu, sposob jego uzycia przez API, tools, AI
runtime i feature'y analityczne oraz granice, ktorych nie wolno odbudowywac.

## Cel

Operational context jest curated navigation layer dla analizy systemowej. Ma
uzupelniac to, czego nie da sie szybko i pewnie wywnioskowac z jednego repo:

- od ktorego systemu, procesu, bounded contextu albo zespolu zaczac,
- ktory code-search scope wskazuje repozytoria i prefixy do dalszego czytania,
- jak przetlumaczyc techniczny sygnal na jezyk analityka biznesowo-systemowego,
- jaki resolved ownership, handoff, partner albo ograniczenie widocznosci jest
  istotne,
- ktore pytania sa nadal otwarte i nie powinny byc zgadywane przez AI.

Operational context nie jest inventory kodu ani runtime. Szczegoly klas,
endpointow, kolejek, tabel, deploymentow, plikow i konfiguracji sa odkrywane
przez dedykowane tools oraz repozytoria zrodlowe.

## Zasady Graniczne

- `system` jest kanonicznym bytem katalogowym.
- Dane runtime, service names i deployment signals sa wlasciwosciami albo
  sygnalami systemu, nie osobnym bytem referencyjnym.
- Ownership jest faktem katalogowym tylko dla `system` i `bounded-context`.
- `bounded-context` ma pierwszenstwo przed `system`; system jest fallbackiem,
  gdy context nie jest znany albo problem jest system-wide.
- Repository, code-search scope, process, integration, handoff rule, glossary
  term i team nie definiuja ownera.
- Katalog wskazuje repozytoria do wspolnego czytania tylko przez
  `code-search-scopes.yml`; `system` nie ma bezposredniej referencji do repo.
  Katalog nie przechowuje szczegolowych elementow kodu, tras API, nazw kolejek
  ani tabel.
- Katalog moze opisac integracje jako relacje systemowe, ale nie przechowuje
  szczegolowych kontraktow transportu, payloadow ani implementacji klientow.
- `code-search-scopes.yml` wskazuje semantyczny scope repozytoriow:
  `repoId`, `projectName`/`projectPath`, `role`, `priority`, `reason`,
  `readFor`, `searchMode`, opcjonalne `pathPrefixes`, limitations i
  validation.
- Incident analysis jest pierwszym feature'em korzystajacym z katalogu, ale
  model i tools pozostaja neutralne.
- Feature-specific zasady uzycia katalogu mieszkaja w policy/guidance feature'a
  i runtime skillach, nie w neutralnych `opctx_*` tools.

## Ownership I Handoff

Docelowy kontrakt ownershipu:

```yaml
ownership:
  ownerTeamIds: []
  ownerLabel: ""
  ownershipStatus: explicit | unknown
  confidence: high | medium | low
  source: ""
  notes: []
```

Znaczenie:

- `ownerTeamIds` wskazuje znane zespoly z `teams.yml`.
- `ownerLabel` opisuje wlasciciela, gdy nie ma stabilnego team id, np.
  `wlasciciel systemu Salesforce`.
- `ownershipStatus=explicit` oznacza potwierdzony fakt katalogowy.
- `ownershipStatus=unknown` oznacza brak jawnego ownera; resolver moze wtedy
  zwrocic opisowa inferencje, ale nie zapisuje jej jako fakt katalogowy.

Resolver ownershipu jest jedynym miejscem, ktore przeklada katalog na wynik
handoffu. Priorytet:

1. owner dopasowanego `bounded-context`,
2. owner systemu powiazanego z bounded contextem,
3. owner dopasowanego `system`,
4. inferowany opis ownera z nazwy systemu albo bounded contextu.

Gdy problem jest na styku dwoch systemow albo bounded contextow, wynik ma
wskazac ownerow obu stron: `primaryOwners` i `partnerOwners`. Dla relacji
system-infrastruktura albo system-zewnetrzny druga strona moze byc ownerem
opisowym, jezeli katalog nie zna teamu.

Pytanie techniczne typu "kto jest wlascicielem endpointa?" nie oznacza
ownershipu endpointa. Lancuch rozstrzygania jest:

1. endpoint, klasa, metoda albo inny technical target,
2. repozytorium i code-search scope,
3. system oraz, jesli da sie ustalic, bounded context,
4. resolved ownership/handoff z resolvera.

Handoff rules pomagaja rozpoznac sytuacje, wymagane evidence i pierwsze
dzialanie. Nie zawieraja listy teamow do routingu.

## Zakres Pytan

Katalog wspiera przede wszystkim pytania:

- "od czego zaczac analize tego problemu?",
- "ktore repozytoria trzeba czytac razem?",
- "jaki proces albo bounded context tlumaczy ten objaw?",
- "kto jest resolved ownerem albo kogo wlaczyc do handoffu?",
- "jak opisac ten techniczny sygnal jezykiem biznesowym?",
- "ktore ograniczenia widocznosci trzeba pokazac uzytkownikowi?",
- "jak przygotowac dev stories, user stories albo scenariusze testowe po
  zrozumieniu systemu?".

Katalog nie odpowiada samodzielnie na pytania:

- "jaki endpoint jest obslugiwany przez dana klase?",
- "w jakim pliku jest konkretna implementacja?",
- "jaka tabela albo kolumna jest uzywana?",
- "jak wyglada deployment manifest?",
- "jaki jest runtime root cause?".

Na takie pytania katalog moze jedynie wskazac repo/system/proces, od ktorego
tool powinien zaczac dalsze czytanie.

## Pliki Katalogu I Storage

`src/main/resources/operational-context` jest immutable bundled seedem
pakowanym do JAR-a. Nie jest writable source of truth i UI nie zapisuje do
`src/main/resources`.

Runtime ma jeden model storage. Przy pierwszym uruchomieniu kompletny bundled
seed jest kopiowany do `${tdw.workspace.directory}/operational-context`
(domyslnie `tdw-data/operational-context`). Od tego momentu read API, tools,
feature'y i maintenance czytaja oraz zapisuja wylacznie lokalna kopie. Kolejne
uruchomienia nie nadpisuja jej nowszym seedem z aplikacji.

Konfiguracja:

```properties
analysis.operational-context.enabled=false
# analysis.operational-context.resource-root=operational-context
analysis.operational-context.storage-directory=${tdw.workspace.directory}/operational-context
# analysis.operational-context.max-items-per-type=2
# analysis.operational-context.max-glossary-terms=3
# analysis.operational-context.max-handoff-rules=2
```

MVP nie ma security/rollout gate, trybu read-only, rewizji, manifestow,
historii ani rollbacku Operational Context. Uzytkownik pracuje lokalnie na
swojej kopii danych. Mutacje przechodza przez walidacje kontraktu i spojnosci
katalogu, a pojedynczy zmieniany dokument jest podmieniany atomowo przez plik
tymczasowy w tym samym katalogu. Backup calego katalogu jest odpowiedzialnoscia
uzytkownika.

Pliki katalogu:

| Plik | Rola |
| --- | --- |
| `systems.yml` | kanoniczne systemy, jawne `systemType`/`systemSubtype`, aliasy, status, summary, references, ownership i open questions |
| `repo-map.yml` | mapa repozytoriow do GitLaba i relacji katalogowych |
| `code-search-scopes.yml` | semantyczne grupy repozytoriow do wspolnego przeszukania |
| `processes.yml` | procesy biznesowo-operacyjne, kroki, rezultaty i relacje |
| `bounded-contexts.yml` | bounded contexty, jezyk lokalny, zakres odpowiedzialnosci i granice |
| `integrations.yml` | integracje jako relacje systemowe: source, target, category, style i direction |
| `teams.yml` | identyfikatory, etykiety i opis zespolow uzywanych przez ownership |
| `glossary.yml` | strukturalny slownik pojec biznesowo-systemowych |
| `handoff-rules.yml` | strukturalne sytuacje handoffu, wymagane evidence i pierwsze akcje |

## Model

### System

`system` jest glownym targetem relacji. Powinien miec:

- `id`, `name`, `aliases`,
- `systemType`, `systemSubtype`, `lifecycleStatus`, `criticality`,
- `summary`,
- `references` do procesow, integracji, bounded contextow, zespolow i pojec,
- `ownership`, `relations`, `sourceCoverage`, `gaps` tam, gdzie sa potrzebne,
- opcjonalne `participants.externalOwner` dla odpowiedzialnosci zewnetrznej,
- opcjonalne `runtime.configurationDirectory` jako bezpieczna, wzgledna sciezke
  konfiguracji wykorzystywana przez Config Drift Viewer.

System nie powinien miec osobnego katalogu runtime names, deployment names,
container names, endpointow ani `references.repositories`. Nazwy serwisow,
deploymentow i aplikacji pozostaja sygnalami w `matchSignals`, a nie druga
prawda w `runtime`. Code discovery dla systemu zaczyna sie od code-search
scope'u targetujacego ten system.

`systemType` jest jedynym kanonicznym polem klasyfikacji; legacy `type` i
`kind` nie sa parsowane ani przyjmowane przez maintenance API. Kazdy
`systemType=internal-service` musi miec dokladnie jeden `systemSubtype` z
vocabulary `frontend`, `backend`, `worker`, `mixed`, `unknown`. Dla pozostalych
typow `systemSubtype` jest pomijany. `unknown` oznacza jawnie przejrzany brak
wystarczajacych dowodow i nie kwalifikuje systemu do feature'ow zaleznych od
subtype. Konkretnego subtype nie wolno inferowac z nazwy repozytorium,
`package.json`, frameworka ani ukladu plikow.

### Repository

Repository opisuje, czym jest repo w krajobrazie systemu i do jakich bytow
katalogowych sie odnosi. Powinno zawierac:

- GitLab identity (`projectName`, `projectPath`),
- jawny `repositoryType`, w tym `frontend` dla repozytorium bedacego glownym
  zrodlem UI,
- purpose/summary,
- status, criticality, aliases,
- references do systemow, procesow, integracji i bounded contextow,
- limitations i open questions,
- opcjonalne typowane karty `evidence` z `sourceRef`, `evidenceType` i `note`,
- opcjonalne `llmToolHints.answerWhenUserMentions` oraz `disambiguateFrom` jako
  wskazowki wyboru repozytorium dla AI.

Repository nie opisuje ukladu katalogow, plikow build/deployment ani sciezek
implementacji. Repository nie definiuje ownera; owner endpointu, klasy albo
repozytorium jest rozstrzygany przez system/bounded context znaleziony przez
code-search scope. `evidence` jest provenance i nie uruchamia pobrania zrodla,
a `llmToolHints` nie przyznaje dostepu ani nie tworzy ownershipu.

### Code Search Scope

Code search scope grupuje repozytoria, ktore trzeba czytac razem. To jest
semantyczny kontrakt wyboru repozytoriow oraz coarse search boundary, nie
instrukcja szukania po klasach.

Repozytorium w scope powinno miec:

- `repoId`,
- `role`,
- `priority`,
- `searchMode`: `whole-repository` albo `path-prefixes`,
- `pathPrefixes`, wymagane tylko dla `searchMode=path-prefixes`,
- `reason`,
- `readFor`,
- `projectName` albo `projectPath`, jezeli sa znane,
- optional limitations/validation.

`pathPrefixes` sa relatywnymi sciezkami GitLaba bez wiodacego `/`. Opisuja
moduly/prefixy katalogow, ktore naleza do semantycznego targetu w duzym
repozytorium. Nie sa lista klas, endpointow, plikow ani pakietow.
Przy wyznaczaniu project paths z katalogu repozytorium w podgrupie GitLab
nalezy do skonfigurowanej glownej grupy; podobnie zaczynajaca sie nazwa
obcej grupy nie jest dopuszczana.

Dozwolone role powinny opisywac relacje w analizie, np. `primary`, `support`,
`shared-library`, `migration-peer`, `external-adapter`. Nie uzywamy roli jako
substytutu dla szczegolow implementacji.

Frontend kwalifikujacy sie do UI Explorer jest rejestrowany bez heurystyk:
system ma `systemType=internal-service` i `systemSubtype=frontend`, dokladnie
jeden code-search scope targetujacy ten system, a scope dokladnie jedno
repozytorium z jawna rola `primary`, `repositoryType=frontend` i poprawnym
`searchMode`. Brak lub niejednoznacznosc ktoregokolwiek elementu jest findingiem
konfiguracji, a nie sygnalem do zgadywania repozytorium.

### Process

Process opisuje przebieg biznesowo-operacyjny:

- uczestnikow,
- kroki i rezultaty,
- warunki sukcesu, porazki i anulowania,
- powiazane systemy, integracje i bounded contexty,
- failure modes jezykiem procesu.

Process nie przechowuje per-step endpointow, klas, pakietow ani kolejek.

### Bounded Context

Bounded context opisuje odpowiedzialnosc domenowa i lokalny jezyk:

- summary oraz `localLanguageSummary`,
- `scope` z includes/excludes, business capabilities, core entities i key
  decisions,
- `semanticBoundary` z core/local concepts, canonical entities, commands,
  events, invariants oraz owns/does-not-own language,
- typowane provenance `evidence` i wskazowki eksploracji `llmToolHints`,
- relacje do systemow, procesow i integracji,
- ownership oraz limitations.

Znane pola scope, semantic boundary, evidence i AI hints sa jawnie walidowane,
indeksowane i projektowane do widoku operatora oraz `opctx_get_entity`.
Nowe nieznane klucze sa odrzucane przez maintenance API, a istniejace
nieznane rozszerzenia sa usuwane przy aktualizacji encji. Jawne pola
preserve-only i dynamiczne nazwy sygnalow pozostaja zachowane. `evidence`
nie uruchamia pobrania zrodla ani nie dowodzi root
cause, a `llmToolHints` nie nadaje dostepu, ownershipu ani prawa do pominiecia
visibility limits.

Bounded context moze wskazac pojecia domenowe, ale nie powinien przechowywac
list klas encji, Java-owych implementation hints ani list repozytoriow. Code
discovery dla bounded contextu zaczyna sie od code-search scope'u targetujacego
ten bounded context.

### Integration

Integration opisuje zaleznosc systemowa:

- source system i target systems,
- category, integration style, direction,
- criticality,
- role uczestnikow,
- references do procesow, bounded contextow i pojec,
- limitations.

Integration nie jest katalogiem HTTP paths, queue names, topics, payloadow ani
klientow technicznych. Integration nie definiuje ownera; handoff na granicy
integracji wynika z ownerow systemow albo bounded contextow po obu stronach.

### Team, Glossary, Handoff

Te pliki maja najwieksza wartosc, gdy tlumacza jezyk i sytuacje przekazania:

- `teams.yml` opisuje team id/label zwracane przez ownership systemu albo
  bounded contextu,
- `glossary.yml` tlumaczy pojecia i rozroznienia,
- `handoff-rules.yml` opisuje, kiedy i z jakim evidence przekazac temat.

Nie nalezy uzywac ich jako miejsca na techniczne instrukcje czytania kodu ani
na reczny routing teamow.

## Loader, Snapshot I Read Models

Katalog jest ladowany przez
`integrations.operationalcontext.OperationalContextAdapter`.

Adapter:

1. zapewnia istnienie lokalnej kopii, bootstrapujac ja z seeda tylko raz,
2. parsuje dziewiec strukturalnych dokumentow YAML przez wspolny codec,
3. mapuje encje do neutralnych DTO,
4. buduje open questions i validation findings,
5. udostepnia filtrowanie przez `OperationalContextQuery`.

Read API, tools i feature'y korzystaja z jednego immutable captured snapshotu
biezacej zawartosci. Logic source references zawieraja nazwe dokumentu i field
path, ale nie absolute local path. Wewnetrzny digest zawartosci sluzy tylko do
identyfikacji captured snapshotu; nie jest publiczna wersja, ETagiem ani
historia katalogu.

Aktywne read modele sa celowo waskie:

- entity detail,
- relations,
- code-search.

Usuniete sa projekcje oparte o techniczne inventory kodu i zasiegu skutkow.
Flow analysis, impact analysis albo technical handoff maja korzystac z
katalogowych relacji i code-search scope, a szczegoly dociagac przez GitLab,
DB, Elasticsearch albo inne tools.

## Shared/Operator API

Shared/operator API pod `/api/operational-context/*` jest fasada do czytania
aktualnego katalogu przez UI i narzedzia operatorskie. Istniejace read modele
zachowuja dotychczasowa semantyke odczytu.

Aktywne endpointy:

```http
GET /api/operational-context/summary
GET /api/operational-context/systems
GET /api/operational-context/repositories
GET /api/operational-context/code-search-scopes
GET /api/operational-context/processes
GET /api/operational-context/integrations
GET /api/operational-context/bounded-contexts
GET /api/operational-context/teams
GET /api/operational-context/glossary
GET /api/operational-context/handoff-rules
GET /api/operational-context/open-questions
GET /api/operational-context/validation
GET /api/operational-context/search?q=...
GET /api/operational-context/entities/{type}?id=...
GET /api/operational-context/entities/{type}/{id}
GET /api/operational-context/read-model/entities/{type}/{id}/relations
GET /api/operational-context/read-model/entities/{type}/{id}/code-search
GET /api/operational-context/read-model/entities/{type}/relations?id=...
GET /api/operational-context/read-model/entities/{type}/code-search?id=...
```

API nie wystawia endpointow dla implementation, flow ani blast-radius
projekcji.

Maintenance korzysta z osobnego kontraktu:

```http
GET    /api/operational-context/catalog/capabilities
GET    /api/operational-context/catalog/entities/{type}/{id}
POST   /api/operational-context/catalog/entities/{type}
PUT    /api/operational-context/catalog/entities/{type}/{id}
GET    /api/operational-context/catalog/entities/{type}/{id}/delete-impact
DELETE /api/operational-context/catalog/entities/{type}/{id}
```

Maintenance DTO zawiera kanoniczny editable payload i nie przyjmuje read
projection ani `rawSourcePreview`. Update jest complete PUT, ID po utworzeniu
jest immutable, a delete stosuje `RESTRICT` bez cascade. Field errors uzywaja
JSON Pointer. API nie wystawia `revision`, ETag ani `If-Match`; zwraca m.in.
`409` dla konfliktu encji lub referencji, `422` dla walidacji oraz `503` dla
niedostepnej lokalnej kopii.

Asysta AI korzysta z tej samej neutralnej walidacji katalogu. Batch preview
buduje w pamieci wynikowy katalog dla wybranych zmian i waliduje go bez
publikacji plikow. Warunkowy batch maintenance porownuje jeden digest
wejsciowy, brak duplikatow ID dla `CREATE` i wartosci wybranych pol `before`
dla `UPDATE`. Pozostale pola zachowuje. Sprawdzenie warunkow, walidacja calego
wynikowego katalogu i publikacja sa objete jednym lockiem. Dla wielu YAML
storage przygotowuje `.opctx-batch-journal` z kopiami dziewieciu aktywnych
dokumentow i kandydatami. Marker `prepared` uruchamia odtworzenie wszystkich
kopii po przerwaniu procesu; immutable snapshot zmienia sie dopiero po pelnym
commicie. Nie jest to absolutna gwarancja trwalosci przy utracie zasilania
Windows przed fizycznym zapisem katalogu na nosniku.

## Asysta AI Przy Utrzymaniu Katalogu

`features.operationalcontextassistance` jest wlascicielem trzech trybow
operatora: `CREATE_AREA` dla pustego lub niepelnego obszaru, `IMPROVE_ENTITY`
dla istniejacej encji oraz `RESOLVE_FINDING` dla wskazanego findingu Validation
albo Open Question. Feature ma wlasne async job API, collector wybranego
zrodla, prompt, polski runtime skill, parser draftu, preview i decyzje
operatora. Korzysta z neutralnych `aiplatform`, `integrations.gitlab` oraz
`integrations.operationalcontext`; nie dodaje mutation tool do `opctx_*`.

```http
POST /api/operational-context/assistance/jobs
GET  /api/operational-context/assistance/jobs/{jobId}
POST /api/operational-context/assistance/jobs/{jobId}/batch/preview
POST /api/operational-context/assistance/jobs/{jobId}/batch/decision
GET  /api/operational-context/assistance/source-options
GET  /api/operational-context/assistance/source-options/branches?project=...&search=...
```

`source-options` zwraca skonfigurowany base URL i glowna grupe GitLab oraz
unikalne projekty z aktualnego katalogu nalezace do tej grupy lub jej
podgrup. `projectPath` jest pelna sciezka pokazywana operatorowi, a `project`
sciezka wzgledna wobec glownej grupy wysylana w `gitLabSource.project` przy
wyborze z katalogu. Lista jest podpowiedzia z katalogu, nie odczytem
wszystkich projektow GitLaba. Gdy projektu nie ma na liscie, operator wkleja
pelny adres strony projektu w `gitLabSource.projectUrl`. Serwer odrzuca adres
o innym origin, poza skonfigurowanym base path lub glowna grupa oraz adres
niejednoznaczny, zanim powstanie job. Z poprawnego URL wylicza wzgledna
sciezke do GitLaba i kanoniczne `git.group`, `git.project`, `git.projectPath`
oraz `git.url` dla propozycji wpisu w `repo-map.yml`. Dla URL projektu
`CLP/PROCESSES/CLP_AGREEMENT_PROCESS` pod glowna grupa `CLP` odczyt GitLab
uzywa `PROCESSES/CLP_AGREEMENT_PROCESS`, a wpis katalogu zachowuje
`group: CLP/PROCESSES`, `project: CLP_AGREEMENT_PROCESS` i pelne
`projectPath: CLP/PROCESSES/CLP_AGREEMENT_PROCESS`.
Te pola trafiaja do `selectedSource.repositoryGit`; parser draftu odrzuca
propozycje `repository.git`, ktora nie odpowiada wybranemu zrodlu.

`source-options/branches` pobiera pierwsza strone galezi jednego wskazanego
projektu GitLab i obsluguje filtr serwerowy `search`. Przyjmuje dokladnie
jedno z `project` (sciezka wzgledna z katalogu) lub `projectUrl` (pelny URL
wklejony przez operatora). Obie formy przechodza ta sama walidacje
skonfigurowanego origin i glownej grupy co zrodlo asysty. Odpowiedz zawiera
`branches`, `truncated` i `warnings`; lista nie jest katalogiem projektow.
Frontend korzysta ze wspolnego selektora galezi Flow Explorer, automatycznie
odczytuje opcje po wyborze projektu i pozwala zawezic je filtrem. Reczne
wpisanie nazwy galezi pozostaje alternatywa; commit jest przypinany automatycznie,
a zmiana projektu usuwa
poprzedni ref przed pobraniem nowej listy.

Start przyjmuje opis operatora, tryb, wymagany dla update'u target, opcjonalny
jeden projekt GitLab z refem oraz preferencje AI. Target `RESOLVE_FINDING`
zawiera istniejacy fingerprint findingu albo ID otwartego pytania i powiazana
encje. Grupa GitLab jest konfigurowana po stronie aplikacji. Collector przypina
ref do commita i wstepnie czyta dokladne sciezki `README.md`, `pom.xml`,
`package.json`, `build.gradle`, `settings.gradle`; sprawdza rozmiar przed
odczytem, limituje rzeczywisty HTTP body do 16 KiB na plik i 64 KiB lacznie
oraz pomija niedostepna albo potencjalnie wrazliwa tresc. Nie wymaga
istniejacego code-search scope. Sesja AI moze pozniej doczytac istotne pliki
przez wspolny neutralny zestaw `gitlab_list_repository_branches`,
`gitlab_list_repository_tree`, `gitlab_list_repository_files`,
`gitlab_search_repository_files` i
`gitlab_read_repository_file`. Model podaje projekt wzgledny wobec
skonfigurowanej glownej grupy oraz galaz, a hidden scope pilnuje, by wybrany
projekt uzywal galezi operatora i jej przypietego commita. Powiazany projekt
z tej samej glownej grupy moze byc odczytany po ustaleniu galezi przez tool
i osobnym przypieciu jej do commita; nie musi byc juz w katalogu. Po przypieciu commita
collector pobiera tez poczatkowa, ograniczona
czteropoziomowa mape sciezek i typow z root repozytorium. Gdy katalog jest
duzy, material pokazuje niepelnosc i kontynuacje; tool moze odczytac kolejne
cztery poziomy od wybranej bezpiecznej sciezki lub strony. Tree/list/search
zwracaja sciezki bez tresci, a dopiero pelny zweryfikowany read
udostepnia cytowalny `gitlab:` source ref zawierajacy projekt i commit.
Odczyt ma limit 256 KiB na plik,
odrzuca pliki nietekstowe, niepelne i potencjalnie wrazliwe. Braki zrodel
staja sie jawnymi ograniczeniami widocznosci.

Przy `CREATE_AREA` z GitLabem formularz przesyla opcjonalne, typowane
`repositoryFacts` obok wolnego opisu. `usage` rozroznia `UNKNOWN`,
`DEPLOYED_SYSTEM`, `SHARED_LIBRARY` i `EXISTING_SYSTEM`. Dla nowego wdrazanego
systemu operator moze podac `systemName` i jawny `runtimeServiceName` (sygnal
`system.matchSignals.exact.serviceNames`); dla niewdrazanej biblioteki wskazuje
0-5 znanych systemow korzystajacych z niej, a dla kodu istniejacego systemu
dokladnie jedno ID. Backend sprawdza ID w aktualnym katalogu, a odpowiedzi
przekazuje do AI jako osobne, sanitizowane `operatorFacts` z refem
`operator:repository-facts`. Ten ref nie potwierdza odczytu GitLaba; do
propozycji repozytorium potrzebny jest ref rzeczywiscie przeczytanego pliku
z przypietego commita, a zmiana pola `repository.git` musi ten ref cytowac.
Bez `repositoryFacts` tryb `CREATE_AREA` jest ogolna rewizja katalogu:
AI moze zaproponowac `CREATE` i `UPDATE` w dziewieciu kanonicznych typach,
korzystajac z pelnego biezacego katalogu, takze gdy wybrano GitLaba. Sam
wybor projektu nie potwierdza jego roli ani wdrozenia; nowe repozytorium
dalej wymaga refa faktycznie przeczytanego pliku, a nowe scope wybranego
repozytorium i potwierdzonej granicy kodu.

Dla `SHARED_LIBRARY` AI moze zaproponowac samo repozytorium typu
`shared-library`, bez sztucznego systemu. Dla `DEPLOYED_SYSTEM` z nazwa moze
zaproponowac `system -> repository -> code-search-scope`; dla
`EXISTING_SYSTEM` repozytorium i powiazanie z wybranym systemem. Przy
powiazaniu biblioteki albo kodu istniejacego systemu job przekazuje tylko
systemowe scope'y jawnie wybranych systemow, wraz z aktualna lista
`beforeRepositories`. Dopuszczalny `UPDATE code-search-scope` dodaje jedno
repozytorium na koncu listy z nizszym priorytetem, zachowujac primary i
granice wyszukiwania. Brak dokladnie jednego scope'u dla wskazanego systemu,
niedostepna lista repozytoriow albo zbyt duzy kontekst blokuje asyste przed AI.
Parser pilnuje wybranego scopeId, a maintenance porownuje `before` z biezacym
stanem katalogu przed zapisem. Zalezna propozycja nie moze uzyc ID z
pominiętego `CREATE`; powiazanie z nowym repo wymaga tez wybrania jego pola
`git`. Przy podlaczaniu nowego repo do wskazanych systemow draft musi zawierac
`UPDATE repositories` dla kazdego ich scope'u; batch review nie pozwala
pominac tej zaleznosci. Operator sprawdza caly zestaw i publikuje go jedna
decyzja.

Sesja AI dostaje sanitizowany opis, pelne decoded mapy dziewieciu aktywnych
YAML z jednego digesta i 11 aktualnych instrukcji
`operational-context-maintenance/` z pakietu aplikacji. Prompt przedstawia
te instrukcje w osobnej sekcji reguł, przed danymi zadania i wybranego GitLaba.
Metadane projektu i drzewo są oddzielone od odczytanych treści. Projekty GitLab
zapisane w repo-map.yml sa dodatkowo wyswietlane jako podpowiedzi, nie pelny
spis dostepnych projektow, a kazdy
aktywny dokument katalogu jest pokazany jako osobny JSON z refem
`opctx:<plik>`. Opis operatora, pliki GitLab i wpisy katalogu sa materialem
do analizy, a nie instrukcjami zmieniajacymi zasady asysty. Skrypty cleanup i raport
porzadkowy nie sa materialem runtime. Reguly sa sprawdzone wobec biezacego
schematu zapisu; schemat/validator ma pierwszenstwo przed przykladami.
Przekroczenie jawnego limitu materialu blokuje job bez cichego obciecia.
Feature wymaga `LONG_CONTEXT_REQUIRED`; brak aktywnego dlugiego kontekstu
wybranego modelu blokuje run przed pierwszym promptem. Domyslne model i
reasoning ustawiaja `analysis.operational-context-assistance.ai.*`, a request
moze je nadpisac. Polski skill `operational-context-catalog-revision` jest
osadzony w prompcie. Ma nowa nazwe, bo runtime zachowuje starsze lokalne
wersje skilli bez nadpisywania; archiwalna wersja
`operational-context-assistance` nie jest uzywana przez ten feature. Przy wybranym
GitLabie sesja dostaje piec neutralnych read-only tools ze wspolnego
katalogu, bez mozliwosci zapisu katalogu lub repozytorium. `list_tree` obejmuje
najwyzej cztery poziomy, 12 zadan HTTP i 120 wpisow na wywolanie;
`list_repository_branches` zwraca do 100 galezi z oznaczeniem domyslnej,
`list_files` pobiera ograniczona strone sciezek z kursorem, a
`search_repository_files` wykorzystuje wyszukiwanie GitLab na galezi jako
zrodlo kandydatow, po czym kazda znaleziona sciezke weryfikuje na przypietym
commicie. Wyniki nawigacji nie sa dowodem
tresci. Odczyt ogranicza sie do 256 KiB i odrzuca sciezki oraz
tresci wygladajace na wrazliwe. Dla tych tools obowiazuje twardy limit
wywolan w sesji, niezalezny od globalnego trybu SOFT.
Parser traktuje wynik jako niezaufany, typowany draft: odrzuca nieznane pola,
niekanoniczne sciezki i typy, nieautoryzowane source refs, wrazliwa tresc oraz
samodzielne potwierdzenie ownershipu albo klasyfikacji `frontend`. Draft ma
uporzadkowane propozycje `CREATE` lub `UPDATE` ze zmianami pol `before/after`,
uzasadnieniem, podstawa (`USER_STATEMENT`, `SOURCE_OBSERVATION`,
`AI_INTERPRETATION`), zrodlami, pewnoscia, pytaniami i limitami widocznosci.
`opctx:<nazwa-pliku>` cytuje aktywny dokument katalogu; `gitlab:` musi
pochodzic z wstepnie przeczytanego pliku albo udanego zweryfikowanego read.
Ref innego projektu moze potwierdzac relacje, ale nie zastepuje refa
wybranego projektu wymaganego do jego nowego wpisu repozytorium. Preview
sprawdza caly wynikowy katalog po wszystkich wybranych zmianach, wiec
referencja do `CREATE` w tym samym zestawie jest dopuszczalna.

Gdy wynik AI zawiera tylko pytania, job zachowuje draft i usage, ale ma
status `BLOCKED`, zeby nie sugerowac gotowych propozycji. Przy wybranym
GitLabie brak odczytu pliku jest osobnym ograniczeniem widocznosci; wynik
z propozycjami jest wtedy co najwyzej `PARTIAL`.

Snapshot joba zawiera status, kroki, sanitizowany prompt przygotowany przed
wywolaniem Copilota, bezpieczne metadane jego pracy, usage/cost, source refs,
ograniczenia, draft, preview i decyzje. Wspolny boczny panel pokazuje przebieg
analizy, prompt w kroku `PREPARE_AI`, tok pracy AI oraz szacunek kosztu.
Operator wybiera pola i jawnie
potwierdza wymagane fakty dla wszystkich propozycji. Jeden batch preview
pokazuje polaczony diff, candidate digest oraz wyniki walidacji; jeden batch
decision publikuje wybrane zmiany. Backend bierze wartosci wylacznie z draftu
zachowanego w jobie, nie z payloadu klienta, i wymaga tego samego candidate
digesta, ktory operator zobaczyl w podgladzie. Konflikt nie zapisuje zadnej
decyzji. Aktywne joby i decyzje pozostaja w pamieci procesu na potrzeby
preview i zapisu. Kazdy run jest rownolegle utrwalany przez feature-owned
persister w neutralnym `LocalAnalysisRunStore`: po starcie, zebraniu kontekstu,
przygotowaniu promptu, zakonczeniu lub bledzie oraz po decyzji operatora.
`Analysis History` otwiera zapisany snapshot na ekranie Operational Context
w trybie read-only, takze po restarcie. Historia analiz nie jest historia
wersji ani mechanizmem rollbacku katalogu YAML.
Odczyty katalogu przez ten proces widza jeden snapshot przed albo po
zatwierdzeniu. Journal przywraca poprzednie dokumenty po przerwaniu zapisu;
osobny proces czytajacy bezposrednio pliki YAML w trakcie publikacji moze
chwilowo zobaczyc mieszany stan, wiec nie jest wspieranym czytelnikiem
transakcyjnym.

## Etap B: strukturalne glossary i handoff rules

Zapis jest wspierany dla wszystkich dziewieciu strukturalnych typow:

- `system`,
- `repository`,
- `code-search-scope`,
- `process`,
- `integration`,
- `bounded-context`,
- `team`,
- `glossary-term`,
- `handoff-rule`.

Kanoniczne zrodla glossary i handoff rules to `glossary.yml` oraz
`handoff-rules.yml`. MVP odczytuje jeden aktualny strukturalny format i nie
utrzymuje decoderow historycznych formatow Markdown.

## Agent Tools

Operational context tools sa neutralna capability pod prefixem `opctx_`:

- `opctx_get_scope`,
- `opctx_list_entities`,
- `opctx_search`,
- `opctx_get_entity`.

Tools nie przyjmuja `correlationId`, `environment`, `gitLabGroup` ani
`gitLabBranch` jako model-facing input. Scope katalogu pochodzi z konfiguracji
aplikacji i adaptera.

Tools sluza do:

- znalezienia systemu, procesu, bounded contextu, integracji, repozytorium,
  zespolu, terminu albo handoff clue,
- dociagniecia kompaktowego detailu encji,
- wskazania code-search scopes i repozytoriow do dalszych GitLab calls,
- pokazania resolved ownership, ograniczen widocznosci i pytan otwartych.

Tools nie sluza do:

- root cause detection,
- odczytu kodu,
- listowania endpointow albo klas,
- wykonywania DB diagnostics,
- odtwarzania deploymentu.

Incident-specific zasady uzycia sa w feature policy i runtime skillach
Copilota, nie w neutralnym kontrakcie tools.

## GitLab I Code Search

`gitlab_list_available_repositories` korzysta z operational context jako
lekkiego discovery nad repozytoriami.

Tool moze zwrocic:

- `projectName`, `gitLabPath`, aliases i summary repozytorium,
- references do systems, bounded contexts, processes i integrations,
- `codeSearchScopes` z targetem semantycznym, rolami repozytoriow,
  priorytetem, `reason`, `readFor`, `searchMode`, `pathPrefixes` i lista
  projektow.

Model uzywa `searchMode/pathPrefixes` jako jawnej granicy dla GitLab
search/flow/class-reference tools. Gdy repozytoria w jednym scope maja rozne
granice, model powinien wykonac osobne focused calls dla repozytoriow/prefixow,
zamiast mieszac niezgodne prefixy w jednym zapytaniu. Po wyborze repozytorium
model uzywa GitLab search/read tools do odkrywania faktycznego kodu.

Wszystkie GitLab MCP tools uzywaja opcjonalnej listy `applicationNames`.
Feature przekazuje w hidden tool context allowliste kanonicznych `system.id`.
Brak `applicationNames` w wywolaniu oznacza wszystkie systemy dozwolone dla
sesji. Jawnie podana lista wybiera systemy do uzycia w danym callu i moze
rozszerzyc zakres poza domyslny evidence scope tylko wtedy, gdy kazdy podany
CRM system istnieje w operational context i ma zdefiniowany `codeSearchScope`.
Repozytoria oraz projekty sa nadal walidowane wobec aktywnej unii
`codeSearchScope`; proba podania systemu bez scope'u albo projektu spoza tej
unii jest odrzucana.

Aktywny code-search scope jest unia scope'ow wszystkich wybranych systemow.
Obejmuje scope targetujacy bezposrednio `system` oraz scope targetujacy jego
referencjonowany `process`, `bounded-context` albo `integration`. Broad
discovery moze przeszukiwac tylko repozytoria `primary` w tej unii;
repozytoria wspierajace pozostaja dostepne dla focused reads po znanej klasie
albo sciezce.

## Incident Analysis Usage

W incident flow operational context jest enrichment stepem nad zebranym
evidence.

Typowe uzycie:

1. Elasticsearch/Dynatrace/GitLab deterministic zbieraja fakty incydentu.
2. Operational context matcher dopasowuje systemy, procesy, bounded contexty,
   integracje, code-search scopes, glossary i sytuacje handoffu.
3. Prompt dostaje operational grounding, code-search scopes i ograniczenia.
4. AI uzywa katalogu do `functionalAnalysis`: system, proces, jezyk lokalny,
   resolved ownership, handoff, widocznosc.
5. AI uzywa GitLab tools do `technicalAnalysis`, gdy trzeba znalezc konkretny
   kod.
6. AI uzywa DB tools tylko zgodnie z feature policy i resolved environment.

Ogolny `max-items-per-type` nie usuwa bezposrednio wykrytych systemow
`internal-service`. Wszystkie neutralne CRM systemy rozpoznane przez
service/container name, deployment signal albo nazwe z `matchSignals` sa
zachowywane w evidence, tak aby feature mogl zbudowac kompletny domyslny scope
GitLaba. AI moze jawnie poprosic o dodatkowy CRM system spoza domyslnego
scope'u, ale tylko przez `applicationNames` przechodzace przez operational
context i `codeSearchScope`.

Operational context moze uzasadnic, gdzie szukac dalej. Nie jest samodzielnym
dowodem root cause ani zamiennikiem deterministic evidence.

## Config Drift Viewer Usage

Publicznym targetem weryfikacji jest kanoniczny `system` o
`systemType=internal-service`. Configuration directory jest rozstrzygany z
`runtime.configurationDirectory` systemu (z tolerancja zastanego legacy
`deployment.configurationDirectory`); nie jest swobodnym inputem operatora
uruchamiajacego analize.

Tryb `BASIC` nie laduje katalogu do interpretacji i nie wykonuje code search.
Tryb `DEEP` wymaga jednoznacznego systemu oraz code-search scope targetujacego
ten system. Preflight potwierdza ref repozytorium kodu albo jawnie oznacza
fallback do default branch jako ograniczenie widocznosci. Dalsze wyszukiwanie
jest ograniczone do repozytoriow i `pathPrefixes` ze scope'u.

Operational Context pomaga powiazac zmienione klucze z systemem, procesem,
bounded contextem i ownershipem. Nie zmienia deterministycznego diffu i nie
potwierdza wdrozonej wersji kodu. Gdy katalog jest pusty, niepelny,
niejednoznaczny albo niedostepny, `DEEP` jest blokowany w preflight lub konczy
sie wynikiem czesciowym z jawnym visibility limit; `BASIC` pozostaje dostepny.

## Maintenance

Prompty w `operational-context-maintenance` musza generowac tylko aktualny
kontrakt katalogu. Nie wolno przywracac instrukcji tworzenia technicznego
inventory.
Kolejnosc uzupelniania i zasady jakosci danych opisuje
[`operational-context-fill-order.md`](../../operational-context-maintenance/operational-context-fill-order.md).

Skrypt:

```powershell
operational-context-maintenance/cleanup-operational-context.ps1
```

ma sluzyc do czyszczenia istniejacych katalogow z usunietych pol i sekcji oraz
do deterministycznych migracji kontraktu. Domyslny tryb jest dry-run; `-Apply`
zapisuje zmiany. Dla tej wersji kontraktu skrypt dopisuje
`systemSubtype: unknown` do kanonicznych wpisow `systemType: internal-service`,
ktore nie maja subtype. Nie klasyfikuje ich jako frontend/backend na podstawie
nazwy lub kodu. Skrypt usuwa tez cale bloki YAML dla starych struktur i
raportuje wszystkie zmiany. Z `handoff-rules.yml` usuwa nieodczytywane pola
`confidence`, `affectedSystems`, `affectedProcesses` i `affectedIntegrations`
na poziomie reguly; powiazania pozostaja w `references`. Zmiana lokalnego
katalogu wymaga jawnego `-Apply` po przegladzie raportu dry-run.

Po wiekszej zmianie katalogu nalezy wykonac:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\operational-context-maintenance\cleanup-operational-context.ps1
```

Komenda powinna pokazac `Changes: 0` po zakonczonej migracji. Dodatkowy `rg`
dobieraj do konkretnej migracji albo listy usuwanych pol z planu.

## Validation

Walidacja katalogu powinna pilnowac:

- unknown relation targets,
- self references,
- duplicate references,
- brak `systemType`, brak/nieobslugiwany `systemSubtype` dla
  `internal-service` albo subtype zapisany dla innego typu,
- jawny `systemSubtype=unknown` jako warning i brak kwalifikacji do feature'a
  zaleznego od subtype,
- code-search scope bez targetu albo repozytorium,
- frontend bez system-targeted code-search scope albo z wieloma takimi scope'ami,
- frontend bez jednego jawnego primary repository albo z wieloma primary,
- primary repository frontendu bez `repositoryType=frontend`,
- unknown code-search repository,
- code-search repository bez `searchMode`,
- `searchMode=path-prefixes` bez `pathPrefixes`,
- `searchMode=whole-repository` z `pathPrefixes`,
- niepoprawne `pathPrefixes`,
- `ownership` poza `system` i `bounded-context`,
- ownera zapisanego jako inferowany zamiast jawnie potwierdzonego,
- poprawnych struktur `localLanguageSummary`, `scope`, `semanticBoundary`,
  `evidence` i `llmToolHints` dla bounded contextu,
- open questions dla realnych luk widocznosci.

Validation pilnuje aktualnego kontraktu API/read-modelu, a nie historycznych
pol migracyjnych, ktorych runtime juz nie parsuje. Nie powinna tez wymuszac
technicznych hintow. Brak endpointu, klasy, tabeli albo deployment file w
katalogu nie jest bledem.

## UI

Frontend route `/operational-context` jest widokiem `Tool Workbench /
Operational Context`.

UI pokazuje:

- summary i validation,
- listy encji,
- detail encji,
- relations,
- code-search scopes,
- search boundary dla code-search scopes,
- open questions,
- asyste AI ze statusem runu, zrodlami, ograniczeniami i review propozycji.

Przycisk `Uzupelnij z AI` zajmuje prawa strone paska statusu i otwiera widok
asysty. Nie ma osobnej zakladki asysty ani technicznej informacji o sciezce
lokalnej kopii w pasku. Wewnetrzny stan `assistance` nadal sluzy wejsciom z
historii, empty state, detail drawer, Validation i Open Questions.

UI najpierw pobiera metadane maintenance lokalnej kopii. Podczas ladowania albo
bledu endpointu caly read view pozostaje dostepny, ale akcje zapisu sa
wylaczone, bo backend nie potwierdzil gotowosci katalogu. Gdy lokalna kopia
jest dostepna:

- `Add` jest dostepne tylko na zakladkach dziewieciu wspieranych typow YAML,
- detail drawer zachowuje `Copy`, `Open raw` i `Close` oraz dodaje `Edit` i
  `Delete`; dla writable encji prowadzi tez do asysty `IMPROVE_ENTITY`,
- editor wysyla kanoniczny maintenance payload i zachowuje immutable ID,
- edytor domyslnie pokazuje podstawowe pola, a pola zaawansowane mozna
  rozwinac bez utraty dostepu do pelnego kontraktu,
- system editor udostepnia jawne pola `systemType` oraz zamkniety select
  `systemSubtype`; subtype jest wymagany dla `internal-service` i pomijany dla
  pozostalych typow,
- zlozone pola systemu i repozytorium (`participants.externalOwner`,
  `runtime.configurationDirectory`, `evidence`, `llmToolHints`) maja prowadzone
  kontrolki z tooltipami opisujacymi format oraz skutek runtime/AI; UI nie
  wymaga dla nich surowego JSON,
- `localLanguageSummary`, `scope`, `semanticBoundary`, `evidence` i
  `llmToolHints` bounded contextu maja listy, karty i tooltipy zgodne z ich
  rzeczywistym uzyciem; zaden wspierany field kanoniczny nie wymaga raw JSON,
- delete dialog pokazuje inbound references i blokuje usuniecie, gdy impact
  nie jest dozwolony,
- po zapisie UI odswieza wszystkie listy, read modele, validation, open
  questions, Signal Resolver i previews.

Validation i Open Questions pozostaja read-only projekcjami. `Copy` targetu
utrzymaniowego zawsze zostaje; `Edit source` jest tylko dodatkowa akcja dla
jednoznacznego, writable i wspieranego targetu. UI nie utrzymuje
kompatybilnosci ze starym payloadem i nie renderuje technicznych read modeli
usunietych z backendu.

Trzy sciezki operatorskie na tym ekranie:

1. Operator naciska `Uzupelnij z AI`, opisuje obszar i opcjonalnie wybiera projekt
   GitLab lub wkleja jego pelny URL. Wybiera galaz z filtrowanej listy
   pobranej dla tego projektu albo recznie podaje galaz/commit. Dla GitLaba wybiera jedna role projektu;
   dopiero wtedy pojawiaja sie potrzebne pytania o nazwe nowego systemu,
   nazwe uslugi w logach albo znane systemy korzystajace z repozytorium.
   Moze otrzymac samo repozytorium, nowy system ze scope'em, repozytorium
   dopiete do scope'u wybranego systemu albo zmiane innych wpisow katalogu.
   Wybiera pola, sprawdza polaczony diff i zapisuje caly zestaw jedna decyzja.
2. Z detail encji operator przechodzi do `IMPROVE_ENTITY`; asysta dostaje
   target, pokazuje diff pol i jego podstawe. Operator wybiera albo pomija
   proponowane pola. Wartosc wymagajaca recznej korekty jest poprawiana w
   dostepnym edytorze encji, po odswiezeniu jej biezacej wersji.
3. Z Validation lub Open Questions operator przechodzi do `RESOLVE_FINDING`
   dla konkretnego targetu. AI moze zaproponowac powiazana poprawke albo
   pytanie do czlowieka; finding/pytanie znika dopiero, gdy po zapisie
   zmieni sie kanoniczny katalog i ponowna walidacja juz go nie zwroci.

Review nie jest zgoda na automatyczny zapis: operator potwierdza wybrane
pola, a backend sprawdza warunek aktualnosci w chwili publikacji. Gdy draft
jest nieaktualny lub kandydat nie przechodzi walidacji, UI pokazuje blad i
zachowuje wybor do dalszej decyzji; nie nadpisuje nowszego stanu katalogu.

## Rozwoj Nowych Feature'ow

Nowe feature'y analityczne powinny reuse'owac:

- `integrations.operationalcontext`,
- `agenttools.operationalcontext`,
- `api.operationalcontext`,
- `shared.ai` i neutralne evidence modele,
- `aiplatform` dla runtime AI.

Feature dostarcza wlasny prompt, policy, hidden context, result contract i
zasady uzycia tools. Operational context daje wspolny katalog orientacyjny,
ale nie przejmuje odpowiedzialnosci feature'a za interpretacje wyniku.

## Anty-Wzorce

Nie przywracaj:

- osobnego canonical runtime component obok `system`,
- inline scope'u kodu pod systemem,
- bezposredniego `system.references.repositories`,
- repository source layout albo module inventory poza coarse
  `searchMode/pathPrefixes` w `code-search-scopes.yml`,
- technicznych hintow kodu/API,
- detailed transport/payload/operation inventory integracji,
- technicznych projekcji implementacji, flow i impact jako operational
  context API,
- incident-specific semantyki w neutralnych `opctx_*` tools,
- fallbackow czy aliasow starego kontraktu.

Najprostsza zasada: jezeli informacja szybko zmienia sie z kodem, deploymentem
albo kontraktem runtime, nie nalezy jej utrzymywac w operational context.
Katalog ma prowadzic do miejsca dalszej analizy, nie zastapic analizy.
