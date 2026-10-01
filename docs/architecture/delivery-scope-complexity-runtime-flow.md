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

Request zawiera tylko `jiraProject`, `fromDate` i `toDate`. UI nie ma wyboru
modelu/effort ani wywolania katalogu AI options; backend odrzuca usuniete pola.
Feature mapuje kryteria na typowany Jira search, potwierdza
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

Kazda ocenialna Delivery Unit uruchamia jedna nowa sesje Copilota. Prompt
zawiera effective tresc skilla `delivery-scope-complexity-evaluator`, kontrakt
JSON i wszystkie artefakty. Allowlista tools i katalogi skilli sa puste.
Pierwsza odpowiedz modelu jest odpowiedzia finalna. Prepared prompt i raw AI
response sa zapisywane przy jednostce przed dalszym przetwarzaniem.

## Dobor modelu przed sesja

Konfiguracja pod prefixem `delivery-scope-complexity.ai` zawiera
`default-model`, `default-reasoning-effort`, `long-context-model` i
`long-context-reasoning-effort`. Domyslne pary to `gpt-5.4-mini/high` oraz
`gpt-6.1-sol/high`; ich dostepnosc i limity potwierdza dynamiczny katalog
Copilota, bez tabel modeli zaszytych w feature'ze.

Po przygotowaniu dokladnego promptu kazdej Delivery Unit neutralny
`CopilotInitialContextModelSelector` szacuje caly inline material, effective
skill, instrukcje sesji oraz platformowa rezerwe. Artefakty juz osadzone w
promptcie sa liczone raz. Estymacja korzysta z
`analysis.ai.copilot.context-tier.estimated-characters-per-token` i
`reserved-tokens`; sprawdza zarowno limit promptu, jak i calkowite okno.
Granica `>=` jest traktowana jako brak bezpiecznego miejsca.

Material mieszczacy sie w zwyklym oknie korzysta z defaultu i jego effort.
Przy przekroczeniu okna default bez `long_context` jest zastepowany
skonfigurowana para long-context tylko dla tej jednostki. Default wspierajacy
rozszerzony tier zachowuje model i effort. Platforma ustawia wtedy
`LONG_CONTEXT_REQUIRED` i potwierdza tier przed pierwszym wyslaniem.
Prog `AUTO` 70% nadal moze wlaczyc tier tego samego modelu; sam nie powoduje
zmiany modelu. Nieznany model/effort, brak wiarygodnych limitow, wylaczona
polityka wymaganego tieru lub przekroczenie rozszerzonego okna koncza tylko
jednostke kontrolowanym bledem przed send.

Activity `platform.model_selection` z phase `INITIAL_MODEL_SELECTION`
zapisuje wybor i estymacje przed utworzeniem sesji. `aiModel` i
`reasoningEffort` naleza do snapshotu jednostki i sa widoczne jako read-only
metadata w jej szczegolach, takze po pozniejszym bledzie. Snapshot runu nie
ma globalnych pol modelu/effort; aggregate usage przy roznych modelach ma
`model=null`, zachowujac sume tokenow i kredytow. Historia i import/export
V1 zachowuja metadata jednostek i odrzucaja poprzedni ksztalt kontraktu.

Dobor modelu nie zmienia promptu, effective skilla, evidence, scoringu,
liczby wywolan ani procedury one-shot. Nie przycina materialu ani nie
wykonuje retry z innym modelem. Pozostale feature'y nie wywoluja tego
preflightu i zachowuja swoj wybor modelu.

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
`timeTrackingCapturedAt` pobrane z Jira. Biezacy envelope V1 zachowuje te pola;
brak dostepnej wartosci jest reprezentowany przez `null`. Nie sa uzywane przez scoring ani UI raportu.

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

## Ownership i usuniecie eksperymentu

Pakiet `features.deliveryscopecomplexity` posiada caly kontrakt use case'u:
source orchestration, Delivery Units, evidence, prompt, parser, scoring, job,
persistence codec, import/export i API. Reuse dotyczy tylko neutralnych
`integrations`, `aiplatform`, `shared`, `localworkspace` i wspolnych wzorcow UI.

Usuniecie feature'a wymaga usuniecia jego pakietu, testow, skilla, katalogu
Angular i punktowych wpisow composition root. Nie wymaga zmiany ani migracji
`Delivery Complexity Assessment`.
