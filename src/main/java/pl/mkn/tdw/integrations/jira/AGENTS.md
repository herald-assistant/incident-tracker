# AGENTS

## Zakres

Ten pakiet jest neutralna capability integracji z Jira. Obejmuje readonly
pobieranie materialu issue, typowany paginowany search mapowany na JQL oraz
odczyt historii statusow i ich kategorii. Material issue moze korzystac z
profilu detailed albo ograniczonego profilu assessment.

## Zasady

- Nie importuj `analysis.*`, `agenttools.*`, `features.*`, `api.*` ani
  `aiplatform.*`.
- Trzymaj tutaj tylko properties, porty, modele i adapter REST.
- Jira-specific parsing, limity i nietypowe zachowania HTTP izoluj lokalnie.
- Publiczny typed search request nie moze przyjmowac raw JQL od feature'a ani UI.
- Status category rozpoznawaj z kontraktu Jira, nie z nazwy statusu.
- Kontrakty tooli, promptow i evidence mapping zostaja w warstwach wyzszych.

## Weryfikacja

- Dla adaptera REST dodaj test z `MockRestServiceServer`.
- Po zmianie zaleznosci uruchom `PackageDependencyGuardTest`.
