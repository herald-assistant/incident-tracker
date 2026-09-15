# UI Explorer - analiza wskazanego elementu przez rozszerzenie Chrome

Status: in-progress

Source need: [UI Explorer - pytania o element uruchomionej strony](../needs/ui-explorer-browser-element-analysis.md)

Klasyfikacja: **L3**. Zmiana dodaje nowy artefakt kliencki, nowa granice
zaufania pomiedzy dowolna strona, rozszerzeniem i TDW, nowy publiczny ingress
REST oraz nowy rodzaj wyniku UI Explorera. Obejmuje tez zmiane L2 w lifecycle
joba i lokalnej historii oraz zmiane L1 promptu, skilla i kontraktu wyniku.

Plan nie jest zgoda na implementacje. Kazdy inkrement wymaga osobnej akceptacji
zgodnie z `docs/AGENTS.md`.

## Decyzja kierunkowa

Rozszerzenie Chrome ma byc **zdalnym selektorem i starterem joba**, a nie
drugim klientem AI ani miniaturowym UI Explorera. Jego odpowiedzialnosc konczy
sie na:

1. bezpiecznym wskazaniu elementu na uruchomionej stronie,
2. zebraniu ograniczonego i zredagowanego kontekstu DOM oraz strony,
3. przyjeciu pytania, modelu i `reasoningEffort`,
4. wykonaniu preflightu i uruchomieniu joba przez REST,
5. pokazaniu potwierdzenia z identyfikatorem oraz linkiem do TDW.

Rozpoznanie systemu, repository, rewizji, ekranu i plikow jest odpowiedzialnoscia
backendu. Analiza kodu, tools, report, usage, wynik i historia pozostaja w
feature `features.uiexplorer` i platformie TDW. Rozszerzenie nie odpytuje joba
do zakonczenia i nie pokazuje odpowiedzi AI.

Pierwszy zatwierdzony inkrement jest uniwersalnym starterem rozszerzenia i
lokalnym UX spike'iem UI Explorera. Nie laczy sie jeszcze z REST ani AI.
Rozszerzenie jest projektowane jako host wielu niezaleznych feature modules i
site integrations: UI Explorer jest pierwszym modulem globalnym, a przyszle
rozszerzenia Confluence, GitLab albo innych stron beda rejestrowane przez ten
sam lifecycle, permission model, settings i message bus bez dopisywania ich
logiki do UI Explorera.

```text
badana strona HTTP(S)
  -> content script: gest, highlight, modal, sanitizacja
  -> service worker: autoryzowany klient tylko jednego TDW
  -> POST /api/ui-explorer/browser/preflight
  -> POST /api/ui-explorer/browser/jobs
  -> browser ingress + target/source resolver
  -> istniejacy screen catalog i Screen Reachability
  -> Element Focus Context
  -> platforma AI + read-only GitLab tools + report tools
  -> UI Explorer / Analysis History w TDW
```

## Co zachowujemy, a co korygujemy ze starego draftu

Poprzednia wersja planu trafnie rozpoznawala, ze:

- sciezka DOM nie jest logicznym drzewem komponentow Angulara,
- repository i ref nie moga byc przyjmowane z przegladarki jako zaufany scope,
- source musi byc przypiety do immutable revision przed AI,
- analiza ma korzystac z read-only tools i jawnych visibility limits.

Nie nalezy jednak implementowac starego draftu wprost. Korygujemy go, poniewaz:

- wymaganym gestem jest `Ctrl` + `Alt`, a nie samo `Ctrl`,
- wymagany jest modal z pytaniem, modelem i `reasoningEffort` oraz start
  asynchronicznego joba, czego stary przeplyw nie domykal,
- screenshot nie jest wymaganiem i nie wchodzi do MVP,
- `window.ng`, source maps i specjalny debug build nie sa potrzebne do MVP,
- obecny UI Explorer ma juz Screen Reachability z route chain, template content,
  component BFS, dependencies i source references; najpierw nalezy dopasowac
  sygnaly DOM do tego grafu, a nie budowac globalny selector index,
- publiczny payload nie moze zawierac wskazanego przez klienta repository ani
  brancha,
- haslo „na kazdej stronie” musi uwzgledniac model uprawnien i strony
  chronione przez Chrome,
- brakowalo bezpiecznego powiazania rozszerzenia z sesja operatora,
- obecny UI Explorer zapisuje lokalnie dopiero terminalne snapshoty, co nie
  spelnia wymagania „job od razu widoczny w historii”.

## Baseline przed zmiana

### Publiczne wejscie i wynik

Obecny UI Explorer dokumentuje jeden wybrany ekran/scenariusz w przypietej
rewizji. `POST /api/ui-explorer/jobs` przyjmuje m.in. `systemId`, `branch`,
`screenId`, `sourceRevision`, tryby osmiu sekcji, opcjonalny opis scenariusza,
model i `reasoningEffort`. Nieznane pola sa odrzucane. Wynik
`UiExplorerResultResponse` jest funkcjonalna dokumentacja osmiu sekcji.

Nowy przypadek uzycia zaczyna sie od pytania o element uruchomionej strony.
Nie jest to tylko inny sposob wypelnienia obecnego formularza: ma inne wejscie,
inna jednostke analizy oraz krotszy, bezposredni kontrakt odpowiedzi.

### Kontekst i tools

Obecny feature przed AI buduje deterministyczny katalog ekranow i pelny Screen
Reachability dla wybranego routowanego poddrzewa. Kontekst zawiera juz material,
do ktorego mozna dopasowac selektor, tekst, role, template i route. Targeted
research preferuje:

- `gitlab_read_frontend_route_branch_slice`,
- `gitlab_read_frontend_typescript_symbol_slice`.

Generyczne GitLab search/read pozostaja fallbackiem dla materialnych luk.
Repository, ref, sciezki i `sliceRef` sa hidden contextem. Nowy ingress nie
zmienia tej granicy i nie dodaje browser-specific MCP toola.

### Job, historia i frontend

Job jest asynchroniczny i ma statusy `QUEUED`, `DISCOVERING_SCREEN`,
`BUILDING_CONTEXT`, `ANALYZING` oraz terminalne. Obecny
`UiExplorerLocalRunPersister` zapisuje tylko terminalny snapshot. Otworzenie
wyniku z `Analysis History` przywraca read-only snapshot i nie wznawia pollingu.
Export UI Explorera ma schemat `tdw.ui-explorer-export` v5.

### Auth

Autoryzacja Copilot/GitHub App jest obecnie zwiazana z backendowa sesja
operatora. Content script dziala w niezaufanym dokumencie i nie moze otrzymac
GitHub tokenu, cookies sesyjnych ani sekretu platformy.

## Zakres MVP

W zakresie sa:

- rozszerzenie Chrome Manifest V3 jako osobny, wersjonowany artefakt,
- jednorazowe nadanie dostepu do badanego originu i polaczenie z jednym TDW,
- aktywacja selektora przez przytrzymanie `Ctrl` + `Alt`,
- efektowne, dostepne podswietlenie elementu i bezpieczne przejecie klikniecia,
- modal w badanej stronie z pytaniem, modelem i `reasoningEffort`,
- wersjonowany, limitowany capture elementu, przodkow i metadanych strony,
- preflight mapujacy origin/route na skonfigurowany system i source,
- asynchroniczny job `ELEMENT_QUESTION` w feature UI Explorer,
- dopasowanie elementu do istniejacego Screen Reachability,
- report-first odpowiedz o wskazanym elemencie z evidence i ograniczeniami,
- natychmiastowy zapis accepted/progress snapshotu w lokalnej historii,
- otwarcie i dalsze sledzenie analizy w UI Explorerze TDW,
- wersjonowany export/import nowego rodzaju analizy bez psucia v5.

Pierwszy wspierany source resolver pozostaje zgodny z aktualna capability
Angular/Nx w `integrations.gitlab.frontend`, ale capture przegladarki nie moze
kodowac Angular-specific danych jako wymaganego kontraktu.

## Non-goals MVP

- Odpowiadanie, polling joba albo renderowanie evidence/usage wewnatrz
  rozszerzenia.
- Wykonywanie kliknietego elementu, automatyzacja badanego UI, wypelnianie
  formularzy, modyfikowanie kodu albo publikowanie zmian.
- Screenshoty, nagrywanie ekranu, przechwytywanie requestow sieciowych,
  cookies, storage, schowka lub credentiali badanego systemu.
- Zgadywanie repository, rewizji lub ekranu dla niezarejestrowanej albo
  wieloznacznej strony.
- Pelna rekonstrukcja runtime component tree i zaleznosc od debug API
  frameworka.
- Obsluga chronionych stron Chrome i dowolnych cross-origin iframe.
- Multi-repository traversal, follow-up chat i continuation sesji AI.
- Zastapienie obecnego, screen-centered UI Explorera opisanego w
  [planie UI Explorer](ui-explorer.md).

## Uczciwe znaczenie „dziala na kazdej stronie”

Interakcja moze dzialac na zwyklych stronach `http://` i `https://` po jawnym
nadaniu uprawnienia dla originu. Analiza kodu wystartuje tylko wtedy, gdy TDW
potrafi deterministycznie przypisac strone do zarejestrowanego, dozwolonego
frontendu i przypiac source revision.

MVP nie obiecuje dzialania na:

- `chrome://`, Chrome Web Store i innych stronach chronionych przegladarki,
- wbudowanym viewerze PDF,
- `file://` bez osobnego opt-in uzytkownika,
- kartach incognito bez osobnego opt-in,
- elementach wewnatrz cross-origin iframe bez uprawnienia do originu ramki,
- stronach bez mapowania na source dostepny dla TDW.

Chrome nie pozwala zarejestrowac `Ctrl` + `Alt` jako skrotu Commands API ze
wzgledu na konflikt z `AltGr`. Gest musi byc wykrywany przez juz wstrzykniety
content script. Samo `activeTab` nadaje czasowy dostep dopiero po wspieranym
gescie wobec rozszerzenia, np. kliknieciu jego ikony, i nie wystarcza do
globalnego aktywowania selektora samymi modyfikatorami.

## Architektura i ownership

### Artefakt rozszerzenia

Powstaje top-level `browser-extension/` z osobnym TypeScript buildem, testami,
manifestem i pipeline'em paczki. Nie jest czescia aplikacji Angular w
`frontend/` i nie jest kopiowany do `src/main/resources/static`.

Proponowany podzial:

```text
browser-extension/
  manifest.json
  src/platform/       runtime, registry, settings, permissions, message bus
  src/features/       niezalezne funkcje, pierwsza: ui-explorer
  src/integrations/   przyszle adaptery zachowania dla konkretnych stron
  src/content/        bootstrap feature modules na badanej stronie
  src/background/     permission, lifecycle i przyszly klient REST
  src/options/        adres TDW, pairing, stan polaczenia
  src/popup/          wlaczanie funkcji dla biezacego originu
  src/shared/         kontrakt capture, walidacja, limity
  demo/               kontrolowana, fikcyjna strona CRM
  test/               unit i testy kontraktow/runtime
```

Feature module deklaruje stabilne `id`, metadane dla popup/options, predicate
dopasowania strony oraz `mount/dispose`. Site integration jest takim samym
modulem z wezszym predicate origin/path. Registry jest jedynym miejscem
kompozycji; platform runtime nie importuje logiki konkretnej strony poza
rejestracja modulu.

Backendowe elementy pozostaja pod `features.uiexplorer`, np.:

```text
features.uiexplorer.browser.api
features.uiexplorer.browser.auth
features.uiexplorer.browser.capture
features.uiexplorer.browser.target
features.uiexplorer.elementcontext
features.uiexplorer.elementanalysis
```

Nie powstaja importy z `integrations`, `agenttools` ani `aiplatform` do
rozszerzenia. Feature moze reuse'owac obecne integracje i platforme zgodnie z
grafem zaleznosci. Ogolne kontrakty trafiaja do `shared` dopiero, gdy pojawi sie
drugi realny konsument.

### Przeplyw odpowiedzialnosci

| Krok | Wlasciciel | Odpowiedzialnosc |
|---|---|---|
| Permission i pairing | extension action/options + browser ingress | jawna zgoda, jeden skonfigurowany TDW, krotko zyjacy credential |
| Hover i wybor | content script | element pod kursorem, overlay, blokada natywnego klikniecia |
| Capture | content script | sanitizacja DOM i strony przed opuszczeniem karty |
| Transport | service worker | schema validation, timeout, request tylko do skonfigurowanego TDW |
| Preflight | UI Explorer browser ingress | auth, limit, mapowanie targetu i publiczny status readiness |
| Pinning | target/source resolver | system, repository/ref, immutable commit, screen candidates |
| Context | UI Explorer | Screen Reachability + `UiExplorerElementFocusContext` |
| AI | feature + platforma | prompt, skill, hidden scope, read-only tools, report-first wynik |
| Historia i wynik | UI Explorer + local workspace + Angular | status, evidence, usage, wynik, export/import |

## Model uprawnien Chrome

Manifest V3 zawiera minimalne stale uprawnienia. Docelowo:

- `storage` dla ustawien niesekretnych oraz `chrome.storage.session` dla
  credentialu sesyjnego,
- `scripting` i `optional_host_permissions` dla originow badanych stron,
- host permission tylko do dokladnie skonfigurowanego originu TDW,
- brak statycznego `<all_urls>` w `host_permissions`,
- brak `cookies`, `webRequest`, `debugger`, `tabCapture` i
  `externally_connectable` w MVP.

Pierwsze uzycie na originie zaczyna sie od klikniecia ikony rozszerzenia.
Popup wyjasnia zakres i prosi Chrome o optional host permission dla tego
originu. Po przyznaniu content script jest wstrzykiwany i kolejne wybory na
tym originie moga uzywac `Ctrl` + `Alt` bez klikania ikony. Odebranie
uprawnienia natychmiast wylacza dzialanie rozszerzenia na originie.

Alternatywa `activeTab` moze pozostac trybem „kliknij ikone, potem wybierz”,
ale nie realizuje doswiadczenia z automatycznym modyfikatorem i nie jest
domyslnym rozwiazaniem.

`chrome.storage.session` pozostaje dostepne tylko dla trusted extension
contexts; content script nie dostaje browser tokenu ani API do jego odczytu.

## Interakcja na stronie

### Maszyna stanow

```text
IDLE
  -- Ctrl+Alt down --> ARMED
ARMED
  -- pointermove --> HOVERING(target)
  -- modifiers up / Escape --> IDLE
HOVERING(target)
  -- pointermove --> HOVERING(next target)
  -- captured click --> FROZEN(target) -> MODAL
  -- modifiers up / Escape --> IDLE
MODAL
  -- cancel / Escape --> IDLE
  -- submit --> SUBMITTING
SUBMITTING
  -- 202 Accepted --> ACCEPTED -> IDLE
  -- recoverable error --> MODAL(error)
```

Aktywacja wymaga `event.ctrlKey && event.altKey` oraz
`!event.getModifierState('AltGraph')`, aby zwykly `AltGr` nie wlaczal
inspektora. Implementacja sledzi tez `blur`, zmiane widocznosci dokumentu i
utracone `keyup`, zeby nie zostawic selektora w stanie aktywnym.

Po aktywacji rozszerzenie wlacza przezroczysta, pelnoekranowa warstwe wyboru.
Warstwa przejmuje pointer event, a target pod nia jest znajdowany przez
`document.elementsFromPoint()` po pominieciu wezlow rozszerzenia. Dzieki temu
klik trafia w shield, nie w badany przycisk, link ani submit. Capture-phase
`preventDefault`, `stopPropagation` i `stopImmediatePropagation` pozostaja
druga linia ochrony. Sam ruch jest grupowany przez `requestAnimationFrame`, a
`event.composedPath()` pomaga rozpoznac granice dostepnego Shadow DOM.

Zwolnienie modyfikatorow przed kliknieciem usuwa highlight. Po przejetym
kliknieciu wybor pozostaje zamrozony na czas modala, niezaleznie od stanu
klawiszy.

### Highlight „wow”, zgodny z TDW

Overlay nie zmienia layoutu strony. Jego warstwa wizualna ma
`pointer-events: none`, a oddzielny przezroczysty shield jest aktywny tylko w
stanie wyboru. Calosc jest renderowana w izolowanym Shadow DOM ponad strona i
sklada sie z:

- precyzyjnego wewnetrznego obrysu `#0c66e4`,
- drugiego granatowego obrysu `#0747a6`,
- miekkiej poswiaty i polprzezroczystego wypelnienia w tonacji `#deebff`,
- czterech animowanych znacznikow naroznych,
- malej etykiety z rola/nazwa elementu, bez ujawniania wrazliwej tresci.

Animacja ma byc subtelna, bez migotania; przy `prefers-reduced-motion: reduce`
pozostaje efekt statyczny. Geometria jest aktualizowana przy scroll, resize,
layout shift i odpieciu elementu. Gdy target znika, wybor jest anulowany z
czytelnym komunikatem.

### Modal

Modal jest renderowany przez extension-owned host z izolacja stylow i warstwa
top-level (`dialog` tam, gdzie zachowuje sie stabilnie). Ma focus trap, poprawne
role/labels, obsluge klawiatury, `Escape` i przywrocenie poprzedniego focusu.
Tekst strony trafia do DOM wyjatkowo przez `textContent`, nigdy `innerHTML`.

Pola:

- wymagane `question`, po trim, maksymalnie 4000 znakow,
- `model` z projekcji wspolnego katalogu zwroconej przez preflight,
- `reasoningEffort` ograniczony do wartosci wspieranych przez wybrany model,
- read-only podsumowanie elementu, strony i wyniku preflightu,
- rozwijany podglad zredagowanych danych, ktore opuszcza karte,
- jedna glowna akcja „Rozpocznij analize” oraz „Anuluj”.

Stan `SUBMITTING` blokuje ponowne wyslanie. Blad sieci/autoryzacji/mapowania
nie zamyka modala i nie traci pytania. Po `202 Accepted` modal pokazuje
„Analiza rozpoczeta”, `jobId`, link „Otworz analize” oraz „Historia analiz”.
Nie wyswietla wyniku AI i nie uruchamia pollingu.

## Kontrakt capture v1

Publiczny kontrakt ma jawny discriminator i wersje:

```json
{
  "captureVersion": "tdw.ui-explorer-browser-capture/v1",
  "question": "Dlaczego ten przycisk jest wyszarzony?",
  "page": {
    "origin": "https://crm.example.com",
    "pathname": "/customers/42/edit",
    "routeHash": "/customers/42/edit",
    "queryParameterNames": ["tab"],
    "title": "Edycja klienta",
    "language": "pl",
    "basePath": "/",
    "declaredBuildRevision": "a1b2c3d4",
    "resourcePathHints": ["main.abc123.js"]
  },
  "selection": {
    "target": {
      "tag": "button",
      "role": "button",
      "accessibleName": "Zapisz",
      "text": "Zapisz",
      "stableAttributes": {
        "data-testid": "customer-save"
      },
      "classes": ["primary-action"],
      "state": {
        "disabled": true,
        "ariaDisabled": true,
        "readOnly": false,
        "required": false,
        "invalid": false,
        "checked": null,
        "expanded": null,
        "hidden": false
      }
    },
    "ancestors": [],
    "traversal": {
      "observedDepth": 11,
      "emittedNodeCount": 8,
      "omittedNodeCount": 3,
      "reachedDocumentRoot": true
    },
    "shadowBoundaries": [],
    "frame": {
      "kind": "TOP_LEVEL"
    },
    "viewportBounds": {
      "x": 1040,
      "y": 712,
      "width": 112,
      "height": 40
    }
  },
  "preferences": {
    "model": "selected-model",
    "reasoningEffort": "high"
  },
  "client": {
    "extensionVersion": "1.0.0",
    "capturedAt": "2026-09-15T10:15:30Z"
  }
}
```

Nazwy sa ilustracyjne i podlegaja review kontraktu przed implementacja.
`routeHash` jest wysylany tylko wtedy, gdy ma forme route hash i przejdzie
redakcje; fragmenty przypominajace OAuth token albo pary klucz-wartosc sa
odrzucane. `resourcePathHints` obejmuja tylko limitowane basename'y zasobow
JavaScript/CSS bez query, fragmentu i obcego originu.

### Znaczenie „stack do roota”

Content script przechodzi przez wszystkie dostepne wezly od targetu do
`Document`, ale nie musi przesylac kazdego anonimowego `div` i `span`.
Kontrakt zachowuje:

- target,
- semantyczne wezly (`form`, `dialog`, `nav`, `main`, `section`, role),
- wezly ze stabilnym identyfikatorem lub custom-element tagiem,
- najblizsze wezly targetu i landmarki przy root,
- kolejnosc, pelna obserwowana glebokosc i liczbe pominietych wezlow,
- granice Shadow DOM i dostepnej ramki.

Domyslny limit to 32 emitowane wezly oraz 64 KB calego capture po serializacji.
Przekroczenie daje deterministyczne, jawne skrocenie, a nie ciche uciecie.
Backend ponownie waliduje wszystkie limity.

### Dane zabronione

Payload nie zawiera:

- cookies, tokenow, naglowkow ani storage przegladarki,
- wartosci `input`, `textarea`, `select` i elementow edytowalnych,
- wartosci query params ani calego URL,
- pelnego DOM, HTML, CSSOM, historii sieciowej i screenshotu,
- repository, GitLab group, branch, commita jako zaufanego scope,
- source path, nazwy komponentu ani sugestii tool calls pochodzacych ze strony,
- zawartosci atrybutow zdarzen `on*`, `style`, `srcdoc`, `value`, `href` z
  query/fragmentem oraz atrybutow przypominajacych sekrety.

Tekst, accessible name, klasy i atrybuty maja oddzielne allowlisty, limity i
redakcje. Dla pol formularza przechowywana jest semantyka i stan, nie wartosc.
Surowy capture moze istniec tylko w pamieci podczas walidacji/rozpoznania.
Historia, prompt, evidence i logi zawieraja wylacznie zredagowana projekcje.

## Mapowanie strony na source

### Feature-owned target registry

Origin nie staje sie nowym polem kanonicznego `system` w Operational Context.
Deployment/runtime URL jest sygnalem uruchomienia, nie osobnym bytem
referencyjnym. UI Explorer otrzymuje feature-owned konfiguracje, np.
`features.ui-explorer.browser.targets`, ktora mapuje:

- dokladny origin,
- dozwolone prefiksy lub wzorce path,
- `systemId` z Operational Context,
- strategie wyboru source ref,
- opcjonalne allowlistowane sygnaly build revision.

Konfiguracja nie duplikuje GitLab group/repository. Po `systemId` backend
korzysta z obecnego Operational Context i resolvera repository. Dla jednego
origin/path musi istniec co najwyzej jeden wynik. Brak albo wiele wynikow
blokuje start zamiast wybierac heurystycznie.

### Route i screen

Backend normalizuje route niezaleznie od query values, dopasowuje ja do
aktualnego screen catalog i zwraca:

- jeden potwierdzony `screenId`,
- uporzadkowanych kandydatow z jawnym ambiguity, albo
- kod blokady, jezeli ekran nie moze byc wyznaczony.

W MVP start jest dozwolony tylko dla jednego deterministycznego ekranu.
Wieloznaczny preflight nie pozwala klientowi samodzielnie podac technicznego
`screenId`; ewentualny wybor operatora jest osobna decyzja produktowa i musi
byc ograniczony do publicznych etykiet wygenerowanych przez backend.

### Rewizja

Ref z konfiguracji jest rozwiazywany do immutable commit przed budowa kontekstu.
Jesli ref nie jest skonfigurowany, resolver uzywa platformowego default branch,
a nie nowego feature-specific defaultu.

`declaredBuildRevision` strony jest tylko niezaufanym hintem. Mozna go uzyc po
walidacji formatu, potwierdzeniu istnienia w rozpoznanym repository i zgodnosci
z jawna strategia targetu. Sam fakt istnienia commita nie dowodzi, ze dana
wersja jest wdrozona. Bez niezaleznego, zaufanego deployment mappingu wynik
publikuje `DEPLOYED_REVISION_UNVERIFIED` i wyjasnia, do ktorej rewizji
przypieto analize.

## REST i autoryzacja rozszerzenia

### Preflight

`POST /api/ui-explorer/browser/preflight` przyjmuje zredagowany locator strony
i minimalne dane selekcji bez pytania. Odpowiedz ma jeden z wynikow:

- `READY` - publiczna etykieta systemu i ekranu, przypieta rewizja oraz
  projekcja wspolnego katalogu modeli i zgodnych `reasoningEffort`,
- `AMBIGUOUS` - bezpieczne publiczne kandydatury i instrukcja dalszego kroku,
- `BLOCKED` - stabilny kod i zrozumialy komunikat.

Preflight nie zwraca GitLab group, repository path, credentiali ani hidden
tool scope. Reuse'uje platformowy katalog modeli; nie tworzy drugiego zrodla
metadanych AI. Jego wynik jest informacyjny; start joba nie ufa preflight
tokenowi ani decyzji klienta i ponownie wykonuje auth, walidacje oraz szybkie
mapowanie targetu.

### Start joba

`POST /api/ui-explorer/browser/jobs` przyjmuje `captureVersion`, `question`,
`page`, `selection`, `preferences` i `client`. Po auth, pelnej walidacji,
ponownym mapowaniu targetu i zapisaniu `QUEUED` - ale przed uruchomieniem pracy
w executorze - zwraca `202 Accepted` z:

```json
{
  "jobId": "...",
  "analysisKind": "ELEMENT_QUESTION",
  "status": "QUEUED",
  "acceptedAt": "...",
  "links": {
    "analysis": "https://tdw.example.com/ui-explorer?localRunId=...",
    "history": "https://tdw.example.com/analysis-history"
  }
}
```

Linki sa generowane przez backend z jego publicznej konfiguracji, nie przez
badana strone ani extension payload. Obecny `POST /api/ui-explorer/jobs` dla
`SCREEN_DOCUMENTATION` pozostaje bez zmian.

Platforma dostaje osobny read endpoint dla nowego joba, np.
`GET /api/ui-explorer/browser/jobs/{jobId}`, albo jeden jawnie wersjonowany
union envelope. Wybor zostaje zamkniety w checkpointcie kontraktu; domyslna
rekomendacja to osobny endpoint, aby nie zmieniac typu obecnego
`UiExplorerJobStateSnapshot` w sposob niekompatybilny.

### Pairing

Rozszerzenie nie moze kopiowac cookie sesji TDW ani przesylac GitHub tokenu.
Rekomendowany MVP:

1. rozszerzenie generuje jednorazowy verifier i otwiera strone pairing w TDW,
2. operator autoryzuje polaczenie w istniejacej sesji TDW,
3. TDW wydaje jednorazowy kod zwiazany z operatorem, extension ID,
   configured TDW origin, verifierem i krotkim TTL,
4. uzytkownik przekazuje kod do extension options/popup,
5. service worker wymienia kod na nieprzezroczysty, odwolalny token sesyjny,
6. backend przechowuje tylko hash tokenu i mapowanie na operator auth context,
7. rozszerzenie trzyma token tylko w `chrome.storage.session`.

Nie uzywamy `externally_connectable`; pierwszy MVP moze wymagac jawnego
copy/paste jednorazowego kodu. Po restarcie Chrome pairing jest ponawiany.
Trwaly refresh token w `storage.local` wymaga osobnej analizy ryzyka.

Token browser ingress jest akceptowany tylko przez endpointy preflight/start,
ma minimalny scope, TTL, revoke i rate limit. Backend mapuje go na
nie-sekretny `AnalysisAiAuthRef`; prawdziwy GitHub/Copilot credential pozostaje
po stronie backendu. W trybie lokalnego tokenu AI nadal wymagane jest
uwierzytelnienie browser ingress, aby dowolna strona nie mogla uruchamiac
kosztownych analiz.

Service worker moze laczyc sie tylko z dokladnym originem TDW zapisanym podczas
pairingu. Nie przyjmuje URL endpointu, redirectu ani hosta z content scriptu.
Origin i path badanej strony wyprowadza z `sender.tab.url` i porownuje z
locatorami capture; nie ufa wartosci URL przeslanej w message body. CORS,
jezeli jest potrzebny, dopuszcza konkretny `chrome-extension://<id>`; wildcard
jest zabroniony. Produkcyjny TDW wymaga HTTPS; wyjatek localhost jest jawny i
tylko developerski.

## Budowa Element Focus Context

Po target/source resolution feature buduje aktualny Screen Reachability dla
przypietej rewizji, a potem rankuje element wobec istniejacego grafu.

Sygnaly rankingowe, od najmocniejszych do pomocniczych:

1. stabilny `data-testid`/allowlistowany identyfikator obecny w template,
2. custom-element tag zgodny z hostem komponentu,
3. role + accessible name + tag w ograniczonym sasiedztwie template,
4. stabilne klasy i nazwy form controls,
5. ograniczony tekst widoczny,
6. route chain, landmarki oraz semantyczni przodkowie.

Wynikiem deterministycznego kroku jest `UiExplorerElementFocusContext`:

- zredagowane podsumowanie obserwowanego elementu i stanu,
- rozpoznany ekran i route chain,
- uporzadkowani kandydaci component/template z powodami i score,
- potwierdzone source references/slice refs,
- informacje o kolizjach i niepokrytych sygnalach,
- status `CONFIRMED`, `INFERRED` albo `UNKNOWN`.

Niski score nie moze zostac przedstawiony jako pewne mapowanie. AI dostaje
wszystkich materialnych kandydatow i jawna niepewnosc. Targeted GitLab tools
sluza do rozstrzygania pozostalych luk w hidden scope.

W MVP nie powstaja:

- globalny indeks selector -> plik,
- zaleznosc od `window.ng`, Angular DevTools albo trybu developerskiego,
- wymog source maps w przegladarce,
- wykonywanie aplikacji przez backend,
- browser capture tool dostepny dla modelu.

## AI, prompt, skill i wynik

### Input runtime

Nowy `UiExplorerElementAnalysisCommand` sklada sie z:

- pytania operatora,
- rozpoznanej tozsamosci strony/ekranu i immutable source revision,
- `UiExplorerElementFocusContext`,
- Screen Reachability ograniczonego logicznie do rozpoznanego ekranu, ale bez
  usuwania materialnych zaleznosci,
- feature-owned promptu, guidance, available tools, hidden context, evidence
  sink i result projector.

Teksty ze strony sa zawsze oznaczone jako **untrusted observed data**. Prompt i
skill zabraniaja wykonywania instrukcji znalezionych w DOM, atrybutach, tytule
lub pytaniu jako zmiany policy/tool scope.

### Skill i tools

Powstaje feature-owned runtime skill, np. `ui-explorer-element-question`, po
polsku. Uczy model:

- zaczynac od bezposredniej odpowiedzi na pytanie,
- oddzielac obserwowany stan DOM od potwierdzonej logiki w kodzie,
- sledzic warunki, zrodla danych, handlery, efekty i warianty tylko w zakresie
  potrzebnym do pytania,
- uzywac przygotowanych slice refs przed generycznym search/read,
- publikowac brakujaca widocznosc zamiast zgadywania.

Allowlista zachowuje obecne waskie frontend slice tools, kontrolowany fallback
GitLab search/read i platformowe report tools. Browser capture nie staje sie
toolem ani hidden mutable session state.

### Report-first contract

Nie wciskamy pytania o element do osmiu sekcji dokumentacji calego ekranu.
Nowy wynik, np. `UiExplorerElementResultResponse`, zawiera:

- `question`,
- publiczna tozsamosc strony i rozpoznanego ekranu,
- zredagowane podsumowanie wyboru,
- immutable `sourceRevision` oraz status zgodnosci z deploymentem,
- `directAnswer`,
- sekcje reportu:
  - `CONDITIONS_AND_BEHAVIOR`,
  - `DATA_AND_EFFECTS`,
  - `EVIDENCE_AND_LIMITS`,
- `overallConfidence`,
- source references, visibility limits i open questions,
- usage.

`DIRECT_ANSWER` jest wymaganym blokiem reportu. Pozostale sekcje sa publikowane,
gdy sa istotne dla pytania. Nazwy techniczne pozostaja evidence, a glowna
tresc odpowiada jezykiem uzytkownika. `AnalysisReport` zapisany przez report
tools jest jedynym zrodlem prawdy; finalny tekst asystenta nie jest parsowany.

## Job, historia, UI i export

### Rodzaj analizy

Wspolny feature key pozostaje `ui-explorer`, ale run ma jawny
`analysisKind`:

- `SCREEN_DOCUMENTATION` - obecny przypadek,
- `ELEMENT_QUESTION` - nowy przypadek z browser ingress.

Nie tworzymy osobnego sibling feature'a ani drugiej pozycji w glownej
nawigacji. UI Explorer wybiera renderer i publiczny kontrakt na podstawie
`analysisKind`.

### Persistence lifecycle

Aby spelnic wymaganie historii, accepted snapshot jest zapisywany przed
oddaniem joba executorowi. Kolejne istotne przejscia statusu aktualizuja ten
sam local run. Rekomendacja obejmuje oba rodzaje runu UI Explorer, aby feature
nie utrzymywal dwoch sprzecznych modeli persistence.

Minimalne punkty zapisu:

- `QUEUED` po synchronicznym auth, walidacji i target mapping, przed
  przekazaniem joba do executora,
- wejscie w discovery/context/AI,
- publikacja prepared prompt i evidence,
- kazdy terminalny snapshot.

Pierwszy deterministyczny krok joba rozwiazuje ref do immutable revision i
potwierdza screen. Brak pinningu konczy run `BLOCKED`; AI nie startuje.

Zapis nie czyni lokalnego workspace trwala kolejka wykonawcza. Po restarcie
procesu niedokonczony run pozostaje widoczny i jest uzgadniany do terminalnego
`FAILED` z kodem `JOB_INTERRUPTED_BY_RESTART`; nie jest automatycznie wznawiany.
TDW moze zaoferowac ponowienie z zapisanej, zredagowanej projekcji, bez
ponownego pobierania niezaufanego raw capture.

### Otwieranie z historii

`Analysis History` kieruje nadal na `/ui-explorer?localRunId=...`. UI Explorer:

1. pobiera local run,
2. rozpoznaje `analysisKind`,
3. dla terminalnego snapshotu pokazuje read-only wynik,
4. dla aktywnego snapshotu przechodzi na live endpoint i wznawia polling,
5. dla stale/przerwanego joba pokazuje stan i ograniczone akcje odzyskania.

Extension link otwiera zwykla strone TDW. Rozszerzenie nie omija shellu,
historii ani autoryzacji platformy.

### Export/import

Istniejacy `tdw.ui-explorer-export` v5 oraz `ui-explorer-result-v5` pozostaja
czytelne bez migracji. Domyslna propozycja to nowy jawny kontrakt, np.
`ui-explorer-element-result-v1`, w tym samym feature i historii. Parser UI
obsluguje oba discriminated envelope'y. Unifikacja do v6 jest dopuszczalna
tylko po osobnej decyzji i macierzy zgodnosci wstecznej.

Export nie zawiera raw capture, pairing tokenu, pelnych URL-i ani ukrytego
repository scope. Import jest read-only, sanitizuje prompt i nie uruchamia AI.

## Bezpieczenstwo i prywatnosc

Model zagrozen obejmuje cztery niezalezne strony:

1. badana strona jest niezaufana i moze probowac wykryc/usunac overlay,
   wstrzyknac prompt albo sprowokowac request,
2. content script ma dostep tylko do DOM przyznanego originu i nie zna sekretow,
3. service worker jest jedynym klientem sieciowym i nie ufa wiadomosciom strony,
4. backend ponownie uwierzytelnia, waliduje, redaguje, mapuje target i scope.

Wymagane zabezpieczenia:

- schema `additionalProperties=false` po obu stronach i limity per pole,
- nonce/correlation dla wiadomosci content script -> service worker,
- service worker przyjmuje tylko znane message types i sender tab/origin,
- allowlista originow i dokladny TDW base URL,
- brak dowolnego proxy/fetch oraz brak podazania za zewnetrznym redirectem,
- rate limit per pairing/operator/origin i idempotency key startu,
- redakcja przed transportem i druga redakcja na backendzie,
- brak raw payloadu w standardowych logach i telemetryce,
- DOM oraz pytanie opakowane jako dane niezaufane w promptcie,
- CSP rozszerzenia bez remote code i `eval`,
- revoke pairingu oraz feature flag browser ingress,
- audyt accepted/rejected bez sekretow i tresci formularzy.

## Conformance delta

| Obszar | Baseline | Delta | Wlasciciel | Zgodnosc |
|---|---|---|---|---|
| Jednostka analizy | ekran/scenariusz/revision | pytanie/element/stan strony/revision | `features.uiexplorer` | nowy `analysisKind`, stary bez zmian |
| Publiczny start | operator podaje system/ref/screen | klient podaje capture i preferencje | browser API feature'a | osobny endpoint |
| Source scope | wybrany w TDW, przypiety backendowo | origin/path mapowany backendowo | target/source resolver | bez client repo/branch |
| Deterministic context | Screen Reachability | ranking selection do obecnego grafu | element context | reuse, bez indeksu globalnego |
| Prompt/skill | dokumentacja 8 sekcji | bezposrednia odpowiedz elementowa | element analysis | osobne assets feature'a |
| Tools | frontend slices + GitLab fallback + report | ten sam model read-only | feature/platforma | bez browser toola |
| Wynik | `UiExplorerResultResponse` | `UiExplorerElementResultResponse` | contract feature'a | discriminated result |
| Job | obecne statusy ekranu | osobny job envelope/kroki elementu | job feature'a | kompatybilne ID/statusy |
| Persistence | terminal only | accepted + progress + terminal | local workspace adapter | migracja wszystkich runow zalecana |
| Historia | read-only snapshot | aktywny run wznawia polling | Angular UI Explorer | route bez zmian |
| Export | v5 screen result | element result v1 | export/import feature'a | v5 pozostaje czytelne |
| AI options | shared API/katalog w TDW | projekcja w browser preflight | UI Explorer -> platformowy katalog | bez duplikacji zrodla |
| Operational Context | `system` i source mapping | lookup po feature-owned target registry | UI Explorer + opctx | bez nowego runtime entity |
| Auth | sesja operatora w TDW | scoped pairing token -> auth ref | browser auth | bez GitHub tokenu w extension |
| Frontend | formularz dokumentacji widoku | renderer element question/history live | Angular UI Explorer | wspolny shell/evidence/usage |
| Zaleznosci | feature -> shared/platform/tools/integrations | bez odwrocenia zaleznosci | package guard | brak sibling imports |

## Konsumenci i migracja

| Kontrakt/zmiana | Konsumenci do aktualizacji | Dowod |
|---|---|---|
| capture v1 | content script, service worker, preflight/start API, sanitizer | contract fixtures i round-trip test |
| target registry | properties binding, preflight, job start, diagnostics | binding + ambiguity tests |
| browser auth | pairing UI/API, service worker, AI auth resolver | expiry/revoke/cross-origin tests |
| `analysisKind` | snapshot, local history, Angular facade/router/renderer | screen + element history tests |
| element result | report projector, REST, Angular renderer, export/import | strict parser + component tests |
| progress persistence | job service, local run store, history API, resume polling | transition/restart tests |
| AI options w modalu | browser preflight client, platformowy options catalog | model-effort compatibility test |

Nie ma migracji danych obecnych runow. Brak `analysisKind` w v5 oznacza
`SCREEN_DOCUMENTATION`. Nowe pola opcjonalne w historii musza miec bezpieczne
defaulty. Nie wolno przepisywac ani kasowac istniejacych exportow.

## Bledy i visibility limits

Stabilne kody powinny rozrozniac co najmniej:

- `BROWSER_ORIGIN_NOT_ALLOWED`,
- `BROWSER_TARGET_NOT_REGISTERED`,
- `BROWSER_TARGET_AMBIGUOUS`,
- `BROWSER_CAPTURE_INVALID_OR_TOO_LARGE`,
- `BROWSER_PAIRING_REQUIRED`, `BROWSER_PAIRING_EXPIRED`,
- `SCREEN_ROUTE_NOT_RESOLVED`,
- `SOURCE_REVISION_NOT_RESOLVED`,
- `DEPLOYED_REVISION_UNVERIFIED`,
- `ELEMENT_SOURCE_NOT_CONFIRMED`,
- `CROSS_ORIGIN_FRAME_NOT_VISIBLE`.

Kody auth/scope nie ujawniaja, czy konkretne prywatne repository istnieje.
Komunikat w modalu jest operator-facing; pelna diagnostyka pozostaje w TDW.

## Rozwazone alternatywy

### Odpowiedz AI bezposrednio w rozszerzeniu

Odrzucone. Duplikuje job UI, evidence, usage, historie i auth oraz zwieksza
ekspozycje sekretow. Rozszerzenie jedynie uruchamia analize.

### Rozszerzenie obecnego requestu o repository, branch i selector

Odrzucone. Klient sterowalby hidden source/tool scope, a unit obecnego requestu
pozostalaby dokumentacja ekranu. Osobny browser ingress mapuje target po stronie
serwera.

### Screenshot jako podstawowy kontekst

Odlozone. Screenshot zwieksza uprawnienia, rozmiar, ryzyko danych wrazliwych i
problem retencji, a pytania dotycza logiki mozliwej do osadzenia przez DOM i
kod. Moze wrocic jako osobny, jawny need po pomiarze jakosci MVP.

### `window.ng`, source maps albo specjalny debug build

Odrzucone dla MVP. Nie dzialaja powszechnie w buildach produkcyjnych i tworza
silne sprzezenie capture z frameworkiem. Moga byc opcjonalnym providerem
sygnalow w przyszlosci, nigdy wymogiem kontraktu.

### Globalny selector index repository

Odlozony. Obecny Screen Reachability juz zawiera route, template i component
graph. Najpierw mierzymy skutecznosc rankingu na tym materiale. Indeks jest
osobna optymalizacja dopiero po danych o nietrafionych dopasowaniach.

### Stale `<all_urls>`

Odrzucone. Generuje szerokie ostrzezenia i daje rozszerzeniu wiekszy zakres niz
potrzebny. Optional host permission jest nadawane per origin.

## Ryzyka i mitygacje

| Ryzyko | Skutek | Mitygacja / bramka |
|---|---|---|
| `AltGr` emituje Ctrl+Alt | przypadkowe wlaczenie | `AltGraph` guard i testy klawiatur regionalnych |
| strona przechwytuje click pierwsza | wykonanie akcji biznesowej | capture-phase listeners, test submit/link/button |
| CSS strony zaslania overlay | zly wybor | Shadow DOM, fixed host, z-index, geometry tests |
| DOM nie wskazuje komponentu | halucynowane mapowanie | ranking + confidence + targeted source research |
| route nie odzwierciedla ekranu | zly source scope | preflight block/ambiguity, bez client override |
| deployment != przypiety commit | bledna odpowiedz | jawny revision status i visibility limit |
| DOM zawiera sekrety/PII | wyciek do AI/historii | denylist, allowlist, limity, podwojna redakcja |
| prompt injection w stronie | sterowanie agentem | untrusted artifact boundary i policy tests |
| token extension zostaje wykradziony | koszt/nieautoryzowany start | session storage, TTL, scope, revoke, rate limit |
| arbitrary fetch przez worker | confused deputy/SSRF | stale endpointy i dokladny paired TDW origin |
| cross-origin iframe | brak targetu | jawny non-goal/status, bez pozorowanej analizy |
| zapis tylko terminalny | brak runu w historii | accepted/progress persistence przed execution |
| restart backendu | wiszacy active run | `FAILED/JOB_INTERRUPTED_BY_RESTART`, bez obietnicy durable queue |
| szeroki zakres L3 | trudny rollback | feature flags i inkrementy z osobnymi gate'ami |

## Kryteria akceptacji

1. Po nadaniu permission dla zwyklej strony HTTP(S) przytrzymanie `Ctrl` +
   `Alt` wlacza highlight elementu pod kursorem; `AltGr` go nie wlacza.
2. Klik w stanie aktywnym nie uruchamia linku, submitu ani handlera badanego
   elementu i otwiera dostepny modal.
3. Highlight uzywa palety TDW, nie zmienia layoutu, reaguje na scroll/resize i
   respektuje reduced motion.
4. Modal pobiera rzeczywisty katalog modeli/effortow, waliduje pytanie i nie
   pozwala na podwojny submit.
5. Capture dochodzi logicznie do document root, raportuje skrocenie i nie
   zawiera zabronionych danych ani wartosci formularza.
6. Nieznany lub wieloznaczny origin/path nie uruchamia joba.
7. Klient nie moze wybrac repository, branch, commit, pliku, `screenId` ani
   hidden tool scope.
8. Start ponownie rozstrzyga target, a pierwszy krok joba przypina immutable
   revision; zadna z tych decyzji nie ufa wynikowi preflightu z klienta.
9. `202 Accepted` powoduje natychmiastowe pojawienie sie `QUEUED` runu w
   `Analysis History` i zwraca dzialajacy link do UI Explorera.
10. Rozszerzenie po accepted nie odpytuje wyniku i nie prezentuje odpowiedzi
    AI.
11. TDW pokazuje przebieg, deterministic/tool evidence, usage, element-specific
    report, confidence i visibility limits.
12. Odpowiedz wskazuje kod tylko przy potwierdzonym evidence i jawnie oddziela
    obserwacje DOM od wnioskow ze zrodla.
13. Obecny screen-centered `POST /api/ui-explorer/jobs`, wynik i export v5
    dzialaja bez regresji.
14. Extension nie posiada GitHub/Copilot tokenu, szerokiego `<all_urls>` ani
    uprawnien `cookies`, `debugger`, `webRequest` lub `tabCapture`.
15. Wszystkie fixtures i przyklady sa w pelni fikcyjne i w domenie CRM.

## Macierz weryfikacji

### Rozszerzenie

- unit/Vitest: state machine, `AltGraph`, sanitizer, ancestry compaction,
  sensitive attributes, URL normalization, message schema, retry/idempotency,
- DOM tests: input/textarea/contenteditable, Shadow DOM, detached target,
  scroll/resize, modal focus/Escape/reduced motion,
- Playwright z unpacked extension na kontrolowanej stronie CRM: permission,
  Ctrl+Alt hover, przejecie click, modal, 202/error/pairing flow,
- manifest audit: brak niedozwolonych permissions i remote code.

### Backend

- `MockMvc`: preflight/start, unknown fields, size limits, auth, 202 i stable
  errors,
- target resolver: exact origin/path, ambiguity, traversal/path normalization,
  default ref i revision hint,
- capture sanitizer: redakcja, traversal limits i prompt-injection fixtures,
- Element Focus Context: selector/template match, kolizja, niski confidence,
  Shadow/frame limitation,
- GitLab integration: immutable pinning, Screen Reachability i obecne slice
  tools w hidden scope,
- report projection: wymagany direct answer, evidence validation, partial/fail,
- pairing: expiry, revoke, replay, wrong extension/origin/operator i rate limit,
- persistence: accepted przed executor, progress overwrite, terminal,
  `JOB_INTERRUPTED_BY_RESTART` i kompatybilnosc v5,
- `PackageDependencyGuardTest` dla nowych pakietow.

### Frontend TDW

- historia rozroznia oba `analysisKind`,
- otwarcie aktywnego element runu wznawia polling,
- terminal/history/import sa read-only i uzywaja wlasciwego renderera,
- element report reuse'uje wspolne progress/evidence/usage komponenty,
- v5 screen exports oraz element v1 przechodza strict parsing,
- loading/empty/error/accessibility i routing `localRunId`.

### Komendy koncowe po implementacji calego pionu

Dokladne skrypty extension zostana zatwierdzone przy scaffoldzie. Docelowa
sekwencja dla zmiany wspolnej:

```text
npm --prefix browser-extension test
npm --prefix browser-extension run build
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
mvn -q -Pbackend-dev clean package
```

Jesli scaffold dodaje lub zmienia lockfile/lifecycle pakowania extension,
dodatkowo wykonujemy czysty, reprodukowalny build zgodnie z root `AGENTS.md`.
Kazdy checkpoint uruchamia najpierw testy celowane swojego zakresu.

## Rollout i rollback

Rollout zaczyna sie od srodowiska developerskiego i jednego jawnie
zarejestrowanego frontendu CRM. Feature flags rozdzielaja co najmniej:

- browser ingress/pairing,
- start `ELEMENT_QUESTION`,
- prezentacje nowego rodzaju runu w UI.

Rollback:

1. wylaczyc browser ingress i nowe pairingi,
2. odwolac aktywne tokeny extension,
3. wycofac dystrybucje/disable rozszerzenia,
4. pozostawic istniejace runy w historii jako read-only,
5. nie zmieniac screen-centered UI Explorera ani exportu v5.

Brak rozszerzenia albo wylaczony flag nie moze blokowac standardowego UI
Explorera.

## Plan inkrementalny

### 0. Bramka L3 i baseline

- [ ] Zatwierdzic Source need, klasyfikacje L3, zakres MVP i non-goals.
- [ ] Zapisac baseline publicznego API, result/report, prompt/skilli/tools,
  historii/exportu, package graph i testow obecnego UI Explorera.
- [ ] Zatwierdzic threat model: niezaufana strona, content script, service
  worker, browser ingress, auth context i source scope.
- [ ] Zatwierdzic nazwe artefaktu, sposob dystrybucji i wspierane wersje Chrome.
- [ ] Udokumentowac decyzje: pairing, target registry, result discriminator,
  revision strategy i retention zredagowanego capture.

Dowod: zaakceptowany checkpoint w planie; bez kodu produkcyjnego.

### 1. Uniwersalny starter rozszerzenia i UX spike bez AI

Zakres zatwierdzony 2026-09-15: dystrybucja jako unpacked package dla Chrome
Developer Mode; bez publikacji w Chrome Web Store i bez pakowania do JAR-a.

- [x] Dodac niezalezny `browser-extension/` z Manifest V3, TypeScript,
  deterministycznym buildem do `dist/`, testami i instrukcja `Load unpacked`.
- [x] Dodac neutralny platform runtime: feature catalog, content registry,
  background message router, ustawienia i per-origin permission lifecycle.
- [x] Zapewnic extension points dla przyszlych globalnych features i
  site-specific integrations bez zaleznosci platformy od UI Explorera.
- [x] Dodac popup do wlaczania/wylaczania feature'a na biezacym originie oraz
  options page z konfiguracja i lista originow.
- [x] Dodac UI Explorer spike: Ctrl+Alt z `AltGraph` guard, tracking, wow
  highlight, click shield i dostepny modal w izolowanym Shadow DOM.
- [x] Modal ma pokazac realny, zredagowany preview capture oraz dummy
  question/model/reasoning flow; start przechodzi przez message bus i zwraca
  lokalny fake `202/jobId` bez requestu sieciowego.
- [x] Dodac kontrolowana strone demo CRM i testy modifiera, sanitizacji,
  registry, settings/message contracts oraz build/manifest smoke test.
- [x] Nie dodawac jeszcze REST, pairingu, source mapping, AI ani historii TDW.
- [ ] Zaladowac `dist/` przez `Load unpacked` w zwyklym oknie Chrome i wykonac
  manualny scenariusz permission -> Ctrl+Alt -> highlight -> click -> modal ->
  dummy accepted. Ta akcja wymaga osobnej zgody na instalacje rozszerzenia.

Dowod: `npm test`, `npm run typecheck`, `npm run build`; katalog `dist/` daje
sie zaladowac przez Chrome `Load unpacked`, a manualny scenariusz demo realizuje
permission -> Ctrl+Alt -> highlight -> click -> modal -> dummy accepted.

Checkpoint 2026-09-15: utworzono uniwersalny runtime, UI Explorer demo, popup,
options, per-origin dynamic content scripts, fixture CRM oraz unpacked build.
`npm run typecheck` przeszedl, 20 testow Vitest przeszlo, `npm run build`
utworzyl kompletne `dist/`, a demo odpowiedzialo HTTP 200. Uzyty smoke test
Chrome headless nie udostepnil targetu TDW extension, dlatego nie uznajemy tej
proby za manualny dowod i ostatni checkbox pozostaje otwarty.

### 2. Browser ingress, mapping i pairing

- [ ] Dodac feature-owned properties i diagnostics target registry.
- [ ] Dodac pairing issue/exchange/revoke z hashami, TTL, scope i rate limit.
- [ ] Dodac backendowy capture validator/sanitizer niezalezny od klienta.
- [ ] Dodac preflight z exact origin/path mapping, screen resolution i pinning.
- [ ] Dodac start joba, ktory powtarza target mapping, zapisuje `QUEUED` przed
  executorem i zwraca 202/linki; pinning pozostaje pierwszym krokiem joba.
- [ ] Przekazac do AI runtime tylko `AnalysisAiAuthRef`; nie browser token.
- [ ] Pokryc MockMvc, security i package dependency tests.

Dowod: API dziala na fixtures bez uruchamiania AI, nie ujawnia source scope i
odrzuca forged/expired/replayed input.

### 3. Produkcyjny transport rozszerzenia

- [ ] Rozszerzyc istniejace action/options o pairing i status polaczenia z TDW.
- [ ] Zastapic dummy handler typed klientem preflight/model options i start z
  timeoutem/idempotency oraz stalymi endpointami TDW.
- [ ] Powiazac message contract z zatwierdzonym browser ingress schema.
- [ ] Zweryfikowac manifest permissions i brak sekretow w bundle/source maps.

Dowod: rozszerzenie laczy sie z testowym TDW, a dummy transport jest usuniety z
produkcyjnego buildu.

### 4. Utwardzenie selekcji, modala i capture

- [ ] Zastapic kontrakt preview zatwierdzonym capture v1 i backendowym
  round-tripem schema fixtures.
- [ ] Utwardzic maszyne stanow, overlay, click shield i modal na rozszerzonej
  macierzy stron, Shadow DOM, dynamic layout i accessibility.
- [ ] Zatwierdzic ancestry traversal, kompakcje, redakcje i limity capture v1.
- [ ] Dodac Playwright z unpacked extension na kontrolowanych stronach CRM.

Dowod: end-to-end od permission do mockowego 202; brak wartosci formularzy w
payloadzie i brak wykonania native click.

### 5. Element Focus Context

- [ ] Reuse'owac obecny screen catalog i Screen Reachability po przypieciu
  immutable revision.
- [ ] Dodac deterministyczny ranking sygnalow capture do template/component.
- [ ] Publikowac kandydatow, reasons, confidence, collisions i gaps.
- [ ] Dodac zredagowana `AnalysisEvidenceSection` wyboru i mapowania.
- [ ] Zablokowac AI przy braku source/screen; niski element match przekazac jako
  jawna niepewnosc, jezeli ekran jest pewny.
- [ ] Zmierzyc trafnosc na fixture'ach button/field/data source i zapisac
  przypadki, ktorych obecny graf nie pokrywa.

Dowod: deterministyczny context wskazuje poprawne source refs bez `window.ng`,
source maps i globalnego selector index.

### 6. AI i report-first wynik

- [ ] Dodac element-specific prompt, polski runtime skill i artifacts.
- [ ] Zachowac obecne hidden GitLab scope, frontend slice tools, fallback i
  report tools; dodac feature-owned guidance/policy tylko tam, gdzie konieczne.
- [ ] Dodac `UiExplorerElementResultResponse` i strict report projection.
- [ ] Walidowac source references wobec prepared/captured evidence.
- [ ] Pokryc prompt injection, brak direct answer, partial report, low
  confidence, usage i brak tools poza allowlista.

Dowod: trzy scenariusze z potrzeby otrzymuja bezposrednia, ugruntowana odpowiedz
albo jawny limit widocznosci.

### 7. Lifecycle, historia, UI i export

- [ ] Dodac `analysisKind` z backward defaultem dla v5.
- [ ] Zapisywac accepted/progress/terminal snapshots w sposob idempotentny.
- [ ] Oznaczac przerwane aktywne runy jako
  `FAILED/JOB_INTERRUPTED_BY_RESTART` bez udawania durable queue.
- [ ] Dodac Angularowy renderer element result, reuse progress/evidence/usage.
- [ ] Wznowic live polling po otwarciu aktywnego local runu z historii.
- [ ] Dodac element export/import v1 bez zmiany czytelnosci screen v5.
- [ ] Dodac linki accepted do UI Explorer i Analysis History.

Dowod: `QUEUED` jest widoczny w historii przed wykonaniem AI, link z extension
otwiera ten sam run, a v5 regression suite przechodzi.

### 8. Hardening, dokumentacja i rollout

- [ ] Wykonac security, accessibility i browser compatibility review.
- [ ] Zweryfikowac CSP, redirecty, CORS, token revoke, rate limits i log
  sanitization.
- [ ] Dodac instrukcje instalacji/pairingu/permission oraz uczciwa liste
  niewspieranych stron i ramek.
- [ ] Zaktualizowac architecture/current-state dopiero po dostarczeniu
  zaakceptowanego runtime, nie na etapie draftu.
- [ ] Wykonac macierz testow i koncowe buildy adekwatne do wspolnej zmiany.
- [ ] Uruchomic ograniczony rollout na fikcyjnym/kontrolowanym CRM i zebrac
  metryki resolution, capture size, blocked reason oraz czasu do accepted.

Dowod: podpisany checklist, artefakt extension, build TDW, rollback drill i brak
danych organizacyjnych w testach/dokumentacji.

## Bramka decyzji przed pierwsza implementacja

Do zatwierdzenia przez uzytkownika pozostaja:

1. czy MVP akceptuje per-session pairing i jawne copy/paste kodu,
2. czy niejednoznaczny screen ma blokowac start (rekomendacja: tak),
3. czy nowy job ma osobny read endpoint (rekomendacja: tak),
4. czy element export ma osobny kontrakt v1 (rekomendacja: tak),
5. jak wyglada feature-owned target registry i ktore srodowisko jest pilotem,
6. jaka jest polityka retencji zredagowanego selection summary,
7. jakie wersje Chrome i sposob dystrybucji rozszerzenia wspieramy.

## Oficjalne ograniczenia Chrome wykorzystane w planie

- [Manifest V3](https://developer.chrome.com/docs/extensions/reference/manifest)
- [Declare permissions](https://developer.chrome.com/docs/extensions/develop/concepts/declare-permissions)
- [`activeTab`](https://developer.chrome.com/docs/extensions/develop/concepts/activeTab)
- [Commands API i ograniczenia skrotow](https://developer.chrome.com/docs/extensions/reference/api/commands)
- [Cross-origin network requests](https://developer.chrome.com/docs/extensions/develop/concepts/network-requests)
- [Extension service workers](https://developer.chrome.com/docs/extensions/develop/concepts/service-workers)
