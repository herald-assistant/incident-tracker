# AGENTS

## Zakres

Ten pakiet jest neutralna, read-only capability statycznego rozpoznawania
frontendow Angular/Nx w jednym repozytorium i jednej rewizji GitLaba. Posiada
modele workspace, route/view catalog, screen source context, manifest,
diagnostics i limity.

## Granice

- Nie importuj `features.*`, `api.*`, `agenttools.*`, `aiplatform.*` ani
  kontraktow Copilota.
- Dostep do repozytorium realizuj przez `GitLabRepositoryPort`; nie duplikuj
  GitLab REST, tree pagination ani cache.
- Scope zawiera jeden group, project, ref i jawne path prefixes. Nie wykonuj
  multi-repository traversal.
- Parser jest heurystyczny i bounded. Dynamiczne definicje, spread operators,
  runtime JSON oraz nierozstrzygniete symbole zwracaj jako diagnostics i
  ograniczenia, bez zgadywania.
- `screenId` jest generowany deterministycznie z route/view identity. Nie jest
  sciezka pliku przekazywana przez operatora.
- Capability nie uruchamia kodu frontendu ani przegladarki i niczego nie
  zapisuje w badanym repozytorium.

## Testy

- Wszystkie fixtures i przyklady domenowe sa silnie zanonimizowanym CRM.
- Pokrywaj standalone i module routes, lazy loading, guardy, template variants,
  formularze, NgRx, REST, WebSocket, auth oraz jawne limity/diagnostics.

