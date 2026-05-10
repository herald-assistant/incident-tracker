# Dokumentacja Projektu

Ten katalog ma jeden glowny obszar:

1. `architecture/`
   stabilny opis kierunku produktu, architektury, decyzji, runtime flow i
   zasad dalszego rozwoju.

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
8. `architecture/08-operational-context-model-tools-and-usage.md`

## Co jest gdzie

- `architecture/`
  source of truth dla architektury i zasad dalszego rozwoju.
- `../operational-context-maintenance`
  prompty i procedury utrzymania katalogu operational context.
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
- `architecture/08-operational-context-model-tools-and-usage.md` odpowiada na
  pytanie: "jak utrzymywac katalog operacyjny uzywany przez analizy i tools?"
