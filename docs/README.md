# Dokumentacja Projektu

Ten katalog ma trzy glowne obszary:

1. `onboarding/`
   sciezka nauki aktualnego systemu krok po kroku,
2. `architecture/`
   stabilny opis architektury, decyzji i runtime flow,
3. `operational-context/`
   materialy pomocnicze do utrzymania katalogu operacyjnego.

## Od czego zaczac

Jesli dopiero wchodzisz do projektu, czytaj w tej kolejnosci:

1. `onboarding/README.md`
2. `architecture/01-system-overview.md`
3. `architecture/02-key-decisions.md`
4. `architecture/03-runtime-flow.md`
5. `architecture/04-codex-continuation-guide.md`

## Co jest gdzie

- `onboarding/`
  aktualna sciezka nauki dla mid developera, ktory zna Spring Boot, ale nie zna
  jeszcze Spring AI i Copilot SDK.
- `architecture/`
  source of truth dla architektury i zasad dalszego rozwoju.
- `operational-context/`
  prompty i instrukcje utrzymania katalogu operacyjnego.
- `../frontend`
  zrodlowy workspace Angular dla ekranu operacyjnego.
- `../src/main/resources/static`
  wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.

## Frontend workflow

- `cd ../frontend && npm start`
  Angular dev server z proxy na lokalny backend Spring Boot.
- `cd ../frontend && npm run build`
  Produkcyjny build Angulara zapisujacy `index.html`, `js`, `css` i assets do
  `../src/main/resources/static`.
- `mvn -q -DskipTests package`
  Buduje backend oraz uruchamia produkcyjny build Angulara w fazie
  `prepare-package`, a potem pakuje wynik do JAR-a.

## Jak czytac te dokumenty

- `onboarding/` odpowiada na pytanie: "jak dziala system teraz i jak sie go nauczyc?"
- `architecture/` odpowiada na pytanie: "jakie sa stale decyzje i granice odpowiedzialnosci?"
- `operational-context/` odpowiada na pytanie: "jak utrzymywac katalog operacyjny uzywany przez analize?"
