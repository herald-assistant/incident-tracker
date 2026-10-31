# Release summary — raport zmian Liquibase

Status: in-progress

Source need: [Release summary](../needs/release-summary.md)

Data analizy kodu: 2026-10-31.

Poziom: L3 dla całego rozwiązania ze względu na nowy wariant zakresu sesji
GitLab obejmujący dwie przypięte rewizje; poszczególne zmiany integracji,
narzędzi i wspólnego UI są L2, a nowy feature jest L1. Nie powstaje nowy
kierunek zależności między warstwami.

Użytkownik zatwierdził wykonanie wszystkich siedmiu kroków 2026-10-31,
z weryfikacją i osobnym commitem po każdym kroku. Szczegółowy zakres obejmuje tylko Liquibase.
Drugi tryb — zmiany kontraktów API — jest zapowiedziany, ale zgodnie
z doprecyzowaniem użytkownika pozostaje poza obecną analizą i krokami.

## Potrzeba / dlaczego

Zespół potrzebuje kompletnego, tabelarycznego opisu zmian bazodanowych między
dwiema wersjami aplikacji oraz ich zastosowania funkcjonalnego. Sam diff nie
łączy migracji z kodem i wiedzą o systemie. Kompletność zebranego materiału
nie może zależeć od tego, czy model sam odnajdzie wszystkie migracje.

## Proponowane rozwiązanie

Dedykowany `features.releasesummary` zbiera porównanie źródeł deterministycznie,
dołącza pełne właściwe encje Operational Context i uruchamia Copilota z własnym
promptem oraz polskim workflow. AI interpretuje zmiany, doczytuje kod i katalog
bez budżetu liczby wywołań, a wynik zapisuje przez istniejące `report_*`.
`AnalysisReport` pozostaje jedynym źródłem treści raportu.

Wspólne dla przyszłych trybów pozostają wybór aplikacji/refów, manifest
porównania, job, historia i prezentacja. W obecnym kontrakcie przyjmujemy
wyłącznie `LIQUIBASE`; nie dodajemy pozornego działającego trybu API, jego
parserów, promptu ani osobnego frameworka rozszerzeń.

### Baseline i reuse/gap analysis

Poniższe wnioski wynikają z kodu, nie wyłącznie z dokumentacji.

| Obszar | Obecny mechanizm | Reuse i brakujący zakres |
| --- | --- | --- |
| Aplikacje | `FlowExplorerSystemSelectionService`, `FlowExplorerRepositoryScopeService` | Systemy pochodzą z Operational Context; repozytoria wynikają z `codeSearchScopes`. Są to klasy feature-owned, nie importujemy ich do Release summary. |
| UI aplikacji | Lokalny select w `flow-explorer-page` | Wydzielić parametryzowany wspólny selektor, zasilany opcjami właściciela ekranu; zachować zachowanie Flow Explorera. |
| Branche | `GitLabSystemBranchesController`, `GitLabRepositoryBranchPort`, `GitLabRepositoryBranchService` | Obecny endpoint agreguje sumę nazw z maksymalnie ośmiu primary repozytoriów; adapter czyta pierwsze 100 branchy. Nie jest dowodem dostępności pary refów we wszystkich repozytoriach. Brakuje tagów i pełnej paginacji wyboru refów. |
| UI branchy | `GitLabBranchSelectComponent` | Ma `sourceKey`, `branchLoader`, anulowanie nieaktualnego zapytania i debounce. Rozszerzyć wspólny wzorzec o typ refa i paginację; nie kopiować go dwa razy w feature. Obecny auto-wybór pierwszej/default gałęzi wymaga świadomej polityki dla pary. |
| Przypinanie źródeł | `GitLabRepositoryRevisionPort.resolveRevision` | Gotowy odczyt SHA. Używać osobno dla obu refów i każdego repo; dalsze odczyty muszą używać SHA. |
| Tree i pliki | `GitLabRepositoryTreePort`, `GitLabRepositoryTreeService`, `GitLabRepositoryReadPort`, `GitLabVerifiedRepositoryFilePort` | Są paginacja, cache, metadata, pełny i fragmentaryczny odczyt. Publiczny tree nie zachowuje blob ID, choć metadata pliku je ma. Dodać neutralny manifest snapshotu bez drugiego klienta/paginacji tree. |
| Diff | `GitLabMergeRequestDiffPort` | Obsługuje MR, nie dowolną parę refów. Brak neutralnego porównania dwóch snapshotów repozytorium i brak Liquibase discovery. Nie tworzyć fikcyjnego MR. |
| Operational Context | `OperationalContextPort.capture()`, pełne `payload` encji, `OperationalContextNavigationPort`, `OperationalContextCodeSearchPort` | Reuse snapshotu i relacji. Początkowy prompt ma otrzymać pełne encje, nie krótkie karty z list/search ani incidentowy limit liczby elementów. |
| Narzędzia katalogu | `opctx_get_scope`, `opctx_list_entities`, `opctx_search`, `opctx_get_entity` | Gotowy pełny read-only katalog; default detail bywa kompaktowy, dodatkowe sekcje są dostępne przez `include`. Dziś każde wywołanie ładuje bieżący katalog; opcjonalny captured read session jest małym neutralnym rozszerzeniem dla spójności runu. |
| GitLab tools | `GitLabRepositoryToolScope`, navigation/search/full/chunk reads | Obecny pinned scope odrzuca drugi ref wybranego projektu. Potrzebna jawna mapa obu rewizji dla repozytoriów porównania. Reuse narzędzi, bez `release_summary_read_file`. |
| Budżet | `CopilotToolBudgetPolicy`, hidden `TOOL_BUDGET_POLICY_GOAL_DRIVEN` | Istniejący wariant omija liczniki eksploracji. Nie dołączać `CopilotSessionHardToolBudget`. Nie zmieniać globalnego budżetu innych analiz. |
| Raport | `CopilotReportTools`, `CopilotReportSessionStore`, `AnalysisReportSection` | Markdown już obsługuje tabele. Manifest potwierdza strukturę, nie opisanie każdej kolumny/indeksu. Feature potrzebuje własnego manifestu pokrycia i walidacji raportu. |
| Runtime | `CopilotRunRequest`, execution gateway, context tier, `CopilotInitialContextModelSelector` | Reuse sesji, auth, usage, aktywności i preflight pełnego promptu. Nie tworzyć własnego cennika ani progów modeli. |
| Historia i UX | `localworkspace.analysisruns`, shared chat/report/steps/aside/share, `AnalysisJobPollingService` | Gotowe neutralne elementy; nowe DTO, mapowanie, persistence adapter i routing są własnością feature'a. |

Istotne miejsca kodu do implementacji:

- [wybór aplikacji](../../src/main/java/pl/mkn/tdw/features/flowexplorer/context/FlowExplorerSystemSelectionService.java),
  [scope repozytoriów](../../src/main/java/pl/mkn/tdw/features/flowexplorer/context/FlowExplorerRepositoryScopeService.java),
  [branch API](../../src/main/java/pl/mkn/tdw/api/gitlab/GitLabSystemBranchesController.java);
- [tree service](../../src/main/java/pl/mkn/tdw/integrations/gitlab/service/GitLabRepositoryTreeService.java),
  [revision port](../../src/main/java/pl/mkn/tdw/integrations/gitlab/GitLabRepositoryRevisionPort.java),
  [verified file port](../../src/main/java/pl/mkn/tdw/integrations/gitlab/GitLabVerifiedRepositoryFilePort.java);
- [scope narzędzi](../../src/main/java/pl/mkn/tdw/agenttools/gitlab/GitLabRepositoryToolScope.java),
  [budżet](../../src/main/java/pl/mkn/tdw/aiplatform/copilot/tools/policy/budget/CopilotToolBudgetPolicy.java),
  [narzędzia raportu](../../src/main/java/pl/mkn/tdw/aiplatform/copilot/tools/report/CopilotReportTools.java);
- [model selector](../../src/main/java/pl/mkn/tdw/aiplatform/copilot/runtime/context/CopilotInitialContextModelSelector.java),
  [wspólny branch select](../../frontend/src/app/components/gitlab-branch-select/gitlab-branch-select.ts).

Porównanie wzorców: Incident Analysis dostarcza referencję lifecycle,
usage/evidence i kontynuacji; Flow Explorer pokazuje dynamiczne sekcje
reportu i wybór aplikacji; UI Explorer pokazuje zachowanie poprawnych sekcji
przy brakach i walidację źródeł; Operational Context Assistance pokazuje
pinned odczyty kolejnych repozytoriów i `GOAL_DRIVEN`. Ich orchestration,
prompty, mappery oraz policy pozostają lokalne. Szczególnie nie kopiujemy
zakazu czytania Liquibase ze skilli Flow Explorera.

### Granice odpowiedzialności

| Właściciel | Zakres |
| --- | --- |
| `integrations.gitlab` | Listing refów, rozwiązywanie SHA, pełny manifest drzewa, pobranie plików, neutralne porównanie snapshotów; bez wiedzy o raporcie release. |
| `integrations.operationalcontext` | Istniejący katalog, captured read session, nawigacja i code-search read model. Bez encji „tabela Liquibase” i bez dopisywania repozytoriów do systemu. |
| `agenttools.gitlab` / `agenttools.operationalcontext` | Neutralny scope wielu przypiętych rewizji i opcjonalny snapshot katalogu, podłączone do obecnych read tools. |
| `aiplatform.copilot` | Istniejące uruchomienie, report store, budżety, context tier i capture; bez logiki Liquibase. |
| `features.releasesummary` | Dobór źródeł, odkrywanie Liquibase, closure zależności, manifest zmian/pokrycia, prompt, skill guidance, policy, walidacja reportu, job, chat i persistence adapter. |
| `api.*` | Wyłącznie wspólne helpery listowania refów; start/run/report/chat zostają w feature. |
| FE `components` / `core` | Wspólny select, polling i istniejące report/run/share/chat primitives. |
| FE `features/release-summary` | Formularz, mapowanie DTO, sekcje wyniku i interpretacja pokrycia Liquibase. |

Nie wydzielamy abstrakcyjnego silnika migracji ani silnika dowolnych analiz.
Parser/discovery Liquibase ma na razie jednego właściciela. Ewentualna
ekstrakcja następuje dopiero przy rzeczywistym drugim konsumencie.

### Formularz, API i kierunek porównania

Route UI: `/release-summary`. Feature ID: `release-summary`.

Formularz zawiera `Application`, `Stan bazowy` i `Stan docelowy` oraz wspólny
wybór modelu/reasoning effort. Jedyny aktywny tryb to zmiany Liquibase.
Przyszłego trybu API nie wysyłamy w requestach; ewentualna informacja
„planowane” nie daje drugiej działającej akcji.

Proponowany start:

```json
{
  "systemId": "crm-customer-service",
  "mode": "LIQUIBASE",
  "baselineRef": { "kind": "BRANCH", "name": "release/current" },
  "targetRef": { "kind": "TAG", "name": "release-next" },
  "model": "<wybór z katalogu platformy>",
  "reasoningEffort": "<opcja obsługiwana przez model>"
}
```

Ref ma typ `BRANCH`, `TAG` albo jawnie zweryfikowany `COMMIT`. Typ rozstrzyga
kolizję nazw brancha i taga; dropdown umożliwia wyszukiwanie i paginację,
a dokładny SHA może być zweryfikowaną opcją. Nie jest to dowolny URL GitLaba.
Backend normalizuje branch/tag do jednoznacznych `refs/heads/<name>` albo
`refs/tags/<name>` przed pinningiem. Mapa scope'u i prompt zachowują te
rozróżnialne selektory także wtedy, gdy obie strony mają tę samą krótką nazwę.

Proponowane endpointy feature'a:

- `GET /api/release-summary/applications` — kwalifikacja aplikacji do tego
  use case'u nad neutralnymi portami katalogu; bez importu API Flow Explorera;
- `GET /api/release-summary/applications/{systemId}/refs` — feature-owned
  agregacja neutralnego listowania refów z pokryciem per repo;
- `POST /api/release-summary/jobs` — `202` i natychmiastowy snapshot;
- `GET /api/release-summary/jobs/{jobId}`;
- `POST /api/release-summary/jobs/{jobId}/chat/messages`;
- `PUT /api/release-summary/jobs/{jobId}/report` — warunkowa ręczna korekta
  przez shared kontrakt edycji, gdy zostanie dostarczony pełny lifecycle edycji.

`from` oznacza stan bazowy, `to` docelowy. Porównujemy dokładne snapshoty
`baselineCommit -> targetCommit`, nie diff od merge-base. Rozbieżne branche
nie mogą zmienić semantyki porównania. Identyczne SHA dają potwierdzony brak
różnic i nie wymagają kosztownej sesji AI.

Aplikacja jest systemem katalogowym, a jej jawne `codeSearchScopes` wyznaczają
repozytoria i ewentualne `pathPrefixes`. Domyślnie badamy wszystkie repozytoria
w tych scope'ach, nie tylko pierwsze/primary. Łączymy powtarzające się wpisy,
zachowując provenance i granice poszczególnych zakresów. Skanowanie jednego
repo nie może zgubić drugiego prefixu tego samego projektu.

Obie nazwy refów są rozwiązywane osobno w każdym repo. Lista opcji pokazuje,
w których repo występują; suma nazw nie oznacza kompletnego scope'u. Brak
refa/odczytu daje jawne `BLOCKED` przygotowania z nazwą repo, a nie przełączenie
na `main`, default branch ani inny commit. W V1 nie dodajemy konfiguratora
osobnej pary refów dla każdego repozytorium. Zewnętrzne repo bez potwierdzonej
rewizji nie może wejść do porównania jako „brak zmian”.

Pozostałe repozytoria odnalezione przez AI mogą służyć wyjaśnieniu znaczenia
zmian. Ich odczyt nie rozszerza automatycznie zakresu release'u.

### Deterministyczne przygotowanie materiału

1. Walidacja requestu, konfiguracji i dostępności auth oraz capture jednego
   snapshotu Operational Context. Rozwiązanie systemu i jego repozytoriów.
2. Zamrożenie par SHA per repo; zapis nazw wybranych refów, typów i SHA
   w manifeście. Usunięcie/przesunięcie brancha po starcie nie zmienia runu.
3. Pobranie kompletnych drzew obu rewizji w dozwolonych prefixach przez wspólny
   `GitLabRepositoryTreeService`. Neutralny manifest zachowuje path, typ oraz
   blob ID; obecny DTO tree wymaga rozszerzenia lub osobnej projekcji portu.
4. Porównanie manifestów: added/deleted/modified, identyczne bloby bez ponownego
   pobierania. Rename można potwierdzić identycznym blobem; przy niepewności
   pozostaje para delete/add. Nie inferujemy rename kolumny z podobnej nazwy.
5. Odkrycie wszystkich changelogów i zależnych zasobów Liquibase po obu
   stronach, następnie pobranie pełnych treści istotnych plików przed i po.
6. Przygotowanie uporządkowanych jednostek zmian, lokalnego diffu tekstowego,
   kontekstu dołączenia/wykonania, źródeł i jawnych braków.
7. Zbudowanie pełnego promptu, scaffold raportu i preflight rozmiaru przez
   platformę. Dopiero potem utworzenie sesji AI.

Discovery ma dwie fazy: pełny manifest ścieżek, następnie sprawdzenie treści
kandydatów obsługiwanych formatów i domknięcie zależności. Sam brak typowej
ścieżki `db/changelog` nie wyklucza pliku. Cache w obrębie przygotowania jest
kluczowany repo/SHA/path lub blob ID i unika wielokrotnych odczytów tego samego
materiału; nie nakłada ukrytego limitu pierwszych plików.

Preferowany fundament kompletności to porównanie drzew i zweryfikowanych
treści. GitLab `repository/compare?straight=true` może dostarczyć pomocniczy
diff/provenance, ale nie jest konieczny w pierwszej implementacji. Nie może
być jedynym źródłem listy plików: GitLab dokumentuje limity i niepełne wyniki
diffu. Nie budujemy nowej ścieżki klonowania repo i uruchamiania lokalnego gita
tylko dla tego feature'a. [GitLab Repositories API](https://docs.gitlab.com/api/repositories/)

### Odkrywanie Liquibase i semantyka źródeł

Zakres obejmuje XML, YAML/YML, JSON, formatted SQL, zwykłe SQL dołączone przez
changelog oraz pliki danych wskazane przez migracje. Rozpoznawanie korzysta
z treści i powiązań, nie tylko folderu `liquibase` albo suffixu `.sql`.
Źródła konfiguracji wskazujące root changelog są czytane jako evidence.
Zmiany poza osiągalnym rootem pozostają widoczne jako niepotwierdzone dołączenie.

Resolver bada po obu stronach `include`, `includeAll`, `sqlFile`, rollback
i zależności plikowe zmian danych, np. `loadData`/`loadUpdateData`. Uwzględnia
ścieżki względne, kolejność, brak pliku, cykle i wielokrotne dołączenia.
Nie wykonuje changelogów, SQL, custom change classes ani skryptów z repo.
XML nie pobiera DTD/encji zewnętrznych; referencje nie wyprowadzają odczytu
poza dozwolone repo/prefixy. Nieznane rozszerzenie, classpath zależności spoza
repo, dynamiczne properties i niemożliwy do rozstrzygnięcia filtr są luką.

Szczególnie wymagane są przypadki:

- istniejący, niezmieniony SQL zostaje dopiero dołączony w target;
- zmienia się zasób SQL/CSV, choć plik changelogu jest niezmieniony;
- zmienia się root, `includeAll`, kolejność albo warunek dołączenia;
- znika plik lub changeset; raport nie nazywa tego automatycznym rollbackiem;
- zmienia się historyczny changeset, ścieżka albo `logicalFilePath`;
- zmieniają się `contexts`, `labels`, `dbms`, `preConditions`, `runAlways`,
  `runOnChange`, `runInTransaction` albo jawny rollback;
- występuje zmiana komentarza/formatowania bez potwierdzonej zmiany działania.

`includeAll` obejmuje katalog i może wciągać pliki, których nie ma w diffie
changelogu; zasady trzeba sprawdzać dla wersji Liquibase używanej przez
analizowane repo, a nie zakładać najnowszych defaultów.
[Liquibase includeAll](https://docs.liquibase.com/secure/reference-guide-5-1-1/changelog-attributes/includeall)

Tożsamość changesetu zachowuje id, autora i logiczną ścieżkę, z dodatkowymi
współrzędnymi repo/modułu; nie deduplikujemy różnych changesetów po samym id.
Autor jest technicznym składnikiem tożsamości migracji, nie metryką pracy osoby.

Nie rekonstruujemy całej bazy i nie udajemy wykonania Liquibase. Raport dotyczy
zmiany deklaracji i przewidywanego skutku. Bez `DATABASECHANGELOG`, parametrów
uruchomienia i stanu bazy nie potwierdza faktycznego wykonania. W szczególności
`runAlways`/`runOnChange` wymagają odrębnej interpretacji.
[Liquibase: ponowne wykonanie changesetu](https://support.liquibase.com/hc/en-us/articles/29383026731035-Can-a-changeset-be-run-again)

### Pełne encje Operational Context i początkowy prompt

Z captured katalogu wybieramy pełną encję systemu, wszystkie scope'y targetujące
ten system i pełne encje wszystkich wskazanych repozytoriów. Każda encja
występuje raz z typem, id, pełnym payloadem i provenance snapshotu. Nie
wybieramy kilku „najważniejszych” pól, nie stosujemy top-N, nie podmieniamy
encji krótką kartą z narzędzia search. Brak encji w referencji jest błędem
przygotowania zakresu, a nie pustym opisem.

Pozostałe encje — glossary, bounded contexts, procesy, integracje, teams —
model doczytuje przez `opctx_*`. Nawigacja pochodzi z
`OperationalContextNavigationPort`, bez własnego odwracania relacji. Pełny
graf katalogu nie staje się automatycznie zgodą na czytanie kodu poza
skonfigurowaną grupą GitLab.

Proponowane małe rozszerzenie neutralnych tools pozwala podać w hidden
context `OperationalContextReadSession`; obecni konsumenci bez niego nadal
czytają bieżący katalog. Release summary korzysta z tego samego captured
katalogu w initial i follow-up. Do kontynuacji zapisuje prywatny snapshot;
publiczny eksport zawiera tylko encje użyte jako evidence, ich digest i czas.
Nie tworzymy nowego storage/versioningu samego Operational Context.

Prompt zawiera logiczne artefakty inline:

- intencję porównania i manifest wszystkich repo/refów/SHA;
- pełne powyższe encje katalogu;
- kompletny manifest zmian i jednostek wymagających opisu;
- pełne before/after istotnych plików z diffem, changesetami i ich kontekstem;
- rozdzielenie zmian, niezmienionych zależności i materiału pomocniczego;
- kontrakt raportu, zasady źródeł i jawne ograniczenia.

Nie jest to zmiana na SDK attachments. Ten sam prepared prompt jest widoczny
w jobie; nie utrzymujemy drugiej, skróconej wersji „do UI”.

### AI, tools i nieograniczona eksploracja

Feature-owned prompt wskazuje nowy polski skill
`release-summary-liquibase` oraz skill follow-up. Skills korzystają ze
wspólnego effective root runtime; loader dopisuje brakujące pliki, bez
nadpisania customizacji innych skilli. Nie wybieramy katalogów per run.

Minimalny kompletny zestaw obejmuje istniejące GitLab repository
discovery/list/tree/search/full/chunk reads, `opctx_*`, `report_*`, `skill`
i jawny feedback. Opcjonalne semantyczne code reads można dopuszczać dopiero
po sprawdzeniu, że respektują ten sam scope obu SHA; nie włączamy całego
prefixu `gitlab_` bez audytu callbacków. Full/chunk reads pozwalają doczytać
każdy tekstowy fragment kodu w dozwolonym zakresie.

Nie przenosimy incidentowej reguły wyłączania GitLaba po dostarczeniu code
evidence. Liquibase jest materiałem zmiany, a odczyty implementacji nadal są
potrzebne do ustalenia jej znaczenia.

Nowy neutralny wariant `GitLabRepositoryToolScope` otrzymuje mapę
`project + canonicalTypedRef -> commit` dla obu stron każdego repo. Nie dodajemy
drugiej implementacji read tools. Istniejący jawny `branchRef` pozostaje
zastanym kontraktem narzędzia: model podaje jednoznaczny selector otrzymany
w manifeście, walidowany wobec tej mapy. Nie rozszerzamy go
o publiczny `gitLabGroup`, `environment` ani dowolny override SHA. Nowe
mechanizmy przechowują wybór rewizji po stronie backendu.

Dodatkowy kod w skonfigurowanej grupie jest dostępny do researchu; pierwszy
odczyt danej pary projekt/ref przypina ją do SHA. Dla repo już objętego
porównaniem nie wolno doczytać trzeciej wersji i przypisać jej do jednej ze
stron release'u. Każdy odczyt rejestruje rzeczywisty source ref z SHA;
nie wystarczy sam path bez wersji. Katalogowe relacje nie rozszerzają uprawnień.

`TOOL_BUDGET_POLICY_GOAL_DRIVEN` obowiązuje w initial i follow-up; brak
`TOOL_HARD_BUDGET`. Nie ma limitu liczby wywołań ani sumarycznej liczby
doczytanych znaków nakładanego przez feature, także na `report_*`. Stronicowanie
pojedynczej odpowiedzi służy transportowi: kolejne strony/chunki pozostają
dostępne. Allowlista, walidacja session scope i zakaz filesystem/shell/write
tools nadal obowiązują.

Runtime ma skończone okno modelu i timeout: obecne properties ustawiają
`send-and-wait-timeout=20m` (default klasy to 5m). Nie opisujemy tego jako
nieskończonego czasu wykonania. Przekroczenie daje jawny stan i zachowany
częściowy raport. Zmiana globalnego timeoutu innych feature'ów nie należy
do planu; nie dodajemy własnego licznika wywołań ani założenia, że
`infiniteSessions` usuwa ograniczenia modelu.

### Kontrakt szczegółowego raportu

Scaffold zawiera stałe `OVERVIEW`, `CHANGE_INDEX`, `DEPLOYMENT_IMPACT`,
`COVERAGE` i deterministycznie wyznaczone sekcje `DB_DETAIL_<unitId>`.
Unit ID pochodzi z manifestu zmian, nie z dowolnego tekstu modelu. Duże pliki
dzielimy na jednostki changesetów/grup operacji przed sesją; nie narzucamy
limitu „pierwszych N zmian”. Wszystkie section IDs trafiają do hidden scope.

| Sekcja | Wymagana treść |
| --- | --- |
| `OVERVIEW` | Funkcjonalny sens release'u i kierunek porównania, bez ukrycia braków. |
| `CHANGE_INDEX` | Wszystkie jednostki: repo, plik, changeset, rodzaj zmiany, link do szczegółów i status opisu. |
| `DB_DETAIL_*` | Tabele szczegółów technicznych oraz tabelaryczne wyjaśnienie funkcjonalne każdej zmiany. |
| `DEPLOYMENT_IMPACT` | Zależności/kolejność, zmiany danych, destrukcyjne operacje, zgodność starego/nowego kodu, rollback i weryfikacja, wyłącznie z uzasadnieniem. |
| `COVERAGE` | Pokrycie plików, changesetów i rozpoznanych elementów, nieobsłużone konstrukcje, brakujące źródła i niepotwierdzone uzasadnienia. |

W szczegółach używamy kilku czytelnych tabel zamiast jednej tabeli
o kilkunastu bardzo szerokich kolumnach. Obie projekcje łączy `elementId`.

Tabela techniczna: `elementId`, rodzaj i pełna nazwa obiektu, operacja,
atrybut, przed, po, źródło. Dla nowej tabeli wymagane są wszystkie kolumny,
typy/rozmiary/precyzja, nullability, default/computed/identity, klucze,
ograniczenia i komentarze obecne w źródle. Dla istniejącego obiektu opisujemy
wszystkie zmienione atrybuty. Brak jawnej wartości nie jest zgadywanym defaultem.

Dla indeksów: nazwa, tabela, uporządkowane kolumny/wyrażenia, unikalność,
warunki i dodatkowe atrybuty obecne w źródle. Dla constraints: kolumny,
referencje, reguły aktualizacji/usunięcia i sprawdzane warunki. Dla danych:
operacja, zbiór docelowy, warunek i transformacja/backfill. Inne obiekty,
np. widoki, sekwencje, procedury i triggery, mają własny jawny wiersz i opis.

Tabela funkcjonalna: `elementId`, znaczenie, użycie w kodzie/procesie,
uzasadnienie zmiany, dowody oraz status `CONFIRMED`, `INFERRED` albo `UNKNOWN`.
Nie łączymy kilku kolumn w wiersz „pozostałe pola”. Dla `UNKNOWN` podajemy
konkretny brak; dla `INFERRED` wyraźnie oddzielamy wniosek od źródłowego faktu.
Hipoteza o intencji autora nie staje się faktem tylko dlatego, że pasuje do nazwy.

### Kompletność i walidacja — osobna od obecności sekcji

Deterministyczny manifest ma trzy warstwy:

- coverage źródeł: repo, oba SHA, wszystkie strony tree, pliki i dependencies;
- coverage jednostek: plik/changeset/operacja, w tym zmiana dołączenia;
- coverage rozpoznanych elementów: identyfikowalne kolumny, indeksy,
  constraints i atrybuty ze strukturalnych deklaracji.

Lekki parser strukturalny Liquibase wyznacza oczekiwane identyfikatory tam,
gdzie wynika to jednoznacznie z XML/YAML/JSON. Nie wyprowadza biznesowego
znaczenia ani pełnego schematu bazy. SQL, dynamiczne konstrukcje i rozszerzenia
są przekazywane w całości jako osobne jednostki wymagające analizy AI;
nie reklamujemy ich jako deterministycznie zweryfikowanej listy wszystkich
obiektów. Nie tworzymy uniwersalnego parsera wszystkich dialektów SQL.

Feature-owned validator sprawdza przed zapisem sekcji przez invocation
policy: dozwolone IDs, wymagane kolumny tabel, pokrycie oczekiwanych
`elementId`, brak duplikatów, niepuste opisy/statusy oraz źródła. GFM trzeba
parsować strukturalnie, z testami pipe/escape/multiline, nie przez
`split('|')`. Walidator nie ingeruje w neutralny `CopilotReportTools`.
Niepoprawna mutacja zwraca modelowi konkretne braki do poprawienia w tej samej
sesji. Po zakończeniu ta sama walidacja tworzy widoczne podsumowanie coverage.

Źródła wolno cytować tylko z initial evidence albo skutecznych odczytów tools,
z poprawnym repo/SHA/path i dostępnym zakresem. Znalezienie ścieżki w search
nie jest przeczytaniem treści. Identyfikatory elementów dopisane przez AI dla
raw SQL mają odrębne pochodzenie `AI_IDENTIFIED` i wskazują jednostkę źródłową.

Pełna struktura reportu nie potwierdza poprawności funkcjonalnej. Dla SQL
potrzebny jest osobny przegląd pokrycia przez model oraz scenariusze akceptacyjne
z niezależnie ustaloną listą elementów. Nierozpoznany albo nieopisany fragment
pozostaje jawnie niezweryfikowany; nie pokazujemy fikcyjnego „100%”.

Publiczny `ReleaseSummaryResult` jest projekcją zapisanego raportu i manifestu:
mode, comparison, coverage, sekcje, visibility limits i usage. Nie ma drugiego
JSON wyniku generowanego przez model ani parsera finalnej prozy jako zamiennika
brakującego reportu. AI kończy krótkim niepustym potwierdzeniem zapisu.

### Duży materiał, błędy i lifecycle

Pełne zmiany i wybrane encje katalogu trafiają do initial promptu. Platforma
sprawdza jego rozmiar wraz z rezerwą i tools oraz dobiera context tier na
podstawie bieżącego katalogu modeli. Feature nie kopiuje progów. Ewentualny
overflow model/effort musi być jawną konfiguracją feature'a i widocznym
wyborem runtime; nie zmieniamy modelu operatora po cichu.

Jeśli pełny materiał nie mieści się w obsługiwanym oknie, przygotowanie kończy
się `BLOCKED / CONTEXT_TOO_LARGE`, z wielkością i wskazaniem źródeł. Nie
streszczamy encji, nie usuwamy plików i nie wysyłamy „reprezentatywnej próbki”.
Dzielenie całego wejścia między wiele sesji byłoby osobną zmianą delivery
mode, wymagającą uzgodnienia; nie jest ukrytym fallbackiem tego planu.

Job ma feature-owned kroki `RESOLVE_SCOPE`, `PIN_REVISIONS`, `COLLECT_CHANGES`,
`PREPARE_CONTEXT`, `AI_ANALYSIS`, `VALIDATE_REPORT`. Udostępnia shared steps,
evidence, tool evidence, activity, feedback i usage oraz jawne
`consumes/producesEvidence`. Nie używa fałszywych incidentowych step codes,
żeby pokazać prompt we wspólnym panelu.

Status wykonania (`QUEUED`, `RUNNING`, `COMPLETED`, `PARTIAL`, `BLOCKED`,
`FAILED`) jest osobny od coverage/pewności treści. Brak źródła wymaganej
strony blokuje przygotowanie; brak biznesowego uzasadnienia pozwala zwrócić
raport z jawną luką. Potwierdzony brak zmian daje deterministyczny raport
`NO_CHANGES`; nieuruchomione AI nie ma zmyślonego usage.

Report store jest efemeryczny i gateway usuwa go również po błędzie.
Dlatego feature-owned listener skutecznych mutacji `report_*`, filtrowany
po feature/run, pobiera bieżący snapshot i zapisuje postęp do joba/historii.
Nie zakładamy, że gateway zwróci częściowy report po wyjątku. Checkpoint
zawiera ostatnią poprawną sekcję; timeout/błąd zachowuje ją i widoczne braki.
Nie zapisujemy drugiego ukrytego magazynu telemetryki.

Historia powstaje już jako `QUEUED`. Reuse `LocalAnalysisRunSnapshotWriter`,
feature-owned envelope i `LocalAnalysisRunOperationGuard`. Po restarcie
nie udajemy, że nieterminalny run nadal pracuje; brak durable kolejki pozostaje
widocznym przerwaniem.

Follow-up wznawia tę samą sesję i dokładne pary SHA oraz captured katalog.
Wyjaśnienie nie zmienia reportu; jego korekta wymaga wyraźnej prośby operatora.
Edycja ręczna i AI korzystają z jednej walidacji, blokady współbieżności oraz
atomicznej aktualizacji report/result/history. Ręczna zmiana nie zmienia
manifestu źródeł ani coverage faktów bez ponownej walidacji.

Eksport: jeden aktualny `version=1`, schema `tdw.release-summary-export`,
`payload.type=release-summary-analysis`,
`payload.resultContract=release-summary-result-v1`, zgodnie ze wspólnym
wzorcem koperty `schema/version/exportedAt/payload`.
Eksport nie zawiera tokenów auth, prywatnych session IDs ani uprawnień do
kontynuacji. Import jest read-only, waliduje tryb, typy, terminalność i spójność
manifestu/reportu. JSON trafia do Analysis History, a shared Share menu
eksportuje czytelny Markdown i Markdown + Meta.

### Frontend i spójność UX

Reuse `analysis-feature-aside`, `analysis-steps-panel`, wspólnego panelu
promptu, `analysis-report-panel`/`analysis-report-section-content`,
`analysis-report-meta`, `analysis-follow-up-chat`, `analysis-share-menu`
i `analysis-report-editor`. Duże tabele mają przewijanie poziome, nawigację
po sekcjach i czytelne source refs; nie dostają nowego renderer'a Markdown.

`AnalysisJobPollingService` pilnuje braku nakładających się requestów.
Zmiana aplikacji resetuje oba refy i odrzuca spóźnione wyniki lookupu.
Pola mają dostępne etykiety i obsługę klawiatury; przy zamianie stron
pokazujemy nowy kierunek porównania. Usage/cost pozostaje w shared aside.

Rejestracja obejmuje lazy route, app shell, landing, Analysis History,
restore po `localRunId` i testy mapowania feature ID. Zmiana wspólnego selecta
wymaga aktualizacji jego wszystkich obecnych konsumentów.

### Conformance delta i konsumenci

| Granica | Delta | Konsumenci/regresja |
| --- | --- | --- |
| Publiczne API | Nowy request/job/result; bez zmian incident API | Release summary BE/FE, historia, import/export, routing |
| Deterministic context | Dwa SHA, neutralny manifest repo, Liquibase closure i pełne encje | Nowy feature; istniejące tree/read adapters i ich konsumenci zachowują odczyty |
| GitLab scope | Nowy jawny wariant wielorewizyjny | `GitLabMcpTools`, `GitLabRepositoryNavigationMcpTools`, testy scope; UX Inspector zachowuje branch mode, Operational Context Assistance pinned mode |
| Ref listing i selector | Paginacja, typ refa; wspólne UI bez kopii | Flow Explorer, UI Explorer, UX Inspector, Operational Context Assistance, branch API i navigation tool |
| Operational Context tools | Opcjonalny immutable read session | Incident Analysis, Flow Explorer, Change Verification, UI Explorer, UX Inspector oraz MCP/Workbench; default behavior bez snapshotu zachowane |
| Prompt/skills/policy | Nowy workflow i GOAL_DRIVEN | Tylko Release summary; brak zmian scoringu/promptów innych feature'ów |
| Report | Feature-owned scaffold, validator i checkpoint | Neutralny kontrakt bez pól Liquibase; regresja report tools i drugiego istniejącego konsumenta |
| Persistence | Nowy V1 envelope i continuation adapter | Shared local workspace, Analysis History, sanitization |
| Graf zależności | Zgodny z istniejącymi warstwami | `PackageDependencyGuardTest`, `IntegrationPackageBoundaryTest` |

Zastany drift jest izolowany: model-facing `branchRef` istniejących tools
pozostaje testowanym istniejącym kontraktem; nie dodajemy kolejnych parametrów
scope. Nie kopiujemy incidentowych fallbacków ani wielotysięcznego page
controllera. Nie przeprojektowujemy globalnie wszystkich feature'ów.

Przed pierwszym wdrożeniem zachowujemy jeden aktualny kontrakt V1. Jeśli
wspólny branch selector/DTO zostaje zastąpiony ref selectorem, wszyscy jego
konsumenci przechodzą w tej samej zmianie; nie utrzymujemy starych eksportów
symboli, aliasów, migratorów ani dwóch równoległych implementacji. Istniejący
tryb branch-only może pozostać świadomą konfiguracją wspólnego komponentu.
Starsze lokalne dane usuwamy wyłącznie dla faktycznie zmienionego formatu,
po sprawdzeniu zakresu; nie kasujemy historii innych analiz.

### Alternatywy

- Sam prompt „przejrzyj GitLaba”: mniej kodu przygotowania, ale brak
  deterministycznego dowodu zebrania wszystkich plików. Odrzucone.
- Import Flow Explorera: pozornie szybki reuse, lecz wiąże rodzeństwo i
  przenosi jego zasady niepasujące do migracji. Odrzucone.
- Wyłącznie GitLab compare: prosty diff, ale brak gwarancji pełnej listy
  plików i domknięcia include/data. Niewystarczające.
- Uruchomienie Liquibase i odtworzenie bazy: wymaga silnika, dialektu, środowiska
  i wykonywania kodu; inny produkt niż dokumentacja zmian. Poza zakresem.
- Nowy strukturalny table tool: zmienia shared report contract, UI i eksport.
  Markdown `report_upsert_section` wystarcza; walidacja należy do feature'a.
- Pełny parser każdego dialektu SQL: duży koszt i nadal brak wiedzy
  funkcjonalnej. Zamiast tego komplet źródeł, prosta strukturalna kontrola
  znanych deklaracji i AI z jawnym statusem niezweryfikowanych fragmentów.

## Zakres

Cały pion Liquibase: wybór aplikacji/refów, kompletne pobranie, początkowy
kontekst, research AI, tabelaryczny report, jawne coverage, job/usage/evidence,
UI, historia, import/export, follow-up i warunkowa edycja raportu. Wspólne
rozszerzenia mają audyt konsumentów oraz testy regresji.

## Non-goals

- Szczegółowa analiza i implementacja trybu zmian kontraktów API.
- Wykonywanie migracji, odpytywanie bazy, modyfikowanie kodu i decyzja „go/no-go”.
- Gwarancja przewidzenia runtime DB bez środowiska i historii migracji.
- Obsługa wszystkich pluginów Liquibase i dialektów SQL bez widocznych luk.
- Osobne pary refów per repo, dowolne instancje GitLaba oraz automatyczny wybór
  zastępczych refów przy braku wskazanego.
- Ukryte obcinanie promptu, nowy tryb attachments, podział wejścia na wiele sesji.

## Ograniczenia i ryzyka

Największe ryzyka to kompletność discovery w niestandardowym układzie repo,
warunkowe dołączenia, duży materiał, różne wersjonowanie repo jednego systemu
i pozornie pewne uzasadnienia AI. Każde ma widoczny skutek w coverage albo
blokadzie przygotowania. Liczba wywołań bez budżetu oznacza potencjalnie
długi run i większy koszt, widoczne przez istniejące usage/activity.

Pinned scope nie zamienia GitLaba w kopię trwałą: usunięty/przepisany obiekt
może uniemożliwić późniejszy odczyt. Follow-up wtedy zgłasza brak zamiast
przypinać nowszy branch. Publiczny import nie odtwarza dostępu do repo.

Rollback wdrożenia: flaga feature'a wyłącza nowe starty i route/CTA, pozostawia
read-only historię; odwrócenie zmian współdzielonych wymaga spójnej zmiany
wszystkich konsumentów, bez aliasów starych formatów. Existing feature'y nie
otrzymują nowego scope/budgetu bez jawnego wyboru w swoim assemblerze.

## Kryteria akceptacji

- Jedna aplikacja i dwa typowane refy dają jednoznaczny manifest porównania
  we wszystkich objętych repozytoriach, odporny na przesunięcie branchy.
- Wszystkie relevant Liquibase files oraz zmienione zasady dołączenia są
  uwzględnione; niepełna paginacja/odczyt nie daje „brak zmian”.
- Początkowy prompt zawiera pełne wybrane encje, wszystkie zmiany i wymagane
  zależności; zbyt duże wejście jest jawnie zatrzymane.
- AI ma nieograniczoną liczbę wywołań read/report w swoim zakresie i potrafi
  przeczytać obie strony porównania bez zamiany refów.
- Każda wymagana jednostka/rozpoznany element ma szczegół w tabelach, dowód
  albo jawny brak; żadna nierozpoznana konstrukcja nie znika z coverage.
- Raport zawiera funkcjonalne wyjaśnienia z rozróżnieniem faktów i wniosków,
  a nie tylko przepisany SQL albo listę nazw.
- Raport częściowy przetrwa błąd/timeout; pełny raport, jego projekcja,
  historia, follow-up i edycja pozostają spójne.
- Shared UX, architektura, aktualny eksport V1 i wszyscy zmienieni konsumenci
  są objęci weryfikacją. Przykłady i fixtures są w całości fikcyjnym CRM.

## Kroki

Każdy krok wymaga akceptacji zgodnie z `docs/AGENTS.md`; akceptacja całego
planu może objąć wszystkie kroki. Kroki są pionowymi inkrementami, a nie
zgodą na utworzenie wszystkich DTO bez działającej ścieżki.

- [x] **1. Kontrakt, wybór aplikacji i dwóch refów.** Wprowadzić feature
  skeleton i guard, request/snapshot V1, kwalifikację aplikacji, neutralne
  paginowane listowanie branch/tag oraz ich rozwiązywanie do SHA. Wydzielić
  wspólny application select i rozwinąć ref selector z aktualizacją obecnych
  konsumentów. Wynik: formularz i start `202` pokazujące poprawny manifest
  zakresu, jeszcze bez AI. Dowód: MockMvc, adapter HTTP z encoding/paginacją,
  FE formularz/race/reset, regresja wszystkich selectów. Akceptacja: brak
  niejawnego primary-only, fallback refa i kolizji branch/tag.
  Weryfikacja 2026-10-31: testy katalogu i startu MockMvc, adapter refs oraz
  oba guardy architektury przeszły; pełny frontend 670 testów, po końcowych
  poprawkach selektorów 28 testów celowanych. Odczyt wielu repozytoriów,
  kanoniczne branch/tag, kodowanie URI i brak fallbacku są objęte testami.
  Pełna regresja pakowania pozostaje w kroku 7.
- [x] **2. Kompletne deterministic evidence Liquibase.** Dodać neutralny
  snapshot/compare nad wspólnym tree service, discovery obu stron, closure
  include/SQL/data i pełny snapshot wybranych encji Operational Context.
  Udostępnić dane w job steps/evidence. Dowód: fikcyjne fixtures CRM dla
  wszystkich przypadków macierzy poniżej, liczników coverage i pełności
  payloadów. Akceptacja: expected manifest równy otrzymanemu, brak AI przy
  niekompletnych wymaganych źródłach, potwierdzone `NO_CHANGES`.
  Weryfikacja 2026-10-31: 47 testów zbierania evidence, scope i konsumentów
  tree; dodatkowo HTTP pagination oraz source resolve. Fixture CRM obejmuje
  XML/YAML/JSON/formatted SQL, CSV i SQL pod niezmienionym changelogiem,
  nowe include, includeAll, usunięcia, rollbackSqlFile, cykle, XXE, brak pliku
  i niezgodny blob. Nieobsługiwane konstrukcje mają jawne ograniczenia.
- [ ] **3. Report-first AI i widoczny job.** Dodać wariant pinned scope obu
  rewizji, opcjonalny captured katalog w `opctx_*`, feature-owned policy,
  `GOAL_DRIVEN`, prompt, polski skill, scaffold, validator i mapper reportu.
  Włączyć context preflight i checkpoint report mutations do historii od
  `QUEUED`. Dowód: assembler bez live Copilota, allow/deny/schema, odczyt obu
  SHA, brak hard budgetu, complete/partial/timeout/report missing. Akceptacja:
  działający backendowy run z pełnym raportem i źródłami, brak final-prose
  fallbacku, częściowy zapis przetrwa błąd gatewaya.
- [ ] **4. Pełny ekran Liquibase.** Podłączyć polling, kroki, prompt,
  evidence/activity/usage, tabelaryczny raport i coverage, Share menu oraz
  routing/landing/history mapping. Dowód: FE testy API i strony, duże tabele,
  keyboard/responsive, empty/blocked/partial/error, bez overlap pollingu.
  Akceptacja: użytkownik przechodzi od trzech selektorów do czytelnego raportu
  w shared UX, bez lokalnej kopii rendererów.
- [ ] **5. Kontynuacja, edycja i przenoszenie wyniku.** Dodać live/local
  follow-up z odtworzeniem obu SHA i katalogu, report edit z revision guard,
  import/export V1 i obsługę przerwania po restarcie. Dowód: round-trip,
  read-only import, odmowa nieobsługiwanego trybu/wersji, concurrency,
  niejawne pytanie nie mutuje reportu, korekta nie gubi coverage. Akceptacja:
  spójny report/result/history i brak auth/session secrets w eksporcie.
- [ ] **6. Weryfikacja kompletności i granic.** Przeprowadzić scenariusz
  akceptacyjny CRM z niezależnie spisaną listą tabel, kolumn, indeksów,
  constraints i zmian danych; sprawdzić uzasadnienia na kodzie. Dodać
  przypadki large input/long output, unsupported SQL/plugin, brak refs,
  błędy sieci i przesunięcie branchy. Dowód: wyniki macierzy testów, pilot
  modelu oraz architecture diff. Akceptacja: żadna zgubiona pozycja nie jest
  oznaczona jako kompletna; brak wpływu nowego scope/budget na inne feature'y.
- [ ] **7. Finalna regresja, dokumentacja i rollout.** Wykonać kolejno
  `npm --prefix frontend test -- --watch=false`, `npm --prefix frontend run build`,
  `mvn -q -Pbackend-dev clean package`; `npm ci` tylko przy zmianie zależności
  lub ich braku. Uaktualnić root/lokalne AGENTS, product direction, system
  overview, key decisions, package dependencies, playbook, dokument runtime
  Release summary, mapę Operational Context tools i README. Sprawdzić brak
  starych aliasów/artefaktów zmienionych kontraktów i anonimizację diffu.
  Dowód: zielone testy, bundle/JAR, sprawdzone linki, opis flagi/rollbacku.
  Akceptacja: bieżące dokumenty opisują faktycznie dostarczony stan; plan
  otrzymuje `done` dopiero po spełnieniu wszystkich kryteriów.

## Macierz testów

| Warstwa | Minimalne scenariusze |
| --- | --- |
| GitLab ref/tree/read | Ponad 100 refów, kolejne strony, Unicode/slash/encoding, branch i tag o tej samej nazwie, wiele repo, ten sam SHA, ref przesunięty po pin, 401/403/404/429/timeout, niepełne tree, identyczny blob, dodanie/usunięcie/rename. |
| Liquibase | XML/YAML/JSON/formatted SQL, zwykły SQL/CSV dependency, nested include/includeAll, niezmieniony nowo dołączony plik, zmieniony sqlFile bez zmiany changelogu, root/kolejność/warunki, rollback, logicalFilePath, duplikaty ID w różnych plikach, cykl/brak pliku, komentarz-only, custom change, nierozwiązane properties. |
| Pełne encje | Zachowanie wszystkich pól i nested payloadów, brak top-N, deduplikacja repo bez utraty scope/provenance, jednostronne relacje z navigation, snapshot stały po edycji katalogu, brak błędnie dziedziczonego ownershipu. |
| Prompt/context | Każda jednostka i pełne encje obecne; exact prepared prompt; brak attachments; preflight zwykłego/long context i jawna blokada zbyt dużego wejścia. |
| Tools | Obie strony dostępne pod właściwymi SHA; trzeci ref repo porównania odrzucony; dodatkowy projekt przypinany osobno; grupa z konfiguracji; ponad globalne progi liczby wywołań bez odmowy; scoped report; brak shell/DB/mutation tools; default deny nieznanych nazw. |
| Raport | Nowa tabela z wieloma kolumnami, indeks złożony/unikalny/warunkowy, FK i constraints, rename/drop/alter, DML/backfill, opisy funkcjonalne i source refs; brak elementu, duplikat, złe SHA, puste uzasadnienie, literalny pipe w komórce, duży report/chunki, AI_IDENTIFIED jawnie oddzielone. |
| Job/history | 202 i localRunId, success/no changes/blocked/partial/failure, checkpoint przed timeoutem, odrzucenie równoległego chatu/edycji, restart nieterminalnego runu, przywrócenie pinned scope, read-only import, schema/resultContract/mode validation. |
| Shared FE | Wszyscy konsumenci selecta, race po zmianie systemu, brak auto-nadpisania wyboru, obsługa typu refa i „więcej”, polling retry/stop, źródła i coverage, routing/landing/history/share/editor. |
| Granice | PackageDependencyGuardTest, IntegrationPackageBoundaryTest, brak importów sibling feature'ów, brak semantyki Liquibase w platformie/report tools, żadnych rzeczywistych danych w testach/przykładach. |

## Dowód przeprowadzonej analizy

Worktree przed pracą był czysty. W tej zmianie powstają wyłącznie dokumenty
potrzeby/planu i wpis w indeksie dokumentacji; kod produktu nie jest zmieniany.

Uruchomiono baseline:

```text
mvn -q -Dtest=GitLabRepositoryToolScopeTest,GitLabRepositoryBranchServiceTest,GitLabSystemBranchesControllerTest,CopilotReportToolsTest,GitLabOpenApiEndpointSliceServiceTest test
```

Wynik: **27 testów, 0 failures, 0 errors, 0 skipped**. Ostatni z zestawów
został uruchomiony podczas początkowego rozpoznania mechanizmów, przed
doprecyzowaniem odłożenia trybu API; nie rozszerza zakresu planu. Baseline
potwierdza obecne mechanizmy, nie przyszłą kompletność feature'a.

Przy implementacji punktem odniesienia są
[playbook](../architecture/analysis-feature-delivery-playbook.md),
[granice pakietów](../architecture/package-dependencies.md),
[model Operational Context](../architecture/operational-context-model-tools-and-usage.md)
oraz [zasady zatwierdzania](../AGENTS.md).
