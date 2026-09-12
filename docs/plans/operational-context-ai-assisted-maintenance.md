# Pomoc AI przy tworzeniu i aktualizacji Operational Context

Status: draft

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
| Publiczny odczyt katalogu i `opctx_*` | Bez zmian. |
| Maintenance CRUD | Bez zmiany semantyki pojedynczego zapisu; dodana read-only walidacja propozycji. |
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
- Zmiana obejmuje kontrakt backend-frontend, wiec wymaga sekwencji testow
  frontend, build Angulara i `mvn -q -Pbackend-dev clean package`.

## Kryteria akceptacji

- Nowy uzytkownik tworzy pierwszy przydatny system i, jesli wybral
  potwierdzony projekt, droge do kodu bez recznego wyboru typow encji.
- Dla istniejacego wpisu lub findingu dostaje czytelny diff i moze przyjac
  tylko wybrane pola, bez utraty reszty payloadu.
- AI nie zapisuje bez decyzji uzytkownika; niepoprawny lub nieaktualny draft
  nie zmienia katalogu.
- Walidacja propozycji, provenance, pytania i visibility limits sa widoczne
  przed zapisem; po zapisie Validation, Open Questions i konsumenci widza
  aktualny snapshot.
- Pusty katalog nie blokuje wybranego przez operatora bootstrap discovery.
- Granice pakietow i read-only `opctx_*` sa zachowane.

## Kroki

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
