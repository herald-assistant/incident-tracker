# Biznesowy CSV dla Delivery Complexity Assessment

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Uzytkownik chce porownywac wyniki Delivery Complexity Assessment pomiedzy
okresami oraz analizowac je dziennie i miesiecznie w Excelu. Obecny export JSON
jest kontraktem przenoszenia i importu calego runu, ale nie jest plaska tabela
biznesowa per issue.

## Proponowane rozwiazanie

Dodac feature-local, frontendowy export CSV generowany z terminalnego snapshotu
Delivery Complexity Assessment. Jeden wiersz bedzie reprezentowal jedno issue,
a plik obejmie caly run niezaleznie od aktywnych filtrow UI.

CSV bedzie zawieral kolumny:

- `issueKey`, `issueUrl`, `summary`, `issueType`, `doneAt`, `teamId`,
  `teamName`, `teamFieldId`,
- `mergeRequestUrls` jako liste linkow MR z Delivery Unit rozdzielona przez
  ` | `,
- `deliveryUnitId` i `assessmentStatus`,
- `outcomeBreadth`, `domainDecisionComplexity`,
  `applicationFlowComplexity`, `boundaryAndDataComplexity`,
  `verificationStateSpace`, `implementedCompatibilityScope`,
  `parameterizationComplexity`,
- `score100`, `deliveredStoryPoints`, `pointsForAggregation`.

Oceny pozostaja ocenami Delivery Unit i sa powtarzane przy nalezacych do niej
issue. `pointsForAggregation` dostaje pelne DSP tylko w jednym wierszu jednostki:
issue z najpozniejszym `doneAt`, a przy remisie z leksykograficznie najmniejszym
`issueKey`. Pozostale wiersze maja pusta wartosc, dzieki czemu suma tej kolumny
jest zgodna z agregatem raportu bez sztucznego dzielenia DSP.

Plik bedzie uzywal separatora `;`, UTF-8 z BOM, CRLF oraz poprawnego cytowania
wartosci zawierajacych separator, cudzyslow albo nowa linie. Istniejacy export
JSON i import pozostana bez zmian.

## Baseline i conformance delta

- Poziom zmiany: L1, feature UI/report export.
- Obecny wynik: UI pokazuje i zapisuje issue oraz wspolne MR-y i ocene na
  poziomie Delivery Unit; terminalny run mozna wyeksportowac jako wersjonowany
  JSON przez Analysis History.
- Publiczne API/DTO: bez zmian.
- Jira/GitLab discovery, evidence, prompt, skill, scoring i AI runtime: bez
  zmian.
- Job state, persistence, import i wersjonowany export JSON: bez zmian.
- Shared frontend: bez zmian; CSV jest semantyka jednego feature'a i pozostaje
  w jego katalogu.
- Konsumenci: tylko ekran Delivery Complexity Assessment i jego testy.
- Znany baseline testow: celowany test Angulara przed zmiana zostal zablokowany
  przez sandbox (`Cannot read directory`, `Access is denied`), zanim
  skompilowal aplikacje; po implementacji weryfikacja zostanie powtorzona z
  wymaganym dostepem.

## Zakres

- Feature-local mapper snapshotu do wierszy CSV i generator pliku.
- Dedykowana akcja `Eksportuj CSV` przy istniejacym eksporcie JSON.
- Nazwa pliku zawierajaca projekt Jira i zakres dat.
- Testy mapowania, escapowania, wielo-issue Delivery Unit, MR links, pustych
  ocen oraz uruchomienia downloadu z ekranu.
- Aktualizacja kanonicznego opisu runtime flow o dodatkowy biznesowy export.

## Non-goals

- Backendowy endpoint CSV.
- Zmiana lub wersjonowanie istniejacego exportu/importu JSON.
- Delivery Scope Complexity CSV.
- Eksport promptu, evidence, odpowiedzi AI, usage, kosztu, opisow Jira,
  acceptance criteria, quality flags i visibility limits.
- Odtworzenie relacji konkretne issue -> MR. Z obecnego snapshotu dostepna jest
  wspolna lista MR-ow Delivery Unit i taka lista bedzie powtarzana przy issue.
- Eksport tylko aktualnie odfiltrowanych Delivery Units.

## Ograniczenia i ryzyka

- `finalScore` nie istnieje w tym feature; biznesowym wynikiem koncowym jest
  `deliveredStoryPoints`, a `score100` pozostaje dokladnym wynikiem przed
  mapowaniem na bucket DSP.
- Powtarzanych `deliveredStoryPoints` nie wolno sumowac per issue;
  `pointsForAggregation` jest jedyna addytywna kolumna punktowa.
- Nieocenione jednostki maja puste kolumny scoringowe, a powod rozroznia
  `assessmentStatus`.
- CSV nie jest kontraktem importu ani wersjonowanym artefaktem aplikacji.

## Kryteria akceptacji

- Terminalny raport ma osobna, czytelnie opisana akcje pobrania CSV.
- CSV zawiera dokladnie jeden wiersz na kazde issue z pelnego runu.
- Link Jira pochodzi z `issueUrl`, a `mergeRequestUrls` zawiera wszystkie
  niepuste `webUrl` wspolnej Delivery Unit bez duplikatow.
- Oceny czastkowe i koncowe sa puste dla jednostek bez assessmentu.
- Dla kazdej ocenionej Delivery Unit dokladnie jeden wiersz zawiera
  `pointsForAggregation`, a suma tej kolumny jest rowna sumie DSP jednostek.
- Tekst z separatorem, cudzyslowem lub nowa linia otwiera sie jako jedna komorka
  CSV.
- Istniejacy export/import JSON dziala bez zmian.
- Celowane testy, caly frontendowy zestaw testow i produkcyjny build Angulara
  przechodza albo zastany problem srodowiskowy jest jawnie udokumentowany.

## Kroki

- [x] Krok 1: Dodac feature-local generator CSV wraz z testami mapowania,
  kotwicy `pointsForAggregation`, linkow MR, pustych ocen i escapowania; dowod:
  5 testow generatora przechodzi.
- [x] Krok 2: Dodac akcje `Eksportuj CSV` do ekranu, nazwe pliku i test
  pobierania bez zmiany istniejacego JSON exportu; dowod: 23 testy feature'a
  przechodza, w tym osobne testy CSV i dotychczasowego JSON exportu.
- [x] Krok 3: Zaktualizowac runtime flow, wykonac architecture diff oraz
  uruchomic `npm --prefix frontend test -- --watch=false` i
  `npm --prefix frontend run build`; dowod: 459 testow Angulara przechodzi,
  produkcyjny build przechodzi i wygenerowal aktualny bundle pod
  `src/main/resources/static`; diff nie dodaje zaleznosci do shared, backendu
  ani sibling feature'ow.
