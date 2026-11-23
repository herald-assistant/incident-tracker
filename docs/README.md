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
- `copilot-sdk-otlp-agent-scanner.md`
  instrukcja opt-in diagnostyki sesji Copilot SDK w lokalnym Agent Scanner.
- `operational-context-assistance-diagnostics.md`
  diagnostyka asysty Operational Context i scenariusze recznej weryfikacji.
- [system-assistant.md](system-assistant.md)
  instrukcja konfiguracji, rozmowy, historii i uruchamiania Browser Tools.
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
6. `architecture/change-verification-runtime-flow.md`
7. `architecture/config-drift-viewer-runtime-flow.md`
8. `architecture/delivery-complexity-assessment-runtime-flow.md`
9. `architecture/delivery-scope-complexity-runtime-flow.md`
10. `architecture/delivery-complexity-trends-runtime-flow.md`
11. `architecture/ui-explorer-runtime-flow.md`
12. `architecture/ux-inspector-runtime-flow.md`
13. `architecture/package-dependencies.md`
14. `architecture/operational-context-model-tools-and-usage.md`
15. `architecture/codex-continuation-guide.md`
16. `architecture/release-summary-runtime-flow.md`

Przy tworzeniu nowego feature'a albo zmianie L1-L3 istniejacego feature'a lub
mechanizmu wspolnego przeczytaj dodatkowo:

17. `architecture/analysis-feature-delivery-playbook.md`

Przy zmianach frontendu przeczytaj
[standard UI i kontrolek](architecture/frontend-ui-standards.md) oraz lokalne
instrukcje `frontend/**/AGENTS.md`.

Nastepnie sprawdz dokument potrzeby i zatwierdzony plan dotyczacy konkretnej
zmiany. Dokument architektoniczny ani business need nie sa zgoda na
implementacje niezatwierdzonego planu.

## System Assistant

[Instrukcja obsługi](system-assistant.md) opisuje sześć widoków: Skille,
Kontrakty, Profile, Rozmowę i Uruchamianie w systemie. Feature udostępnia import
wybranych operacji OpenAPI, podgląd Swagger UI, eksport YAML i przenoszenie
całego zestawu konfiguracji. Trwałą rozmowę można kontynuować z Analysis
History lub bezpośrednio w panelu Browser Tools, z pierwotnym kontekstem
i aktualnymi skillami oraz narzędziami.

[Runtime](architecture/system-assistant-runtime-flow.md) określa kontrakty,
zapis konfiguracji i rozmów, podzbiór OpenAPI oraz granice platformy.
Samodzielna [instrukcja tworzenia konfiguracji przez AI](system-assistant-configuration-authoring.md)
pozwala przygotować cały plik importu na podstawie repozytoriów lub dokumentów
w zewnętrznym chacie, np. Codex lub VS Code.
Feature jest domyślnie wyłączony. Obsługuje hosty HTTP(S) bez ograniczenia
do loopback. [Auth API](architecture/http-api-authentication.md) obejmuje Basic,
Bearer, OAuth2 i OIDC; załączniki pozostają poza bieżącym zakresem.

## Aktualne potrzeby i plany

`needs/` i `plans/` nie sa archiwum zakonczonych prac. Po zamknieciu zmiany
trwale decyzje oraz wynikowy stan powinny byc przeniesione do `architecture/`,
a niepotrzebny plan moze zostac usuniety; historie zachowuje Git.

- [Historia kodu w follow-up](needs/gitlab-history-in-follow-up.md) oraz
  [kontrakt narzędzi historii GitLaba](architecture/gitlab-code-history-tools.md)
  opisują dostępne odczyty commitów, diffów i wcześniejszego kodu.
  Każdy z pięciu feature’ów używa jednej allowlisty dla initial i follow-up
  oraz guidance w opisach narzędzi. Historia nie wymaga grantów SHA;
  wspólny resolver zachowuje przypięcia branchy i tagów do commitów.

- Biezacy kontrakt Release summary opisuje
  [runtime Release summary](architecture/release-summary-runtime-flow.md).
  Potrzebę wyboru okresu opisuje [zakres dat](needs/release-summary-date-range.md).

- Rozpoznanie elementu z capture, przejście od żądania HTTP do kodu backendu
  i wznowienie rozmowy z historii opisuje [przepływ UX Inspectora](architecture/ux-inspector-runtime-flow.md).
  Zakres wykonanych testów i prób z modelem opisuje [weryfikacja capture i historii HTTP](ux-inspector-http-history-verification.md).
- Zapis wzorców URL przy systemie opisuje [model Operational Context](architecture/operational-context-model-tools-and-usage.md#system).
  Sposób sprawdzenia propozycji i opublikowanego wpisu opisuje [diagnostyka asysty AI](operational-context-assistance-diagnostics.md).
- Analizę całego widoku i jego routingu opisuje [przepływ UI Explorera](architecture/ui-explorer-runtime-flow.md).
- `plans/open-work.md`
  jest aktywnym backlogiem. Kazdy element ma wlasne uzasadnienie i checkliste,
  a wykonanie kolejnych krokow podlega bramkom akceptacji z `AGENTS.md`.

Rodzina Delivery Complexity nie utrzymuje zakonczonych potrzeb ani planow jako
archiwum. Aktualny stan Behavior Change Assessment, Technical Change Assessment i Delivery Trends jest opisany w odpowiednich dokumentach
`architecture/*-runtime-flow.md`. Kolejny inkrement L1-L3 wymaga nowego albo
zaktualizowanego dokumentu potrzeby oraz zatwierdzonego planu; historie
zakonczonych prac zachowuje Git.

Aktualny kontrakt recznej edycji wyniku i odczytu dlugich sekcji raportu
opisuje `architecture/analysis-feature-delivery-playbook.md` oraz
`architecture/key-decisions.md`.

Dla nowego feature'a albo kolejnego inkrementu L1-L3 utworz lub zaktualizuj
konkretny dokument w `needs/` oraz zatwierdzony plan w `plans/`, zamiast
odtwarzac usuniete roadmapy zakonczonych albo porzuconych prac.

## Odpowiedzialnosc dokumentow architektonicznych

- `architecture/product-direction.md`
  definiuje oferte produktu i model rozszerzalnosci.
- [Standard UI i kontrolek](architecture/frontend-ui-standards.md)
  opisuje obowiazujacy wyglad, wspolne pola formularzy i pare model/reasoning,
  ownership, dostepnosc, gestosc oraz weryfikacje konsumentow.
- `architecture/system-overview.md`
  opisuje elementy systemu, publiczne wejscia oraz ownership implementacji.
- `architecture/key-decisions.md`
  utrwala decyzje, ktore maja pozostac stabilne po refaktorach.
- `architecture/incident-analysis-runtime-flow.md`
  opisuje wykonanie glownych sciezek Incident Analysis oraz jego uzycie
  neutralnego runtime Copilota.
- `architecture/change-verification-runtime-flow.md`
- `architecture/gitlab-mr-diff-tools.md`
  opisuje source scope, rule ledger, deterministyczna decyzje, prompt, skille,
  tools, report, import/export i UI Change Verification.
- [Historia kodu GitLab](architecture/gitlab-code-history-tools.md)
  opisuje commit/diff tools, dane commitera i zakres historycznych odczytów
  w pięciu follow-upach, w tym Flow Explorerze.
- `architecture/ui-explorer-runtime-flow.md`
  opisuje katalog widokow, branchowy source context, report-first Copilot flow,
  osiem sekcji wyniku, follow-up chat, historie i portability UI Explorera.
- `architecture/ux-inspector-runtime-flow.md`
  opisuje Browser Tools, capture v1 przez jeden POST i odczyt po ID, target resolution,
  focused Copilot workflow, jednosekcyjny report, follow-up chat, job/history
  i security model.
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
  deduplikacje issue, addytywnosc Delivery Units, okresy, filtry, jakosc oraz
  opcjonalna analize punktow na osobodzien i estimate kontra actual.
- `architecture/package-dependencies.md`
  definiuje dozwolony graf zaleznosci i odpowiedzialnosc warstw.
- `architecture/operational-context-model-tools-and-usage.md`
  definiuje katalog Operational Context, jego API, tools i zasady uzycia;
  diagnostyke asysty opisuje `operational-context-assistance-diagnostics.md`.
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
  niezmienny seed katalogu; runtime czyta lokalna kopie w
  `${tdw.workspace.directory}/operational-context`.
- `../operational-context-maintenance`
  pakowane reguly merytoryczne asysty AI oraz skrypty utrzymaniowe.
- `../frontend`
  zrodlowy workspace Angular dla aplikacji operatorskiej.
- `../src/main/resources/static`
  wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.

## Build i frontend workflow

Spring Boot domyślnie nasłuchuje na porcie `4200` i pod
`http://localhost:4200` serwuje także gotowy frontend. Port można nadpisać
przez `SERVER_PORT` lub argument `--server.port`.

- `mvn -q test`
  uruchamia testy backendu bez instalowania Node, `npm ci` i budowania Angulara.
- `cd ../frontend && npm start`
  Angular dev server na `4300` z proxy na lokalny backend Spring Boot na
  `4200`. Backend uruchom np. `mvn -Pbackend-dev spring-boot:run`, bez zmiany
  portu. Po zbudowaniu paczki Spring Boot serwuje API i gotowy frontend na
  `4200`; skrypty `run-tdw.cmd` / `run-tdw.ps1` używają tego samego adresu.
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

## Operational Context

- [Model, tools i zasady uzycia](architecture/operational-context-model-tools-and-usage.md)
- [Zrealizowana potrzeba mapy dalszej analizy](needs/operational-context-navigation-map.md)
