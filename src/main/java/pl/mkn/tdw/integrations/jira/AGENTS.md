# AGENTS

## Zakres

Ten pakiet jest neutralna capability integracji z Jira. Obejmuje readonly
pobieranie materialu issue: opis, status, typ, komentarze, linki i
skonfigurowane pola kryteriow akceptacji.

## Zasady

- Nie importuj `analysis.*`, `agenttools.*`, `features.*`, `api.*` ani
  `aiplatform.*`.
- Trzymaj tutaj tylko properties, porty, modele i adapter REST.
- Jira-specific parsing, limity i nietypowe zachowania HTTP izoluj lokalnie.
- Kontrakty tooli, promptow i evidence mapping zostaja w warstwach wyzszych.

## Weryfikacja

- Dla adaptera REST dodaj test z `MockRestServiceServer`.
- Po zmianie zaleznosci uruchom `PackageDependencyGuardTest`.
