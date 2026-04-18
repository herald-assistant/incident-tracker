# Krok 14: GitLab MCP Tools

W tym kroku dodajemy pierwszy rzeczywisty zestaw tooli MCP dla GitLaba.

Uwaga:
ponizszy krok opisuje pierwsza wersje narzedzi. W aktualnym stanie projektu
kontrakt tooli zostal rozszerzony o jawne `group` i `branch`.
Aktualny, runtime-owy model integracji jest opisany w kroku 18.
Historycznie ten krok startowal od fake repo, ale dzisiaj runtime korzysta juz z
`GitLabRestRepositoryAdapter`, a testy utrzymuja tylko helper
`TestGitLabRepositoryPort`.

## Po co robimy ten krok

W aktualnym flow mamy juz:

- evidence z Elastic i Dynatrace,
- deterministyczny GitLab provider do poczatkowego zawazenia plikow,
- glowny flow analizy oparty o AI.

Brakuje jeszcze drugiej fazy pracy z repo:

- AI powinno moc samo szukac kolejnych plikow,
- AI powinno moc czytac wskazane pliki,
- ale musi to robic przez kontrolowane tool-e, a nie przez niejawny dostep do GitLaba.

## Co dodalismy

- `GitLabMcpTools`
- `gitlab_search_repository_candidates`
- `gitlab_read_repository_file`
- `GitLabRepositoryFileContent`

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabMcpTools.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabSearchRepositoryCandidatesToolResponse.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabReadRepositoryFileToolResponse.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRepositoryPort.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRepositoryFileContent.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRestRepositoryAdapter.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/TestGitLabRepositoryPort.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabMcpToolsTest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabMcpToolsContextTest.java`

## Jakie tool-e wystawiamy

### 1. `gitlab_search_repository_candidates`

Ten tool przyjmuje clue takie jak:

- `serviceNames`
- `operationNames`
- `keywords`

i zwraca kandydackie pliki w repo wraz z:

- `projectName`
- `filePath`
- `matchReason`
- `matchScore`

To jest narzedzie do zawazenia przestrzeni przeszukiwania.

### 2. `gitlab_read_repository_file`

Ten tool przyjmuje:

- `projectName`
- `filePath`
- opcjonalne `maxCharacters`

i zwraca:

- nazwe projektu,
- sciezke,
- tekst pliku,
- informacje, czy odpowiedz zostala obcieta.

To jest pierwszy prosty mechanizm kontrolowanego dogrywania kodu przez AI.

## Jak to sie laczy z obecna architektura

Mamy teraz dwa rozne mechanizmy pracy z GitLabem:

1. `GitLabDeterministicEvidenceProvider`
   Ten element dziala w glownym flow i na podstawie logs/traces wybiera
   pierwszych kandydatow.

2. `GitLabMcpTools`
   Te tool-e sa przeznaczone do dalszego, iteracyjnego dociagania plikow przez
   AI wtedy, gdy analiza poczatkowa nie wystarczy.

To rozdzielenie jest bardzo wazne:

- provider evidence robi krok bazowy,
- MCP tools robia krok eksploracyjny.

## Konfiguracja

W `application.properties` wlaczylismy:

```properties
spring.ai.mcp.server.enabled=true
spring.ai.mcp.server.annotation-scanner.enabled=false
spring.ai.mcp.server.name=incident-tracker-gitlab
spring.ai.mcp.server.version=0.0.1-SNAPSHOT
```

Tool-e rejestrujemy teraz jawnie przez:

- `MethodToolCallbackProvider`
- konfiguracje `GitLabMcpToolConfiguration`

To jest bardzo dobry pierwszy krok edukacyjny, bo jasno widac:

1. gdzie sa metody `@Tool`,
2. jak zamieniaja sie na `ToolCallbackProvider`,
3. jak trafiaja do MCP servera.

## Jak testowac

```powershell
mvn test
```

Najwazniejsze testy:

- `GitLabMcpToolsTest`
- `GitLabMcpToolsContextTest`

## Jak debugowac

Ustaw breakpointy w:

- `GitLabMcpTools.searchRepositoryCandidates(...)`
- `GitLabMcpTools.readRepositoryFile(...)`
- `GitLabRestRepositoryAdapter.searchCandidateFiles(...)`
- `GitLabRestRepositoryAdapter.readFile(...)`

Od tego kroku warto tez patrzec w logi aplikacji.
Kazde wywolanie toola loguje teraz:

- nazwe toola,
- najwazniejsze argumenty wejsciowe,
- skrot wyniku, np. liczbe kandydatow albo informacje o obcieciu odpowiedzi.

## Co warto zrozumiec po tym kroku

1. Dlaczego MCP tool jest lepszy niz ukryte pobieranie plikow w samym providerze AI?
2. Dlaczego rozdzielamy zawazanie kandydatow od czytania plikow?
3. Jakie limity trzeba bedzie pozniej dodac dla bezpiecznego dogrywania plikow?

## Co dalej

Nastepny dobry krok to:

- dodac narzedzie do czytania fragmentu pliku po liniach,
- albo podlaczyc `Copilot SDK` tak, aby mogl korzystac z tych tooli w praktyce.
