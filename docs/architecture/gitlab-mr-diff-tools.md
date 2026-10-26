# GitLab MR diff i rewizje

## Ownership

`GitLabMergeRequestDiffPort` jest neutralna capability integracji GitLab.
Adapter REST reuse'uje `GitLabRestClientFactory`, konfiguracje polaczenia,
encoding sciezek i model odpowiedzi diffu istniejacego adaptera repozytorium.
Nie importuje feature'ow, tools ani Copilota.

`readMergeRequestDiff(group, projectName, iid)` wybiera najnowsza wersje z
`merge_requests/:iid/versions`, a potem pobiera konkretne `versions/:id`.
Patch i SHA pochodza z jednej odpowiedzi tej wersji; nie sa laczone z
pozniej odczytanym headem brancha. `baseSha` jest merge base, `headSha` headem
zrodla, a `startSha` tipem targetu dla tej wersji.

Snapshot jest niemutowalny i zawiera old/new paths, statusy new/rename/delete,
patch, collapsed/too-large oraz limitations. Liczba plikow podlega
`analysis.gitlab.max-merge-request-changed-files`. Stan inny niz `collected`,
niepelne SHA, rozjazd `real_size` i limit plikow sa jawne. HTTP 403/404 daje
limit widocznosci, bez odczytu z innej rewizji.

Semantyka API: [GitLab Merge Requests API, diff versions](https://docs.gitlab.com/api/merge_requests/#retrieve-a-merge-request-diff-version).

## Tools

`GitLabDiffMcpTools` ma osobny `GitLabDiffMcpToolConfiguration` rejestrujacy
`ToolCallbackProvider`. Tool `gitlab_read_merge_request_diff` czyta wylacznie
snapshot przygotowany przez aplikacje w hidden `GitLabDiffToolScope`.
Nie wybiera grupy ani rewizji na podstawie argumentow modelu i nie odswieza
snapshotu podczas kolejnych odczytow.

Wejscie: `projectName`, `mergeRequestIid`, opcjonalne `filePaths`,
`fileOffset`, `patchOffset`, `maxCharacters` oraz operator-facing `reason`.
Domyslny limit patcha to 20000 znakow, maksymalny 60000 i 20 plikow na
odpowiedz. Kontynuacja zwraca offset pliku i znaku wewnatrz patcha; dotyczy
tej samej wersji i tych samych filtrow sciezek. Brak patcha nie potwierdza
braku zmian: moze oznaczac binary, metadata-only, collapsed albo too-large.

Odczyty oldPath@baseSha i newPath@headSha reuse'uja dotychczasowe file-read
tools. Feature wybiera allowliste oraz egzekwuje scope dla kazdego rodzaju
odczytu/research. `GitLabDiffToolScope` waliduje tozsamosc MR i dozwolone
projekty/SHA; nie zmienia dotychczasowego `GitLabRepositoryToolScope`
Operational Context Assistance ani branch-scoped UX Inspectora.

Wyniki sa przechwytywane przez `GitLabToolEvidenceMapper` jako neutralne
`tool-fetched-diff` z argumentami, powodem, call id i odpowiedzia zawierajaca
provenance wersji i SHA. Feature pozostaje wlascicielem interpretacji patcha.
