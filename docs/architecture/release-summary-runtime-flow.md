# Release summary: Diff-First AI

## Zakres i ownership

Release summary dokumentuje planowane zmiany bazodanowe miedzy dwoma refami
systemu Operational Context. Jedyny tryb to `LIQUIBASE`. Nie wykonuje migracji
i nie ustala rzeczywistego stanu bazy. Analiza zmian publicznych API jest poza zakresem.

`features.releasesummary` posiada katalog, kolektor diff, interpretacje AI,
prompt/policy, raport, job i persistence. Neutralny transport porownania
nalezy do `integrations.gitlab`; narzedzia odczytu do `agenttools`, a sesja
i report tools do `aiplatform`. Feature nie importuje siblingow. Nie ma
deterministycznego parsera ani obowiazkowego skanu tresci kandydatow Liquibase.

## API i wybor

| Endpoint | Kontrakt |
| --- | --- |
| `GET /api/release-summary/applications` | Systemy z jawnymi primary/priority 1. |
| `GET /api/release-summary/applications/{systemId}/refs` | `kind`, `search`, `page`; paginowane opcje i dostepnosc per repo. |
| `POST /api/release-summary/jobs` | `systemId`, `mode: LIQUIBASE`, `baselineRef`, `targetRef`, opcjonalne `model`, `reasoningEffort`; 202 i snapshot. |
| `GET /api/release-summary/jobs/{jobId}` | Przebieg, porownanie, evidence, raport, wynik, usage i chat. |
| `POST /api/release-summary/jobs/{jobId}/chat/messages` | `message`, `updateReport` domyslnie false; 202. |
| `PUT /api/release-summary/jobs/{jobId}/report` | Shared AnalysisReportEditRequest z oczekiwana rewizja. |
| `GET /api/release-summary/jobs/{jobId}/export` | Publiczny envelope V1 terminalnego runu. |

Ref to `kind: BRANCH|TAG|COMMIT` i `name`; branch/tag maja kanoniczne nazwy
`refs/heads/...` / `refs/tags/...`, commit pelny SHA 40/64 hex. Ta sama para
nazw obowiazuje wszystkie wybrane repozytoria. Brak refa nie uruchamia fallbacku.

Lista refow przekazuje `committedAt` i `repositoryCommits` (repositoryId,
committedAt). Data pochodzi z `commit.committed_date` listy branches/tags,
bez dodatkowych odczytow per ref; dla jawnego SHA z juz rozwiazywanej rewizji.
Niepoprawna lub brakujaca data daje null, nie date zastepcza. Agregacja tej
samej nazwy wybiera najnowszy znany timestamp i zachowuje daty per repo.
Nie jest to data pusha ani utworzenia brancha/tagu.

Katalog, refs i pinowanie stosuja ten sam filtr: tylko `role: primary`
z `priority: 1`. Shared-library, pozostale role i brak wartosci sa wykluczone.
Dopiero zaakceptowane wpisy sa scalane po repo z suma prefixow; odrzucony
wpis nie rozszerza zakresu. Brak prefixow w zaakceptowanym wpisie oznacza
cale repo. Brak takich repo daje `NO_REPOSITORIES`. Grupa jest z konfiguracji.
Filtr nie jest dowodem skladu wdrozenia ani wersji bibliotek i nie ogranicza
dotychczasowego osobno przypinanego research AI w skonfigurowanej grupie.

## Przygotowanie diff

1. Zapis QUEUED z czterema planowanymi krokami nastepuje przed dispatch.
   Blad zapisu blokuje dispatch; odmowa executora zostawia FAILED/SCHEDULING_FAILED.
2. RESOLVE_REFS przechwytuje katalog i przypina oba refy per repo do SHA.
   Identyczna para projekt/ref jest rozwiazywana raz w runie.
3. COLLECT_DIFF wywoluje GitLabRepositoryComparisonPort. Dwa kompletne drzewa
   path/id/mode ze wspolnego GitLabRepositoryTreeService wyznaczaja delte;
   nie pobiera sie tresci blobow, archiwum, klona ani cache kodu.
4. REST compare z `straight=true` dostarcza opcjonalne patche A -> B, nie
   diff od merge base. GitLab moze ograniczyc tablice diffs przy timeout lub
   limicie. Lista zmian nie opiera sie na tej tablicy: pominiety plik nadal
   istnieje w manifeście. Rename jest laczony tylko dla potwierdzonych stron;
   bez patcha zachowujemy dodanie/usuniecie zamiast zgadywac rename.
5. Feature zachowuje tylko zmiany dopasowane do zaakceptowanych prefixow
   i `release-summary.diff.path-contains-any` w application.properties.
   Domyslna lista to `sql,changelog`; contains any of sprawdza stara i nowa
   pelna sciezke bez rozrozniania wielkosci liter. Slowa sa trimowane;
   pusta lista lub puste slowo blokuja start aplikacji. Rozszerzenie nie
   ogranicza wyboru. Przed pozytywnym dopasowaniem wykluczana jest cala
   zmiana, jesli stara lub nowa sciezka zawiera `src/test`, bez rozrozniania
   wielkosci liter. Dotyczy tez rename do/z katalogu testowego. Pozostale
   pliki nie trafiaja do evidence, eksportu
   ani initial promptu. Rename uwzglednia oba konce. Evidence ma `changes`,
   `gaps`, `changeListComplete`; ostatnie pole potwierdza kompletna liste Git
   stanowiaca podstawe selekcji, nie kompletna interpretacje migracji.
   ChangedFile zachowuje repositoryId, project i obie strony wraz z patchem.
6. ReleaseSummaryPreparation uzupelnia brakujacy lub niepelny wybrany patch:
   czyta tylko istniejace strony wybranej zmiany przez verified read na SHA
   i wylicza unified diff biblioteka JGit. Gotowy kompletny patch nie wymaga
   tych odczytow. Dodanie/usuniecie nie powoduje odczytu nieistniejacej strony.
   Identyczny blob przy zmianie nazwy/trybu nie wymaga odczytu tresci.
   Binary, LFS, symlink, gitlink, niepelny tekst i blad odczytu pozostaja
   jawnym PATCH_INCOMPLETE, bez udawania braku zmian i bez skanu modulow.
   Brak poprawnego drzewa/manifestu blokuje AI; problem pojedynczej wybranej
   roznicy nie usuwa potwierdzonego manifestu i nie blokuje calej sesji.
7. Ta sama preparacja dobiera `.github/**/*.instructions.md` z target SHA
   przez neutralne GitLabInstructionContextPort.discoverApplicable.
   Inventory sluzy do odkrycia nazw; tresc czytana jest tylko dla instrukcji,
   nie pozostalych modulow. applyTo obsluguje przecinkowe globy `*`, `**`, `?`;
   brak pola oznacza zakres repozytorium. Dopasowanie obejmuje obie sciezki
   wybranych zmian. Grupy klamrowe, klasy znakow, negacja, bledny YAML,
   obciecie, limit i niezgodne provenance sa jawna luka, bez cichego
   zastosowania do wszystkiego. Nie sa dolaczane pliki niepasujace.
   Brak wybranych zmian nie uruchamia discovery instrukcji.
   Dotychczasowe discover dla GitLab Source i Change Verification pozostaje
   bez zmian; scoped discovery nie podaza rekurencyjnie za referencjami.

Paginacja przechodzi wszystkie strony. Niepoprawny kursor, wpis, path/id/mode,
duplikat lub brak body jest bledem, nie pustym diffem. Przejsciowe bledy
transportu i HTTP 408/429/500/502/503/504 maja neutralny GitLabReadRetry:
trzy proby, backoff 500/1000 ms, Retry-After do 120 s. TLS, walidacja i
401/403/404 nie sa ponawiane. Retry nie dotyczy mutacji ani sesji AI.

To nadal odczyt metadanych obu pelnych drzew, wiec duze repo wymaga wielu
stron HTTP. Nie gwarantuje czasu w sekundach. Ogranicza koszt glownie przez
usuniecie tysiecy HEAD/GET tresci; wybrane brakujace diffy i instrukcje maja
celowane odczyty. Czas przygotowania i AI nalezy mierzyc osobno.

## Przebieg i prompt

Kolejnosc to RESOLVE_REFS, COLLECT_DIFF, PREPARE_PROMPT, AI_REPORT.
Nieuruchomione kroki sa SKIPPED, ukonczone pozostaja po bledzie i restarcie.
ProgressEvidence publikuje manifest i patch jako atrybut `Patch`, nie jako
pelne beforeContent/afterContent. Zawiera refy/SHA, oba konce i tryby oraz
ograniczenia. `producesEvidence` wskazuje projekcje takze przy blokadzie.
Nie duplikuje jej w prompcie.

PREPARE_PROMPT utrwala dokladny `preparedPrompt` i COMPLETED przed sprawdzeniem
modelu, assemble i wykonaniem. Ten sam tekst trafia do CopilotRunRequest.
Zawiera pelne captured payload encji systemu/repozytoriow/scope, obie rewizje,
manifest tylko wybranych zmian (repozytorium, projekt, old/newPath), changelogChanges
z przygotowanymi patchami, pasujace repositoryInstructions z target SHA,
jawne luki oraz adres GitLaba dla cytowan. Nie osadza patchy pozostalego kodu.
Limity discovery instrukcji sa jawne; platformowy preflight pozostaje bez
cichego przycinania diffow albo Operational Context.
Shared panel dostaje `preparedPromptStepCode=PREPARE_PROMPT`.

Kompletna lista Git bez zmian dopasowanych do filtra daje NO_CHANGES bez AI
i bez pustych tabel. Wynik dotyczy tylko skonfigurowanego zakresu: nie oznacza
identycznych rewizji ani braku zmian bazodanowych poza filtrem. Niepusty wybrany
diff nie daje NO_CHANGES tylko dlatego, ze nie rozpoznano Liquibase.
AI interpretuje przygotowane wybrane roznice i instrukcje, wykorzystujac pelny
captured Operational Context. Manifest pozostalych plikow nie jest przekazywany. Filtr sciezki nie dowodzi braku skutkow poza nim.
AI doczytuje konkretne potrzebne pelne pliki, include/includeAll, SQL/CSV,
properties, rollback, kolejnosc, warunki i kod wyjasniajacy funkcje, nie skanuje
wszystkich modulow ani wszystkich zmienionych Java/POM. Uwzglednia zasoby pomiedzy modulami,
bez zgadywania po basename. Nierozwiazana zaleznosc to jawna luka badania,
nie deterministyczna blokada przed sesja.

## AI i raport

Polski skill `release-summary-write-report` pracuje w jednym wspolnym effective
root platformy. Seed nie nadpisuje istniejacych plikow; operator moze przywrocic
aktualny seed w Platform / AI Skills. Inline prompt, puste artifactContents,
bez attachments, shell, DB, zapisu repo ani wykonywania migracji.

Allowlista: szesc narzedzi GitLab (branches, tree, files, search, read, chunk),
cztery opctx (scope, list, search, get) i szesc report (get, chunk, upsert,
patch, header, meta). Built-in skill obsluguje platforma. GOAL_DRIVEN nie ma
feature'owego limitu liczby wywolan; nadal obowiazuja scope i limity providera.
Hidden forRevisions zachowuje oba SHA, odrzuca trzeci ref tych repo i osobno
przypina dodatkowe projekty grupy. Katalog jest captured read session.
Wybrany/domyslny model jest wymagany, a platformowy preflight sprawdza caly
prompt bez cichego przycinania i bez feature'owego wyboru tieru.

AnalysisReport jest trescia; finalna proza ani JSON nie zastepuja report tools.
Scaffold jest prywatny. Zaakceptowany zapis sekcji publikuje checkpoint.

| Sekcja | Order i tresc |
| --- | --- |
| OVERVIEW | 1; krotkie podsumowanie zmian danych i celu. |
| TABLE_LIST | 2; Tabela / Zakres zmian albo dokladnie `Nie zidentyfikowano zmian tabel.`. |
| DB_DETAIL_<slug> | 3..999999; osobno repo/kwalifikowana tabela, tytul zgodny z lista. |
| APPENDIX | 1000000; pozostale operacje, ustawienia, wycofanie i warunki. |

Feature dopuszcza nowe DB_DETAIL id dopiero po walidacji, dopisujac je do
wlasnej session-bound listy uzywanej przez report tools. Platforma nadal
sprawdza dokladne id, bez wildcardow i bez semantyki Liquibase.
Tabela szczegolow ma Obiekt / Zmiana / Opis funkcjonalny. Zmiana zachowuje
SQL, typ, NULL, default, definicje i wartosci przed/po. Kolumna z constraints
to jeden wiersz; indeks zachowuje kolejnosc i unikalnosc. SQL rozwin na obiekty,
bez dodatkowego wiersza skryptu i bez sztucznego sql-coverage. Funkcja wymaga
odczytanego kodu/opctx albo jawnego braku dowodow. Nie zawiera zargonu kodu,
prefiksow wniosku, linkow i identyfikatorow parsera. Usuniecie pliku nie
oznacza DROP w dzialajacej bazie.

Cytowania sa tylko w meta.references jako URL rzeczywiscie odczytanego zrodla.
Sam patch compare NIE rejestruje pelnego sourceRef. Pelne verified odczyty
przygotowania roznic oraz kompletne pasujace instrukcje sa rejestrowane w
prywatnym knownSources, przekazywane w source-links i zachowywane po restarcie;
AI nie powtarza ich tylko dla cytowania. Kazda sekcja tabeli wymaga
odczytanego pliku GitLab na przypietym SHA. URL opctx otwiera biezaca encje,
ale podstawą badania jest captured snapshot. Patch i manual edit zachowuja
walidacje struktury i cytowan; neutralny digest/revision guard pozostaje.
Backend nie udaje niezaleznej weryfikacji prawdziwosci definicji SQL przez parser.

Wynik `release-summary-diff-report-v1` ma changeListComplete, changedFiles (liczba wybranych zmian),
documentedTables, readSources, semanticCoverage i visibilityLimits. Nie ma
expectedUnits/expectedElements ani licznika pelnosci semantycznej.
semanticCoverage to AI_ASSESSED (NOT_APPLICABLE dla pustej delty). COMPLETED
oznacza poprawnie zapisany raport bez zadeklarowanych luk, nie dowod rozpoznania
wszystkich skutkow. Systemowy visibilityLimit przypomina o tej granicy także
przy COMPLETED; UI i Share przekazuja go do shared meta. Braki sekcji,
niezgodnosc listy tabel, niedozwolone zrodla lub jawne luki daja PARTIAL.
Brak zapisu raportu to FAILED/REPORT_NOT_WRITTEN. Timeout po mutacji zachowuje
PARTIAL z checkpointem. Niepelny manifest daje BLOCKED/CHANGE_LIST_INCOMPLETE.

## Historia i UI

Shared polling, progress/activity/evidence/usage, report renderer, Share,
manual editor, chat i operation guard pozostaja. Usage initial i chatu nie
jest liczone podwojnie. Restart zachowuje SHA i captured katalog bez resolve;
przerwany run staje sie INTERRUPTED/PARTIAL, wiadomosc FAILED bez resend.
Brak prywatnego kontekstu wylacza kontynuacje. Chat wznawia istniejaca sesje
bez fallbacku; mutacje wymagaja jawnego updateReport, domyslnie false.

Publiczny envelope: schema tdw.release-summary-export, version 1,
payload.type release-summary-analysis i resultContract release-summary-diff-report-v1.
Zawiera diff, prompt, przebieg, raport, wynik, chat i usage, bez auth i sessionId.
Import lokalny jest read-only. Stary ksztalt skanu i inne wersje sa odrzucane,
bez migratora. Stare lokalne runy mozna usunac przez shared History API
sprzatajace tez sesje; wymaga to osobnej zgody na dokladny zakres danych.

Ekran zachowuje wspolne application/branch select, paginacje i typowane refy,
bez auto-select dla pary. Aplikacja i refy sa w jednym rzedzie, AI w drugim,
akcje na dole; mobile uklada pola pionowo. Etykiety to Punkt wejscia i Stan
docelowy, bez zamiany kierunku. Oba selektory zamiast etykiety rodzaju refa pokazuja date ostatniego commita
w formacie dd.MM.yyyy, w strefie przegladarki, bez godziny. Brak danych to
`Brak daty`. Tooltip wyjasnia najnowsza date i daty per repozytorium.
Paginacja scala metadane powtarzajacych sie refow, nie gubi dat poprzedniej
strony. Kolejnosc listy i wyszukiwanie po nazwie pozostaja bez zmian.
Historia QUEUED/RUNNING wznawia polling.
Wynik zaczyna jeden AnalysisResultHeader: `Zmiany bazodanowe miedzy ... a ...`
z requestu joba, także dla Share. Bez dodatkowego naglowka, zakladek, paskow
statusu i osobnego widoku kompletności. Report panel ma embedded=true,
showTechnicalIds=false, zachowane kotwice i zwijany appendix/meta. Bledy i
informacja o imporcie sa pod naglowkiem. Nawigacja lokuje feature po Change
Verification, przed Config Drift Viewer, z ikona deployed_code.

`release-summary.enabled` blokuje nowe starty/chat/edit, zachowujac historie
i export. Nie wycofuje reusable capability. Testy sa fikcyjnym CRM; opt-in
RELEASE_SUMMARY_LIVE_TEST=true uruchamia platna sesje Copilota, standardowo
pomijana. Walidacja wspolnego kontraktu wymaga testow Angulara, builda UI,
a nastepnie `mvn -q -Pbackend-dev clean package`.