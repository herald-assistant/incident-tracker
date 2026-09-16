# UX Inspector Runtime Flow

## Cel i granica feature'a

UX Inspector odpowiada na jedno pytanie o jeden element wskazany w uruchomionej
aplikacji frontendowej. Nie dokumentuje calego widoku i nie jest trybem UI
Explorera. Oba feature'y korzystaja z neutralnego katalogu frontendu,
`integrations.gitlab.frontend`, platformy Copilota i wspolnych komponentow run
UI, ale posiadaja rozdzielne requesty, joby, prompty, polityki tools,
raporty oraz kontrakty importu i eksportu.

Pierwsze wydanie wspiera Chrome desktop, strony `http`/`https`, top-level
dokument oraz otwarty Shadow DOM dostepny dla skryptu. Nie wykonuje akcji
uzytkownika i nie nagrywa sesji, requestow sieciowych ani screenshotow.
Operator przed wskazaniem elementu wybiera `ELEMENT_CONTEXT` albo
`FORM_DIAGNOSTICS`; drugi profil zamraza dozwolone wartosci najblizszego
formularza i jego natywny stan walidacji.

## Publiczne wejscia

- `/browser-tools/install.html` generuje bookmarklet oraz samowystarczalny
  DevTools Snippet.
- `/browser-tools/demo.html` udostepnia kontrolowany, fikcyjny scenariusz CRM.
- `GET /api/ux-inspector/input-options` zwraca zarejestrowane frontendy.
- `GET /api/ux-inspector/views?systemId=...&branch=...` zwraca widoki i
  immutable source revision.
- `POST /api/ux-inspector/jobs` tworzy run i zwraca snapshot `QUEUED`.
- `GET /api/ux-inspector/jobs/{jobId}` zwraca aktualny snapshot.
- `GET /api/ux-inspector/jobs/{jobId}/export` zwraca tylko
  `tdw.ux-inspector-export/v2` dla ukonczonego wyniku.
- `POST /api/ux-inspector/imports` przyjmuje tylko ten sam kontrakt v2 z
  capture v3 i zapisuje nowy read-only wpis historii.
- `/ux-inspector` jest jedynym receiverem capture i workspace'em feature'a.

Nie istnieja aliasy pod `/tdw-inspector/**`, receiver UI Explorera, capture v1,
dummy accepted ani reczny kanal przeniesienia capture.

## Przeplyw Browser Tools

```text
badana strona
  -> bookmarklet albo DevTools Snippet
  -> Browser Tools shell w izolowanym Shadow DOM
  -> wybor profilu danych + UX Inspector selection shield + highlight
  -> jeden capture v3
  -> popup /ux-inspector#nonce=...&sourceOrigin=...
  -> READY / CAPTURE / RECEIVED przez exact-origin postMessage
  -> capture tylko w pamieci receivera
```

Launcher zawiera publiczny origin TDW, ale nie zawiera tokenu, cookies ani
klienta REST. Zdalny wariant laduje tylko statyczne `protocol.js` i
`runtime.js` z przypietego originu. Snippet zawiera ten kod inline.

Klik w trybie selekcji jest przejmowany i nie uruchamia natywnej akcji
badanej strony. Runtime zamraza jeden capture, otwiera popup i wymaga:

1. `event.origin` rownego skonfigurowanemu originowi TDW,
2. `event.source` rownego dokladnie utworzonemu oknu,
3. protokolu v3 i jednorazowego nonce,
4. potwierdzenia `captureId` w wiadomosci `RECEIVED`.

Ponowiony `READY`, obcy origin/source, niepoprawny nonce, przekroczenie czasu,
popup blocker albo COOP koncza operacje bez wyslania drugiego capture i bez
kanalu zastepczego. Po sukcesie lub bledzie listenery, payload, overlay i
referencja do okna sa usuwane.

Receiver odczytuje z fragmentu tylko `nonce` i `sourceOrigin`, po czym od razu
usuwa fragment przez `history.replaceState`. Sprawdza exact message shape,
origin badanej strony, klienta, schema/version i limit 128 KiB. Capture nie
trafia do URL, `localStorage`, `sessionStorage`, clipboardu ani analytics.

## Capture v3

Kanoniczny kontrakt ma `schema=tdw.ux-inspector-capture`, `version=3`, klienta
`TDW UX Inspector` i `featureId=ux-inspector`. Zawiera:

- origin oraz route bez wartosci query,
- tag, role, accessible name i ograniczony tekst targetu,
- `domFingerprint`: allowlistowane stabilne atrybuty, wygenerowane z nich
  selector candidates, label linkage i lancuch custom-element boundaries,
- obserwowalny stan boolean/ARIA i bounds do preview,
- maksymalnie 24 istotnych przodkow,
- informacje o truncation, Shadow DOM, ramce i redakcji,
- `captureProfile` oraz opcjonalny `formSnapshot`.

`ELEMENT_CONTEXT` nigdy nie zawiera `formSnapshot`. `FORM_DIAGNOSTICS`
odczytuje tylko najblizszy owning form, a gdy kontrolka nie nalezy do form -
tylko wskazana kontrolke. Snapshot przechowuje do 64 kontrolek, do 16 KiB na
wartosc i lacznie do 64 KiB znakow wartosci. Zachowuje `checked`, wybrane
opcje, disabled/readonly/required, `ValidityState`, validation message oraz
submittery. Kazde obciecie jest jawne.

Hasla, hidden controls, pliki oraz pola lub wartosci rozpoznane jako token,
session, secret, CSRF/JWT albo jednorazowy kod sa zawsze wykluczane. Cookies,
storage i ruch sieciowy nie sa odczytywane. Dozwolone wartosci formularza nie
przechodza redakcji e-mail/numerow stosowanej do przypadkowego tekstu DOM, bo
sa jawnym diagnostycznym evidence; pozostaja jednak niezaufanymi danymi.

Frontend receiver i backend waliduja niezaleznie ten sam zamrozony kontrakt.
Backend odrzuca nieznane pola, inna wersje i payload wiekszy niz 128 KiB przed
oraz po normalizacji. Allowlista atrybutow obejmuje tylko `id`, `name`, `type`,
`data-testid`, `data-test`, `data-cy`, `formcontrolname`, `aria-label` i
`aria-describedby`. Inline handlers, style, klasy, dowolne `data-*` i query
values nie sa przechowywane. E-mail, UUID i dlugie numery w przypadkowym
tekscie DOM sa redagowane, a tekst zawierajacy sygnal sekretu jest usuwany.

## Zaufany formularz i source selection

Capture sugeruje tylko runtime observation. System, branch, view, source
revision, pytanie, model i reasoning effort pochodza z formularza na originie
TDW. Operator jawnie wybiera frontend i branch, laduje katalog widokow oraz
potwierdza view. Zmiana systemu albo brancha usuwa poprzedni wybor i revision.

Backend waliduje aktualna pare model/effort z dynamicznym katalogiem Copilota.
Brak katalogu, usuniety model albo effort niewspierany przez model zatrzymuja
start. Pierwszy snapshot `QUEUED` musi zostac zapisany w Analysis History przed
dispatch do executora; brak persistence zatrzymuje utworzenie runu.

## Deterministyczne rozpoznanie targetu

`UxInspectorTargetResolver` rozwiazuje zarejestrowany frontend do ukrytego
GitLab scope, przypina branch do oczekiwanej rewizji i buduje screen
reachability dla wybranego view. Ranking korzysta z dokladnych wartosci
stabilnych atrybutow, selector candidates, accessible name, tekstu, tagu,
role, custom-element ancestry oraz route. Kazdy kandydat zachowuje wynik i
`matchReasons`. Dla rozpoznanego targetu resolver wyprowadza jawny
`sourceBinding`: komponent/symbol, template z rozroznieniem external/inline,
ograniczony element slice, bindingi elementu i formularza oraz symbole
referencyjne. Tylko external template otrzymuje cytowalny zakres linii;
pozycje inline template sa metadanymi wzglednymi i nie sa udawanym source ref.

Rezultat ma jeden ze stanow:

- `RESOLVED` - jeden kandydat ma wystarczajaca przewage; initial prompt dostaje
  waski template/component slice oraz mape wybranego repozytorium,
- `AMBIGUOUS` - kilku kandydatow jest porownywalnych; model musi zachowac
  niejednoznacznosc i moze skorzystac z target tools,
- `NOT_FOUND` - brak zweryfikowanego celu; provider blokuje sesje zamiast
  przeszukiwac dowolne repository.

Zmiana source revision, brak refa albo nieaktualny view sa jawnym bledem.
Feature nie przelacza sie na inny branch i nie zgaduje targetu.

## Prompt i tools

Prompt oddziela trzy poziomy zaufania: pytanie operatora,
`UNTRUSTED_RUNTIME_OBSERVATION` i `UNTRUSTED_SOURCE_EVIDENCE`. Zawiera capture,
deterministyczny `sourceBinding`, target context, focused source slice,
adaptacyjna procedure dla pytan precyzyjnych i ogolnych oraz kontrakt raportu.
Osobny logical artifact zawiera kompletny, posortowany spis nazw sciezek z
pierwszych czterech poziomow wybranego repozytorium na pinned commit. Backend
czyta wszystkie strony kazdego katalogu; nie publikuje obcietego drzewa ani
fallbacku. Drzewo sluzy tylko do nawigacji i nie jest cytowalnym dowodem.
`formSnapshot` jest niezaufana obserwacja konkretnego stanu runtime: model ma
uzyc jego wartosci lub `ValidityState` tylko wtedy, gdy sa istotne dla pytania,
nie przepisywac calego snapshotu i potwierdzac zachowanie w kodzie. UX
Inspector nie ma runtime skilli i uruchamia sesje z `skillsEnabled=false`;
built-in `skill` ani katalog skilli nie trafiaja do tej sesji. Inne feature'y
zachowuja domyslny wspolny katalog skilli.

Dla pytania precyzyjnego model pobiera tylko evidence niezbedne do odpowiedzi
na wskazany aspekt. Dla pytania ogolnego ustala cel biznesowy, pochodzenie i
zmiany danych lub stanu, warunki biznesowe i techniczne, skutek interakcji oraz
miejsce przekazania albo zapisu danych. Research moze przejsc przez frontend,
serwis, klienta i backend w tym samym repozytorium, ale rozwija tylko lancuch
materialny dla pytania i potwierdzony w source evidence. Odpowiedz pozostaje
zrozumiala biznesowo; kod jest dowodem w references, nie glownym jezykiem.

Allowlista Copilota obejmuje tylko:

- `uxi_list_target_candidates(reason)`,
- `uxi_read_target_slice(targetRef, reason)`,
- waskie frontend route/symbol slice tools,
- neutralne `gitlab_list_repository_tree`, `gitlab_list_repository_files` i
  `gitlab_search_repository_files` do nawigacji po wybranym repozytorium,
- neutralne `gitlab_read_repository_file` i
  `gitlab_read_repository_file_chunk` do potwierdzania tresci,
- `record_tool_feedback`,
- cztery platformowe report tools.

`targetRef` jest losowy, session-bound, jednorazowy i nie zawiera path ani
repository scope. Nowe feature-specific tools nie eksponuja scope'u. Neutralne
GitLab file-read tools zachowuja uniwersalne pola `projectName`, `branchRef` i
`filePath`; navigation tools korzystaja dodatkowo z bezpiecznych `path` albo
`pathPrefix`. Prompt przekazuje wybrany project i branch, a hidden
`GitLabRepositoryToolScope` przypina je do immutable commit. Feature policy
wymusza dokladnie ten project i branch, bezpieczne sciezki, brak
`applicationNames` oraz krotki powod. Nie stosuje `pathPrefixes`,
`codeSearchScopes` ani feature-specific limitu liczby wywolan. Hidden context
zawiera ponadto allowlistowane slice targets, `reportId` i jedyna dozwolona
sekcje `answer`. Inny projekt, branch, niebezpieczna sciezka i replay
referencji sa odrzucane. README, `AGENTS.md`, `.github/copilot-instructions.md`
i inne pliki sa zawsze `UNTRUSTED_SOURCE_EVIDENCE`.

## Report-first wynik

Przed sesja powstaje `AnalysisReport` z jedna pusta sekcja:

```text
id: answer
title: Odpowiedz
order: 1
```

Model ma w jednym turnie zlecic rownolegle `report_update_header`,
`report_upsert_section` i `report_update_meta`, a po ich zakonczeniu raz
wywolac `report_get_current`. Finalna wiadomosc tekstowa nie jest parsowana i
nie stanowi fallbacku.

Feature-owned mapper wymaga dokladnie jednej niepustej sekcji `answer`, headera
i `markdownSummary`, Markdown zamiast payloadu JSON oraz source references
nalezacych do initial focused context albo plikow faktycznie odczytanych przez
repo-bound full/chunk tools z tego samego projektu i pinned commit. Sama
obecnosc sciezki w drzewie, liscie albo wyniku wyszukiwania nie uprawnia do
cytowania. Odpowiedz bez referencji musi zawierac jawny evidence gap lub
visibility limit. Wynik jest projekcja raportu do `UxInspectorResultResponse`;
`PARTIAL` oznacza jawne ograniczenia, a brak poprawnego raportu konczy run
`FAILED` lub `BLOCKED`.

## Job, historia i UI

Lifecycle ma kroki `TARGET_RESOLUTION`, `AI_PREPARATION` i `AI_ANALYSIS` oraz
statusy `QUEUED`, `RESOLVING_TARGET`, `PREPARING_AI`, `ANALYZING`,
`COMPLETED`, `PARTIAL`, `BLOCKED`, `FAILED`. Snapshot zawiera request,
przypieta rewizje, deterministic/tool evidence, activity, tool feedback,
prepared prompt, usage, report i typed result.

`UxInspectorLocalRunPersister` zapisuje kazdy snapshot pod feature key
`ux-inspector`. Ekran Analysis History otwiera run przez `/ux-inspector`.
Aktywny wpis odzyskuje polling; terminalny lub importowany wynik jest
read-only. Import nie uruchamia Copilota ani nie tworzy continuation.

Workspace Angulara korzysta ze znanego ukladu UI Explorera: formularz i wynik
po lewej, `AnalysisFeatureAsideComponent` z krokami, AI activity, tool feedback
i usage po prawej. Roznica jest merytoryczna: nie ma section modes ani osmiu
sekcji, a `AnalysisReportPanelComponent` renderuje tylko `Odpowiedz`.

## Security, privacy i failure matrix

| Ryzyko | Zabezpieczenie | Zachowanie po bledzie |
|---|---|---|
| falszywy popup/message | exact origin, source, nonce i protocol v3 | capture odrzucony |
| replay READY/targetRef | state machine i jednorazowe refs | brak drugiego transferu/odczytu |
| CSP lub Local Network Access | samowystarczalny DevTools Snippet | jawny blad load; brak transfer fallbacku |
| popup blocker lub COOP | wymagany `window.opener` | jawny blad, payload usuniety |
| prompt injection z DOM/kodu | trust labels, kanoniczna procedura promptu i allowlista tools | instrukcja z evidence ignorowana |
| dane formularza/sekret | profil opt-in, najblizszy form, limity i bezwzgledne wykluczenia password/hidden/file/token/session/secret/OTP | wykluczenie widoczne w preview albo request odrzucony |
| stale source | expected immutable revision | conflict; operator laduje katalog ponownie |
| niejednoznaczny target | ranked candidates i jawny status | brak zgadywania, wynik partial/blocked |
| wyjscie poza selected repository/revision | hidden repository scope, feature policy i safe paths | tool call odrzucony |
| brak raportu | report-first validator, brak parsera tekstu | `FAILED`/`BLOCKED` |
| nieznany import | exact schema/version/result contract | `400 UX_INSPECTOR_IMPORT_INVALID` |

Logowanie moze zawierac job id, capture id, rozmiar, liczbe przodkow, status i
kody redakcji. Nie powinno zawierac raw capture, pytania ani tresci strony.

## Granice pakietow

- `features.uxinspector` posiada request/result, capture, resolver, kanoniczna
  procedure promptu, tool policy, report, job i import/export.
- `frontendcatalog` jest neutralnym katalogiem zarejestrowanych frontendow i
  widokow, uzywanym rownolegle przez UX Inspector i adapter UI Explorera.
- `integrations.gitlab.frontend` posiada graph discovery i source slices.
- `agenttools` oraz `aiplatform` pozostaja neutralne wobec feature'a.
- `features.uxinspector` i `features.uiexplorer` nie importuja siebie.
- `frontend/public/browser-tools` jest uniwersalnym shellem z rejestrem akcji;
  pierwsza akcja jest UX Inspector, ale shell moze przyjac przyszle narzedzia.

Kierunki zaleznosci sa egzekwowane przez `PackageDependencyGuardTest`.

## Weryfikacja wydania

Minimalna macierz obejmuje:

- testy obu profili capture, shape, limitow, redakcji, dozwolonych form values
  oraz bezwzglednego braku password/hidden/file/token/query values,
- co najmniej 12 fikcyjnych fixture'ow CRM dla target resolution oraz
  `AMBIGUOUS`, `NOT_FOUND` i stale revision,
- origin/source/nonce/replay/popup failure w Browser Tools i receiverze,
- model/effort, czteropoziomowe drzewo, tool scope/replay, source-ref capture i
  report validator,
- job `QUEUED` przed dispatch, import/export v2 oraz obce kontrakty,
- testy Angulara dla receivera, requestu, pollingu i braku startu bez capture,
- build Angulara, `FrontendPageTest` i pelny pakiet backend-dev.

Testy nie zastepuja kontrolowanego pilota z rzeczywistym Copilot/GitLab.
Decyzja o dystrybucji Browser Tools wymaga osobno zarejestrowanego pilota dla
czterech rodzin pytan, pomiaru trafnosci targetu, source-groundingu, czasu,
tokenow i porownania z analogicznym szerokim runem UI Explorera.
