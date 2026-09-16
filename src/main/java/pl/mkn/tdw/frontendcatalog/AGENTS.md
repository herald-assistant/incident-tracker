# AGENTS

## Zakres

Ten pakiet jest neutralna capability aplikacyjna katalogu frontendow. Laczy
Operational Context z readonly discovery GitLab frontendu i udostepnia
zarejestrowane systemy, widoki oraz przypiete rewizje feature'om analitycznym.

## Zasady

- Pakiet nie zalezy od `features.*`, `api.*`, `agenttools.*` ani `aiplatform.*`.
- Kontrakty nie niosa semantyki konkretnego feature'a.
- Repository scope pochodzi wylacznie z Operational Context.
- Kazdy widok jest zwracany razem z immutable commit id.
- Publiczne API feature'ow mapuje te neutralne modele na wlasne DTO.

