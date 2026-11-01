# Release summary — runtime Liquibase

## Zakres i ownership

Release summary dokumentuje różnice Liquibase między stanem bazowym i docelowym
jednego systemu z Operational Context. Jedyny aktywny tryb to `LIQUIBASE`.
Zmiany kontraktów API pozostają poza bieżącym produktem. Feature nie wykonuje
migracji i nie ustala stanu wdrożenia w konkretnej bazie.

Pakiet `features.releasesummary` posiada request, katalog aplikacji, discovery,
manifest elementów, prompt, policy, raport, job i persistence adapter. Korzysta
z `integrations`, `agenttools`, `aiplatform`, `shared` i `localworkspace` bez
importowania innych feature'ów. Parser Liquibase nie jest częścią platformy AI.

## Wybór i publiczne API

Ekran `/release-summary` używa wspólnego `ApplicationSelectComponent` oraz dwóch
`GitLabBranchSelectComponent` z wyłączonym automatycznym wyborem pierwszej opcji.
Każda strona ma rodzaj `BRANCH`, `TAG` albo `COMMIT`.
Ten sam zestaw nazw refów obowiązuje wszystkie repozytoria systemu.

| Endpoint | Kontrakt |
| --- | --- |
| `GET /api/release-summary/applications` | Systemy z jawnymi zakresami kodu i repozytoriami. |
| `GET /api/release-summary/applications/{systemId}/refs` | `kind`, `search`, `page`; paginowane opcje i dostępność per repo. Dla commita wymagany pełny SHA. |
| `POST /api/release-summary/jobs` | `systemId`, `mode: LIQUIBASE`, `baselineRef`, `targetRef`, opcjonalne `model`, `reasoningEffort`; `202` i snapshot joba. |
| `GET /api/release-summary/jobs/{jobId}` | Stan runu, porównanie, evidence, raport, pokrycie, usage i chat. |
| `POST /api/release-summary/jobs/{jobId}/chat/messages` | `message`, `updateReport` (domyślnie `false`); `202`. |
| `PUT /api/release-summary/jobs/{jobId}/report` | Wspólny `AnalysisReportEditRequest` z oczekiwaną rewizją. |
| `GET /api/release-summary/jobs/{jobId}/export` | Publiczny envelope V1 terminalnego runu. |

Ref ma pola `kind` i `name`. Branch i tag są rozróżniane kanonicznymi nazwami
`refs/heads/...` i `refs/tags/...`; commit to pełne 40 lub 64 znaki hex.
Nie ma zastępowania niedostępnego refa domyślną gałęzią ani pomijania repozytorium.

## Przygotowanie materiału

1. Job utrwala `QUEUED` w Analysis History przed przekazaniem pracy executorowi.
   Wpis od początku identyfikuje aplikację i kierunek porównania, ma datę
   rozpoczęcia i cztery planowane kroki. Błąd zapisu blokuje dispatch;
   odmowa executora pozostawia wpis `FAILED` z `SCHEDULING_FAILED`.
2. Katalog jest przechwytywany przez `OperationalContextPort.capture()`.
   System i jego jawne `codeSearchScopes` wyznaczają wszystkie repozytoria
   oraz sumę prefixów. Powtórzenia repo są scalane bez utraty zakresów.
   Grupa GitLab pochodzi z konfiguracji.
3. Oba refy każdego repo są rozwiązywane do SHA. Identyczna para projekt/ref
   jest przypinana raz w runie, także przy porównaniu refa z nim samym.
4. `GitLabRepositorySnapshotPort` pobiera pełne drzewo przez wspólny
   `GitLabRepositoryTreeService`, zachowując blob ID, gitlinks i symlinki.
   Odczyt przechodzi wszystkie strony; przekroczenie technicznego zakresu
   kursora, niepoprawny wpis lub tryb obiektu kończy się błędem, nie pustym wynikiem.
5. `LiquibaseEvidenceCollector` skanuje w prefixach XML, YAML/YML, JSON i SQL
   na obu SHA. Rozpoznaje changelogi, formatted SQL oraz domyka odwołania
   `include`, `includeAll`, `sqlFile`, `rollbackSqlFile`, pliki danych,
   właściwości i inne deklarowane źródła. Zależność może wychodzić poza prefix
   discovery, ale musi pozostać w tym samym repozytorium.
6. Pełny odczyt UTF-8 jest weryfikowany względem blob ID z drzewa. Zmiana SQL/CSV
   pod niezmienionym changelogiem, nowe dołączenie istniejącego pliku, usunięcie,
   zmiana atrybutu lub kolejności pozostaje widoczna. Komentarz XML bez zmiany
   deklaracji nie jest zmianą schematu.
   Gdy ta sama ścieżka ma identyczny blob ID w obu pełnych drzewach, collector
   korzysta z już zweryfikowanych bajtów strony bazowej. Nie powtarza wtedy
   odczytu HTTP, ale ponownie parsuje plik i rozwiązuje zależności dla strony
   docelowej. Nowy, zmieniony lub wcześniej nieodczytany plik wymaga własnego
   verified read. Reuse jest lokalny dla repozytorium w jednym porównaniu.
7. Manifest zawiera stabilne `unitId`, `elementId`, lokalizację, pełne mapy
   właściwości `before`/`after`, źródła i luki. Duże pliki są dzielone według
   changesetów/grup operacji na jednostki do 32 elementów, bez limitu łącznej
   liczby elementów. Pełne źródła i deklaracje przodków zachowują kontekst.

XML nie rozwiązuje zewnętrznych encji. YAML jest odczytywany bez wykonywania
konstruktorów. Parser nie wykonuje SQL, pluginów ani kodu custom changes.

Przejściowe awarie odczytu GitLab są ponawiane w neutralnej integracji,
na poziomie jednej strony drzewa albo jednego odczytu pliku/metadanych,
z zachowaniem tego samego refa i ścieżki. Maksymalnie trzy próby obejmują
HTTP 408, 429, 500, 502, 503, 504 oraz błędy transportu, także podczas czytania
body. Backoff wynosi 500 ms i 1000 ms. `Retry-After` określa minimalną przerwę;
jeżeli przekracza 120 s, odczyt kończy się błędem bez przedwczesnego ponowienia.
Przerwanie wątku zatrzymuje retry. Błędy TLS, kodowania, parsowania i walidacji
oraz HTTP 401/403/404 nie są ponawiane. Nie dotyczy to mutacji ani sesji AI.

Po nieudanym odczycie luka `SOURCE_UNAVAILABLE` zawiera stronę porównania,
ścieżkę pliku lub zależności i etap: drzewo, odczyt/weryfikacja lub parser.
Opis rozróżnia status HTTP, timeout, TLS, błąd I/O i brak potwierdzenia
poprawności źródła; HTTP 429 jawnie wskazuje ograniczenie liczby odczytów.
Nie ujawnia surowej wiadomości wyjątku ani body błędu.
Diagnostyka serwera zapisuje lokalizację i typy przyczyn. Niepełny materiał
nadal blokuje AI; ponowienia nie osłabiają kontroli SHA, blobów i pełności.

Początkowy prompt zawiera kompletne `payload` wybranego systemu, repozytoriów
i zakresów kodu oraz pełny zebrany materiał źródłowy. Nie sprowadza encji do
kart wyszukiwania i nie wycina danych do top-N. Pozostałe encje są dostępne
przez `opctx_*` na tym samym zamrożonym katalogu.

## Podgląd przebiegu przed AI

Przebieg ma stałą kolejność: `RESOLVE_REFS` (przypięcie rewizji),
`COLLECT_LIQUIBASE` (zmiany per plik), `PREPARE_PROMPT` (inicjalny prompt)
i `AI_REPORT`. Krok, który nie został uruchomiony przed zakończeniem runu,
ma status `SKIPPED`. Ukończone kroki pozostają dostępne po błędzie kolejnego.

Po zbieraniu `ReleaseSummaryProgressEvidence` tworzy operatorową projekcję
do `contextSections`: jedną pozycję na repozytorium i ścieżkę, niezależnie
od liczby jednostek manifestu. Pozycja zawiera typ zmiany, refy i SHA obu
stron, stan odczytu oraz pełne `beforeContent` / `afterContent`.
Wspólny panel pokazuje diff liniowy z oznaczeniami dodania/usunięcia.
Nie przycina treści; duży diff zachowuje całe bloki, ograniczając koszt
wyliczania dopasowania. Zmiana dołączenia lub zależności bez zmiany treści
ma jawny opis. Brak pliku w zakresie, zweryfikowany pusty plik i brak
zweryfikowanego odczytu są różnymi stanami. Luki zbierania mają osobną sekcję.
`producesEvidence` kroku wskazuje te sekcje, także przy blokadzie przygotowania.
Projekcja nie jest dopisywana do materiału AI; pełne źródła już są w evidence.

`PREPARE_PROMPT` zapisuje pełny `preparedPrompt` i status `COMPLETED` przed
sprawdzeniem modelu, assemble i wykonaniem AI. Jest to ten sam tekst, który
otrzymuje `CopilotRunRequest`, bez osobnej wersji wyświetlanej. Panel otrzymuje
jawne `preparedPromptStepCode="PREPARE_PROMPT"` i oferuje podgląd/kopiowanie.
Nie wymaga odpowiedzi ani raportu AI; prompt pozostaje w historii i eksporcie
także po błędzie modelu. Przy niepełnych źródłach albo `NO_CHANGES` sesja AI
i przygotowanie promptu są pomijane.

## Pełność źródeł i wynik

| Sytuacja | Zachowanie |
| --- | --- |
| Brak refa, błąd HTTP, niepełne drzewo, odczyt lub niezgodny blob | `BLOCKED`, bez sesji AI. |
| Brak wymaganej zależności, cykl, dynamiczna ścieżka lub custom filtr includeAll | Jawna blokada przygotowania. Opcjonalne puste includeAll jest respektowane. |
| Git LFS bez treści, symlink w zakresie lub zmieniony submodule | Jawna luka i blokada; nie deklarujemy pełności plików tych mechanizmów. |
| Pełne źródła, brak jednostek i luk | `NO_CHANGES`, raport deterministyczny bez AI. |
| SQL, dane, custom change lub nieznana przestrzeń nazw XML | Materiał trafia do AI, ale jego pełność semantyczna pozostaje `AI_IDENTIFIED`. |
| Brak uzasadnienia funkcjonalnego lub nierozstrzygnięte ograniczenie | Raport `PARTIAL` z konkretną luką. |
| Wszystkie wymagane sekcje i elementy udokumentowane bez luk | `COMPLETED`. |
| AI nie zapisze raportu przez tools | `FAILED` z `REPORT_NOT_WRITTEN`; finalna proza nie zastępuje raportu. |
| Błąd/timeout po zaakceptowanej mutacji raportu | `PARTIAL`; checkpoint zachowuje zapisane sekcje. |

`expectedUnits` / `documentedUnits` liczą grupy tabel wyodrębnione po repozytorium
i kwalifikowanej nazwie oraz opcjonalną grupę pozycji appendixu. Nie liczą
plików, węzłów parsera ani jego paczek evidence. `expectedElements` to suma
obowiązkowych pozycji inwentarza, a `documentedElements` obejmuje pozycje
z poprawnie zwalidowanych sekcji. Dodatkowe wiersze opisane przez AI nie
zwiększają tych liczników; mapowanie skryptu na wiersze nie jest niezależnym
potwierdzeniem liczby wszystkich obiektów semantycznych SQL.

Pełne pokrycie wymaganych pozycji może nadal dawać `PARTIAL`, gdy pozostają
niezweryfikowane operacje SQL, brak zastosowania funkcjonalnego lub inne luki.
Nie oznacza to braku wygenerowanego raportu. Status, pokrycie strukturalne
i `visibilityLimits` należy odczytywać razem. `AnalysisReport` jest źródłem
treści; wynik V1 jest jego deterministyczną projekcją względem inwentarza
zbudowanego z evidence.

## Sesja AI i narzędzia

Feature składa `CopilotRunRequest` oraz polski skill
`release-summary-write-report`. Katalog skilli jest wspólnym effective rootem
platformy. Materiał jest inline w prompcie; `artifactContents` jest pusty,
bez attachments i bez lokalnego filesystemu dla modelu.

Jawna allowlista obejmuje sześć neutralnych narzędzi GitLab
(`gitlab_list_repository_branches`, `gitlab_list_repository_tree`,
`gitlab_list_repository_files`, `gitlab_search_repository_files`,
`gitlab_read_repository_file`, `gitlab_read_repository_file_chunk`), cztery
narzędzia `opctx_*` (`get_scope`, `list_entities`, `search`, `get_entity`)
oraz sześć narzędzi raportu (`report_get_current`, `report_read_section_chunk`,
`report_upsert_section`, `report_patch_section`, `report_update_header`,
`report_update_meta`). Wbudowany `skill` pozostaje obsługiwany przez platformę.

Hidden `GitLabRepositoryToolScope.forRevisions` dopuszcza obie przypięte rewizje
repozytoriów porównania, odrzuca trzeci ref tych repo i osobno przypina
dodatkowe projekty skonfigurowanej grupy. Odczyty rejestrują `sourceRef`.
Odczyt może podać nazwę wybranego refa albo jego pełny SHA; znany SHA jest
akceptowany tylko dla projektu, do którego został przypięty, bez nowego resolve.
Hidden `OPERATIONAL_CONTEXT_READ_SESSION` zamraża katalog. Nie zmienia to
domyślnego zachowania innych feature'ów.

`GOAL_DRIVEN` wyłącza budżet liczby wywołań dla tej sesji, bez
`CopilotSessionHardToolBudget`. Nadal obowiązują scope, allowlista, uprawnienia
oraz limity dostawcy. Shell, DB, zapis kodu i mutation tools katalogu nie są
dostępne. Brak budżetu wywołań nie oznacza nieskończonego okna modelu ani czasu.

Wybrany model lub skonfigurowany `analysis.ai.copilot.model` jest wymagany.
Platformowy preflight sprawdza cały przygotowany prompt z dynamicznym katalogiem
modeli i polityką long context. Feature nie przycina wejścia, nie zmienia
samodzielnie tieru ani nie przełącza go na inny model. Brak modelu daje
`MODEL_REQUIRED`, odmowa preflightu `CONTEXT_PREFLIGHT_FAILED`. Bieżący
`analysis.ai.copilot.send-and-wait-timeout` w application.properties wynosi `20m`.

## Raport i walidacja

Odbiorcą jest analityk systemowy na poziomie mid, który zna SQL, ale nie kod
aplikacji ani Liquibase. `ReleaseSummaryReportInventory` grupuje obiekty według
repozytorium i kwalifikowanej nazwy tabeli, niezależnie od plików i paczek
parsera. Tabele z różnych repozytoriów lub schematów pozostają oddzielne.
Kolumna wraz z ograniczeniami jest jedną pozycją, a indeks obejmuje uporządkowane
kolumny i unikalność. Niezmienione właściwości rodzica są odtwarzane z pełnego
pliku, także przy zmianie samego NULL/NOT NULL lub kolejności kolumn indeksu.
Pełne źródła, manifest parsera i wszystkie właściwości pozostają w evidence.

| Sekcja | Treść |
| --- | --- |
| `OVERVIEW` | Podsumowanie: 2–3 krótkie zdania o zmianach danych i ich celu. |
| `TABLE_LIST` | Lista tabel: Tabela, Zakres zmian; bez odsyłaczy do innych sekcji. |
| `DB_DETAIL_<groupId>` | Jedna sekcja tabeli: Obiekt, Zmiana, Opis funkcjonalny. |
| `APPENDIX` | Nieprzypisane operacje, ustawienia migracji, wycofanie oraz istotne warunki wdrożenia. |

Kolumna **Zmiana** zachowuje rodzaj operacji i szczegóły SQL: typ, dopuszczalność
NULL, wartość domyślną, definicję indeksu lub ograniczenia oraz wartości przed/po.
Znana definicja jest niezmiennym początkiem komórki; dodatkowe szczegóły tego
obiektu można dopisać po `<br>`. Dzięki temu operacja SQL zmieniająca tryb tabeli
pozostaje przy tej tabeli w kolumnie Zmiana. Akapity pod tabelą nie zastępują
definicji w komórkach, a mapowanie SQL na znaną pozycję wymaga takiego uzupełnienia.
Opis funkcjonalny wyjaśnia zastosowanie danych zwykle jednym, najwyżej dwoma
krótkimi zdaniami. Nie zawiera nazw klas, metod, DAO, changelogów ani prefiksów
„Potwierdzone”, „Wniosek” i „Do wyjaśnienia”. Każda sekcja jest samodzielna.
Kod pozostaje materiałem badania, a źródła pojawiają się wyłącznie w meta.
Gdy nie ustalono funkcji, opis mówi o tym wprost, np. „Nie ustalono zastosowania
pola.”; zakres badania i brakujące dowody trafiają do `meta.gaps`.

Surowy SQL otrzymuje jedynie wskazówki przypisania do tabeli. Nieznane operacje
pozostają w appendixie, a AI nadal odpowiada za pełną inwentaryzację SQL.
W głównej tabeli opisuje rzeczywiste obiekty bez powtarzającego je wiersza
skryptu. Dla każdego tak rozwiniętego skryptu metadane zawierają referencję
`type: sql-coverage`, etykietę z planu, URL źródła oraz dokładne nazwy opisanych
wierszy w `description`, po jednej w linii. Walidator sprawdza istnienie wierszy
i odczytanego źródła. Mapowanie nie jest dowodem semantycznej pełności SQL:
`structurallyVerified` pozostaje false, a raport nie zyskuje przez nie COMPLETED.
Usunięcie definicji z repozytorium nie oznacza wykonania DROP w działającej bazie.

Walidator wymaga wszystkich pozycji, znanych definicji, właściwego kierunku
zmiany, kompletnych komórek, kolejności sekcji i źródeł faktycznie odczytanych
przez sesję. `meta.references.target` jest zweryfikowanym URL z mapy źródeł,
nie surowym sourceRef. Krótkie etykiety linków otwierają pliki na przypiętym SHA;
linki Operational Context otwierają bieżącą encję, lecz podstawą analizy pozostaje
captured snapshot. Nie pokazujemy identyfikatorów parsera ani technicznych ID
sekcji w treści raportu.

Wspólny renderer sortuje sekcje według `order`, niezależnie od kolejności
checkpointów. Reuse'uje section-content i zwijane meta pod sekcją. Feature
wskazuje opcjonalną sekcję appendixu; jej treść i metadane całego raportu są
zwinięte na samym dole. Share używa tej samej kolejności: Markdown obejmuje
główny raport, a Markdown + Meta także źródła i appendix. Edytor ręczny
zachowuje walidację definicji, źródeł i mapowania SQL.

Mutacja jest walidowana przed zapisem. `report_patch_section` dodatkowo
zachowuje wspólny digest guard i unikalność zastępowanego fragmentu.
Scaffold i instrukcje uzupełniania są prywatnym materiałem AI. Samo przygotowanie
kontekstu, blokada modelu lub zmiana nagłówka nie publikuje ich jako wyniku.
Dopiero zaakceptowana sekcja daje checkpoint raportu. Placeholdery są odrzucane;
literalne wartości źródłowe nie są traktowane jak instrukcje do uzupełnienia.
Nie ma odczytu rezultatu z JSON ani prozy ostatniej wiadomości modelu.
NO_CHANGES ma osobny krótki raport bez pustych tabel i bez sesji AI.

## Historia, chat, edycja i przenoszenie

UI reuse'uje polling, report panel, evidence/activity/usage, Share,
manual editor i chat. Wymaga wybranego modelu albo modelu domyślnego backendu.
W nawigacji Analysis Features i siatce Workspace Overview Release summary
występuje bezpośrednio po Change Verification i używa ikony `deployed_code`.
Analysis History udostępnia run również w stanach `QUEUED` i `RUNNING`.
Otwarcie wpisu prowadzi do `/release-summary?localRunId=...`; ekran odczytuje
bieżący snapshot i wznawia polling tego samego joba, bez ponownego startu.
Formularz grupuje aplikację i parę refów oznaczonych „Punkt wejścia” i „Stan
docelowy” w jednym rzędzie, a model i reasoning effort w dwóch równych polach
poniżej. Akcje generowania, importu i eksportu są na dole. Wspólny application
select przedstawia nazwę i opis aplikacji zgodnie z wzorcem Explorerów.
Na węższych ekranach pola układają się pionowo.
Formularz nie oferuje automatycznej zamiany kierunku.
Wynik zaczyna się od jednego wspólnego nagłówka
`Zmiany bazodanowe między {punkt wejścia} a {stan docelowy}`, bez poprzedzających
go pasków statusu i kierunku ani zakładek. Tytuł pochodzi z requestu zapisanego
joba, także w historii i imporcie; zmiana formularza kolejnego porównania
go nie zmienia.
Share używa tego samego tytułu. Shared report panel z `embedded=true` renderuje
samą treść bez drugiego nagłówka i zewnętrznej zwijanej karty; domyślny wariant
pozostałych konsumentów zachowuje nagłówek. `showTechnicalIds=false` ukrywa
techniczne ID, zachowując kotwice sekcji.
Nie generujemy osobnego widoku kompletności i źródeł. Źródła i ograniczenia
raportu są w meta i appendixie, a pełny zebrany materiał w przebiegu analizy.
Błędy oraz informacja o imporcie pojawiają się pod nagłówkiem. Wyliczanie
kompletności i statusu nadal należy do backendowej walidacji raportu.
Usage runu dotyczy initial analysis,
a usage wiadomości kolejnych tur; wspólny aside agreguje je bez podwójnego liczenia.

Publiczny export ma `schema: tdw.release-summary-export`, `version: 1`,
`payload.type: release-summary-analysis` i
`payload.resultContract: release-summary-analyst-report-v1`. Zawiera pełne źródła,
kroki przebiegu, przygotowany prompt, raport, wynik, wiadomości i statystyki
usage, w tym liczbę tokenów. Nie zawiera danych uwierzytelniających, identyfikatora
sesji Copilota ani prywatnego kontekstu kontynuacji.
Import pliku jest lokalny w UI, wyłącznie do odczytu, bez
odtwarzania uprawnień lub sesji AI. Inne wersje, tryby i poprzedni układ sekcji
raportu są odrzucane. Bieżący format pozostaje w wersji 1, bez migratora.
Przy zmianie formatu lokalne raporty poprzedniego układu usuwa się przez API
historii, które sprząta również powiązaną sesję Copilota.

Prywatny local run zapisuje captured katalog, przypięte rewizje, znane źródła
oraz referencję auth i sesji. Restart odtwarza je bez ponownego przypinania
branchy. Przerwany run staje się `INTERRUPTED` lub `PARTIAL`, a niedokończona
wiadomość otrzymuje błąd; wysłanie nie jest automatycznie powtarzane. Brak
prywatnego kontekstu oznacza read-only z wyjaśnieniem.

Zwykłe pytanie follow-up jest read-only. Checkbox „Zleć AI zmianę raportu”
ustawia jawne `updateReport=true`. Kontynuacja używa istniejącej sesji bez
fallbacku do nowej. Wspólna blokada operacji serializuje chat i manual edit,
a revision guard chroni przed zapisem nieaktualnej wersji raportu.

## Konfiguracja i diagnostyka

`release-summary.enabled=${RELEASE_SUMMARY_ENABLED:true}` steruje dostępnością.
Wyłączenie i restart usuwają formularz nowych runów oraz CTA z nawigacji/landing,
blokują start, chat i zapis, zachowując trasę odczytu historii i export.
Nie wymaga to wycofywania neutralnych capability używanych przez inne feature'y.

Testy jednostkowe i integracyjne nie wymagają zewnętrznego Copilota. Pilot
`ReleaseSummaryRuntimeTest` jest opt-in przez `RELEASE_SUMMARY_LIVE_TEST=true`;
wymaga lokalnie skonfigurowanego dostępu do Copilota i zużywa jego limit/koszt.
Używa fikcyjnych portów CRM dla źródeł, rzeczywistej sesji i report tools.
Zapisuje raport pod `target/release-summary-pilot/result.json`. Zwykła regresja
pomija tylko ten test live. Seed skilla nie nadpisuje operatorowych zmian
effective kopii; przy potrzebie powrotu do seeda służy ekran Platform / AI Skills.
