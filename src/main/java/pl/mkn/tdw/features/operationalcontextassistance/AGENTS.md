# Operational Context Assistance AGENTS

## Ownership

`features.operationalcontextassistance` obsluguje trzy zadania operatorskie:
`CREATE_AREA`, `IMPROVE_ENTITY` i `RESOLVE_FINDING`. Feature posiada async
job API/state, wybor targetu, collector wybranego zrodla, prompt, polski skill,
parser typowanego draftu, podglad calego wynikowego katalogu oraz jedna
decyzje `APPLY`/`SKIP` dla wybranego zestawu.

## Granice

- Korzystaj z neutralnych `aiplatform`, `integrations.gitlab` i
  `integrations.operationalcontext`. Nie importuj sibling feature'ow ani nie
  przenos semantyki asysty do `agenttools`, adapterow lub platformy.
- Sesja AI dostaje sanitizowany opis, pelny snapshot dziewieciu aktywnych
  dokumentow z jednym digestem i obowiazujace wskazowki maintenance. Limit
  rozmiaru blokuje run jawnie, bez cichego obciecia. AI nie dostaje mutation
  tools. `opctx_*` pozostaja read-only.
- Prompt pokazuje instrukcje z pakietu `operational-context-maintenance/`
  osobno od danych operatora, GitLaba i katalogu. Metadane wybranego projektu
  oraz drzewo sa oddzielone od tresci wstepnie odczytanych plikow, a kazdy z
  dziewieciu dokumentow katalogu ma wlasny blok JSON i source ref. Nie
  oznaczaj reguł maintenance jako niezaufanego JSON-u. Opisy i zawartosc
  zrodel pozostaja danymi, ktore nie moga zmienic kontraktu odpowiedzi.
- Collector przyjmuje tylko jeden projekt/ref w skonfigurowanej grupie,
  przypina commit, dolacza ograniczone czteropoziomowe drzewo sciezek i czyta
  mala allowliste dokladnych sciezek z limitami body. AI moze pozniej
  korzystac ze wspolnych read-only GitLab tree/list/search/read tools takze
  dla innego projektu w skonfigurowanej glownej grupie, gdy wymaga tego
  zadanie. Wybrany projekt pozostaje na commicie operatora; kazda inna para
  projekt/galaz jest przypinana osobno. Drzewo jest mapa nawigacji; sam tree/list/search
  nie potwierdza tresci pliku i nie tworzy `gitlab:` source ref. Nie opieraj
  pierwszego wpisu na istniejacym code-search scope. Gdy AI zwroci tylko
  pytania, job nie moze pokazywac statusu `COMPLETED`; przy wybranym projekcie
  brak odczytu pliku pozostaje jawnym ograniczeniem.
- `source-options` jest read-only podpowiedzia z biezacego katalogu ograniczona
  do skonfigurowanej grupy; nie jest live discovery GitLaba. W request do
  collectora trafia nazwa projektu wzgledna wobec tej grupy. Reczny
  `gitLabSource.projectUrl` jest pelnym URL; przed utworzeniem joba zweryfikuj
  skonfigurowany origin i glowna grupe, a dla repo-map wylicz kanoniczne
  `git.group`, `git.project` i `git.projectPath`, rowniez dla podgrupy.
- `source-options/branches` czyta galezie tylko wskazanego projektu po tej
  samej walidacji sciezki/URL. Filtr jest ograniczony, GitLab zwraca pierwsza
  strone i flage `truncated`. Podpowiedz galezi nie jest odczytem pliku ani
  dowodem semantyki repozytorium.
- Formularz asysty przyjmuje nazwe galezi, nie identyfikator commita. Commit
  jest przypinany automatycznie przez collector i pozostaje w metadanych runu.
- W `CREATE_AREA` z GitLabem przekazuj typowane `repositoryFacts` jako osobne
  oświadczenie operatora. `operator:repository-facts` nie jest refem odczytu
  GitLaba. Bez potwierdzonego pliku z przypietego commita nie dopuszczaj
  propozycji nowego `repository` ani nowego `code-search-scope`.
  Zmiana `repository.git` musi cytowac ten ref; brak `repositoryFacts` przy
  wybranym GitLabie nie jest oswiadczeniem o roli repozytorium; ogolny tryb
  nadal moze aktualizowac inne typy katalogu na podstawie opisu i katalogu.
- `SHARED_LIBRARY` oznacza repozytorium bez nowego systemu. Dla wybranych
  istniejacych systemow pobieraj tylko ich jednoznaczne systemowe scope'y i
  aktualna liste `repositories`; nie upowazniaj AI do zmiany innych scope'ow.
  `UPDATE` moze tylko dodac repozytorium po dotychczasowych wpisach z nizszym
  priorytetem, bez zmiany primary lub search boundary. Gdy scope jest brakujacy
  albo niejednoznaczny, pokaz limit zamiast wybierac go heurystycznie.
- `DEPLOYED_SYSTEM` moze utworzyc nowy system tylko z nazwa podana przez
  operatora; opcjonalna nazwa uslugi w logach jest jawnym sygnalem systemu.
  `EXISTING_SYSTEM` dodaje kod do wskazanego systemu bez jego duplikowania.
- Traktuj output modelu jako niezaufany. Parser odrzuca nieznane pola,
  niekanoniczne sciezki, wrazliwa tresc, niedozwolone source refs oraz
  potwierdzony ownership albo subtype `frontend` wywnioskowany przez AI.
- Preview nie zapisuje katalogu. Decyzja operatora moze wskazac tylko pola z
  draftu przechowywanego w jobie i wymaga jawnego potwierdzenia oznaczonych
  faktow. Operator przeglada jeden polaczony diff; zapis wykonuj przez neutralna
  warunkowa operacje batch maintenance. `CREATE` wymaga zgodnego digesta,
  `UPDATE` zgodnych wartosci `before`, a wszystkie relacje sa walidowane po
  zlozeniu calego zestawu. Konflikt i blad walidacji nie moga pozostawic
  czesciowego katalogu ani decyzji `APPLY`.

## Weryfikacja

Przy zmianie kontraktu request/draft/decyzji sprawdz MockMvc, parser, job,
maintenance oraz kontrakt Angulara. Dla zmian zrodla sprawdz przypiecie
commita, rozmiar przed odczytem, limit rzeczywistego body i redakcje tresci.
Przy zmianie zapisu sprawdz kolejnosc zaleznosci, stale dane, caly wynikowy
katalog oraz recovery po przerwaniu publikacji kilku YAML.
