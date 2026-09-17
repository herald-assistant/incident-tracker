# UX Inspector - skupiona analiza wskazanego elementu

Status: in-progress

Source need: [UX Inspector - pytania o wskazany element uruchomionej aplikacji](../needs/ux-inspector.md)

Klasyfikacja: **L3**. Feature uruchamia kod na niezaufanej stronie, przekracza
granice originow, udostepnia publiczny kontrakt joba i posiada wlasna polityke
source research oraz tools.

## Cel

UX Inspector ma odpowiadac na jedno pytanie dotyczace jednego elementu
wskazanego w uruchomionej aplikacji. Nie jest profilem UI Explorera i nie
generuje dokumentacji calego widoku. Uzytkownik wskazuje element przez TDW
Browser Tools, a nastepnie na zaufanym ekranie TDW potwierdza Application,
Branch, View, model, reasoning effort oraz pytanie.

Wynikiem jest `AnalysisReport` z dokladnie jedna sekcja `answer`, zapisany w
Analysis History. Odpowiedz ma byc zrozumiala biznesowo, a odniesienia do kodu
maja pelnic role dowodow.

## Baseline i conformance delta

| Obszar | UI Explorer | UX Inspector |
|---|---|---|
| Jednostka pracy | caly View | jedno pytanie o jeden target |
| Wejscie runtime | reczny wybor View | capture v1 + jawnie potwierdzony View |
| Backend | `features.uiexplorer` | `features.uxinspector` |
| Publiczne API | `/api/ui-explorer/**` | `/api/ux-inspector/**` |
| Source preparation | reachability wielu sekcji | target resolution + focused slice + nieblokujacy component source pack |
| Repository guidance | feature-owned skille UI Explorera | inline Copilot instructions + katalog naglowkow project skills |
| Source research | feature policy UI Explorera | cale jedno repo na pinned commit |
| Report | do osmiu sekcji | dokladnie jedna sekcja `answer` |
| Historia/export | osobny kontrakt UI Explorera | `tdw.ux-inspector-export` v1 |

Oba feature'y reuse'uja `frontendcatalog`, GitLab integrations, Copilot
runtime, neutralne tools, platformowy report store, Analysis History oraz
wspolne komponenty UI. Nie importuja siebie.

## Finalne kontrakty v1

Przed pierwszym wydaniem wszystkie kontrakty zostaja atomowo oznaczone jako
pierwsza publiczna wersja:

- launcher v1,
- protocol v1,
- `tdw.ux-inspector-capture` v1,
- `tdw.ux-inspector-export` v1,
- result contract `ux-inspector-result-v1`.

Nie istnieja migratory, dual-read, aliasy, stare schematy ani alternatywny
transport. Kazda inna wersja jest odrzucana przez receiver/import/backend.

## Browser Tools

### Dystrybucja

Na ekranie `/ux-inspector` przycisk `Browser Tools` otwiera modal zgodny z UI
TDW. Modal zawiera krotka instrukcje oraz przeciagany element `TDW Browser
Tools`. Bookmarklet jest generowany z originu aktualnej instancji i laduje
wylacznie `/browser-tools/loader.js?v=1.0.0`.

Publikowane zasoby Browser Tools to uniwersalny loader, protocol, runtime i
README. Shell ma rejestr akcji, aby w przyszlosci mogl obslugiwac kolejne
narzedzia, ale pierwsza akcja to UX Inspector.

### Capture i bezpieczenstwo

Runtime tworzy jeden capture v1 w profilu `ELEMENT_CONTEXT` albo
`FORM_DIAGNOSTICS`. Capture zawiera ograniczony fingerprint targetu, stany,
przodkow i metadata strony. Diagnostyka formularza zamraza dozwolone wartosci
najblizszego formularza, wlacznie z kontrolkami `type=hidden`, oraz
`ValidityState`, ale bez hasel, tokenow, plikow, cookies, storage i ruchu
sieciowego.

Transfer uzywa exact `origin`, exact `source`, nonce i protocol v1. Klik nie
wykonuje akcji badanej strony. Popup failure, timeout, COOP/CSP albo zly
handshake zatrzymuja operacje i usuwaja payload bez innego kanalu danych.

## Formularz UX Inspectora

Application, Branch, View i `Load views` maja ten sam wzorzec wizualny i
interakcyjny co UI Explorer:

- Application ustawia domyslny Branch i czysci stary View,
- potwierdzenie Branch automatycznie laduje katalog,
- View jest wybierany z katalogu przypietego do immutable revision,
- automatyczny load korzysta z cache,
- jawny `Load views` wymusza refresh aktualnego scope'u,
- Model AI i Reasoning effort sa przed polem pytania,
- zmiana upstream selection uniewaznia wszystkie zalezne pola.

Cache katalogu jest neutralna capability `frontendcatalog`, kluczowana przez
system, repository scope, ref i limity discovery. UX Inspector nie posiada
wlasnego formatu cache. UI Explorer zachowuje ten sam kontrakt odswiezenia.

## Backend i source research

1. Request zawiera capture v1, Application, Branch, View, oczekiwana immutable
   revision, pytanie, model i reasoning effort.
2. `QUEUED` jest zapisane przed dispatch.
3. `UxInspectorTargetResolver` buduje ranked candidates i `sourceBinding` z
   deduplikowanych selector signals, zachowujac odleglosc component boundaries
   i nie zgadujac zakresu po pierwszym ogolnym tagu.
4. `NOT_FOUND` nie blokuje AI: pozostaje jawna luka i uruchamia celowany
   research z component source pack oraz repository tools. `AMBIGUOUS`
   pozostaje jawne w odpowiedzi i przekazuje ograniczone evidence 2-3
   najlepszych kandydatow wraz z bindingami.
5. Initial context zawiera focused slice, nieblokujacy pack wszystkich
   odnalezionych komponentow z pelnymi dostepnymi plikami TS/HTML oraz komplet
   nazw sciezek pierwszych
   czterech poziomow jednego wybranego repozytorium, pelna tresc obecnego
   `.github/copilot-instructions.md` oraz naglowki `name` i `description`
   skilli wykrytych w `.github/skills`, `.claude/skills` i `.agents/skills`.
6. Hidden context przypina neutralne GitLab tools do jednego projektu,
   Branch i commita, bez ograniczenia do Operational Context path prefixes.
7. AI moze listowac, wyszukiwac i czytac dowolna bezpieczna sciezke w tym repo.
   Plik z initial evidence albo rzeczywiscie odczytany na pinned commit jest
   referencja `source`; sciezka tylko wywnioskowana pozostaje widoczna jako
   `source-unverified` i nie blokuje calego raportu.
8. Prompt rozroznia pytanie operatora, runtime observation i source evidence,
   wymaga doczytania kazdego skilla materialnego dla researchu, sprawdzenia
   mechanizmow przekrojowych oraz domkniecia lancucha potrzebnego do odpowiedzi
   i narracji biznesowej.
9. Report tools zapisuja naglowek, jedna sekcje oraz metadata; finalny tekst
   modelu nie jest fallbackiem wyniku.

Feature nie tworzy repository tools o nazwach specyficznych dla UX Inspectora.
Korzysta z uniwersalnych `gitlab_list_repository_tree`,
`gitlab_list_repository_files`, `gitlab_search_repository_files`,
`gitlab_read_repository_file` i `gitlab_read_repository_file_chunk`.

## Wynik i historia

Glowny workspace reuse'uje uklad UI Explorera oraz wspolny prawy aside dla
progress, pracy AI, tool evidence i usage.

Finalny renderer:

- pokazuje jedna sekcje odpowiedzi,
- nie pokazuje osobnej sekcji metadata raportu,
- scala report-level i section-level References, Visibility limits, Gaps,
  Open questions i Warnings z deduplikacja,
- pokazuje te metadata tylko raz pod odpowiedzia, po prawej stronie jak w UI
  Explorerze,
- zachowuje copy oraz download Markdown.

Historia i import nie tworza osobnej karty read-only. Application, Branch,
Revision, View, Model i pytanie sa pokazane drobna czcionka w stopce karty
runu. Import odtwarza wynik read-only bez wznawiania sesji AI.

## Granice i konsumenci

- `features.uxinspector` posiada capture, request/result, resolver, prompt,
  policy, job, report i import/export.
- `frontend/features/ux-inspector` posiada receiver, formularz, run i wynik.
- `frontendcatalog` posiada neutralny katalog Application/View oraz cache.
- `frontend/public/browser-tools` posiada uniwersalny launcher runtime.
- `integrations.gitlab.frontend` posiada route graph i source slices.
- `agenttools` oraz `aiplatform` pozostaja neutralne.
- UI Explorer nie zna capture ani Browser Tools; jego zachowanie nie zmienia
  sie poza reuse neutralnego cache/refresh.

Konsumenci zmiany to UX Inspector API/UI, UI Explorer catalog adapter,
neutralny frontend catalog, Browser Tools assets, Analysis History, static
routing oraz finalny bundle w `src/main/resources/static`.

## Ryzyka i fail-closed

- Strona moze obserwowac albo zaklocic kod dzialajacy w main world; runtime nie
  zawiera sekretow, a capture jest niezaufany.
- Selector i DOM ancestry sa sygnalem lokalizacyjnym, nie dowodem ownership.
- Origin/route nie dowodzi wdrozonej rewizji; operator potwierdza Branch/View,
  a backend przypina commit.
- Drzewo repozytorium jest mapa, nie dowodem. Brak kompletnego drzewa blokuje
  przygotowanie.
- Brak poprawnego reportu, targetu, persistence albo zgodnosci wersji blokuje
  run zamiast uruchamiac alternatywna sciezke.

## Macierz testow

| Warstwa | Wymagana weryfikacja |
|---|---|
| Browser Tools | v1 schema, oba profile, redakcje/limity, shield, lifecycle, handshake, replay i cleanup |
| Modal | bookmarklet z originu TDW, poprawny JavaScript, brak dodatkowego sposobu uruchomienia |
| Receiver | exact origin/source/nonce, tylko capture v1, limit 128 KiB, fragment cleanup |
| Catalog | progresywny Application -> Branch -> View, cache hit, scoped refresh, pinned revision |
| Backend | target resolution, source binding, repository tree, tool scope, report validator |
| Job/history | `QUEUED` przed dispatch, strict export/import v1, read-only history |
| Wynik | jedna sekcja, jedno scalone metadata pod odpowiedzia, brak osobnej karty read-only |
| Packaging | Angular tests/build, Browser Tools Node tests, `FrontendPageTest`, backend-dev package |

Wszystkie nowe fixture'y uzywaja fikcyjnej domeny CRM.

## Inkrement: staly kontrakt tlumaczenia odpowiedzi na jezyk biznesowy

Status: done

Zatwierdzenie: uzytkownik zatwierdzil 2026-09-16 umieszczenie kompletnego
kontraktu bezposrednio w canonical initial prompt, bez nowego runtime skilla i
bez zmiany publicznego kontraktu `answer`.

### Baseline

- Prompt wymaga biznesowo czytelnej odpowiedzi, ale nie definiuje procedury
  przejscia od source evidence do zachowania, regul, testow akceptacyjnych ani
  instrukcji obslugi.
- Techniczne nazwy mechanizmow przekrojowych sa potrzebne w researchu, lecz
  moga przenikac do finalnej narracji.
- Interakcje HTTP moga byc opisywane nazwami wygenerowanych metod zamiast
  obserwowalnym kontraktem `metoda + path`.
- Runtime skills sa celowo wylaczone dla UX Inspectora; pozostaja wylaczone.

### Conformance delta

- Canonical initial prompt otrzymuje staly business-first writing contract.
- Finalna narracja rozdziela odpowiedzialnosc `frontend` i `backend`; nie
  uzywa ogolnego slowa "system" jako wykonawcy zachowania.
- Kod potwierdza aktualne zachowanie, ale sam nie ustanawia wymagania. Prompt
  rozroznia zachowanie potwierdzone, regule odtworzona z implementacji,
  kandydackie kryterium akceptacji, decyzje wymagajaca potwierdzenia i brak.
- Materialna interakcja HTTP jest opisywana przez zweryfikowane `METHOD path`,
  trigger, cel oraz efekt we frontendzie. Nazwa implementacji klienta pozostaje
  tylko evidence; frontend nie dowodzi backendowej walidacji ani persistence.
- Odpowiedz dobiera forme do pytania: wyjasnienie, reguly, kandydackie kryteria
  akceptacji, scenariusze testowe albo instrukcje obslugi. Nie generuje
  automatycznie wszystkich formatow.

### Konsumenci i kompatybilnosc

- `UxInspectorPromptPreparationService` jest jedynym zmienianym runtime
  konsumentem.
- `UxInspectorCopilotAnalysisProvider`, report tools, mapper wyniku, job,
  historia oraz import/export nadal transportuja jedna sekcje Markdown
  `answer`; ich DTO i wersje pozostaja bez zmian.
- Frontend renderuje ten sam kontrakt raportu i nie wymaga zmiany ani rebuilda.
- `UxInspectorCopilotRunRequestAssembler` nadal przekazuje
  `skillsEnabled=false`; nie dochodzi dodatkowy turn ani tool call `skill`.

### Macierz testow inkrementu

| Warstwa | Weryfikacja |
|---|---|
| Prompt | staly kontrakt zachowania, statusow ustalen, terminologii frontend/backend i zakazu "system" jako aktora |
| HTTP | metoda + zweryfikowany path, placeholdery, trigger, efekt, gateway oraz granica backend evidence |
| Wynik | adaptacyjne wyjasnienie/reguly/testy/instrukcja i obserwowalne scenariusze |
| Runtime | brak feature runtime skilla i zachowane `skillsEnabled=false` |
| Regresja | celowany test promptu, a przed przekazaniem pelne `mvn -q test` |

### Checklista inkrementu

- [x] Zaktualizowac need, runtime flow i lokalne niezmienniki UX Inspectora o
  staly business-first answer contract.
- [x] Dodac kompletny kontrakt do canonical initial prompt bez runtime skilla i
  bez zmiany publicznego reportu.
- [x] Rozszerzyc test promptu o semantyczne inwarianty translacji,
  frontend/backend oraz `METHOD path`.
- [x] Uruchomic test celowany i pelna regresje backendu, przejrzec diff oraz
  potwierdzic brak zmiany kontraktu frontend/import/export.

### Wynik weryfikacji inkrementu

- `mvn -q "-Dtest=UxInspectorPromptAndSkillsTest,UxInspectorCopilotRunRequestAssemblerTest" test`
  - PASS; 3 testy, zachowany `skillsEnabled=false`.
- `mvn -q test`
  - PASS; 341 raportow testowych, 1628 testow, 0 failures, 0 errors, 1 skipped.
- `git diff --check`
  - PASS; brak bledow whitespace.
- Frontend, publiczny result contract oraz import/export nie zostaly zmienione;
  testy Angulara i produkcyjny build nie sa wymagane dla tego backend-only
  inkrementu.

## Inkrement: nieblokujace referencje wywnioskowane przez model

Status: done

Zatwierdzenie: uzytkownik zatwierdzil 2026-09-16 zachowanie raportu, gdy model
wskaze referencje do pliku, ktory nie zostal odczytany przez tool ani dolaczony
jako zweryfikowane initial evidence.

### Baseline i conformance delta

- Mapper obecnie odrzuca caly raport, gdy choc jedna source reference nie
  nalezy do zbioru zweryfikowanych initial/tool reads.
- Po zmianie poprawna, ale niepotwierdzona referencja pozostaje w raporcie jako
  `source-unverified` wraz z widocznym warningiem; nie jest traktowana jako
  zweryfikowany dowod.
- Niezweryfikowana referencja obniza `high` do `medium` i powoduje wynik
  `PARTIAL`, ale nie usuwa merytorycznej odpowiedzi.
- Nie zmienia sie publiczny ksztalt `AnalysisReportReference`, result DTO,
  export/import ani renderer. Istniejace pola `type`, `description` i
  `warnings` niosa status weryfikacji.
- Walidacja scope'u i przypietego commita dla referencji oznaczanych jako
  `source` pozostaje bez zmian; zmienia sie tylko skutek braku takiego dowodu.

### Checklista inkrementu

- [x] Zmienic `UxInspectorReportMapper`, aby brak potwierdzonego odczytu
  degradowal pojedyncza referencje zamiast odrzucac caly raport.
- [x] Zaktualizowac canonical prompt i dokumentacje runtime o rozroznienie
  `source` oraz `source-unverified`.
- [x] Rozszerzyc testy mappera i providera o niezweryfikowana, zweryfikowana i
  niepoprawna referencje, a nastepnie uruchomic pelna regresje backendu.

### Wynik weryfikacji inkrementu

- `mvn -q
  "-Dtest=UxInspectorReportMapperTest,UxInspectorPromptAndSkillsTest,UxInspectorCopilotAnalysisProviderTest,UxInspectorJobServiceTest,UxInspectorImportServiceTest"
  test`
  - PASS; zweryfikowano degradacje referencji, warning, confidence, status
    `PARTIAL`, zachowanie referencji z poprawnego commita oraz prompt.
- `mvn -q test`
  - PASS, exit code 0.
- `mvn -q "-Dtest=PackageDependencyGuardTest" test`
  - PASS; zmiana pozostaje w feature-owned mapperze i nie dodaje nowych
    zaleznosci miedzy pakietami.
- Nie zmieniono wspolnego DTO ani frontendu; `type`, `description` i
  `warnings` istniejacego kontraktu przenosza jawny status referencji.

## Checklista realizacji

- [x] Dodac modal Browser Tools z przeciaganym bookmarkletem na ekranie UX
  Inspectora i pozostawic tylko jeden sposob uruchomienia.
- [x] Ujednolicic Application, Branch, View, Load views, Model AI i Reasoning
  effort z UI Explorerem.
- [x] Dodac wspolny neutralny cache katalogu widokow oraz jawny scoped refresh.
- [x] Usunac osobna karte read-only i przeniesc scope/pytanie do stopki runu.
- [x] Zastapic generyczny panel wyniku jednosekcyjnym rendererem UX Inspectora
  i pokazac scalone metadata tylko raz pod odpowiedzia.
- [x] Ustawic launcher/protocol/capture/export na strict v1 bez kompatybilnosci.
- [x] Usunac nieuzywane statyczne wejscia i kod alternatywnych sposobow
  uruchomienia Browser Tools.
- [x] Dodac testy Browser Tools, bookmarkleta, formularza, cache, strict v1,
  wyniku i statycznego routingu.
- [x] Wlaczyc do diagnostyki formularza kontrolki `type=hidden` i zachowac dla
  nich te same reguly redakcji wartosci wrazliwych co dla pozostalych pol.
- [x] Naprawic target resolver: uzyc selector candidates bez podwojnego
  scoringu, zachowac kolejnosc component boundaries, usunac fallback do
  pierwszego ogolnego tagu i ugruntowac `AMBIGUOUS` 2-3 candidate slices z
  bindingami.
- [x] Dodac do initial prompt zweryfikowana tresc repository-wide Copilot
  instructions, katalog naglowkow project skills oraz obowiazek doczytania
  istotnych skilli i sprawdzenia mechanizmow przekrojowych.
- [x] Dodac nieblokujacy initial component source pack: uporzadkowany manifest
  wszystkich komponentow odnalezionych w screen reachability graph, relacje
  grafu, pelne zweryfikowane pliki TS/HTML wybranej sciezki oraz jawne braki plikow, granic
  komponentow i nierozwiazane diagnostics. Brak pojedynczego komponentu albo
  pliku nie zatrzymuje analizy i nie uruchamia alternatywnego flow.
- [x] Rozszerzyc kanoniczny prompt o zasady wykorzystania component source
  packu przed dodatkowymi odczytami, bez traktowania statycznego grafu jako
  dowodu runtime ancestry, oraz pokazac rzeczywista liczbe przygotowanych
  artefaktow w kroku preparation.
- [x] Pokryc source pack testami kolejnosci, deduplikacji, pelnych plikow,
  nieblokujacych brakow i integracji z promptem, a nastepnie uruchomic pelna
  regresje backendu.
- [x] Ograniczyc pelne pliki component source packu: dla `RESOLVED` wysylac
  tylko deterministycznie najlepsza sciezke target -> selected view po
  `TEMPLATE_CHILD`/`ROUTED_CHILD`, z warunkowym `DYNAMIC_COMPONENT` i bez
  `COMPONENT_REFERENCE` jako ancestry; pozostale komponenty zachowac w
  lekkim indeksie grafu.
- [x] Dla `AMBIGUOUS` wysylac unie najlepszych sciezek maksymalnie trzech
  kandydatow, a dla `NOT_FOUND` tylko component selected view; brak sciezki
  oznaczyc jawnie bez blokowania analizy.
- [x] Zaktualizowac prompt, dokumentacje i testy source packu oraz ponownie
  uruchomic pelna regresje backendu.
- [x] Uruchomic pelne testy Angulara, produkcyjny build frontendu oraz
  `mvn -q -Pbackend-dev clean package` i zapisac wynik ponizej.

## Inkrement: kompaktowy component source pack

Status: done

Zatwierdzenie: uzytkownik zatwierdzil 2026-09-17 wykonanie pierwszego kroku
optymalizacji kosztu UX Inspectora: bezstratna kompakcje indeksu komponentow i
relacji, bez ograniczania liczby komponentow albo pelnych plikow wybranej
sciezki.

Klasyfikacja: **L1**. Zmienia sie model-facing format feature-owned artifactu,
ale nie publiczne API, DTO, export v1, source selection, tool schema, hidden
scope, report ani UI.

### Baseline

- Component source pack zawiera wszystkie komponenty statycznego screen
  reachability graphu w kolejnosci depth/BFS.
- `RESOLVED`, `AMBIGUOUS` i `NOT_FOUND` zachowuja obecna strategie wyboru
  pelnych plikow; pozostale komponenty sa `INDEX_ONLY`.
- Kazdy komponent powtarza `dependencyIds`, `childComponentIds`,
  `incomingEdges` i `outgoingEdges`, przez co ta sama relacja moze wystapic
  kilkukrotnie, a komponenty bez relacji nadal emituja puste pola.
- Testy baseline
  `UxInspectorComponentSourcePackArtifactServiceTest` i
  `UxInspectorPromptAndSkillsTest` przechodza przed zmiana.

### Conformance delta

- Wlasciciel: bez zmian, `features.uxinspector.ai`.
- Publiczne API/DTO, job state, persistence/export, report i frontend: bez
  zmian.
- Context/evidence: ten sam komplet komponentow, plikow, brakow i relacji.
- Prompt/artifacts: `component-source-pack.md` dostaje zwarta tabele wszystkich
  komponentow oraz jedna deterministyczna, deduplikowana liste relacji.
- Relacje obecne tylko w `childComponentIds` albo `dependencyIds` pozostaja
  widoczne jako jawne relacje deklarowane; relacje juz obecne w grafie nie sa
  powtarzane.
- Tools/policy/hidden scope/budzet: bez zmian.
- Zaleznosci pakietowe: bez zmian; nie powstaje shared abstraction ani import
  sibling feature'a.
- Konsumenci: canonical prompt UX Inspectora, diagnostyczna mapa artifactow i
  testy feature'a. Publiczny wynik i UI nie parsują wewnetrznego formatu packu.
- Kompatybilnosc: artifact jest przygotowywany od nowa dla kazdego runu i nie
  ma publicznej wersjonowanej migracji; schema/version pozostaja bez zmian,
  poniewaz semantyka danych sie nie zmienia.
- Znany drift: brak nowego driftu; zakres nie dotyka source selection ani
  budzetu researchu.

### Macierz testow

| Zakres | Dowod |
|---|---|
| komplet i kolejnosc komponentow | test tabeli dla `RESOLVED` z siblingiem `INDEX_ONLY` |
| deduplikacja relacji grafu | jedna reprezentacja relacji mimo powtorzenia w graph/adjacency |
| zachowanie relacji tylko deklarowanych | osobne `DECLARED_CHILD` i `DECLARED_DEPENDENCY` |
| strategie pelnych plikow | istniejace testy `RESOLVED`, `AMBIGUOUS`, `NOT_FOUND` i brak view |
| prompt contract | `UxInspectorPromptAndSkillsTest` |
| granice pakietow | `PackageDependencyGuardTest` oraz diff importow |
| regresja backendu | `mvn -q test` |

### Kroki

- [x] Zastapic per-component bloki zwarta tabela i jedna znormalizowana lista
  relacji, zachowujac wszystkie komponenty, statusy plikow, limitations oraz
  relacje niewystepujace w graph edges.
- [x] Rozszerzyc testy o brak duplikatow, relacje deklarowane i kontrakt
  promptu, a nastepnie uruchomic testy celowane i pelna regresje backendu.
- [x] Zaktualizowac kanoniczny runtime flow, wykonac architecture diff i
  zapisac wynik weryfikacji.

### Wynik weryfikacji inkrementu

- Baseline przed zmiana:
  `mvn -q "-Dtest=UxInspectorComponentSourcePackArtifactServiceTest,UxInspectorPromptAndSkillsTest" test`
  - PASS.
- Testy celowane po zmianie:
  `mvn -q "-Dtest=PackageDependencyGuardTest,UxInspectorComponentSourcePackArtifactServiceTest,UxInspectorPromptAndSkillsTest,UxInspectorCopilotRunRequestAssemblerTest" test`
  - PASS.
- Pelna regresja backendu: pierwszy przyrostowy `mvn -q test` wykryl
  zanieczyszczony `target/test-classes` i zakonczyl sie bledami ladowania
  fixture'ow innych feature'ow. Powtorzenie zgodnie z procedura dla stale
  output, `mvn -q clean test`, przeszlo: 1640 testow, 0 failures, 0 errors,
  1 skipped.
- Architecture diff: bez zmian zaleznosci pakietowych, publicznego API, DTO,
  persistence/export, UI, tool schema, hidden scope i strategii wyboru pelnych
  plikow.
- Pomiar na zalaczonym eksporcie z 104 komponentami: sekcja indeksu i relacji
  maleje z 167863 do 91903 znakow, czyli o 45,3%. Zachowanych jest wszystkich
  104 komponentow i 654 unikalnych relacji; poczatkowy prompt maleje
  szacunkowo o 75960 znakow, czyli o 24,8% wzgledem 306391 znakow.
- Nie uruchamiano testow ani builda Angulara, poniewaz zmiana jest wylacznie
  backendowym, wewnetrznym formatem logical artifactu i nie zmienia kontraktu
  ani integracji z frontendem.

## Inkrement: sugestia View na podstawie route capture

Status: done

Zatwierdzenie: uzytkownik zatwierdzil 2026-09-17 automatyczne uzupelnienie
selektora View na podstawie URL-u zebranego w capture.

Klasyfikacja: **L1**. Zmienia sie feature-owned zachowanie formularza UX
Inspectora, ale nie publiczne API, DTO, capture v1, job, source revision,
resolver targetu, AI runtime, report ani export.

### Baseline

- Capture v1 zawiera znormalizowane `page.path`, bez wartosci query; obsluguje
  tez bezpiecznie rozpoznana trase hash.
- Katalog wybranego Application/Branch zwraca `viewId` oraz `routePattern`, ale
  operator zawsze wybiera View recznie.
- Wybor Application albo zmiana Branch czysci View i katalog; zaladowany View
  pozostaje zwiazany z pokazana immutable source revision.
- Capture nie zawiera zaufanego mapowania `origin -> systemId`, wiec nie moze
  samodzielnie zmieniac Application ani Branch.
- Celowane testy Angulara przed zmiana zostaly zablokowane przez ograniczenia
  sandboxa podczas odczytu zaleznosci i plikow SCSS; nie odnotowano failure
  funkcjonalnego przed zmiana.

### Conformance delta

- Cel: ograniczyc reczne wyszukiwanie View, gdy runtime route jednoznacznie
  odpowiada trasie z zaladowanego katalogu.
- Wlasciciel: frontend `features/ux-inspector`; nie powstaje shared helper ani
  zaleznosc od UI Explorera.
- Publiczne API/DTO, capture, context/evidence, prompt/artifacts/skills,
  tools/policy/hidden scope, report/result, job state, persistence/export:
  bez zmian.
- UI: po dostepnosci capture i katalogu lokalny matcher wybiera tylko jedyny
  najlepszy View. Statyczny segment ma pierwszenstwo przed parametrem, a
  wildcard jest najslabszy. Remis albo brak dopasowania pozostawia View pusty.
- Scope: matcher nigdy nie zmienia Application ani Branch, nie dopasowuje po
  `origin` i nie omija zwiazania View z source revision.
- Operator widzi, ze wybor zostal zasugerowany z route capture, moze go
  zastapic recznie, a uruchomienie joba pozostaje jawnym potwierdzeniem calego
  scope'u formularza.
- Konsumenci: tylko formularz i testy UX Inspectora.
- Zaleznosci i architecture drift: bez nowych zaleznosci i bez rozszerzenia
  istniejacego driftu.

### Macierz testow

| Zakres | Dowod |
|---|---|
| trasa statyczna | exact View jest wybierany automatycznie |
| parametr trasy | `/contacts/:contactId` pasuje do zredagowanego albo runtime ID |
| specyficznosc | `/contacts/new` wygrywa z `/contacts/:contactId` |
| niejednoznacznosc | rowny najlepszy wynik nie ustawia View |
| brak dopasowania | selektor pozostaje pusty |
| decyzja operatora | reczny wybor usuwa oznaczenie sugestii |
| regresja UI | celowane testy UX Inspectora, pelne testy Angulara i build produkcyjny |

### Kroki

- [x] Dodac feature-owned matcher route oraz automatyczna sugestie po
  dostepnosci capture i katalogu, bez zmiany Application/Branch.
- [x] Pokazac pochodzenie automatycznego wyboru i zachowac reczny override.
- [x] Rozszerzyc testy o exact, parametr, priorytet, remis, brak dopasowania i
  reczny override.
- [x] Zaktualizowac runtime flow, wykonac architecture diff oraz uruchomic
  celowane i pelne testy Angulara wraz z produkcyjnym buildem.

### Wynik weryfikacji inkrementu

- Celowane testy UX Inspectora:
  `npm --prefix frontend test -- --watch=false --include src/app/features/ux-inspector/utils/ux-inspector-view-route-match.utils.spec.ts --include src/app/features/ux-inspector/state/ux-inspector.facade.spec.ts --include src/app/features/ux-inspector/pages/ux-inspector-page/ux-inspector-page.spec.ts`
  - PASS, 3 pliki testowe i 14 testow.
- Pelna regresja frontendu: `npm --prefix frontend test -- --watch=false`
  - PASS, 74 pliki testowe i 611 testow.
- Produkcyjny build: `npm --prefix frontend run build`
  - PASS; aktualny bundle zapisany w `src/main/resources/static`.
- Architecture diff: brak zmian publicznego API/DTO, capture v1, joba,
  persistence/export, AI runtime, tools, hidden scope i zaleznosci pakietowych.
  Dopasowanie pozostaje lokalne dla formularza UX Inspectora i korzysta tylko
  z juz dostepnych `capture.page.path` oraz `routePattern`.
- Nie uruchamiano regresji backendu, poniewaz zmiana nie dotyka kontraktu
  backend-frontend, routingu zasobow statycznych ani integracji backendowej.

## Wynik weryfikacji

- Browser Tools: `node --test frontend/tests/browser-tools/browser-tools.test.mjs`
  - PASS, 10/10 testow.
- Frontend: `npm --prefix frontend test -- --watch=false`
  - PASS, 73 pliki testowe i 601 testow.
- Frontend: `npm --prefix frontend run build`
  - PASS, produkcyjny bundle zapisany w `src/main/resources/static`.
- Backend, testy celowane po zmianie kontraktow, cache i statycznego routingu:
  - PASS dla `FileSystemFrontendViewCatalogCacheTest`,
    `UxInspectorInputOptionsControllerTest`,
    `UxInspectorInputOptionsServiceTest`, `UxInspectorCaptureContractTest`,
    `UxInspectorTargetResolverTest`, `UxInspectorPromptAndSkillsTest`,
    `UxInspectorRepositoryTreeArtifactServiceTest`,
    `UxInspectorRepositoryGuidanceArtifactServiceTest`,
    `UxInspectorCopilotRunRequestAssemblerTest`,
    `UxInspectorTargetToolsTest`, `UxInspectorJobControllerTest`,
    `UxInspectorImportServiceTest`, `UiExplorerScreenCatalogServiceTest` i
    `FrontendPageTest`.
- Pelny pakiet: `mvn -q -Pbackend-dev clean package`
  - PASS.
- Pelna regresja backendu po dodaniu repository guidance: `mvn -q test`
  - PASS.
- Testy celowane component source packu, promptu, provider flow, referencji,
  joba i importu:
  - PASS dla `UxInspectorComponentSourcePackArtifactServiceTest`,
    `UxInspectorPromptAndSkillsTest`,
    `UxInspectorCopilotRunRequestAssemblerTest`,
    `UxInspectorCopilotAnalysisProviderTest`,
    `UxInspectorReportMapperTest`, `UxInspectorJobServiceTest` oraz
    `UxInspectorImportServiceTest`.
- Pelna regresja backendu po dodaniu component source packu: `mvn -q test`
  - PASS, 341 raportow test suites bez failures/errors.
- Testy celowane hybrydowego component source packu, promptu i integracji
  Copilot/report: `mvn -q
  "-Dtest=UxInspectorComponentSourcePackArtifactServiceTest,UxInspectorPromptAndSkillsTest,UxInspectorCopilotRunRequestAssemblerTest,UxInspectorCopilotAnalysisProviderTest,UxInspectorReportMapperTest"
  test`
  - PASS; pokryto `RESOLVED`, `AMBIGUOUS`, `NOT_FOUND`, brak komponentu widoku,
    indeks komponentow rownoleglych i odrzucenie `COMPONENT_REFERENCE` jako
    ancestry.
- Pelna regresja backendu po ograniczeniu pelnych zrodel do wybranych sciezek:
  `mvn -q test`
  - PASS, exit code 0; 341 raportow test suites bez failures/errors.

Pilot jakosciowy z rzeczywistym Copilot/GitLab pozostaje osobnym kryterium
produktowym. Powinien objac pytania o walidacje, pochodzenie danych,
dostepnosc/stany i skutek akcji oraz porownanie z szerokim runem UI Explorera.
