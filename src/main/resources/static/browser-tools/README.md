# TDW Browser Tools

TDW Browser Tools jest dostarczany razem z frontendem Team Delivery Workspace.
Nie jest rozszerzeniem Chrome i nie ma stalego dostepu do odwiedzanych stron.
Pierwszym narzedziem w launcherze jest UX Inspector.

## Uruchomienie

1. Otworz ekran `/ux-inspector` na wlasnej instancji TDW.
2. Wybierz `Dodaj Browser Tools` i przeciagnij element `TDW Browser Tools`
   z modala na pasek zakladek. Zakladka zawiera tylko maly loader przypiety do
   originu tej instancji TDW.
3. Na zwyklej stronie HTTP(S) uruchom zakladke i kliknij przycisk TDW w prawym
   dolnym rogu.
4. W menu wybierz zakres `Element` albo `Formularz`, uruchom `UX Inspector`,
   wskaz element i kliknij. `Escape` konczy inspekcje i przywraca launcher.
5. Ekran UX Inspectora odbierze capture v1. Dopisz pytanie, potwierdz
   Application, Branch, View, model i reasoning effort, a nastepnie uruchom job.

Ikona `X` w naglowku menu usuwa Browser Tools i wszystkie jego listenery z
badanej strony.

## Granica zaufania

- Kod zakladki zawiera jedynie publiczny origin TDW i adres statycznego
  `loader.js`. Loader pobiera `protocol.js` oraz `runtime.js` z tego samego
  originu i ustawia `referrerPolicy=no-referrer`.
- Shell wyswietla publiczny `assets/brand/main-logo.png` z tego samego originu
  TDW, rowniez z `referrerPolicy=no-referrer`.
- Badana strona nie otrzymuje cookies, tokenow, katalogu modeli ani klienta
  REST.
- Profil `Element` nie czyta wartosci formularza. Profil `Formularz` zamraza
  dozwolone wartosci najblizszego formularza i jego `ValidityState`, wlacznie
  z kontrolkami `type=hidden`. Nadal wyklucza hasla, tokeny, pliki, cookies i
  storage.
- Runtime nie czyta requestow ani screenshotow.
- Transfer wymaga zgodnosci `event.origin`, `event.source`, nonce, protokolu v1
  oraz limitu 128 KiB.
- UX Inspector ponownie waliduje capture na zaufanym originie. Raw capture nie
  jest przekazywany do UI Explorera ani kodowany w URL.

## Ograniczenia

Zakladka albo pobranie statycznego runtime moze byc blokowane przez CSP,
polityke enterprise lub Chrome Local Network Access. Popup blocker albo
Cross-Origin-Opener-Policy moze odciac `window.opener`; operacja konczy sie
wtedy bledem, a capture jest odrzucany. Nie ma alternatywnego transferu ani
trybu DevTools.

Nie sa wspierane chronione strony Chrome, PDF viewer, `file://` ani
cross-origin iframe.
