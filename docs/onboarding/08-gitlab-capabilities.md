# Krok 8: Trzy Capability GitLaba

## Cel

Zrozumiec najlatwiejsze do pomylenia miejsce w projekcie: GitLab wystepuje tu
jako trzy rozne capability.

## Po tym kroku rozumiesz

- co nalezy do generycznego adaptera GitLaba,
- co nalezy do deterministic evidence providera,
- co nalezy do tools dla AI,
- dlaczego `group` i `branch` pochodza z roznych zrodel.

## Capability 1: generyczny adapter

Pakiet: `integrations.gitlab`

Odpowiada za:

- konfiguracje,
- porty i adapter REST,
- repository search,
- source resolve.

Shared/operator endpointy do recznego testowania mieszkaja w `api.gitlab` i
`api.gitlab.source`, ale deleguja do tych samych services integracji.

Nie powinien znac logiki `logs -> repo`.

## Capability 2: deterministic evidence provider

Pakiet: `features.incidentanalysis.evidence.provider.gitlabdeterministic`

Odpowiada za:

- mapowanie logow i deployment context na project hints,
- znajdowanie code references,
- pobieranie fragmentow kodu jako evidence do promptu.

Tutaj wolno miec heurystyki incidentowe, bo to jest krok pipeline.

## Capability 3: MCP tools

Pakiet: `agenttools.gitlab.mcp`

Odpowiada za:

- listowanie repozytoriow GitLaba dostepnych w sesji na podstawie operational
  context,
- wyszukiwanie kandydatow repozytoriow,
- znajdowanie referencji i importow dla ugruntowanej klasy,
- znajdowanie szerszego flow context,
- czytanie outline pliku,
- czytanie pliku,
- czytanie chunku pliku,
- czytanie malych batchy chunkow.

Tool ma byc maly, reuse'owac adapter i brac `group`, `branch` oraz
`correlationId` z hidden `ToolContext`, a nie od modelu.

Kazdy GitLab MCP tool przyjmuje opcjonalne `reason`: krotki powod po polsku,
po co model prosi o dany odczyt albo wyszukiwanie. `gitlab_find_flow_context`
przyjmuje focused `keywords` z evidence/logow, bez osobnych parametrow klasy,
metody albo pliku.

`gitlab_list_available_repositories` nie szuka po kodzie i nie odpytuje GitLaba
o pliki. Zwraca katalog repozytoriow z operational context dla biezacej grupy:
`projectName` do dalszych GitLab tools, pelny `gitLabPath`, summary oraz
sygnaly dopasowania takie jak aliases, systems, boundedContexts, package
prefixes, endpoint prefixes i module paths.

Ten tool zwraca tez `codeSearchScopes` z `repo-map.yml`. Scope pokazuje, ktore
repozytoria trzeba przeszukiwac razem dla jednego systemu, jakie maja role i
priorytety oraz jakie `projectName` przekazac wspolnie do
search/flow/class-reference tools.

Operational context moze wskazac kilka repozytoriow dla jednego systemu. Dla
GitLab tools oznacza to jeden code search scope: repo glowne, biblioteki,
shared modules i wygenerowane klienty powinny byc przeszukiwane razem, jesli
klasa albo collaborator nie znajduje sie w main repo.

User-facing capture z GitLaba pozostaje maksymalnie prosty. Do UI trafia
`reason` jako naglowek wpisu. File/chunk/chunks pokazuja nazwe/sciezke pliku,
tresc kodu i opcjonalny start line. Search, outline, flow context i class
references pokazuja szczegoly lookupu: kandydatow, grupy, outline i
rekomendowane dalsze odczyty.

## Dwie stale decyzje

- `gitLabGroup` pochodzi z konfiguracji aplikacji,
- `gitLabBranch` jest rozwiazywany z evidence, glownie z deployment context.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/integrations/gitlab`
- `src/main/java/pl/mkn/incidenttracker/api/gitlab`
- `src/main/java/pl/mkn/incidenttracker/features/incidentanalysis/evidence/provider/gitlabdeterministic`
- `src/main/java/pl/mkn/incidenttracker/agenttools/gitlab/mcp`

## Checkpoint

- Gdzie dodasz nowa heurystyke mapowania `container -> project`?
- Gdzie dodasz nowy endpoint do testowania source resolve?
- Gdzie dodasz nowy tool czytajacy tylko metadata pliku?
- Gdzie dodasz nowy tool znajdujacy importy lub uzycia klasy w repozytorium?
