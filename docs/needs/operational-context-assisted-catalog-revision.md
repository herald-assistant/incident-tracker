# Asysta AI dla spojnych zmian Operational Context

## Problem

Asysta widzi obecnie tylko fragment katalogu i kilka plikow z wybranego
repozytorium. Przy zmianie definicji terminu, dodaniu integracji albo
powiazaniu wielu systemow z kodem moze nie znac istniejacych identyfikatorow,
relacji i zasad utrzymania. Zmiany powiazanych wpisow sa zapisywane osobno,
wiec przerwanie pracy moze pozostawic uzytkownikowi niepelny zestaw.

## Oczekiwany rezultat

- Operator opisuje zamiar, a asysta rozpoznaje odpowiednie istniejace wpisy
  i proponuje zmiany w kazdym potrzebnym typie katalogu.
- AI ma dostep do calego aktualnego katalogu oraz, jesli operator wybierze
  projekt GitLab, moze doczytac istotne pliki tego projektu na zadanie.
- Propozycje sa zgodne z obowiazujacymi zasadami utrzymania, maja wskazane
  zrodla, wartosci sprzed zmiany, jawne pytania i ograniczenia widocznosci.
- Operator widzi skutki calego zestawu zmian przed zatwierdzeniem. Zatwierdzony
  zestaw nie pozostawia polowicznych powiazan, a zmiana katalogu w miedzyczasie
  zatrzymuje publikacje.
- Brak obslugi duzego kontekstu lub niedostepnosc zrodla jest pokazana jawnie;
  AI nie otrzymuje pozornie pelnego, po cichu obcietego materialu.

## Kryteria sukcesu

- Zmiana terminu, dodanie integracji miedzy istniejacymi systemami i opis
  funkcjonalnych powiazan kilku endpointow daja propozycje oparte na aktualnym
  katalogu i faktycznie przeczytanych plikach.
- ID i referencje sa walidowane wzgledem calego wynikowego katalogu.
- Przed zapisem widoczny jest diff zestawu, a publikacja jest warunkowa i
  daje jeden spojny snapshot wszystkim czytelnikom aplikacji. Po przerwaniu
  wymiany plikow start aplikacji przywraca poprzedni komplet dokumentow.
- Repozytorium i katalog pozostaja read-only dla AI; zapis wymaga jawnej
  decyzji operatora.

## Ograniczenia i non-goals

- Operational Context pozostaje indeksem wiedzy, nie inventory kodu, endpointow
  ani konfiguracji runtime.
- AI nie uzyskuje dostepu do innych projektow GitLab niz wybrany przez
  operatora i nie otrzymuje narzedzi mutacyjnych.
- Nie wprowadzamy automatycznej publikacji, workflow zespolowych akceptacji
  ani historii wersji katalogu.
