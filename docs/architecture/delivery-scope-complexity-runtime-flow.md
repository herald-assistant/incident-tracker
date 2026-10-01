# Delivery Scope Complexity Runtime Flow

## Cel i izolacja eksperymentu

`Delivery Scope Complexity` jest niezaleznym feature'em eksperymentalnym do
rownoleglego porownania z `Delivery Complexity Assessment`. Ocenia ten sam typ
dostarczonego materialu, ale uzywa innego kontraktu AI i innej arytmetyki.
Nie importuje kodu, modeli ani komponentow sibling feature'a.

Publiczne wejscia:

- UI: `GET /delivery-scope-complexity`,
- start: `POST /api/delivery-scope-complexity/jobs`,
- polling: `GET /api/delivery-scope-complexity/jobs/{jobId}`,
- import: `POST /api/delivery-scope-complexity/imports`,
- przenosny export JSON: wspolny
  `GET /api/analysis/runs/{analysisId}/export`,
- biznesowy export CSV: generowany przez UI z terminalnego snapshotu bez
  dodatkowego endpointu,
- historia: wspolne `/api/analysis/runs/**` z feature id
  `delivery-scope-complexity`.

Eksport ma schema `tdw.delivery-scope-complexity-export`, wersje `1` i result
contract `delivery-scope-complexity-v1`. Nie jest zgodny z eksportem drugiego
assessmentu i nie ma aliasow migracyjnych.

## Discovery i Delivery Units

Request zawiera `jiraProject`, `fromDate`, `toDate`, model i opcjonalny
`reasoningEffort`. Feature mapuje kryteria na typowany Jira search, potwierdza
przejscie issue do statusu Done w lokalnym zakresie dat i pobiera material Jira
oraz merged MR-y przez neutralne integracje.

Profil assessment pobiera bezposredni parent i bezposrednie children,
zawsze bez komentarzy takze w odczytach powiazanych. Nie rozwija rodzenstwa,
dalszych przodkow ani dzieci kontekstowych zadan. Neutralny adapter
rozpoznaje relacje z pol Jira `parent` i `subtasks`, bez slownika nazw
typow. Flaga `issuetype.subtask` sluzy do wykrywania braku parent key.
Nieudany odczyt parent/child zachowuje znana relacje i klucz z jawnym
limitation zamiast zatrzymywac ocene zadania.

Jedna jednostka oceny odpowiada spojnemu grafowi issue-MR. Powiazane issue,
subtaski i wiele MR-ow sa skladane w jedna wirtualna zmiane. Ten sam MR nie jest
punktowany wielokrotnie. Source discovery i assessment maja osobne bounded
executors kontrolowane przez:

- `delivery-scope-complexity.max-parallel-source-requests`,
- `delivery-scope-complexity.max-parallel-analyses`,
- `delivery-scope-complexity.item-timeout`.

## Evidence i wykonanie AI

Feature buduje wlasny inline evidence packet z Jira, Confluence i pelnych
danych merged MR zwroconych przez integracje. Story Points, snapshot
`timespent` i estimate, worklogi, komentarze i dane osobowe nie sa evidence dla
modelu.

Artifact Jira wskazuje oceniane zadania z `unit.issues()` oraz potwierdzone
role nadrzedne/podrzedne. Parent i children nienalezace do jednostki maja
oznaczenie `KONTEKST POZA ZAKRESEM OCENY`. Nazwa typu Jira nie jest
metadana inputu AI; pozostaje oryginalna metadana snapshotu, CSV i trendow.
Kazde zadanie jest renderowane w jednej sekcji z zachowaniem odmiennych
tresci, a identyczne dokumenty raz z lista powiazanych zadan. Brak relacji
nie jest podstawa zgadywania typu.
Opis rodzica jest intencja, a nie dowodem dostarczenia jego calego zakresu.
Prompt i skill wymagaja oceny union zachowania potwierdzonego przez merged
MR-y jednostki bez zwielokrotnienia wyniku przez podzial na zadania.
Kontrakt zakresu oceny w prompcie ma pierwszenstwo przed sprzeczna definicja
jednostki w customowym effective skillu.

Prompt zawiera snapshot effective skilla `delivery-scope-complexity-evaluator`,
kontrakt JSON i inline artifacts danego etapu. Kazde wywolanie ma nowa sesje,
pusta allowliste tools i katalogow skilli. Odpowiedz jest finalnym JSON-em
danego etapu; prepared prompt oraz raw response sa zapisywane przed dalszym
przetwarzaniem przy odpowiednim wywolaniu jednostki.

## Kontrakt i scoring

AI zwraca klasyfikacje, confidence, ograniczenia oraz szesc wymiarow:

- `novelty` - waga `0.20`,
- `structuralAndLogic` - waga `0.25`,
- `businessAndInvariants` - waga `0.15`,
- `robustnessAndTests` - waga `0.10`,
- `refactorAndArchitecture` - waga `0.10`,
- `distribution` - waga `0.20`.

Kazdy wymiar zawiera calkowity `score` `0-100`, `scopeSignal` `0-1` i
evidence. Niezerowy score bez evidence jest bledem kontraktu. Brak widocznosci
nie oznacza score `0`; prowadzi do nizszego confidence, visibility limit albo
`INSUFFICIENT_EVIDENCE`.

Backend najpierw zaokragla `scopeSignal` do jednego miejsca, a potem liczy
deterministycznie z `HALF_UP`:

```text
scope = round1(0.50 + 1.50 * scopeSignal)
scaledScore = round1(score * scope)
points = round1(scaledScore * weight)
finalScore = round1(clamp(sum(points), 0, 200))
```

AI nie zwraca `scope`, `scaledScore`, `points` ani `finalScore`. Publiczny
wynik zachowuje wszystkie te skladowe, aby uzytkownik mogl odtworzyc rachunek.
Aggregate zawiera sume final score, srednia ocenionych jednostek, confidence,
liczniki statusow i usage/cost. Wynik nie jest mapowany na DSP.

## Live state i UI

Snapshot `QUEUED` musi zostac zapisany w Analysis History przed zaplanowaniem
wykonania. Discovery, przygotowanie promptow, wyniki jednostek, raw responses,
usage i status terminalny aktualizuja ten sam run czastkowo.
Kazdy issue snapshot zawiera opcjonalne `timeSpentSeconds`,
`originalEstimateSeconds`, `remainingEstimateSeconds` oraz
`timeTrackingCapturedAt` pobrane z Jira. Pola sa opcjonalne w aktualnym envelope V1; brak danych Jira mapuje sie na `null`. Nie sa uzywane przez scoring ani UI raportu.

UI ma wlasna route i API service. Pokazuje jedna rozwijalna tabele Delivery
Units, linki Jira/MR, final score `0-200`, rozklad wymiarow, evidence, quality
flags, visibility limits, warnings i raw response. Koszt AI jest widoczny tylko
we wspolnym aside i dotyczy calego runu. Filtry team/author
sa deterministyczna projekcja zakonczonego runu i nie zmieniaja zapisanych
wynikow.

Obok przenosnego JSON UI udostepnia niewersjonowany, biznesowy CSV calego runu
z jednym wierszem na issue, niezaleznie od aktywnych filtrow. CSV zawiera
zapisane metadata Jira issue, wspolna liste linkow MR z Delivery Unit,
stabilnie sparowane listy `mergeRequestAuthorIds` i
`mergeRequestAuthorNames`, snapshot `timeSpentSeconds`,
`originalEstimateSeconds`, `remainingEstimateSeconds` i
`timeTrackingCapturedAt`, punktowy wklad szesciu wymiarow, `finalScore` i
`pointsForAggregation`. Nie eksportuje
wewnetrznych skladowych `score`, `scopeSignal`, `scope`, `scaledScore` ani
`weight`. Ocena pozostaje ocena Delivery Unit i jest powtarzana przy jej
issue. `pointsForAggregation` jest wypelnione tylko dla issue z najpozniejszym
`doneAt`, a przy remisie z leksykograficznie najmniejszym `issueKey`, przez co
suma tej kolumny nie zwielokrotnia wyniku jednostki. CSV nie jest formatem
importu; uzywa separatora `;`, UTF-8 z BOM i cytowania wartosci z separatorem,
cudzyslowem albo nowa linia. Ten biznesowy CSV moze byc lokalnie wczytany
przez `Delivery Complexity Trends`; nie staje sie przez to wersjonowanym
kontraktem backendowego importu.


## Walidacja rdzenia i metadanych odpowiedzi

Classification oraz komplet szesciu wymiarow sa wymagane. Score musi byc
calkowity w zakresie 0-100, scopeSignal skonczony w zakresie 0-1, a dodatni
score wymaga evidence wewnatrz wymiaru. ScopeSignal i evidence wymiaru sa
czescia rdzenia, nie metadanymi mozliwymi do pominiecia.
Confidence jest metadana: brak, bledny typ lub liczba poza skonczonym zakresem
0-1 daje 0 z opisem przyczyny. Poprawne teksty evidenceSummary, qualityFlags
i visibilityLimits sa zachowywane, a bledy tych pol daja AI_METADATA_WARNING
i opis w visibilityLimits. Kompletny rdzen przed uszkodzonym opisowym koncem
JSON mozna odzyskac z jawnym ostrzezeniem; nie zgaduje sie brakujacych wymiarow.

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

Prompt rozdziela `EVIDENCE_PART`, `REDUCTION` i `SYNTHESIS`, bez zmiany
merytorycznych kotwic, skal i wag rubryki. Czesci zwracaja sufficientEvidence,
findings (behaviorId, dimension, fact, references, dependencies), confidence
i visibilityLimits, bez punktow i bez powtarzania coverage. Jedyna allowlista
referencji czesci i redukcji to `part-scope.json`; manifest calej dostawy jest
tlem, nie dowodem kodu poza zakresem. Parser tworzy `DeliveryPartFindings`
z coverage przypisanym deterministycznie z przekazanego materialu.
Sprawdza wymagane fakty, wymiary i niepuste referencje w tym zakresie.
Coverage oznacza przekazany material, nie deklaracje jego przeczytania przez AI.

Jezeli model mimo instrukcji zwroci coverage, rozbieznosc daje visibility limit
z liczba brakujacych, nadmiarowych, powtorzonych i niepoprawnych elementow.
Bledny typ deklaracji rowniez daje ostrzezenie. Raw pozostaje niezmieniony;
nie przycina sie deklaracji modelu i nie ponawia AI dla korekty metadanych.
Brak/niepoprawne confidence daje 0 z ostrzezeniem. Niepoprawne listy opisowe
zachowuja poprawne teksty i jawna diagnoze odrzuconych elementow.
SufficientEvidence=false nadal oznacza brak wyceny; brak opisu ograniczenia
daje komunikat o niedostepnosci wyceny, nie syntetyczne punkty.
`SYNTHESIS` otrzymuje pelny Jira, manifest i wszystkie zweryfikowane ustalenia;
deduplikuje zachowania i zwraca dotychczasowy finalny kontrakt. Backend
wykonuje scoring raz. `MULTIPART_SYNTHESIS` i visibilityLimits ujawniaja,
ze synteza korzysta z ustalen, bez bezposredniego odczytu calego surowego diffu.
Confidence nie przekracza minimum confidence czesci i syntezy.

Nadmierne ustalenia sa redukowane w budzetowanych grupach (`REDUCTION`),
bez scoringu. Walidacja zachowuje zakres aplikacji, komplet referencji,
pary behaviorId/wymiar oraz ich referencje i zaleznosci; wynik musi byc mniejszy.
Nie ma ciecia listy ustalen. Ostrzezenia i ograniczenia wejscia sa przypinane
do wywolania redukcji oraz finalnego wyniku nawet wtedy, gdy model ich nie
powtorzy. Confidence nadal nie przekracza minimum czesci/redukcji/syntezy.
Blad metadanych nie zatrzymuje czesci ani syntezy. Awaria, niepoprawny JSON,
referencje poza zakresem lub utrata ustalen blokuja finalna ocene z konkretna
diagnoza, zachowujac dotychczasowy material. Niewystarczajace evidence daje
`NOT_SCORABLE`.

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
Poprawna ocena z ostrzezeniami metadanych pozostaje COMPLETED, wnosi jeden
wynik do agregacji i jest widoczna wraz z visibilityLimits/qualityFlags
w obecnym UI. Obecny ksztalt API, format V1 i zasady statusu joba pozostaja
takie same. Historyczne runy nie sa korygowane ani przeliczane.
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

## Ownership i usuniecie eksperymentu

Pakiet `features.deliveryscopecomplexity` posiada caly kontrakt use case'u:
source orchestration, Delivery Units, evidence, prompt, parser, scoring, job,
persistence codec, import/export i API. Reuse dotyczy tylko neutralnych
`integrations`, `aiplatform`, `shared`, `localworkspace` i wspolnych wzorcow UI.

Usuniecie feature'a wymaga usuniecia jego pakietu, testow, skilla, katalogu
Angular i punktowych wpisow composition root. Nie wymaga zmiany ani migracji
`Delivery Complexity Assessment`.
