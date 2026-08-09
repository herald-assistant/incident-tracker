# Dokumentacja projektu

Dokumentacja rozdziela stan systemu, potrzeby i propozycje wykonania. Zasady
tworzenia oraz aktualizowania dokumentow sa w `AGENTS.md`.

## Struktura

- `architecture/`
  kanoniczny opis obowiazujacego i docelowego stanu: decyzje, granice,
  zaleznosci, publiczne kontrakty, runtime flow i niezmienniki.
- `needs/`
  problemy, wartosc biznesowa, oczekiwane rezultaty, success criteria,
  ograniczenia i non-goals, bez wybierania implementacji.
- `plans/`
  zatwierdzane propozycje implementacji i aktywny backlog. Plany wynikajace z
  potrzeby biznesowej linkuja do odpowiedniego pliku w `needs/`.
- `local-workspace-and-launcher.md`
  instrukcja lokalnego uruchamiania JAR-a, katalogu `tdw-data`, backupu oraz
  roznicy pomiedzy local workspace i exportem.
- `presentations/`
  materialy prezentacyjne i ich zrodla.

Git przechowuje historie dochodzenia do rozwiazania. Dokumenty w
`architecture/` opisuja wynikowy stan, a nie zakonczone fazy migracji.

## Kolejnosc czytania

Przed wieksza zmiana przeczytaj:

1. `AGENTS.md`
2. `architecture/product-direction.md`
3. `architecture/system-overview.md`
4. `architecture/key-decisions.md`
5. `architecture/incident-analysis-runtime-flow.md`
6. `architecture/config-drift-viewer-runtime-flow.md`
7. `architecture/package-dependencies.md`
8. `architecture/operational-context-model-tools-and-usage.md`
9. `architecture/codex-continuation-guide.md`

Przy tworzeniu nowego feature'a albo zmianie L1-L3 istniejacego feature'a lub
mechanizmu wspolnego przeczytaj dodatkowo:

10. `architecture/analysis-feature-delivery-playbook.md`

Nastepnie sprawdz dokument potrzeby i zatwierdzony plan dotyczacy konkretnej
zmiany. Dokument architektoniczny ani business need nie sa zgoda na
implementacje niezatwierdzonego planu.

## Aktualne potrzeby i plany

- Config Drift Viewer wraz z Runtime Configuration Tool Workbench jest
  zakonczony. Wynikowy kontrakt, runtime flow, UI, granice bezpieczenstwa i
  ograniczenia sa opisane kanonicznie w
  `architecture/config-drift-viewer-runtime-flow.md`.
- `needs/change-verification.md`
  opisuje problem, wartosc, oczekiwany wynik i granice Change Verification.
  Nowy plan implementacji powinien wskazywac ten plik jako `Source need`.
- `needs/domain-skill-generation.md`
  opisuje potrzebe przygotowania source-backed, zatwierdzonego i
  zwalidowanego Agent Skill dla jednego modulu.
- `plans/domain-skill-generation.md`
  jest aktywnym planem MVP Domain Skill Generation. Read-only preflight jest
  zakonczony; kazdy kolejny krok nadal wymaga jawnego zatwierdzenia.
- `plans/open-work.md`
  jest aktywnym backlogiem. Kazdy element ma wlasne uzasadnienie i checkliste,
  a wykonanie kolejnych krokow podlega bramkom akceptacji z `AGENTS.md`.

## Odpowiedzialnosc dokumentow architektonicznych

- `architecture/product-direction.md`
  definiuje oferte produktu i model rozszerzalnosci.
- `architecture/system-overview.md`
  opisuje elementy systemu, publiczne wejscia oraz ownership implementacji.
- `architecture/key-decisions.md`
  utrwala decyzje, ktore maja pozostac stabilne po refaktorach.
- `architecture/incident-analysis-runtime-flow.md`
  opisuje wykonanie glownych sciezek Incident Analysis oraz jego uzycie
  neutralnego runtime Copilota.
- `architecture/config-drift-viewer-runtime-flow.md`
  opisuje deterministic/AI flow, tryby `BASIC/DEEP`, scope, limity i granice
  bezpieczenstwa Config Drift Viewer.
- `architecture/package-dependencies.md`
  definiuje dozwolony graf zaleznosci i odpowiedzialnosc warstw.
- `architecture/operational-context-model-tools-and-usage.md`
  definiuje katalog Operational Context, jego API, tools i zasady uzycia.
- `architecture/codex-continuation-guide.md`
  wskazuje kanoniczne miejsca w kodzie oraz bezpieczny sposob kontynuowania
  rozwoju.
- `architecture/analysis-feature-delivery-playbook.md`
  jest kompletna instrukcja dostarczania i rozwoju feature'a analitycznego:
  ownership, kontrola architecture drift, backend, Copilot runtime, tools,
  frontend, historia, testy i Definition of Done.

## Powiazane zasoby runtime

- `../src/main/resources/copilot/skills`
  skille Copilota pakowane do runtime aplikacji.
- `../src/main/resources/operational-context`
  realny katalog operacyjny czytany przez integracje, tools i feature'y.
- `../frontend`
  zrodlowy workspace Angular dla aplikacji operatorskiej.
- `../src/main/resources/static`
  wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.

## Frontend workflow

- `cd ../frontend && npm start`
  Angular dev server z proxy na lokalny backend Spring Boot.
- `cd ../frontend && npm test`
  testy UI Angulara; nie sa uruchamiane przez `mvn test`.
- `cd ../frontend && npm run build`
  produkcyjny build Angulara zapisywany w `../src/main/resources/static`.
- `mvn -q -DskipTests package`
  buduje backend, uruchamia produkcyjny build Angulara w `prepare-package` i
  pakuje wynik do JAR-a.
