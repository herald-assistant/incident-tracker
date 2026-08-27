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
7. `architecture/delivery-complexity-assessment-runtime-flow.md`
8. `architecture/delivery-scope-complexity-runtime-flow.md`
9. `architecture/delivery-complexity-trends-runtime-flow.md`
10. `architecture/package-dependencies.md`
11. `architecture/operational-context-model-tools-and-usage.md`
12. `architecture/codex-continuation-guide.md`

Przy tworzeniu nowego feature'a albo zmianie L1-L3 istniejacego feature'a lub
mechanizmu wspolnego przeczytaj dodatkowo:

13. `architecture/analysis-feature-delivery-playbook.md`

Nastepnie sprawdz dokument potrzeby i zatwierdzony plan dotyczacy konkretnej
zmiany. Dokument architektoniczny ani business need nie sa zgoda na
implementacje niezatwierdzonego planu.

## Aktualne potrzeby i plany

`needs/` i `plans/` nie sa archiwum zakonczonych prac. Po zamknieciu zmiany
trwale decyzje oraz wynikowy stan powinny byc przeniesione do `architecture/`,
a niepotrzebny plan moze zostac usuniety; historie zachowuje Git.

- `needs/operational-context-catalog-editing.md`
  opisuje potrzebe bezpiecznego create, edit i delete katalogu Operational
  Context z poziomu UI przy zachowaniu spojnosci relacji i wszystkich
  konsumentow.
- `plans/operational-context-catalog-editing.md`
  dokumentuje wykonana ewolucje L3 oraz wynikowy prosty MVP: bundled seed jest
  kopiowany raz do `tdw-data/operational-context`, wszystkie dziewiec typow ma
  CRUD przez UI, a zapis korzysta z walidacji domenowej i atomowej podmiany
  jednego dokumentu. Wynik nie ma storage modes, security gate, rewizji,
  historii ani rollbacku Operational Context.
- `needs/ai-skills-catalog-preview.md`
  opisuje potrzebe wygodnego podgladu efektywnego katalogu runtime bez
  filesystemu, edycji, uruchamiania ani przypisywania skilli per feature.
- `plans/ai-skills-catalog-preview.md`
  dokumentuje wykonany read-only MVP L2: immutable projekcje zwalidowanych
  `SKILL.md`, shared/operator API, ekran `Platform / AI Skills`, deep link,
  wyszukiwanie, filtry oraz renderowany i surowy Markdown.
- `needs/ui-explorer.md`
  opisuje potrzebe generowania funkcjonalnej lub technicznej dokumentacji
  zlozonego widoku frontendu oraz materialu do przygotowania zmiany przez
  analityka, ktory nie musi znac repozytorium ani mechaniki AI.
- `needs/delivery-complexity-trends.md`
  opisuje potrzebe lokalnego polaczenia biznesowych CSV jednego assessmentu i
  podgladu zmian dostarczonej zlozonosci dziennie, miesiecznie lub kwartalnie.
- `plans/delivery-complexity-trends.md`
  dokumentuje zrealizowany plan L2 dla uniwersalnego frontendowego dashboardu,
  rozszerzenia obu CSV o autorow MR oraz filtrow zespolu, autora i dat bez
  zapisu na serwerze.
- `plans/ui-explorer.md`
  jest zatwierdzonym planem L2 w realizacji dla statycznego, screen-centered MVP z katalogiem
  ekranow wybieranych przez jawnie zarejestrowany system
  `internal-service/frontend`, wybieralnymi sekcjami, source-grounded raportem
  oraz neutralna capability rozpoznawania frontendu. Plan obejmuje breaking
  migracje Operational Context bez warstwy kompatybilnosci dla zmienianych
  kontraktow. Wykonanie kolejnych niezaznaczonych krokow nadal podlega osobnym
  bramkom akceptacji zapisanym w planie.
- Config Drift Viewer wraz z Runtime Configuration Tool Workbench jest
  zakonczony. Wynikowy kontrakt, runtime flow, UI, granice bezpieczenstwa i
  ograniczenia sa opisane kanonicznie w
  `architecture/config-drift-viewer-runtime-flow.md`.
- `plans/open-work.md`
  jest aktywnym backlogiem. Kazdy element ma wlasne uzasadnienie i checkliste,
  a wykonanie kolejnych krokow podlega bramkom akceptacji z `AGENTS.md`.

Dla nowego feature'a albo kolejnego inkrementu L1-L3 utworz lub zaktualizuj
konkretny dokument w `needs/` oraz zatwierdzony plan w `plans/`, zamiast
odtwarzac usuniete roadmapy zakonczonych albo porzuconych prac.

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
- `architecture/delivery-complexity-assessment-runtime-flow.md`
  opisuje typed Jira discovery, Delivery Units, evidence/privacy, scoring,
  rownolegle wykonanie oraz zapis runow nowego feature'a.
- `architecture/delivery-scope-complexity-runtime-flow.md`
  opisuje niezalezny eksperyment score x scope, kontrakt `0-200`, izolacje od
  drugiego assessmentu i deletion boundary.
- `architecture/delivery-complexity-trends-runtime-flow.md`
  opisuje client-only import wielu biznesowych CSV jednego assessmentu,
  deduplikacje issue, addytywnosc Delivery Units, okresy, filtry i jakosc
  trendu.
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

## Build i frontend workflow

- `mvn -q test`
  uruchamia testy backendu bez instalowania Node, `npm ci` i budowania Angulara.
- `cd ../frontend && npm start`
  Angular dev server z proxy na lokalny backend Spring Boot.
- `cd ../frontend && npm test`
  testy UI Angulara; nie sa uruchamiane przez `mvn test`.
- `cd ../frontend && npm run build`
  produkcyjny build Angulara zapisywany w `../src/main/resources/static`.
- `mvn -q -DskipTests package`
  buduje backend, uruchamia produkcyjny build Angulara w `prepare-package` i
  pakuje wynik do JAR-a.
- `mvn -q -Pbackend-dev -DskipTests package`
  tworzy szybka lokalna paczke backendu bez przebudowy frontendu. JAR zawiera
  zastany bundle z `src/main/resources/static`, wiec ten wariant nie potwierdza
  spojnosci zmian backendu i UI i nie powinien zastepowac pelnego builda przed
  wydaniem.
