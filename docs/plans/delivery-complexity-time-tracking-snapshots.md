# Snapshot danych czasu pracy dla obu assessmentow

Status: done

Source need: [Delivery Complexity - dane czasu pracy per issue](../needs/delivery-complexity-effort-data.md)

## Poziom zmiany

L2 - przekrojowy. Zmiana rozszerza neutralny material Jira, publiczne snapshoty
dwoch feature'ow, wersjonowane eksporty JSON oraz niewersjonowane biznesowe
CSV konsumowane przez frontend i dashboard trendow.

## Baseline

- `JiraIssueMaterial` nie zawiera danych time tracking, a adapter nie prosi o
  pola `timespent`, `timeoriginalestimate` ani `timeestimate`.
- Snapshoty issue obu assessmentow koncza sie na `doneAt` i `team`.
- Analysis History i pelny eksport JSON V1 przechowuja caly snapshot joba, ale
  nie maja danych czasu.
- Biznesowe CSV maja jeden wiersz per issue, bez danych czasu.
- Dashboard trendow rozpoznaje wymagane naglowki i ignoruje dodatkowe kolumny.
- Renderery evidence obu feature'ow wybieraja pola jawnie; time tracking nie
  jest obecnie dostepny ani potrzebny do scoringu.
- Celowane testy backendu adaptera, job state i persystencji przechodza.
  Baseline testow frontendowych zostal zablokowany przez dostep sandboxa do
  workspace'u i zostanie powtorzony w zatwierdzonym trybie.

## Conformance delta

- Neutralna integracja Jira otrzyma typowany snapshot czasu z trzema
  opcjonalnymi wartosciami sekundowymi i timestampem pobrania.
- Oba feature'y spłaszcza te dane do swoich publicznych issue response, bez
  importow pomiedzy sibling feature'ami.
- Analysis History i JSON V1 zachowaja nowe nullable pola automatycznie;
  wersja pozostaje `1`, bo zmiana jest addytywna, a stare payloady mapuja brak
  pol na `null`.
- Oba biznesowe CSV otrzymaja kolumny `timeSpentSeconds`,
  `originalEstimateSeconds`, `remainingEstimateSeconds` i
  `timeTrackingCapturedAt` bez zmiany pozostalych semantyk.
- Prompt, evidence, skill, scoring, discovery Delivery Units i UI raportu
  pozostaja bez zmian.
- Dashboard trendow ma tolerowac nowe kolumny, ale ich nie agreguje.

## Konsumenci

- neutralny Jira REST adapter i API Jira source,
- feature'y Change Verification, Delivery Complexity Assessment i Delivery
  Scope Complexity korzystajace z `JiraIssueMaterial`,
- job state, local run persister, Analysis History i eksport/import JSON obu
  assessmentow,
- frontendowe modele i generatory biznesowych CSV obu assessmentow,
- lokalny importer Delivery Complexity Trends,
- test fixtures i testy granic pakietow.

## Macierz weryfikacji

- Jira: selekcja trzech pol REST, poprawne mapowanie liczb, zachowanie `null`,
  timestamp tylko dla pobranego materialu.
- Oba job state: mapowanie czterech pol do issue response.
- AI: regresja potwierdzajaca brak nowych nazw i wartosci w evidence/prompt.
- JSON/persistence: round-trip nowych pol i odczyt starego V1 bez nich.
- CSV: kolejnosc naglowkow, wartosci, puste komorki oraz quoting Excela.
- Trends: rozszerzone CSV obu typow nadal sa rozpoznawane.
- Architektura: `PackageDependencyGuardTest` i brak sibling imports.
- Pelna granica backend-frontend: testy Angulara, produkcyjny build oraz
  `mvn -q -Pbackend-dev clean package`.

## Kroki

- [x] Krok 1: Rozszerzyc neutralny material i adapter Jira wraz z testami
  selekcji, mapowania i brakujacych wartosci. Wynik: assessment profile zawsze
  prosi o trzy pola Jira, wartosci ujemne lub nietypowane nie sa traktowane
  jako czas, a timestamp powstaje dla pobranego materialu.
- [x] Krok 2: Rozszerzyc snapshoty, persistence i eksport JSON obu assessmentow
  z kompatybilnoscia starszego V1 oraz regresja braku danych w evidence AI.
  Wynik: nullable pola sa addytywne, konstruktory kompatybilnosci zachowuja
  dotychczasowych konsumentow, a testy obu evidence packetow wykluczaja nazwy
  i wartosci time tracking.
- [x] Krok 3: Rozszerzyc frontendowe modele i oba biznesowe CSV oraz potwierdzic
  kompatybilnosc importera trendow. Wynik: cztery kolumny znajduja sie po
  `doneAt`, brak danych daje puste komorki, a importer przyjmuje nowe i starsze
  pliki. Celowane 3 pliki / 21 testow Angulara przechodza.
- [x] Krok 4: Zaktualizowac runtime docs, wykonac pelna macierz weryfikacji,
  odswiezyc bundle SPA i ustawic `Status: done` dopiero po przejsciu kontroli.
  Wynik: 63 pliki / 497 testow Angulara przechodzi, produkcyjny build odswieza
  bundle, `mvn -q -Pbackend-dev clean package` przechodzi wraz z pelnymi
  testami backendu, a `diff --check` nie wskazuje bledow whitespace.
