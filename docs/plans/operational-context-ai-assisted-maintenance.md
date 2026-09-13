# Pomoc AI przy tworzeniu i aktualizacji Operational Context

Status: done

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Pusty katalog wymaga dzis od operatora recznego wyboru typow i kolejnosci
wpisow. Istniejace findings wskazuja problemy, ale nie pomagaja zamienic
wiedzy uzytkownika i wybranych zrodel na bezpieczna poprawke. Celem jest
skrocenie drogi od pytania uzytkownika do malego, uzytecznego i sprawdzonego
fragmentu katalogu.

## Klasyfikacja i baseline

Proponowany pierwszy przyrost to L2: nowy przeplyw AI i kontrakt HTTP/UI nad
reusable katalogiem, istniejacymi integracjami i platforma AI. Zmiana modelu
storage albo atomowy zapis wielu dokumentow podnioslyby zakres do L3 i nie
naleza do tego przyrostu.

Zatwierdzony krok przygotowawczy upraszcza istniejacy kontrakt L2 przed
projektowaniem propozycji AI. `handoff-rule.confidence`, `affectedSystems`,
`affectedProcesses` i `affectedIntegrations` byly edytowalne przed krokiem 0,
lecz nie byly odczytywane do runtime DTO ani do `opctx_get_entity`.
`affected*` dubluja
powiazania utrzymywane w `references`. Biezacy bundled seed i lokalna kopia nie
zawieraja tych pol, ale starsze lokalne katalogi moga je miec. Reguly
`useWhen`, `doNotUseWhen`, `requiredEvidence`, `expectedFirstAction`,
`references`, `notes`, `llmToolHints` i `limitations` pozostaja poza zakresem
redukcji.

Zatwierdzony kolejny krok przygotowawczy usuwa `process.operationalOutcome`
oraz `integration.dataSensitivity`. Oba pola byly edytowalne, lecz nie byly
projektowane do runtime DTO ani do `opctx_get_entity`. Usuwamy je z
kanonicznego schematu, formularza i instrukcji, bez utrzymywania osobnej
listy wycofanych kluczy. Znaczenie wyniku procesu pozostaje w strukturalnych
`completionSignals` i `lifecycle`; pozostale pola procesu i integracji nie sa
objete tym krokiem.

Po sprawdzeniu kontraktu uzytkownik zatwierdzil oczyszczanie wszystkich
nieznanych pol istniejacych encji przy aktualizacji, dla kazdego typu.
Wczesniej nowe nieznane klucze najwyzszego poziomu byly odrzucane, ale
istniejace w YAML byly zachowywane. Zagniezdzone rozszerzenia takze byly
przepuszczane; teraz podlegaja jawnej walidacji znanych ksztaltow, z
wyjatkiem celowo dynamicznych nazw sygnalow.

Zatwierdzony krok 0c usunal `operational-context-index.md` z
runtime katalogu. Tekst indeksu nie trafia do `opctx_*`, evidence ani UI;
przed zmiana byl tylko kopiowany do `OperationalContextCatalog.indexDocument`
i opcjonalnie zwracany przez `OperationalContextQuery.includeIndexDocument`.
Indeks powielal dokumentacje, a jego lista plikow i targetow code-search scope
byla nieaktualna. Loader wymagal go jako jednego z dokumentow, dlatego krok
objal tez kontrakt neutralnego adaptera/read DTO i zestaw plikow (L2).
Lokalna kopia miala tresc identyczna z seedem i zostala usunieta po ponownym
porownaniu. Pozostale encje YAML, ich relacje, read API oraz formularz nie
zostaly zmienione w tym kroku.

Audyt po kroku 0c nie wykazal kolejnych istotnych wypelnionych danych do
usuniecia: lokalne dwa systemy, dwa repozytoria i dwa code-search scopes sa
wykorzystywane przez `opctx_*` lub Flow Explorer. Nieuzywane pola glowne
`openQuestions`, `tribe`, `catalogKind` i katalogowe `schemaVersion` nie sa
czytane przez codec, ale ich usuniecie ma tylko kosmetyczny efekt. Dalsza
redukcja katalogu nie jest warunkiem rozpoczecia asysty AI. Znane drifty
projekcji handoff-rule i doboru repozytoriow w incident evidence pozostaja
poza zakresem pierwszego pionowego MVP.

Obecnie `/operational-context` pokazuje summary, katalog, Signal Resolver,
Validation i Open Questions. `Add` dziala na zakladce konkretnego typu, a
`Edit` otwiera kompletny formularz. Read API i `opctx_*` sa tylko do odczytu.
Maintenance API zapisuje jeden logiczny dokument w jednej operacji, po
walidacji calego candidate catalog; ID jest niezmienne. Lokalna kopia nie ma
rewizji, historii ani rollbacku. Seed startuje z pustymi listami encji.

Konsumenci biezacego katalogu obejmuja read API i workbench, `opctx_*`,
GitLab discovery/resolvers, Incident Analysis, Flow Explorer, Config Drift
Viewer, Change Verification i UI Explorer. Propozycja nie zmienia ich
kontraktow odczytu; po kazdym zapisie nadal czytaja biezacy snapshot.

## Proponowane rozwiazanie

Na tym samym ekranie pojawia sie pomoc zadaniowa w trzech kontekstach:

1. Przy pustym lub niepelnym katalogu: `Zacznij z AI`. Uzytkownik opisuje
   obszar zwyklym jezykiem i opcjonalnie wskazuje jeden projekt GitLab/ref.
   UI pyta o kilka faktow, ktorych kod sam nie potwierdzi: nazwe logicznego
   systemu, cel, granice i ownership. AI proponuje minimalny, przydatny
   fragment katalogu. Domyslnie jest to system; repozytorium i code-search
   scope pojawiaja sie tylko przy wystarczajacym zrodle. Proces, integracja,
   bounded context lub termin powstaja jedynie wtedy, gdy wyraznie pomagaja
   odpowiedziec na podane pytanie.
2. Przy encji: `Zaproponuj uzupelnienie`. AI porownuje aktualna encje z
   informacjami podanymi przez uzytkownika i wybranym zrodlem. Pokazuje
   zmiany na poziomie pol, z wartosciami przed/po, powodem, zrodlem i
   niepewnoscia. Edytor reczny pozostaje dostepny.
3. Przy findingu lub otwartym pytaniu: `Pomoz rozwiazac`. Asystent dostaje
   konkretny target i proponuje tylko zwiazana z nim poprawke lub pytanie
   do osoby, ktora zna fakt. Nie oznacza pytania jako rozwiazanego bez zmiany
   kanonicznego zrodla.

UI prowadzi przez `Zbierz informacje -> Sprawdz propozycje -> Zapisz i
kontynuuj`. Jezyk ekranu opisuje zadania, a pola techniczne sa w szczegolach
propozycji i w istniejacym edytorze. Reczny edytor pokazuje najpierw pola
podstawowe, a zaawansowane sekcje rozwija na zadanie; sugestia z inboxu
prowadzi do wlasciwego pola. Review tlumaczy skutek zapisu jezykiem zadania,
np. czy po zmianie da sie przejsc od systemu do kodu. Bez lokalnego hero i
bez wymagania znajomosci promptow lub tools. Sugestie sa kolejka malych operacji. Uzytkownik
moze przyjac, poprawic albo pominac kazda z nich. Zapis kazdej przyjetej
encji odbywa sie osobno przez obecna sciezke maintenance; postep i bledy sa
jawne, a po kazdym zapisie UI odswieza aktualny katalog.

Wynik AI ma typowany kontrakt draftu, nie dowolny YAML ani automatyczne
mutation tool: proponowana operacja, typ i ID, proponowane pola, uzasadnienie,
source refs, poziom pewnosci, pytania do potwierdzenia oraz ograniczenia
widocznosci. Backend odrzuca pola poza kanonicznym schematem i read
projections. Dla update'u stosuje sie tylko jawnie zaakceptowane pola do
swiezo odczytanej encji, zachowujac pozostale dane. Kandydat jest
sprawdzany bez zapisu przed pokazaniem akcji `Zapisz`; bledy sa pokazane
przy propozycji. Przed zapisem trzeba ponownie porownac dotkniete pola z
aktualna encja, aby nie nadpisac pozniejszej edycji z innej karty.

Selected-source collector jest read-only i ograniczony do jawnie wskazanego
projektu/ref oraz limitu plikow i znakow. Musi dzialac, gdy Operational
Context jest pusty; nie moze polegac na code-search scope z katalogu.
Pierwszy przyrost wykorzysta opis uzytkownika i najwyzej jeden wskazany
projekt GitLab z refem w skonfigurowanej grupie. Confluence pozostaje poza
tym przyrostem. Obecne `listRepositoryFiles` pobiera cale drzewo, a
`readFile(maxCharacters)` skraca tresc dopiero po jej pobraniu; nie spelniaja
same wymagania ograniczonego odczytu. Collector bedzie czytac tylko mala
allowliste dokladnych sciezek po sprawdzeniu rozmiaru i przypieciu commita;
brak metadanych rozmiaru lub przekroczony limit pomija plik. Przygotowany
material do AI nie zawiera sekretow ani calych nieograniczonych repozytoriow;
tresc zrodel jest traktowana jako dane, a nie instrukcje dla modelu. Opis
operatora i fragmenty kodu sa sanitizowane przed wyslaniem do AI; niepewny
fragment jest pomijany z jawnym ograniczeniem widocznosci.

AI orchestration, prompt, polski runtime skill, wynik i budzet naleza do
dedykowanego use case'u w `features.*`. Uzywaja neutralnego `aiplatform`,
`integrations.operationalcontext` i wybranych read-only adapterow. Nowe
endpointy asysty sa feature-owned, nawet jezeli sluza temu samemu ekranowi.
Neutralne `opctx_*` pozostaja read-only i nie otrzymuja semantyki zapisu.
Asynchroniczny run pokazuje status, uzyte zrodla, widoczne ograniczenia i
usage/cost zgodnie ze wspolnym wzorcem UI.

## Kontrakt pierwszego MVP i baseline (Krok 1)

Trzy zadania operatora pozostaja osobnymi trybami jednego flow:

| Tryb | Wejscie | Oczekiwany draft |
| --- | --- | --- |
| `CREATE_AREA` | Opis obszaru; opcjonalnie jeden wybrany projekt GitLab i ref. Nie wymaga istniejacego katalogu. | Najpierw system z jawnie nieznanym ownerem. Repozytorium i systemowy code-search scope tylko po potwierdzeniu relacji do wybranego projektu. |
| `IMPROVE_ENTITY` | Typ i ID istniejacej encji, opis brakujacego faktu, opcjonalne zrodlo. | Zmiany konkretnych pol z wartoscia przed/po; pozostale pola nie sa regenerowane. |
| `RESOLVE_FINDING` | ID findingu Validation albo Open Question oraz powiazana encja, opis lub zrodlo. | Tylko poprawka wynikajaca z evidence albo pytanie do czlowieka. Samo AI nie oznacza findingu jako rozwiazanego. |

Obecnie maintenance przyjmuje pelny payload encji i atomowo wymienia jeden
dokument YAML po walidacji calego katalogu. Nie ma endpointu walidacji
kandydata bez zapisu ani precondition chroniacego przed aktualizacja innej
karty. Katalog udostepnia wewnetrzny `contentDigest` snapshotu, ale obecny
PUT nie sprawdza go. Nowy feature nie importuje sibling feature'ow i nie
przenosi job flow, promptu, parsera ani policy do `integrations`, `agenttools`
lub `aiplatform`.

Lista konsumentow i zasieg zmiany:

| Konsument | Wplyw MVP |
| --- | --- |
| Workbench `/operational-context` | Nowe wejscia do asysty, status runu i review propozycji; reczny edytor pozostaje dostepny. |
| `api.operationalcontext` i `integrations.operationalcontext` | Odczyt bez zmian; pozniejsze read-only preview i atomowy warunek update'u wspoldziela walidacje z obecnym maintenance. |
| `integrations.gitlab` | Read-only, wybrany projekt/ref i ograniczony body; bez zaleznosci od istniejacego code-search scope. |
| `opctx_*`, GitLab discovery, Incident Analysis, Flow Explorer, Config Drift Viewer, Change Verification, UI Explorer | Bez zmiany kontraktu; po zaakceptowanym zapisie widza zwykly nowy snapshot katalogu. |
| `aiplatform` i shared run UI | Reuse sesji, activity, krokow i usage; bez semantyki Operational Context w platformie. |

Planowany feature-owned `POST /api/operational-context/assistance/jobs`
zwraca `202` i `jobId`; `GET /api/operational-context/assistance/jobs/{jobId}`
zwraca snapshot runu. Request ma zamkniety `mode`, wymagany opis operatora
(maksymalnie 4000 znakow), opcjonalny `target` z rodzajem `ENTITY`,
`VALIDATION_FINDING` albo `OPEN_QUESTION`, opcjonalny `gitLabSource` z
`project` (wybor z katalogu) albo `projectUrl` (reczny pelny URL projektu)
oraz `ref`, a takze `model`/`reasoningEffort` zgodne ze wspolnym
`AnalysisAiOptions`. Dla `CREATE_AREA` target jest pusty; dla pozostalych
trybow target musi wskazywac istniejacy wpis lub finding. Nieznane pola
requestu sa odrzucane. Grupa GitLab pochodzi z konfiguracji, a nie z
parametru modelu lub dowolnej sciezki podanej w request.

Korekta wyboru zrodla po feedbacku operatora: przy projekcie w podgrupie
reczne wpisanie sciezki wzglednej wymaga rozumienia granicy skonfigurowanej
grupy. Reczny wariant przyjmuje zatem pelny URL projektu, sprawdzony po
stronie serwera wobec skonfigurowanego base URL i glownej grupy. Z URL
wyprowadzamy wzgledna sciezke do odczytu GitLab oraz kanoniczne `git.group`,
`git.project`, `git.projectPath` do propozycji repozytorium w `repo-map.yml`.
Alternatywa wpisywania sciezki wzglednej pozostaje tylko dla opcji katalogowej.
Nie uruchamiamy enumeracji projektow GitLab ani odczytu dowolnej instancji.

Collector przyjmuje najwyzej jeden projekt/ref z tej grupy, przypina ref do
commita i czyta tylko istniejace dokladne sciezki z allowlisty: `README.md`,
`pom.xml`, `package.json`, `build.gradle`, `settings.gradle`. Limit to piec
plikow, 16 KiB na plik i 64 KiB lacznie. Przed odczytem sprawdza rozmiar,
a warstwa HTTP musi ograniczac rzeczywisty pobrany body; samo skrocenie
Stringa po GET nie wystarcza. Nie wolno listowac calego drzewa repozytorium
ani czytac innych projektow. Brak pliku, rozmiaru lub bezpiecznego odczytu
oznacza pominiecie tego zrodla, nie domysl. W monorepo MVP moze nie znalezc
modulu; pokazuje wtedy pytanie i limit widocznosci zamiast wymyslac scope.

Snapshot joba pokazuje `QUEUED`, `COLLECTING_CONTEXT`, `AI_PREPARATION`,
`ANALYZING` i terminalne `COMPLETED`, `PARTIAL`, `BLOCKED`, `FAILED`, a takze
kroki, czas, zrodla i ich ograniczenia, activity oraz `AnalysisAiUsage`.
`PARTIAL` oznacza uzyteczny draft mimo niedostepnego lub pominietego zrodla;
`FAILED` nie zawiera uzytecznego draftu. Run przechowuje bazowy digest
katalogu i przypiety commit GitLab do review, nie jako automatyczna zgode na
pozniejszy zapis. Sesja pierwszego MVP dostaje wstepnie zebrany material,
pusta allowliste tools i polski skill osadzony w prompcie przy wylaczonym
runtime ladowaniu skills; Copilot nie dostaje mutation tools.

Draft jest zamknieta lista malych, uporzadkowanych propozycji `CREATE` lub
`UPDATE`. Kazda zawiera `entityType`, `entityId`, zmiany pol z `path`,
`before`, `after`, krotkie `reason`, `basis` (`USER_STATEMENT`, `SOURCE_OBSERVATION`,
`AI_INTERPRETATION`), logiczne `sourceRefs`, poziom pewnosci, wymagane
potwierdzenia czlowieka, pytania i `visibilityLimits`. Backend buduje z nich
pelny `candidatePayload` i diff do UI, filtruje dane wrazliwe i odrzuca pola
poza kanonicznym schematem oraz nieznane pola odpowiedzi AI. Preview musi
uzyc tych samych regul walidacji
co zapis, ale nie moze wykonac `publishCandidate`. Przed pozniejszym PUT
backend w jednej operacji sprawdzi wartosci dotknietych pol wobec bazowej
wersji; nie wystarczy sam ponowny GET w UI. AI nie moze nadac
`ownershipStatus=explicit` ani `systemSubtype=frontend` bez jawnej decyzji
operatora. Dla frontendu kolejka najpierw zapisuje system z subtype `unknown`,
potem repozytorium i scope, a dopiero po ich walidacji pozwala potwierdzic
subtype `frontend`.

Syntetyczne przypadki kontraktu sa zapisane w
[`operational-context-ai-assisted-maintenance-fixtures.json`](operational-context-ai-assisted-maintenance-fixtures.json).
Przypadki obejmuja pusty katalog bez GitLaba, wybrany projekt z przypietym
commitem, uzupelnienie jednego pola istniejacej encji i nierozstrzygniete
pytanie po niedostepnym zrodle. Nie zawieraja danych produkcyjnych.

Macierz weryfikacji dalszych krokow: parser odrzuca nieznane pola i
niepotwierdzony ownership; collector sprawdza pojedynczy wybrany projekt,
przypiety commit, limity body, sekrety i niedostepne pliki; preview i zapis
stosuja te same reguly, a preview nie zmienia YAML; atomowy precondition
odrzuca stale propozycje; job wystawia status, `PARTIAL`, activity i usage;
UI pokazuje diff i zapisuje tylko wybrane pola po akcji uzytkownika.
Regresja L2 obejmuje konsumentow maintenance i `PackageDependencyGuardTest`;
zmiana wspolnego kontraktu UI wymaga testow Angulara, builda Angulara i
`mvn -q -Pbackend-dev clean package` w tej kolejnosci.

## Conformance delta

| Obszar | Zamierzona zmiana |
| --- | --- |
| Handoff-rule maintenance (zatwierdzony krok 0) | Usuniete cztery nieodczytywane pola z zapisu, edytora i instrukcji; starsze lokalne YAML czyszczone tylko po dry-run i jawnym `-Apply`. `references` i read API pozostaja. |
| Process/integration maintenance (zatwierdzony krok 0b) | Usuniecie `operationalOutcome` i `dataSensitivity` z kanonicznego schematu, formularza i instrukcji; bez osobnej listy dawnych kluczy. Biezace katalogi nie zawieraja tych pol; starsze wpisy sa oczyszczane przy aktualizacji encji. |
| Nieznane klucze JSON/YAML (zatwierdzony krok 0b) | Nowe klucze poza schematem sa odrzucane. Istniejace nieznane pola sa pomijane przez edytor i usuwane podczas aktualizacji encji; jawne preserve-only pola pozostaja. |
| Runtime index (zatwierdzony krok 0c) | Usuniety z seeda, loadera, codec, DTO i query. Lokalna kopia zgodna z seedem zostala usunieta po porownaniu; zmieniony plik w innej instalacji pozostanie ignorowany, bez automatycznego kasowania. Unikalne zasady jakosci danych przeniesiono do instrukcji utrzymania. |
| Publiczny odczyt katalogu i `opctx_*` | Bez zmian. |
| Maintenance CRUD | Pojedynczy zapis pozostaje atomowy. Asysta dodaje read-only preview z ta sama walidacja co commit oraz atomowy warunek zgodnosci dotknietych pol przy pozniejszym zapisie; bez mutacji w runie AI. |
| AI runtime | Feature-owned one-shot prompt, polski skill inline, scisly parser draftu, pusta allowlista tools, status/activity/usage; bez mutation tools. |
| Zrodla | Opis operatora i najwyzej jeden projekt/ref z konfigurowanej grupy GitLab; przypiety commit, allowlista pieciu sciezek, 16 KiB na plik i 64 KiB lacznie, realnie ograniczony HTTP body; bez pelnego drzewa i Confluence. |
| UI | Nowe wejscia z empty state, detail i maintenance inbox; field-level review i uproszczony domyslny widok formularza w istniejacym workbench. |
| Persistence | Brak zmiany lokalnego katalogu, brak historii i batch transaction. Draft asysty jest stanem runu, nie kanonicznym wpisem. |
| Konsumenci | Po zapisie dotychczasowy refresh i aktualny snapshot; bez migracji DTO odczytu. |

## Alternatywy

- Dalsze rozbudowywanie tooltipow i formularzy nie usuwa koniecznosci
  wyboru typu, kolejnosci i relacji przez poczatkujacego uzytkownika.
- Wolny chat bez typowanych propozycji utrudnia review, walidacje i
  wyjasnienie, co dokladnie trafi do katalogu.
- Jednym kliknieciem opublikowany komplet wielu encji wymagalby nowej
  semantyki transakcji i odzyskiwania po bledzie. To osobna decyzja L3.

## Zakres

- Usuniecie czterech nieodczytywanych pol `handoff-rule` z kanonicznego
  maintenance payloadu, formularza i instrukcji; dry-run migracji starszych
  lokalnych katalogow bez automatycznego nadpisywania danych uzytkownika.
- Usuniecie nieodczytywanych `process.operationalOutcome` i
  `integration.dataSensitivity` z maintenance payloadu, formularza i
  instrukcji; odrzucanie nowych nieznanych kluczy i usuwanie istniejacych
  nieznanych pol przy aktualizacji encji, bez listy wycofanych kluczy.
- Usuniecie nieuzywanego runtime indexu i jego pola/flag w neutralnym
  kontrakcie; zachowanie unikalnych zasad jakosci danych w instrukcjach
  utrzymania.
- Jeden pionowy przebieg pierwszego obszaru i kontekstowa poprawa wpisu.
- Typowane propozycje, zrodla, pytania, diff, walidacja bez zapisu i
  jednoznaczny zapis zaakceptowanej encji.
- Reuse obecnej lokalnej kopii, maintenance CRUD i odswiezania konsumentow.

## Non-goals

- Autonomiczne przeszukiwanie calego GitLaba, masowe generowanie wszystkich
  dziewieciu typow i kopiowanie inventory technicznego.
- Automatyczny zapis AI, mutation tools, historia wersji katalogu,
  rollback, wspoldzielone uprawnienia i approval workflow. Historia runow
  analizy jest opisana osobno w
  [planie przebiegu i historii asysty](operational-context-assistance-observability-history.md).
- Potwierdzanie ownershipu lub `systemSubtype=frontend` z nazwy projektu,
  frameworka albo samej odpowiedzi modelu.

## Ograniczenia i ryzyka

- Lokalny store nie zapewnia atomowego zapisu wielu dokumentow. Po czesciowym
  wykonaniu uzytkownik kontynuuje od ostatniej nieudanej propozycji; UI nie
  moze sugerowac, ze caly zestaw zapisano transakcyjnie.
- Propozycje moga byc niepoprawne lub nadmiernie pewne. Zrodlo, brak
  potwierdzenia i ograniczenie widocznosci sa widoczne przy kazdym polu.
- `ownershipStatus=explicit` wymaga jawnego potwierdzenia czlowieka.
  Klasyfikacja frontendu wymaga osobnego potwierdzenia zgodnego z kontraktem
  katalogu. Nieznane zostaje nieznane.
- Aktualizacja starszej encji usuwa jej nieznane rozszerzenia z YAML;
  jawnie znane pola preserve-only pozostaja zachowane.
- W kroku 0c zmienil sie wewnetrzny digest katalogu; lokalny indeks o zmienionej
  tresci pozostanie na dysku jako ignorowany plik, aby nie usuwac potencjalnych
  zmian uzytkownika. Testy musza objac start istniejacej lokalnej kopii.
- Zmiana obejmuje kontrakt backend-frontend, wiec wymaga sekwencji testow
  frontend, build Angulara i `mvn -q -Pbackend-dev clean package`.
- Pelny URL wpisany przez operatora jest niezaufanym identyfikatorem, a nie
  nowym zrodlem konfiguracji. Porownanie z `analysis.gitlab.base-url` musi
  obejmowac scheme, host, port i opcjonalny base path; sciezka musi lezec pod
  skonfigurowana glowna grupa. Odrzucamy userinfo, query, fragment i
  niejednoznacznie kodowane separatory, zanim powstanie job lub odczyt HTTP.

## Kryteria akceptacji

- Nowy uzytkownik tworzy pierwszy przydatny system i, jesli wybral
  potwierdzony projekt, droge do kodu bez recznego wyboru typow encji.
- Dla istniejacego wpisu lub findingu dostaje czytelny diff i moze przyjac
  tylko wybrane pola, bez utraty pozostalych znanych danych.
- AI nie zapisuje bez decyzji uzytkownika; niepoprawny lub nieaktualny draft
  nie zmienia katalogu.
- Walidacja propozycji, provenance, pytania i visibility limits sa widoczne
  przed zapisem; po zapisie Validation, Open Questions i konsumenci widza
  aktualny snapshot.
- Pusty katalog nie blokuje wybranego przez operatora bootstrap discovery.
- Projekt w podgrupie moze byc wskazany pelnym URL bez recznego rozbijania
  sciezki. Obcy adres jest odrzucony przed runem, a propozycja repozytorium
  otrzymuje kanoniczne `git.group`, `git.project` i `git.projectPath`.
- Granice pakietow i read-only `opctx_*` sa zachowane.

## Kroki

- [x] Krok 0 (zatwierdzony 2026-09-12): Usunac `handoff-rule.confidence` oraz
  `affectedSystems`, `affectedProcesses`, `affectedIntegrations` z kontraktu
  maintenance, edytora, promptu utrzymaniowego i field guidance. Rozszerzyc
  skrypt cleanup o deterministyczny dry-run i jawne `-Apply` dla starszych
  lokalnych katalogow; nie zmieniac `references` ani innych pol reguly.
  Dowod: test zapisu/odmowy usunietych pol, test edytora i round-trip legacy,
  probny cleanup zachowujacy inne pola, testy FE i backendu zgodne z zakresem
  wspolnym oraz brak wystapien usunietych pol w aktywnym kontrakcie.
  Weryfikacja: probny dry-run i `-Apply` na legacy YAML usunely cztery pola,
  zachowujac `references` i zagniezdzone `gaps[].confidence`; dry-run seed i
  lokalnej kopii wykazal `Changes: 0`. Testy adaptera formularza: 29/29,
  pelny zestaw FE: 507/509 (dwa timeouty), ponowne uruchomienie dwoch
  plikow: 43/43. Build Angulara i `mvn -q -Pbackend-dev clean package`
  zakonczone powodzeniem.
- [x] Krok 0b (zatwierdzony 2026-09-12): Usunac
  `process.operationalOutcome` i `integration.dataSensitivity` z kanonicznego
  schematu, edytora oraz instrukcji. Nie utrzymywac listy dawnych kluczy.
  Odrzucac nowe nieznane klucze i oczyszczac istniejace wpisy z nieznanych
  pol przy aktualizacji, we wszystkich typach, zachowujac pola jawnie
  preserve-only i dynamiczne nazwy sygnalow. Dowod: testy walidacji zapisu,
  starszego YAML i zagniezdzonych struktur, testy formularza, probny cleanup
  zachowujacy znane pola oraz weryfikacja backendu i frontendu dla zmiany
  wspolnego kontraktu. Weryfikacja: celowane testy maintenance i nested schema
  przeszly; pelny zestaw Angulara 518/518, build produkcyjny Angulara oraz
  `mvn -q -Pbackend-dev clean package` zakonczyly sie powodzeniem. Test
  starszego YAML potwierdza usuniecie nieznanych kluczy i zachowanie
  `process.outcomes`, `team.references`, `team.relations`.
- [x] Krok 0c (zatwierdzony 2026-09-13): Usunac
  `operational-context-index.md` z runtime seeda i obowiazkowego zestawu
  dokumentow; usunac `indexDocument` z `OperationalContextCatalog` i
  `includeIndexDocument` z `OperationalContextQuery` oraz dostosowac ich
  konsumentow. Przeniesc do `operational-context-maintenance` unikalne
  reguly indeksu: zachowywanie potwierdzonych faktow bez kontrdowodu, brak
  obserwacji jako brak dowodu, wymog silnych sygnalow, `gaps` tylko dla
  trwalych luk i zakaz sekretow, danych osobowych oraz produkcyjnych
  payloadow. Poprawic kanoniczna architekture i wzmianki o indeksie.
  Obecna lokalna kopie identyczna z seedem usunac dopiero po ponownym
  porownaniu; zmienionych lokalnych kopii nie kasowac automatycznie.
  Konsumenci: loader, codec, lokalny store, query/DTO i ich testy; `opctx_*`,
  evidence oraz UI nie odczytuja tekstu indeksu. Weryfikacja: test bootstrap
  nowej i istniejacej lokalnej kopii bez wymaganego indeksu, test atomowego
  zapisu YAML i odmowy wielu zmian, test adaptera/query, test guardu pakietow
  oraz `mvn -q test`. Kryterium akceptacji: brak indeksu w runtime kontrakcie
  i brak utraty obecnych encji lub zasad jakosci danych.
  Weryfikacja: testy celowane store/adapter/maintenance 40/40 oraz pelny
  backend `mvn -o -q test` zakonczyly sie powodzeniem (1388 testow,
  0 failures/errors, 1 skipped); `git diff --check` bez bledow. Test
  istniejacej lokalnej kopii potwierdza ladowanie encji i
  zachowanie zmienionego osieroconego indeksu poza snapshotem.
- [x] Krok 1 (zatwierdzony 2026-09-13): Zamknac kontrakt pionowego MVP i baseline: trzy scenariusze
  operatora, wybrane zrodla, typowany draft, status runu, granice prywatnosci,
  liste konsumentow, conformance delta i macierz testow. Dowod: review
  request/result oraz test fixtures bez danych rzeczywistych. Dowod: sekcja
  `Kontrakt pierwszego MVP i baseline` oraz cztery syntetyczne przypadki
  w pliku fixtures (poprawny JSON). Uzytkownik zatwierdzil kontrakt
  i rozpoczecie Kroku 2 odpowiedzia `ok go`.
- [x] Krok 2 (zatwierdzony 2026-09-13): Dodac ograniczony, read-only
  collector wybranego zrodla i
  feature-owned przygotowanie AI/skill oraz parsing typowanego draftu.
  Collector musi przypiac commit, sprawdzac rozmiar przed odczytem i
  ograniczac rzeczywisty HTTP body; nie uzywa pelnego drzewa GitLab.
  Dowod: test pustego katalogu, budzetu, sanitizacji, odmowy nieznanego pola,
  braku potwierdzonego ownershipu i braku mutation tools.
  Weryfikacja: collector przypina commit, sprawdza HEAD i limituje pobrany
  body, a testy adaptera obejmuja odpowiedz bez Content-Length. Polski skill
  jest osadzony inline w sesji bez tools; parser odrzuca nieznane pola,
  niedozwolone source refs, potwierdzony ownership, frontend i propozycje
  spoza trybu. Testy celowane i `mvn -q test` przeszly; raporty Surefire:
  1409 testow, 0 failures/errors, 1 skipped. `git diff --check` bez bledow.
- [x] Krok 3 (zatwierdzony 2026-09-13): Dodac async API asysty i read-only walidacje kandydata; UI
  wejscia z empty state i przeglad proponowanych encji. Dowod: MockMvc,
  testy kontraktu FE, field-level diff, zrodla i ograniczenia, identyczne
  reguly preview/commit oraz brak zapisu przed decyzja uzytkownika.
  Weryfikacja: MockMvc obejmuje start/status, niepoprawny request i nieznane
  pola; testy joba obejmuja pusty katalog, czesciowy wynik, unikalny
  fingerprint findingu, brak mutacji, redakcje publicznego activity i
  nieaktualny digest preview. Testy maintenance potwierdzaja identyczna
  decyzje preview/commit dla create/update oraz brak zmiany YAML i digesta
  przed zapisem. Draft wymaga uzasadnienia kazdego pola i odrzuca wrazliwa
  tresc modelu. UI pokazuje diff, zrodla, pytania, limity i walidacje oraz
  odzyskuje job po bledzie odczytu lub zmianie zakladki. Testy Angulara
  524/524, build produkcyjny oraz `mvn -q -Pbackend-dev clean package`
  przeszly; Surefire: 1427 testow, 0 failures/errors, 1 skipped.
  `git diff --check` bez bledow. Wejscia z detail, Validation i Open
  Questions pozostaja zakresem Kroku 5.
- [x] Krok 4: Dodac prowadzone zapisy pojedynczych, zaakceptowanych encji
  przez obecny maintenance flow z atomowym warunkiem zgodnosci dotknietych
  pol; odswiezanie i kontynuacje po bledzie. Dowod: create/update, referencje
  w kolejnosci, stale draft z innej karty, validation, otwarte pytania i brak
  czesciowego pliku. Zakonczono 2026-09-13: operator wybiera pola i jawnie
  potwierdza fakty wymagajace decyzji, po czym zapisuje lub pomija kolejna
  propozycje. Backend korzysta wylacznie z draftu przechowywanego w jobie;
  sprawdza digest przy create oraz wartosci wybranych pol przy update pod
  lockiem maintenance, waliduje caly katalog i atomowo publikuje jeden YAML.
  Konflikt nie zapisuje decyzji ani pliku; UI zachowuje wybor i odswieza job,
  a przy utraconej odpowiedzi APPLY rozpoznaje zapis przez GET bez drugiego
  POST. Po zapisie odswieza katalog, Validation i Open Questions. Test realnego
  joba i store obejmuje system -> repository -> code-search-scope z nowym
  digestem po kazdym kroku oraz odmowe scope po pominieciu repository bez
  zmiany YAML. Testy maintenance obejmuja stale create/update, niezalezne
  pola update i brak czesciowego zapisu przy walidacji. Weryfikacja: Angular
  530/530, build produkcyjny, `mvn -q -Pbackend-dev clean package` (Surefire:
  1440 testow, 0 failures/errors, 1 skipped), `git diff --check` bez bledow.
  Uproszczona korekta reczna oraz wejscia z detail, Validation i Open
  Questions pozostaja w Kroku 5.
- [x] Krok 5: Dodac wejscia z detail, Validation i Open Questions oraz
  domyslnie uproszczony formularz, regresje wszystkich konsumentow;
  zaktualizowac kanoniczna architekture,
  lokalne `AGENTS.md` i dokumentacje ekranu. Dowod: testy FE, Angular build,
  backend package, `PackageDependencyGuardTest` i scenariusze uzytkownika.
  Zakonczono 2026-09-13: detail otwiera `IMPROVE_ENTITY`, Validation i Open
  Questions otwieraja `RESOLVE_FINDING` z konkretnym targetem; pytanie bez
  encji nie uruchamia niedozwolonego joba. `Edit source` z Validation
  otwiera wskazane pole, a formularz domyslnie pokazuje podstawowe sekcje;
  zaawansowane i bledne pola mozna rozwinac bez utraty wartosci. Testy FE
  obejmuja pusty katalog, detail, finding/pytanie, przelaczenie targetu,
  focus, zachowanie wartosci i odswiezenie po zapisie. Zaktualizowano
  kanoniczna architekture, lokalne `AGENTS.md`, opis ekranu i need.
  Weryfikacja: Angular 545/545, build produkcyjny,
  `mvn -q -Pbackend-dev clean package` (Surefire: 1440 testow,
  0 failures/errors, 1 skipped), `PackageDependencyGuardTest` 4/4,
  `git diff --check` bez bledow. Pomiar uzytecznosci z operatorami
  pozostaje osobnym dzialaniem produktowym. Korekta po feedbacku operatora:
  pole projektu GitLab zastapiono jednoznacznym wyborem unikalnych projektow
  z katalogu w skonfigurowanej grupie i recznym wpisaniem projektu spoza
  podpowiedzi. UI pokazuje pochodzenie listy i nazwe grupy, backend wysyla
  projekt wzgledny wobec grupy i toleruje wklejona pelna sciezke. Weryfikacja
  po korekcie: Angular 546/546, build produkcyjny,
  `mvn -q -Pbackend-dev clean package` (Surefire: 1449 testow,
  0 failures/errors, 1 skipped), `PackageDependencyGuardTest` 4/4,
  `git diff --check` bez bledow.
- [x] Krok 6: Przy recznym wskazaniu zrodla przyjac pelny URL projektu GitLab,
  zweryfikowac jego origin, base path i przynaleznosc do skonfigurowanej
  glownej grupy, wyliczyc wzgledna sciezke do odczytu oraz kanoniczne pola
  `git` do propozycji `repo-map.yml`. Uzgodnic GitLab repository resolver z
  repozytoriami w podgrupach, aby zapisany wpis nadawal sie do code search.
  Zachowac wybor katalogowy, odrzucac
  niejednoznaczne i obce URL przed startem joba. Dowod: testy URL w podgrupie,
  poza grupa, na innym origin i z niebezpiecznym kodowaniem; testy promptu,
  kontraktu FE, Angular build, backend package i dependency guard.
  Zakonczono 2026-09-13: `projectUrl` jest walidowany przed utworzeniem joba,
  a backend wyprowadza wzgledna sciezke do odczytu GitLab oraz kanoniczne
  `git.group`, `git.project`, `git.projectPath` i `git.url` dla propozycji.
  Parser odrzuca repozytorium o tozsamosci innej niz wybrane zrodlo, a resolver
  code search uwzglednia repozytoria w podgrupach i odrzuca obca grupe.
  Weryfikacja: Angular 547/547, build produkcyjny,
  `mvn -q -Pbackend-dev clean package` (Surefire: 1460 testow,
  0 failures/errors, 1 skipped), `PackageDependencyGuardTest` 4/4,
  `git diff --check` bez bledow.

Wykonanie kazdego kroku wymaga zatwierdzenia zgodnie z `docs/AGENTS.md`.
