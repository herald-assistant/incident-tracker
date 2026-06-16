# Team Delivery Workspace UI Migration Plan

Status: active plan
Created: 2026-06-16

## Cel

Ten dokument opisuje migracje UI z historycznego "Incident Tracker" do
workspace'u wspierajacego codzienna prace calego zespolu wytworczego.

Produktowy framing:

- incident analysis jest pierwszym dostepnym feature'em,
- Elastic, GitLab i Database sa czescia Tool Workbench, czyli zaplecza do
  testow, debugowania i recznego zebrania inputu,
- Operational Context jest katalogiem platformowym i capability wspolna dla
  feature'ow oraz tools,
- UI ma wspierac skills/capabilities, a nie role-based organization,
- layout ma byc prosty, spokojny, korporacyjny i gesty informacyjnie, bez
  marketingowego hero.

## Zasada pracy nad tym planem

Kazdy krok implementacyjny wymaga osobnego potwierdzenia zakresu.

Proces dla kazdego kroku:

1. Codex proponuje dokladny zakres najblizszej zmiany.
2. Uzytkownik zatwierdza albo koryguje zakres.
3. Codex implementuje tylko zatwierdzony zakres.
4. Codex uruchamia adekwatna weryfikacje.
5. Codex aktualizuje checklisty w tym dokumencie.

Jesli w trakcie rozmowy zmieni sie kierunek produktu albo UI, ten dokument ma
byc zaktualizowany przed dalsza implementacja.

## Decyzje potwierdzone

- Defaultowa nazwa workspace'u: `Team Delivery Workspace`.
- Tytul aplikacji ma byc konfigurowalny z `application.properties`.
- Property tytulu UI: `app.ui.title`.
- Runtime config dla frontendu: `GET /api/ui/config`.
- Jesli tytul nie jest ustawiony, UI pokazuje tylko `Team Delivery Workspace`
  i nie pokazuje podtytulu.
- Jesli tytul jest ustawiony, UI pokazuje ustawiony tytul oraz podtytul
  `Team Delivery Workspace`.
- Sidebar ma miec neutralny znak `AI`.
- Glowna nawigacja ma byc pogrupowana:
  - `Analysis Features`
  - `Tool Workbench`
  - `Platform`
- Topbar ma byc kontekstowy, a nie glowna nawigacja.
- V1 ma zachowac jasny motyw, ale style maja byc przygotowane pod kolejne
  motywy przez tokeny CSS.
- V1 zachowuje obecne URL-e: `/`, `/elastic`, `/gitlab`, `/database`,
  `/operational-context`.
- Pozycje platformowe `AI Models`, `GitHub Auth` i `Settings` sa w Krok 2
  disabled/nav placeholders.
- Status GitHub/Copilot zostaje na razie w widoku `Incident Analysis`; shell
  ma tylko kontekst widoku: breadcrumb i tytul.
- Krok 3 dodaje mechanizm przyszlych motywow przez `:root[data-theme='light']`,
  ale V1 nadal ma tylko jasny motyw.
- Stare zmienne CSS (`--primary`, `--surface`, `--border` itd.) zostaja jako
  aliasy kompatybilnosci wskazujace na nowe tokeny `--color-*`.
- Globalne tlo aplikacji w V1 jest spokojnym kolorem, bez dekoracyjnego
  gradientu.
- Docelowy brand uzywa obrazka `assets/brand/main-logo.png`; nie wracamy do
  tekstowego znaku `AI`.
- Krok 4a zmienia Incident Analysis z dwoch kolumn na czytelniczy uklad:
  finalny wynik ma pelna szerokosc, a trace/evidence/AI activity ida pod wynik.
- `analysis-stepper__step-meta` nie jest prezentowane w naglowkach krokow,
  poniewaz rozpychalo stepper i pogarszalo czytelnosc.
- Po zakonczeniu analizy albo wczytaniu rezultatu z pliku panele
  `Przebieg analizy` oraz `Tok dzialania AI` sa domyslnie zwiniete, ale nadal
  mozna je recznie rozwinac.
- `Usage` w pasku kontekstu runu pozostaje kompaktowym itemem z ellipsis, ale
  musi miec tooltip ze szczegolami tokenow, estymacji kosztu, credits, modelu i
  kontekstu sesji.
- Tooltip `Usage` ma byc jasnym panelem informacyjnym zgodnym z light theme,
  miec limit wysokosci z przewijaniem i zwiezly breakdown, zeby nie przykrywal
  ani nie ucinal dlugich danych.
- Lewy sidebar ma miec tryb zwijany do ikonowego raila o szerokosci `60px`.
  W trybie zwinietym nawigacja pozostaje klikalna, aktywny ekran jest nadal
  oznaczony, a etykiety sa dostepne przez `aria-label`/tooltip.
- Kontrolka zwijania/rozwijania uzywa ikony Material Symbols `dock_to_right`.
  Po zwinieciu osobna kontrolka znika, a jej role przejmuje `main-logo.png`,
  ktore na hover/focus zmienia sie wizualnie w ikone `dock_to_right`.
- Pusty stan `Incident Analysis` przed startem analizy nie pokazuje statusowego
  kickera `Gotowe`, zeby nie sugerowac zakonczonego wyniku.
- Domyslny uklad dla wszystkich ekranow `Tool Workbench` nie jest
  trzykolumnowy. Wyniki tooli moga byc szerokie i zlozone, wiec response musi
  dostac glowna szerokosc robocza pod formularzem.
- Workbench i jego shared/operator endpointy sa analysis-independent. Widoki
  Workbench nie eksponuja `correlationId`, `analysisRunId` ani incidentowego
  session scope'u; taki scope nalezy do feature-owned hidden `ToolContext` dla
  sesji AI, a nie do manualnego API integracji.
- Scrollbary w calej aplikacji maja byc delikatne i zgodne z light theme:
  transparentny track, cienki zaokraglony thumb z tokenow CSS, bez natywnych
  strzalek i bez ciezkiego guttera psujacego karty.
- Panele `Request preview` i `JSON response` w Tool Workbench maja uzywac
  spojnego wzorca akcji z GitLab: ikonowe `content_copy` i `download`,
  z feedbackiem `task_alt` po skopiowaniu.
- `Request preview` i `JSON response` w Tool Workbench sa zwijalne. Po
  wykonaniu requestu `JSON response` jest domyslnie rozwiniety, a mniej istotny
  `Request preview` domyslnie zwiniety.

## Decyzje do zatwierdzenia podczas implementacji

- Czy V1 zachowuje obecne URL-e (`/`, `/elastic`, `/gitlab`, `/database`,
  `/operational-context`) i zmienia tylko shell/nawigacje, czy od razu dodaje
  docelowe aliasy typu `/skills/incident-analysis` i `/workbench/elastic`.
  Rekomendacja V1: zachowac obecne URL-e.
- Czy platformowe pozycje `AI Models`, `GitHub Auth`, `Settings` maja byc
  w V1 tylko disabled/nav placeholders, czy maja dostac proste strony.
  Rekomendacja V1: disabled/nav placeholders poza GitHub auth statusem w
  topbarze.

## Docelowa struktura UI

### App Shell

Docelowo wszystkie widoki korzystaja ze wspolnego shell'a aplikacji:

- lewy sidebar jako glowna nawigacja,
- marka workspace'u w sidebarze,
- neutralny znak `AI`,
- kontekstowy topbar z tytulem widoku, breadcrumbami, statusem GitHub/Copilot i
  opcjonalnymi akcjami strony,
- centralny obszar roboczy bez marketingowego hero.

### Nawigacja

`Analysis Features`

- `Incident Analysis` - enabled, route obecnie `/`.
- `Flow Explorer` - disabled / coming soon.
- `Functional Logic` - disabled / coming soon.
- `Data Diagnostics` - disabled / coming soon.

`Tool Workbench`

- `Elastic Logs` - route obecnie `/elastic`.
- `GitLab Source` - route obecnie `/gitlab`.
- `Database Tools` - route obecnie `/database`.
- `Operational Context` - route obecnie `/operational-context`.

`Platform`

- `AI Models` - V1 disabled albo prosty widok pozniej.
- `GitHub Auth` - status i akcje w topbarze; osobny widok opcjonalny.
- `Settings` - V1 disabled.

### Incident Analysis

Widok ma byc narzedziowy:

- bez hero,
- kompaktowy panel startu na gorze,
- pola: `Correlation ID`, `Model AI`, `Reasoning effort`,
- primary action: `Run analysis`,
- secondary actions: `Import`, `Export`, `Copy prompt` tam, gdzie dostepne,
- po starcie joba panel ma dzialac jak pasek kontekstu:
  - `correlationId`,
  - `analysisId`,
  - status,
  - wykryte `environment`,
  - branch,
  - model,
  - tokeny/koszt,
- glowna przestrzen robocza:
  - finalny wynik i sekcje `Functional analysis`, `Technical analysis`,
    `Visibility limits` na pelnej szerokosci,
  - kroki, coverage i AI/tool activity pod wynikiem jako sekcje zwijane,
- follow-up chat jako dolny panel albo prawa zakladka, domyslnie mniej
  dominujacy niz finalny wynik i trace.

### Tool Workbench

Elastic, GitLab i Database powinny dojsc do wspolnego szablonu:

- gorny naglowek na pelna szerokosc:
  - krotki tytul i najwazniejsza akcja/status,
  - zwijane szczegoly capability, inicjalnie zwiniete,
  - metadane: endpointy, scope, AI reusable, guardrails,
- lewy panel roboczy:
  - lista elementow/tooli do testowania,
  - wspolne wartosci scope i parametry uzywane przez kilka elementow,
- glowna przestrzen robocza:
  - u gory formularz albo payload aktualnie wybranego elementu,
  - primary `Run request` albo inna nazwa akcji pasujaca do operacji integracji,
  - wynik pod formularzem dopiero po wykonaniu requestu,
  - response JSON/status/timing/copy actions w tej samej szerokiej przestrzeni,
- kazdy tool pokazuje:
  - endpoint,
  - wymagany scope,
  - czy capability jest reusable przez AI,
  - ograniczenia bezpieczenstwa,
  - ostatni wynik.

Nie projektujemy Tool Workbench jako stalego ukladu trzykolumnowego ani jako
domyslnego drawer-first UI. Drawery zostaja opcja dla szczegolow/raw preview,
ale nie dla glownego wyniku testowanego toola w V1.

Tool Workbench ma wygladac jak laboratorium operatora, a nie osobny produktowy
feature.

### Operational Context

Operational Context jest katalogiem platformowym. Widok ma miec tytul:
`Operational Context Catalog`.

Zakladki:

- `Overview`
- `Systems`
- `Processes`
- `Repositories`
- `Bounded Contexts`
- `Teams`
- `Validation`
- `Open Questions`

Szczegoly encji pozostaja prawym drawerem. Validation i Open Questions maja
dzialac jak inbox utrzymaniowy z filtrami i akcjami kopiowania sciezki albo
targetu poprawki.

### Modale i drawery

Modale tylko dla akcji blokujacych:

- potwierdzenie importu,
- utrata niezapisanego payloadu,
- blad autoryzacji wymagajacy akcji operatora.

Drawery dla szczegolow:

- code/evidence,
- encje katalogu,
- payloady,
- odpowiedzi JSON,
- raw details.

Drawer powinien miec stale akcje: `Copy`, `Open raw`, `Close`.

### Przyciski i statusy

- Jeden primary action na widok albo sekcje robocza: `Run analysis`,
  `Run request`, `Search`.
- Secondary dla import/export/reset/copy.
- Icon-only dla kopiowania, rozwijania, odswiezania i zamykania drawerow.
- Statusy jako pill/chip: `Ready`, `Running`, `Completed`, `Failed`,
  `Auth required`.

### Styl

V1: jasny motyw.

Docelowe tokeny:

```scss
--color-bg: #f5f7fb;
--color-surface: #ffffff;
--color-border: #d8e0eb;
--color-text: #172b4d;
--color-muted: #5e6c84;
--color-primary: #0c66e4;
--color-primary-dark: #0747a6;
--color-success: #216e4e;
--color-warning: #b76e00;
--color-danger: #ae2e24;
--color-ai: #6554c0;
```

Zasady:

- radius glownie `6px` albo `8px`,
- bez duzych "puchatych" kart jako glownej kompozycji,
- bez dekoracyjnych gradientow jako tla aplikacji,
- tabele, listy i timeline maja byc geste, ale czytelne,
- tekst ma miescic sie w kontrolkach na desktopie i mobile.

## Plan migracji

### Krok 0: Plan i governance

- [x] Utworzyc dokument planu migracji UI.
- [ ] Aktualizowac checklisty po kazdej zatwierdzonej i wykonanej zmianie.
- [ ] Dopisywac nowe decyzje uzytkownika do sekcji "Decyzje potwierdzone".

### Krok 1: Runtime UI config i brand

Cel: przygotowac sparametryzowany tytul aplikacji bez ruszania jeszcze calego
layoutu.

Zakres proponowany do zatwierdzenia:

- [x] Dodac backendowe properties dla UI: `app.ui.title`.
- [x] Dodac shared/operator endpoint `GET /api/ui/config`.
- [x] Endpoint zwraca:
  - `title`,
  - `subtitle` albo `null`,
  - `defaultTitle`.
- [x] Zasada backendu:
  - jesli property nie ma tekstu: `title = Team Delivery Workspace`,
    `subtitle = null`,
  - jesli property ma tekst: `title = <property>`,
    `subtitle = Team Delivery Workspace`.
- [x] Dodac frontendowy service/model do pobierania configu.
- [x] Tymczasowo podpiac config do istniejacych naglowkow przez
  `AppBrandComponent`, bez przebudowania layoutu.
- [x] Zmienic znak brandu z `IR` na neutralne `AI`.
- [x] Zaktualizowac widoczny komunikat importu, zeby nie odsylal juz do
  nazwy `Incident Tracker`.
- [x] Dodac testy backendowe i frontendowe dla fallbacku/defaultu.

Weryfikacja:

- [x] `mvn -q "-Dtest=UiConfigServiceTest,UiConfigControllerTest" test`.
- [x] `npm test -- --watch=false`.

### Krok 2: Wspolny App Shell

Cel: usunac powielone topbary z widokow i wprowadzic jeden shell.

Zakres:

- [x] Dodac `AppShellComponent` albo rownowazny layout na poziomie root route.
- [x] Przeniesc brand i nawigacje do shell'a.
- [x] Dodac route data dla tytulow i breadcrumbow widokow.
- [x] Wprowadzic lewy sidebar z grupami:
  - `Analysis Features`,
  - `Tool Workbench`,
  - `Platform`.
- [x] Dodac disabled coming-soon items dla przyszlych feature'ow.
- [x] Topbar ograniczyc do kontekstu widoku.
- [x] Usunac lokalne topbary z `AnalysisConsole`, `DatabaseConsole`,
  `ElasticEvidenceConsole`, `GitLabEvidenceConsole` i
  `OperationalContext`.

Weryfikacja:

- [x] routing dziala dla wszystkich obecnych URL-i,
- [x] aktywna pozycja sidebaru jest poprawna,
- [x] mobile layout nie traci nawigacji,
- [x] testy FE,
- [x] browser check po uruchomieniu frontendu.

### Krok 3: Tokeny stylu i spokojny light theme

Cel: ustabilizowac wizualny fundament przed przebudowa ekranow.

Zakres:

- [x] Dodac docelowe tokeny `--color-*` w `styles.scss`.
- [x] Zachowac kompatybilne aliasy dla obecnych zmiennych, zeby nie robic
  big-bang rewrite.
- [x] Uspokoic tlo aplikacji: bez dekoracyjnych gradientow jako glownego tla.
- [x] Ujednolicic radius glownych kontrolek do `6px`/`8px`, zostawiajac
  wieksze radiusy tylko tam, gdzie sa uzasadnione.
- [x] Ujednolicic buttony, chipy, inputy, selecty i status pills.
- [x] Przygotowac `data-theme` albo podobny mechanizm pod przyszle motywy,
  bez dodawania ciemnego motywu w V1.

Weryfikacja:

- [x] wizualny check glownych widokow,
- [x] mobile/desktop check,
- [x] testy FE/build.

### Krok 4: Incident Analysis jako widok roboczy

Cel: przestawic pierwszy feature z hero na codzienny workspace.

Zakres:

- [x] Usunac hero-card jako pierwszy viewport.
- [x] Zbudowac kompaktowy panel startu analizy.
- [x] Po starcie joba pokazac pasek kontekstu z faktami runu.
- [x] Uporzadkowac wynik w czytelnym workspace:
  - wynik i sekcje finalne na pelnej szerokosci,
  - kroki, coverage i AI/tool activity pod wynikiem.
- [x] Zmienic follow-up chat na mniej dominujacy panel.
- [x] Zachowac obecne kontrakty API i runtime behavior.
- [x] Usunac prezentacje `analysis-stepper__step-meta` z naglowkow krokow.
- [x] Domyslnie zwijac przebieg analizy i tok dzialania AI po finalnym wyniku
  albo imporcie rezultatu z pliku.
- [x] Przywrocic szczegolowy tooltip kosztow i tokenow dla kompaktowego itemu
  `Usage` w pasku kontekstu runu.
- [x] Dopracowac kolorystyke, przewijanie i zwiezlosc tooltipa `Usage`.
- [x] Dodac zwijany lewy sidebar z ikonowym trybem nawigacji.
- [x] Usunac kicker `Gotowe` z pustego stanu ekranu analizy.

Weryfikacja:

- [x] start analizy nadal wysyla tylko `correlationId`, `model`,
  `reasoningEffort`,
- [x] import/export nadal dziala,
- [x] prompt nadal da sie skopiowac, gdy jest dostepny,
- [x] chat dziala dla live joba i jest read-only dla importu,
- [x] testy FE i browser check.

### Krok 5: Tool Workbench layout

Cel: ujednolicic Elastic, GitLab i Database jako narzedzia operatorskie.

Zakres:

- [x] Zdefiniowac pierwszy wariant layoutu workbench na `Database Tools`:
  - full-width header z inicjalnie zwinietymi szczegolami capability,
  - lewy panel wyboru toola/elementu i wspolnego scope,
  - glowny workspace z formularzem wybranego elementu,
  - wynik renderowany pod formularzem dopiero po wykonaniu requestu,
  - status/timing/copy actions w sekcji wyniku.
- [x] Przebudowac `Database Tools` na wspolny wzorzec jako pierwszy ekran.
- [ ] Wyciagnac wspolne komponenty dopiero po drugim ekranie, jesli wzorzec
  zacznie sie stabilnie powtarzac bez nadmiarowych roznic.
- [ ] Przebudowac `Elastic Logs`.
- [ ] Przebudowac `GitLab Source`.
- [ ] Dla kazdego toola pokazac endpoint, scope, AI reusable capability,
  guardrails i last result.
- [ ] Nie wprowadzac stalej trzeciej kolumny dla response w V1.
- [ ] Nie zmieniac kontraktow backendowych helper endpointow.
- [x] Wprowadzic globalny, delikatny styl scrollbarow dla calej aplikacji.
- [x] Uspojnic `Database Tools`: `Request preview` i `JSON response` maja
  takie same ikonowe akcje kopiowania i pobierania jak GitLab JSON response.
- [x] Ustawic `Database Tools`: request preview i response JSON jako zwijalne
  panele, z requestem domyslnie zwinietym po otrzymaniu wyniku.

Weryfikacja:

- [ ] kazdy obecny tool request nadal dziala,
- [x] `Database Tools`: JSON request/response jest kopiowalny i czytelny.
- [x] `Database Tools`: status HTTP, timing i blad walidacji sa widoczne.
- [x] `Database Tools`: wynik nie renderuje sie przed pierwsza akcja.
- [x] `Database Tools`: UI i publiczny payload Workbench nie eksponuja
  `correlationId` ani `analysisRunId`.
- [x] `Database Tools`: backend API uzywa neutralnego `environment` scope'u i
  nie importuje nazw tooli z `agenttools`.
- [x] `Database Tools`: browser check desktop/mobile bez poziomego overflow.
- [x] `Database Tools`: lewy panel scrolluje cala karte bez ciezkiego guttera.
- [x] `Database Tools`: request preview i response JSON sa kopiowalne oraz
  pobieralne do pliku przez spojne ikonowe akcje.
- [x] `Database Tools`: request preview nie zajmuje miejsca po wyniku, dopoki
  operator go recznie nie rozwinie.
- [x] `Database Tools`: `npm test -- --watch=false`.
- [x] `Database Tools`: `npm run build`.
- [ ] `Elastic Logs`: JSON request/response jest kopiowalny i czytelny.
- [ ] `GitLab Source`: JSON request/response jest kopiowalny i czytelny.
- [ ] finalny browser check dla wszystkich ekranow Tool Workbench.

### Krok 6: Operational Context Catalog polish

Cel: ustawic Operational Context jako katalog platformowy, nie zwykly tool.

Zakres:

- [ ] Zmienic tytul widoku na `Operational Context Catalog`.
- [ ] Dopasowac widok do shell'a i nowych tokenow.
- [ ] Utrzymac zakladki katalogowe i detail drawer.
- [ ] Uporzadkowac Validation i Open Questions jako inbox utrzymaniowy.
- [ ] Zachowac obecne API `/api/operational-context/*`.

Weryfikacja:

- [ ] summary, search, tabs i detail drawer dzialaja,
- [ ] validation/open questions filtry dzialaja,
- [ ] copy maintenance target dziala,
- [ ] testy FE i browser check.

### Krok 7: Platform navigation i placeholdery

Cel: pokazac docelowe miejsce dla ustawien platformy bez nadmiarowej
implementacji.

Zakres:

- [ ] Ustalic, ktore pozycje platformowe sa disabled w V1.
- [ ] GitHub/Copilot auth zostawic jako status i akcje w topbarze albo dodac
  prosty widok, jesli zostanie zatwierdzony.
- [ ] AI Models zostawic jako disabled albo dodac prosty read-only widok
  katalogu modeli, jesli zostanie zatwierdzony.
- [ ] Settings zostawic disabled.

Weryfikacja:

- [ ] disabled items sa czytelne i niedezorientujace,
- [ ] nie ma martwych linkow prowadzacych do pustych route'ow.

### Krok 8: Optional route normalization

Cel: ewentualnie dodac docelowe URL-e bez lamania obecnych.

Zakres opcjonalny:

- [ ] `/skills/incident-analysis` jako alias/redirect dla `/`.
- [ ] `/workbench/elastic` jako alias/redirect dla `/elastic`.
- [ ] `/workbench/gitlab` jako alias/redirect dla `/gitlab`.
- [ ] `/workbench/database` jako alias/redirect dla `/database`.
- [ ] `/context/operational` albo pozostawienie `/operational-context`.

Rekomendacja: odlozyc do pozniejszego kroku, bo V1 moze uporzadkowac jezyk UI
bez zmiany URL-i.

### Krok 9: Dokumentacja i finalna weryfikacja

Cel: domknac migracje jako stabilny kierunek produktu.

Zakres:

- [ ] Zaktualizowac `00-product-direction.md`, jesli framing UI zmieni sposob
  opisu produktu.
- [ ] Zaktualizowac `01-system-overview.md`, jesli zmieniaja sie route'y albo
  role ekranow.
- [ ] Zaktualizowac ten plan: oznaczyc zrealizowane kroki i pozostale decyzje.
- [ ] Uruchomic pelna adekwatna weryfikacje:
  - `npm test -- --watch=false`,
  - `npm run build`,
  - backendowe testy dotknietych endpointow,
  - browser screenshot/check dla glownych route'ow.

## Biezacy next step

Najblizszy proponowany krok do zatwierdzenia:

`Krok 5b: Elastic Logs Workbench layout`.

Proponowany zakres:

- przebudowac `/elastic` wedlug wzorca sprawdzonego na `Database Tools`,
- zostawic obecne endpointy i payloady Elastica bez zmian,
- header full-width z inicjalnie zwinietymi metadanymi capability,
- lewy panel ze scope/wartosciami wspolnymi i wyborem elementu testowego,
- glowny formularz u gory, wynik JSON/status/timing/copy pod formularzem po
  wykonaniu requestu,
- bez stalej trzeciej kolumny i bez drawer-first response.
