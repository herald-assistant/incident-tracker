# Follow-up result update proposal plan

Status: completed.

Plan zostaje jako decision/implementation record dla MVP follow-up
`resultUpdate`. Nie ma osobnych backlog items do przeniesienia do
`07-open-work-plan`; przyszle rozszerzenia sa opisane w sekcji "Poza MVP".

## Cel

Ten plan zastepuje wycofane podejscie sekcyjne. Nowy model pracy jest oparty
o follow-up chat:

1. operator zadaje zwykle pytanie albo polecenie w chacie,
2. AI zawsze zwraca `message`,
3. AI moze opcjonalnie zwrocic `resultUpdate`,
4. `message` trafia do historii chatu,
5. `resultUpdate` jest tylko propozycja zmiany wyniku,
6. UI pokazuje przy odpowiedzi przycisk `Review changes`,
7. operator widzi stan `Before` i `After`,
8. dopiero `Apply` aktualizuje wynik analizy.

To nie jest osobny flow sekcji, osobny prompt sekcyjny ani osobny tryb UI.
Aktualizacja wyniku jest kontrolowanym side effectem odpowiedzi follow-up
chatu.

## Zasady realizacji

- Przed implementacja kazdego kroku opisujemy zakres i czekamy na zatwierdzenie.
- Jezeli przed krokiem pojawi sie korekta zalozen, najpierw aktualizujemy ten
  plan, a dopiero potem implementujemy.
- Po zakonczonym kroku aktualizujemy status w tym pliku.
- Nie utrzymujemy kompatybilnosci wstecznej dla roboczych kontraktow Flow
  Explorera ani wycofanego podejscia sekcyjnego.
- Nie przywracamy endpointow, DTO, promptow ani UI z wycofanego podejscia.
- Incident Analysis ma miec te mozliwosc wylaczona.
- Flow Explorer jest pierwszym feature'em, ktory dostaje result update
  proposals.
- Importowany export JSON pozostaje read-only. Kontynuowalne local runs maja
  dzialac tak jak zwykly follow-up chat.

## Decyzja produktowa

Follow-up chat ma dwa tryby odpowiedzi, ale jeden UX:

```json
{
  "message": "Odpowiedz widoczna w chacie."
}
```

albo:

```json
{
  "message": "Zaproponowalem aktualizacje wyniku. Sekcje A i B zostaly rozbudowane, C zostala bez zmian.",
  "resultUpdate": {
    "...": "partial zgodny z kontraktem initial result danego feature'a"
  }
}
```

Semantyka:

- samo `message` oznacza zwykla odpowiedz chatu,
- `message + resultUpdate` oznacza odpowiedz chatu z propozycja zmiany wyniku,
- brak pola w `resultUpdate` oznacza brak zmiany,
- brak sekcji na liscie `sections` oznacza, ze sekcja zostaje bez zmian,
- pole obecne z pusta lista oznacza jawne zastapienie wartosci pusta lista,
- `null` w patchu nie jest potrzebne w MVP i powinno byc traktowane jako brak
  wartosci albo blad walidacji feature'a,
- wynik nie zmienia sie automatycznie po odpowiedzi AI,
- tylko operator moze zaakceptowac propozycje.

## Zakres MVP

MVP obejmuje:

- JSON-only follow-up response z `message` i opcjonalnym `resultUpdate`,
- zapis `resultUpdate` przy wiadomosci assistant,
- server-side wyliczenie pelnego proponowanego wyniku do review,
- przycisk `Review changes` przy wiadomosci assistant z aktywna propozycja,
- duzy modal z zakladkami `Before` i `After`,
- brak edycji w modalu,
- `Apply` aktualizuje wynik,
- `Reject` zapisuje aktualny wynik bez aplikowania propozycji,
- blokade apply/reject podczas aktywnego follow-up chatu,
- dzialanie dla live Flow Explorer job oraz kontynuowalnego local run.

Poza MVP:

- edycja `After` w modalu,
- tekstowy diff markdown,
- wersjonowanie wielu applied resultow,
- cofanie applied update,
- laczenie konfliktowych propozycji,
- result update proposals w Incident Analysis,
- migracja starych exportow albo dawnych roboczych kontraktow.

## Ownership

### Shared

Shared warstwa moze znac opcjonalne pole `resultUpdate` przy wiadomosci chatu,
poniewaz follow-up chat, aktywna propozycja i przycisk UI sa przekrojowym
wzorcem.

Shared nie zna kontraktu wyniku danego feature'a i nie wykonuje merge'a.

Kandydat na rozszerzenie `AnalysisChatMessageResponse`:

```java
JsonNode resultUpdate
```

`resultUpdate` w odpowiedzi chatu jest pelnym proponowanym wynikiem po
nalozeniu zmiany. Jego typ semantyczny pozostaje feature-owned. Shared model
przenosi go jako JSON/obiekt dla UI, ale go nie interpretuje.

`message.content` pozostaje opisem zmiany dla operatora, wiec nie potrzebujemy
osobnego `summary`. Feature page zna swoj typ wyniku, wiec nie potrzebujemy
tez `resultType` w MVP.

`Before` nie jest przenoszony w wiadomosci chatu. UI ma aktualny `result` w
snapshotcie joba/runu i uzywa go jako stanu przed zaakceptowaniem propozycji.

Nie ma osobnego `proposalId`, bo jedna wiadomosc assistant moze miec najwyzej
jedna propozycje. Dla akcji wystarcza `messageId`.

Nie ma pola `status`, bo aktywnosc propozycji wynika z obecnosci
`resultUpdate` przy wiadomosci. Po decyzji operatora backend zapisuje aktualny
wynik i zwraca snapshot, w ktorym propozycja nie jest juz aktywna.

### Feature

Feature decyduje:

- czy result update proposals sa wlaczone,
- jaki jest kontrakt `resultUpdate`,
- jak sparsowac odpowiedz AI,
- jak zwalidowac partial update,
- jak nalozyc patch na aktualny wynik,
- jakie pola wyniku sa mutowalne.

### Incident Analysis

Incident Analysis zostaje message-only:

- prompt follow-up nie wspomina `resultUpdate`,
- parser incydentowy nie wymaga nowego formatu,
- job state moze miec pole proposal z shared DTO, ale zawsze puste,
- UI nie pokazuje `Review changes`, bo nie ma propozycji.

### Flow Explorer

Flow Explorer wlacza result update proposals.

`resultUpdate` jest partialem `FlowExplorerAiResponse`, czyli moze aktualizowac:

- `audience`,
- `overview`,
- `sections`,
- `globalVisibilityLimits`,
- `globalOpenQuestions`,
- `sourceReferences`,
- `confidence`,
- `followUpPrompts`.

Nie aktualizujemy w MVP metadanych runu:

- `systemId`,
- `endpointId`,
- `httpMethod`,
- `endpointPath`,
- `branch`,
- `goal`,
- `prompt`,
- `usage`.

Sekcje sa mergowane po `id`. Brak sekcji oznacza bez zmian. Sekcja obecna z
czescia pol aktualizuje tylko te pola. `mode` i `title` powinny pozostac
zgodne z aktualna konfiguracja sekcji; jezeli AI je pominie, backend zachowuje
stan obecny.

## Kontrakt AI dla Flow Explorera

Follow-up response dla Flow Explorera ma byc JSON-only:

```json
{
  "message": "Rozbudowalem Flow i Persistence. Security zostawilem bez zmian, bo nie znalazlem dodatkowych zrodel.",
  "resultUpdate": {
    "overview": {
      "markdown": "..."
    },
    "sections": [
      {
        "id": "FUNCTIONAL_FLOW",
        "markdown": "...",
        "sourceRefs": ["..."],
        "visibilityLimits": ["..."],
        "openQuestions": ["..."]
      }
    ],
    "globalVisibilityLimits": ["..."],
    "sourceReferences": ["..."],
    "confidence": "medium"
  }
}
```

AI powinno zwrocic `resultUpdate` tylko wtedy, gdy uzytkownik prosi o zmiane,
rozbudowe, doprecyzowanie albo przebudowe wyniku. Dla pytan wyjasniajacych,
np. "gdzie jest walidacja?", odpowiedz powinna zawierac tylko `message`.

`message` ma wyjasnic:

- co zmieniono,
- czego nie zmieniono,
- dlaczego jakas prosba nie zostala przeniesiona do wyniku,
- jakie sa ograniczenia widocznosci.

## Model stanu

Propozycja jest przypisana do konkretnej wiadomosci assistant.

Przy zakonczeniu follow-up chatu:

1. backend parsuje odpowiedz AI,
2. zapisuje `message` jako `content`,
3. jezeli AI zwrocilo poprawny partial `resultUpdate`, backend naklada go na
   aktualny wynik,
4. zapisuje pelny proponowany wynik jako `resultUpdate` przy wiadomosci
   assistant,
5. nie zmienia `result` joba/runu.

Przy `Apply`:

1. UI wysyla aktualny zaakceptowany wynik do backendu,
2. backend blokuje apply, jezeli trwa inny chat,
3. backend zapisuje przeslany wynik jako aktualny `result`,
4. backend usuwa aktywna propozycje z wiadomosci albo oznacza ja jako
   nieaktywna bez wystawiania statusu w shared response,
5. response zwraca zaktualizowany snapshot.

Przy `Reject`:

1. UI wysyla aktualny odrzucony stan wyniku do backendu, czyli zwykle
   dotychczasowy `Before`,
2. backend blokuje reject, jezeli trwa inny chat,
3. backend zapisuje przeslany wynik jako aktualny `result`,
4. backend usuwa aktywna propozycje z wiadomosci albo oznacza ja jako
   nieaktywna bez wystawiania statusu w shared response,
5. response zwraca zaktualizowany snapshot.

Po apply/reject nie zapisujemy osobnego stanu synchronizacji. Aktualny
`result` joba/runu jest jedynym zrodlem prawdy.

W ramach apply/reject backend wysyla tez techniczna wiadomosc do tej samej
sesji Copilota. Wiadomosc nie jest widoczna w chacie operatora i sluzy tylko
zsynchronizowaniu pamieci sesji:

- przy `Apply`: operator zaakceptowal `resultUpdate` z danej wiadomosci, a
  ponizszy wynik jest teraz authoritative state,
- przy `Reject`: operator odrzucil `resultUpdate` z danej wiadomosci, a
  ponizszy wynik pozostaje authoritative state.

Prompt synchronizacyjny ma prosic model tylko o krotkie `OK`. Odpowiedz `OK`
nie trafia do widocznej historii chatu. Dzieki temu kolejny follow-up nie musi
rekonstruowac decyzji z poprzednich odpowiedzi; sesja ma juz jawny nowszy fakt.

Backend nadal przy kazdym follow-up traktuje zapisany `result` jako zrodlo
prawdy aplikacji. Synchronizacja sesji ulatwia kontynuacje rozmowy, ale nie
zastepuje zapisu stanu w jobie/runie.

Na MVP nie robimy conflict detection poza blokada rownoleglego chatu. Dopoki
propozycja jest aktywna, aktualny `result` w snapshotcie jest stanem `Before`,
a `message.resultUpdate` jest stanem `After`. Jezeli w przyszlosci pojawi sie
wiele aktywnych propozycji, apply powinien sprawdzac base result hash albo
wymuszac review aktualnego `After`.

## API

Istniejace wyslanie chatu zostaje:

```http
POST /api/flow-explorer/jobs/{jobId}/chat/messages
```

Nowe akcje live Flow Explorer:

```http
POST /api/flow-explorer/jobs/{jobId}/chat/messages/{messageId}/result-update/apply
POST /api/flow-explorer/jobs/{jobId}/chat/messages/{messageId}/result-update/reject
```

Nie dodajemy osobnego endpointu do pobierania danych review. Snapshot joba ma
aktualny `result`, a wiadomosc assistant ma `resultUpdate`.

Dla local run potrzebny jest odpowiednik w shared/operator history API, ktory
deleguje do feature-owned handlera:

```http
POST /analysis/runs/{analysisId}/chat/messages/{messageId}/result-update/apply
POST /analysis/runs/{analysisId}/chat/messages/{messageId}/result-update/reject
```

Shared history API nie zna merge'a Flow Explorera. Powinno tylko odnalezc
local run, rozpoznac feature type i delegowac do handlera feature'a.

## UI MVP

Wspolny chat panel:

- wyswietla `Review changes`, gdy wiadomosc assistant ma
  aktywne `resultUpdate`,
- emituje zdarzenie do feature page zamiast samodzielnie aplikowac zmiane.

Flow Explorer page:

- trzyma stan aktywnej propozycji do review,
- otwiera duzy modal,
- pokazuje dwie zakladki `Before` i `After`,
- renderuje aktualny `job.result` jako `Before`,
- renderuje `message.resultUpdate` jako `After`,
- nie pozwala edytowac `After` w MVP,
- `Apply` wywoluje endpoint apply,
- `Reject` wywoluje endpoint reject,
- po sukcesie podmienia snapshot.

Imported export:

- moze pokazac historie chatu i aktywne propozycje, jezeli sa w pliku,
- nie pokazuje aktywnych apply/reject akcji,
- pozostaje read-only tak jak dotychczasowy import.

## Kroki wykonawcze

### Krok 1. Backend shared chat result update field

Status: completed.

Zakres:

- rozszerzyc `AnalysisChatMessageResponse` o opcjonalne `JsonNode resultUpdate`,
- shared model ma przenosic feature-owned proponowany wynik bez interpretacji,
- zaktualizowac testy serializacji/snapshotow tam, gdzie wymagane,
- incidentowe snapshoty maja zwracac `null` albo brak aktywnej propozycji.

Przed implementacja: opisac dokladny ksztalt DTO i poczekac na akceptacje.

### Krok 2. Flow Explorer parser follow-up response

Status: completed.

Zakres:

- dodac feature-local response parser dla follow-up:
  `message` + opcjonalny `resultUpdate`,
- result update walidowac jako partial zgodny z `FlowExplorerAiResponse`, ale
  zachowac jako `JsonNode`, zeby merge mogl rozroznic brak pola od pustej
  listy,
- dodac walidacje targetow sekcji i confidence,
- traktowac brak `resultUpdate` jako zwykly chat,
- dodac testy parsera dla message-only, partial update, unknown section i
  malformed JSON.

Przed implementacja: pokazac przyklady JSON akceptowane/odrzucane.

### Krok 3. Flow Explorer merge/applicator

Status: completed.

Zakres:

- dodac feature-owned komponent nakladajacy partial update na
  `FlowExplorerResultResponse.aiResponse`,
- merge sekcji po `id`,
- pola obecne zastepuja aktualne wartosci,
- pola nieobecne zostaja bez zmian,
- nie zmieniac metadanych runu,
- dodac testy merge dla overview, sekcji, list globalnych i no-op update.

Przed implementacja: zatwierdzic semantyke absent vs empty list vs null.

### Krok 4. Live Flow Explorer job state i API apply/reject

Status: completed.

Zakres:

- zapisywac aktywne `resultUpdate` przy assistant chat message,
- przechowywac przy wiadomosci pelny proponowany wynik, bez osobnego
  `beforeResult` i bez pola `afterResult`,
- apply/reject ma przyjmowac aktualny wynik przeslany z UI i zapisac go jako
  aktualny stan,
- po apply/reject wyslac do tej samej sesji Copilota techniczna wiadomosc
  synchronizacyjna z aktualnym authoritative `result` i oczekiwac `OK`,
- nie zapisywac odpowiedzi `OK` jako widocznej wiadomosci chatu,
- dodac apply/reject metody w `FlowExplorerJobState`,
- dodac endpointy apply/reject w `FlowExplorerJobController`,
- blokowac apply/reject dla braku aktywnej propozycji i aktywnego chatu,
- zwracac zaktualizowany snapshot,
- dodac testy service/controller.

Przed implementacja: zatwierdzic URL-e endpointow i kody bledow.

### Krok 5. Local run apply/reject

Status: completed.

Zakres:

- rozszerzyc local analysis run continuation o apply/reject result update,
- dodac shared/operator endpointy `/analysis/runs/.../result-update/...`,
- delegowac do feature-owned handlera Flow Explorera,
- zapisac aktualny wynik przeslany z UI w `run.json` i index metadata,
- po apply/reject wyslac do kontynuowanej sesji Copilota techniczna wiadomosc
  synchronizacyjna z aktualnym authoritative `result` i oczekiwac `OK`,
- nie zapisywac odpowiedzi `OK` jako widocznej wiadomosci chatu,
- blokowac imported/read-only runs,
- dodac testy handlera i API.

Przed implementacja: zatwierdzic, czy local run API ma byc wspolne dla
przyszlych feature'ow juz teraz, czy minimalnie wspierac tylko Flow Explorer.

### Krok 6. Prompt i skill Flow Explorera

Status: completed.

Zakres:

- zaktualizowac follow-up prompt/guidance Flow Explorera,
- opisac JSON-only response `{ message, resultUpdate? }`,
- zaznaczyc, ze `resultUpdate` jest partialem initial result contract,
- dodac prompt technicznej wiadomosci synchronizacyjnej wysylanej po
  apply/reject do tej samej sesji Copilota,
- prompt synchronizacyjny ma zawierac decyzje operatora, aktualny authoritative
  `result` i prosbe o odpowiedz tylko `OK`,
- dopilnowac, ze compact/deep sekcji pozostaje zgodne z aktualnymi mode
  assignments,
- dodac testy kontraktu promptu/skilla, jezeli istnieja dla runtime skills.

Przed implementacja: pokazac finalna instrukcje dla AI do zatwierdzenia.

### Krok 7. Frontend models i chat button

Status: completed.

Zakres:

- rozszerzyc frontendowy `AnalysisChatMessageResponse` o opcjonalne
  `resultUpdate`,
- zachowac `resultUpdate` w frontendowej normalizacji importu/exportu, zeby
  aktywna propozycja nie ginela z historii chatu,
- pokazac `Review changes` przy aktywnej propozycji,
- przekazac event do Flow Explorer page,
- nie pokazywac przycisku dla incident analysis bez `resultUpdate`.

Przed implementacja: zatwierdzic copy przycisku i komunikatow.

### Krok 8. Flow Explorer review modal

Status: completed.

Zakres:

- dodac duzy modal review changes,
- zakladki `Before` i `After`,
- renderowac pelny wynik Flow Explorera w obu zakladkach,
- brak edycji w MVP,
- akcje `Apply`, `Reject`, `Close`,
- obsluga loading/error,
- apply/reject wywoluja live albo local-run endpoint zależnie od pochodzenia
  analizy,
- imported export pokazuje modal read-only bez akcji apply/reject,
- testy komponentu/page.

Przed implementacja: zatwierdzic layout modala i nazwy akcji.

### Krok 9. Export/import i read-only semantics

Status: completed.

Zakres:

- upewnic sie, ze review modala poprawnie dziala dla aktywnych propozycji z
  importu/exportu,
- imported result pozostaje read-only i nie pozwala apply/reject,
- testy import/export.

Przed implementacja: zatwierdzic, czy imported read-only ma pokazywac modal
review dla historycznych aktywnych `resultUpdate` bez akcji apply/reject.

### Krok 10. Dokumentacja i sprzatanie

Status: completed.

Zakres:

- zaktualizowac `02-key-decisions.md` albo `03-runtime-flow.md`, jezeli zmieni
  sie trwaly kontrakt follow-up chatu,
- usunac ewentualne slady wycofanej nazwy UI/API,
- uruchomic targeted backend/frontend tests,
- po zakonczeniu przeniesc resztki backlogu do `07-open-work-plan` albo
  zamknac ten plan.

Przed implementacja: zatwierdzic zakres dokumentacji koncowej.

## Kryteria akceptacji MVP

- Follow-up chat bez `resultUpdate` dziala jak dotychczas.
- Follow-up chat z `resultUpdate` nie zmienia wyniku automatycznie.
- Przy aktywnej propozycji UI pokazuje `Review changes`.
- Modal pokazuje pelny wynik `Before` i `After`.
- `Apply` aktualizuje Flow Explorer result.
- `Reject` nie aktualizuje wyniku.
- Incident Analysis nie pokazuje result update proposals.
- Live Flow Explorer job i local run zachowuja ten sam UX.
- Importowany export pozostaje read-only.
- W kodzie i dokumentacji nie wraca wycofane podejscie sekcyjne jako funkcja
  UI/API.
