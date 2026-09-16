# TDW Browser Tools

TDW Browser Tools jest instalowany razem z frontendem Team Delivery Workspace.
Nie jest rozszerzeniem Chrome i nie ma stalego dostepu do odwiedzanych stron.
Pierwszym narzedziem dostepnym w menu jest Inspector wspolpracujacy z
dedykowanym feature'em UX Inspector w TDW.

## Uruchomienie

1. Otworz `/browser-tools/install.html` na wlasnej instancji TDW.
2. Przeciagnij `TDW Browser Tools` na pasek zakladek. Zakladka zawiera maly
   loader przypiety do originu tej instancji TDW. Alternatywnie skopiuj pelny,
   samowystarczalny kod do Chrome DevTools / Sources / Snippets.
3. Otworz zwykla strone HTTP(S), uruchom launcher i kliknij przycisk TDW w
   prawym dolnym rogu.
4. W menu wybierz zakres `Element` albo `Formularz`, uruchom `UX Inspector`,
   wskaz element i kliknij. `Escape` konczy inspekcje i przywraca launcher.
5. Dedykowany ekran UX Inspectora odbierze capture. Dopisz pytanie, potwierdz
   system, branch, view, model i reasoning effort, a nastepnie uruchom job.

Ikona `X` w naglowku menu usuwa Browser Tools i wszystkie jego listenery z
badanej strony.

Kontrolowany scenariusz CRM jest dostepny pod `/browser-tools/demo.html` i
uruchamia ten sam finalny `runtime.js` automatycznie. Automatyczny bootstrap
istnieje tylko na stronie demo; na badanych stronach nadal obowiazuje launcher
z bookmarkleta albo DevTools Snippet.

## Granica zaufania

- Kod bookmarkleta zawiera jedynie publiczny origin TDW i adres statycznego
  `loader.js`. Loader pobiera `protocol.js` oraz `runtime.js` z tego samego
  originu i ustawia `referrerPolicy=no-referrer`.
- Shell wyswietla dokladny publiczny `assets/brand/main-logo.png` z tego samego
  originu TDW, rowniez z `referrerPolicy=no-referrer`.
- Pelny DevTools Snippet zawiera protokol i runtime inline, dlatego nie pobiera
  z TDW kodu wykonywalnego; pobiera jedynie logo marki. Zablokowanie obrazu
  przez CSP nie zatrzymuje dzialania narzedzia.
- Badana strona nie otrzymuje cookies, tokenow, model catalog ani klienta REST.
- Profil `Element` nie czyta wartosci formularza. Profil `Formularz` zamraza
  dozwolone wartosci najblizszego formularza i jego `ValidityState`, ale zawsze
  wyklucza hasla, tokeny, hidden controls, pliki, cookies i storage.
- Runtime nie czyta requestow ani screenshotow.
- Automatyczny transfer wymaga zgodnosci `event.origin`, `event.source`, nonce,
  wersji protokolu i limitu 128 KiB.
- UX Inspector ponownie waliduje capture na zaufanym originie. Raw capture nie
  jest przekazywany do UI Explorera ani kodowany w URL.

## Ograniczenia

Bookmarklet albo pobranie statycznego runtime moze byc blokowane przez CSP,
polityke enterprise lub Chrome Local Network Access. Samowystarczalny DevTools
Snippet jest drugim obslugiwanym sposobem uruchomienia, jezeli DevTools sa
dostepne. Popup blocker albo Cross-Origin-Opener-Policy moze odciac
`window.opener`; operacja konczy sie wtedy bledem, a capture jest odrzucany.
Nie ma recznego transferu ani ekranu dummy.

Nie sa wspierane chronione strony Chrome, PDF viewer, `file://` ani
cross-origin iframe.
