# Flow Explorer Section Refine Plan

Ten dokument opisuje plan MVP dla akcji `Refine` w Flow Explorerze. Celem
jest pozwolic uzytkownikowi poglebic konkretna sekcje wyniku bez
przegenerowywania calej analizy i bez rozjechania stanu UI, chatu oraz sesji
Copilota.

## Cel

Flow Explorer czesto trafia na zlozona implementacje endpointu. Initial result
ma pozostac samowystarczalny, ale uzytkownik po przeczytaniu wyniku powinien
moc poprosic o waskie doprecyzowanie konkretnego obszaru:

- `FUNCTIONAL_FLOW`,
- `VALIDATIONS`,
- `PERSISTENCE`,
- `INTEGRATIONS`.

`Overview` nie dostaje akcji `Refine` w MVP. Overview jest syntetycznym
podsumowaniem calego wyniku, a nie miejscem na celowe doczytywanie zrodel.
Jezeli doprecyzowanie sekcji zmienia syntetyczny obraz endpointu, aktualizacja
Overview moze byc osobna decyzja produktowa pozniej.

## Zasady MVP

- `Refine` jest akcja na aktywnej sekcji wyniku, nie nowym initial runem.
- Sekcja musi istniec w `result.aiResponse.sections`; sekcje `OFF` i `Overview`
  sa odrzucane.
- Tryb sekcji pozostaje taki jak w aktualnym wyniku: `compact` albo `deep`.
  `Refine` nie jest przelacznikiem `COMPACT -> DEEP`.
- Wynik refine ma byc zgodny z zasadami odpowiedniej sekcji ze skilla
  `flow-explorer-result-contract` oraz goal-specific skilla, zeby `compact`
  nadal bylo zwarte, a `deep` zawieralo wymagane szczegoly.
- Nie wersjonujemy sekcji w MVP. Gwarantujemy tylko jedna aktywna prace AI na
  job/sesje, wiec nie ma konfliktu rownoleglych zapisow.
- Chat i `Refine` korzystaja z tej samej blokady aktywnej odpowiedzi. Jezeli
  assistant jest `IN_PROGRESS`, kolejny chat albo refine zwraca blad
  `FLOW_EXPLORER_CHAT_IN_PROGRESS` albo jego refine odpowiednik.
- Canonical source of truth po udanym refine to `FlowExplorerResultResponse` w
  job state. Chat jest historia pracy nad tym stanem.

## UX

Na kazdej aktywnej sekcji poza Overview UI pokazuje przycisk `Refine`.
Przycisk jest disabled, gdy:

- job nie ma statusu `COMPLETED`,
- wynik nie zawiera tej sekcji,
- trwa odpowiedz chatu albo inny refine,
- brakuje `copilotSessionId`.

Po kliknieciu otwiera sie modal z textarea:

- tytul: `Refine <section title>`,
- pole: prosba uzytkownika, np. "Doprecyzuj integracje X" albo "Opisz bardzej biznesowo część Z" ,
- submit uruchamia refine i zamyka albo blokuje modal do zakonczenia.

Po udanym refine UI:

- podmienia `markdown`, `sourceRefs`, `visibilityLimits` i `openQuestions`
  wskazanej sekcji,
- aktualizuje globalne `globalVisibilityLimits`, `globalOpenQuestions`,
  `sourceReferences` i opcjonalnie `followUpPrompts`,
- dopisuje w chacie specjalny turn z prosba uzytkownika i `changeSummary`,
- nie wkleja pelnego zaktualizowanego markdowna do chatu, bo jest widoczny w
  sekcji.

## API

Preferowany endpoint MVP:

```text
POST /api/flow-explorer/jobs/{jobId}/sections/{sectionId}/refine
```

Request:

```json
{
  "message": "Doprecyzuj, ktore pola trafiaja do tabeli i skad pochodza."
}
```

Walidacje:

- `message` ma te same ograniczenia co follow-up chat, np. max 4000 znakow,
- `sectionId` musi byc jednym z `FUNCTIONAL_FLOW`, `VALIDATIONS`,
  `PERSISTENCE`, `INTEGRATIONS`,
- job musi byc `COMPLETED`,
- wynik musi zawierac dana sekcje,
- sesja Copilota musi istniec,
- nie moze trwac inna odpowiedz AI.

Endpoint zwraca aktualny `FlowExplorerJobStateSnapshot`, tak jak chat. Dzieki
temu frontend moze dalej uzywac pollingu/projekcji stanu joba.

## Chat Jako Historia Refine

Refine powinien byc widoczny w historii chatu jako specjalny turn, ale bez
duplikowania pelnego wyniku.

Minimalna reprezentacja uzytkownika:

```text
Refine PERSISTENCE:
Doprecyzuj, ktore pola trafiaja do tabeli i skad pochodza.
```

Minimalna reprezentacja odpowiedzi:

```text
Refined PERSISTENCE

Change summary:
- Dodano mapowanie kolumn i zrodel danych.
- Doprecyzowano, ktore wartosci sa GENERATED, REQUEST i CALCULATED.
- Zostawiono limit widocznosci dla pola bez potwierdzonego zrodla.
```

Docelowo warto rozroznic typy wiadomosci strukturalnie:

- `CHAT`,
- `SECTION_REFINE_REQUEST`,
- `SECTION_REFINE_RESULT`.

W MVP sa dwie opcje:

- dodac neutralne opcjonalne `metadata`/`kind` do shared chat projection,
- albo przechowac specjalny turn jako zwykly tekst i dopiero w kolejnym kroku
  rozszerzyc kontrakt UI.

Nie nalezy dodawac Flow-Explorer-specific enumow do neutralnego
`shared.ai.AnalysisChatMessageResponse`, chyba ze zostana opakowane w
feature-local projection.

## Prompt Refine

Refine korzysta z tej samej sesji Copilota i tych samych allowed tools co
follow-up chat, ale ma osobny prompt preparation. Prompt powinien zawierac:

- initial request: system, endpoint, branch, goal, focusAreas,
  `sectionModes`, reasoningEffort,
- aktualny canonical result po wszystkich poprzednich chat/refine,
- wskazana sekcje jako target: `id`, `title`, `mode`, `markdown`,
  `sourceRefs`, `visibilityLimits`, `openQuestions`,
- aktualne `globalVisibilityLimits`, `globalOpenQuestions`,
  `sourceReferences` i `followUpPrompts`,
- poprzednie tool evidence z initial runu i chat/refine,
- chat history jako historia rozmowy, ale z jasna zasada, ze aktualny
  `FlowExplorerResultResponse` jest canonical state,
- artefakty Flow Explorera: `canonical-tool-inputs.md`,
  `compact-flow-manifest.md`, `snippet-cards.md`,
  `openapi-endpoint-contract.md` jezeli istnieje,
- instrukcje uzycia tools analogiczne do follow-up promptu,
- jawna instrukcje: wynik sekcji musi pozostac zgodny ze skillem
  `flow-explorer-result-contract` i trybem `compact`/`deep` z target section.

Wazna regula dla kompatybilnosci sesji:

```text
W historii sesji moga istniec starsze wersje tej sekcji. Aktualny canonical
stan sekcji jest w bloku "Current target section" i to on ma pierwszenstwo.
Po refine zwroc nowy stan tej sekcji; nie traktuj starszego markdowna z chatu
jako rownorzednego zrodla prawdy.
```

## Response Contract Refine

Nie wymagamy od modelu ponownego zwrocenia calego
`flow-explorer-result-contract`, bo to zwieksza ryzyko przypadkowej zmiany
Overview albo innych sekcji. Parser refine powinien oczekiwac mniejszego JSON-a
i dopiero backend scala go z aktualnym wynikiem.

Proponowany kontrakt:

```json
{
  "section": {
    "id": "PERSISTENCE",
    "title": "Persistence",
    "mode": "deep",
    "markdown": "string",
    "sourceRefs": ["string"],
    "visibilityLimits": ["string"],
    "openQuestions": ["string"]
  },
  "globalVisibilityLimits": ["string"],
  "globalOpenQuestions": ["string"],
  "sourceReferences": ["string"],
  "followUpPrompts": ["string"],
  "confidence": "high|medium|low",
  "changeSummary": ["string"]
}
```

Zasady scalania:

- `section.id` musi byc rowne target section.
- `section.mode` musi byc rowne aktualnemu trybowi target section.
- Backend podmienia tylko wskazana sekcje.
- Inne sekcje i Overview zostaja bez zmian.
- `globalVisibilityLimits`, `globalOpenQuestions`, `sourceReferences` i
  `followUpPrompts` sa traktowane jako nowy scalony stan, nie append-only.
- AI dostaje poprzednie wartosci i ma zachowac aktualne, usunac nieaktualne,
  dodac nowe oraz nie duplikowac pozycji.
- `confidence` moze zaktualizowac top-level confidence tylko wtedy, gdy refine
  realnie zmienia pewnosc calego wyniku; w przeciwnym razie backend moze
  zachowac poprzednia wartosc.
- `changeSummary` trafia do wiadomosci chatu i nie jest czescia finalnej sekcji.

Parser powinien odrzucic odpowiedz, ktora:

- nie jest JSON-em,
- zwraca inny `section.id`,
- zmienia `mode`,
- probuje zwrocic albo zmienic `Overview`,
- zawiera sekcje spoza aktywnych `sectionModes`.

## State Update

W `FlowExplorerJobState` potrzebna jest operacja podobna do `startChatMessage`,
ale targetowana na sekcje:

1. Sprawdz status joba, sesje Copilota i aktywna odpowiedz.
2. Pobierz aktualna sekcje z `result.aiResponse.sections`.
3. Dopisz user chat message jako refine request.
4. Dopisz assistant message `IN_PROGRESS`.
5. Zbuduj `FlowExplorerSectionRefineRequest`.
6. Uruchom Copilot follow-up w tej samej sesji.
7. Zparsuj `FlowExplorerSectionRefineResponse`.
8. Podmien sekcje i scal globalne pola wyniku.
9. Oznacz assistant message jako `COMPLETED` z `changeSummary`.
10. Zachowaj tool evidence i activity events przy tej assistant message.

Po kroku 8 kazdy kolejny chat albo refine widzi aktualny wynik, bo follow-up
prompt renderuje `result` ze stanu joba.

## Runtime I Tools

Refine nie dostaje nowych tools. Uzywa tych samych capability co Flow Explorer
follow-up:

- GitLab tools dla konkretnego kodu, metody, walidatora, mappera, repository,
  klienta integracji albo outline,
- operational context tools dla nazw domenowych, ownership, handoffu,
  glossary i code-search scope,
- bez DB tools w MVP.

Prompt ma przypominac:

- najpierw uzyj initial result, current section, snippet cards i manifestu,
- tool call wykonaj tylko dla konkretnego braku z prosby uzytkownika,
- `reasoningEffort` steruje glebokoscia dodatkowego czytania,
- dla sekcji `COMPACT` odpowiedz ma zostac zwarta,
- dla sekcji `DEEP` trzeba respektowac deep contract, np.
  `PERSISTENCE=DEEP` ma zachowac tabele `TABLE_NAME | COLUMN | SOURCE |
  SOURCE DETAILS`.

## Frontend

Zmiany UI:

- dodac `Refine` button w headerze kazdej aktywnej sekcji poza Overview,
- dodac modal z textarea i submit/cancel,
- pokazywac busy state na sekcji oraz globalnie w chacie,
- blokowac chat input, gdy refine jest w toku,
- po snapshot update odswiezyc sekcje z `result.aiResponse.sections`,
- w chacie pokazac specjalny wpis `Refined <section title>` i
  `changeSummary`.

Nie trzeba dodawac historii wersji sekcji w MVP. Jezeli uzytkownik chce
porownac zmiane, ma minimalny slad w `changeSummary` i tool evidence
wiadomosci refine.

## Testy

Backend:

- controller odrzuca `Overview` i nieznany `sectionId`,
- controller odrzuca refine dla sekcji `OFF` albo nieobecnej w wyniku,
- state blokuje refine, gdy assistant chat/refine jest `IN_PROGRESS`,
- prompt zawiera current target section, poprzednie limits/questions/refs,
  `sectionModes`, reasoningEffort i instrukcje zgodnosci ze
  `flow-explorer-result-contract`,
- parser przyjmuje poprawny refine JSON,
- parser odrzuca zmiane `section.id` albo `mode`,
- state podmienia tylko target section i zachowuje inne sekcje oraz Overview,
- state aktualizuje global limits/questions/sourceReferences jako scalony stan,
- assistant chat message dostaje `changeSummary` i tool evidence.

Frontend:

- `Refine` widoczny tylko przy aktywnych sekcjach poza Overview,
- button disabled podczas aktywnej odpowiedzi,
- submit wysyla `sectionId` i `message`,
- po odpowiedzi UI pokazuje nowy markdown sekcji i wpis w chacie,
- blad refine nie podmienia sekcji.

## Kolejnosc Implementacji

1. Dodac backend request/response dla section refine oraz parser maly JSON.
2. Dodac `FlowExplorerSectionRefinePromptPreparationService`.
3. Rozszerzyc `FlowExplorerJobState` o start/complete/fail refine i aktualizacje
   wyniku.
4. Dodac endpoint w `FlowExplorerJobController`.
5. Dodac testy backendowe dla walidacji, promptu, parsera i state update.
6. Dodac UI button, modal, service call i rendering chat summary.
7. Dodac testy frontendu.
8. Dopiero po MVP zdecydowac, czy chat messages dostaja neutralne `kind` /
   `metadata` w shared projection.

## Decyzje Odlozone

- Czy `Refine` moze opcjonalnie promowac sekcje z `COMPACT` do `DEEP`.
- Czy Overview powinno byc automatycznie regenerowane po refine sekcji.
- Czy trzymac historie wersji sekcji i funkcje undo.
- Czy `changeSummary` powinno byc strukturalnym polem API chatu, czy tylko
  trescia assistant message.
- Czy source refs powinny przejsc na strukturalny kontrakt zanim refine stanie
  sie szerzej uzywany.
