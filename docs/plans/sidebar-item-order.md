# Kolejność pozycji w lewym panelu

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator może ustawić najczęściej używane ekrany wyżej w swojej nawigacji.
Przeglądarka odtwarza ustawioną kolejność po odświeżeniu strony.

## Proponowane rozwiązanie

Zmiana L2 obejmuje wspólny `AppShellComponent`. `app.routes.ts` osadza go
raz dla wszystkich ekranów. Shell jest właścicielem list pozycji, stanu
zwinięcia i aktywnego linku. Release summary ma widoczność sterowaną przez
`AppUiConfigService`; Incident Analysis i Flow Explorer korzystają z tego
samego mechanizmu otwierania nowego runu po kliknięciu aktywnego linku.
Sprawdzenie stanu wyjściowego: 25 testów `app.spec.ts` przeszło.

Użyć zainstalowanego Angular CDK Drag and Drop. Każda kategoria dostaje
własną listę z przeciąganiem całych pozycji. W rozwiniętym panelu listy
sortują się pionowo na desktopie i poziomo w układzie mobilnym. Dostępne
linki obsługują też Shift ze strzałkami zgodnymi z kierunkiem listy. Na
ekranie dotykowym przeciąganie zaczyna się po krótkim przytrzymaniu,
co pozwala przewijać listę zwykłym gestem. Tryb ikonowy na desktopie
wyświetla zapisaną kolejność i zachowuje zwarty układ.

Zapisać osobną listę stabilnych identyfikatorów dla każdej kategorii w
`localStorage`, na wzór jej preferencji zwinięcia. Wyświetlanie nadal wynika
z bieżącego katalogu pozycji i konfiguracji widoczności. Reorder aktualizuje
względną kolejność widocznych pozycji z zachowaniem miejsca pozycji chwilowo
ukrytych w pełnej kolejności.

## Zakres

- Pozycje w Analysis Features, Platform oraz Tool Workbench, w tym pozycje
  oznaczone jako „coming soon”.
- Zapis w przeglądarce, odtwarzanie oraz obsługa nieaktualnych preferencji.
- Testy `app.spec.ts`, standard UI i produkcyjny bundle Angulara.

## Non-goals

- Sortowanie kategorii, przenoszenie pozycji między nimi i synchronizacja
  między urządzeniami.
- Zmiany tras, payloadu konfiguracji i zachowania ekranów feature'ów.

## Ograniczenia i ryzyka

- Zepsuty zapis lub niedostępny storage pozwala używać nawigacji. Nowe pozycje
  trafiają na koniec spersonalizowanej listy, usunięte identyfikatory są
  pomijane, a duplikaty są deduplikowane.
- Rozpoczęcie przeciągania nie może aktywować linku. Puszczenie poza listą
  nie zapisuje preferencji.
- Regresję sprawdzić na wspólnym shellu z trasami Incident Analysis,
  Flow Explorer, Platform i Tool Workbench. Produkcyjny build ma uwzględnić
  zastane zmiany źródłowe Workspace Overview bez ręcznej edycji bundla.

## Kryteria akceptacji

- Po przełożeniu pozycji widać nową kolejność w tej kategorii. Pozostałe
  kategorie zachowują swoją kolejność.
- Po odtworzeniu shella ustawiona kolejność jest widoczna również w railu.
  Pozycja ukryta konfiguracją wraca na zapisaną pozycję po ponownym włączeniu.
- Kliknięcie linku i zwijanie panelu działają również po sortowaniu.

## Kroki

- [x] Wdrożyć sortowanie, obsługę klawiatury i zapis w `AppShellComponent`.
  Dodać przypadki integracyjne dla zmiany i odtworzenia kolejności, odrzucenia
  dropu poza kategorią, starych danych oraz flagi Release summary. Sprawdzić
  nawigację i zwijanie w istniejących testach, wykonać interakcję w przeglądarce
  w układzie desktopowym i wąskim, uruchomić pełny zestaw testów Angulara i
  produkcyjny build. Zapisać wynikowe zachowanie w standardzie UI i sprawdzić
  diff pod kątem zakresu.
  Przeszło 809 testów Angulara, produkcyjny build i `git diff --check`.
  W Chrome potwierdzono przeciąganie bezpośrednio za nazwę w układzie
  pionowym i poziomym, zwykłe kliknięcie linku, odtworzenie po odświeżeniu
  oraz zmianę kolejności skrótem Shift ze strzałką.
