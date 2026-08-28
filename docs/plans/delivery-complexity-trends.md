# Delivery Complexity Trends z wielu CSV

Status: done

Source need: [Delivery Complexity Trends - analiza trendow z CSV](../needs/delivery-complexity-trends.md)

## Inkrement 2026-08-28: filtr typu issue

Poziom zmiany: L1. Rozszerzenie dotyczy lokalnego kontraktu filtrow i
interpretacji juz importowanego `issueType`; nie zmienia CSV, scoringu, API ani
backendu.

Baseline: znormalizowany wiersz zachowuje `issueType`, ale Delivery Unit nie
buduje katalogu typow, agregacja nie potrafi po nim filtrowac, a UI udostepnia
tylko zespol, autora MR i daty. Delta doda wielokrotny filtr z semantyka OR:
jednostka pasuje, jezeli co najmniej jedno nalezace do niej issue ma wybrany
typ. Do wyniku nadal trafia cala zlozonosc Delivery Unit tylko raz. Wynikow
osobnych filtrow typow nie wolno sumowac, bo jedna jednostka moze zawierac
kilka typow issue.

Konsumentami sa feature-local model filtrow i widoku, skladanie Delivery Unit,
agregacja, panel filtrow, testy oraz dokumentacja need/runtime. Import,
deduplikacja `issueKey`, wybor kotwicy punktowej, trendy wymiarow, oba sibling
assessmenty i neutralny parser CSV pozostaja bez zmian.

Macierz weryfikacji obejmuje dopasowanie typu z niekotwiczacego issue, OR dla
wielu zaznaczen, jednokrotne policzenie jednostki wielotypowej, katalog opcji z
liczba DU, reset filtrow, komunikat o nakladaniu typow, dostepne przyciski
`aria-pressed`, pelne testy frontendu, produkcyjny build i kontrole granic.

## Inkrement 2026-08-27: granulacja tygodniowa

Poziom zmiany: L1. Uzytkownik rozszerza istniejacy filtr okresu o tydzien.

Baseline: model, agregacja, UI i dokumentacja obsluguja `DAY`, `MONTH` oraz
`QUARTER`. Delta dodaje `WEEK` jako tydzien ISO od poniedzialku do niedzieli,
z ISO week-year na granicy roku. Konsumentami sa typ filtra, funkcje klucza i
etykiety okresu, select strony, testy agregacji/komponentu oraz kanoniczny opis
runtime. Import, deduplikacja, addytywnosc Delivery Unit, CSV i granice sibling
feature'ow pozostaja bez zmian.

Macierz weryfikacji obejmuje wspolny tydzien, rozne tygodnie, granice ISO roku,
widoczna opcje filtra, pelny zestaw testow frontendu i produkcyjny build.

## Inkrement 2026-08-28: trendy ocen czastkowych

Poziom zmiany: L1. Rozszerzenie dotyczy interpretacji istniejacych kolumn CSV
i nowej sekcji raportu; nie zmienia scoringu, eksportu CSV ani API.

Baseline: import rozpoznaje kolumny wymiarow obu assessmentow, ale po walidacji
ich nie zachowuje. Agregacja pokazuje tylko wynik koncowy Delivery Unit. Delta
zachowa wartosci wymiarow na znormalizowanym wierszu, wybierze je z tego samego
wiersza jednostki co wynik punktowy i zagreguje po tych samych filtrach oraz
okresach. Konsumentami sa feature-local modele importu, deduplikacja,
agregacja, strona, testy i runtime flow. Neutralny parser CSV, sibling
assessmenty, ich eksporty i scoring pozostaja bez zmian.

Semantyka zrodel:

- Delivery Scope Complexity: `*Points` sa addytywnym rozkladem `finalScore`,
  dlatego widok laczny moze byc dokladnym wykresem skumulowanym,
- Delivery Complexity Assessment: wymiary `0-4` beda pokazane jako sredni
  profil jednostki, a widok laczny uzyje ich wag `10/20/20/15/10/10/15` do
  pokazania wkladu do `score100`; nie bedzie udawal rozkladu progowego DSP.

Macierz weryfikacji obejmuje parsing i deduplikacje wymiarow, addytywny rozklad
Scope, wazony wklad i srednie `0-4` Assessment, filtry/okresy, najwieksza zmiane
wymiaru, przelacznik widoku, dostepna reprezentacje danych, pelne testy
frontendu i produkcyjny build.

## Potrzeba / dlaczego

Biznesowe CSV obu assessmentow pozwalaja obrabiac pojedynczy run w Excelu, ale
aplikacja nie potrafi obecnie polaczyc raportow z wielu okresow ani pokazac
zmiany dostarczonej zlozonosci dzien do dnia, tydzien do tygodnia, miesiac do
miesiaca lub kwartal do kwartalu. Uzytkownik potrzebuje lokalnego,
uruchamianego na zadanie dashboardu z filtrami zespolu, autora MR i dat.

## Poziom zmiany

L2 - przekrojowy. Zakres obejmuje nowy frontendowy feature, zmiane
niewersjonowanych CSV dwoch istniejacych feature'ow oraz neutralny parser CSV w
`core`. Wymaga audytu wszystkich konsumentow wspolnego helpera i regresji obu
assessmentow.

## Proponowane rozwiazanie

Dodac frontendowy feature `Delivery Complexity Trends` pod route
`/delivery-complexity-trends`. Strona bedzie przyjmowala wielokrotny wybor
plikow `.csv`, przetwarzala je tylko w przegladarce i po poprawnej walidacji
zastepowala nimi aktualny zestaw danych.

### Kontrakt plikow i autorzy

Oba istniejace eksporty CSV zostana rozszerzone o dwie biznesowe kolumny:

- `mergeRequestAuthorIds`,
- `mergeRequestAuthorNames`.

Wartosci beda unikalnymi autorami MR wspolnej Delivery Unit, zapisanymi w tej
samej stabilnej kolejnosci i rozdzielonymi przez ` | `. ID bedzie kluczem
filtra, a nazwa etykieta; przy braku ID dashboard uzyje znormalizowanej nazwy
jako jawnego fallbacku. Kolumny beda powtarzane przy kazdym issue jednostki,
tak jak `mergeRequestUrls`.

Nowy feature nie zaimportuje naglowkow, modeli ani mapperow sibling feature'ow.
Bedzie mial lokalne adaptery dwoch obslugiwanych formatow, rozpoznajace typ po
zestawie naglowkow. Wspolna mechanika parsowania separatora `;`, BOM, CRLF/LF,
cytowanych komorek, escapowanych cudzyslowow i nowych linii zostanie dodana do
neutralnego `core/utils/csv-file.utils.ts` bez znajomosci assessmentow.

Starsze CSV bez kolumn autorow pozostana obslugiwane dla trendu, zespolu i dat.
Strona pokaze ograniczenie danych, a filtr autora obejmie tylko rekordy, w
ktorych metadane autora sa dostepne.

### Walidacja i deduplikacja

Import bedzie transakcyjny: nieprawidlowy lub mieszany zestaw nie podmieni
poprzednio zaladowanego widoku. Wszystkie poprawne pliki musza reprezentowac
ten sam assessment. Wymagane beda wspolne kolumny identyfikujace issue,
Delivery Unit, status i punkty oraz naglowki charakterystyczne dla wybranego
algorytmu.

Po sparsowaniu wiersze zostana znormalizowane do lokalnego modelu dashboardu i
zdeduplikowane globalnie po przycietym `issueKey`:

1. wygrywa rekord z pozniejszym prawidlowym `doneAt`,
2. przy tym samym `doneAt` wygrywa rekord z pliku wystepujacego pozniej w
   wybranej liscie,
3. dashboard pokazuje liczbe usunietych duplikatow i konfliktow wartosci.

Niepuste `issueKey`, prawidlowe `doneAt`, `deliveryUnitId` i nieujemne liczby
punktowe beda walidowane. Puste scoringi jednostek `NOT_SCORABLE`, `EXCLUDED`
lub `FAILED` pozostana danymi statusow, ale nie dodadza punktow do wykresu.

### Agregacja i filtry

Podstawowa miara bedzie zalezec od rozpoznanego pliku:

- Delivery Complexity Assessment: Delivered Story Points,
- Delivery Scope Complexity: Complexity Points.

Dashboard bedzie grupowal znormalizowane wiersze po `deliveryUnitId` i liczyl
jednostke tylko przez jej `pointsForAggregation`. Data punktow to `doneAt`
wiersza z ta wartoscia. Jezeli z powodu deduplikacji zniknie wiersz-kotwica,
dashboard uzyje jednej zgodnej, powtorzonej wartosci koncowej jednostki oraz
oznaczy fallback w sekcji jakosci danych.

Filtr zespolu lub autora bedzie dopasowywal cala Delivery Unit na podstawie
dowolnego nalezacego do niej issue albo MR. Do sumy wejdzie pelna wartosc
jednostki raz, bez dzielenia jej pomiedzy osoby lub zespoly. Zakres dat bedzie
inkluzywny i zastosowany do daty punktowej jednostki. Granulacja utworzy klucze:

- dzien: `YYYY-MM-DD`,
- tydzien ISO: `GGGG-Www`, od poniedzialku do niedzieli,
- miesiac: `YYYY-MM`,
- kwartal: `YYYY-Q1` do `YYYY-Q4`.

Data kalendarzowa bedzie pobierana z czesci `YYYY-MM-DD` oryginalnego `doneAt`,
bez przesuwania dnia przez strefe czasowa przegladarki. Delta bezwzgledna i
procentowa bedzie liczona wzgledem poprzedniego widocznego okresu z danymi.
Brakujace okresy nie zostana dopelnione zerami.

### UI

Strona bedzie zawierala:

- zwarty panel wyboru wielu CSV z lista nazw oraz akcja podmiany/wyczyszczenia
  danych,
- komunikat o lokalnym przetwarzaniu i obslugiwanym typie pliku,
- filtry `Okres: dzien / tydzien / miesiac / kwartal`, `Zespol`, `Autor MR`,
  `Od`, `Do` oraz reset,
- karty podsumowania: laczna zlozonosc, zmiana ostatniego okresu, liczba
  Delivery Units i liczba unikalnych issue,
- responsywny, przewijany wykres slupkowy bez nowej biblioteki z etykieta
  wartosci, delta i dostepnym opisem tekstowym,
- tabele szczegolow okresow z punktami, delta, procentem, liczba jednostek i
  issue,
- osobne sekcje `Najwazniejsze zmiany`, `Status ocen` oraz `Jakosc importu`,
- stale zastrzezenie, ze filtr autora pokazuje powiazanie MR z zakresem, a nie
  indywidualna produktywnosc.

Pierwszy okres i okres po wartosci zero beda mialy procent `-`, zamiast
nieskonczonej lub mylacej zmiany procentowej. Kierunek wzrost/spadek bedzie
informacyjny i nie otrzyma etykiety sukcesu lub porazki.

Route, sidebar oraz Workspace Overview otrzymaja po jednej pozycji prowadzacej
do nowego ekranu. Feature pozostanie lazy-loaded i nie bedzie wymagac backendu.

## Baseline i conformance delta

### Baseline

- Delivery Complexity Assessment i Delivery Scope Complexity maja oddzielne
  frontendowe mappers CSV, jeden wiersz per issue i wspolna liste URL-i MR per
  Delivery Unit.
- Oba snapshoty juz przechowuja `authorId` i `authorName` MR oraz uzywaja ich
  do filtrowania jednostek na ekranie pojedynczego runu.
- CSV nie zawiera autorow, modelu, effortu ani metadanych pokrycia raportu.
- `pointsForAggregation` jest jedyna addytywna kolumna issue-level i ma wartosc
  w jednym wierszu ocenionej Delivery Unit.
- `core/utils/csv-file.utils.ts` koduje i pobiera CSV, ale go nie parsuje.
- Nie istnieje import wielu biznesowych CSV, model trendu, wykres ani route
  przekrojowego dashboardu.
- Frontend nie ma zaleznosci od biblioteki wykresow.
- Ostatnia pelna weryfikacja obu eksportow: 467 testow Angulara i produkcyjny
  build przeszly.

### Conformance delta

- Cel zmiany: lokalny podglad trendow jednego rodzaju assessmentu z wielu CSV.
- Dlaczego nie wystarcza obecny mechanizm: ekrany runow i Excel nie daja
  wspolnego, filtrowalnego widoku okresow w aplikacji.
- Warstwa bedaca wlascicielem: nowy frontendowy feature; neutralne parsowanie w
  `core`; mapowanie nowych kolumn pozostaje w obu feature-owned eksportach.
- Publiczne API/DTO: bez zmian.
- Context/evidence, Jira/GitLab discovery, prompt, artifacts, skills, tools,
  policy, hidden scope, budzety i AI runtime: bez zmian.
- Report/result i scoring backendu: bez zmian.
- Job state, persistence, Analysis History, import/export JSON i continuation:
  bez zmian.
- Niewersjonowany CSV: dwie nowe kolumny autorow w obu assessmentach; pozostale
  kolumny i semantyka `pointsForAggregation` bez zmian.
- Shared FE/UX: neutralny parser CSV rozszerza istniejacy helper bez zmiany
  zachowania writera; nowy ekran ma feature-local modele, adaptery i wykres.
- Nowe zaleznosci: brak; wykres powstaje w HTML/CSS/Angular.
- Konsumenci dotknietego shared mechanizmu: generatory i testy obu CSV oraz
  nowy dashboard; shell/routes i Workspace Overview jako composition roots.
- Kompatybilnosc: istniejace CSV bez autorow sa akceptowane z ograniczonym
  filtrem osoby; mieszanie algorytmow jest odrzucane; CSV pozostaje
  niewersjonowany i nie staje sie formatem importu runu.
- Testy regresji: parser, oba generatory, adaptery formatow, deduplikacja,
  agregacja, granulacje, filtry, komponent strony, routing/nawigacja, pelny
  frontend i production build.
- Dokumentacja: oba runtime flow, nowy opis dashboardu, product direction,
  system overview i `docs/README.md`.
- Znany drift: brak wspolnego dashboardu zapisany w decyzji o izolacji obu
  assessmentow zostaje zamkniety nowym, tylko-konsumenckim ekranem; oba
  assessmenty nadal nie importuja siebie wzajemnie.

## Zakres

- Rozszerzenie obu biznesowych CSV o stabilnie sparowane ID i nazwy autorow MR.
- Neutralne, przetestowane parsowanie CSV zgodnego z aktualnym writerem.
- Feature-local rozpoznawanie i normalizacja dwoch formatow bez importu sibling
  feature'ow.
- Transakcyjny upload wielu plikow, walidacja, deduplikacja i raport jakosci.
- Agregacja Delivery Units oraz granulacje dzien, tydzien ISO, miesiac i
  kwartal.
- Filtry zespolu, autora MR i inkluzywnego zakresu dat.
- Wykres slupkowy, summary, szczegoly okresow i sekcje pomocnicze.
- Lazy route, sidebar, Workspace Overview i testy composition rootow.
- Aktualizacja dokumentacji kanonicznej oraz wygenerowanego bundle'a SPA.

## Non-goals

- Backend, zapis plikow, Analysis History, serwerowa sesja dashboardu i
  synchronizacja pomiedzy uzytkownikami.
- Wersjonowanie CSV albo zmiana wersjonowanych eksportow JSON.
- Porownanie Delivery Complexity Assessment z Delivery Scope Complexity.
- Walidacja zgodnosci modelu lub reasoning effort i dodawanie ich do CSV.
- Ranking, velocity, prognozowanie lub indywidualna atrybucja punktow.
- Dzielenie punktow jednej Delivery Unit pomiedzy zespoly, issue lub autorow.
- Dopelnianie brakujacych okresow zerami bez danych o pokryciu raportu.
- Zewnetrzna biblioteka wykresow, eksport obrazu/PDF albo ponowny eksport CSV.
- Zmiana scoringu, discovery, AI, persistence lub API obu assessmentow.

## Ograniczenia i ryzyka

- Dashboard nie moze potwierdzic zalozenia o tym samym modelu i effort, bo CSV
  celowo ich nie zawiera; UI przypomni o tym przy imporcie.
- Dwa raporty moga zawierac sprzeczne dane tego samego issue. Regula wyboru
  jest deterministyczna, ale nie rozstrzyga, ktora ocena byla biznesowo
  poprawna; konflikt pozostanie widoczny.
- Autor bez ID jest mniej stabilny, a dwie osoby o tej samej nazwie moga zostac
  polaczone. UI pokaze fallback i liczbe takich rekordow.
- Jedna jednostka moze nalezec do wielu zespolow lub autorow. Filtr obejmuje
  pelna jednostke, dlatego nie wolno interpretowac wyniku jako podzialu punktow.
- Brak okresu na wykresie oznacza brak zaimportowanych punktow, nie
  potwierdzone zero dostaw.
- Bardzo duzy zestaw jest przetwarzany w pamieci przegladarki; implementacja
  wprowadzi jawny limit liczby wierszy i lacznego rozmiaru oraz czytelny blad
  przed kosztowna agregacja.

## Kryteria akceptacji

- Nowy ekran jest dostepny z sidebaru, Workspace Overview i bezposredniego URL.
- Uzytkownik wybiera wiele CSV, a pliki nie opuszczaja przegladarki.
- Typ assessmentu jest wykrywany z naglowkow; zestaw mieszany lub bledny nie
  podmienia poprzednich danych.
- Oba nowe eksporty zawieraja unikalne ID i nazwy autorow w zgodnej kolejnosci.
- Starszy prawidlowy CSV bez autorow nadal tworzy trend i jawne ograniczenie.
- Deduplikacja `issueKey` oraz agregacja `pointsForAggregation` nie zawyzaja
  liczby issue ani zlozonosci Delivery Unit.
- Dzien, tydzien ISO, miesiac i kwartal, daty od-do oraz filtry zespolu/autora
  zwracaja wyniki zgodne z tabela szczegolow.
- Wykres pokazuje wartosci i kierunek zmiany, a brak poprzedniej podstawy nie
  tworzy blednego procentu.
- Statusy, duplikaty, konflikty, fallbacki i odrzucone dane sa widoczne w
  osobnej sekcji.
- UI jawnie odroznia filtrowanie po autorze od mierzenia produktywnosci.
- Testy wszystkich konsumentow, pelny frontendowy zestaw testow i produkcyjny
  build Angulara przechodza.
- Diff nie dodaje importow pomiedzy sibling feature'ami ani semantyki
  assessmentow do neutralnego parsera.

## Kroki

- [x] Krok 1: Rozszerzyc oba feature-local eksporty CSV o
  `mergeRequestAuthorIds` i `mergeRequestAuthorNames`, zachowac stabilne
  parowanie oraz dodac regresje istniejacych naglowkow, linkow, pustych autorow
  i wieloautorskiej Delivery Unit. Weryfikacja: celowane testy wspolnego
  writera i obu generatorow CSV przechodza w zestawie 28 testow.
- [x] Krok 2: Dodac neutralny parser CSV i feature-local pipeline importu z
  wykrywaniem formatu, transakcyjna walidacja, limitem wejscia, obsluga starszych
  plikow, deduplikacja issue oraz modelem jakosci. Weryfikacja: testy BOM,
  cudzyslowow, separatorow, nowych linii, pustych i uszkodzonych plikow,
  zestawu mieszanego, konfliktow oraz deterministycznego zwyciezcy.
  Weryfikacja: testy importu, parsera i limitow kontraktu przechodza.
- [x] Krok 3: Dodac czyste funkcje grupowania Delivery Units, filtrow zespolu,
  autora i dat oraz okresow dzien/miesiac/kwartal z delta. Weryfikacja: testy
  addytywnosci wielo-issue, wielozespolowej i wieloautorskiej jednostki,
  fallbacku kotwicy, granic dat, kwartalow i poprzedniego okresu z wartoscia
  zero. Weryfikacja: testy agregacji, addytywnosci, fallbacku, filtrow i
  granic kwartalow przechodza.
- [x] Krok 4: Zbudowac strone z uploadem, filtrami, kartami, dostepnym wykresem
  slupkowym, tabela szczegolow, zmianami, statusami i jakoscia importu.
  Weryfikacja: testy komponentu dla empty/error/loaded state, podmiany i
  czyszczenia plikow, filtrow, zgodnosci wykresu z tabela oraz komunikatu o
  nieproduktywnosciowym charakterze filtra autora. Weryfikacja: cztery testy
  komponentu przechodza; kontrola w przegladarce potwierdza zgodnosc wykresu z
  tabela, filtr zespolu, kwartaly i brak bledow konsoli.
- [x] Krok 5: Zarejestrowac lazy route, sidebar i karte Workspace Overview oraz
  zaktualizowac testy nawigacji/composition root. Weryfikacja: bezposrednia
  nawigacja renderuje ekran i zaznacza wlasciwa pozycje. Celowane testy
  composition root, shell i landing przechodza.
- [x] Krok 6: Zaktualizowac kanoniczne runtime flow obu assessmentow, dodac
  opis przeplywu dashboardu oraz wpisy w product direction, system overview i
  `docs/README.md`. Wykonac architecture diff i audyt konsumentow. Wynik:
  semantyka pozostaje feature-local, oba assessmenty zachowuja niezalezne
  mappery CSV, a neutralny parser nie zna ich naglowkow ani scoringu.
- [x] Krok 7: Uruchomic `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build`, potwierdzic aktualny bundle w
  `src/main/resources/static`, uzupelnic dowody w planie i ustawic
  `Status: done` dopiero po przejsciu wszystkich kryteriow. Wynik: 63 pliki
  testowe i 489 testow przechodza; produkcyjny build Angulara przechodzi i
  zapisuje lazy chunk `delivery-complexity-trends-page` w
  `src/main/resources/static`.
- [x] Krok 8: Dodac granulacje `WEEK` jako tydzien ISO poniedzialek-niedziela,
  opcje `Tydzien` w filtrze oraz regresje wspolnego tygodnia i granicy roku.
  Zaktualizowac need/runtime flow, uruchomic pelne testy frontendu i build, a
  nastepnie ponownie ustawic `Status: done`. Wynik: regresja laczy 31 grudnia i
  3 stycznia w `2020-W53`, rozpoczyna `2021-W01` w poniedzialek 4 stycznia,
  63 pliki testowe i 490 testow przechodza, a produkcyjny build zawiera
  zaktualizowany lazy chunk `delivery-complexity-trends-page`.
- [x] Krok 9: Zachowac czastkowe wymiary obu CSV, agregowac je raz na Delivery
  Unit i dodac sekcje `Co napedza zmiane` z widokiem lacznym, srednia na DU,
  liczebnoscia proby oraz najwieksza zmiana wymiaru. Dla Scope zachowac
  addytywny rozklad `finalScore`, a dla Assessment jawnie oddzielic srednia
  `0-4` i wazony wklad `score100` od DSP. Zaktualizowac dokumentacje, uruchomic
  pelne testy frontendu i build, a nastepnie ustawic `Status: done`.
  Wynik: wymiary sa wybierane z tej samej kotwicy punktowej co Delivery Unit,
  brakujace wartosci maja osobna liczebnosc i ostrzezenie jakosci, a wazone
  czesci DCA zachowuja precyzje do setnych. Celowane 21 testow importu,
  agregacji i strony oraz pelne 63 pliki / 494 testy przechodza. Produkcyjny
  build Angulara przechodzi i zapisuje lazy chunk
  `chunk-U2VV7T6B.js` (`delivery-complexity-trends-page`) w statycznym bundle.
  Kontrola w przegladarce potwierdza domyslny heatmap DCA, przelacznik widoku,
  skumulowane segmenty, liczebnosc DU, karte najwiekszej zmiany i brak bledow
  konsoli; regresja komponentu potwierdza domyslny widok addytywny Scope.
- [x] Krok 10: Dodac wielokrotny filtr `Typ issue` z semantyka OR na poziomie
  Delivery Unit, licznikiem jednostek wielotypowych i jawnym komunikatem, ze
  filtrowane wyniki typow moga sie nakladac. Zachowac pelna wartosc jednostki
  raz, zaktualizowac need/runtime flow, uruchomic testy celowane, pelny zestaw
  frontendu i build, a `Status: done` ustawic dopiero po ich przejsciu.
  Wynik: typy sa katalogowane ze wszystkich issue jednostki, opcje pokazuja
  liczbe DU, kilka zaznaczen dziala jako OR, a jednostka wielotypowa pozostaje
  pojedynczym wkladem. Celowane 22 testy oraz pelne 63 pliki / 495 testow
  przechodza. Produkcyjny build Angulara zapisuje lazy chunk
  `chunk-BKARX2QM.js` (`delivery-complexity-trends-page`) w statycznym bundle.
  Kontrola w przegladarce potwierdza czytelne chipy, `aria-pressed`, wynik 8 po
  jednoczesnym wybraniu `Bug` i `Story`, brak podwojenia wspolnej jednostki i
  brak bledow konsoli. `diff --check` przechodzi, a feature nadal nie importuje
  sibling assessmentow.
