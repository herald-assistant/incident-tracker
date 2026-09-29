# AGENTS

## Zakres

Ten pakiet jest neutralna capability aplikacyjna katalogu frontendow. Laczy
Operational Context z readonly discovery GitLab frontendu i udostepnia
zarejestrowane systemy, widoki oraz czas zebrania katalogu feature'om analitycznym.

## Zasady

- Pakiet nie zalezy od `features.*`, `api.*`, `agenttools.*` ani `aiplatform.*`.
- Kontrakty nie niosa semantyki konkretnego feature'a.
- Repository scope pochodzi wylacznie z Operational Context.
- Katalog widokow zwraca wybrany branch i `dataCollectedAt`; cache hit zachowuje
  timestamp, a refresh tworzy nowy. Czas katalogu nie oznacza czasu kazdego
  pozniejszego odczytu kodu.
- Katalog widokow jest cache'owany jako neutralny kontrakt per system, ref,
  repository scope i limity discovery. Feature moze jawnie wymusic refresh,
  ale nie utrzymuje wlasnego formatu tego cache'a.
- Publiczne API feature'ow mapuje te neutralne modele na wlasne DTO.
