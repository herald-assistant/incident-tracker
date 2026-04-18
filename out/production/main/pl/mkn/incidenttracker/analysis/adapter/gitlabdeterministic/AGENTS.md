# AGENTS

## Zakres

Ten katalog odpowiada za deterministic mapowanie evidence z Elastica do kontekstu
GitLaba.

Obejmuje:

- `GitLabDeterministicEvidenceProvider`,
- heurystyki deploymentu z `container.image.name` i namespace,
- ekstrakcje odniesien do kodu z logow i stacktrace,
- reuse generycznego adaptera GitLaba i source resolvera.

Nie obejmuje:

- klas MCP `@Tool`,
- szczegolow REST API GitLaba poza uzyciem portow z `analysis.adapter.gitlab`.

## Zasady modyfikacji

- Traktuj ten katalog jako warstwe mapowania `elastic evidence -> gitlab context`.
- `gitLabGroup` nadal pochodzi z konfiguracji aplikacji, nie z evidence.
- `branch` i `environment` maja byc wyprowadzane z logs, nie z requestu.
- Gdy potrzebujesz pobrac kod, korzystaj z `GitLabRepositoryPort` albo
  `GitLabSourceResolveService`, zamiast omijac adapter.
- Nie przenos odpowiedzialnosci MCP do tego katalogu.
- Zwracane evidence ma pozostac w formie generycznych `AnalysisEvidenceSection`
  i `AnalysisEvidenceItem`.

## Testy

- Dla heurystyk i selekcji kandydatow preferuj testy jednostkowe providera.
- Gdy zmieniasz mapowanie symboli albo deploymentu, pilnuj przypadkow
  `container.image.name`, namespace fallback i stacktrace filtering.
