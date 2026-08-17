# Delivery Effectiveness Assessment Runtime Flow

## Cel i kontrakt

`Delivery Effectiveness Assessment` mierzy obserwowalna, semantyczna
zlozonosc zmian dostarczonych w projekcie Jira i zakresie lokalnych dat.
`Delivered Story Points` jest metryka wyniku, nie nazwa feature'a i nie
odtwarza czasu pracy ani istniejacych Story Points z Jira.

Publiczne wejscia:

- UI: `GET /delivery-effectiveness-assessment`,
- start: `POST /api/delivery-effectiveness-assessment/jobs`,
- polling: `GET /api/delivery-effectiveness-assessment/jobs/{jobId}`,
- modele AI: wspolny `GET /api/analysis/ai/options`,
- historia: wspolne `/api/analysis/runs/**` z feature id
  `delivery-effectiveness-assessment`.

Request startu zawiera tylko `jiraProject`, `fromDate`, `toDate`, `model` i
opcjonalny `reasoningEffort`. UI nie przyjmuje JQL.

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

Local run przechowuje sanitizowany export envelope V1 i nie ma continuation.
Otwarcie historii odtwarza formularz oraz ostatni snapshot. Dla stanu
nieterminalnego UI probuje polling live joba; restart backendu nie wznawia
pracy, ale zapis pozostaje czytelny.

## Jira discovery

Neutralny `integrations.jira.JiraIssueSearchPort` mapuje typowany request na
kontrolowany JQL:

```text
project = "<PROJECT>"
AND statusCategory = Done
AND statusCategoryChangedDate >= "<fromDate>"
AND statusCategoryChangedDate < "<toDate + 1 day>"
ORDER BY key ASC
```

Adapter wykonuje paginowany `POST /rest/api/2/search`, zwraca effective JQL,
total, truncation i limitations. JQL jest prefiltracja. Dla kazdego kandydata
feature dodatkowo:

1. potwierdza biezaca kategorie `Done`,
2. pobiera changelog i mapuje status id na status category przez Jira REST,
3. wybiera ostatnie przejscie do kategorii `Done`,
4. sprawdza granice `[fromDate 00:00, toDate + 1 day 00:00)` w
   `delivery-effectiveness-assessment.time-zone`,
5. odrzuca issue z ucietym changelogiem albo niepotwierdzonym `doneAt`.

Material issue jest pobierany istniejacym `JiraIssuePort`, ale profilem
assessment: bez komentarzy, parent i subtasks, z opisem, acceptance criteria,
issue links, remote links i jawnie powiazanymi stronami Confluence. Stary
profil detailed pozostaje kontraktem Change Verification.

## GitLab i Delivery Units

Dla zakwalifikowanego issue feature wywoluje
`GitLabRepositoryPort.findMergeRequestsByIssueKey` w grupie z konfiguracji.
Do dalszego flow przechodza tylko MR-y ze stanem `merged` i jawnym `mergedAt`.
Adapter GitLab publikuje metadata, changed paths i diff; dane autorow i
commitow nie sa renderowane do evidence assessmentu.

`DeliveryUnitBuilder` buduje spojne komponenty grafu `issue <-> MR`.
To samo id MR-a, URL albo para `projectPath!iid` jest jedna tozsamoscia, wiec
wspolny MR laczy issue w jedna Delivery Unit i jest liczony raz.

## Evidence i prywatnosc

Kazda jednostka dostaje ograniczony pakiet inline artifacts:

- Jira intent: summary, opis, acceptance criteria i jawne dokumenty,
- merged MR metadata i changed paths,
- ograniczone diffy,
- visibility limits wynikajace z truncation i partial source failures.

Feature renderuje te logiczne pliki bezposrednio w finalnym prompcie miedzy
jawnymi markerami artifact. `CopilotRunRequest.artifactContents` zachowuje ich
projekcje diagnostyczna, ale runtime nie uzywa SDK attachments jako kanalu
evidence.

Limity liczby issue, MR-ow, dokumentow, plikow i znakow diffu sa properties
feature'a. Brak MR-a albo changed files daje `NOT_SCORABLE`. Zmiany skladajace
sie wylacznie z generated/build/dist/lock/minified/binary artifacts sa
`EXCLUDED` bez uruchamiania AI.

Artifacts i prompt nie zawieraja istniejacych Story Points, worklogow,
assignee, autorow, reviewerow ani komentarzy. Wynik nie moze sluzyc do rankingu
osob lub zespolow.

## AI i scoring

Ocenialna Delivery Unit uruchamia najwyzej jedna nowa sesje Copilota.
Feature przekazuje prompt, skill
`delivery-effectiveness-assessment-evaluator`, inline artifacts, model/effort,
initial report oraz tylko dwa report tools:

- `report_get_current`,
- `report_upsert_section` dla sekcji `ASSESSMENT`.

Jira, GitLab, Confluence, filesystem, shell i terminal nie sa dostepne w
sesji. AI zwraca classification, confidence, evidence/quality/visibility oraz
szesc wymiarow 0-4 dla `DELIVERY`. Nie zwraca DSP ani `score100`.

Skala jest zakotwiczona behawioralnie osobno dla kazdego wymiaru. Skill
definiuje obserwowalne kotwice `0`, `2` i `4`; `1` i `3` sa poziomami
posrednimi wymagajacymi porownania z obiema sasiednimi kotwicami. Wynik `0`
oznacza obserwowalny brak istotnej zmiany, a nie brak danych. Syntetyczne
przypadki kalibracyjne stabilizuja znaczenie skali, ale nie zastepuja evidence
biezacej Delivery Unit i nie wykorzystuja historycznych Story Points.

Dla kazdego niezerowego wymiaru AI zwraca `evidenceSummary` w formacie
`dimension | artifact#section | observed fact`. Parser wymaga kompletnego
pokrycia niezerowych wymiarow i logicznej sciezki `delivery-effectiveness/`.
Brak takiego evidence uniewaznia odpowiedz zamiast pozwalac na scoring
niezakotwiczonej liczby.

Ten sam zwalidowany skill jest widoczny w read-only ekranie `Platform / AI
Skills`. Frontendowa projekcja grupuje
`delivery-effectiveness-assessment-evaluator` jako rodzine
`Delivery Effectiveness Assessment` i odpowiedzialnosc `Assessment`; pozostaje
to etykieta nawigacyjna, a nie runtime selection skilla.

Backend liczy `score100` wagami `10/25/25/15/15/10` i mapuje wynik na
`0/1/2/3/5/8/13`. `INSUFFICIENT_EVIDENCE` przechodzi do `NOT_SCORABLE`, a
niepoprawna odpowiedz AI konczy tylko jednostke jako `FAILED`.
Kazdy terminalny wynik uruchomionej sesji AI zachowuje jej `usage`, ostatni
report oraz visibility limits, rowniez dla `INSUFFICIENT_EVIDENCE` i
`EXCLUDED`.

## Rownoleglosc i wynik

Jednostki sa wykonywane przez dedykowany, ograniczony executor z
konfigurowalnym parallelism, kolejka i timeoutem. Status jednostki jest
monotoniczny; spozniony wynik po timeoutcie nie moze nadpisac `FAILED`.
Awaria jednej jednostki nie zatrzymuje pozostalych.

Snapshot publikuje postep Jira, kroki, context, activity, czastkowe jednostki,
aggregate, visibility limits i report. Aggregate zawiera total DSP,
distribution, coverage, confidence, liczniki `EXCLUDED`/`NOT_SCORABLE`/`FAILED`
oraz zsumowane usage/cost. Parent job konczy sie `COMPLETED`,
`COMPLETED_WITH_WARNINGS` albo `FAILED`.
Top-level visibility limits sa suma ograniczen discovery i jednostek. Gdy
zadna jednostka nie zostala oceniona, report publikuje jawny gap i warning
zamiast prezentowac samo zerowe DSP jako pelny wynik.

## Ownership

- `features.deliveryeffectivenessassessment` posiada request, discovery
  orchestration, Delivery Units, evidence, AI policy, scoring, job state,
  runs mapping, result/report i API.
- `integrations.jira` posiada typed search/JQL, paging, material profile i
  status history REST.
- `integrations.gitlab` posiada MR discovery, metadata, changed files i diff.
- `aiplatform.copilot` pozostaje neutralnym runtime.
- `localworkspace.analysisruns`, `shared.ai` i frontendowe komponenty
  przebiegu/reportu pozostaja wspolne.

Feature nie importuje sibling feature'ow.
