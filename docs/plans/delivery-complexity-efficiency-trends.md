# Efektywnosc dostarczania na ekranie Delivery Complexity Trends

Status: done

Source need: [Delivery Complexity Trends - efektywnosc dostarczania](../needs/delivery-complexity-efficiency-trends.md)

## Poziom zmiany

L1 - feature-local. Zmiana rozszerza lokalny model importu, agregacje i raport
frontendowego feature'a. Nie zmienia backendu, eksportow assessmentow ani
neutralnego parsera CSV.

## Baseline

- Ekran importuje oba biznesowe formaty i ignoruje cztery opcjonalne kolumny
  time tracking.
- Deduplikacja wybiera pozniejsze `doneAt`, potem pozniej wybrany plik i wiersz.
- Agregacja buduje Delivery Units, liczy punkty raz i stosuje filtry zespolu,
  autora, typu issue oraz dat.
- Brak modelu czasu, osobodnia, pokrycia i porownania z estimate.
- Obecne wykresy zlozonosci, wymiarow, tabele i jakosc importu sa kontraktem do
  zachowania.

## Conformance delta

- Import zachowa nullable `timeSpentSeconds`, `originalEstimateSeconds`,
  `remainingEstimateSeconds` i `timeTrackingCapturedAt`.
- Przy remisie `doneAt` nowszy timestamp time tracking wyprzedzi kolejnosc
  plikow; starszy CSV bez kolumn zachowa dotychczasowy fallback.
- Agregacja doda osobny model efektywnosci i estymacji, ale nie zmieni obecnych
  pol `AssessmentTrendView` ani ich semantyki.
- UI doda warunkowe sekcje pod obecnymi prezentacjami, pole dlugosci osobodnia
  z domyslna wartoscia 8 godzin oraz jawne liczniki pokrycia.
- Przy filtrze zespolu jednostki wielozespolowe nie beda przypisywane do
  wskaznika jednego zespolu.
- Filtr autora pozostaje filtrem zakresu MR i otrzyma jawne ograniczenie
  interpretacji w sekcji efektywnosci.

## Konsumenci

- feature-local importer i deduplikacja CSV,
- model Delivery Complexity Trends,
- agregacja Delivery Units i okresow,
- strona, HTML, style i testy komponentu,
- dokument runtime flow i testy obu aktualnych formatow CSV.

## Macierz weryfikacji

- import nowych pol, pustych komorek, blednych sekund i timestampu,
- preferowanie nowszego snapshotu czasu przy duplikacie,
- wskaznik punktow/osobodzien, delta i zmienny dzien pracy,
- strict completeness czasu na poziomie Delivery Unit,
- pokrycie punktow i mala proba,
- tribe, jednoznaczny zespol i pominiecie jednostek wspoldzielonych,
- estimate kontra wykonanie oraz remaining estimate przy Done,
- brak sekcji dla legacy CSV i brak regresji istniejacych wykresow,
- celowane i pelne testy Angulara, produkcyjny build oraz kontrola UI w
  przegladarce.

## Kroki

- [x] Krok 1: Rozszerzyc model i import wraz z kompatybilnoscia legacy oraz
  deterministyczna deduplikacja snapshotow.
- [x] Krok 2: Dodac czysta agregacje efektywnosci, estimate i quality na
  poziomie Delivery Unit i okresu.
- [x] Krok 3: Dodac warunkowa prezentacje kart, wykresu, tabeli i porownania
  estimate bez zmiany obecnych sekcji.
- [x] Krok 4: Zaktualizowac dokumentacje, wykonac macierz testow, odswiezyc
  bundle i sprawdzic UI w przegladarce przed ustawieniem `Status: done`.

## Wynik

- Import obu formatow zachowuje opcjonalne snapshoty czasu i estimate oraz
  preferuje nowszy `timeTrackingCapturedAt` przy remisie `doneAt`.
- Ekran pokazuje warunkowo punkty na osobodzien, delty, pokrycie, wielkosc
  proby, porownanie estimate z actual i liczniki jakosci.
- Filtr zespolu nie przypisuje wielozespolowej Delivery Unit do wskaznika
  jednego zespolu, a filtr autora ma jawne ograniczenie interpretacji.
- Starsze CSV bez time tracking zachowuja dotychczasowy ekran bez pustej sekcji
  efektywnosci.
- Weryfikacja: 504 testy Angulara, produkcyjny build, `FrontendPageTest` oraz
  lokalny import i kontrola UI w przegladarce bez bledow konsoli.
