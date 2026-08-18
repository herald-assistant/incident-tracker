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
- export: wspolny `GET /api/analysis/runs/{analysisId}/export`,
- modele AI: wspolny `GET /api/analysis/ai/options`,
- historia: wspolne `/api/analysis/runs/**` z feature id
  `delivery-complexity-assessment`.

Request startu zawiera tylko `jiraProject`, `fromDate`, `toDate`, `model` i
opcjonalny `reasoningEffort`. UI nie przyjmuje JQL.
Filtry raportu po zespole Jira i autorze MR sa lokalnym zawezeniem
widocznego wyniku po wykonaniu analizy; nie zmieniaja requestu startu,
JQL ani promptu AI.

## Interpretacja i non-goals

Wynik opisuje obserwowalna, semantyczna zlozonosc dostarczonej zmiany. Ma
pomagac w retrospektywnej analizie zakresu dostawy wraz z coverage, confidence,
visibility limits i kosztem AI. Nie jest estymacja czasu, predykcja effortu,
velocity ani miara produktywnosci osoby, zespolu, tribe'a lub vendora.

Feature nie rekonstruuje czasu pracy, nie kalibruje sie do historycznych Story
Points, nie analizuje worklogow i komentarzy oraz nie pozwala recznie
korygowac wyniku w UI. POC nie ma follow-up chat, trwalego cache miedzy jobami
ani durable kolejki wznawianej po restarcie backendu.

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

Local run przechowuje sanitizowany export envelope V2 i nie ma continuation.
Otwarcie historii odtwarza formularz oraz ostatni snapshot. Dla stanu
nieterminalnego UI probuje polling live joba; restart backendu nie wznawia
pracy, ale zapis pozostaje czytelny.

UI pozwala wyeksportowac terminalny run przez wspolny endpoint Analysis
History. Import przyjmuje tylko terminalny envelope o dokladnym schemacie,
wersji, payload type i result contract V2 oraz sprawdza spojnosci podstawowych
danych i agregatu. Backend nadaje zaimportowanemu snapshotowi nowy `jobId`,
zapisuje go od razu jako osobny local run i zwraca wynik tylko do odczytu.
Import nie rejestruje live joba, nie uruchamia pollingu, Jira, GitLab ani AI i
nie dodaje continuation. Gdy local workspace jest wylaczony albo zapis sie nie
powiedzie, import jest odrzucany zamiast zwracac wynik niewidoczny w historii.

Przed kazdym wywolaniem modelu feature zapisuje na Delivery Unit dokladny
`preparedPrompt` i `promptPreparedAt`, a dopiero potem uruchamia sesje. Krok
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
assessment: bez komentarzy, parent i subtasks, z opisem, acceptance criteria,
issue links, remote links, jawnie powiazanymi stronami Confluence oraz
opcjonalnym polem zespolu z
`delivery-complexity-assessment.jira-team-field-id`. Pole zespolu jest
metadana raportu i filtra, nie evidence dla AI. Stary profil detailed
pozostaje kontraktem Change Verification.

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

Artifacts i prompt nie zawieraja istniejacych Story Points, worklogow,
assignee, autorow, reviewerow, komentarzy ani pola zespolu. Wynik nie moze
sluzyc do rankingu osob lub zespolow. Team Jira i autor MR pozostaja tylko
deterministycznymi metadanymi UI do filtrowania obserwowalnej zlozonosci.

## AI i scoring

Ocenialna Delivery Unit uruchamia jedna nowa sesje Copilota i wysyla jedna
inicjalna wiadomosc. Feature sklada w niej instrukcje, effective tresc skilla
`delivery-complexity-assessment-evaluator`, inline artifacts, kod MR oraz
kontrakt odpowiedzi. Sesja ma pusta allowliste tools, w tym wylaczony built-in
`skill`, nie dostaje katalogow skilli i nie posiada initial reportu. Model nie
wykonuje pobrania skilla, report tools ani innych krokow agentowych; pierwsza
odpowiedz ma byc finalnym JSON-em.

Jira, GitLab, Confluence, filesystem, shell i terminal nie sa dostepne w
sesji. AI zwraca classification, confidence, evidence/quality/visibility oraz
szesc wymiarow 0-4 dla `DELIVERY`. Nie zwraca DSP ani `score100`. Zwalidowany
JSON jest mapowany bezposrednio na wynik jednostki; feature nie tworzy
rownoleglego `AnalysisReport` ani nie wykonuje kolejnego wywolania modelu lub
toola.

Skala jest zakotwiczona behawioralnie osobno dla kazdego wymiaru. Skill
definiuje obserwowalne kotwice `0`, `2` i `4`; `1` i `3` sa poziomami
posrednimi wymagajacymi porownania z obiema sasiednimi kotwicami. Wynik `0`
oznacza obserwowalny brak istotnej zmiany, a nie brak danych. Syntetyczne
przypadki kalibracyjne stabilizuja znaczenie skali, ale nie zastepuja evidence
biezacej Delivery Unit i nie wykorzystuja historycznych Story Points.

Dla kazdego niezerowego wymiaru AI zwraca `evidenceSummary` w formacie
`dimension | artifact#section | observed fact`. Parser wymaga kompletnego
pokrycia niezerowych wymiarow i logicznej sciezki `delivery-complexity/`.
Brak takiego evidence uniewaznia odpowiedz zamiast pozwalac na scoring
niezakotwiczonej liczby.

Ten sam zwalidowany skill jest widoczny w read-only ekranie `Platform / AI
Skills`. Frontendowa projekcja grupuje
`delivery-complexity-assessment-evaluator` jako rodzine
`Delivery Complexity Assessment` i odpowiedzialnosc `Assessment`; pozostaje
to etykieta nawigacyjna, a nie runtime selection skilla.

Backend liczy `score100` wagami `10/25/25/15/15/10` i mapuje wynik na
`0/1/2/3/5/8/13`. `INSUFFICIENT_EVIDENCE` przechodzi do `NOT_SCORABLE`, a
niepoprawna odpowiedz AI konczy tylko jednostke jako `FAILED`.
Kazdy terminalny wynik uruchomionej sesji AI zachowuje jej `usage` i visibility
limits, rowniez dla `INSUFFICIENT_EVIDENCE` i `EXCLUDED`.

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

Snapshot publikuje postep Jira, kroki, context, activity, czastkowe jednostki i
aggregate. Visibility limits pozostaja przy jednostkach, ktorych widocznosci
dotycza. Aggregate backendowy zawiera total DSP, distribution, coverage,
confidence, liczniki `EXCLUDED`/`NOT_SCORABLE`/`FAILED` oraz zsumowane
usage/cost dla calego runu. UI moze deterministycznie przeliczyc ten sam ksztalt
agregatu dla widocznych jednostek po filtrze. Parent job konczy sie `COMPLETED`,
`COMPLETED_WITH_WARNINGS` albo `FAILED`.

Tokeny, duration, liczba wywolan i SDK `cost` sa sumowane dokladnie raz z usage
kazdej jednostki. Pole SDK `cost` oznacza sume mnoznikow rozliczeniowych modelu,
nie kwote USD. UI pokazuje szacowany koszt tokenow wyliczony z
input/output/cache i cennika modelu osobno dla jednostki oraz zbiorczo na dole.

Glowny wynik UI jest jedna rozwijalna tabela Delivery Units. Wiersz pokazuje
issue, MR-y, status, DSP i koszt AI; ikona ostrzezenia przy statusie sygnalizuje
quality flags, visibility limits albo blad jednostki. Rozwiniecie pokazuje
MR-y jako linki oraz tylko dostepne Evidence, Quality flags, Visibility limits
i Warnings. Nad tabela sa filtry po zespole Jira i autorze MR. Po wybraniu
filtra UI pokazuje te sama tabele, wynik zbiorczy i koszt w ksztalcie
odfiltrowanym do widocznych Delivery Units. Gdy widoczne issue ma MR-y wiecej
niz jednego autora, UI pokazuje ostrzezenie informacyjne, bo DSP dotyczy calej
jednostki i nie jest dzielone pomiedzy osoby. Pod tabela znajduje sie prosty
wynik zbiorczy bez ponownego wyliczania konkretnych issue, a na samym dole koszt
widocznego zakresu albo calej analizy bez filtra. Nie ma osobnego visibility
band, assessment summary, report meta ani drugiej listy jednostek.

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
