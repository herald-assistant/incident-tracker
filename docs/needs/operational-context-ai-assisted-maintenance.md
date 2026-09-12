# Pomoc AI przy tworzeniu i aktualizacji Operational Context

## Problem

Operational Context jest wartosciowy dopiero wtedy, gdy opisuje rzeczywisty
obszar systemu i wskazuje, gdzie zaczac dalsza analize. Lokalny seed jest pusty.
Obecny ekran pozwala edytowac dziewiec typow encji, ale wymaga od uzytkownika
znajomosci modelu katalogu, kolejnosci tworzenia wpisow, referencji i znaczenia
zaawansowanych pol. To szczegolnie utrudnia pierwszy zapis. Przy aktualizacji
uzytkownik widzi validation findings i open questions, lecz nadal musi sam
przelozyc je na konkretne, bezpieczne zmiany w formularzach.

Problem odczuwa analityk lub operator znajacy swoj obszar biznesowy, ale
nieznajacy architektury platformy ani sposobu dzialania AI. Obecny edytor jest
uzyteczny dla osoby utrzymujacej kontrakt katalogu; nie jest dobrym punktem
startowym dla tej grupy.

## Oczekiwany rezultat

- Uzytkownik zaczyna od rozpoznawalnego zadania: opisania nowego obszaru albo
  poprawienia istniejacego wpisu, findingu czy otwartego pytania.
- Podaje informacje, ktore zna, i wskazuje zrodla, do ktorych ma dostep. Nie
  musi wybierac typu encji ani samodzielnie ukladac referencji.
- Dostaje maly, uzyteczny zestaw propozycji do sprawdzenia, z wyjasnieniem
  wartosci kazdej zmiany dla nastepnej analizy, zrodlami, niepewnoscia i
  pytaniami wymagajacymi decyzji czlowieka.
- Moze poprawic, przyjac lub odrzucic poszczegolne propozycje przed zapisem.
  Po zapisie widzi aktualny stan katalogu i pozostale luki.
- Gdy zrodla sa niepelne, wynik pozostaje czesciowy. Brak danych jest
  widoczny, a nie zastepowany prawdopodobnie brzmiacym faktem.

## Kryteria sukcesu

- W tescie z osoba nieznajaca modelu katalogu da sie utworzyc pierwszy
  uzyteczny obszar bez otwierania surowych YAML-i i bez instrukcji o dziewieciu
  typach encji. Obszar wskazuje co najmniej rozpoznawalny system, a gdy
  dostepne jest potwierdzone zrodlo kodu, rowniez droge do repozytorium.
- Ten sam uzytkownik potrafi przejsc od findingu lub otwartego pytania do
  propozycji poprawki i rozumie, co zmieni sie po jej zapisaniu.
- Zadna tresc wygenerowana przez AI nie trafia do katalogu bez jawnego wyboru
  uzytkownika i walidacji. Wpis nie nabywa potwierdzonego ownershipu ani
  klasyfikacji z samej sugestii modelu.
- Przed zapisem kazdego proponowanego pola pochodzacego z zewnetrznego
  zrodla mozna odroznic obserwacje od interpretacji i wskazac zrodlo.
- Blad AI, niedostepne zrodlo lub niepoprawna propozycja nie niszcza lokalnego
  katalogu i pozostawiaja reczna edycje dostepna.

## Ograniczenia produktowe

- Katalog pozostaje curated navigation layer. Nie staje sie kopia inventory
  kodu, runtime, endpointow, tabel ani konfiguracji.
- `system` pozostaje kanonicznym bytem. Ownership moze byc potwierdzony tylko
  na systemie lub bounded context; brak potwierdzenia pozostaje jawny.
- Uzytkownik wybiera zakres czytanych zrodel. Dostep do zrodla nie oznacza, ze
  kazda wywnioskowana z niego relacja jest faktem katalogowym.
- Dane wrazliwe, sekrety i prywatne dane kontaktowe nie powinny byc
  przenoszone do katalogu ani do materialu wysylanego do AI.
- Reczna edycja oraz Validation i Open Questions pozostaja dostepne rownolegle
  z pomoca AI.

## Non-goals pierwszego przyrostu

- Automatyczne opisanie calej organizacji lub skanowanie wszystkich projektow.
- Samodzielny zapis lub masowa aktualizacja katalogu przez AI.
- Wnioskowanie potwierdzonego ownera, bounded contextu, klasyfikacji frontendu
  albo granic odpowiedzialnosci tylko z nazwy projektu czy frameworka.
- Historia wersji, rollback, wieloosobowy approval workflow i wspoldzielony
  katalog.

## Otwarte decyzje produktowe

- Czy pierwszy przyrost ma korzystac tylko z opisu uzytkownika i jednego
  wskazanego projektu GitLab, czy od razu takze ze wskazanej strony Confluence?
- Jakie trzy scenariusze uzytkownikow posluza do pomiaru czasu, zrozumienia
  proponowanych zmian i liczby odrzuconych sugestii?
