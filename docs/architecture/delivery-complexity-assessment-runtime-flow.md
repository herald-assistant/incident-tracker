# Delivery Complexity Assessment Runtime Flow

## Cel i kontrakt

`Delivery Complexity Assessment` mierzy obserwowalna, semantyczna
zlozonosc zmian dostarczonych w projekcie Jira i zakresie lokalnych dat.
`Delivered Story Points` jest metryka wyniku, nie nazwa feature'a i nie
odtwarza czasu pracy ani istniejacych Story Points z Jira.

Publiczne wejscia:

- UI: `GET /delivery-complexity-assessment`,
- start: `POST /api/delivery-complexity-assessment/jobs`,
- polling: `GET /api/delivery-complexity-assessment/jobs/{jobId}`,
- import: `POST /api/delivery-complexity-assessment/imports`,
- przenosny export JSON: wspolny
  `GET /api/analysis/runs/{analysisId}/export`,
- biznesowy export CSV: generowany przez UI z terminalnego snapshotu bez
  dodatkowego endpointu,
- modele AI: wspolny `GET /api/analysis/ai/options`,
- historia: wspolne `/api/analysis/runs/**` z feature id
  `delivery-complexity-assessment`.

Request startu zawiera tylko `jiraProject`, `fromDate`, `toDate`, `model` i
opcjonalny `reasoningEffort`. UI nie przyjmuje JQL.
Po wyborze modelu bez wsparcia reasoning UI czysci i wylacza pole effort, a
backend nie uzupelnia go globalnym defaultem. Do Copilot SDK trafia wtedy
jawny model bez `reasoningEffort`.
Filtry raportu po zespole Jira i autorze MR sa lokalnym zawezeniem
widocznego wyniku po wykonaniu analizy; nie zmieniaja requestu startu,
JQL ani promptu AI.

## Interpretacja i non-goals

Wynik opisuje obserwowalna, semantyczna zlozonosc dostarczonej zmiany. Ma
pomagac w retrospektywnej analizie zakresu dostawy wraz z coverage, confidence,
visibility limits i kosztem AI. Nie jest estymacja czasu, predykcja effortu,
velocity ani miara produktywnosci osoby, zespolu, tribe'a lub vendora.

Feature nie rekonstruuje historii czasu pracy, nie kalibruje sie do
historycznych Story Points, nie analizuje worklogow i komentarzy oraz nie
pozwala recznie korygowac wyniku w UI. Zapisuje jedynie biezacy snapshot
`timespent` i estimate z Jira jako metadata raportowa do przyszlych analiz.
POC nie ma follow-up chat, trwalego cache miedzy jobami ani durable kolejki
wznawianej po restarcie backendu.

## Start i historia

Start wykonuje synchronicznie preflight feature flag, maksymalnego zakresu
dat, local workspace oraz auth Copilota. Nastepnie:

1. tworzy in-memory job `QUEUED`,
2. zapisuje pierwszy snapshot pod `runs/<jobId>/run.json`,
3. dopiero po udanym zapisie zleca prace w tle,
4. zwraca `202 Accepted` z tym samym `jobId`.

Blad pierwszego zapisu usuwa live job i odrzuca start. Kazda pozniejsza
zmiana discovery, jednostki, AI activity, usage albo statusu zapisuje kolejny
snapshot tego samego runu. Zapisy sa serializowane na stanie joba, aby
rownolegle konczace sie jednostki nie cofnely `run.json`.

Local run przechowuje sanitizowany export envelope V1 i nie ma continuation.
Otwarcie historii odtwarza formularz oraz ostatni snapshot. Dla stanu
nieterminalnego UI probuje polling live joba; restart backendu nie wznawia
pracy, ale zapis pozostaje czytelny.

UI pozwala wyeksportowac terminalny run przez wspolny endpoint Analysis
History. Import przyjmuje tylko terminalny envelope o dokladnym schemacie,
wersji, payload type i result contract V1 oraz sprawdza spojnosci podstawowych
danych i agregatu. Backend nadaje zaimportowanemu snapshotowi nowy `jobId`,
zapisuje go od razu jako osobny local run i zwraca wynik tylko do odczytu.
Import nie rejestruje live joba, nie uruchamia pollingu, Jira, GitLab ani AI i
nie dodaje continuation. Gdy local workspace jest wylaczony albo zapis sie nie
powiedzie, import jest odrzucany zamiast zwracac wynik niewidoczny w historii.
Issue snapshot zawiera opcjonalne `timeSpentSeconds`,
`originalEstimateSeconds`, `remainingEstimateSeconds` oraz
`timeTrackingCapturedAt`. Pola sa opcjonalne w aktualnym envelope V1; brak danych Jira mapuje sie na `null`.

Obok przenosnego JSON UI udostepnia niewersjonowany, biznesowy CSV calego runu
z jednym wierszem na issue. CSV nie jest formatem importu i nie zalezy od
aktywnych filtrow raportu. Zawiera zapisane metadata Jira issue, wspolna liste
linkow MR z Delivery Unit, stabilnie sparowane listy `mergeRequestAuthorIds`
i `mergeRequestAuthorNames`, snapshot `timeSpentSeconds`,
`originalEstimateSeconds`, `remainingEstimateSeconds` i
`timeTrackingCapturedAt`, wymiary oceny, `score100`,
`deliveredStoryPoints` oraz `pointsForAggregation`. Ocena pozostaje ocena
Delivery Unit i jest powtarzana przy jej issue. Aby analiza w Excelu nie
zwielokrotniala DSP, `pointsForAggregation` jest wypelnione tylko dla issue z
najpozniejszym `doneAt`, a przy remisie z leksykograficznie najmniejszym
`issueKey`. CSV uzywa separatora `;`, UTF-8 z BOM oraz cytowania wartosci
z separatorem, cudzyslowem albo nowa linia. Ten biznesowy CSV moze byc lokalnie
wczytany przez `Delivery Complexity Trends`; nie zmienia to jego
niewersjonowanego, niezaleznego od backendowego importu charakteru.

Przed kazdym wywolaniem modelu feature zapisuje na Delivery Unit dokladny
`aiInvocations[].preparedPrompt` i `startedAt`, a dopiero potem uruchamia sesje. Krok
`AI_INPUT_PREPARATION` w bocznym przebiegu analizy pokazuje wszystkie
przygotowane wiadomosci z mozliwoscia rozwiniecia i skopiowania. Prompt zawiera
pelna effective tresc skilla oraz dane konkretnej jednostki, wiec snapshot jest
jednoczesnie audytowalnym zapisem rzeczywistego inputu AI.

## Jira discovery

Neutralny `integrations.jira.JiraIssueSearchPort` mapuje typowany request na
kontrolowany JQL:

```text
project = "<PROJECT>"
AND status = <configured done status id/name>
AND resolved >= "<fromDate>"
AND resolved < "<toDate + 1 day>"
ORDER BY key ASC
```

Wartosc statusu pochodzi z
`delivery-complexity-assessment.jira-done-status-id`. Numeryczny status id
jest wstawiany do JQL bez cudzyslowu, a nazwa statusu jest escapowana i
cytowana; domyslna wartosc pozostaje `Done`.

Adapter wykonuje paginowany `POST /rest/api/2/search`, zwraca effective JQL,
total, truncation i limitations. JQL jest prefiltracja. Dla kazdego kandydata
feature dodatkowo:

1. potwierdza biezaca kategorie `Done`,
2. pobiera changelog i mapuje status id na status category przez Jira REST,
3. wybiera ostatnie przejscie do kategorii `Done`,
4. sprawdza granice `[fromDate 00:00, toDate + 1 day 00:00)` w
   `delivery-complexity-assessment.time-zone`,
5. odrzuca issue z ucietym changelogiem albo niepotwierdzonym `doneAt`.

Material issue jest pobierany istniejacym `JiraIssuePort`, ale profilem
assessment: bez komentarzy, z bezposrednim parent i bezposrednimi subtasks,
z opisem, acceptance criteria,
issue links, remote links, jawnie powiazanymi stronami Confluence oraz
opcjonalnym polem zespolu z
`delivery-complexity-assessment.jira-team-field-id`. Pole zespolu jest
metadana raportu i filtra, nie evidence dla AI. Neutralny material zapisuje
tez widoczne w chwili pobrania `timespent`, original estimate i remaining
estimate wraz z timestampem snapshotu. Te pola sa metadanymi raportowymi i nie
sa evidence dla AI. Stary profil detailed pozostaje kontraktem Change
Verification.

Profil assessment rozwija tylko jeden poziom relacji: material parent nie
zawiera rodzenstwa ani dalszych przodkow, a material child nie rozwija
kolejnego poziomu ani parent. Ustawienia pobierania komentarzy, issue links
i remote links obowiazuja takze przy odczytach powiazanych. Relacje pochodza
z pol Jira `parent` i `subtasks`, bez interpretowania nazw typow.
Flaga `issuetype.subtask` pozwala zglosic brak parent key. Nieudany odczyt
powiazanego zadania zachowuje jego znany klucz i relacje, z pustym materialem
oraz jawnym limitation; nie usuwa poprawnie zakwalifikowanego zadania.

Przetwarzanie kandydatow issue po wyszukaniu JQL jest wykonywane przez
dedykowany, ograniczony executor source discovery. Limit
`delivery-complexity-assessment.max-parallel-source-requests` kontroluje
rownolegle pobieranie status history, materialu issue i powiazanych MR-ek, aby
nie zamienic oszczednosci czasu w niekontrolowany fan-out do Jiry albo GitLaba.
Wynik jest skladany z powrotem w kolejnosci zwroconej przez Jira search, a
progress discovery raportuje monotoniczny licznik faktycznie zakonczonych
kandydatow.

## GitLab i Delivery Units

Dla zakwalifikowanego issue feature wywoluje
`GitLabRepositoryPort.findMergeRequestsByIssueKey` w grupie z konfiguracji.
Do dalszego flow przechodza tylko MR-y ze stanem `merged` i jawnym `mergedAt`.
Adapter GitLab publikuje metadata, `author.id`, `author.name`, changed paths i
diff. Dane autorow sa metadana raportu i filtra osoby; commit authors ani dane
autorow MR nie sa renderowane do evidence assessmentu.

`DeliveryUnitBuilder` buduje spojne komponenty grafu `issue <-> MR`.
To samo id MR-a, URL albo para `projectPath!iid` jest jedna tozsamoscia, wiec
wspolny MR laczy issue w jedna Delivery Unit i jest liczony raz.

## Evidence i prywatnosc

Kazda jednostka dostaje pelny pakiet inline artifacts z danych zwroconych przez
integracje:

- Jira intent: summary, opis, acceptance criteria i jawne dokumenty,
- merged MR metadata i changed paths,
- wszystkie changed paths i pelna tresc dostepnych diffow,
- visibility limits wynikajace wylacznie z partial source failures albo
  ograniczen zgloszonych przez integracje.

Jira artifact jawnie wskazuje oceniane zadania z `unit.issues()` oraz ich
potwierdzone role nadrzedne/podrzedne. Dodatkowy parent i children sa oznaczone
`KONTEKST POZA ZAKRESEM OCENY`, chyba ze ich klucz juz nalezy do jednostki.
Nazwy typow Jira nie sa renderowane jako metadata dla AI; oryginalny
`issueType` pozostaje w operatorskim snapshotcie i CSV.
Kazde zadanie ma jedna sekcje z zachowaniem odmiennych tresci z odczytow,
a identyczne dokumenty sa renderowane raz z lista powiazanych zadan.
Brak potwierdzonej relacji nie upowaznia modelu do zgadywania hierarchii.
Opis rodzica wyjasnia intencje, ale nie dowodzi dostarczenia jego calego
zakresu. Scoring dotyczy union zachowania potwierdzonego przez merged MR-y
biezacej jednostki. Hierarchia nie laczy dodatkowych Delivery Units i nie
zmienia qualification, zakresu dat ani wyszukiwania MR-ow.

Feature renderuje te logiczne pliki bezposrednio w finalnym prompcie miedzy
jawnymi markerami artifact. `CopilotRunRequest.artifactContents` zachowuje ich
projekcje diagnostyczna, ale runtime nie uzywa SDK attachments jako kanalu
evidence.

Builder pakietu nie przycina liczby issue, MR-ow, dokumentow ani plikow oraz nie
skraca opisow i diffow. Jezeli integracja zrodlowa zwrocila dane niepelne, jej
ograniczenie pozostaje jawne zamiast byc maskowane. Brak MR-a albo changed files
daje `NOT_SCORABLE`. Zmiany skladajace sie wylacznie z
generated/build/dist/lock/minified/binary artifacts sa `EXCLUDED` bez
uruchamiania AI.

Artifacts i prompt nie zawieraja istniejacych Story Points, snapshotu
`timespent` ani estimate, worklogow, assignee, autorow, reviewerow, komentarzy
ani pola zespolu. Wynik nie moze
sluzyc do rankingu osob lub zespolow. Team Jira i autor MR pozostaja tylko
deterministycznymi metadanymi UI do filtrowania obserwowalnej zlozonosci.

## AI i scoring

Feature sklada instrukcje, effective skill
`delivery-complexity-assessment-evaluator`, inline artifacts i kontrakt JSON.
Kazde wywolanie ma pusta allowliste tools, wylaczony built-in `skill`, brak
katalogow skilli i initial reportu. Jira, GitLab, Confluence, filesystem,
shell i terminal sa niedostepne w sesji. Odpowiedz jest finalnym JSON-em
danego etapu. Feature nie tworzy rownoleglego `AnalysisReport`.
Finalne AI zwraca classification, confidence, evidence/quality/visibility
oraz siedem wymiarow 0-4 dla `DELIVERY`, bez DSP ani `score100`.

Skala jest zakotwiczona behawioralnie osobno dla kazdego wymiaru. Skill
definiuje obserwowalne kotwice `0`, `2` i `4`; `1` i `3` sa poziomami
posrednimi wymagajacymi porownania z obiema sasiednimi kotwicami. Wynik `0`
oznacza obserwowalny brak istotnej zmiany, a nie brak danych. Syntetyczne
przypadki kalibracyjne stabilizuja znaczenie skali, ale nie zastepuja evidence
biezacej Delivery Unit i nie wykorzystuja historycznych Story Points.

`parameterizationComplexity` jest osobnym wymiarem wyniku. Ocenia faktycznie
dodana lub zmieniona mozliwosc sterowania zachowaniem przez properties,
konfiguracje bazodanowa, tabele decyzyjna, DMN albo silnik regul. Poziom rosnie
od pojedynczego parametru z bezposrednim skutkiem do wersjonowanych,
dynamicznie przeladowywanych regul z zaleznosciami, priorytetami, konfliktami,
datami obowiazywania, rollbackiem albo audytem.

Dla kazdego niezerowego wymiaru AI zwraca `evidenceSummary` w formacie
`dimension | artifact#section | observed fact`. Jest to kontrakt jakosciowy
promptu i material wyjasniajacy wynik, ale nie wejscie do deterministycznego
scoringu. Parser nie odrzuca poprawnej oceny przez wariant formatowania, brak
lub nieugruntowana referencje w polach opisowych. Twardo waliduje tylko dane
niezbedne do obliczen: klasyfikacje, komplet wymiarow `DELIVERY` w zakresie
`0-4` oraz `confidence` w zakresie `0-1`. Niepoprawne opcjonalne kolekcje
opisowe sa pomijane, a surowa odpowiedz pozostaje dostepna do diagnostyki.
Ogrodzenie kodu Markdown z etykieta `json` jest traktowane jako format
transportowy i usuwane przez wybranie obiektu pomiedzy pierwsza `{` i ostatnia
`}`.
Niestandardowe separatory Unicode oraz whitespace poza stringami sa przed
parsowaniem normalizowane do legalnego whitespace JSON; surowa odpowiedz nie
jest przy tym modyfikowana.
Jezeli blad skladni JSON wystapi dopiero po kompletnych polach scoringowych,
parser odzyskuje je przez strumieniowy parser Jacksona i pomija uszkodzony
ogon opisowy. Nie odzyskuje ani nie zgaduje niekompletnych pol scoringowych.

Ten sam zwalidowany skill jest widoczny w read-only ekranie `Platform / AI
Skills`. Frontendowa projekcja grupuje
`delivery-complexity-assessment-evaluator` jako rodzine
`Delivery Complexity Assessment` i odpowiedzialnosc `Assessment`; pozostaje
to etykieta nawigacyjna, a nie runtime selection skilla.

Backend liczy `score100` wagami `10/20/20/15/10/10/15` i mapuje wynik na
`0/1/2/3/5/8/13`. `INSUFFICIENT_EVIDENCE` przechodzi do `NOT_SCORABLE`, a
niepoprawna odpowiedz AI konczy tylko jednostke jako `FAILED`.
Kazdy terminalny wynik uruchomionej sesji AI zachowuje jej `usage` i visibility
limits, rowniez dla `INSUFFICIENT_EVIDENCE` i `EXCLUDED`.
Surowa odpowiedz modelu jest przypisywana do wywolania w `aiInvocations` Delivery Unit i zapisywana w
Analysis History przed uruchomieniem parsera odpowiedzi. Dlatego pozostaje
dostepna w API i eksporcie rowniez wtedy, gdy niepoprawny JSON albo niezgodny
kontrakt konczy jednostke statusem `FAILED`.

## Rownoleglosc i wynik

Source discovery ma osobny executor dla kandydatow issue, a AI assessment ma
osobny executor dla Delivery Units. Oba fan-outy sa ograniczone properties,
zeby niezalezne joby i wolne integracje zewnetrzne nie zalaly runtime'u
nieograniczona liczba requestow.

Jednostki sa wykonywane przez dedykowany, ograniczony executor z
konfigurowalnym parallelism, kolejka i timeoutem. Timeout jednostki zaczyna
sie dopiero, gdy worker faktycznie rozpocznie jej wykonanie; czas oczekiwania w
kolejce nie jest traktowany jako czas pracy konkretnej Delivery Unit. Status
jednostki jest monotoniczny; spozniony wynik po timeoutcie nie moze nadpisac
`FAILED`.
Awaria jednej jednostki nie zatrzymuje pozostalych.

Kazda rownolegla jednostka korzysta ze wspolnego lifecycle klienta Copilot.
Na Windows platforma uruchamia bezposrednio bezwzgledny `copilot.exe`, bez
posredniego `cmd.exe`, a po wyniku terminalnym albo bledzie startu wykonuje
ograniczony czasowo `stop()` z fallbackiem `forceStop()`. Zapobiega to
pozostawianiu osobnego procesu CLI po zakonczonej Delivery Unit.

Snapshot publikuje postep Jira, kroki, context, activity, czastkowe jednostki i
aggregate. Visibility limits pozostaja przy jednostkach, ktorych widocznosci
dotycza. Aggregate backendowy zawiera total DSP, distribution, coverage,
confidence, liczniki `EXCLUDED`/`NOT_SCORABLE`/`FAILED` oraz zsumowane
usage/cost dla calego runu. UI moze deterministycznie przeliczyc ten sam ksztalt
agregatu dla widocznych jednostek po filtrze. Parent job konczy sie `COMPLETED`,
`COMPLETED_WITH_WARNINGS` albo `FAILED`.

Tokeny, duration, liczba wywolan i kredyty Copilota sa sumowane dokladnie raz
z usage kazdej jednostki. Jezeli usage dowolnej jednostki nie zawiera kredytow,
agregat kredytow pozostaje nieznany. Wspolny aside pokazuje kredyty i ekwiwalent
USD przy zalozeniu 100 kredytow = 1 USD.

Glowny wynik UI jest jedna rozwijalna tabela Delivery Units. Wiersz pokazuje
issue, MR-y, status i DSP; ikona ostrzezenia przy statusie sygnalizuje
quality flags, visibility limits albo blad jednostki. Rozwiniecie pokazuje
MR-y jako linki oraz tylko dostepne Evidence, Quality flags, Visibility limits
i Warnings. `Unit insights` zawiera wspolne szczegoly wywolan AI z domyslnie zwinietym
`Raw AI response` kazdej sesji, przeznaczone do diagnostyki. Nad tabela sa filtry po zespole Jira i
autorze MR. Po wybraniu
filtra UI pokazuje te sama tabele i wynik zbiorczy w ksztalcie
odfiltrowanym do widocznych Delivery Units. Gdy widoczne issue ma MR-y wiecej
niz jednego autora, UI pokazuje ostrzezenie informacyjne, bo DSP dotyczy calej
jednostki i nie jest dzielone pomiedzy osoby. Pod tabela znajduje sie prosty
wynik zbiorczy bez ponownego wyliczania konkretnych issue. Koszt w aside dotyczy
calego wykonania, niezaleznie od filtra widoku. Nie ma osobnego visibility
band, assessment summary, report meta ani drugiej listy jednostek.

## Material przekraczajacy budzet modelu

`CopilotPromptBudgetService` dostarcza neutralna estymacje pelnego wejscia:
prompt, durable system instructions, definicje tools i rezerwa. Limit promptu
oraz outputu pochodzi z dynamicznego `models.list`, rowniez bez long tieru.
Domyslny `prompt-budget-safety-ratio=0.80` i rezerwa 16000 tokenow chronia
przed roznica estymacji i rzeczywistego tokenizowania. Limit UNKNOWN pozwala
na jedna zwykla probe; nie oznacza potwierdzonej pojemnosci. Platforma wybiera
i potwierdza `long_context`; odrzucenie tieru zwraca zwykly budzet przed
wyslaniem nadmiernego promptu. Feature nie ustawia tieru i nie koduje modeli.

Mala jednostka nadal ma jedno wywolanie `ASSESSMENT` z finalnym JSON-em.
Duza jednostka jest planowana wedlug rozmiaru pelnych promptow: najpierw
rozlaczne MR-y, a gdy pojedynczy MR nie miesci sie, jego pelne zmienione
pliki. Metadata MR sa zachowane. Kazda `EVIDENCE_PART` dostaje identyczny
pelny Jira/Confluence, hierarchie semantyczna, manifest calej dostawy i jawny
zakres implementacji. Kod nie jest przycinany. Nierozdzielny plik/metadata
(`EVIDENCE_ATOM_TOO_LARGE`) albo sam bazowy kontekst
(`JIRA_CONTEXT_TOO_LARGE`) konczy jednostke czytelna diagnostyka rozmiaru.

Czesci zwracaja `DeliveryPartFindings`, bez punktow: coverage, sufficientEvidence,
findings (behaviorId, dimension, fact, references, dependencies), confidence
i visibilityLimits. Parser wymaga dokladnej coverage i referencji z zakresu.
`SYNTHESIS` otrzymuje pelny Jira, manifest i wszystkie zweryfikowane ustalenia;
deduplikuje zachowania i zwraca dotychczasowy finalny kontrakt. Backend
wykonuje scoring raz. `MULTIPART_SYNTHESIS` i visibilityLimits ujawniaja,
ze synteza korzysta z ustalen, bez bezposredniego odczytu calego surowego diffu.
Confidence nie przekracza minimum confidence czesci i syntezy.

Nadmierne ustalenia sa redukowane w budzetowanych grupach (`REDUCTION`),
bez scoringu. Walidacja zachowuje coverage, referencje, pary behaviorId/wymiar
i zaleznosci; wynik musi byc mniejszy. Nie ma ciecia listy ustalen.
Awaria lub niepoprawny JSON jednej czesci blokuje finalna ocene, zachowujac
dotychczasowy material. Niewystarczajace evidence daje `NOT_SCORABLE`.

Wywolania sa sekwencyjne w jednym bounded workerze jednostki, kazde w nowej
sesji, z pusta allowlista tools i katalogow skilli. Effective rubric jest
snapshotem pobranym raz dla jednostki. Jeden `item-timeout` od startu workera
obejmuje przygotowanie, czesci i synteze; timeout przerywa worker, a deadline
blokuje nowe sesje i ogranicza pozostaly czas `sendAndWait`.
Domyslne limity: 32 wywolania na jednostke, 3 korekty context overflow,
4 poziomy redukcji. Tylko jednoznaczny `CopilotPromptOverflowException`
koryguje budzet i podzial odrzuconej czesci, w nowej sesji. Rate limit,
auth, transport i parsowanie nie sa ponawiane jako context overflow.
`oversized-evidence-enabled=false` wylacza podzial z jawna diagnostyka.

Kazda jednostka zapisuje liste `aiInvocations`: id, rola, status, numer/liczba
czesci, zakres MR/plikow, estymacja/limit/rezerwa, preparedPrompt, rawResponse,
ustalenia, visibilityLimits, sessionId, usage, blad i timestamps. Prompt jest
zapisany przed wykonaniem, rawResponse przed parsowaniem, takze po awarii.
Aktualizacja tego samego id nie zwielokrotnia usage. Zuzycie wywolan jest
sumowane; obserwacje limitu/wypelnienia okna maja maksimum zamiast sumy.
Nie ma niewidocznego magazynu sesyjnej telemetryki.

Aktualny publiczny format oraz import/export pozostaja V1. `aiInvocations`
zastepuje pojedyncze unit-level preparedPrompt/promptPreparedAt/rawAiResponse;
brak tej listy jest bledem importu. Nie ma migratora starego formatu.
Zatwierdzony cleanup dotyczy tylko starych lokalnych wynikow i artefaktow obu
assessmentow; inne feature'y i CUSTOM skille pozostaja poza nim.

UI zachowuje jeden wiersz i jedna finalna ocene Delivery Unit. Etykieta
`Analiza w N czesciach` wskazuje podzial. Wspolny `analysis-ai-invocations`
pokazuje zakres, status, ustalenia, raw response oraz wejscie kazdego wywolania;
shared aside udostepnia wszystkie prepared prompts i caly koszt. Progress
pokazuje czesci oraz laczenie wynikow. Agregaty, CSV i Trends nadal licza
jednostke raz, niezaleznie od liczby wywolan AI.

## Ownership

- `features.deliverycomplexityassessment` posiada request, discovery
  orchestration, Delivery Units, evidence, AI policy, scoring, job state,
  runs mapping, result i API.
- `integrations.jira` posiada typed search/JQL, paging, material profile i
  status history REST.
- `integrations.gitlab` posiada MR discovery, metadata, changed files i diff.
- `aiplatform.copilot` pozostaje neutralnym runtime.
- `localworkspace.analysisruns`, `shared.ai` i frontendowe komponenty
  przebiegu pozostaja wspolne.

Feature nie importuje sibling feature'ow.
