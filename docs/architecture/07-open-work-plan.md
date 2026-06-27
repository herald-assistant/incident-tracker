# Open Work Plan

Ten dokument jest jedynym aktywnym backlogiem planistycznym projektu.
Zastapil stare, robocze plany:

- `07-universal-analysis-event-model-migration-plan.md`
- `09-flow-explorer-implementation-plan.md`
- `09-operational-context-read-model-optimization-plan.md`
- `10-gitlab-endpoint-use-case-context-plan.md`
- `11-flow-explorer-analysis-quality-improvement-plan.md`
- `12-flow-explorer-goal-based-result-contract-plan.md`

Zawiera tylko prace otwarte albo decyzje do podjecia. Wykonane kroki i
decision logi zostaly przeniesione do stabilnych dokumentow architektury albo
usuniete z planu.

## Zasady pracy z planem

- Przed implementacja kolejnego punktu opisujemy zakres i czekamy na
  zatwierdzenie albo korekte.
- Po kazdym zakonczonym kroku aktualizujemy ten plik i usuwamy wykonane
  zadania.
- Nie utrzymujemy kompatybilnosci wstecznej dla roboczych kontraktow Flow
  Explorera, jezeli nie jest to jawnie potrzebne.
- Testy i fixture'y dla Flow Explorera maja byc CRM-specific i
  zanonimizowane.
- Jesli brakuje wyjasnien w kodzie GitHub Copilot SDK, najpierw sprawdzamy
  dokumentacje Node SDK i miejsca, w ktorych opisane sa infinite sessions.

## 1. Cross-goal smoke test Flow Explorera

Cel: potwierdzic, ze goal-based result contract daje uzyteczny wynik dla
kazdego glownego celu Flow Explorera, a nie tylko przechodzi przez parser.

- Uruchomic smoke test na realnym albo zanonimizowanym CRM-specific endpoincie
  dla `DEEP_DISCOVERY`, `TEST_SCENARIOS` i `RISK_DETECTION`.
- Ocenic, czy initial result jest samowystarczalny, czy overview daje szybkie
  zrozumienie i czy kazda aktywna sekcja wnosi wartosc.
- Porownac jakosc `compact` i `deep`: `compact` nie moze byc zbyt plytkie, a
  `deep` nie moze byc rozwlekle bez konkretow.
- Sprawdzic, czy follow-up jest potrzebny tylko do wyjatkow, a nie do
  uzyskania podstawowej wartosci.
- Sprawdzic usage/token cost dla kazdego celu.
- Skorygowac skille i template'y, jezeli smoke test pokaze braki.

## 2. Snippet ranking pod primary flow i focus areas

Cel: zwiekszyc wartosc initial contextu w malym budzecie tokenowym.

- Dopracowac ranking kandydatow snippetow w `features.flowexplorer`.
- Priorytetyzowac controller/API entrypoint, primary use-case service,
  input mapper, persistence/update/save i response mapper przed drugorzednymi
  detalami read/response.
- Traktowac focus areas jako kierunek rankingu, a nie jako poziom glebokosci
  analizy.
- Dodac coverage diagnostics dla primary roles covered/missing.
- Dodac testy CRM-specific i zanonimizowane.

## 3. Baseline quality report dla Flow Explorer runu

Cel: mierzyc jakosc i koszt runu w powtarzalny sposob, zanim zaczniemy
optymalizowac kolejne fragmenty.

- Dodac feature-local model `FlowExplorerRunQualityReport` albo rownowazny
  kontrakt pod `features.flowexplorer`.
- Zmapowac istniejace usage, activity i context coverage na pierwsze quality
  signals.
- Uwzglednic m.in. tool calls, denied/redundant attempts, context rebuild,
  repository rediscovery, tokens, snippet budget, primary flow role coverage i
  noncanonical tool inputs.
- Dodac quality report do diagnostic exportu.
- Dodac testy CRM-specific i zanonimizowane.

## 4. Ranking i grupowanie limitations oraz next reads

Cel: poprawic czytelnosc wyniku i ograniczyc pokuse nadmiernego doczytywania
kodu.

- Rozdzielic `limitations` na technical, user-facing i AI-guidance.
- Grupowac powtarzalne niskopoziomowe ograniczenia.
- Przyciac inline `suggestedNextReads` do top N pozycji dla focus areas.
- Pelna liste zostawiac tylko w diagnostic artifact, jezeli nadal jest
  potrzebna.
- Dla nowych AI/UI consumers preferowac strukturalne `nextReads`;
  `suggestedNextReads` traktowac jako tymczasowa kompatybilnosc.
- Dodac testy CRM-specific i zanonimizowane.

## 5. Result contract: fact, inference, unknown

Cel: zmniejszyc ryzyko, ze model opisze inferencje jako potwierdzony fakt.

- Dostosowac runtime skill `flow-explorer-result-contract`.
- Dostosowac parser/DTO, jezeli kontrakt wymaga strukturalnej zmiany.
- Wprowadzic walidacje albo quality flags dla zbyt pewnych twierdzen.
- Wymagac source reference dla mocnych twierdzen albo oznaczenia ich jako
  inference/unknown.
- Dodac testy CRM-specific i zanonimizowane.

## 6. User-facing export vs diagnostic export

Cel: rozdzielic eksport dla analityka od eksportu do debugowania platformy.

- User-facing export nie powinien zawierac surowego kodu ani pelnego promptu.
- Diagnostic export powinien zawierac quality report i pelne artifacts.
- Dostosowac UI, import i dokumentacje roznicy miedzy exportem do podgladu a
  lokalna persystencja do kontynuacji.
- Dodac testy CRM-specific i zanonimizowane.

## 7. Flow Explorer UX polish

Cel: dopracowac miejsca, ktore nie blokuja MVP, ale poprawiaja czytelnosc
wyboru systemu i endpointu.

- Dodac karte albo panel szczegolow systemu: owner/team, lifecycle,
  code-search scopes, repozytoria, validation findings i open questions.
- Rozstrzygnac po smoke testach UX, czy collapsed endpoint item powinien
  pokazywac confidence.
- W expanded/popover endpoint item dodac tags, documentation source i next
  reads, jezeli nie zaszumia wyboru.
- Potwierdzic manualnie finalne odczucie hover/focus/click dla tooltipow i
  popoverow Materiala.

## 8. Platform/shared architecture hardening

Cel: utrzymac kierunek platformowy po dodaniu drugiego feature'u bez
przedwczesnego wyciagania abstrakcji.

- Stopniowo wzmacniac `PackageDependencyGuardTest` albo ArchUnit rules dla
  granic `features`, `aiplatform`, `agenttools`, `integrations`, `api` i
  `shared`.
- Nie wymuszac pelnego cycle-free graphu, dopoki pakiety nie sa stabilne.
- Zweryfikowac po porownaniu Incident Analysis i Flow Explorera, czy nazwy i
  kontrakty `AnalysisEvidenceSection`, `AnalysisAiActivityEvent`,
  `AnalysisAiUsage` i `AnalysisAiToolFeedback` nadal sa wystarczajaco
  neutralne.
- Wydzielac neutralny run/job projection dopiero wtedy, gdy realne podobienstwa
  obu feature'ow sa widoczne w kodzie.
- Wydzielac wspolne UI komponenty timeline, prompt, usage i tool evidence
  dopiero po stabilnym reuse w obu feature'ach.
- Wrocic do neutralnego per-run budget request tylko wtedy, gdy feature-level
  budzety realnie beda potrzebne.

## 9. Decyzje produktowo-techniczne do podjecia pozniej

Cel: nie blokowac teraz rozwoju, ale nie zgubic decyzji, ktore wplyna na
kontrakty i UX.

- Czy source refs w wynikach maja zostac stringami, czy przejsc na strukturalny
  kontrakt z `file`, `method`, `line` i `toolCallId`?
- Czy DB tools wejda do Flow Explorera jako V2 focus area runtime data, czy
  zostana czescia osobnego feature'u natural-language data diagnostics?
- Czy endpoint inventory ma miec jawnie udokumentowany scalony widok z wielu
  repozytoriow, czy primary repository pozostaje podstawowa perspektywa?
- Jak opisac branch/ref resolution dla feature'ow bez incident evidence, zeby
  UI, operational context i GitLab tools mialy jeden kontrakt?

## 10. Operational hardening

Cel: domknac niezawodnosc i obserwowalnosc tylko tam, gdzie jest to widoczne
dla uzytkownika albo potrzebne do utrzymania produktu.

- Dodac e2e albo manual smoke scripts dla glownych sciezek Incident Analysis i
  Flow Explorera.
- Dopracowac timeouty i retry tam, gdzie produktowo ma to sens.
- Nie dodawac fallbacku follow-up chat poza wznowieniem tej samej sesji
  Copilota; jesli sesja nie dziala, blad ma byc jawny.
- Dodac widoczne error/raw-response diagnostics tam, gdzie pomagaja operatorowi
  albo developerowi zrozumiec awarie.
- Nie przywracac niewidocznej telemetryki sesji Copilota; telemetryka moze
  wrocic tylko jako productized, user-visible element z testami i
  dokumentacja.
