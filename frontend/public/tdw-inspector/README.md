# TDW Inspector Lite

Inspector Lite jest instalowany razem z frontendem Team Delivery Workspace.
Nie jest rozszerzeniem Chrome i nie ma stalego dostepu do odwiedzanych stron.

## Uruchomienie

1. Otworz `/tdw-inspector/install.html` na wlasnej instancji TDW.
2. Przeciagnij `TDW · wskaz element` na pasek zakladek. Zakladka zawiera maly
   loader przypiety do originu tej instancji TDW. Alternatywnie skopiuj pelny,
   samowystarczalny kod do Chrome DevTools / Sources / Snippets.
3. Otworz zwykla strone HTTP(S), uruchom launcher, wskaz element i kliknij.
4. Sprawdz capture na zaufanym ekranie TDW i uruchom demonstracyjny formularz.

Kontrolowany scenariusz CRM jest dostepny pod `/tdw-inspector/demo.html`.

## Granica zaufania

- Kod bookmarkleta zawiera jedynie publiczny origin TDW i adres statycznego
  `loader.js`. Loader pobiera `protocol.js` oraz `runtime.js` z tego samego
  originu i ustawia `referrerPolicy=no-referrer`.
- Pelny DevTools Snippet zawiera runtime inline i nie laczy sie z TDW przed
  wybraniem elementu.
- Badana strona nie otrzymuje cookies, tokenow, model catalog ani klienta REST.
- Runtime nie czyta wartosci formularzy, cookies, storage, requestow ani
  screenshotow.
- Automatyczny transfer wymaga zgodnosci `event.origin`, `event.source`, nonce,
  wersji protokolu i limitu 64 KiB.
- Capture jest niezaufanym inputem i musi byc ponownie walidowany przez backend
  przed przyszlym startem produkcyjnej analizy.

## Ograniczenia

Bookmarklet albo pobranie statycznego runtime moze byc blokowane przez CSP,
polityke enterprise lub Chrome Local Network Access. DevTools Snippet jest
samowystarczalnym fallbackiem, jezeli DevTools pozostaja dostepne. Popup
blocker albo Cross-Origin-Opener-Policy moze odciac `window.opener`; wtedy
selektor pokazuje JSON do recznego skopiowania, a ekran capture przyjmuje
reczny import.

Nie sa wspierane chronione strony Chrome, PDF viewer, `file://` ani
cross-origin iframe.
