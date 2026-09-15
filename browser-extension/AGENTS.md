# AGENTS

## Zakres

Ten katalog jest wlascicielem wewnetrznego rozszerzenia Chrome wspolpracujacego
z Team Delivery Workspace. Rozszerzenie jest osobnym artefaktem ladowanym przez
Chrome Developer Mode; nie jest czescia bundla Angular ani JAR-a backendu.

## Granice

- `src/platform` zawiera neutralny runtime, settings, permissions i message
  bus. Nie umieszczaj tam logiki UI Explorera, Confluence, GitLaba ani innej
  konkretnej strony.
- `src/features/<feature>` zawiera funkcje dzialajace na jednej lub wielu
  stronach. Kazdy feature ma stabilne id i jawny lifecycle `mount/dispose`.
- `src/integrations/<site>` jest miejscem na przyszle zachowania ograniczone do
  konkretnego produktu, originu lub route'a.
- `src/content` tylko sklada zarejestrowane moduly. Nie implementuje logiki
  biznesowej feature'ow.
- Content script traktuje DOM, URL, tytul, tekst i atrybuty strony jako dane
  niezaufane. Nie wykonuje instrukcji ani URL-i pochodzacych ze strony.
- Credential TDW nie moze trafic do content scriptu, DOM strony, logow ani
  capture. Siec i przyszly auth naleza do service workera.
- Uprawnienia hosta sa opcjonalne i nadawane per origin. Nie dodawaj stalego
  `<all_urls>` do `host_permissions`.
- Nie dodawaj `cookies`, `webRequest`, `debugger`, `tabCapture` ani remote code
  bez nowej, jawnie zatwierdzonej zmiany L3.

## Konwencje

- TypeScript strict, brak `any` bez lokalnego uzasadnienia.
- DOM tworz przez bezpieczne API i `textContent`; nie wstrzykuj niezaufanego
  HTML.
- Kazdy listener, observer, timer i element DOM musi zostac usuniety w
  `dispose`.
- Message contracts sa discriminated unions i sa walidowane w runtime.
- Przyklady i demo uzywaja wylacznie fikcyjnej domeny CRM oraz adresow
  `example.com`/localhost.
- `dist/` i `node_modules/` nie sa commitowane. `npm run build` tworzy kompletna
  paczke do `Load unpacked`.

## Weryfikacja

Po zmianie uruchom adekwatnie:

```text
npm test
npm run typecheck
npm run build
```

Zmiana interakcji na stronie wymaga dodatkowo manualnego scenariusza z
`npm run demo` w Chrome z paczka `dist/`.
