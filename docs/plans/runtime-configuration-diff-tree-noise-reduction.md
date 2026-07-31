# Uproszczenie drzewa porownania konfiguracji

Status: done

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Drzewo porownania powtarza informacje o zmianie na poziomie galezi, w licznikach
pol, legendzie i tekstowych etykietach. Utrudnia to szybkie odczytanie wartosci
source/target. Operator potrzebuje pelnego drzewa, w ktorym sygnal zmiany jest
jedna kolorowa kropka przy lisciu, a opis jest dostepny po najechaniu.

## Klasyfikacja i baseline

Poziom: L1 - zmiana feature-owned UI bez zmiany deterministic result ani API.

Pozostaja prawdziwe:

- wartosci source/target sa prezentowane zgodnie z istniejacym kontraktem,
- difference IDs nadal stanowia cele nawigacji i laczenia adnotacji,
- tryb `Zmiany` nadal pozwala ograniczyc widok do zmienionych galezi,
- stare wyniki bez projekcji plikowej zachowuja uproszczony fallback.

Konsumenci: renderer drzewa, ekran wyniku BASIC/DEEP, linkowanie findingow i
adnotacji do difference IDs oraz statyczny bundle aplikacji.

## Rozwiazanie

- domyslnie pokazac pelne, rozwiniete drzewo wraz z niezmienionymi liscmi,
- nie renderowac kardynalnosci map/list,
- renderowac kropke tylko przy lisciu: czerwone dodanie/usuniecie, pomaranczowa
  zwykla zmiana lub zmiana typu, zolta zmiana efektywna,
- usunac tekstowe etykiety i legende; znaczenie kropki udostepnic w tooltipie,
- wspolna wartosc liscia pokazac jako `source = target <wartosc>` bez kropki.

## Non-goals

- zmiana algorytmu diffu lub klasyfikacji findingow,
- zmiana wartosci przesylanych przez API,
- przebudowa sekcji adnotacji AI i fallbacku historycznego.

## Kryteria akceptacji

- w galeziach nie ma kropek ani licznikow pol/elementow,
- dodanie/usuniecie ma kropke czerwona i tooltip,
- zmiana ma kropke pomaranczowa, a efektywna zmiana zolta,
- wiersze nie zawieraja tekstowych etykiet statusu,
- niezmieniony lisc pokazuje `source = target` i wspolna wartosc bez kropki,
- testy komponentu i pelny zestaw testow frontendu przechodza.

## Kroki

- [x] Uproscic model renderowania i domyslnie pokazac pelne drzewo.
- [x] Zmienic znaczniki, tooltipy i prezentacje wartosci.
- [x] Zaktualizowac testy, runtime flow i produkcyjny bundle.
- [x] Odsunac znaczniki lisci od pionowych prowadnic bez przesuwania tresci.
- [x] Usunac techniczna ramke dokumentu dla pliku z jednym dokumentem.
- [x] Uporzadkowac pliki od konfiguracji szczegolowej do globalnej.

## Dowody weryfikacji

- testy frontendu: 223/223 zaliczone,
- produkcyjny build frontendu: zaliczony,
- architecture guard: zaliczony,
- `git diff --check`: bez bledow.
