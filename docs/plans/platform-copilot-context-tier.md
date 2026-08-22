# Platformowa polityka Copilot context tier

Status: completed

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Wszystkie feature'y uruchamiaja Copilota przez wspolny runtime, ale obecnie
nie ustawiaja `contextTier`. Infinite sessions korzystaja z domyslnego
kompaktowania runtime, przez co duzy initial prompt moze zostac skompaktowany
przed wykonaniem merytorycznej analizy. Feature nie powinien sam znac limitow
modelu, progow kompaktowania ani RPC zmiany tieru.

Uzytkownik zatwierdzil 2026-08-22 platformowa polityke: duzy initial context
ma startowac od razu z `long_context`, a sesja zblizajaca sie do kompaktowania
ma jednokierunkowo przejsc z `default` do `long_context`.

## Proponowane rozwiazanie

Neutralny `aiplatform.copilot` wykorzysta dynamiczny, cache'owany katalog
typed RPC `models.list`, estymator initial context oraz policy/session
controller. Dla create i resume policy wybierze `long_context` przed otwarciem
sesji, gdy estymowany prompt, definicje tools i rezerwa przekrocza 70% znanego
standardowego okna. Wsparcie tieru i rozmiary okien wynikaja z metadanych
billing/capability, a nie z identyfikatora modelu ani listy w properties. Dla
sesji pozostawionej na `default`, rzeczywiste
`session.usage_info` uruchomi jedna asynchroniczna probe upgrade'u po
przekroczeniu 70% aktualnego standardowego `tokenLimit`, przed domyslnym
progiem kompaktowania.

Decyzja oraz powod beda publikowane jako user-visible activity `CONTEXT`.
Upgrade jest tylko `DEFAULT -> LONG_CONTEXT`; platforma nie obniza tieru.
Brak pelnych metadanych modelu, brak wsparcia organizacji albo blad RPC nie
przerywaja analizy i pozostawiaja domyslne zachowanie infinite sessions.

## Zakres

- nowe i wznawiane sesje wszystkich feature'ow korzystajacych z
  `CopilotSdkExecutionGateway`,
- konfigurowalne progi i estymacja tokenow oraz dynamiczne metadata modeli,
- preflight przed `createSession`/`resumeSession`,
- runtime upgrade z `session.usage_info`,
- jawna aktywnosc decyzji i testy wszystkich konsumentow platformy.

## Non-goals

- brak anulowania rozpoczetego background compaction,
- brak zaleznosci od eksperymentalnego `preCompact`,
- brak recznego wyboru tieru w feature UI,
- brak zmiany promptow, result contracts, exportow i hidden tool scope,
- brak obietnicy long context dla modelu bez potwierdzonego wiekszego okna w
  dynamicznych metadanych capability/billing.

## Ograniczenia i ryzyka

- High-level `CopilotClient.listModels()` gubi czesc metadanych billing, dlatego
  katalog korzysta z typed RPC `models.list`, ktore zachowuje
  `tokenPrices.contextMax`, long-context metadata oraz capability limits.
- Niepelne metadata dynamiczne oznaczaja fail-open do defaultow SDK; platforma
  nie uzupelnia ich lista nazw ani heurystyka po identyfikatorze modelu.
- Estymacja przed startem nie jest billingowym tokenizerem; pozostaje
  konserwatywna i dolicza tool definitions oraz rezerwe platformowa.
- Runtime switch jest asynchroniczny i moze przegrac wyscig z bardzo duzym
  kolejnym wynikiem toola. Preflight pozostaje podstawowa ochrona.
- Zmiana modelu sesji jest L3. Rollback polega na jednym property
  `analysis.ai.copilot.context-tier.enabled=false`, ktore przywraca obecne
  SDK/CLI defaults bez zmiany feature'ow.

## Baseline i conformance delta

Baseline:

- `CopilotSessionConfigFactory` nie ustawia `contextTier` ani
  `InfiniteSessionConfig` dla create/resume,
- `CopilotSdkExecutionGateway` rejestruje usage i compaction activity, ale nie
  reaguje na procent wykorzystania,
- wszystkie feature'y skladaja neutralny `CopilotRunRequest`,
- publiczne API modeli, joby, prompty, tools, usage i raporty nie zawieraja
  context tier.

Delta:

- ownership: wylacznie `aiplatform.copilot.runtime`,
- publiczne API/DTO: bez zmian,
- context/evidence, prompt/artifacts/skills/tools/hidden scope: bez zmian,
- session config: opcjonalny `long_context` wybrany przez neutralna policy,
- activity: nowe jawne eventy decyzji/upgrade'u w istniejacym kontrakcie,
- persistence/export: bez nowych pol; resumed session stosuje ta sama policy,
- zaleznosci: bez nowego kierunku miedzy top-level pakietami.

Konsumenci: Incident Analysis initial i chat, Flow Explorer initial i
continuation, Change Verification, Config Drift Viewer DEEP, Delivery
Complexity Assessment oraz UI Explorer.

## Kryteria akceptacji

- prompt przekraczajacy skonfigurowane 70% standardowego okna dostaje
  `long_context` przed otwarciem sesji,
- mniejszy prompt pozostaje na `default`,
- nieznany lub niewspierany model nie dostaje wymuszonego tieru,
- `session.usage_info` przy 70% uruchamia najwyzej jedna probe upgrade'u,
- sesja juz korzystajaca z wiekszego `tokenLimit` nie jest przelaczana ponownie,
- nieudany upgrade jest widoczny, ale nie przerywa runu,
- create i resume maja spojne zachowanie,
- wszystkie fixture'y i przyklady sa silnie zanonimizowanym CRM.

## Kroki

- [x] Krok 1: Dodac progi i konserwatywny estymator initial context; pokryc
  walidacje i decyzje testami jednostkowymi.
- [x] Krok 2: Zastosowac decyzje przed `createSession` i `resumeSession` oraz
  opublikowac user-visible activity z estymacja, progiem i wybranym tierem.
- [x] Krok 3: Monitorowac `session.usage_info` i wykonac jedna asynchroniczna,
  weryfikowana probe `DEFAULT -> LONG_CONTEXT` bez blokowania dispatchera.
- [x] Krok 4: Wykonac consumer audit, testy create/resume/runtime failure,
  `PackageDependencyGuardTest`, pelny backend i architecture diff.
- [x] Krok 5: Zaktualizowac kanoniczny opis runtime oraz zapisac dowod
  weryfikacji i rollback.
- [x] Krok 6: Usunac statyczna mape identyfikatorow i limitow modeli; rozszerzyc
  wspolny cache katalogu o pelne dynamiczne metadata typed `models.list`,
  przepiac policy oraz wykonac ponowna regresje na silnie zanonimizowanym CRM.

## Wynik wdrozenia

`CopilotContextTierPolicy` dziala przed otwarciem sesji i mutuje oba SDK
configi tylko dla modelu, ktorego dynamiczne metadata potwierdzaja wiekszy
context tier. Estymacja liczy prompt,
definicje tools oraz rezerwe, ale nie liczy ponownie artifact contents, ktore
sa juz osadzone w prompcie. Dla decyzji default/long/unsupported publikuje
jawna aktywnosc `platform.context_tier`; wylaczenie policy nie dodaje eventow i
pozostawia SDK defaults.

`CopilotContextTierSession` reaguje na `session.usage_info`, rozpoznaje juz
wieksze okno po `tokenLimit`, atomowo dopuszcza najwyzej jedna probe i uznaje
zakonczony future `setModel(..., "long_context", ...)` za potwierdzenie RPC.
Blad synchroniczny albo asynchroniczny publikuje `FAILED`, nie propaguje
wyjatku do runu i nie ponawia upgrade'u.

Consumer audit potwierdzil wspolny gateway dla Incident Analysis initial/chat,
Flow Explorer initial/continuation, Change Verification, Config Drift Viewer,
Delivery Complexity Assessment i UI Explorer. Zaden feature nie otrzymal
metadanych modelu, progu ani bezposredniego `contextTier`.

Po review uzytkownika usunieto `analysis.ai.copilot.context-tier.models`.
`CopilotSdkModelOptionsProvider` jest jednym cache'owanym source of truth dla
dynamicznie zmieniajacych sie modeli: publiczna fasada AI options nadal mapuje
tylko pola potrzebne selectowi, natomiast policy korzysta wewnetrznie z
domyslnego i maksymalnego okna wyprowadzonych z billing/capability. Brak tych
danych pozostawia default SDK bez zgadywania.

Weryfikacja 2026-08-22: CRM-only testy dynamicznego mapowania
billing/capability, decyzji, create/resume, auth scope, tool definitions,
unknown/disabled model, runtime threshold, single switch i failure — PASS;
gateway integration, niezmieniony publiczny AI options contract i
`PackageDependencyGuardTest` — PASS; `mvn -q test` — 1301 testow, 0 bledow,
0 pominietych; `git diff --check` — PASS. Rollback:
`analysis.ai.copilot.context-tier.enabled=false`.
