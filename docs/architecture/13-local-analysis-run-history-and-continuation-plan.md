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
4. follow-up moze uzyc poprzedniej sesji Copilot SDK przez `resumeSession`,
   ale ma fallback do odtworzenia kontekstu z zapisanego snapshotu,
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
- Pelny backup albo przeniesienie kontynuowalnego workspace'u oznacza
  skopiowanie calego katalogu danych, nie zwykly export JSON.
- V1 lokalnego store jest wlaczone domyslnie w lokalnej dystrybucji JAR.
- V1 zapisuje tylko zakonczone initial runy oraz kazda zakonczona odpowiedz w
  follow-up chat. Nie zapisujemy roboczych, czesciowych ani nieudanych
  przebiegow jako lokalnych runow.
- Tokeny dostepowe wpisane przez uzytkownika w UI moga byc zapisane lokalnie w
  katalogu workspace'u, zeby aplikacja po restarcie mogla kontynuowac prace bez
  ponownego wpisywania tokenow.
- `resumeSession` Copilot SDK jest optymalizacja kontynuacji, nie jedyny
  mechanizm poprawnosci. Fallbackiem jest nowa sesja z promptem zbudowanym z
  lokalnego snapshotu.

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
- Hidden tool context nadal ma sens jako walidacja integralnosci runu. Przy
  `resumeSession` `copilotSessionId` w hidden context musi odpowiadac
  rzeczywistej wznawianej sesji SDK.

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
```

`authPrincipalRef` wskazuje wpis w lokalnym `tokens.json`, jesli uzytkownik
zdecydowal sie zapamietac tokeny w UI. Sam token nie jest polem runu ani
exportu.

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
PATCH /analysis/runs/{analysisId}/name
POST /analysis/runs/{analysisId}/chat/messages
DELETE /analysis/runs/{analysisId}
```

Uwagi:

- `GET /analysis/jobs/{analysisId}` moze zostac dla live pollingu.
- Docelowo live job po utworzeniu powinien miec odpowiadajacy rekord lokalny,
  zeby historia i polling nie byly dwoma swiatami.
- `POST /analysis/runs/{analysisId}/chat/messages` kontynuuje tylko lokalne
  runy z `continuation.enabled=true`.
- Importowany export z UI nie tworzy rekordu lokalnego i nie pojawia sie w
  `GET /analysis/runs`, chyba ze kiedys powstanie osobna akcja "zapisz kopie
  jako lokalny, bez kontynuacji".

## Ekran Historii

Nowy ekran UI, roboczo `Analysis History`.

Lista powinna pokazywac:

- feature,
- nazwe,
- daty utworzenia, aktualizacji i zakonczenia.

Akcje:

- otworz,
- zmien nazwe,
- kontynuuj,
- exportuj,
- usun z lokalnego katalogu danych.

Widok szczegolow powinien reuse'owac obecne komponenty wyniku, evidence, AI
activity, tool evidence i follow-up chat. Tryb widoku wynika ze zrodla:

- `live`: polling joba,
- `local`: odczyt z lokalnego store i mozliwy follow-up,
- `imported`: tylko podglad w UI.

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

- package docelowy: neutralny root `pl.mkn.incidenttracker.localworkspace`, np.
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

### [ ] 003. Persistowanie zakonczonego incident job state

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

Do zatwierdzenia:

- zapis tylko dla zakonczonych initial runow,
- `FAILED`, `NOT_FOUND` i przerwane runy nie sa zapisywane w V1,
- czy w V1 zapisujemy prompt/artifacts inline czy w plikach pomocniczych.

Kryteria akceptacji:

- po restarcie aplikacji zakonczony run jest widoczny w store,
- snapshot odczytany ze store pasuje do obecnego UI,
- import/export dalej dziala bez wiedzy o lokalnych polach.

### [ ] 004. API historii lokalnych runow

Cel:

- wystawic liste i szczegoly lokalnych runow dla UI.

Zakres:

- `GET /analysis/runs`,
- `GET /analysis/runs/{analysisId}`,
- `PATCH /analysis/runs/{analysisId}/name`,
- `DELETE /analysis/runs/{analysisId}`,
- DTO list item zoptymalizowane pod ekran historii.

Do zatwierdzenia:

- nazwy endpointow,
- czy delete usuwa tez katalog SDK session store/czysci powiazane artefakty,
- czy imported UI state kiedykolwiek moze byc zapisany jako local read-only
  copy.

Kryteria akceptacji:

- testy MockMvc,
- lista nie zwraca ciezkich artifacts/promptow i bazuje na `index.json`,
- detail zwraca `exportEnvelope.payload.job` jako snapshot do UI.

### [ ] 005. Ekran historii analiz

Cel:

- dodac UI do przegladania poprzednich lokalnych runow.

Zakres:

- route historii,
- tabela/lista runow,
- filtry podstawowe,
- akcje otworz/exportuj/usun,
- przejscie do szczegolow runu,
- reuse obecnych komponentow wyniku i timeline.

Do zatwierdzenia:

- nazwa w nawigacji,
- minimalne filtry V1,
- czy historia pokazuje rowniez aktywne live joby.

Kryteria akceptacji:

- uzytkownik moze znalezc poprzednia analize,
- otwarty lokalny run wyglada jak obecny wynik analizy,
- imported export pozostaje read-only i nie pojawia sie w historii lokalnej.

### [ ] 006. Kontynuacja lokalnego runu bez `resumeSession`

Cel:

- umożliwic follow-up dla lokalnego runu po restarcie aplikacji, uzywajac
  dzisiejszego prompt-rehydrate.

Zakres:

- `POST /analysis/runs/{analysisId}/chat/messages`,
- zbudowanie `AnalysisAiChatRequest` z lokalnego rekordu,
- zapis odpowiedzi follow-up do lokalnego runu dopiero po zakonczonej
  odpowiedzi assistant,
- UI polling albo odswiezanie odpowiedzi follow-up dla local runu.

Do zatwierdzenia:

- czy local follow-up tworzy background task jak live job,
- jak UI pokazuje blad kontynuacji,
- jak odroznic prompt-rehydrate od resume w activity/metadata.

Kryteria akceptacji:

- zakonczony run po restarcie moze dostac follow-up,
- follow-up uzywa zapisanego evidence, result, historii i tool evidence,
- tool evidence z follow-up zapisuje sie przy konkretnej odpowiedzi assistant.

### [ ] 007. Zapis `copilotSessionId` i metadanych SDK

Cel:

- przygotowac lokalny rekord do wznawiania sesji Copilota.

Zakres:

- execution gateway zwraca albo publikuje rzeczywisty `session.getSessionId()`,
- initial run zapisuje ten id w `continuation.copilotSessionId`,
- runtime zapisuje model, reasoning, SDK/CLI metadata, context usage.

Do zatwierdzenia:

- czy `CopilotExecutionResult` dostaje `sessionId`,
- czy `AnalysisAiActivityEvent` wystarczy jako zrodlo context usage,
- jak nazwac `analysisRunId` vs `copilotSessionId`.

Kryteria akceptacji:

- lokalny run ma session id zgodny z SDK,
- export nie zawiera session id,
- walidacja hidden tool context nadal chroni tool invocation.

### [ ] 008. `resumeSession` dla follow-up

Cel:

- uzyc poprzedniej sesji Copilot SDK przy follow-up lokalnego runu.

Zakres:

- nowa sciezka gatewaya `resumeSession(sessionId, ResumeSessionConfig)`,
- mapowanie `CopilotSessionConfigRequest` na `ResumeSessionConfig`,
- rejestracja aktualnych tools, hooks, evidence sink i budget dla wznawianej
  sesji,
- fallback do prompt-rehydrate przy bledzie resume.

Do zatwierdzenia:

- czy resume jest domyslne, czy feature-flagowane w V1,
- ktore bledy resume sa ciche i ida fallbackiem,
- co pokazac operatorowi w UI, gdy fallback zostal uzyty.

Kryteria akceptacji:

- follow-up probuje resume tylko dla lokalnych runow z session id,
- nieudane resume nie blokuje kontynuacji,
- usage/activity rozroznia `RESUMED_SESSION` i `PROMPT_REHYDRATED_SESSION`.

### [ ] 009. Kompaktowanie i kontrola rozmiaru kontekstu

Cel:

- uniknac degradacji po wielu follow-upach albo po dlugiej sesji.

Zakres:

- pomiar `session.usage_info`,
- decyzja kiedy wywolac `compact()`,
- zapis wynikow compaction/truncation w activity,
- fallback do nowej sesji, jesli kontekst jest zbyt duzy albo resume staje sie
  niestabilne.

Do zatwierdzenia:

- progi tokenow/messages,
- czy compaction uruchamiac po initial czy dopiero przed follow-up,
- czy operator widzi ostrzezenie o kompaktowaniu.

Kryteria akceptacji:

- UI pokazuje context usage,
- follow-up nie rosnie bez kontroli,
- po compaction dalej dziala tool evidence i hidden scope.

### [ ] 010. Sanitized export z lokalnego runu

Cel:

- upewnic sie, ze export jest read-only pakietem do dzielenia sie wynikiem.

Zakres:

- export lokalnego runu jako zwrocenie `exportEnvelope`,
- testy, ze export usuwa pola kontynuacyjne,
- zachowanie obecnej kompatybilnosci importu.

Do zatwierdzenia:

- czy export ma dalej zawierac `preparedPrompt`,
- czy potrzebny jest osobny tryb "diagnostic export" vs "share export",
- czy export ostrzega, ze nie jest backupem kontynuowalnego workspace'u.

Kryteria akceptacji:

- export nie zawiera hidden context, session id, auth metadata ani lokalnych
  sciezek,
- import pozostaje read-only,
- lokalny run po eksporcie nadal pozostaje kontynuowalny tylko u wlasciciela
  katalogu danych.

### [ ] 011. Dokumentacja i launcher

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
  SDK/CLI. Dlatego fallback prompt-rehydrate jest wymagany.
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
- Czy `resumeSession` wlaczamy dopiero po ekranie historii i prompt-rehydrate
  continuation?
