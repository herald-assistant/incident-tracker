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
- Badana strona nie otrzymuje cookies, tokenow, katalogu modeli ani klienta
  REST.
- Profil `Element` nie czyta wartosci formularza. Profil `Formularz` zamraza
  pola widoczne w calym dokumencie w chwili capture, wraz z zastanym stanem
  walidacji. Nie ogranicza liczby pol. Wyklucza pola ukryte, hasla, tokeny,
  pliki, cookies i storage.
- Dla UX Inspectora runtime po wskazaniu elementu wywoluje jednokrotnie
  aplikacyjny `globalThis.getStoreState()`. Dostepny stan JSON (do 16 MiB)
  przechodzi porcjami do karty TDW i jest dolaczany do recznie uruchomionego
  joba. Aplikacja powinna wystawiac do tej funkcji stan odpowiedni do
  analizy AI; znane pola sekretow sa redagowane. Blad, timeout lub brak
  funkcji pozostawia capture bez store'a.
- Runtime nie czyta requestow ani screenshotow.
- Capture, transfer pol i transfer store'a wymagaja zgodnosci `event.origin`,
  `event.source`, nonce i protokolu v1. UX capture v2 ma limit 128 KiB,
  kazdy z osobnych transferow ma limit 16 MiB, a wskazowka UI Explorera 4 KiB.
  Blad odczytu lub przekazania pol nie zatrzymuje capture ani analizy.
- UI Explorer nie pobiera store'a i przekazuje tylko dotychczasowa wskazowke
  widoku.
- UX Inspector ponownie waliduje capture na zaufanym originie. UI Explorer
  odbiera osobny kontekst: zredagowany route path oraz tagi glownego
  routowanego komponentu. Nie zawiera elementu ani wartosci formularzy.
  Obserwacja nie jest kodowana w URL ani zapisywana w storage.

## Ograniczenia

Zakladka albo pobranie statycznego runtime moze byc blokowane przez CSP,
polityke enterprise lub Chrome Local Network Access. Popup blocker albo
Cross-Origin-Opener-Policy moze odciac `window.opener`; operacja konczy sie
wtedy bledem, a capture jest odrzucany. Nie ma alternatywnego transferu ani
trybu DevTools.

Nie sa wspierane chronione strony Chrome, PDF viewer, `file://` ani
cross-origin iframe.
