# AGENTS

## Zakres

Ten katalog odpowiada za GitLab MCP tools wystawiane do Copilota.

Obejmuje:

- definicje tooli,
- DTO odpowiedzi tooli,
- konfiguracje rejestracji `ToolCallbackProvider`.

Nie obejmuje:

- logiki deterministic evidence provider,
- bezposredniej obslugi REST API GitLaba poza reuse portow z
  `analysis.adapter.gitlab`.

## Zasady modyfikacji

- Tool-e maja korzystac z `GitLabRepositoryPort`, a nie z klienta REST
  bezposrednio.
- Kontrakty tooli maja pozostac jawne: `group`, `projectName`, `branch`,
  `filePath`.
- Nie dodawaj tu heurystyk incidentowych ani mapowania logs -> repo.
- Gdy dodajesz tool wysokiego poziomu, deleguj go do use case albo orchestratora,
  zamiast sklejania flow bezposrednio w klasie `@Tool`.
- Logowanie tooli powinno pozostac operacyjne: czytelne wejscie, skrot wyniku,
  bez zbednego dumpowania duzych payloadow.

## Testy

- Sprawdzaj zarowno testy klas tooli, jak i rejestracje w kontekscie Spring AI.
