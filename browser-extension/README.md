# TDW Browser Extension

Wewnetrzne rozszerzenie Chrome dla Team Delivery Workspace. Jest hostem wielu
niezaleznych funkcji pracujacych na stronach przegladarki. Pierwszym modulem
jest prototyp UI Explorera: `Ctrl` + `Alt` wlacza wybor elementu, klik otwiera
modal i uruchamia lokalny dummy job.

Aktualny starter nie laczy sie z TDW REST ani AI. Dummy `202 Accepted` jest
generowane przez service workera i sluzy do zweryfikowania UX, permissions,
message bus oraz struktury przyszlych integracji.

## Uruchomienie

```text
npm ci
npm run check
```

Nastepnie:

1. Otworz `chrome://extensions`.
2. Wlacz `Developer mode`.
3. Kliknij `Load unpacked`.
4. Wybierz katalog `browser-extension/dist`.
5. Przypnij `TDW Browser Companion` do paska Chrome.

## Demo

```text
npm run demo
```

Otworz `http://localhost:4175/crm-demo.html`, kliknij ikone rozszerzenia i
wybierz `Wlacz UI Explorer na tej stronie`. Nastepnie:

1. przytrzymaj `Ctrl` + `Alt`,
2. przesuwaj mysz, aby zobaczyc highlight,
3. kliknij wybrany element,
4. sprawdz capture preview i wypelnij dummy modal,
5. kliknij `Rozpocznij analize demo`.

Klik przejety przez selektor nie powinien wykonac akcji strony. `AltGr` nie
powinien aktywowac selektora.

## Architektura rozszerzen

```text
src/platform/       neutralny runtime, kontrakty, ustawienia i permissions
src/features/       funkcje globalne, obecnie ui-explorer
src/integrations/   przyszle integracje stron, np. Confluence albo GitLab
src/content/        bootstrap registry w izolowanym content script context
src/background/     message router i lifecycle service workera
src/popup/          konfiguracja biezacego originu
src/options/        konfiguracja calego rozszerzenia
```

Nowa funkcja implementuje `ContentFeatureModule` i jest dopisywana tylko do
`contentFeatureRegistry`. Integracja konkretnej strony deklaruje predicate
oparty o publiczny `PageContext`, zamiast umieszczac sprawdzenia hosta w
platform runtime. Background handlery sa rejestrowane w `MessageRouter`.

Ustawienia sa przechowywane per origin. Manifest nie deklaruje stalego
`<all_urls>`; popup prosi o optional host permission dopiero po akcji
uzytkownika. Produkcyjny transport TDW i pairing zostana dodane w osobnym
inkremencie.

## Skrypty

- `npm test` - testy Vitest/jsdom,
- `npm run typecheck` - TypeScript strict bez emisji,
- `npm run build` - kompletna paczka unpacked w `dist/`,
- `npm run check` - typecheck, testy i build,
- `npm run demo` - lokalna, fikcyjna strona CRM na porcie 4175.
