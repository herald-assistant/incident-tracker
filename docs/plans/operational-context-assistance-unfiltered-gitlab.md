# Odczyt GitLab i material asysty bez heurystycznej redakcji

Status: done

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Heurystyki nazw i tresci pomijaly cale pliki lub fragmenty kontekstu, nawet
jesli uzytkownik wybral repozytorium i potrzebowal pelnego materialu do analizy.
Uzytkownik zdecydowal o usunieciu tej ochrony rowniez ze wspolnego czytnika
GitLab. Wynik ma zachowac faktycznie odczytany tekst do sprawdzenia zrodel.

## Klasyfikacja, baseline i conformance delta

Zmiana jest L3, bo zmienia dotychczasowa granice filtrowania danych w promptach
i wspolnej integracji. Przed zmiana collector pomijal plik przy dopasowaniu
wzorca sekretu, przygotowanie promptu maskowalo opis, JSON katalogu i pliki,
parser odrzucal draft z dopasowaniem wzorca, a drzewo i wspolny czytnik GitLab
blokowaly wybrane nazwy i tresci. Po zmianie te heurystyki nie dzialaja.

Bez zmian pozostaja: skonfigurowana grupa, wybrany projekt i galaz,
przypiecie do commita, ograniczenia liczby i rozmiaru odczytow, sciezka
wzgledna bez traversal, tekstowe i kompletne body, sprawdzenie metadanych
oraz source ref tylko po pelnym odczycie. Draft nadal przechodzi walidacje
schematu, dozwolonych refs i zakresu propozycji, a zapis wymaga review.

Konsumenci wspolnego czytnika to `gitlab_read_repository_file` w sesji z
`GitLabRepositoryToolScope` oraz weryfikacja trafien
`gitlab_search_repository_files`; drzewo jest uzywane przez collector asysty i
`gitlab_list_repository_tree`. Pozostale tryby `GitLabMcpTools` nie korzystaja
z tego czytnika. Kontrakt HTTP/DTO i UI nie zmienia ksztaltu, lecz przygotowany
prompt oraz lokalna historia moga zawierac niezmienione wartosci zrodel.

## Proponowane rozwiazanie

Usunac filtry nazw/tresci z collectora, prompt preparation, parsera oraz
wspolnego drzewa i czytnika GitLab. Zachowac jeden neutralny zestaw tools;
nie dodawac wyjatku feature'owego ani nowego przelacznika. Potwierdzic testami
przejscie fikcyjnych wartosci przez initial prompt i pozniejsze odczyty oraz
odmowe odczytu z innego commita lub spoza dozwolonej sciezki.

## Zakres i non-goals

Zakres obejmuje omowione sciezki Asysty AI i wspolne tree/list/search/read
oparte na `GitLabVerifiedRepositoryFileReader`. Nie zmienia filtrow Database,
Config Drift Viewer ani niezaleznych trybow GitLab, ktore nie uzywaja tego
czytnika. Nie zmienia reguly, ze tresc repozytorium jest niezaufanym materialem
i nie moze modyfikowac instrukcji asysty.

## Ograniczenia i ryzyka

Material moze zawierac tokeny, dane kontaktowe lub inne poufne wartosci.
Backend przekazuje je dostawcy AI, pokazuje prompt w jobie i utrwala go w
lokalnej historii. Brak automatycznej klasyfikacji i redakcji jest swiadoma
decyzja operatora. Powrot do filtrow wymaga osobnej zmiany kodu; wycofanie
tej zmiany nie usuwa juz zapisanych historii ani danych przekazanych AI.

## Kryteria akceptacji

- Fikcyjna wartosc pasujaca do starego wzorca przechodzi bez zmian przez
  collector, prompt i parser oraz zweryfikowany odczyt przez tool.
- Drzewo/list/search uwzgledniaja sciezki ukryte dotad przez nazwe.
- Walidacja sciezki, tekstu, limitow i przypietego commita nadal dziala.
- Dokumentacja opisuje faktyczne przekazywanie i utrwalanie promptu.

## Kroki

- [x] Zapisac baseline, conformance delta, konsumentow i granice zmiany.
- [x] Usunac heurystyczne filtry ze wskazanych sciezek bez zmiany kontraktu API.
- [x] Zaktualizowac testy, dokumenty stanu i instrukcje lokalne.
- [x] Uruchomic testy celowane, pelny backend `mvn -q test` i sprawdzic diff.

Weryfikacja: celowane testy collectora, promptu, parsera, drzewa, czytnika i
GitLab tools przeszly. `mvn -q test` oraz `mvn -q -Pbackend-dev clean package`
zakonczyly sie powodzeniem (1569 testow, 0 failures/errors, 1 skipped).
Angular: 588 testow, 0 failures; produkcyjny build przeszedl. Diff nie ma
bledow whitespace, a testy i przyklady pozostaja w fikcyjnej domenie CRM.
