# Historia kodu GitLab w analizie i follow-up

## Wartość i konsumenci

Po inicjalnej analizie AI może krótko odpowiedzieć, kiedy i jak zmieniono
funkcjonalność. Historia jest dodatkową capability istniejącego chatu,
bez nowego ekranu, obowiązkowej procedury ani samodzielnego startu.
Zwykłe pytanie nie upoważnia do modyfikacji raportu.

Incident Analysis, Flow Explorer, UI Explorer, UX Inspector i Release summary
mają po jednej jawnej allowliście dla initial i follow-up. Commit history/diff
oraz report patch/chunk są zwykłymi wpisami tych list. Dostęp nadal podlega
konfiguracji capability, coverage evidence incydentu i walidacji hidden scope.
Pozostali konsumenci zachowują dotychczasowe allowlisty. Publiczne API chatu, lifecycle,
UI, persistence i export/import V1 nie zmieniają kształtu. Nie dodano migracji
ani obsługi analiz/chatów sprzed zmiany.

## Warstwy i kontrakty

`GitLabCommitHistoryPort` oraz `GitLabRestCommitHistoryAdapter` należą do
`integrations.gitlab`. Reuse obejmuje RestClient factory, read retry, verified
file reader i model `FileDiff`. `latestBefore`, MR diff i compare zachowują
odrębną semantykę. `GitLabHistoryMcpTools` ma jawny callback provider w
`GitLabHistoryMcpToolConfiguration`. Feature wybiera allowlistę i dostarcza
guidance; platforma wykonuje zwykłe callbacki. Nie ma osobnego scope historii
ani rejestru dozwolonych SHA.

| Tool | Wejście modelu | Wynik |
| --- | --- | --- |
| `gitlab_list_repository_commits` | projectName, opcjonalne ref/filePaths/since/until/cursor, reason | projectPath, historyHeadSha, commits, nextCursor, limitations |
| `gitlab_read_commit_diff` | projectName, commitId, opcjonalne filePaths/cursor, reason | commitId, sourceRef, bounded files/patches, nextCursor, limitations |

Schematy nie przyjmują group ani environment. Opcjonalny `ref` listy commitów
pozwala wskazać dowolną badaną rewizję; domyślnie pochodzi z kontekstu sesji. `reason` to krótkie
zdanie dla operatora, do 500 znaków. Daty są inkluzywnymi timestampami ISO-8601
ze strefą. Zapytanie akceptuje do 10 ścieżek.

Lista plików oznacza sumę historii. API dostaje żądanie dla każdej ścieżki,
z tą samą kotwicą SHA i datami, po maksymalnie 20 commitów. Wynik deduplikuje
SHA w bieżącej odpowiedzi i sortuje ją po committedAt. `matchedPaths` to
ścieżki zapytania znalezione w tej partii, nie komplet zmienionych plików.
SHA może powrócić w kolejnej partii innej ścieżki; nie obiecujemy globalnej
kolejności chronologicznej po połączeniu niezależnych stron.

Kursor przenosi postęp każdej ścieżki, kotwicę SHA oraz projekt i filtry.
Jest stateless i działa również w kolejnych wiadomościach, bez grantów sesji.
Błąd strony daje jawne limitation i możliwość ponowienia, zachowując udane
wyniki. Surowe błędy HTTP nie trafiają do evidence. Zmiana filtrów wymaga
nowego zapytania. Nie ma limitu liczby kursorów w turze.

Commit zawiera SHA, parentIds, committedAt, tytuł, opcjonalny webUrl oraz
`committerName` / `committerEmail`. Brakujące i puste dane commitera pozostają
nieobecne. Nie zastępujemy ich autorem ani katalogiem użytkowników. Committer
nie oznacza autora implementacji; data commita nie dowodzi wdrożenia ani awarii.

## Zakres i historyczne pliki

Historia korzysta ze zwykłego rozwiązywania grupy/repozytorium GitLaba oraz
allowlisty feature’a. Nie dodaje własnych ograniczeń projektów, ścieżek,
rewizji ani kolejności wywołań. AI może od razu czytać dowolny pełny SHA,
w tym wskazany przez użytkownika albo pochodzący z wcześniejszej rozmowy.
Nie musi najpierw odczytać listy commitów ani ponownie potwierdzać SHA.

Lista commitów przypina ref do SHA dla stabilnej paginacji konkretnego
zapytania; nie jest to granica uprawnień. Kolejne zapytanie może wskazać
inną rewizję. Odczyty plików i chunków używają `branchRef` bez rejestru grantów.
Zwykły repository scope zachowuje przygotowane przypięcia znanych refs
oraz granice repozytorium; badanie innego ref nie zmienia bazowej analizy.
Wszystkie operacje z `GitLabRepositoryToolScope` używają jednego `resolve`:
nowy branch/tag jest przypinany do SHA raz dla pary projekt/ref, także w
wybranym projekcie i projektach porównania. Kolejne odczyty używają tego
samego SHA mimo zmiany HEAD. Pełny SHA jest czytany bezpośrednio. Restore
zachowuje dodatkowe przypięcia, nie pozwalając nadpisać przygotowanych refs.
Nie ma osobnej ścieżki `resolveResearch`.
Walidacja poprawności ścieżek, parametrów, odpowiedzi GitLaba i limity
pojedynczej odpowiedzi pozostają techniczną odpowiedzialnością narzędzi.

## Diff i luki

Diff zwraca do 20 plików strony API i 20000 znaków patcha na wywołanie.
Kursor zachowuje stronę, plik i offset. Old/new paths i flagi new/rename/delete/
collapsed/tooLarge pozostają widoczne. Filtry ścieżek dopasowują starą lub
nową ścieżkę; rename nie wymaga wcześniejszego odkrycia obu plików.

Brak patcha może oznaczać binary, metadata-only albo limit serwera. Pusty
wynik filtra i koniec paginacji nie dowodzą braku zmiany ani kompletności.
Merge może mieć wielu rodziców; sam diff nie opisuje wszystkich. Historia
rename zależy od serwera i nie ma gwarancji kompletności.

Adapter nie używa `all=true` ani first-parent. Respektuje `X-Next-Page`;
pełna strona bez nagłówka wymaga dodatkowego odczytu. Nie zakłada total-count.
Źródło: [GitLab Commits API](https://docs.gitlab.com/api/commits/).

`GitLabToolEvidenceMapper` przechwytuje wyniki w dotychczasowym evidence
chatu wraz z argumentami, reason i call id. Interpretacja należy do feature'a.
Instrukcje użycia historii są częścią neutralnych opisów tools oraz
feature-owned customization per tool. Prompt zachowuje cel analizy i kontrakt
odpowiedzi, bez osobnej instrukcji obsługi historii. Dostępność report tools
nie upoważnia do mutacji raportu na zwykłe pytanie.

Rollback: usunięcie dwóch tools ze wspólnych allowlist feature’ów.
