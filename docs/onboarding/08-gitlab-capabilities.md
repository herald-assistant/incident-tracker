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

Pakiet: `analysis.adapter.gitlab`

Odpowiada za:

- konfiguracje,
- porty i adapter REST,
- repository search,
- source resolve,
- helper endpointy do recznego testowania.

Nie powinien znac logiki `logs -> repo`.

## Capability 2: deterministic evidence provider

Pakiet: `analysis.evidence.provider.gitlabdeterministic`

Odpowiada za:

- mapowanie logow i deployment context na project hints,
- znajdowanie code references,
- pobieranie fragmentow kodu jako evidence do promptu.

Tutaj wolno miec heurystyki incidentowe, bo to jest krok pipeline.

## Capability 3: MCP tools

Pakiet: `analysis.mcp.gitlab`

Odpowiada za:

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

Operational context moze wskazac kilka repozytoriow dla jednego komponentu
wdrozeniowego. Dla GitLab tools oznacza to jeden code search scope:
repo glowne, biblioteki, shared modules i wygenerowane klienty powinny byc
przeszukiwane razem, jesli klasa albo collaborator nie znajduje sie w main
repo.

User-facing capture z GitLaba pozostaje maksymalnie prosty. Do UI trafia
`reason` jako naglowek wpisu. File/chunk/chunks pokazuja nazwe/sciezke pliku,
tresc kodu i opcjonalny start line. Search, outline, flow context i class
references pokazuja szczegoly lookupu: kandydatow, grupy, outline i
rekomendowane dalsze odczyty.

## Dwie stale decyzje

- `gitLabGroup` pochodzi z konfiguracji aplikacji,
- `gitLabBranch` jest rozwiazywany z evidence, glownie z deployment context.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/gitlabdeterministic`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/gitlab`

## Checkpoint

- Gdzie dodasz nowa heurystyke mapowania `container -> project`?
- Gdzie dodasz nowy endpoint do testowania source resolve?
- Gdzie dodasz nowy tool czytajacy tylko metadata pliku?
- Gdzie dodasz nowy tool znajdujacy importy lub uzycia klasy w repozytorium?
