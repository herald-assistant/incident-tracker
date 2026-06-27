# AGENTS

## Zakres

Ten pakiet jest neutralna capability integracji z GitLabem. Obejmuje adapter
REST, port repozytorium, wyszukiwanie kodu, pobieranie plikow, source resolve,
wspolne repository tree/cache oraz inventory endpointow repozytorium.

Kod w `integrations.gitlab` ma byc reusable przez evidence providers,
tools/MCP, shared/operator API i przyszle feature'y analityczne. Nie moze
zawierac semantyki incident analysis, Copilota ani MCP runtime.

## Granice

- Nie importuj tutaj `analysis.*`, `agenttools.*`, `features.*`, `api.*` ani
  `aiplatform.*`.
- Kontrakty tooli, opisy dla AI, evidence mapping i polityki uzycia zostaja w
  warstwach wyzszych. Ten pakiet zwraca neutralne modele integracyjne,
  ograniczenia widocznosci i techniczne sygnaly jak confidence albo suggested
  next reads.
- `GitLabRepositoryPort` jest neutralna fasada dla warstw wyzszych. Jesli nowa
  capability GitLaba ma byc uzywana przez tool albo feature, najpierw rozwaz
  dodanie metody portu i implementacji adaptera zamiast laczenia sie z GitLabem
  bezposrednio poza tym pakietem.
- `source/` zawiera kontrakty i serwis source resolve. Nie duplikuj tam
  mechaniki wspolnej z reszta GitLab capability.

## Repository tree i cache

- Dostep do GitLab `repository/tree` ma isc przez `GitLabRepositoryTreeService`.
  Nie tworz osobnej paginacji, osobnego URL buildera ani osobnego cache dla tego
  endpointu.
- Cache tree trzymaj w `GitLabRepositoryTreeSession`. Jesli konkretna capability
  ma wlasna sesje, powinna opakowywac albo delegowac do wspolnej sesji, tak jak
  `GitLabSourceResolveSession`.
- Klucz cache musi uwzgledniac GitLab base URL, projekt, branch/ref oraz
  `pathPrefix`, zeby nie mieszac wynikow miedzy repozytoriami i scope'ami.
- Zachowanie statusow HTTP specyficzne dla tree trzymaj lokalnie przez
  `GitLabRepositoryTreeException`, a mapowanie na kontrakt danej capability
  wykonuj w serwisie wywolujacym.

## Endpoint inventory

- Endpoint discovery ma pozostac neutralne domenowo: zwracaj endpointy,
  kontrolery, metody handlerow, request/response types, lokalizacje w plikach,
  confidence, limitations i suggested next reads. Nie dodawaj incidentowych
  wnioskow ani instrukcji dla AI.
- Parser endpointow jest best-effort dla Spring MVC/REST controllerow. Jesli
  nie da sie jednoznacznie rozstrzygnac sciezki albo typu, zwroc wyrazenie,
  confidence i limitation zamiast zgadywac.
- Preferuj ograniczenia skanowania i jawne limity nad pelnym pobieraniem
  repozytorium. Wynik powinien mowic, ile plikow bylo kandydatami, ile
  przeskanowano i czy limit zostal osiagniety.

## HTTP i modele

- Integracja HTTP korzysta z `RestClient` przez `GitLabRestClientFactory`.
- Izoluj nietypowe zachowania GitLaba lokalnie dla tej integracji; nie dodawaj
  globalnych zmian SSL ani globalnych klientow HTTP.
- Modele request/result trzymaj jako male, niemutowalne `record`, gdy to
  wystarcza.
- Dla nowej operacji GitLab API dodaj test adaptera z `MockRestServiceServer`,
  obejmujacy URL, encoding, paginacje albo statusy graniczne, jesli dana
  operacja ich uzywa.

## Weryfikacja

- Uruchom `PackageDependencyGuardTest` po zmianie zaleznosci w tym pakiecie.
- Dla zmian w source resolve uruchom `GitLabSourceResolveServiceTest`.
- Dla zmian w adapterze REST uruchom `GitLabRestRepositoryAdapterTest`.
- Dla zmian uzywanych przez tools uruchom odpowiednie testy w
  `agenttools.gitlab.mcp`, ale nie przenos logiki tooli do tego pakietu.
