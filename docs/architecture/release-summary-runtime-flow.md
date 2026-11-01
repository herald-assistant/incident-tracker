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
Każda strona ma rodzaj `BRANCH`, `TAG` albo `COMMIT`; można zamienić kierunek.
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
7. Manifest zawiera stabilne `unitId`, `elementId`, lokalizację, pełne mapy
   właściwości `before`/`after`, źródła i luki. Duże pliki są dzielone według
   changesetów/grup operacji na jednostki do 32 elementów, bez limitu łącznej
   liczby elementów. Pełne źródła i deklaracje przodków zachowują kontekst.

XML nie rozwiązuje zewnętrznych encji. YAML jest odczytywany bez wykonywania
konstruktorów. Parser nie wykonuje SQL, pluginów ani kodu custom changes.

Początkowy prompt zawiera kompletne `payload` wybranego systemu, repozytoriów
i zakresów kodu oraz pełny zebrany materiał źródłowy. Nie sprowadza encji do
kart wyszukiwania i nie wycina danych do top-N. Pozostałe encje są dostępne
przez `opctx_*` na tym samym zamrożonym katalogu.

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

`expectedElements` / `documentedElements` liczą obiekty i operacje w inwentarzu
raportu, a `expectedUnits` / `documentedUnits` zakresy plików. Nie są liczbami
węzłów parsera ani jego paczek evidence. Obecność pełnego wiersza nie oznacza
pewności funkcjonalnej. Status, pokrycie strukturalne i `visibilityLimits` muszą być
odczytywane razem. `AnalysisReport` jest źródłem treści; wynik V1 jest jego
deterministyczną projekcją względem manifestu evidence.

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

Odbiorcą jest analityk systemowy na poziomie mid, który sprawdza zakres
wdrożenia. `ReleaseSummaryReportInventory` scala techniczne paczki jednego
pliku w jeden zakres raportu. Kolumna wraz z ograniczeniami jest jedną pozycją,
a indeks obejmuje uporządkowane kolumny i unikalność. Niezmienione właściwości
rodzica są odtwarzane z pełnego pliku, także przy zmianie samego NULL/NOT NULL
lub kolejności kolumn indeksu. Puste kontenery składniowe nie są pozycjami.
Pełne źródła, manifest parsera i wszystkie właściwości pozostają w evidence.

| Sekcja | Treść |
| --- | --- |
| `OVERVIEW` | 2–4 zdania: co wchodzi na wdrożenie, po co i jaki jest kierunek porównania. |
| `CHANGE_INDEX` | Obszar, krótki opis zmiany i link do szczegółów każdego zakresu. |
| `DB_DETAIL_<groupId>` | Jedna tabela: Obiekt, Zmiana techniczna, Znaczenie funkcjonalne, Źródła. |
| `DEPLOYMENT_IMPACT` | Kolejność, zgodność, warunki, checksumy, rollback, ryzyka i weryfikacja. |

Wiersz szczegółów łączy definicję obiektu z opisem danych, sposobu użycia i
funkcjonalnego efektu zmiany, zwykle w 1–2 krótkich zdaniach. Nie powtarza typu,
NULL ani wspólnych ograniczeń badania. Liczby kolumn i indeksów obejmują także
obiekty z SQL. Opis zaczyna się od `Potwierdzone:`, `Wniosek:`
lub `Do wyjaśnienia:` i zawiera konkretne uzasadnienie. Model bada kod i katalog;
sama nazwa obiektu nie wystarcza. Dla SQL opisuje wszystkie rozpoznane obiekty
i zachowuje jawne ograniczenie pewności. Usunięcie pliku albo include opisuje
jako zmianę changelogu, bez sugerowania wykonania DROP na działającej bazie.

Walidator wymaga wszystkich pozycji, niezmienionych definicji rozpoznanych
obiektów, właściwego kierunku zmian, kompletnych komórek oraz źródeł faktycznie
odczytanych przez tę sesję. SQL i zależności wymagają opisu AI. Może ono dopisać
czytelny tytuł po nazwie operacji SQL i dodać rozpoznane obiekty, ale nie usunąć
pozycji inwentarza. Linki mają krótkie etykiety i wskazują pliki na przypiętym SHA;
linki Operational Context otwierają bieżącą encję, podczas gdy podstawą analizy
pozostaje captured snapshot. Hashy elementów, ścieżek AST, surowego JSON i
technicznych identyfikatorów sekcji nie pokazujemy w raporcie.

Mutacja jest walidowana przed zapisem. `report_patch_section` dodatkowo
zachowuje wspólny digest guard i unikalność zastępowanego fragmentu.
Scaffold i instrukcje uzupełniania są prywatnym materiałem AI. Samo przygotowanie
kontekstu, blokada modelu lub zmiana nagłówka nie publikuje ich jako wyniku.
Dopiero zaakceptowana sekcja daje checkpoint raportu. Placeholdery są odrzucane;
literalne wartości źródłowe nie są traktowane jak instrukcje do uzupełnienia.
Nie ma odczytu rezultatu z JSON ani prozy ostatniej wiadomości modelu.
NO_CHANGES ma osobny krótki raport bez pustych tabel i bez sesji AI.

## Historia, chat, edycja i przenoszenie

UI reuse'uje polling, report panel, zakładki, evidence/activity/usage, Share,
manual editor i chat. Wymaga wybranego modelu albo modelu domyślnego backendu.
Kierunek „Stan obecny → Wdrażana wersja” pozostaje widoczny nad wynikiem.
Shared report panel ma `showTechnicalIds=false`, zachowując kotwice sekcji.
Linki Markdown do sekcji otwierają je w bieżącym dokumencie bez nawigowania
do bazowego URL aplikacji; Share zachowuje kotwice także w eksporcie.
Kompletność źródeł i interpretacji ma osobną zakładkę, poza treścią raportu.
Usage runu dotyczy initial analysis,
a usage wiadomości kolejnych tur; wspólny aside agreguje je bez podwójnego liczenia.

Publiczny export ma `schema: tdw.release-summary-export`, `version: 1`,
`payload.type: release-summary-analysis` i
`payload.resultContract: release-summary-analyst-report-v1`. Zawiera pełne źródła,
raport, wynik i wiadomości; nie zawiera tokenów, sesji ani prywatnego kontekstu
kontynuacji. Import pliku jest lokalny w UI, wyłącznie do odczytu, bez
odtwarzania uprawnień lub sesji AI. Inne wersje i tryby są odrzucane.

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
