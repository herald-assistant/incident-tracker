# Nawigacja po drzewie GitLab w asyscie Operational Context

Status: done

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Test operatora z 2026-09-13 wykazal, ze wybranie projektu GitLab nie daje AI
uzytecznego punktu startu w monorepo. Collector sprawdzal tylko piec plikow
w katalogu glownym; dla `unicam-group/Unicam-project` nie przeczytal zadnego.
Model nie wywolal narzedzi GitLab i zwrocil same pytania mimo dostepnych
sciezek `Backend/hackhub-backend` i `Frontend/hackhub-frontend` w katalogu.

## Baseline i zakres zmiany

Feature ma jeden wybrany projekt/ref, przypina commit i udostepnia trzy
read-only tools: `gitlab_pinned_list_files`, `gitlab_pinned_search_files` oraz
`gitlab_pinned_read_file`. GitLab adapter zwraca obecnie rekurencyjne okno
plikow bez katalogow. Parser uznaje tylko udany, pelny odczyt pliku za
cytowalne zrodlo `gitlab:`. Job moze zakonczyc sie samymi pytaniami.

Zmiana jest L2: neutralna integracja drzewa i nowy tool oraz ich uzycie w
feature-owned collectorze, prompcie, skillu i jobie. Nie zmienia zapisu YAML,
modelu katalogu ani uprawnienia modelu do projektu lub commita.

## Proponowane rozwiazanie

GitLab adapter pobiera pojedyncze strony nierekurencyjnego drzewa z katalogami
i plikami. Wspolna, ograniczona eksploracja sklada do czterech poziomow od
wskazanej sciezki przy stalych limitach wpisow i zadanych stron. Zwraca
bezpieczne `path` i `type`, jawne informacje o pominietych galeziach oraz
kursory dla niepelnych stron. Nie pobiera tresci plikow. Collector dolacza
odczyt od root do `selectedSource` w initial prompt po przypieciu commita.
Neutralny `gitlab_pinned_list_tree(path, cursor, reason)` uzywa tej samej
eksploracji i ukrytego scope'u sesji.

Drzewo jest tylko mapa nawigacji, nie refem dowodowym. Przy zadaniu opartym
na implementacji AI ma przeczytac wybrane pliki przez `gitlab_pinned_read_file`
przed propozycja. Brak wybranego projektu albo brak jakiegokolwiek odczytu
pliku musi byc jawny dla operatora i nie moze wygladac jak zakonczona analiza
kodu. Zachowujemy walidacje typowanego draftu i review przed zapisem.

## Alternatywy

Samo podanie istniejacego rekurencyjnego `list_files` w prompcie daje plaska,
potencjalnie niezrownowazona pierwsza strone monorepo. Wczytanie calego
drzewa albo tresci kodu do promptu przekroczyloby przewidywalny budzet.
Nierekurencyjna eksploracja po sciezkach pozwala pokazac rodzenstwo katalogow
przy stalej liczbie requestow i zejsc dalej kolejnym wywolaniem toola.

## Non-goals

- Skanowanie innych projektow, automatyczny wybor projektu i zapis katalogu.
- Traktowanie nazw plikow jako dowodu znaczenia biznesowego.
- Gwarancja, ze model zawsze utworzy termin bez odpowiedniego odczytu kodu.

## Ograniczenia i ryzyka

Kazdy odczyt jest przypiety do wybranego commita. Sciezka i kursor musza byc
walidowane; odpowiedz jest ograniczona pod katem liczby requestow, wpisow,
dlugosci i potencjalnie wrazliwych nazw. Czesc drzewa moze byc pominieta;
wynik oznacza niepelnosc i podaje mozliwosc dalszej eksploracji. Zmiana
allowlisty tools wymaga testu rejestracji callbackow i policy sesji.

## Kryteria akceptacji

- Wybrane monorepo bez plikow root allowlisty daje AI ograniczona mape
  katalogow i plikow do czterech poziomow z przypietego commita.
- Tool potrafi zejsc do czterech kolejnych poziomow od bezpiecznej sciezki,
  rowniez po niepelnej stronie, bez model-facing group/project/ref.
- Odczyt drzewa nie tworzy `gitlab:` source refs; tylko udany read pliku je
  tworzy. Brak odczytu jest jawny w wyniku zadania zaleznym od kodu.
- Testy adaptera, toola, allowlisty, promptu i joba pokrywaja limit,
  pagination, scope, pominiete sciezki i monorepo.

## Kroki

- [x] Dodac ograniczona eksploracje drzewa w integracji GitLab i testy HTTP.
- [x] Dodac neutralny pinned tree tool, rejestracje, budzet i testy scope'u.
- [x] Dolaczyc initial tree do promptu asysty, doprecyzowac skill i wynik przy
  braku odczytu; przetestowac job i frontendowy komunikat.
- [x] Uruchomic weryfikacje warstw, zaktualizowac dokumentacje aktualnego
  runtime i zamknac plan po spelnieniu kryteriow.

Weryfikacja: celowane testy GitLab adaptera, nawigacji, pinned toola,
collectora, promptu i joba; `npm --prefix frontend test -- --watch=false`
(553 testy), `npm --prefix frontend run build` oraz
`mvn -q -Pbackend-dev clean package`.
