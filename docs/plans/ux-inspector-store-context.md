# UX Inspector: zrzut store'a jako evidence sesji

Status: done

Source need: [UX Inspector: kontekst stanu uruchomionego frontendu](../needs/ux-inspector-store-context.md)

Poziom zmiany: L3. Zmieniamy granice transferu danych z badanej strony,
neutralna persistence runu, model sesji AI i kontrakt publicznego startu joba.
Wspolny runtime Browser Tools i platformowe logowanie tooli wymagaja audytu
konsumentow L2.

## Potrzeba / dlaczego

Kod moze wskazywac, ze zachowanie wybranego elementu zalezy od NgRx store'a,
ale dzisiejszy UX Inspector nie zna wartosci z badanej sesji. Wtedy nie moze
polaczyc reguly z kodu z konkretnym stanem operatora. Potrzeba i miary
sukcesu sa opisane w dokumencie z sekcji `Source need`.

## Proponowane rozwiazanie

1. Przy wskazaniu elementu Browser Tools wywoluje raz
   `globalThis.getStoreState()` w kontekscie badanej strony. Akceptuje
   wynik synchroniczny albo `Promise`, stosuje timeout i przyjmuje tylko
   serializowalny JSON. `null`/`undefined` oznacza brak danych; puste
   `{}` albo `[]` oznacza poprawny, pusty stan. Blad, timeout, brak funkcji,
   niepoprawny lub zbyt duzy wynik daje jawny status `UNAVAILABLE`, bez
   przerwania transferu dotychczasowego capture.
2. Zrzut jest utrwalony w chwili capture. Rozszerzenie transferu UX
   Inspectora przekazuje go do zaufanej karty TDW ograniczonymi porcjami z
   potwierdzeniami, przypietymi do obecnego exact-origin/source, nonce i
   `captureId`. Odbiornik potwierdza capture niezaleznie od wyniku transferu
   store'a; dodatkowe komunikaty UX maja dokladny, wersjonowany ksztalt, a
   starszy sender bez store'a pozostaje obslugiwany. Dotychczasowy capture
   v1 i akcja UI Explorera zachowuja swoje kontrakty. Zrzut nie trafia do
   URL, browser storage, clipboardu ani bezposredniego requestu z badanej
   strony do backendu TDW.
3. Karta UX Inspectora trzyma zrzut tylko do jawnego startu joba. Przed
   `POST /api/ux-inspector/jobs` wysyla go do osobnego, ograniczonego
   endpointu UX Inspectora, ktory zwraca jednorazowy, krotko zyjacy
   `storeSnapshotRef`. Publiczny request startu dostaje opcjonalny ref, a
   dotychczasowy JSON request bez niego pozostaje poprawny. Backend wiaze
   ref z jednym `captureId`, originem, operatorem i jobem przed dispatch do
   AI. Nieudany upload lub zwiazanie ref uruchamia job bez store'a.
4. Backend zapisuje poprawny snapshot w neutralnym polu `storeSnapshot`
   pliku `run.json`, poza publicznym export envelope, raportem i portable
   exportem. Uzywa ograniczenia rozmiaru, atomowego zapisu, TTL dla
   niezuzytych uploadow oraz usuwania wraz z runem. Kolejne zapisy runu
   zachowuja store. Wznowiony follow-up korzysta z tego samego snapshotu;
   stary run i import bez store'a kontynuuja bez niego. Awaria storage oznacza
   `UNAVAILABLE`, nie blad analizy.
5. Poczatek promptu dostaje tylko status, czas i origin capture oraz
   ograniczona mapa najwyzej dwoch poziomow sciezek, typow i liczebnosci.
   Mapa ma limit znakow i jawne liczniki pominiec; nie zawiera duzych
   wartosci. Gdy store jest niedostepny, prompt mowi wprost: `Dane store nie
   sa dostepne w tej sesji`. Surowy snapshot nie jest logical artifact
   renderowanym inline.
6. Neutralne, read-only tools `run_store_list_paths` i
   `run_store_read_value` odczytuja pole `storeSnapshot` w `run.json` po
   model-facing `runId` i JSON Pointer. Nie przyjmuja sciezki lokalnego pliku
   ani adresu badanej strony. Kazda odpowiedz ma twardy
   limit, stronicowanie lub cursor, informacje o pominieciach i typie
   wartosci. Nie da sie odczytac calego, duzego korzenia jednym wywolaniem.
   Tools sa w allowliscie initial i follow-up tylko gdy snapshot istnieje.
7. Kanoniczny prompt pozostaje wlascicielem kontraktu `answer` i report
   tools. Nowy polski runtime skill `ux-inspector-store-grounding` opisuje
   waska procedure: najpierw potwierdzic zaleznosc w kodzie, potem odczytac
   potrzebna sciezke store'a, porownac warunek z wartoscia w chwili capture
   i oddzielic ogolna regule od konkretnego przypadku. Prompt wskazuje ten
   skill tylko dla dostepnego store'a i materialnego pytania. Dla takich
   sesji UX Inspector wlacza built-in `skill`; repository project skills
   nadal sa czytane przez scoped GitLab file-read tool i nie sa instalowane
   jako runtime skills.

Alternatywy odrzucone: pelny store w initial prompt/artifacts (koszt i
rozmiar), dopisanie go do capture lub publicznego export envelope (limity i
portable export), odczyt live store podczas tool call (brak stalego
polaczenia z badana strona i niepowtarzalne evidence), bezposredni HTTP z
badanej strony do TDW (nowa granica CORS/autoryzacji).

## Baseline przed implementacja

| Obszar | Stan obecny do zachowania |
| --- | --- |
| Wartosc i wynik | Jedno pytanie o wskazany element; jedyna sekcja raportu `answer`, business-first narracja, source gaps i visibility limits. |
| Wejscie | Browser Tools przekazuje capture v1 do `/ux-inspector` przez exact-origin/source `postMessage`; limit 128 KiB. `POST /api/ux-inspector/jobs` przyjmuje JSON z capture i parametrami AI. |
| Kontekst | Target-first resolution, focused component source pack, pinned commit, pelne repozytoryjne instructions i katalog naglowkow project skills. |
| AI | Prompt zawiera cala procedure; runtime skills i built-in `skill` sa wylaczone. Sesja ma scoped GitLab, target i report tools. |
| Follow-up | Wznawia te sama sesje i pinned source; live i historia po restarcie moga prowadzic chat, import jest read-only. |
| Historia | Local run przechowuje kopie publicznego export envelope v2; `storeSnapshot` jest osobnym opcjonalnym polem rekordu, nie trafia do exportu. Usuniecie runu usuwa jego katalog. |
| UI Explorer | Osobna akcja Browser Tools przekazuje tylko route i tagi glownego komponentu, limit 4 KiB; UI Explorer nie zna store'a. |

Baseline testow wykonany przy planowaniu: `node --test
frontend/tests/browser-tools/browser-tools.test.mjs` (13/13) oraz
`mvn -q '-Dtest=UxInspectorPromptAndSkillsTest,UxInspectorCopilotRunRequestAssemblerTest,UxInspectorJobControllerTest' test`
(exit 0). Pozostalych testow i builda nie uruchamiano na etapie planu.

## Conformance delta

| Kontrakt / warstwa | Zamierzona zmiana |
| --- | --- |
| Publiczne API/DTO | Opcjonalny `storeSnapshotRef` w UX job start i dedykowany upload; bez zmiany obecnego capture v1 i result/report shape. Stare requesty pozostaja poprawne. |
| Context/evidence | Status dostepnosci, czas, origin, ograniczona mapa; wartosci tylko przez celowane tools. Snapshot jest niezaufana obserwacja runtime. |
| Prompt/artifacts/skills | UX prompt i follow-up guidance o stanie konkretnej sesji; nowy `ux-inspector-store-grounding`, bez kopiowania procedury raportu do skilla. |
| Tools/policy/hidden scope | Dwa neutralne tools z model-facing `runId`, JSON Pointer i ograniczonymi odpowiedziami; UX initial/follow-up allowlista tylko dla dostepnego snapshotu. |
| Report/result | Sekcja `answer` i DTO bez zmian; tresc moze zawierac istotne, celowo odczytane wartosci. |
| Job/history/export | Neutralne pole `storeSnapshot` w `run.json`, TTL staging, status w prompcie; raw store wykluczony z publicznego snapshotu i exportu, legacy/import bez store'a. |
| Shared FE/UX | Browser Tools i UX receiver/facade/API; UI Explorer i jego publiczny page-context bez zmian. |
| Zaleznosci | Feature UX moze uzywac platformy i localworkspace; brak importu sibling feature. Ewentualna neutralna policy logowania nie importuje UX. |
| Znany drift | Instrukcja UX, ze nie korzysta z runtime skilli, jest zgodna z baseline; po wdrozeniu trzeba ja zmienic. Inny drift poza zakresem. |

## Konsumenci i kompatybilnosc

- Browser Tools `protocol.js`, `runtime.js`, loader/bookmarklet i ich testy;
  UX capture ingress, facade, API service, formularz i modele Angular.
- UX job controller/service/state, prompt preparation, initial i follow-up
  assemblers, tool allowlist/policy, live oraz history continuation,
  local persistence, history deletion, export/import.
- Platformowe invocation logging, tool evidence/feedback i diagnostyczny
  eksport OTLP moga zawierac odczytane wartosci store. Uzytkownik zaakceptowal
  to dla nieprodukcyjnej instancji i usunal `CopilotSensitiveToolPolicy`.
- UI Explorer konsumuje wspolny shell Browser Tools, lecz jego akcja,
  4 KiB page-context, prompt, tools, job i raport pozostaja bez zmian.
- Stary Browser Tools bez zrzutu, reczny request bez ref, stare runy oraz
  importy nadal daja analize bez store'a. Ref jest jednorazowy i nie trafia
  do persisted request snapshot ani exportu.

## Zakres

- Pobranie store przez aplikacyjny `globalThis.getStoreState()` tylko po
  wskazaniu elementu dla UX Inspectora.
- Transfer, upload i neutralny zapis jednego snapshotu per run w `run.json`.
- Ograniczona mapa w prompcie, celowany odczyt wartosci przez tools,
  prompt/skill/policy initial i follow-up.
- Status dla operatora, failure path bez blokowania analizy, ochrona danych,
  testy regresji i aktualizacja dokumentacji po wdrozeniu.

## Non-goals

- Zmiana UI Explorera albo jego page-context, promptu, skills i tools.
- Ciagly podglad lub historia akcji/reducerow/store'a; snapshot nie jest
  aktualizowany po capture.
- Iniekcja NgRx do badanej aplikacji, czytanie DevTools, cookies, storage,
  requestow sieciowych ani danych z cross-origin iframe.
- Wysylanie calego store'a do initial promptu, raportu albo portable exportu.
- Pobieranie store z przegladarki przez UI Explorer albo inne feature'y.

## Ograniczenia i ryzyka

- `getStoreState()` jest kontraktem aplikacji. Moze zwrocic dane wrazliwe,
  dane nieserializowalne albo obiekt z kosztownym `toJSON`/getterem.
  Eksporter frontendu powinien zwracac AI-safe stan; Browser Tools i backend
  stosuja limity, walidacje i redakcje znanych sekretow. Redakcje i braki
  sa jawne, bo moga ograniczyc odpowiedz. Przed implementacja ustalic i
  przetestowac domyslny limit zrzutu oraz response cap toola na fikcyjnych
  duzych danych CRM; przekroczenie daje `UNAVAILABLE`, nie cichy partial JSON.
- Zrzut jest niezaufany i moze zawierac tekst wygladajacy jak instrukcja.
  Nie zmienia tool policy, pinned repository scope ani report contract.
- Wybrany branch/commit moze roznic sie od kodu uruchomionego na stronie.
  Odpowiedz nie moze twierdzic, ze snapshot dowodzi zgodnosci rewizji.
- Raw wartosci sa celowo przekazywane Copilotowi przy odczycie toola.
  Moga pojawic sie w preview, tool evidence, feedback i diagnostycznym OTLP
  `capture-content=true`; instancja nie jest srodowiskiem produkcyjnym.
  Portable export nie kopiuje calego `storeSnapshot`, poza materialnym
  fragmentem odpowiedzi.
- Staging ma limit liczby/rozmiaru wpisow, TTL i powiazanie z operatorem,
  `captureId` oraz originem; nie moze stac sie ogolnym magazynem danych.
  Cleanup musi obejmowac orphan uploads i usuniecie runu.
- Rollback: dotychczasowy request bez ref, capture v1 i UI Explorer dzialaja
  samodzielnie. Wylaczenie nowej sciezki UX w backendzie i powrot do
  poprzedniego bundle Browser Tools/Angular przywracaja baseline; opcjonalne
  pole `storeSnapshot` jest ignorowane przez starsze odczyty i usuwane z runem.

## Kryteria akceptacji

- Przy dostepnym store AI potrafi wykazac na fikcyjnym scenariuszu CRM:
  kodowy warunek -> odpowiedni JSON Pointer -> konkretna wartosc snapshotu
  -> wyjasnienie obserwowanego zachowania, z zaznaczeniem czasu obserwacji.
- Poczatkowy prompt nie zawiera calego store'a ani nieograniczonych wartosci.
  Tool nie zwraca odpowiedzi ponad ustalony limit nawet dla pointera root.
- Brak funkcji, `null`/`undefined`, wyjatek, timeout, przekroczony limit,
  blad transferu/uploadu/persistence i brak pola `storeSnapshot` przy
  continuation nie zatrzymuja analizy; prompt zawiera jawna niedostepnosc.
- Dla poprawnego `{}`/`[]` widoczny jest pusty snapshot, bez mylenia go z
  bledem wywolania.
- Live i wznowiony po restarcie follow-up odczytuja ten sam snapshot po ID;
  import/stary run bez niego nadal dziala. Raw JSON nie wystepuje w
  publicznym job snapshot, export ani UI Explorer payload.
- Built-in `skill` jest wlaczony dla sesji UX ze store'em, a prompt wskazuje
  `ux-inspector-store-grounding`; test potwierdza zaladowanie nowego
  polskiego skilla. Wspolny katalog innych runtime skilli pozostaje dostepny
  zgodnie z platformowym kontraktem, ale nie jest sugerowany jako procedura
  UX Inspectora.

## Kroki

- [x] Krok 1: Zamknac kontrakt zrzutu i zapis w `run.json`. Dodac walidowany,
  ograniczony upload UX, jednorazowy ref oraz atomowe przypiecie snapshotu
  do nowego runu przed dispatch. Zapewnic TTL orphanow, cleanup z runem,
  zachowanie requestu bez ref i failure-as-unavailable. Dowod: testy
  MockMvc, storage/cleanup, replay i izolacji dwoch runow; kryterium:
  zredagowany JSON trafia do `storeSnapshot` w `run.json`, lecz nie do exportu.
- [x] Krok 2: Rozszerzyc tylko akcje UX Browser Tools o pojedynczy odczyt
  `getStoreState()`, serializacje, timeout/limit i potwierdzany transfer
  porcjami. Rozszerzyc UX receiver, API service, facade i widoczny status
  przed startem. Dowod: testy `frontend/tests/browser-tools`, Angular
  ingress/facade/API oraz regresja akcji UI Explorera; kryterium: kazdy blad
  store'a pozostawia poprawny UX capture i dotychczasowy UI Explorer payload.
- [x] Krok 3: Dodac neutralne read-only tools list/read
  z model-facing `runId`, JSON Pointer, stronicowaniem i output cap. Spiac
  initial/follow-up allowlisty oraz odczyt `run.json` po restarcie;
  usunac `CopilotSensitiveToolPolicy`.
  Dowod: testy pointer escaping, arrays, duzych wartosci, ID runu, replay,
  legacy/import continuation i `PackageDependencyGuardTest`;
  kryterium: tool czyta wskazany run przez wspolne API localworkspace.
- [x] Krok 4: Dodac ograniczona mape i status do UX promptu, nowy polski
  `ux-inspector-store-grounding` oraz guidance initial/follow-up. Wlaczyc
  runtime skill tylko dla sesji z dostepnym snapshotem i zachowac kanoniczny
  business-first report contract. Dowod: prompt/skill contract, session
  config create/resume oraz dwa scenariusze CRM (warunek zalezy i nie zalezy
  od store'a); kryterium: wynik odroznia regule od stanu sesji, a brak
  store'a nie powoduje probe odczytu toola.
- [x] Krok 5: Wykonac regresje i architecture diff calego przekroju,
  zanonimizowac wszystkie nowe fixture'y CRM, potwierdzic policy OTLP,
  zaktualizowac `architecture/ux-inspector-runtime-flow.md`, odpowiednie
  lokalne `AGENTS.md`, Browser Tools README i docs index o stan wynikowy.
  Dowod: `node --test frontend/tests/browser-tools/browser-tools.test.mjs`,
  `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build`, nastepnie
  `mvn -q -Pbackend-dev clean package`, plus kontrola diffu i wyszukanie
  dawnych danych w testach/dokumentacji; kryterium: UX dziala z i bez store'a,
  UI Explorer pozostaje identyczny, wszystkie wymagane testy przechodza.

## Wynik wdrozenia

- Browser Tools 1.2.0 zamraza store dla UX Inspectora; UI Explorer zachowuje
  dotychczasowy kontrakt.
- Upload pozostaje przy UX Inspectorze, poniewaz weryfikuje jego `captureId` i
  origin. Snapshot jest zapisywany w `LocalAnalysisRunRecord.storeSnapshot`,
  a neutralne `run_store_*` tools czytaja go z `run.json` po model-facing
  `runId`. Kolejne zapisy runu i follow-up zachowuja to pole. Platformowa
  `CopilotSensitiveToolPolicy` zostala usunieta.
- Zweryfikowano `node --test frontend/tests/browser-tools/browser-tools.test.mjs`
  (14/14), Angular `npm test -- --watch=false` (642/642 w pelnym udanym
  przebiegu przed zmiana sposobu zapisu; pozniejsze proby: 640/642 i 638/642
  przez timeout 5 s w niezmienianych testach), `npm run build`, `mvn -q test`
  oraz `mvn -q -Pbackend-dev clean package` (exit 0). Polecenia Angular
  uruchomiono z `frontend/`, poniewaz wariant `npm --prefix frontend` z
  katalogu root sporadycznie konczyl sie bledem dostepu do plikow w tym
  srodowisku.
- Ograniczenie obecnej architektury: `AnalysisAiAuthRefResolver` zwraca
  wspolna tozsamosc workspace. Jednorazowy ref jest z nia zwiazany oraz z
  `captureId` i originem, ale nie rozroznia osobnych operatorow korzystajacych
  z tej samej instancji TDW. Wymaga to odrebnej tozsamosci operatora, jezeli
  produkt zacznie obslugiwac wielouzytkownikowy workspace.
