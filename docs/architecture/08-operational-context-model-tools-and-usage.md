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

## Pliki Katalogu

Domyslny katalog runtime znajduje sie w:

```properties
src/main/resources/operational-context
```

Runtime root jest konfigurowany przez:

```properties
analysis.operational-context.enabled=false
# analysis.operational-context.resource-root=operational-context
# analysis.operational-context.max-items-per-type=2
# analysis.operational-context.max-glossary-terms=3
# analysis.operational-context.max-handoff-rules=2
```

Pliki katalogu:

| Plik | Rola |
| --- | --- |
| `operational-context-index.md` | opis celu katalogu, zasad modelowania, quality gates i ograniczen |
| `systems.yml` | kanoniczne systemy, aliasy, status, summary, references, ownership i open questions |
| `repo-map.yml` | mapa repozytoriow do GitLaba i relacji katalogowych |
| `code-search-scopes.yml` | semantyczne grupy repozytoriow do wspolnego przeszukania |
| `processes.yml` | procesy biznesowo-operacyjne, kroki, rezultaty i relacje |
| `bounded-contexts.yml` | bounded contexty, jezyk lokalny, zakres odpowiedzialnosci i granice |
| `integrations.yml` | integracje jako relacje systemowe: source, target, category, style i direction |
| `teams.yml` | identyfikatory, etykiety i opis zespolow uzywanych przez ownership |
| `glossary.md` | slownik pojec biznesowo-systemowych |
| `handoff-rules.md` | sytuacje handoffu, wymagane evidence i pierwsze akcje |

## Model

### System

`system` jest glownym targetem relacji. Powinien miec:

- `id`, `name`, `aliases`,
- `systemType`, `lifecycleStatus`, `criticality`,
- `summary`,
- `references` do procesow, integracji, bounded contextow, zespolow i pojec,
- `ownership`, `relations`, `sourceCoverage`, `gaps` tam, gdzie sa potrzebne.

System nie powinien miec osobnego katalogu runtime names, deployment names,
container names, endpointow ani `references.repositories`. Code discovery dla
systemu zaczyna sie od code-search scope'u targetujacego ten system.

### Repository

Repository opisuje, czym jest repo w krajobrazie systemu i do jakich bytow
katalogowych sie odnosi. Powinno zawierac:

- GitLab identity (`projectName`, `projectPath`),
- purpose/summary,
- status, criticality, aliases,
- references do systemow, procesow, integracji i bounded contextow,
- limitations i open questions.

Repository nie opisuje ukladu katalogow, plikow build/deployment ani sciezek
implementacji. Repository nie definiuje ownera; owner endpointu, klasy albo
repozytorium jest rozstrzygany przez system/bounded context znaleziony przez
code-search scope.

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

Dozwolone role powinny opisywac relacje w analizie, np. `primary`, `support`,
`shared-library`, `migration-peer`, `external-adapter`. Nie uzywamy roli jako
substytutu dla szczegolow implementacji.

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

- summary i local language,
- includes/excludes,
- business capabilities,
- invariants,
- relacje do systemow, procesow i integracji,
- ownership oraz limitations.

Bounded context moze wskazac pojecia domenowe, ale nie powinien przechowywac
list klas encji, Java-owych implementation hints ani list repozytoriow. Code
discovery dla bounded contextu zaczyna sie od code-search scope'u targetujacego
ten bounded context.

### Integration

Integration opisuje zaleznosc systemowa:

- source system i target systems,
- category, integration style, direction,
- criticality i data sensitivity,
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
- `glossary.md` tlumaczy pojecia i rozroznienia,
- `handoff-rules.md` opisuje, kiedy i z jakim evidence przekazac temat.

Nie nalezy uzywac ich jako miejsca na techniczne instrukcje czytania kodu ani
na reczny routing teamow.

## Loader I Read Models

Katalog jest ladowany przez
`integrations.operationalcontext.OperationalContextAdapter`.

Adapter:

1. normalizuje `analysis.operational-context.resource-root`,
2. laduje pliki YAML z classpath,
3. parsuje `glossary.md`, `handoff-rules.md` i index,
4. mapuje encje do neutralnych DTO,
5. buduje open questions i validation findings,
6. udostepnia filtrowanie przez `OperationalContextQuery`.

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
aktualnego katalogu przez UI i narzedzia operatorskie.

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
projekcji. UI `/operational-context` jest tylko widokiem aktualnego katalogu i
ma dopasowywac sie do kontraktu backendu bez kompatybilnosci wstecznej.

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

Operational context moze uzasadnic, gdzie szukac dalej. Nie jest samodzielnym
dowodem root cause ani zamiennikiem deterministic evidence.

## Maintenance

Prompty w `operational-context-maintenance` musza generowac tylko aktualny
kontrakt katalogu. Nie wolno przywracac instrukcji tworzenia technicznego
inventory.

Skrypt:

```powershell
operational-context-maintenance/cleanup-operational-context.ps1
```

ma sluzyc do czyszczenia istniejacych katalogow z usunietych pol i sekcji.
Domyslny tryb jest dry-run; `-Apply` zapisuje zmiany. Skrypt usuwa cale bloki
YAML dla starych struktur oraz raportuje zakres usuniec.

Po wiekszej zmianie katalogu nalezy wykonac:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\operational-context-maintenance\cleanup-operational-context.ps1 -DryRun
```

Komenda powinna pokazac `Removal candidates: 0`. Dodatkowy `rg` dobieraj do
konkretnej migracji albo listy usuwanych pol z planu.

## Validation

Walidacja katalogu powinna pilnowac:

- unknown relation targets,
- self references,
- duplicate references,
- code-search scope bez targetu albo repozytorium,
- unknown code-search repository,
- code-search repository bez `searchMode`,
- `searchMode=path-prefixes` bez `pathPrefixes`,
- `searchMode=whole-repository` z `pathPrefixes`,
- niepoprawne `pathPrefixes`,
- `ownership` poza `system` i `bounded-context`,
- ownera zapisanego jako inferowany zamiast jawnie potwierdzonego,
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
- open questions.

UI nie utrzymuje kompatybilnosci ze starym payloadem i nie renderuje
technicznych read modeli usunietych z backendu.

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
