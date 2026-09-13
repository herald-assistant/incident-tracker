# UI Explorer - inspekcja elementu i kontekst AI z przegladarki

Status: draft

Source need: [UI Explorer - dokumentacja funkcjonalna widokow](../needs/ui-explorer.md)

Klasyfikacja propozycji: L3 - nowy dostep do obrazu i stanu sesji przegladarki
oraz nowa granica przekazania tych danych do analizy. Przed implementacja
wymaga decyzji architektonicznej, etapowania i rollbacku.

Ten plan dotyczy odrebnego, proponowanego rozszerzenia istniejacego UI Explorera.
Obecny [plan MVP](ui-explorer.md)
analizuje statycznie kod widoku i jawnie wyklucza wykonywanie frontendu oraz
screenshoty. Ponizsze zachowanie nie jest opisem aktualnego runtime ani zmiana
zakresu tamtego MVP.

## Potrzeba / dlaczego

Analityk widzacy konkretny przycisk, pole lub fragment strony chce zapytac,
jak dziala ten element, bez znajomosci klasy Angulara, sciezki pliku i grafu
zaleznosci. Sam URL lub katalog ekranow zawieraja zbyt malo informacji, aby
trafnie odnalezc logike wybranego elementu. Wskazanie elementu w uruchomionej
aplikacji ma dostarczyc ograniczony, sprawdzalny punkt wejscia do kodu, a AI
ma wyjasnic zachowanie, warunki oraz skutki z dowodami i poziomem pewnosci.

Docelowy gest: uzytkownik trzyma `Ctrl`, najechanie mysza pokazuje obramowanie
elementu pod kursorem, a `Ctrl` + klik zamraza wybor i uruchamia capture.
Podczas samego hover nie ma wywolania AI ani pobierania kodu. Po zwolnieniu
`Ctrl` zamrozony wybor pozostaje widoczny do ponownego wyboru lub anulowania.

## Proponowane rozwiazanie

### Granice i przeplyw

```text
Chrome extension content script
  -> lokalny hover, overlay i identyfikacja elementu
  -> Ctrl + klik: snapshot DOM, bounds, metadane strony
  -> extension service worker/background: chrome.tabs.captureVisibleTab
  -> feature-owned backend UI Explorer: walidacja i deterministyczny context
  -> istniejace read-only GitLab/MCP tools i runtime AI
  -> odpowiedz funkcjonalna z dowodami i visibility limits
```

Content script obsluguje gest i DOM, ale nie otrzymuje credentiali GitLab.
Service worker wykonuje operacje wymagajace uprawnien extension. Backend
ponownie weryfikuje zarejestrowany frontend, repository/ref i zakres dostepu;
nie uznaje nazwy repozytorium ani brancha z payloadu za zaufany scope tooli.
Wykorzystuje obecne capability Operational Context, GitLab search/read,
feature-owned context pipeline i platformowy runtime AI, bez zaleznosci od
Incident Analysis. Decyzja o kontrakcie przeslania screenshotu, retencji,
redakcji danych i auth extension/backend nalezy do etapu architektonicznego.

### Snapshot po wyborze

Kontrakt wejsciowy do dalszej analizy powinien przenosic co najmniej:

```json
{
  "question": "Wyjasnij jak dziala zaznaczony element",
  "ui": {
    "text": "Zapisz",
    "tag": "button",
    "attributes": {},
    "domPath": [],
    "componentCandidates": []
  },
  "browser": {
    "url": "/customers/123/details",
    "fullUrl": "https://example.test/customers/123/details",
    "title": "Szczegoly klienta",
    "viewport": {
      "width": 1440,
      "height": 900,
      "devicePixelRatio": 1,
      "scrollX": 0,
      "scrollY": 0
    },
    "targetBounds": {
      "x": 120,
      "y": 240,
      "width": 100,
      "height": 36,
      "documentX": 120,
      "documentY": 240
    },
    "screenshot": null
  },
  "frontend": {
    "repository": "customer-web",
    "branch": "develop"
  }
}
```

`browser.url` jest sciezka z query/hash, `fullUrl` pelnym adresem, a
`targetBounds` opisuje element w CSS pixels. Screenshot widocznej karty
uzyskany przez `chrome.tabs.captureVisibleTab` nalezy skojarzyc z tymi
wspolrzednymi i `devicePixelRatio`, aby wskazac lub wyciac wlasciwy obszar.
Trzeba zdecydowac, czy wysylac caly obraz, wycinek czy identyfikator artefaktu;
prototyp DevTools ma w tym polu `null`, bo skrypt strony nie wykonuje capture.
Przed capture mozna ukryc overlay, aby nie zaslanial obrazu, albo nalozyc
oznaczenie na kopie obrazu po capture.

Tekst trzeba normalizowac i ograniczac dlugosc. `attributes` zawiera tylko
przydatne, limitowane dane po odfiltrowaniu Angularowego `_ngcontent-*` i
`_nghost-*` oraz wrazliwych wartosci; `id`, `class` i `style` moga sluzyc do
lokalnej identyfikacji, ale nie powinny bezrefleksyjnie trafiac do AI. Payload
nie moze kopiowac hasel, tokenow, wartosci pol wrazliwych ani calego DOM.

### Rozpoznanie elementu bez debug API

Podstawowa sciezka dziala bez `window.ng`: od targetu przechodzi po DOM do
root, zapisuje target i kandydatow na hosty komponentow, a `domPath` filtruje
zwykle `div`/`span` bez znaczenia. Zachowuje hosty-kandydatow oraz strukturalne
`form`, `dialog`, `nav`, `main`, `section` i wezly z `role`. Nie ucina sciezki
przed rozpoznaniem kontekstu widoku; limituje i streszcza wynik dopiero po
trawersowaniu. `domPath` pozostaje visual/DOM path, nie drzewem Angulara.

Sygnaly: custom-element tag (np. `app-customer`), atrybut `_nghost-*` oraz
dopasowanie do indeksu selektorow wygenerowanego ze zrodel repozytorium.
`_nghost-*` nie zawsze wystepuje, a custom tag nie dowodzi, ze to komponent
Angulara. Bez debug API wynik nazywamy `nearestComponentHost` lub
`componentCandidates`, nie `owningComponent`; nie rekonstruujemy runtime
instance ani pewnego logicznego rodzica.

Kazdy kandydat zawiera `host`, `selector`, opcjonalne `componentName` i
`sourceFile`, `detection` oraz `confidence`. Przykladowa skala: debug API
`window.ng` = `exact`, `_nghost-*` = `high`, sam custom-element = `medium`;
dopasowanie do indeksu wzmacnia identyfikacje selector/source file, lecz samo
nie potwierdza runtime ownership. Przy konfliktach zachowujemy alternatywy i
powod niepewnosci, zamiast arbitralnie deklarowac jedna klase.

### Opcjonalny fast path Angulara

Jesli na dozwolonym srodowisku istnieje `window.ng`, wykorzystac
`getComponent(element)` dla hosta, `getOwningComponent(element)` dla zwyklego
wezla wewnatrz template oraz `getHostElement(component)` do potwierdzenia
hosta. Przez granice isolated world content script moze byc potrzebny waski
page-context bridge; nie wystawiac calego Angular runtime jako kontraktu
extension. Brak lub blad tych metod musi automatycznie przejsc do DOM/repo
fallbacku. `__ngContext__` i protokol Angular DevTools nie sa stabilnym API i
nie powinny stanowic podstawy funkcji.

### Indeks kodu i kontekst dla AI

Generowac z `@Component` zrodel wybranego repository/ref indeks
`selector -> component class -> source file` z identyfikatorem rewizji. Wspierac
selektory elementowe, atrybutowe, klasowe, listy i kombinacje; dopasowywac do
rzeczywistego elementu przez `element.matches(selector)` z obsluga
niepoprawnych/niewspieranych selektorow. Nie wyszukiwac calego repo na kazdy
hover ani nie polegac jedynie na nazwach custom tagow. Indeks jest materialem
zrodlowym, a nie dowodem, ze dany komponent posiada klikniety wezel.

Przed wywolaniem AI deterministycznie wyznaczyc maly zestaw najblizszych
trafien (orientacyjnie 3-5 plikow): selector/host, outline klasy, imports,
istotny fragment template i najblizszy routing context. Nie wczytywac pelnego
kodu wszystkich przodkow. Model dostaje pytanie, snapshot, screenshot/bounds,
wersje zrodla i te skroty; pelny kod konkretnych komponentow, handlerow,
fasad, serwisow oraz klientow API doczytuje przez istniejace read-only tools
z przypietej rewizji i ograniczonym budzetem. AI ma podawac evidence oraz
odroznic potwierdzone zachowanie od hipotezy.

Analiza prowadzi dwie jawne sciezki:

```text
visual path: button -> hosty/sekcje DOM -> kontekst ekranu
source path: component -> handler -> facade/store/service -> API -> backend
```

Pierwsza wyjasnia, gdzie uzytkownik widzi element; druga, co dzieje sie po
akcji. Laczenie sciezek wymaga dowodu z template/bindingu, nie podobienstwa
nazw. Wynik obejmuje warunki widocznosci i aktywnosci, walidacje, uprawnienia,
stan, requesty oraz skutki lub jawny brak widocznosci implementacji backendu.

### Build i dostepnosc debug API

Podstawowa funkcja nie zalezy od debug buildu ani source maps. PROD pozostaje
`optimization: true`, `sourceMap: false`. Na kontrolowanym TEST mozna
rozwazyc osobny artefakt `optimization: false`, `sourceMap: false` jako fast
path do `window.ng`; jego faktyczna dostepnosc musi byc sprawdzona w danej
wersji Angulara i konfiguracji. Taki build ma inne cechy runtime i wiekszy
bundle, wiec nie wolno go pomylic z artefaktem PROD w CI/CD. Source maps nie
sa potrzebne do mapowania komponentow, bo kod pochodzi z kontrolowanego
GitLab/MCP. Runtime feature flag steruje widocznoscia produktu, nie usuwa
debug API z dostarczonego JS. To konfiguracja i promocja artefaktow builda
stanowi security boundary; usuwanie `window.ng` w runtime nie jest nim.

## Zakres

- Gest Ctrl + hover / Ctrl + klik, freeze i anulowanie wyboru.
- Snapshot DOM/viewport/bounds oraz screenshot widocznej karty z extension.
- Resolver kandydatow i indeks selector/class/file z przypietej rewizji.
- Deterministyczny context, read-only doglebianie i wyjasnienie elementu przez AI.
- Jawna pewnosc, dowody, ograniczenia i kontrola danych wrazliwych.

## Non-goals

- Odtwarzanie calego drzewa logicznego Angulara z produkcyjnego DOM.
- Udostepnianie source maps lub wymuszanie debug buildu na TEST/PROD.
- Pobieranie calego kodu wszystkich rodzicow przy kazdym kliknieciu.
- Wykonywanie akcji biznesowej wybranego elementu, modyfikacja badanego repo
  lub obchodzenie autoryzacji badanego systemu.
- Zastepowanie istniejacej analizy calego widoku z planu MVP.

## Ograniczenia i ryzyka

- Content projection, CDK overlay/portal, dynamic components i Shadow DOM
  powoduja, ze DOM tree moze nie odpowiadac logicznemu drzewu Angulara.
  Wybrany host moze byc tylko najblizsza wskazowka; trzeba raportowac limity.
- Hover i click po wklejeniu prototypu w DevTools moga wymagac pierwszego
  klikniecia strony, bo fokus pozostaje w konsoli. Content script docelowej
  extension dziala w normalnym kontekscie strony, wiec ten problem praktycznie
  zanika. W `mousemove` uzywac `event.ctrlKey`, a nie oddzielnego stanu
  `keydown`/`keyup`, ktory moze sie rozjechac po przelaczeniu karty. Ruch myszy
  nad DevTools nadal nie trafia do dokumentu.
- Przechwycenie Ctrl + klik powinno blokowac natywna akcje elementu tylko
  podczas aktywnej selekcji. Nalezy sprawdzic kolizje ze skrotami strony,
  selekcja tekstu i oczekiwanym zachowaniem `preventDefault`/propagation.
- Screenshot, URL, tekst i atrybuty moga ujawniac dane klienta. Potrzebne sa
  ograniczenie hostow i uprawnien extension, minimalizacja, redakcja,
  transport, polityka retencji i jawne poinformowanie operatora o capture.
- Branch z payloadu moze nie odpowiadac wdrozonemu artefaktowi. Analiza musi
  wskazywac zweryfikowany ref/commit albo jawna niepewnosc wersji.
- Build `optimization: false` i opublikowane `.map` zwiekszaja ekspozycje
  struktury/zrodel; `sourceMap: false` oraz kontrola artefaktow w CI/CD sa
  niezalezne od runtime flagi.

## Kryteria akceptacji

1. Na wspieranej stronie Ctrl + hover zaznacza element bez wywolania backendu,
   a Ctrl + klik zatrzymuje wybor, nie wykonuje akcji przycisku i tworzy jeden
   snapshot z wymaganymi polami.
2. Na buildzie bez `window.ng` target i kandydaci nadal mapuja sie do
   zweryfikowanego repository/ref; selector atrybutowy i klasowy sa obslugiwane.
3. Gdy debug API istnieje, wynik jest dokladniejszy, ale jego usuniecie nie
   przerywa feature'a ani nie zamienia heurystyki w `exact`.
4. Screenshot i targetBounds wskazuja ten sam element przy roznych DPR,
   scrollu i rozmiarach viewportu; brak uprawnienia/capture daje jawny wynik
   czesciowy.
5. AI pokazuje visual path i source path z dowodami, a braki wynikajace z
   overlay/projection/dynamic runtime lub niedostepnego backendu sa nazwane.
6. PROD jest budowany z `optimization: true`, `sourceMap: false`; zaden
   testowy debug artefakt ani `.map` nie trafia do promocji produkcyjnej.

## Kroki

- [ ] 1. Zrobic baseline istniejacego UI Explorera, conformance delta i audit
  konsumentow API/FE/tools; podjac decyzje L3 o dostepie extension do sesji,
  obrazu, auth, retencji i rollbacku. Dowod: zapis decyzji i macierz ryzyk
  powiazana z aktualnym MVP, bez zmiany jego kontraktu.
- [ ] 2. Ustalic wersjonowany kontrakt capture i granice uprawnien. Dowod:
  testy walidacji payloadu, redakcji danych wrazliwych, ograniczenia hostow,
  mismatchu repository/ref i odmowy capture.
- [ ] 3. Dostarczyc content script z gestem, overlay, freeze/reset i lokalnym
  DOM snapshotem. Dowod: testy hover bez requestow, Ctrl + klik bez akcji
  strony, zwolnienia Ctrl, zmiany karty, scrollu i cleanup listenerow.
- [ ] 4. Dodac service worker/background z `captureVisibleTab` i powiazaniem
  obrazu z bounds. Dowod: testy uprawnien, DPR, scrollu, overlay oraz jawnego
  fallbacku, gdy screenshot nie jest dostepny.
- [ ] 5. Generowac indeks selektorow dla przypietej rewizji i resolver DOM
  bez `window.ng`; dodac opcjonalny debug fast path. Dowod: fixtures dla
  selectorow elementowych/atrybutowych/klasowych, konfliktow, `_nghost-*`,
  zwyklego buttona, projection i CDK overlay.
- [ ] 6. Podlaczyc feature-owned backend context i read-only GitLab/MCP tools,
  z ograniczonym seedem oraz doglebianiem na zadanie AI. Dowod: testy limitu
  plikow/budzetu, pinning rewizji, evidence, brak pelnego kodu przodkow w
  initial prompt i regresja konsumentow wspolnych capability.
- [ ] 7. Wprowadzic wynik wyjasnienia elementu z visual/source path,
  confidence i visibility limits w istniejacym UX UI Explorera. Dowod:
  scenariusze akceptacyjne dla przycisku, formularza, uprawnienia, API,
  niejednoznacznego kandydata i niedostepnej implementacji.
- [ ] 8. Zweryfikowac build/CI oraz dokonac architecture diff i aktualizacji
  dokumentacji po wdrozeniu. Dowod: manifesty TEST/PROD, brak `.map` na PROD,
  test regresji obecnego screen-centered flow i plan rollbacku extension.


## Sample script example:

```js
(() => {
// ============================================================
// CONFIG
// ============================================================

  const CONFIG = {
    repository: 'customer-web',
    branch: 'develop',

    question: 'Wyjaśnij jak działa zaznaczony element',

    border: '3px solid #ff2d55',
    background: 'rgba(255, 45, 85, 0.08)',

    maxTextLength: 300,
    maxDomPathLength: 15
  };

// ============================================================
// CLEANUP PREVIOUS INSTANCE
// ============================================================

  if (window.__uiExplorer?.stop) {
    window.__uiExplorer.stop();
  }

// ============================================================
// STATE
// ============================================================

  let ctrlPressed = false;
  let currentElement = null;

// ============================================================
// OVERLAY
// ============================================================

  const overlay = document.createElement('div');

  Object.assign(overlay.style, {
    position: 'fixed',
    pointerEvents: 'none',
    zIndex: '2147483647',
    border: CONFIG.border,
    background: CONFIG.background,
    boxSizing: 'border-box',
    display: 'none'
  });

  document.documentElement.appendChild(overlay);

  const label = document.createElement('div');

  Object.assign(label.style, {
    position: 'fixed',
    pointerEvents: 'none',
    zIndex: '2147483647',
    background: '#111',
    color: '#fff',
    padding: '4px 8px',
    borderRadius: '4px',
    font: '12px monospace',
    maxWidth: '500px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    display: 'none'
  });

  document.documentElement.appendChild(label);

// ============================================================
// HELPERS
// ============================================================

  function normalizeText(text) {
    if (!text) {
      return null;
    }

    const normalized = text
            .replace(/\s+/g, ' ')
            .trim();

    if (!normalized) {
      return null;
    }

    return normalized.length > CONFIG.maxTextLength
            ? normalized.slice(0, CONFIG.maxTextLength) + '…'
            : normalized;
  }

  function interestingAttributes(element) {
    const ignored = new Set([
      'class',
      'style',
      'id'
    ]);

    return [...element.attributes]
            .filter(attr =>
                    !ignored.has(attr.name) &&
                    !attr.name.startsWith('_ngcontent-') &&
                    !attr.name.startsWith('_nghost-')
            )
            .reduce((result, attr) => {
              result[attr.name] =
                      attr.value.length > 100
                              ? attr.value.slice(0, 100) + '…'
                              : attr.value;

              return result;
            }, {});
  }

  function cssLikeSelector(element) {
    if (!(element instanceof Element)) {
      return null;
    }

    const tag = element.tagName.toLowerCase();

    if (element.id) {
      return `${tag}#${CSS.escape(element.id)}`;
    }

    const classes = [...element.classList]
            .filter(Boolean)
            .slice(0, 3);

    let selector = tag;

    if (classes.length > 0) {
      selector += classes
              .map(c => `.${CSS.escape(c)}`)
              .join('');
    }

    return selector;
  }

  function hasNgHost(element) {
    return [...element.attributes]
            .some(attr => attr.name.startsWith('_nghost-'));
  }

  function isCustomElement(element) {
    return element.tagName
            .toLowerCase()
            .includes('-');
  }

  function getAngularDebugComponent(element) {
    if (!window.ng?.getComponent) {
      return null;
    }

    try {
      return window.ng.getComponent(element) || null;
    } catch {
      return null;
    }
  }

  function componentCandidate(element) {
    const angularComponent = getAngularDebugComponent(element);

    if (angularComponent) {
      return {
        selector: inferAngularSelector(element),
        componentName:
                angularComponent.constructor?.name ?? null,
        detection: 'window.ng',
        confidence: 'exact'
      };
    }

    if (hasNgHost(element)) {
      return {
        selector: inferAngularSelector(element),
        componentName: null,
        detection: '_nghost',
        confidence: 'high'
      };
    }

    if (isCustomElement(element)) {
      return {
        selector: element.tagName.toLowerCase(),
        componentName: null,
        detection: 'custom-element',
        confidence: 'medium'
      };
    }

    return null;
  }

  function inferAngularSelector(element) {
    const tag = element.tagName.toLowerCase();

    if (tag.includes('-')) {
      return tag;
    }

    const attrs = [...element.attributes]
            .map(a => a.name)
            .filter(name =>
                    !name.startsWith('_ngcontent-') &&
                    !name.startsWith('_nghost-') &&
                    !['class', 'style', 'id'].includes(name)
            );

    if (attrs.length > 0) {
      return `${tag}[${attrs[0]}]`;
    }

    return tag;
  }

// ============================================================
// DOM PATH
// ============================================================

  function buildDomPath(startElement) {
    const path = [];

    let element = startElement;
    let depth = 0;

    while (
            element &&
            element instanceof Element &&
            depth < CONFIG.maxDomPathLength
            ) {
      const candidate = componentCandidate(element);

      const entry = {
        selector: cssLikeSelector(element),
        tag: element.tagName.toLowerCase()
      };

      if (candidate) {
        entry.angularCandidate = candidate;
      }

      // Zachowujemy target zawsze.
      // Dla rodziców preferujemy interesujące węzły.
      if (
              depth === 0 ||
              candidate ||
              ['form', 'dialog', 'nav', 'main', 'section'].includes(
                      element.tagName.toLowerCase()
              ) ||
              element.getAttribute('role')
      ) {
        path.push(entry);
      }

      if (
              element === document.body ||
              element === document.documentElement
      ) {
        break;
      }

      element = element.parentElement;
      depth++;
    }

    return path;
  }

  function buildComponentCandidates(startElement) {
    const candidates = [];

    let element = startElement;

    while (element && element instanceof Element) {
      const candidate = componentCandidate(element);

      if (candidate) {
        candidates.push({
          ...candidate,
          host: cssLikeSelector(element)
        });
      }

      if (element === document.body) {
        break;
      }

      element = element.parentElement;
    }

    return candidates;
  }

// ============================================================
// OVERLAY
// ============================================================

  function highlight(element) {
    if (!element) {
      return;
    }

    const rect = element.getBoundingClientRect();

    Object.assign(overlay.style, {
      display: 'block',
      left: `${rect.left}px`,
      top: `${rect.top}px`,
      width: `${rect.width}px`,
      height: `${rect.height}px`
    });

    const candidate = componentCandidate(element);

    const description =
            candidate?.componentName ??
            candidate?.selector ??
            cssLikeSelector(element);

    label.textContent = description;

    Object.assign(label.style, {
      display: 'block',
      left: `${Math.max(0, rect.left)}px`,
      top: `${Math.max(0, rect.top - 26)}px`
    });
  }

  function hideHighlight() {
    overlay.style.display = 'none';
    label.style.display = 'none';
  }

// ============================================================
// SNAPSHOT
// ============================================================

  function buildSnapshot(element) {
    const rect = element.getBoundingClientRect();

    return {
      question: CONFIG.question,

      ui: {
        text: normalizeText(element.innerText || element.textContent),
        tag: element.tagName.toLowerCase(),

        attributes: interestingAttributes(element),

        domPath: buildDomPath(element),

        componentCandidates:
                buildComponentCandidates(element)
      },

      browser: {
        url:
                location.pathname +
                location.search +
                location.hash,

        fullUrl: location.href,

        title: document.title,

        viewport: {
          width: window.innerWidth,
          height: window.innerHeight,
          devicePixelRatio: window.devicePixelRatio,
          scrollX: window.scrollX,
          scrollY: window.scrollY
        },

        targetBounds: {
          x: Math.round(rect.x),
          y: Math.round(rect.y),
          width: Math.round(rect.width),
          height: Math.round(rect.height),

          documentX:
                  Math.round(rect.x + window.scrollX),

          documentY:
                  Math.round(rect.y + window.scrollY)
        },

        // Z poziomu zwykłego JS uruchomionego w stronie
        // nie robimy bezpiecznie screenshotu viewportu.
        //
        // Chrome Extension może tutaj wstawić wynik
        // chrome.tabs.captureVisibleTab().
        screenshot: null
      },

      frontend: {
        repository: CONFIG.repository,
        branch: CONFIG.branch
      }
    };
  }

// ============================================================
// EVENTS
// ============================================================

  function onMouseMove(event) {
    if (!ctrlPressed) {
      return;
    }

    const element = document.elementFromPoint(
            event.clientX,
            event.clientY
    );

    if (
            !element ||
            element === overlay ||
            element === label
    ) {
      return;
    }

    currentElement = element;

    highlight(element);
  }

  function onKeyDown(event) {
    if (event.key === 'Control') {
      ctrlPressed = true;
    }
  }

  function onKeyUp(event) {
    if (event.key === 'Control') {
      ctrlPressed = false;
      currentElement = null;

      hideHighlight();
    }
  }

  function onClick(event) {
    if (!event.ctrlKey) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();

    const element =
            document.elementFromPoint(
                    event.clientX,
                    event.clientY
            );

    if (!element) {
      return;
    }

    const snapshot = buildSnapshot(element);

    console.log(
            '%cUI Explorer capture',
            [
              'background:#ff2d55',
              'color:white',
              'font-weight:bold',
              'padding:4px 8px',
              'border-radius:4px'
            ].join(';')
    );

    console.log(snapshot);

    console.log(
            'JSON:\n' +
            JSON.stringify(snapshot, null, 2)
    );

    window.__uiExplorer.lastCapture = snapshot;

    return snapshot;
  }

// ============================================================
// START / STOP
// ============================================================

  function stop() {
    document.removeEventListener(
            'mousemove',
            onMouseMove,
            true
    );

    document.removeEventListener(
            'keydown',
            onKeyDown,
            true
    );

    document.removeEventListener(
            'keyup',
            onKeyUp,
            true
    );

    document.removeEventListener(
            'click',
            onClick,
            true
    );

    overlay.remove();
    label.remove();

    delete window.__uiExplorer;

    console.log('UI Explorer stopped');
  }

  document.addEventListener(
          'mousemove',
          onMouseMove,
          true
  );

  document.addEventListener(
          'keydown',
          onKeyDown,
          true
  );

  document.addEventListener(
          'keyup',
          onKeyUp,
          true
  );

  document.addEventListener(
          'click',
          onClick,
          true
  );

  window.__uiExplorer = {
    stop,
    lastCapture: null,
    config: CONFIG
  };

  console.log(
          '%cUI Explorer started',
          [
            'background:#1769aa',
            'color:white',
            'font-weight:bold',
            'padding:4px 8px',
            'border-radius:4px'
          ].join(';')
  );

  console.log(
          'Hold CTRL to highlight elements. CTRL + click to capture. Stop with: __uiExplorer.stop()'
  );
})();
```