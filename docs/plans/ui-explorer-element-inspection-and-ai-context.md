# UI Explorer - Inspector Lite bez rozszerzenia przegladarki

Status: in-progress

Source need: [UI Explorer - pytania o element uruchomionej strony](../needs/ui-explorer-browser-element-analysis.md)

Klasyfikacja: **L3**. Zmiana dotyka sposobu dostarczenia kodu do niezaufanej
strony, granicy zaufania browser-TDW, przyszlego publicznego ingressu i nowego
rodzaju analizy. Sam pierwszy inkrement jest frontendowym UX spike'iem bez
zmiany backendowego API, AI, historii ani kontraktu obecnego UI Explorera.

Zakres replacementu pierwszego inkrementu zostal jawnie zatwierdzony przez
uzytkownika 2026-09-15: usunac poprzedni artefakt Manifest V3 i dostarczyc
dzialajaca alternatywe niewymagajaca instalacji rozszerzenia.

## Decyzja kierunkowa

Powstaje **TDW Inspector Lite**: zestaw statycznych, wersjonowanych zasobow
pakowanych razem z frontendem i samouruchamialnym JAR-em.

- zaufana strona instalacyjna TDW generuje maly bookmarklet ladujacy runtime z
  przypietego originu oraz samowystarczalny DevTools Snippet,
- launcher uruchamia efemeryczny runtime bezposrednio na badanej stronie,
- runtime zaznacza jeden element i buduje zredagowany capture,
- osobne okno capture nalezace do TDW przyjmuje dane przez scisle walidowany
  `postMessage`, pokazuje preview i formularz,
- przyszly REST startuje tylko ze strony TDW i korzysta ze zwyklej sesji
  operatora,
- badana strona nigdy nie otrzymuje tokenu, cookie TDW, klienta REST, model
  catalog ani repository scope.

Pierwszy inkrement zachowuje dummy `accepted`, aby osobno zweryfikowac sposob
uruchomienia, selekcje, transport i UX. Nie udaje produkcyjnego joba ani wpisu
w historii.

```text
TDW /tdw-inspector/install.html
  -> bookmarklet -> GET statycznego loader/protocol/runtime z originu TDW
     albo pelny DevTools Snippet z osadzonym runtime
  -> dowolna wspierana strona HTTP(S)
  -> hover + wow highlight + przejecie click
  -> zredagowany capture v1
  -> window.open(TDW /tdw-inspector/capture.html#nonce+sourceOrigin)
  -> postMessage: exact origin + source + nonce + schema + size
  -> preview + pytanie + model + reasoningEffort w zaufanym TDW
  -> teraz: lokalny dummy accepted
  -> pozniej: preflight + POST job + Analysis History
```

## Baseline i conformance delta

### Baseline przed replacementem

- Obecny UI Explorer analizuje wybrany ekran/scenariusz w przypietej rewizji
  przez `POST /api/ui-explorer/jobs`.
- Screen Reachability, GitLab slice tools, AI runtime, wynik v5, report,
  import/export i historia sa juz feature-owned przez `features.uiexplorer`.
- Pierwszy UX spike selektora istnial jako top-level `browser-extension/`:
  Manifest V3, stale content scripts, popup/options, permission lifecycle i
  dummy modal na badanej stronie.
- Rozszerzenie zostalo zapisane w commicie `45428236`, wiec usuniecie katalogu
  z biezacej galezi pozostaje odwracalne.

### Conformance delta

| Obszar | Rozszerzenie MV3 | Inspector Lite | Konsekwencja |
|---|---|---|---|
| Dystrybucja | osobna paczka i Developer Mode | zasoby w JAR + zakladka/snippet | brak instalacji rozszerzenia |
| Aktywacja | stale listenery + `Ctrl`/`Alt` | jawne uruchomienie launchera | brak host permissions i konfliktu `AltGr` |
| Izolacja | content-script isolated world | main world badanej strony | runtime i capture sa niezaufane |
| Formularz | modal na obcej stronie | osobne okno na originie TDW | modele, sesja i REST nie trafiaja na obca strone |
| Auth | pairing i osobny credential | zwykla sesja operatora TDW | brak tokenu browser companion |
| Transport | service worker | exact-origin `postMessage`; copy/paste fallback | brak CORS i background worker |
| Bootstrap | kod w paczce extension | maly remote bookmarklet + pelny snippet | brak limitu duzego bookmark URL; CSP/LNA dotyczy tylko remote path |
| Rozszerzalnosc | extension feature registry | wersjonowany launcher z `featureId` | kolejne browser tools moga reuse'owac bootstrap |

### Konsumenci i niezmienniki

- `frontend/public/tdw-inspector/**` jest zrodlem statycznych zasobow i jest
  kopiowane przez Angular build do `src/main/resources/static`.
- UI Explorer dodaje tylko link do launchera; istniejacy formularz, route,
  job, report, historia oraz export v5 nie zmieniaja kontraktu.
- `FrontendRouteController` nie przejmuje sciezek z rozszerzeniem pliku;
  Spring serwuje zasoby statyczne bez nowego controllera.
- Nie dodajemy klas do zamknietego `analysis.*`, nowych dependencies ani
  browser-specific kodu do `aiplatform`, `agenttools` lub `integrations`.
- Przyszly ingress i mapping pozostana w `features.uiexplorer`, a wspolny
  katalog modeli pozostanie pod `api.aioptions`.

## Architektura pierwszego inkrementu

### Zasoby

```text
frontend/public/tdw-inspector/
  install.html       zaufany ekran instalacji i instrukcje
  install.css
  install.js         buduje maly bookmarklet i pelny snippet
  loader.js          remote bootstrap z originu TDW
  capture.html       zaufany ekran odbioru i formularza
  capture.css
  capture.js         walidacja bridge, preview i dummy accepted
  runtime.js         selektor uruchamiany na obcej stronie
  demo.html/css      kontrolowany, fikcyjny scenariusz CRM
  README.md          obsluga, granice i troubleshooting

frontend/tests/tdw-inspector/
  inspector-lite.test.mjs
```

Runtime nie wykonuje `fetch`, nie uzywa `eval` i nie pobiera kodu. Strona
instalacyjna tworzy dwa funkcjonalnie rownowazne launchery:

- bookmarklet ponizej 2 KiB dodaje klasyczny `script` z
  `/tdw-inspector/loader.js`, przypiety do originu strony instalacyjnej i z
  `referrerPolicy=no-referrer`; loader wylicza `tdwOrigin` z wlasnego `src`,
  laduje kolejno `protocol.js` i `runtime.js`, po czym usuwa tymczasowe tagi,
- DevTools Snippet osadza publiczna konfiguracje, protokol i runtime inline;
  jest fallbackiem bez pobierania skryptu z TDW.

Obie sciezki nie zawieraja sekretow. Remote bootstrap wykonuje tylko odczyt
statycznych plikow z dokladnego originu TDW, ale moze zostac zatrzymany przez
CSP strony albo Chrome Local Network Access. Pelny snippet nie ma tej
zaleznosci sieciowej.

### Uniwersalny bootstrap

Konfiguracja launchera ma stabilny minimalny ksztalt:

```json
{
  "schema": "tdw.browser-tool-launcher",
  "version": 1,
  "featureId": "ui-explorer-inspector",
  "tdwOrigin": "https://tdw.example.com"
}
```

`runtime.js` posiada registry feature factories. Pierwsza fabryka to
`ui-explorer-inspector`. Ponowne uruchomienie tej samej wersji najpierw usuwa
poprzednia instancje. Pozwala to pozniej dodac osobne launchery, np. menu dla
Confluence albo pomocnicza manipulacje widoku GitLaba, bez mieszania ich logiki
z capture UI Explorera. Kazdy nowy feature wymaga osobnego needu, threat modelu
i zatwierdzonego planu.

### State machine selektora

```text
BOOTSTRAP -> SELECTING -> TRANSFERRING -> DISPOSED
                  |             |
                Escape       bridge error
                  |             |
                  +--------> FALLBACK -> DISPOSED
```

- launcher aktywuje `SELECTING` natychmiast,
- przezroczysty shield przejmuje pointer events i zapobiega native click,
- `elementsFromPoint()` wybiera pierwszy element spoza hosta Inspectora,
- geometria jest aktualizowana przez `requestAnimationFrame`, scroll, resize i
  `ResizeObserver`,
- highlight dziala w zamknietym Shadow DOM, ma etykiete semantyczna i
  respektuje `prefers-reduced-motion`,
- `Escape` lub ponowne uruchomienie usuwa wszystkie listenery i host,
- po kliknieciu tworzony jest jeden immutable capture.

### Capture v1

Kontrakt jest framework-neutralny i zawiera:

- `captureVersion`, `captureId`, `capturedAt`,
- `page`: origin, znormalizowany pathname, bezpieczny route hash, tytul,
  jezyk i maksymalnie 16 nazw query parameters,
- `selection.target`: tag, role, accessible name, ograniczony tekst, dozwolone
  stabilne atrybuty, klasy i obserwowalny stan,
- maksymalnie 24 ancestors: wezly blisko celu, semantyczne granice oraz root,
- informacje o observed/emitted/omitted depth, Shadow DOM, frame i viewport,
- `client`: nazwa, wersja i feature id.

Nie sa zbierane wartosci `input`, `textarea`, `select`, contenteditable,
cookies, storage, URL query values, requesty ani screenshot. Tekst i
identyfikatory przechodza limity, redakcje e-mail/UUID/dlugich numerow i filtr
slow wrazliwych. Backend w przyszlym inkremencie powtarza walidacje i nie ufa
deklaracji klienta.

### Bridge i granica zaufania

1. Runtime generuje kryptograficzny nonce i otwiera dokladny, skonfigurowany
   origin TDW.
2. URL fragment zawiera nonce i source origin; fragment nie trafia do serwera.
3. Capture page wysyla `TDW_INSPECTOR_READY` tylko do source origin.
4. Runtime akceptuje ready tylko, gdy `event.origin === tdwOrigin`,
   `event.source === openedWindow` i nonce jest zgodny.
5. Capture page przyjmuje `TDW_INSPECTOR_CAPTURE` tylko z `window.opener`,
   exact source origin, zgodnym nonce, schema/version i limitem 64 KiB.
6. Dane sa ponownie normalizowane przed wyswietleniem. UI uzywa `textContent`,
   nie interpoluje capture przez `innerHTML`.
7. Brak handshake pokazuje panel fallback z copy-to-clipboard oraz linkiem do
   ekranu TDW. Capture page ma kontrolowane pole do recznego wklejenia JSON.

Nonce nie jest credentialem. Chroni korelacje okien i przypadkowe wiadomosci,
ale capture nadal jest niezaufanym inputem.

### Formularz i dummy accepted

Zaufany ekran capture zawiera:

- podsumowanie wybranego elementu i originu,
- rozwijalny JSON preview,
- pytanie maksymalnie 4000 znakow,
- demonstracyjny model i `reasoningEffort`, jawnie oznaczone jako placeholder,
- lokalny wynik `DUMMY/QUEUED` z identyfikatorem generowanym w przegladarce.

Nie wywoluje obecnego `POST /api/ui-explorer/jobs`, poniewaz jego jednostka
analizy i request v5 dotycza calego ekranu, a nie pytania o element.

## Produkcyjny pion po zaakceptowaniu spike'a

### Backend ingress i source resolution

- osobny feature-owned preflight przyjmuje capture v1 bez repository/ref,
- registry mapuje exact origin + znormalizowany path do jednego `systemId`,
- backend pinning wyprowadza immutable revision z zaufanej konfiguracji lub
  wiarygodnego deployment metadata,
- brak, konflikt albo niezweryfikowany target blokuje start z jawnym powodem,
- start tworzy `ELEMENT_QUESTION`, zapisuje `QUEUED` przed executorem i zwraca
  `202` z linkiem do tego samego runu w UI Explorer/Analysis History.

REST jest same-origin i wywolywany z capture page TDW. Nie potrzebuje CORS,
pairingu extension ani browser tokenu. Standardowe zabezpieczenia sesji i
CSRF pozostaja wymagane zgodnie z finalnym mechanizmem auth platformy.

### Element Focus Context i AI

- reuse obecnego screen catalog oraz Screen Reachability,
- deterministyczny ranking sygnalow DOM do route/template/component,
- jawni kandydaci, powody, confidence, collisions i gaps,
- element-specific prompt, polski runtime skill, result contract i report,
- obecne hidden GitLab scope i frontend slice tools; generyczne search/read
  tylko jako fallback,
- tekst strony zawsze jako data/evidence, nigdy instrukcja.

### Historia i wynik

- `analysisKind` rozroznia screen documentation i element question,
- accepted/progress/terminal snapshot jest zapisywany idempotentnie,
- aktywny run otwarty z historii wznawia polling,
- element result reuse'uje progress, AI workflow, evidence, usage i report UI,
- export elementowy ma osobny wersjonowany kontrakt; v5 screen export pozostaje
  kompatybilny.

## Alternatywy

### Zarzadzane rozszerzenie Chrome

Odrzucone jako jedyna sciezka, poniewaz czesc uzytkownikow ma zablokowana
instalacje rozszerzen. Commit `45428236` zachowuje spike do ewentualnego
powrotu jako opcjonalny kanal dla organizacji z centralnym deploymentem.

### Userscript manager

Nie jest rozwiazaniem problemu, bo sam wymaga rozszerzenia. Natywnie
zarzadzany userscript moze byc osobnym kanalem enterprise, ale nie MVP.

### Wklejanie kodu do konsoli

Dziala technicznie, ale jest podatne na ostrzezenia self-XSS i bledy kopiowania.
DevTools Snippet jest powtarzalniejszym fallbackiem dla developerow.

### Pelny runtime osadzony w bookmarklecie

Odrzucony po pierwszym tescie. Zakodowany URL mial ok. 64 KiB, byl trudny do
zweryfikowania po zapisaniu i ujawnil defekt separatora `\\n`. Pelny kod
pozostaje w Snippecie, gdzie nie podlega limitowi adresu zakladki.

### Proxy/reverse proxy modyfikujacy kazda aplikacje

Daje silniejsza kontrole, ale wymaga ingerencji w infrastrukture, CSP i ruch
kazdego systemu. Nie jest proporcjonalne do pierwszego eksperymentu.

### Sam zewnetrzny ekran z recznym selektorem CSS

Nie potrzebuje kodu na stronie, ale przerzuca identyfikacje DOM na uzytkownika
i nie realizuje glownej wartosci wyboru wizualnego.

## Ryzyka i mitigacje

| Ryzyko | Skutek | Mitigacja |
|---|---|---|
| strona modyfikuje runtime/capture | falszywy kontekst | brak sekretow, preview operatora, backend validation, evidence traktowane jako untrusted |
| CSP, Local Network Access lub enterprise blokuje remote loader | brak startu | samowystarczalny DevTools Snippet i uczciwy komunikat; pozniej opcjonalny managed channel |
| popup/COOP odcina opener | brak automatycznego bridge | timeout, copy JSON, reczny import na capture page |
| klik dociera do aplikacji | zmiana biznesowa | pelny shield i przejecie pointerdown/mousedown/up/click/contextmenu w capture phase |
| pole formularza ujawnia wartosc | wyciek danych | brak `.value`, brak tekstu controls/contenteditable, allowlista atrybutow, testy |
| tekst/route zawiera PII lub secret | wyciek danych | redakcja, limity, query names only, preview i ponowna walidacja backendu |
| ancestry jest mylone z component stack | bledne wnioski | osobne nazwy DOM ancestry i deterministic source matching z confidence |
| bookmarklet lub snippet ma niepoprawna skladnie | launcher nie startuje | test parsera obu wygenerowanych wariantow |
| loader jest uruchamiany wielokrotnie albo zawiesza sie | race i pozostawione tagi | blokada in-flight, timeout oraz cleanup tagow i globalnego stanu |
| wiele uruchomien zostawia listenery | degradacja strony | singleton + idempotent `dispose` i test |

## Macierz weryfikacji

### Runtime browser tool

- sanitizer: e-mail, UUID, dlugie numery, secret-like text i dynamic ids,
- form controls: brak values i text content,
- pathname/hash/query-name normalization,
- ancestry compaction, semantic nodes i root,
- observable element state,
- exact-origin/source/nonce handshake,
- invalid schema oraz payload >64 KiB,
- singleton lifecycle, Escape i native click suppression,
- bookmarklet ponizej 2 KiB, poprawna skladnia obu launcherow,
- remote loader: origin wyprowadzony z `src`, kolejnosc protocol -> runtime i
  cleanup,
- reduced motion i dostepny focus na capture page.

### Frontend/JAR

- Angular unit: widoczny link launchera tylko w edytowalnym workspace,
- statyczny smoke: installer, loader, capture i runtime sa serwowane z JAR
  resources,
- brak zmian regresyjnych obecnego UI Explorer v5,
- brak nowych dependencies i kodu pobieranego z originow innych niz TDW.

Komendy dla replacementu pierwszego inkrementu:

```text
node --test frontend/tests/tdw-inspector/*.test.mjs
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
mvn -q -Dtest=FrontendPageTest test
```

Testy runtime uzywaja wbudowanego `node:test`, obecnego `jsdom` i wspolnego
protokolu uruchamianego w izolowanym harnessie; nie wymagaja zmiany
`package.json` ani lockfile.

## Rollout i rollback

Rollout zaczyna sie od lokalnego TDW i kontrolowanej, fikcyjnej strony CRM.
Installer pokazuje ograniczenia zanim uzytkownik przeciaga zakladke. Brak
Inspectora nie blokuje standardowego UI Explorera.

Rollback pierwszego inkrementu:

1. usunac link z UI Explorera i statyczny katalog `tdw-inspector`,
2. uzytkownik usuwa istniejacy bookmarklet; nie posiada on credentiali ani
   trwalego storage,
3. screen-centered UI Explorer i kontrakt v5 pozostaja bez zmian,
4. poprzedni extension spike mozna odtworzyc z commita `45428236`, jezeli
   organizacja wybierze managed extension jako dodatkowy kanal.

## Plan inkrementalny

### 0. Replacement delivery mode i threat model

- [x] Potwierdzic blokade rozszerzen jako problem dystrybucyjny.
- [x] Wybrac bookmarklet + DevTools Snippet + trusted TDW capture page.
- [x] Zapisac baseline, conformance delta, konsumentow i granice zaufania.
- [x] Zatwierdzic replacement pierwszego inkrementu poleceniem uzytkownika z
  2026-09-15.

### 1. Inspector Lite UX spike bez AI

- [x] Usunac `browser-extension/` z biezacej galezi.
- [x] Dodac pakowane w JAR zasoby installer/capture/runtime.
- [x] Dodac uniwersalny launcher contract i registry z pierwszym feature id.
- [x] Przeniesc wow highlight, shield, Escape i bezpieczny capture v1.
- [x] Dodac exact-origin/source/nonce bridge oraz manual copy/paste fallback.
- [x] Dodac zaufany formularz question/model/reasoning z dummy accepted.
- [x] Dodac wejscie „Inspect running page” w UI Explorerze.
- [x] Dodac testy runtime, Angular i statycznego routingu.
- [x] Zastapic duzy bookmarklet malym loaderem statycznych zasobow TDW i
  zachowac pelny snippet jako fallback.
- [x] Dodac test skladni obu launcherow oraz kolejnosci i cleanup remote
  loadera.
- [x] Wykonac celowane testy i build frontendu.
- [ ] Wykonac recznie pelny scenariusz bookmarklet oraz DevTools Snippet w
  zwyklym Chrome na stronie demo.

Dowod: bez instalacji rozszerzenia uzytkownik otwiera installer TDW, zapisuje
launcher/snippet, zaznacza element na fixture CRM, widzi zredagowany payload na
originie TDW i otrzymuje jawny dummy `QUEUED`.

Checkpoint 2026-09-15: 10 testow Inspector Lite przeszlo, w tym parser obu
launcherow i zachowanie remote loadera. 586 testow Angulara przeszlo,
produkcyjny build wygenerowal zasoby statyczne, a `FrontendPageTest`
potwierdzil ich serwowanie przez Spring. Pelne
`mvn -q -Pbackend-dev clean package` wygenerowalo JAR z czystymi raportami
Surefire. Bookmarklet dla `http://localhost:8080` ma 453 bajty. Pelny snippet
ma poprawne separatory nowej linii. Reczny test poprawionej zakladki w zwyklym
Chrome pozostaje jawnym ostatnim krokiem UX spike'a.

### 2. Browser ingress i source mapping

- [ ] Zatwierdzic capture v1 oraz feature-owned target registry.
- [ ] Dodac backend validator/sanitizer i limity niezalezne od klienta.
- [ ] Dodac preflight exact origin/path -> system/screen/revision.
- [ ] Dodac same-origin start joba `ELEMENT_QUESTION` oraz idempotency.
- [ ] Korzystac z sesji operatora; nie dodawac pairingu/browser tokenu.

### 3. Element Focus Context

- [ ] Reuse'owac screen catalog i Screen Reachability po pinningu rewizji.
- [ ] Dodac ranking capture -> template/component z confidence i collisions.
- [ ] Publikowac zredagowane evidence oraz visibility limits.

### 4. AI, report, historia i export

- [ ] Dodac element-specific prompt, polski skill, policy i result contract.
- [ ] Dodac `analysisKind`, immediate history i live polling po linku.
- [ ] Dodac renderer wyniku i osobny export/import element question.
- [ ] Zachowac pelna kompatybilnosc UI Explorer v5.

### 5. Hardening i rollout

- [ ] Wykonac security, accessibility i browser compatibility review.
- [ ] Zweryfikowac CSP, COOP, popup blockers i polityki docelowych stacji.
- [ ] Uruchomic pilot na kontrolowanym CRM i zmierzyc skutecznosc transferu,
  capture size, target resolution i czas do accepted.
- [ ] Rozwazyc zarzadzany dodatkowy kanal tylko dla organizacji, ktore go
  dopuszczaja.
