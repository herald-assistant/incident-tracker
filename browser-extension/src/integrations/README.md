# Site integrations

Ten katalog jest punktem rozszerzen dla funkcji ograniczonych do konkretnego
produktu, originu albo route'a. Przyklady przyszlych modulow:

- menu kontekstowe TDW nad kontrolowana strona Confluence,
- dodatkowe akcje lub prezentacja danych na stronie GitLaba,
- integracja z wewnetrznym portalem organizacji.

Integracja implementuje `ContentFeatureModule`, deklaruje
`availability: 'site-specific'`, rozpoznaje strone w `matches(page)` i zwraca
uchwyt `dispose()` z `mount(context)`. Nastepnie jest dopisywana do
`siteIntegrationRegistry`.

Modul musi:

- dzialac tylko na jawnie nadanym originie,
- izolowac swoje style i elementy DOM,
- nie kopiowac credentiali ani wartosci formularzy,
- usuwac wszystkie listenery, observery i elementy w `dispose`,
- komunikowac sie z service workerem przez typowany message contract,
- miec testy z calkowicie fikcyjnym scenariuszem CRM albo neutralnym
  `example.com`.

Nie dodawaj sprawdzen `hostname` ani selektorow produktu do `src/platform`.
