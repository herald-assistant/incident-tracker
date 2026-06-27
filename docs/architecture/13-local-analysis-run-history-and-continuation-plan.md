# Local Analysis Run History and Continuation Plan

## Status

Plan roboczy do realizacji krok po kroku.

Zasada pracy:

- przed implementacja kazdego kroku agent prezentuje zakres kroku,
- uzytkownik modyfikuje albo zatwierdza krok,
- po zmianie decyzji agent aktualizuje ten plik,
- dopiero zatwierdzony krok jest implementowany,
- po implementacji kroku status w tym pliku jest aktualizowany.

Legenda statusow:

- `[ ]` do zrobienia,
- `[~]` w trakcie,
- `[x]` zakonczone,
- `[!]` zablokowane albo wymaga decyzji.

## Cel

Dodac lokalny model pracy z analizami uruchamianymi z aplikacji jako JAR na
komputerze operatora:

1. aplikacja zapisuje lokalne runy analiz w katalogu danych,
2. uzytkownik widzi poprzednie sesje w ekranie historii,
3. lokalny run moze byc kontynuowany przez follow-up chat,
4. follow-up ma jedna sciezke wykonania: kontynuuje techniczna sesje Copilot
   SDK przez zapisany `copilotSessionId`,
5. export/import pozostaje read-only mechanizmem dzielenia sie wynikiem i nie
   przenosi metadanych kontynuacji.

## Decyzje Produktowe

- Lokalny katalog danych jest zrodlem prawdy dla historii i kontynuacji pracy.
- Usuniecie katalogu danych usuwa historie oraz mozliwosc kontynuacji lokalnych
  runow.
- Importowany export JSON jest ladowany tylko do UI do podgladu. Backend nie
  zapisuje go jako lokalnego runu i nie pozwala go kontynuowac.
- Export jest sanitizowana projekcja lokalnego runu. Zawiera tylko pola
  potrzebne do podgladu/audytu wyniku.
- Import/export nie utrzymuje kompatybilnosci wstecznej w V1. UI i backend
  akceptuja aktualny kontrakt `tdw.analysis-export` w aktualnej wersji; stare
  schematy, starsze wersje envelope i surowe snapshoty joba bez envelope sa
  odrzucane.
- Pelny backup albo przeniesienie kontynuowalnego workspace'u oznacza
  skopiowanie calego katalogu danych, nie zwykly export JSON.
- V1 lokalnego store jest wlaczone domyslnie w lokalnej dystrybucji JAR.
- V1 zapisuje tylko zakonczone initial runy oraz kazda zakonczona odpowiedz w
  follow-up chat. Nie zapisujemy roboczych, czesciowych ani nieudanych
  przebiegow jako lokalnych runow.
- Tokeny dostepowe wpisane przez uzytkownika w UI moga byc zapisane lokalnie w
  katalogu workspace'u, zeby aplikacja po restarcie mogla kontynuowac prace bez
  ponownego wpisywania tokenow.
- Copilot SDK jest podstawowym mechanizmem dlugiej rozmowy: daje identyfikator
  sesji, utrzymanie historii konwersacji po stronie runtime, `resumeSession`,
  `infiniteSessions`/background compaction oraz eventy usage, truncation,
  compaction i resume.
- `copilotSessionId` zapisany w lokalnym runie jest lokalnym uchwytem do
  kontynuacji tej samej technicznej sesji SDK. Nie trafia do exportu i nie jest
  identyfikatorem biznesowym analizy.
- Follow-up nie ma fallbacku. Jesli jego wybrana sciezka wykonania nie dziala,
  odpowiedz follow-up konczy sie bledem, a uzytkownik moze recznie uruchomic
  nowa initial analysis.
- Uzytkownik nie ma myslec o oknie kontekstu, kompaktowaniu ani limitach
  follow-upu. Dlugie konwersacje i wielomilionowe przebiegi tokenow sa
  oczekiwane, szczegolnie dla ciezkich analiz typu flow explorer.
- Platforma ma automatycznie zarzadzac kontekstem przede wszystkim przez
  mechanizmy SDK dla dlugiej pojedynczej sesji. Lokalny `run.json` jest
  zrodlem prawdy dla UI/audytu i uchwytem do sesji, a nie materialem do
  recznego replayu calej rozmowy przy kazdym follow-upie.
- Kazdy turn AI, zarowno initial analysis jak i follow-up chat, powinien miec
  spojna prezentacje wykonania w UI: koszt/usage, model, reasoning effort,
  eventy sesji, tool calls, dociagniete evidence/materialy oraz ograniczenia
  widocznosci.

## Decyzje Copilot SDK i AI Gateway

Jesli lokalny kod Java SDK, bytecode albo publiczne API nie wyjasnia dokladnie
semantyki opcji, nie projektujemy zachowania na podstawie samych nazw metod.
Obowiazkowo trzeba sprawdzic upstream GitHub Copilot SDK, szczegolnie:

- `https://github.com/github/copilot-sdk`
- `https://github.com/github/copilot-sdk/blob/main/nodejs/README.md`
- schemat/protokol pakietu npm `@github/copilot`, z ktorego generowane sa
  kontrakty runtime.

Java SDK jest wrapperem nad Copilot CLI, dlatego czesc praktycznych informacji
o efektywnym i bezpiecznym uzyciu SDK moze byc opisana lepiej w dokumentacji
Node/CLI niz w klasach Javy. Dla `infiniteSessions` wlasnie tam potwierdzono,
ze mechanizm jest domyslnie wlaczony, uzywa workspace
`~/.copilot/session-state/{sessionId}`, startuje background compaction przy
`backgroundCompactionThreshold=0.8` i blokuje na domkniecie kompaktowania przy
`bufferExhaustionThreshold=0.95`.

Copilot SDK daje nam mechanike, ktorej nie powinnismy duplikowac w aplikacji:

- tworzenie i wznawianie sesji przez `createSession` / `resumeSession`,
- utrzymanie historii i kontekstu rozmowy w jednej sesji,
- `InfiniteSessionConfig` dla automatycznego zarzadzania bardzo dlugim
  kontekstem,
- eventy pozwalajace pokazac w UI usage, truncation, compaction i resume,
- runtime tools, hooks i permission callbacks podpinane do sesji.

SDK nie zwalnia aplikacji z przekazywania aktualnej konfiguracji runtime przy
utworzeniu albo wznowieniu sesji. Callbacki Javy, allowlista tools, hidden tool
context, hooks, skille, permission handler, model i reasoning effort sa
konfiguracja naszego procesu oraz feature'a. Dlatego na kazdym `createSession`
i `resumeSession` platforma musi zbindowac pelny aktualny runtime config.

AI platform gateway ma miec jedno neutralne publiczne API wykonania turnu w
sesji, zamiast osobnych metod `initial` i `follow-up`. Roboczy kontrakt:

```text
CopilotTurnRequest
- sessionTarget: NEW | EXISTING(copilotSessionId)
- prompt: string
- runtimeConfig: tools, tool policy, hidden context, skills, model,
  reasoning effort, hooks, evidence/activity sinks

CopilotTurnResult
- assistantText
- sessionId
- usage/cost
- activity/events
- user-facing tool evidence
```

Semantyka `initial` oraz `follow-up` zostaje w feature'ze:

- incident initial analysis buduje pelny prompt z deterministic evidence,
- flow explorer initial analysis zbuduje prompt z endpointu, celu i evidence,
- follow-up dla lokalnego runu wybiera `EXISTING(copilotSessionId)` i przekazuje
  tylko tresc wiadomosci uzytkownika,
- feature decyduje o kontrakcie odpowiedzi i parserze wyniku.

Dlaczego tak:

- platforma AI pozostaje reusable dla incident analysis, flow explorer i
  kolejnych feature'ow,
- nie duplikujemy sciezek `initial/follow-up` w gatewayu,
- dluga rozmowa korzysta z tego, co SDK juz utrzymuje w sesji,
- feature nadal kontroluje prompt, polityke tools, hidden context i kontrakt
  odpowiedzi,
- UI moze pokazac ten sam model pracy AI dla initial i follow-up: co kosztowalo
  dany turn, jakie narzedzia zostaly wywolane, co zostalo dociagniete i na
  jakim reasoning effort/modelu pracowal runtime,
- brak fallbacku jest jawny: jesli `EXISTING(copilotSessionId)` nie moze byc
  wykonany albo wznowiony, follow-up konczy sie bledem zamiast po cichu
  startowac inna sesje lub odtwarzac prompt z lokalnego snapshotu.

## Granice i Niezmienniki

- Nie przenosimy incident-specific promptow, coverage ani policy do
  `aiplatform`.
- Lokalny katalog runow ma byc wspolnym mechanizmem platformowym dla feature'ow,
  ale pierwszym klientem jest `features.incidentanalysis`.
- Export/import nie moze ujawniac hidden tool context, tokenow, lokalnych
  sciezek, `copilotSessionId`, auth markerow ani runtime continuation metadata.
- `index.json` nie jest magazynem sekretow. Moze zawierac co najwyzej
  referencje typu `authPrincipalRef`, a sam material tokenow powinien byc
  zapisany w osobnym lokalnym pliku `tokens.json`.
- `gitLabGroup` pozostaje konfiguracja aplikacji albo lokalna metadana
  kontynuacji, a nie publiczne pole exportu.
- Follow-up publiczny nie przyjmuje recznego `environment`, branch,
  `gitLabGroup`, DB scope ani nowego `correlationId`.
- Hidden tool context nadal ma sens jako walidacja integralnosci runu i
  scope'u tools. Dla `sessionTarget=EXISTING` `copilotSessionId` w hidden
  context musi odpowiadac rzeczywistej wznawianej sesji SDK, a scope
  GitLab/DB/operational context dalej pochodzi z konfiguracji aplikacji,
  evidence i lokalnych metadanych, nigdy z model-facing inputu.
- UI moze pokazywac reasoning effort, model, usage, koszt, eventy i publiczne
  activity/tool evidence. Nie pokazujemy ukrytego toku rozumowania modelu jako
  reasoning content.

## Docelowy Model Danych

### Local Analysis Run Record

V1 lokalnego rekordu runu ma byc absolutnym minimum: opakowuje obecny export
jako jeden obiekt i doklada tylko najmniejszy lokalny blok kontynuacji. Dane,
ktore juz sa w export envelope, nie sa przepisywane drugi raz do pol
top-level, zeby snapshot UI, export i local store nie mogly sie rozjechac.

```text
LocalAnalysisRunRecord
- schema: "tdw.local-analysis-run"
- version: 1
- exportEnvelope: AnalysisExportEnvelope
- continuation
```

`exportEnvelope` jest dokladnie tym ksztaltem, ktory obecnie eksportujemy do
pliku JSON. Dla V1 ekran historii i szczegoly lokalnego runu czytaja dane
prezentacyjne przez `exportEnvelope.payload.job`.

`continuation` jest tylko lokalne i zawiera tylko pola, ktorych nie ma w
exporcie, a sa potrzebne do kontynuacji:

```text
continuation
- enabled
- gitLabGroup
- authMode
- authPrincipalRef
- copilotSessionId
- copilotRuntime
```

`authPrincipalRef` wskazuje wpis w lokalnym `tokens.json`, jesli uzytkownik
zdecydowal sie zapamietac tokeny w UI. Sam token nie jest polem runu ani
exportu.

`copilotSessionId` wskazuje ostatnia zakonczona techniczna sesje SDK dla
danego lokalnego runu. `copilotRuntime` pozwala jawnie odroznic runtime, np.
`github-copilot-sdk`, bez ujawniania tego w exporcie.

Na start nie dodajemy `runtime`, `storage`, `displaySnapshot`,
`title/searchText`, `analysisId`, `status`, `createdAt` ani innych pol
top-level, jesli mozna je odczytac z `exportEnvelope`. Jesli w kolejnych
krokach realnie bedzie potrzebne pole indeksujace albo runtime metadata,
dodamy je wersjonowana migracja.

### Export Envelope

Export pozostaje read-only:

```text
AnalysisExportEnvelope
- schema: "tdw.analysis-export"
- version
- exportedAt
- payload.type: "analysis-job"
- payload.job: AnalysisJobStateSnapshot
```

W V1 export lokalnego runu jest zwroceniem `exportEnvelope` z rekordu.
Lokalne `continuation` nie jest czescia exportu i nie jest dopisywane do
pliku dzielonego z innymi uzytkownikami.

## Docelowy Katalog Danych

Dla V1 zakladamy dystrybucje lokalna: skrypt uruchamiajacy lezy w tym samym
katalogu co plik JAR i przekazuje aplikacji katalog danych obok JAR-a,
domyslnie `tdw-data/`. Backend powinien nadal dostawac te sciezke jako property
albo zmienna srodowiskowa, zamiast samodzielnie zgadywac polozenie JAR-a. To
upraszcza uruchamianie z IDE, testy i przyszle warianty instalacji.

Proponowany layout:

```text
tdw-data/
  index.json
  tokens.json
  runs/
    <analysisId>/
      run.json
```

`index.json` sluzy do szybkiej listy historii i jest lekkim read modelem dla UI.
Na starcie aplikacji i przy otwieraniu ekranu historii powinno wystarczyc
odczytanie samego `index.json`; konkretne `run.json` jest ladowane dopiero po
otwarciu szczegolow albo kontynuacji wybranego runu. `run.json` pozostaje
zrodlem prawdy dla pelnego snapshotu. Pliki pomocnicze moga byc wydzielone
pozniej, jesli pojedynczy JSON stanie sie zbyt duzy albo pojawi sie realna
potrzeba trzymania artifacts poza exportem.

Minimalny wpis w `index.json` ma byc prosty: techniczne `analysisId`,
`schema`, `version` i `runPath` oraz UI-owe `feature`, `name`, `createdAt`,
`updatedAt`, `completedAt`. `name` jest zwyklym stringiem tworzonym przez
feature przy initial runie i edytowalnym z poziomu UI. Dla incident analysis
wartoscia startowa jest `correlationId`; dla flow explorer moze to byc endpoint
path + goal. Indeks nie przechowuje statusu, promptu, evidence, usage,
environment, branch ani miniatury wyniku.

`tokens.json` jest lokalnym store access tokenow zapisanych z UI. Lezy obok
`index.json`, nie jest czescia exportu, nie jest importowany do trybu read-only
i nie powinien byc czytany na potrzeby zwyklej listy historii.

## API Docelowe

Shared/operator API historii powinno byc neutralne wzgledem feature'a:

```http
GET /analysis/runs
GET /analysis/runs/{analysisId}
GET /analysis/runs/{analysisId}/export
PATCH /analysis/runs/{analysisId}/name
POST /analysis/runs/{analysisId}/chat/messages
DELETE /analysis/runs/{analysisId}
```

Uwagi:

- `GET /analysis/jobs/{analysisId}` moze zostac dla live pollingu.
- `GET /analysis/runs/{analysisId}/export` zwraca tylko zapisany
  `exportEnvelope`, bez lokalnego `continuation`, bez `index.json` metadata i
  bez technicznych uchwytow kontynuacji.
- Docelowo live job po utworzeniu powinien miec odpowiadajacy rekord lokalny,
  zeby historia i polling nie byly dwoma swiatami.
- `POST /analysis/runs/{analysisId}/chat/messages` kontynuuje tylko lokalne
  runy z `continuation.enabled=true`.
- Importowany export z UI nie tworzy rekordu lokalnego i nie pojawia sie w
  `GET /analysis/runs`, chyba ze kiedys powstanie osobna akcja "zapisz kopie
  jako lokalny, bez kontynuacji".

## Ekran Historii

Nowy ekran UI, roboczo `Analysis History`, jest przekrojowym launcherem
lokalnych runow wszystkich feature'ow, a nie osobnym ekranem merytorycznych
szczegolow analizy.

Lista powinna pokazywac:

- feature,
- nazwe,
- daty utworzenia, aktualizacji i zakonczenia.

Akcje:

- otworz,
- zmien nazwe,
- exportuj,
- usun z lokalnego katalogu danych.

Klikniecie lokalnego runu otwiera ekran wlasciwego feature'a z parametrem
lokalnego runu, np. `Incident Analysis` albo `Flow Explorer`. To ekran feature'a
odtwarza wynik tak jak importowany snapshot, ale z trybem `local`, ktory moze
uruchamiac kontynuacje pracy. Historia nie renderuje uniwersalnego detailu,
zeby nie wymuszac incident-specific kontraktu na kolejnych feature'ach.

Widok szczegolow w ekranie feature'a powinien reuse'owac obecne komponenty
wyniku, evidence, AI activity, tool evidence i follow-up chat. Tryb widoku
wynika ze zrodla:

- `live`: polling joba,
- `local`: odczyt z lokalnego store i mozliwy follow-up,
- `imported`: tylko podglad w UI.

Docelowo follow-up chat nie jest tylko lista wiadomosci. Kazda odpowiedz
assistant powinna miec taki sam zwijalny execution trace jak initial analysis:
usage/koszt, model, reasoning effort, activity events, tool evidence,
dociagniete materialy i ograniczenia widocznosci. Dzieki temu operator widzi
koszt oraz podstawe kazdego turnu bez uczenia sie osobnego UI dla follow-upu.

## Plan Realizacji

### [x] 001. Zatwierdzenie kontraktu lokalnego runu

Cel:

- ustalic minimalny `LocalAnalysisRunRecord` dla pierwszej wersji.

Zakres:

- zdefiniowac minimalny shape: `schema`, `version`, `exportEnvelope`,
  `continuation`,
- zdecydowac, ktore pola sa obowiazkowe dla incident analysis,
- potwierdzic, ze na start rekord jest jednym plikiem JSON bez duplikowania
  danych z exportu.

Zatwierdzone:

- nazwa schema `tdw.local-analysis-run`,
- wersja poczatkowa `1`,
- nazwa katalogu danych `tdw-data` obok skryptu/JAR-a,
- techniczna nazwa ustawienia, ktorym launcher przekazuje te sciezke
  backendowi: `tdw.workspace.directory`,
- minimalne `continuation` zawiera `enabled`, `gitLabGroup`, `authMode` i
  `authPrincipalRef`,
- `exportEnvelope` jest 1:1 tym, co obecnie zapisujemy przez export,
- tokeny z UI moga byc zapisywane lokalnie w `tokens.json`, a run i index
  trzymaja tylko referencje.

Kryteria akceptacji:

- plan zawiera finalny shape V1,
- wiadomo, ze export lokalnego runu zwraca `exportEnvelope` bez lokalnego
  `continuation`,
- wiadomo, ktore pola sa potrzebne do follow-up po restarcie aplikacji.

### [x] 002. Backend filesystem store dla lokalnych runow

Cel:

- dodac neutralny mechanizm zapisu/odczytu lokalnych runow.

Zakres:

- nowy shared/operator package dla historii analiz,
- konfiguracja katalogu danych,
- atomiczny zapis plikow JSON,
- odczyt listy runow z samego `index.json`, bez ladowania wszystkich
  `run.json`,
- lokalny store access tokenow zapisywanych z UI,
- tolerancja na uszkodzony rekord: nie blokowac calej listy.

Zatwierdzone decyzje:

- package docelowy: neutralny root `pl.mkn.tdw.localworkspace`, np.
  `localworkspace.analysisruns` i `localworkspace.tokens`; endpointy REST
  dopiero pozniej w `api.analysisruns`,
- `index.json` jest lekkim read modelem listy historii i moze zawierac tylko
  `analysisId`, `schema`, `version`, `runPath`, `feature`, `name`, `createdAt`,
  `updatedAt` i `completedAt`,
- `name` jest inicjalizowane przez feature i edytowalne z UI; incident analysis
  uzywa startowo `correlationId`, a flow explorer endpoint path + goal,
- `run.json` jest ladowany dopiero przy szczegolach/kontynuacji konkretnego
  runu i pozostaje zrodlem prawdy dla pelnego snapshotu,
- `tokens.json` lezy obok `index.json` i przechowuje access tokeny zapisane z
  UI; run i index trzymaja tylko referencje,
- przy przekazanym `tdw.workspace.directory` aplikacja tworzy katalog danych,
- uszkodzony pojedynczy `run.json` nie blokuje calej historii.

Kryteria akceptacji:

- testy jednostkowe store,
- brak zaleznosci store od incident-specific klas poza adapterem/mappingiem,
- katalog danych jest tworzony tylko gdy lokalny store jest wlaczony,
- lista historii nie wymaga odczytu wszystkich `runs/*/run.json`.

### [x] 003. Persistowanie zakonczonego incident job state

Cel:

- zapisywac zakonczone rezultaty incident analysis do lokalnego store.

Zakres:

- zapis lokalnego rekordu dopiero po zakonczonym initial runie,
- brak zapisu roboczego `IN_PROGRESS`, czesciowych evidence stepow i
  nieudanych przebiegow w V1,
- zapis/aktualizacja `exportEnvelope` jako 1:1 projekcji aktualnego stanu
  exportu,
- uzupelnienie tylko minimalnego lokalnego `continuation`, bez duplikowania
  danych dostepnych juz w `exportEnvelope`.

Zatwierdzone i zaimplementowane:

- zapis tylko dla zakonczonych initial runow,
- `FAILED`, `NOT_FOUND` i przerwane runy nie sa zapisywane w V1,
- w V1 zapisujemy prompt/artifacts inline jako czesc `exportEnvelope`, bez
  plikow pomocniczych,
- incident export schema zostala przeniesiona na `tdw.analysis-export`;
  legacy `incident-tracker.analysis-export`, starsze wersje envelope i surowe
  job snapshoty bez envelope nie sa wspierane.

Kryteria akceptacji:

- po restarcie aplikacji zakonczony run jest widoczny w store,
- snapshot odczytany ze store pasuje do obecnego UI,
- import/export dziala tylko dla aktualnego envelope bez wiedzy o lokalnych
  polach.

### [x] 004. API historii lokalnych runow

Cel:

- wystawic liste i szczegoly lokalnych runow dla UI.

Zakres:

- `GET /analysis/runs`,
- `GET /analysis/runs/{analysisId}`,
- `PATCH /analysis/runs/{analysisId}/name`,
- `DELETE /analysis/runs/{analysisId}`,
- DTO list item zoptymalizowane pod ekran historii.

Zatwierdzone decyzje:

- endpointy V1: `GET /analysis/runs`,
  `GET /analysis/runs/{analysisId}`,
  `PATCH /analysis/runs/{analysisId}/name`,
  `DELETE /analysis/runs/{analysisId}`,
- package docelowy: `pl.mkn.tdw.api.analysisruns`,
- lista zwraca tylko lekki read model z `index.json`: `analysisId`,
  `feature`, `name`, `createdAt`, `updatedAt`, `completedAt`; nie zwraca
  `runPath`, promptu, evidence ani innych ciezkich danych,
- detail zwraca bezpieczna projekcje: dane z indexu, `exportEnvelope` i
  `continuationEnabled`; nie ujawnia `gitLabGroup`, `authMode` ani
  `authPrincipalRef`,
- `PATCH /name` zmienia tylko `name` w `index.json` i wymaga istniejacego wpisu
  indexu,
- `DELETE` w V1 usuwa tylko lokalny wpis i katalog `runs/<analysisId>/`; nie
  usuwa zadnych przyszlych katalogow sesji SDK, bo tych metadanych jeszcze nie
  zapisujemy,
- brak runu zwraca `404 LOCAL_ANALYSIS_RUN_NOT_FOUND`,
- istniejacy wpis w indexie z brakujacym albo uszkodzonym `run.json` przy
  detail zwraca `409 LOCAL_ANALYSIS_RUN_CORRUPTED`,
- API pracuje tylko na aktualnym lokalnym kontrakcie
  `tdw.local-analysis-run` + `tdw.analysis-export`, bez migracji i bez wsparcia
  legacy formatow,
- imported UI state nie jest zapisywany jako lokalna kopia V1.

Kryteria akceptacji:

- testy MockMvc,
- lista nie zwraca ciezkich artifacts/promptow i bazuje na `index.json`,
- detail zwraca `exportEnvelope.payload.job` jako snapshot do UI.

### [x] 005. Ekran historii analiz

Cel:

- dodac przekrojowy UI do przegladania poprzednich lokalnych runow i
  otwierania ich w ekranach wlasciwych feature'ow.

Zakres:

- route historii,
- tabela/lista runow,
- filtry podstawowe,
- akcje otworz/exportuj/usun/zmien nazwe,
- przejscie do feature screen z `localRunId`,
- reuse obecnych komponentow wyniku i timeline w ekranie feature'a, nie w
  historii.

Zatwierdzone decyzje:

- nazwa w nawigacji: `Analysis History`, spojna z anglojezycznym menu,
- route UI: `/analysis-history`, zeby nie kolidowac z backendowym JSON API
  `/analysis/runs`,
- pozycja nawigacji znajduje sie w sekcji `Platform`, bo historia jest
  przekrojowa dla wszystkich analiz,
- minimalne filtry V1: tekstowy filtr po `name` i `feature`,
- historia pokazuje tylko zakonczone lokalne runy zapisane w `index.json`;
  aktywne live joby pozostaja poza historia,
- lista na starcie pobiera tylko `GET /analysis/runs` i nie laduje ciezkich
  `run.json`,
- otwarcie runu z historii nie renderuje detailu w historii; nawiguje do route
  feature'a z `localRunId`, a ekran feature'a doczytuje
  `GET /analysis/runs/{analysisId}` i odtwarza snapshot jako origin `local`,
- export lokalnego runu uzywa `exportEnvelope` 1:1,
- edycja nazwy uzywa `PATCH /analysis/runs/{analysisId}/name`,
- usuniecie runu uzywa `DELETE /analysis/runs/{analysisId}`,
- imported export pozostaje read-only i nie pojawia sie w historii lokalnej,
- V1 UI obsluguje odtworzenie `incident-analysis` z lokalnego store; Flow
  Explorer dostaje ten sam mechanizm wejscia po `localRunId`, a pelna wartosc
  pojawi sie po persistowaniu flow runow w local store.

Kryteria akceptacji:

- uzytkownik moze znalezc poprzednia analize,
- otwarty lokalny run wyglada jak obecny wynik analizy w ekranie danego
  feature'a,
- imported export pozostaje read-only i nie pojawia sie w historii lokalnej.

Wykonane:

- dodano route UI `/analysis-history` i pozycje nawigacji `Analysis History`
  w sekcji `Platform`,
- dodano lekki klient `AnalysisRunHistoryApiService`,
- ekran startowo pobiera tylko `GET /analysis/runs`, a pelny run doczytuje
  dopiero ekran feature'a po `localRunId`,
- akcja exportu uzywa dedykowanego `GET /analysis/runs/{analysisId}/export`,
  a nie detailu lokalnego runu,
- historia jest lista belek/launcherem bez uniwersalnego detailu,
- ekrany Incident Analysis i Flow Explorer umieja przyjac `localRunId` jako
  wejscie origin `local`,
- akcje `Export`, `Nazwa` i `Usun` sa podlaczone do API historii,
- Spring fallback serwuje SPA dla `/analysis-history`,
- dodano testy frontendowe i backendowy test routingu.

### [x] 006. Kontynuacja lokalnego runu przez API historii

Cel:

- umożliwic follow-up dla lokalnego runu po restarcie aplikacji, bez
  traktowania importowanego exportu jako kontynuowalnego stanu.

Zakres:

- `POST /analysis/runs/{analysisId}/chat/messages`,
- zbudowanie `AnalysisAiChatRequest` z lokalnego rekordu,
- zapis odpowiedzi follow-up do lokalnego runu dopiero po zakonczonej
  odpowiedzi assistant,
- UI polling albo odswiezanie odpowiedzi follow-up dla local runu.

Zatwierdzone decyzje:

- V1 local follow-up jest synchroniczny: request czeka na pelna odpowiedz AI,
- V1 obsluguje `incident-analysis`; Flow Explorer zostaje pod ten sam model UI,
  ale wymaga osobnego persistowania flow runow w local store,
- przy bledzie follow-up UI pokazuje blad i `run.json` zostaje bez zmian; nie
  zapisujemy wiadomosci `FAILED` do lokalnego snapshotu w V1.

Kryteria akceptacji:

- zakonczony run po restarcie moze dostac follow-up, jesli ma zapisany
  `copilotSessionId`,
- follow-up uzywa zapisanego session id oraz aktualnie odtworzonego runtime
  configu,
- tool evidence z follow-up zapisuje sie przy konkretnej odpowiedzi assistant.

Wykonane:

- dodano neutralny handler local run chat w `localworkspace.analysisruns`,
- dodano `POST /analysis/runs/{analysisId}/chat/messages` w shared/operator
  API historii,
- dodano incidentowy handler odtwarzajacy `AnalysisAiChatRequest` z lokalnego
  `exportEnvelope.payload.job` oraz `continuation`,
- odpowiedz follow-up jest zapisywana do `run.json` dopiero po pelnym sukcesie,
- bledy follow-up nie nadpisuja lokalnego rekordu,
- UI Incident Analysis kontynuuje `origin=local` przez API historii, a
  `origin=imported` pozostaje read-only.

### [x] 007. Zapis `copilotSessionId` i metadanych SDK

Cel:

- przygotowac lokalny rekord do wznawiania sesji Copilota.

Zakres:

- execution gateway zwraca albo publikuje rzeczywisty `session.getSessionId()`,
- initial run zapisuje ten id w `continuation.copilotSessionId`,
- lokalny follow-up po sukcesie przesuwa `continuation.copilotSessionId` na
  ostatnia zakonczona sesje Copilota,
- `continuation` zapisuje tylko lekkie metadata runtime:
  `copilotSessionId`, `copilotRuntime`, `continuationMode`,
- model, reasoning i context usage pozostaja w istniejacym export-envelope /
  `usage` i nie sa dublowane w `continuation`.

Uzgodnione:

- `CopilotExecutionResult` dostaje `sessionId`,
- `InitialAnalysisResponse` i `AnalysisAiChatResponse` przenosza
  `copilotSessionId` do warstwy feature'a,
- `analysisId` pozostaje identyfikatorem lokalnego runu/UI,
  `copilotSessionId` jest wylacznie identyfikatorem sesji SDK,
- `continuationMode=copilot-session` jest lekkim lokalnym opisem sposobu
  kontynuacji; technicznym uchwytem pozostaje `copilotSessionId`.

Kryteria akceptacji:

- lokalny run ma session id zgodny z SDK,
- export nie zawiera session id,
- walidacja hidden tool context nadal chroni tool invocation.

Wykonane:

- `CopilotSdkExecutionGateway` zwraca rzeczywisty `sessionId` w
  `CopilotExecutionResult`,
- local `run.json` zapisuje `continuation.copilotSessionId`,
  `continuation.copilotRuntime=github-copilot-sdk` i
  `continuation.continuationMode=copilot-session`,
- local follow-up aktualizuje `continuation.copilotSessionId` po pelnym sukcesie
  odpowiedzi,
- testy pokrywaja zapis session id poza exportem oraz przekazanie session id z
  SDK do lokalnej persystencji.

### [x] 008. Decyzja: neutralny AI gateway i follow-up bez fallbacku

Cel:

- utrzymac jedna sciezke wykonania follow-upu, ale oprzec ja na tej samej
  technicznej sesji Copilot SDK,
- nie wprowadzac do `aiplatform` pojec `initial analysis` i `follow-up chat`.

Zakres:

- AI platform gateway dostaje jedno neutralne API wykonania turnu w sesji,
  parametryzowane przez `sessionTarget=NEW` albo
  `sessionTarget=EXISTING(copilotSessionId)`,
- platforma mapuje neutralny request na `SessionConfig` albo
  `ResumeSessionConfig` i za kazdym razem rejestruje aktualne tools, skille,
  hooks, permission handler, hidden context, model i reasoning effort,
- feature decyduje, czy turn jest initial, czy follow-up:
  - initial przekazuje pelny prompt z deterministic evidence,
  - follow-up przekazuje tylko tresc wiadomosci uzytkownika do istniejacej
    sesji,
- brak automatycznego fallbacku follow-upu do innej sesji, innego promptu albo
  nowej initial analysis,
- przy bledzie follow-upu lokalny run nie jest nadpisywany, a uzytkownik moze
  recznie zaczac nowa initial analysis, gdy chce pracowac na swiezszym kodzie.

Uzgodnione:

- czeste startowanie od zera jest akceptowalne i produktowo zdrowe,
- lokalna kontynuacja sluzy wygodnemu dopytywaniu w ramach tej samej sesji SDK,
- `copilotSessionId` jest technicznym uchwytem kontynuacji, ale nie trafia do
  exportu i nie staje sie identyfikatorem biznesowym,
- Copilot SDK ma utrzymywac kontekst rozmowy; aplikacja nie powinna doklejac
  calego zapisanego runu do kazdego follow-up promptu,
- callbacki, tools i hidden context nie sa trwale "zapamietane" przez nasz
  proces po restarcie, dlatego musza byc podawane przy wznowieniu sesji,
- brak fallbacku jest celowa cecha follow-upu, nie brakujacy mechanizm.

Kryteria akceptacji:

- `aiplatform` nie ma osobnych publicznych metod `initial` i `follow-up`,
- gateway przyjmuje neutralny request turnu oraz `sessionTarget`,
- lokalny follow-up uzywa `sessionTarget=EXISTING(copilotSessionId)` i wysyla
  jako prompt/tresc tylko wiadomosc uzytkownika,
- `createSession` i `resumeSession` dostaja pelny aktualny runtime config,
- blad follow-upu nie uruchamia alternatywnej sciezki wykonania ani nowej
  initial analysis,
- plan nie przedstawia ponownego skladania pelnego promptu jako awaryjnego
  fallbacku po resume.

### [x] 009. Neutralny AI Gateway i wznawianie sesji SDK

Cel:

- zastapic rozdzielone sciezki platformowe neutralnym wykonaniem turnu w sesji,
  tak zeby `aiplatform` nie wiedziala, czy feature realizuje initial analysis,
  follow-up chat, flow explorer czy inny przyszly use case.

Zakres:

- przeksztalcic istniejacy kontrakt gatewaya bez wymuszania rename'u klas:
  `CopilotRunRequest` / `CopilotExecutionResult` pozostaja neutralnym
  kontraktem turnu,
- dodac `sessionTarget=NEW | EXISTING(copilotSessionId)` jako techniczny wybor
  utworzenia albo wznowienia sesji,
- mapowac `sessionTarget=NEW` na `SessionConfig` i `createSession`,
- mapowac `sessionTarget=EXISTING` na `ResumeSessionConfig` i `resumeSession`,
- przeniesc wspolne budowanie konfiguracji sesji do jednego miejsca, zeby
  create i resume dostawaly ten sam runtime config: tools, available tools,
  hidden context, skille, hooks, permission handler, model, reasoning effort,
  event handlers i evidence/activity sinks,
- `CopilotExecutionResult` ma przenosic dane potrzebne do per-turn UI:
  `sessionId`, tekst odpowiedzi, usage/koszt, model/reasoning metadata,
  activity/events oraz user-facing tool evidence,
- zmienic incident initial analysis tak, zeby wysylala pelny prompt przez
  `sessionTarget=NEW`,
- zmienic incident local follow-up tak, zeby wysylal sama wiadomosc uzytkownika
  przez `sessionTarget=EXISTING(copilotSessionId)`,
- usunac docelowe zalezenie follow-upu od ponownego skladania pelnego promptu,
- zachowac brak fallbacku: blad create/resume/send nie uruchamia alternatywnej
  sciezki.

Rozstrzygniete:

- nie robimy wymuszonego rename'u na `CopilotTurnRequest`, bo obecny
  `CopilotRunRequest` jest juz neutralny i nie zawiera semantyki feature'a,
- gateway zwraca surowa odpowiedz plus `sessionId` i usage; parsing wyniku
  pozostaje w feature/providerze,
- wspolny trace per turn idzie przez istniejace
  `AnalysisAiUsage`, `AnalysisAiActivityEvent` i user-facing tool evidence,
- `copilotSessionId` jest wymagany dla kazdego lokalnego follow-upu; jego brak
  oznacza blad "run nie jest kontynuowalny", bez startowania nowej sesji.

Kryteria akceptacji:

- `aiplatform` nie importuje feature'ow i nie ma productowych flag
  `initial/followUp`,
- initial i follow-up uzywaja tego samego gatewaya,
- follow-up po restarcie aplikacji probuje wznowic zapisana sesje SDK,
- do wznowionej sesji sa ponownie podpinane tools, hooks, hidden context,
  permission handler i skille,
- initial i follow-up zwracaja porownywalny execution trace per turn:
  usage/koszt, model, reasoning effort, activity/events i tool evidence,
- lokalny `run.json` nadal jest aktualizowany dopiero po pelnym sukcesie
  odpowiedzi assistant,
- export pozostaje bez session id i bez metadanych kontynuacji.

Wykonane:

- dodano `CopilotSessionTarget` z trybami `NEW` i `EXISTING(sessionId)`,
- `CopilotRunRequest` i `CopilotPreparedSession` przenosza `sessionTarget`,
  `SessionConfig` i `ResumeSessionConfig`,
- `CopilotSessionConfigFactory` buduje konfiguracje create/resume z tym samym
  zestawem tools, available tools, skill directories, hooks, permission
  handler, disabled skills, modelem i `reasoningEffort`,
- `CopilotSdkExecutionGateway` wybiera `createSession` albo `resumeSession`
  wedlug `sessionTarget`, rejestruje evidence/budget store pod realnym
  session id i nie odpala zadnego fallbacku,
- incident initial wysyla pelny prompt przez `sessionTarget=NEW`,
- incident follow-up wymaga `copilotSessionId`, wysyla tylko tresc wiadomosci
  operatora przez `sessionTarget=EXISTING` i nie renderuje juz promptu
  kontynuacyjnego z lokalnego runu,
- job state i lokalny handler kontynuacji przekazuja ostatni zakonczony
  `copilotSessionId`,
- testy pokrywaja `resumeSession`, konfiguracje resume, przygotowanie
  existing session target, assembler follow-upu, lokalny zapis/kontynuacje,
  job chat i guard architektury.

### [x] 010. Automatyczne zarzadzanie dlugim kontekstem

Cel:

- pozwolic na dlugie rozmowy i bardzo kosztowne analizy bez wymagania od
  operatora decyzji o kompaktowaniu, czyszczeniu historii albo restartowaniu
  konwersacji.

Decyzja MVP:

- nie ustawiamy explicit `InfiniteSessionConfig` w `SessionConfig` ani
  `ResumeSessionConfig`, bo upstream Node/CLI docs potwierdzaja, ze
  `infiniteSessions` jest domyslnie wlaczone,
- nie kodujemy progow `backgroundCompactionThreshold=0.8` ani
  `bufferExhaustionThreshold=0.95`; zostaja defaultem SDK, zeby aplikacja nie
  zamrazala wartosci, ktore SDK moze poprawiac,
- tuning progow wraca dopiero wtedy, gdy realne pomiary albo smoke testy pokaza
  problem z defaultami.

Zakres:

- ustawic `CopilotClientOptions.copilotHome` na katalog kontrolowany przez
  lokalny workspace aplikacji, domyslnie
  `analysis.ai.copilot.copilot-home=${tdw.workspace.directory}/copilot`,
- nie ustawic `SessionConfig.infiniteSessions` i
  `ResumeSessionConfig.infiniteSessions`, zeby create/resume korzystaly z
  domyslnej polityki SDK/CLI,
- zachowac obserwacje `session.usage_info`, `session.truncation`,
  `session.compaction_start` i `session.compaction_complete` jako activity/usage,
- local run zapisuje pelna historie, wyniki, usage i tool evidence na potrzeby
  UI/audytu, ale normalny follow-up nie buduje promptu przez replay tych danych,
- eventy compaction/truncation/usage sa przypinane do konkretnego turnu, zeby
  UI moglo pokazac koszt i przebieg pracy kazdej odpowiedzi follow-up,
- pelna historia i pelne rezultaty pozostaja zapisane w lokalnym `run.json`;
  sa zrodlem prezentacji i backupu lokalnego workspace'u, a nie zamiennikiem
  sesji SDK,
- brak automatycznego fallbacku: jezeli automatyczne zarzadzanie kontekstem
  zawiedzie, follow-up konczy sie bledem i nie uruchamia innej sciezki.

Do zatwierdzenia po MVP:

- czy kiedykolwiek wystawiamy tuning progow `InfiniteSessionConfig`, czy
  zostawiamy go wylacznie jako internal override na potrzeby diagnostyki,
- czy po bledzie `resumeSession` komunikat UI ma sugerowac reczny start nowej
  analizy.

Kryteria akceptacji:

- operator moze kontynuowac dluga rozmowe bez recznego czyszczenia kontekstu,
- SDK infinite/background compaction pozostaje wlaczone przez domyslna
  polityke SDK/CLI tam, gdzie tworzymy sesje Copilota oraz tam, gdzie je
  wznawiamy,
- stan techniczny sesji Copilota trafia do lokalnego workspace'u aplikacji, a
  nie do przypadkowego domyslnego katalogu uzytkownika,
- local follow-up wysyla do istniejacej sesji tylko kolejna wiadomosc
  uzytkownika,
- UI moze pokazywac usage/compaction jako informacyjny przebieg pracy, ale nie
  wymaga od uzytkownika decyzji,
- per-turn execution trace pozostaje spojny dla initial i follow-up,
- po automatycznym zarzadzaniu kontekstem nadal dziala tool evidence i hidden
  scope.

### [x] 011. Sanitized export z lokalnego runu

Cel:

- upewnic sie, ze export jest read-only pakietem do dzielenia sie wynikiem.

Zakres:

- export lokalnego runu jako zwrocenie `exportEnvelope`,
- testy, ze export usuwa pola kontynuacyjne,
- brak kompatybilnosci wstecznej importu/exportu: tylko aktualny schema i
  version envelope.

Zatwierdzone i zaimplementowane:

- dodano `GET /analysis/runs/{analysisId}/export`, ktory zwraca bezposrednio
  `exportEnvelope` zapisany w lokalnym `run.json`,
- frontend historii uzywa tego endpointu przy akcji exportu zamiast
  `GET /analysis/runs/{analysisId}`,
- export nadal zawiera to, co zawiera aktualny `tdw.analysis-export` V6,
  wlacznie z `preparedPrompt`, bo w MVP jest 1:1 projekcja obecnego wyniku,
- nie dodano osobnego trybu diagnostic/share; rozdzielenie trybow zostaje
  osobna decyzja produktowa po MVP,
- informacja, ze export nie jest backupem kontynuowalnego workspace'u, zostaje
  do dopisania w kroku 012 razem z dokumentacja lokalnego katalogu danych.

Kryteria akceptacji:

- export nie zawiera hidden context, session id, auth metadata ani lokalnych
  sciezek,
- import pozostaje read-only i odrzuca stare schematy oraz stare wersje,
- lokalny run po eksporcie nadal pozostaje kontynuowalny tylko u wlasciciela
  katalogu danych.

Wykonane:

- serwis historii zwraca w eksporcie wylacznie `record.exportEnvelope()`,
- kontroler wystawia envelope bez opakowania detailem lokalnego runu,
- testy backendu pilnuja, ze export nie zawiera `continuation`,
  `copilotSessionId`, auth markerow, lokalnych sciezek ani runtime metadata,
- testy frontendowe pilnuja, ze przycisk Export korzysta z endpointu exportu i
  nie laduje lokalnego detailu.

### [x] 011a. Rename bazowego pakietu Java na `pl.mkn.tdw`

Cel:

- dopasowac techniczny root pakietow Javy do product-facing kierunku
  `Team Delivery Workspace`, zamiast historycznego `incidenttracker`.

Zakres:

- przeniesc produkcyjny i testowy root Javy z dotychczasowego rootu do
  `src/main/java/pl/mkn/tdw` oraz `src/test/java/pl/mkn/tdw`,
- zmienic deklaracje `package` i importy na `pl.mkn.tdw.*`,
- zaktualizowac `pom.xml` groupId do `pl.mkn.tdw`,
- zaktualizowac `PackageDependencyGuardTest`, `AGENTS.md` i dokumentacje
  architektury z nowym rootem,
- nie zmieniac endpointow HTTP, schema JSON, nazw tooli, nazw feature'ow,
  kontraktow exportu ani zachowania runtime.

Kryteria akceptacji:

- backend kompiluje sie po rename,
- guard architektoniczny nadal blokuje te same kierunki zaleznosci pod nowym
  rootem,
- nie ma pozostalych deklaracji/importow Javy pod historycznym rootem,
- dokumentacja pokazuje `pl.mkn.tdw` jako aktualny root pakietow.

Wykonane:

- przeniesiono produkcyjne i testowe drzewo Javy do `pl/mkn/tdw`,
- zaktualizowano deklaracje pakietow, importy, `pom.xml` groupId,
  `PackageDependencyGuardTest`, `AGENTS.md` i dokumentacje architektury,
- potwierdzono brak pozostalych referencji do historycznego pelnego rootu
  pakietu w `src`, `docs`, `AGENTS.md` i `pom.xml`,
- nie zmieniano `artifactId`, endpointow HTTP, schema JSON, nazw tooli,
  kontraktow exportu ani zachowania runtime.

### [ ] 012. Dokumentacja i launcher

Cel:

- opisac model lokalnego workspace'u i ustawic katalog danych w sposob
  zrozumialy dla uzytkownika.

Zakres:

- properties katalogu danych,
- rekomendowany launch script,
- opis roznicy: local workspace vs export,
- instrukcja backupu przez kopiowanie katalogu danych.

Do zatwierdzenia:

- domyslna nazwa katalogu `tdw-data`,
- nazwa ustawienia albo zmiennej srodowiskowej, ktore launcher przekazuje
  backendowi,
- launcher w V1 tworzy/ustawia katalog obok JAR-a.

Kryteria akceptacji:

- uzytkownik rozumie, gdzie sa dane,
- usuniecie katalogu ma jasne konsekwencje,
- export nie jest przedstawiany jako pelny backup.

## Ryzyka

- `resumeSession` moze zalezec od lokalnego session store Copilot CLI i wersji
  SDK/CLI. Trzeba obsluzyc blad wznowienia jako jawny blad follow-upu bez
  fallbacku, z czytelnym komunikatem i bez nadpisywania lokalnego runu.
- Lokalny katalog danych moze zawierac wrazliwe informacje operacyjne. Trzeba
  opisac to w dokumentacji i nie eksportowac pol kontynuacyjnych.
- Jesli V1 zapisuje tokeny w pliku lokalnym, trzeba jawnie opisac konsekwencje
  security, zadbac o mozliwie restrykcyjne uprawnienia pliku i zostawic droge do
  pozniejszego OS keychain albo szyfrowania.
- Ciezkie prompt/artifacts moga zwiekszyc rozmiar katalogu. Potrzebne beda
  limity albo pozniejsza kompresja/retencja.
- Wspolny ekran historii nie powinien wymuszac incident-specific kontraktow na
  przyszlych feature'ach.

## Otwarte Decyzje

- Czy `GET /analysis/jobs/{analysisId}` powinien czytac ze store po
  zakonczeniu joba, czy dopiero ekran historii?
- Czy export ma miec dwa tryby: share/read-only oraz diagnostic?
- Czy lokalny store ma miec retencje albo tylko reczne usuwanie?
