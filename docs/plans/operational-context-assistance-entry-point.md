# Wejscie do asysty AI z paska Operational Context

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

W prawym rogu paska katalogu widnieje techniczna informacja o lokalnej kopii,
a asysta AI zajmuje osobna zakladke miedzy sekcjami danych. Operator potrzebuje
jednego czytelnego przycisku uruchamiajacego widok asysty w tym miejscu.

## Baseline i conformance delta (L1)

- Baseline: `assistance` jest stanem `selectedTab`, zakladka `Asysta AI` jest
  widoczna w nawigacji, a pasek pokazuje ikone i sciezke lokalnej kopii.
  Empty state, detail drawer, findingi i Analysis History moga otworzyc ten sam
  panel. Zamontowany panel zachowuje job podczas zmiany sekcji.
- Delta: usuwamy zakladke z listy widocznych sekcji i techniczna notke z paska.
  W prawym rogu paska przycisk `Uzupelnij z AI` wybiera istniejacy stan
  `assistance`; nie zmienia requestu, joba, danych ani trybu historii.
- Konsumenci: routing historii, empty state, detail drawer, Validation i Open
  Questions nadal wybieraja `assistance`. Publiczny kontrakt i backend bez zmian.

## Proponowane rozwiazanie

Przeniesc istniejace wejscie z tab navigation do status toolbar, zachowujac
wewnetrzny identyfikator `assistance`. Wykorzystac wspolny styl przycisku,
bez tworzenia drugiego panelu.

## Zakres

Template, styl, widoczna lista sekcji, test nawigacji i dokumentacja UX.

## Non-goals

Zmiana logiki asysty, statusu katalogu, historii, edycji albo API maintenance.

## Ograniczenia i ryzyka

Deep link z historii oraz wejscia z encji i findingow musza nadal otwierac
panel. Zmiana sekcji nie moze kasowac aktywnego joba. Przycisk musi byc
dostepny rowniez przy niedostepnych metadanych maintenance, tak jak dotychczas
zakladka.

## Kryteria akceptacji

Prawa strona paska pokazuje `Uzupelnij z AI` bez ikony i informacji o lokalnej
kopii. Zakladka `Asysta AI` nie jest widoczna. Klikniecie otwiera asyste;
historia i pozostale wejscia nadal dzialaja.

## Kroki

- [x] Krok 1: Sprawdzono obecny toolbar, nawigacje, wszystkie wejscia do
  `assistance` i zachowanie zamontowanego panelu.
- [x] Krok 2: Przeniesiono wejscie do toolbar, usunieto zakladke i
  zaktualizowano test oraz trwale zasady UI. Test strony: 24/24.
- [x] Krok 3: Testy Angulara: 562/562. Build produkcyjny i `git diff --check`
  przeszly.
