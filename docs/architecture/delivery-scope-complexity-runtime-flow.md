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
- export: wspolny `GET /api/analysis/runs/{analysisId}/export`,
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

Jedna jednostka oceny odpowiada spojnemu grafowi issue-MR. Powiazane issue,
subtaski i wiele MR-ow sa skladane w jedna wirtualna zmiane. Ten sam MR nie jest
punktowany wielokrotnie. Source discovery i assessment maja osobne bounded
executors kontrolowane przez:

- `delivery-scope-complexity.max-parallel-source-requests`,
- `delivery-scope-complexity.max-parallel-analyses`,
- `delivery-scope-complexity.item-timeout`.

## Evidence i wykonanie AI

Feature buduje wlasny inline evidence packet z Jira, Confluence i pelnych
danych merged MR zwroconych przez integracje. Story Points, worklogi,
komentarze i dane osobowe nie sa evidence dla modelu.

Kazda ocenialna Delivery Unit uruchamia jedna nowa sesje Copilota. Prompt
zawiera effective tresc skilla `delivery-scope-complexity-evaluator`, kontrakt
JSON i wszystkie artefakty. Allowlista tools i katalogi skilli sa puste.
Pierwsza odpowiedz modelu jest odpowiedzia finalna. Prepared prompt i raw AI
response sa zapisywane przy jednostce przed dalszym przetwarzaniem.

## Kontrakt i scoring

AI zwraca klasyfikacje, confidence, ograniczenia oraz szesc wymiarow:

- `novelty` - waga `0.15`,
- `structuralAndLogic` - waga `0.25`,
- `businessAndInvariants` - waga `0.15`,
- `robustnessAndTests` - waga `0.10`,
- `refactorAndArchitecture` - waga `0.15`,
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

UI ma wlasna route i API service. Pokazuje jedna rozwijalna tabele Delivery
Units, linki Jira/MR, final score `0-200`, rozklad wymiarow, evidence, quality
flags, visibility limits, warnings, raw response i koszt AI. Filtry team/author
sa deterministyczna projekcja zakonczonego runu i nie zmieniaja zapisanych
wynikow.

## Ownership i usuniecie eksperymentu

Pakiet `features.deliveryscopecomplexity` posiada caly kontrakt use case'u:
source orchestration, Delivery Units, evidence, prompt, parser, scoring, job,
persistence codec, import/export i API. Reuse dotyczy tylko neutralnych
`integrations`, `aiplatform`, `shared`, `localworkspace` i wspolnych wzorcow UI.

Usuniecie feature'a wymaga usuniecia jego pakietu, testow, skilla, katalogu
Angular i punktowych wpisow composition root. Nie wymaga zmiany ani migracji
`Delivery Complexity Assessment`.
