# Dokumentacja Projektu

Ten katalog ma dwa glowne obszary:

1. `architecture/`
   stabilny opis architektury, decyzji i runtime flow,
2. `operational-context/`
   materialy pomocnicze do utrzymania katalogu operacyjnego.

Dookola tej dokumentacji sa jeszcze dwa istotne runtime obszary:

- `../src/main/resources/copilot/skills`
  skille Copilota pakowane do runtime aplikacji,
- `../src/main/resources/operational-context`
  realny katalog operacyjny czytany przez enrichment provider.

## Od czego zaczac

Jesli dopiero wchodzisz do projektu, czytaj w tej kolejnosci:

1. `architecture/00-product-direction.md`
2. `architecture/01-system-overview.md`
3. `architecture/02-key-decisions.md`
4. `architecture/03-runtime-flow.md`
5. `architecture/04-codex-continuation-guide.md`
6. `architecture/05-package-dependencies.md`
7. `architecture/06-modular-architecture-roadmap.md`

## Co jest gdzie

- `architecture/`
  source of truth dla architektury i zasad dalszego rozwoju.
- `operational-context/`
  prompty i instrukcje utrzymania katalogu operacyjnego.
- `../examples`
  bardziej rozbudowane przyklady promptow i preferowanej kolejnosci
  uzupelniania operational context.
- `../frontend`
  zrodlowy workspace Angular dla ekranu operacyjnego.
- `../src/main/resources/static`
  wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.

## Frontend workflow

- `cd ../frontend && npm start`
  Angular dev server z proxy na lokalny backend Spring Boot.
- `cd ../frontend && npm test`
  Testy UI Angulara. Nie sa uruchamiane przez `mvn test`.
- `cd ../frontend && npm run build`
  Produkcyjny build Angulara zapisujacy `index.html`, `js`, `css` i assets do
  `../src/main/resources/static`.
- `mvn -q -DskipTests package`
  Buduje backend oraz uruchamia produkcyjny build Angulara w fazie
  `prepare-package`, a potem pakuje wynik do JAR-a.

## Jak czytac te dokumenty

- `architecture/` odpowiada na pytanie: "jakie sa stale decyzje i granice odpowiedzialnosci?"
- `operational-context/` odpowiada na pytanie: "jak utrzymywac katalog operacyjny uzywany przez analize?"
