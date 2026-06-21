# App Frontend AGENTS

## Ownership

`frontend/src/app` zawiera Angular UI dla `Team Delivery Workspace`.

Najwazniejsze granice:

- `app.routes.ts` trzyma route metadata: `section`, `title` i opcjonalne
  `capabilityInfo`.
- `components/app-shell` jest wlascicielem sidebaru, kontekstowego topbaru,
  breadcrumbow i capability info tooltipa.
- `core` trzyma male reusable serwisy, modele i utils frontendu, w tym
  wspolne kontrakty HTTP zasilajace przekrojowe elementy UI.
- `features` trzyma dedicated feature screens i workbench screens.
- `components` trzyma wspolne elementy UI, szczegolnie te, ktore maja byc
  identyczne miedzy feature'ami: przebieg analizy, tok pracy AI, follow-up
  chat, evidence/result panels, import/export controls i podobne wzorce pracy.
- `operational-context` trzyma ekran katalogu Operational Context i jego
  komponenty pomocnicze.

## Route metadata

Kazdy nowy route w shellu powinien dostarczyc:

- `section` zgodne z jedna z grup nawigacji,
- `title` widoczny w topbarze,
- `capabilityInfo`, jezeli to ekran Workbench z opisem reusable capability.

Nie dubluj tytulu widoku lokalnym headerem, jezeli topbar juz pokazuje ten
kontekst. Ekrany Workbench nie powinny renderowac `.workbench-header`.

## App shell

Sidebar jest glowna nawigacja. Topbar jest tylko kontekstowy.

Sidebar ma tryb rozwiniety i zwiniety rail ikonowy. Przy zmianach:

- nie dodawaj pustych placeholderow tylko po to, zeby ukryc skok layoutu,
- utrzymuj klikalnosc nawigacji w trybie rail,
- teksty w trybie rail powinny byc dostepne przez `aria-label`/`title`,
- nie zmieniaj pionowej pozycji ikon miedzy finalnym expanded/collapsed
  stanem bez swiadomej decyzji UX.

## Kontrakty backendowe

Frontend nie jest source of truth dla:

- katalogu modeli AI,
- dostepnych `reasoningEffort`,
- GitHub/Copilot auth,
- runtime tytulu UI.

Te dane pobieraj z backendu przez shared/operator API:

- `GET /analysis/ai/options`,
- `GET /api/auth/github/status`,
- `GET /api/ui/config`.

UI nie powinno zalezec od typow Copilot SDK ani od backendowych klas Javy.
Trzymaj kontrakty w TypeScript modelach na granicy HTTP.

Jezeli dwa feature'y potrzebuja tego samego rodzaju danych do UI, np. kroku
analizy, zdarzenia AI, wiadomosci follow-up, evidence section, usage albo
feedbacku tooli, preferuj jeden wspolny model w `core/models` i jeden wspolny
komponent w `components`. Lokalne modele/komponenty feature'a sa uzasadnione
tylko dla danych i prezentacji, ktore faktycznie sa merytorycznie specyficzne
dla danego problemu.

## Testy

Zmiany w shellu, route metadata albo nav wymagaja aktualizacji `app.spec.ts`.
Zmiany w standalone componentach powinny miec test komponentu albo test
integracyjny widoku, jezeli dotykaja zachowania operatora.
