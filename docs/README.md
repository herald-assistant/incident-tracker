# Dokumentacja Projektu

Ten katalog ma teraz dwa rodzaje dokumentow:

1. dokumenty krok-po-kroku, ktore opisuja jak budowalismy projekt,
2. dokumenty architektoniczne, ktore maja pomagac w dalszej pracy i kolejnych sesjach Codex.

## Od czego zaczac

Jesli celem jest zrozumienie obecnego stanu projektu, czytaj w tej kolejnosci:

1. `architecture/01-system-overview.md`
2. `architecture/02-key-decisions.md`
3. `architecture/03-runtime-flow.md`
4. `architecture/04-codex-continuation-guide.md`

Jesli celem jest przejscie edukacyjne krok po kroku, czytaj:

1. `learning-plan.md`
2. `01-starter-spring-boot.md`
3. kolejne pliki numerowane az do `18-real-gitlab-rest-integration.md`

## Co jest gdzie

- `learning-plan.md`
  Glowna sciezka edukacyjna i roadmapa malych krokow.
- `architecture/`
  Stabilny opis systemu, decyzji, aktualnego flow i zasad dalszego rozwoju.
- `../frontend`
  Zrodlowy workspace Angular dla ekranu operacyjnego.
- `../src/main/resources/static`
  Wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.
- `01-*.md` do `18-*.md`
  Historia kolejnych iteracji, testow i decyzji podejmowanych po drodze.

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

- Dokumenty krokowe odpowiadaja na pytanie: "jak doszlismy do obecnego miejsca?"
- Dokumenty architektoniczne odpowiadaja na pytanie: "jak system wyglada teraz i jak go rozwijac dalej?"
