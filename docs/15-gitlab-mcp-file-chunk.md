# Krok 15: GitLab MCP File Chunk

W tym kroku rozszerzamy GitLab MCP tools o czytanie fragmentu pliku po liniach.

Uwaga:
ponizszy krok opisuje pierwsza wersje narzedzia. W aktualnym stanie projektu
`gitlab_read_repository_file_chunk` pracuje juz na `group`, `projectName`,
`branch` i `filePath`. Aktualny stan jest opisany w kroku 18.
Historycznie krok byl testowany na fake repo, ale dzisiaj runtime korzysta z
REST, a testy uzywaja `TestGitLabRepositoryPort`.

To jest bardzo wazne, bo w pracy agentowej pelny plik nie zawsze jest najlepszym
wyborem:

- czasem wystarczy kilka linii wokol metody lub stack frame,
- krotszy fragment zmniejsza koszt promptu,
- AI moze iteracyjnie dociagac tylko to, czego naprawde potrzebuje.

## Po co robimy ten krok

Po kroku 14 AI umie juz:

- wyszukac kandydackie pliki,
- przeczytac pelny plik lub jego poczatek po znakach.

To nadal jest za malo precyzyjne. W praktyce AI czesto chce:

- linie 40-90,
- fragment metody wskazanej przez trace,
- okolice miejsca, gdzie pojawil sie timeout lub lock.

Dlatego dodajemy tool oparty o zakres linii.

## Co dodalismy

- `GitLabRepositoryFileChunk`
- rozszerzenie `GitLabRepositoryPort` o `readFileChunk(...)`
- `gitlab_read_repository_file_chunk`
- `GitLabReadRepositoryFileChunkToolResponse`

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRepositoryPort.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRepositoryFileChunk.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRestRepositoryAdapter.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabMcpTools.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabReadRepositoryFileChunkToolResponse.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/TestGitLabRepositoryPort.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/SyntheticAdaptersTest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabMcpToolsTest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/adapter/gitlabmcp/GitLabMcpToolsContextTest.java`

## Jak dziala nowy tool

`gitlab_read_repository_file_chunk` przyjmuje:

- `projectName`
- `filePath`
- `startLine`
- `endLine`
- opcjonalne `maxCharacters`

Line numbers sa:

- `1-based`
- `inclusive`

Tool zwraca:

- nazwe projektu,
- sciezke pliku,
- zakres zadany przez klienta,
- faktycznie zwrocony zakres,
- liczbe wszystkich linii w pliku,
- tekst fragmentu,
- informacje, czy wynik zostal obciety przez limit znakow.

## Dlaczego zwracamy requested i returned range

To jest przydatne, bo AI moze poprosic o linie `80-140`, ale plik moze miec
tylko `97` linii.

Wtedy model widzi:

- co chcial pobrac,
- co faktycznie dostal,
- ile linii ma caly plik.

To ulatwia dalsze dogrywanie kolejnych chunkow bez zgadywania.

## Jak testowac

```powershell
mvn test
```

Najwazniejsze testy:

- `SyntheticAdaptersTest`
- `GitLabMcpToolsTest`
- `GitLabMcpToolsContextTest`

## Jak debugowac

Ustaw breakpointy w:

- `GitLabMcpTools.readRepositoryFileChunk(...)`
- `GitLabRestRepositoryAdapter.readFileChunk(...)`

W logach zobaczysz tez teraz:

- `requestedStartLine`
- `requestedEndLine`
- `returnedStartLine`
- `returnedEndLine`
- `totalLines`
- `truncated`

## Co warto zrozumiec po tym kroku

1. Dlaczego czytanie po liniach jest lepsze niz czytanie calego pliku?
2. Po co AI potrzebuje `totalLines` i faktycznie zwroconego zakresu?
3. Dlaczego nadal trzymamy `maxCharacters`, mimo ze czytamy tylko fragment?

## Co dalej

Nastepny dobry krok to:

- dodac czytanie kilku chunkow w jednej operacji,
- albo podlaczyc `Copilot SDK`, zeby mogl z tych GitLab tooli korzystac
  iteracyjnie.
