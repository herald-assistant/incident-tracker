# AGENTS

## Zakres

Ten pakiet jest neutralna capability integracji z Jira. Obejmuje readonly
pobieranie materialu issue, typowany paginowany search mapowany na JQL oraz
odczyt historii statusow i ich kategorii. Material issue moze korzystac z
profilu detailed albo ograniczonego profilu assessment.

## Zasady

- Nie importuj `analysis.*`, `agenttools.*`, `features.*`, `api.*` ani
  `aiplatform.*`.
- Bezposrednio w `jira/` trzymaj tylko `JiraIssuePort`, `JiraIssueSearchPort`
  i `JiraIssueStatusHistoryPort`. Publiczne modele trzymaj w `contract`, oba
  adaptery w `adapter.rest`, a properties i fabryke klienta w `config`.
- Jira-specific parsing, limity i nietypowe zachowania HTTP izoluj lokalnie.
- Publiczny typed search request nie moze przyjmowac raw JQL od feature'a ani UI.
- Status category rozpoznawaj z kontraktu Jira, nie z nazwy statusu.
- Hierarchie odczytuj z `parent` i `subtasks`, bez fallbacku po nazwie typu.
  Flaga `issuetype.subtask` sygnalizuje luke przy braku parent key.
- Profil assessment obejmuje bezposredni parent i dzieci, bez komentarzy
  takze w odczytach powiazanych. Nie rozwijaj rodzenstwa ani kolejnych
  poziomow. Profil detailed zachowuje rozszerzony material powiazany.
- Nieudany odczyt powiazanego zadania zachowuje znany klucz i relacje oraz
  jawny limitation. Limit dzieci obejmuje tez nieudane proby odczytu.
- Kontrakty tooli, promptow i evidence mapping zostaja w warstwach wyzszych.

## Weryfikacja

- Dla adaptera REST dodaj test z `MockRestServiceServer`.
- Po zmianie zaleznosci uruchom `PackageDependencyGuardTest` i
  `IntegrationPackageBoundaryTest`.
