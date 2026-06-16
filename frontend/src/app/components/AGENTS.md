# Frontend Components AGENTS

## Cel

Ten katalog zawiera wspolne komponenty UI, w tym `app-shell`, `app-brand` i
komponenty prezentacji analizy. Komponent wspolny powinien byc reusable w
ramach `Team Delivery Workspace`, a nie zakodowany pod pojedynczy route, jesli
nie jest to jawnie komponent analizy.

## App shell

`app-shell` jest jedynym wlascicielem:

- glownego sidebaru,
- kontekstowego topbaru,
- breadcrumbow,
- capability info tooltipa,
- zwijanego raila nawigacji.

Nie dodawaj drugiego topbaru w feature screen. Jezeli widok potrzebuje
statycznego opisu capability, dodaj go do route data jako `capabilityInfo`.

## Dostepnosc i gestosc UI

- Icon-only controls musza miec `aria-label` albo `title`.
- Tooltipy maja wyjasniac skrocone dane, ale nie moga zaslaniac glownego
  workflow.
- Tekst w kontrolkach nie powinien byc ucinany bez sensownego tooltipa albo
  alternatywy.
- Shared components powinny uzywac tokenow CSS, a nie lokalnych jednorazowych
  kolorow.

## Czego unikac

- Nie tworz komponentow marketingowych hero dla narzedzi roboczych.
- Nie buduj kart w kartach.
- Nie przenos logiki backendowego scope'u albo Copilot SDK do komponentow UI.
- Nie duplikuj brandu albo navigation state poza `app-shell`.
