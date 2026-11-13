# TDW Browser Tools

TDW Browser Tools jest dostarczany razem z frontendem Team Delivery Workspace.
Nie jest rozszerzeniem Chrome i nie ma stalego dostepu do odwiedzanych stron.
Menu udostepnia UX Inspector dla wskazanego elementu oraz UI Explorer dla
biezacego widoku.

## Uruchomienie

1. Otworz ekran `/ux-inspector` na wlasnej instancji TDW.
2. Wybierz `Dodaj Browser Tools` i przeciagnij element `TDW Browser Tools`
   z modala na pasek zakladek. Zakladka zawiera tylko maly loader przypiety do
   originu tej instancji TDW.
3. Na zwyklej stronie HTTP(S) uruchom zakladke i kliknij przycisk TDW w prawym
   dolnym rogu.
4. Dla UX Inspectora kliknij jego przycisk, wybierz maly `Zakres danych`
   (`Element` albo `Formularz`), kliknij `Wskaz element`, a nastepnie wskaz
   element na stronie. `Escape` konczy inspekcje i przywraca launcher. Po
   kazdym otwarciu menu `Zakres danych` jest zwiniety; po rozwinieciu ostatnio
   wybrany profil nadal jest zaznaczony.
5. Dla UI Explorera kliknij przycisk pod UX Inspectorem. Nowa karta TDW
   otworzy formularz bez wybierania elementu na badanej stronie.
6. Potwierdz Application, Branch i sugerowany View. Rewizja pochodzi z
   katalogu kodu; doprecyzuj scenariusz i uruchom job recznie.

Przycisk `X` w modalu instalacyjnym zamyka modal. Ikona `X` w naglowku menu
usuwa Browser Tools i wszystkie jego listenery z badanej strony.

## Granica zaufania

- Kod zakladki zawiera jedynie publiczny origin TDW i adres statycznego
  `loader.js`. Loader pobiera `protocol.js` oraz `runtime.js` z tego samego
  originu i ustawia `referrerPolicy=no-referrer`.
- Shell wyswietla publiczny `assets/brand/main-logo.png` z tego samego originu
  TDW, rowniez z `referrerPolicy=no-referrer`.
- Badana strona nie otrzymuje cookies, tokenow ani katalogu modeli. Runtime
  wysyla jeden bezposredni POST capture do originu TDW bez credentials.
- Profil `Element` nie czyta wartosci formularza. Profil `Formularz` zamraza
  pola widoczne w calym dokumencie w chwili capture, wraz z zastanym stanem
  walidacji. Nie ogranicza liczby pol. Wyklucza pola ukryte, hasla, tokeny,
  pliki, cookies i storage.
- Dla UX Inspectora runtime po wskazaniu elementu wywoluje jednokrotnie
  aplikacyjny `globalThis.getStoreState()`. Dostepny stan JSON jest wysylany
  razem z elementem i polami w jednym POST do TDW. Po recznym starcie joba
  backend utrwala go w `run.json` dla analizy i follow-up. Aplikacja powinna
  wystawiac do tej funkcji stan odpowiedni do
  analizy AI; znane pola sekretow sa redagowane. Blad, timeout lub brak
  funkcji pozostawia capture bez store'a.
- W obu profilach `globalThis.getHttpHistory()` jest odczytywany jednokrotnie,
  niezaleznie od store'a. Przyjmuje tablice do 50 wpisow albo Promise; czeka
  najwyzej 3 sekundy. Zachowuje pelne URL-e po redakcji sekretow, naglowki i
  cale dostepne body; nie instaluje interceptora ani nie ponawia wywolan API.
  Retry POST uzywa tego samego snapshotu. Po starcie historia jest prywatnym
  polem run.json; AI widzi indeks wszystkich wpisow i doczytuje wybrane
  dane przez JSON Pointer oraz fragmenty ponizej 8 KB.
- HTTP body obsluguje natywne FormData, File i Blob zwracane przez
  HttpDebugSnapshotUtils. FormData zachowuje kolejnosc i powtorzone nazwy;
  pliki zachowuja MIME, nazwe, size, lastModified i tresc UTF-8 lub Base64
  dla innych bajtow. Sekrety tekstowe nadal podlegaja redakcji. `[OMITTED]`
  jako cale body daje jawne SOURCE_OMITTED. Blad odczytu bajtow zachowuje
  metadane i BINARY_READ_FAILED. Dane wczesniej usuniete przez eksporter sa
  nieodzyskiwalne. Odczyt plikow podlega temu samemu timeoutowi 3 sekund.
- Runtime nie odczytuje screenshotow.
- UX capture v1 jest zapisywany jednym POST przez CORS. Odpowiedz zwraca
  `captureId`, a karta TDW otwiera formularz z tym ID i pobiera snapshot
  przez GET. Capture, pola, store i body HTTP nie maja transportowego limitu rozmiaru.
  Blad odczytu pol, store lub HTTP nie zatrzymuje capture ani analizy. Nieudany
  POST mozna ponowic bez ponownego wybierania elementu.
- UI Explorer nie pobiera store'a ani historii HTTP i przekazuje tylko dotychczasowa wskazowke
  widoku.
- Backend UX Inspectora ponownie waliduje capture. UI Explorer
  odbiera osobny kontekst: zredagowany route path oraz tagi glownego
  routowanego komponentu. Nie zawiera elementu ani wartosci formularzy.
  Obserwacja nie jest kodowana w URL ani zapisywana w storage.

## Ograniczenia

Zakladka, runtime albo POST moze byc blokowany przez CSP, mixed content,
polityke enterprise lub Chrome Local Network Access. Gdy popup jest
zablokowany, overlay pokazuje link do zapisanego capture. Pending capture
jest tylko w pamieci backendu i znika po restarcie przed startem analizy.
Domyslny CORS POST dopuszcza kazdy origin; administrator moze ustawic
`ux-inspector.capture.allowed-origins`. Bez limitow liczby i rozmiaru
snapshotow zuzycie pamieci backendu moze rosnac.

Nie sa wspierane chronione strony Chrome, PDF viewer, `file://` ani
cross-origin iframe.

## Demo CRM

`demo/index.html` zawiera fikcyjny getter z sukcesem, bledem, pending, cancelled
i duzym body oraz przelacznik store'a. Serwuj katalog demo z innego originu
HTTP i uruchom na nim zakladke swojej instancji TDW. Przykladowy cel odczytu:
entryId=101, part=responseBody, pointer=/customer/permissions/editable.
Pelny kontrakt opisuje `docs/architecture/ux-inspector-runtime-flow.md`.
