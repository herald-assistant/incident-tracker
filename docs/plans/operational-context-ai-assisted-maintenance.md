# Pomoc AI przy tworzeniu i aktualizacji Operational Context

Status: in-progress

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
Pierwszy przyrost moze wykorzystac opis uzytkownika i wskazany projekt GitLab.
Confluence jest kolejnym zrodlem dopiero po potwierdzeniu potrzeby. Przygotowany
material do AI nie zawiera sekretow ani calych nieograniczonych repozytoriow;
tresc zrodel jest traktowana jako dane, a nie instrukcje dla modelu.

AI orchestration, prompt, polski runtime skill, wynik i budzet naleza do
dedykowanego use case'u w `features.*`. Uzywaja neutralnego `aiplatform`,
`integrations.operationalcontext` i wybranych read-only adapterow. Nowe
endpointy asysty sa feature-owned, nawet jezeli sluza temu samemu ekranowi.
Neutralne `opctx_*` pozostaja read-only i nie otrzymuja semantyki zapisu.
Asynchroniczny run pokazuje status, uzyte zrodla, widoczne ograniczenia i
usage/cost zgodnie ze wspolnym wzorcem UI.

## Conformance delta

| Obszar | Zamierzona zmiana |
| --- | --- |
| Handoff-rule maintenance (zatwierdzony krok 0) | Usuniete cztery nieodczytywane pola z zapisu, edytora i instrukcji; starsze lokalne YAML czyszczone tylko po dry-run i jawnym `-Apply`. `references` i read API pozostaja. |
| Process/integration maintenance (zatwierdzony krok 0b) | Usuniecie `operationalOutcome` i `dataSensitivity` z kanonicznego schematu, formularza i instrukcji; bez osobnej listy dawnych kluczy. Biezace katalogi nie zawieraja tych pol; starsze wpisy sa oczyszczane przy aktualizacji encji. |
| Nieznane klucze JSON/YAML (zatwierdzony krok 0b) | Nowe klucze poza schematem sa odrzucane. Istniejace nieznane pola sa pomijane przez edytor i usuwane podczas aktualizacji encji; jawne preserve-only pola pozostaja. |
| Runtime index (zatwierdzony krok 0c) | Usuniety z seeda, loadera, codec, DTO i query. Lokalna kopia zgodna z seedem zostala usunieta po porownaniu; zmieniony plik w innej instalacji pozostanie ignorowany, bez automatycznego kasowania. Unikalne zasady jakosci danych przeniesiono do instrukcji utrzymania. |
| Publiczny odczyt katalogu i `opctx_*` | Bez zmian. |
| Maintenance CRUD | Pojedynczy zapis pozostaje atomowy; dodatkowo odrzuca nieznane klucze i nie zachowuje nieznanych rozszerzen przy aktualizacji. W przyszlym przeplywie asysty dodana read-only walidacja propozycji. |
| AI runtime | Nowy feature-owned prompt, skill, kontrakt draftu i ograniczony run; bez mutation tools. |
| Zrodla | Read-only, operator-selected, ograniczony bootstrap bez zaleznosci od istniejacego scope'u. |
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
- Automatyczny zapis AI, mutation tools, multi-document transaction, historia,
  rollback, wspoldzielone uprawnienia i approval workflow.
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
- [ ] Krok 1: Zamknac kontrakt pionowego MVP i baseline: trzy scenariusze
  operatora, wybrane zrodla, typowany draft, status runu, granice prywatnosci,
  liste konsumentow, conformance delta i macierz testow. Dowod: review
  request/result oraz test fixtures bez danych rzeczywistych.
- [ ] Krok 2: Dodac ograniczony, read-only collector wybranego zrodla i
  feature-owned przygotowanie AI/skill oraz parsing typowanego draftu.
  Dowod: test pustego katalogu, budzetu, sanitizacji, odmowy nieznanego pola,
  braku potwierdzonego ownershipu i braku mutation tools.
- [ ] Krok 3: Dodac async API asysty i read-only walidacje kandydata; UI
  wejscia z empty state i przeglad proponowanych encji. Dowod: MockMvc,
  testy kontraktu FE, field-level diff, zrodla i ograniczenia, brak zapisu
  przed decyzja uzytkownika.
- [ ] Krok 4: Dodac prowadzone zapisy pojedynczych, zaakceptowanych encji
  przez obecny maintenance flow; odswiezanie i kontynuacje po bledzie.
  Dowod: create/update, referencje w kolejnosci, stale draft, validation,
  otwarte pytania i brak czesciowego pliku.
- [ ] Krok 5: Dodac wejscia z detail, Validation i Open Questions oraz
  domyslnie uproszczony formularz, regresje wszystkich konsumentow;
  zaktualizowac kanoniczna architekture,
  lokalne `AGENTS.md` i dokumentacje ekranu. Dowod: testy FE, Angular build,
  backend package, `PackageDependencyGuardTest` i scenariusze uzytkownika.

Wykonanie kazdego kroku wymaga zatwierdzenia zgodnie z `docs/AGENTS.md`.
