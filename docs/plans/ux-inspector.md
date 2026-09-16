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
| Source preparation | reachability wielu sekcji | target resolution + focused slice |
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
3. `UxInspectorTargetResolver` buduje ranked candidates i `sourceBinding`.
4. `NOT_FOUND` blokuje AI; `AMBIGUOUS` pozostaje jawne w odpowiedzi.
5. Initial context zawiera focused slice oraz komplet nazw sciezek pierwszych
   czterech poziomow jednego wybranego repozytorium.
6. Hidden context przypina neutralne GitLab tools do jednego projektu,
   Branch i commita, bez ograniczenia do Operational Context path prefixes.
7. AI moze listowac, wyszukiwac i czytac dowolna bezpieczna sciezke w tym repo,
   ale cytowac moze tylko plik rzeczywiscie odczytany na pinned commit.
8. Prompt rozroznia pytanie operatora, runtime observation i source evidence,
   wymaga domkniecia lancucha potrzebnego do odpowiedzi i narracji biznesowej.
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
- [x] Uruchomic pelne testy Angulara, produkcyjny build frontendu oraz
  `mvn -q -Pbackend-dev clean package` i zapisac wynik ponizej.

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
    `UxInspectorJobControllerTest`, `UxInspectorImportServiceTest`,
    `UiExplorerScreenCatalogServiceTest` i `FrontendPageTest`.
- Pelny pakiet: `mvn -q -Pbackend-dev clean package`
  - PASS.

Pilot jakosciowy z rzeczywistym Copilot/GitLab pozostaje osobnym kryterium
produktowym. Powinien objac pytania o walidacje, pochodzenie danych,
dostepnosc/stany i skutek akcji oraz porownanie z szerokim runem UI Explorera.
