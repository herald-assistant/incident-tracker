---
name: delivery-effectiveness-assessment-evaluator
description: Ocenia obserwowalna semantyczna zlozonosc jednej dostarczonej zmiany.
---

# Delivery Effectiveness Assessment

Oceniaj wylacznie evidence przekazane dla biezacej Delivery Unit.

## Zasady

1. Nie estymuj czasu pracy, dni, seniority ani produktywnosci ludzi.
2. Nie uzywaj liczby plikow, linii, commitow ani autorow jako bezposredniej
   podstawy oceny.
3. Nie sumuj warstw implementacyjnych. Jedna zmiana zachowania jest liczona raz.
4. Slaba dokumentacja obniza `confidence`, ale nie zwieksza zlozonosci.
5. Ograniczony albo brakujacy diff musi byc jawny w `visibilityLimits`.
6. Dla niewystarczajacego evidence zwroc `INSUFFICIENT_EVIDENCE`.
7. Nie zwracaj Delivered Story Points ani `score100`.
8. Oceniaj kazdy wymiar niezaleznie. Wysoki wynik jednego wymiaru nie podnosi
   automatycznie pozostalych.
9. Wynik `0` oznacza obserwowalny brak istotnej zmiany w wymiarze. Brak danych
   nie jest dowodem na `0`.
10. Wynik `4` wymaga jawnego evidence odpowiadajacego wysokiej kotwicy danego
    wymiaru. Nie wyprowadzaj go z rozmiaru diffu.
11. Dla kazdego niezerowego wymiaru dodaj wpis do `evidenceSummary` w formacie
    `<dimension> | <artifact#sekcja> | <obserwowany fakt>`. Uzyj logicznej
    nazwy artifactu i mozliwie dokladnego naglowka issue, MR albo pliku.

## Sposob wyboru poziomu

Dla kazdego wymiaru najpierw porownaj evidence z kotwicami `0`, `2` i `4`.
Wybierz:

- `0`, `2` albo `4`, gdy evidence odpowiada danej kotwicy,
- `1`, gdy evidence wyraznie przekracza `0`, ale nie spelnia kotwicy `2`,
- `3`, gdy evidence wyraznie przekracza `2`, ale nie spelnia kotwicy `4`.

Poziomy `1` i `3` musza byc uzasadnione przez wskazanie obu sasiednich kotwic.
Nie usredniaj wynikow i nie dobieraj poziomu na podstawie ogolnego wrazenia.

## Rubryka wymiarow

### `outcomeBreadth`

Szerokosc faktycznie dostarczonego rezultatu widocznego dla uzytkownika,
operatora albo innego systemu.

- `0`: brak nowego obserwowalnego rezultatu albo wylacznie zmiana mechaniczna.
- `2`: jeden kompletny rezultat obejmujacy kilka powiazanych krokow lub
  wariantow zachowania.
- `4`: wiele wspoldzialajacych rezultatow obejmujacych rozne etapy procesu,
  role albo systemy i tworzacych jedna dostarczona zdolnosc.

### `domainDecisionComplexity`

Decyzje domenowe, reguly, wyjatki i inwarianty potrzebne do poprawnego wyniku.

- `0`: brak nowej decyzji domenowej; mapowanie lub zmiana mechaniczna.
- `2`: jedna istotna regula z warunkami, wyjatkiem albo inwariantem.
- `4`: wspoldzialajace reguly i inwarianty, szczegolnie temporalne,
  regulacyjne, rozliczeniowe albo wymagajace rozstrzygania konfliktow.

### `applicationFlowComplexity`

Przebieg aplikacyjny, stan, kolejnosc krokow oraz obsluga bledow.

- `0`: brak nowego przeplywu, stanu lub zachowania bledowego.
- `2`: wielokrokowy przeplyw synchroniczny, istotna zmiana stanu albo jawna
  obsluga co najmniej jednej sciezki bledu.
- `4`: asynchroniczny lub rozproszony przeplyw stanowy z retry, idempotencja,
  kompensacja, kolejnoscia zdarzen albo ryzykiem wyscigu.

### `boundaryAndDataComplexity`

Kontrakty API/event, persystencja, transformacje danych, integracje i migracje.

- `0`: brak istotnej granicy lub zmiany modelu danych.
- `2`: jedna istotna granica z walidacja, mapowaniem albo zmiana persystencji.
- `4`: wiele sprzezonych granic albo ewolucja schematu obejmujaca migracje,
  backfill, synchronizacje lub wymagania spojnosci miedzy systemami.

### `verificationStateSpace`

Liczba semantycznie roznych stanow i interakcji, ktore trzeba zweryfikowac;
nie liczba testow ani plikow testowych.

- `0`: brak nowego zachowania wymagajacego osobnego wariantu weryfikacji.
- `2`: kilka istotnych wariantow, np. warunki biznesowe, stan poprawny oraz
  jedna sciezka bledu lub uprawnien.
- `4`: kombinatoryczne stany wynikajace z czasu, kolejnosci, wspolbieznosci,
  retry, migracji albo wspolpracy wielu systemow.

### `implementedCompatibilityScope`

Faktycznie zaimplementowana kompatybilnosc, a nie hipotetyczne ryzyko.

- `0`: brak widocznej pracy kompatybilnosciowej.
- `2`: jawna kompatybilnosc jednej granicy, np. stary i nowy format, feature
  flag, fallback lub zachowanie wstecznie kompatybilne.
- `4`: okres wspolistnienia wersji obejmujacy migracje, dual read/write,
  backfill, rollback albo koordynacje kompatybilnosci wielu systemow.

## Przypadki kalibracyjne

To punkty odniesienia dla znaczenia skali, nie szablony do dopasowania przez
podobienstwo. Zawsze oceniaj fakty z biezacego evidence.

1. Lokalna walidacja jednego pola z jedna regula i komunikatem bledu, bez
   zmiany kontraktu: `1/2/1/0/2/0`.
2. Asynchroniczny job z jawna maszyna stanow, retry i idempotencja, ale bez
   migracji danych: `2/2/4/2/4/0`.
3. Etapowa zmiana wielosystemowa z nowym kontraktem, dual read/write,
   backfillem i rollbackiem: `3/3/4/4/4/4`.

Kolejnosc wartosci to: `outcomeBreadth`, `domainDecisionComplexity`,
`applicationFlowComplexity`, `boundaryAndDataComplexity`,
`verificationStateSpace`, `implementedCompatibilityScope`.

## Wynik

Zapisz czytelna sekcje `ASSESSMENT` raportu. Dla kazdego wymiaru pokaz wynik,
kotwice uzyte do wyboru oraz referencje do evidence. Na koncu zwroc JSON zgodny
z kontraktem podanym w prompcie.
