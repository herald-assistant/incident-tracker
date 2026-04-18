# AGENTS

## Zakres

Ten katalog odpowiada za uniwersalny adapter GitLaba w projekcie.

Obejmuje:

- konfiguracje i properties,
- porty i adaptery REST,
- manualny endpoint HTTP do testowania mapowania `component -> repo` i
  kandydatow plikow,
- pomocniczy source resolver po symbolu.

Nie obejmuje:

- deterministic mapowania evidence z Elastica do GitLaba
  (`../../evidence/provider/gitlabdeterministic`),
- MCP tools dla Copilota (`../../mcp/gitlab`).

## Aktualne zalozenia architektoniczne

- `gitLabGroup` pochodzi z konfiguracji aplikacji.
- `branch` i `environment` sa rozwiazywane z wczesniej zebranego evidence,
  glownie z logow Elasticsearch i `container.image.name`.
- `AnalysisContext` niesie `correlationId` i zebrane sekcje evidence, a nie
  `branch`.
- Source resolver pozostaje generycznym capability GitLaba i moze byc
  reuse'owany przez deterministic provider z pakietu
  `analysis.evidence.provider.gitlabdeterministic`.
- AI-guided fetching jest realizowany przez tools z pakietu
  `analysis.mcp.gitlab`, nie przez kod w tym katalogu.
- Source resolver jest osobnym use case'em pomocniczym, a nie glownym flow
  `/analysis`, chociaz deterministic provider moze go reuse'owac.

## Struktura katalogu

- katalog glowny
  Properties, porty, adaptery REST i reczne endpointy testowe.
- `source/`
  Endpoint i serwis do rozwiazywania pliku po symbolu.

## Zasady modyfikacji

### Grupa i branch

- Nie dedukuj `gitLabGroup` z evidence.
- Nie przywracaj `branch` do requestu `/analysis` ani do `AnalysisContext`.
- Tool-e i AI maja dostawac `group/project/branch/filePath`, ale `branch` moze
  byc przekazywany dopiero po rozwiazaniu z logs.

### HTTP i auth

- Do wywolan GitLaba uzywaj `GitLabRestClientFactory`.
- Nie hardcoduj tokena.
- Token pochodzi z konfiguracji aplikacji.
- Ignorowanie SSL jest dozwolone tylko lokalnie dla GitLaba i tylko przez
  istniejacy mechanizm `analysis.gitlab.ignore-ssl-errors`.

### Granice odpowiedzialnosci

- Nie dodawaj tu logiki mapowania incident/evidence -> deployment context.
- Nie dodawaj tu klas `@Tool` dla Copilota ani MCP.
- Ten katalog ma dawac generyczne capability GitLaba, ktore mozna reuse'owac z
  innych pakietow.

### Source resolver

- Resolver ma pozostac prosty i przewidywalny.
- Ranking kandydatow ma byc deterministyczny.
- Drzewo repozytorium moze byc cache'owane tylko per request, nie globalnie.
- Bledy GitLaba powinny byc mapowane na czytelny JSON, nie HTML.

## Testy

- Dla REST adapterow i resolvera preferuj `MockRestServiceServer`.
- Dla kontrolerow preferuj `MockMvc`.
- Gdy zmieniasz resolver albo kontrakty generycznego adaptera, sprawdz takze
  dokumentacje i pakiety siostrzane
  `analysis.evidence.provider.gitlabdeterministic` oraz `analysis.mcp.gitlab`.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/18-real-gitlab-rest-integration.md`
