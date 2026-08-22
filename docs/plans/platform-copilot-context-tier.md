# Platformowa polityka Copilot context tier

Status: completed

Source need: brak osobnego dokumentu; korekta zatwierdzona przez uzytkownika
2026-08-22 po obserwacji kompaktowania UI Explorera przed 272 000 tokenow.

## Potrzeba / dlaczego

Wszystkie feature'y uruchamiaja Copilota przez wspolny runtime. Duzy initial
prompt albo goal-driven research moze przekroczyc standardowe okno i wywolac
infinite-session compaction przed zbudowaniem finalnego wyniku. Feature nie
powinien znac limitow konkretnych modeli ani wywolywac provider-specific RPC,
ale musi moc okreslic, ze jego sposob pracy wymaga rozszerzonego okna od
pierwszej wiadomosci.

Pierwsza implementacja preflightu dodatkowo probowala reagowac na
`session.usage_info` przez
`setModel(model, reasoningEffort, "long_context", null)`. W uzywanym Java SDK
trzeci `String` oznacza `reasoningSummary`, nie context tier. Dokumentacja SDK
stwierdza tez, ze model switch obowiazuje dopiero dla nastepnej wiadomosci.
Nie zabezpieczalo to aktywnego, jednowiadomosciowego research turnu i zostalo
usuniete bez kompatybilnosci wstecznej.

## Rozwiazanie wynikowe

Neutralny `CopilotSessionConfigRequest` przyjmuje preference:

- `AUTO` — platforma moze wybrac `long_context` przed create/resume, gdy
  dynamiczny katalog modelu i estymacja initial context potwierdzaja potrzebe,
- `LONG_CONTEXT_REQUIRED` — platforma ustawia `long_context` w
  `SessionConfig` i `ResumeSessionConfig` niezaleznie od kompletnosci metadanych
  katalogu.

Po otwarciu sesji, ale przed pierwszym `sendAndWait`, runtime odczytuje typed
RPC `session.model.getCurrent`. Gdy zazadano `long_context`, efektywny tier musi
byc rowny `long_context`. Brak potwierdzenia lub tier `default` zatrzymuje run
przed wyslaniem duzego promptu i publikuje jawne activity `FAILED`.

UI Explorer wybiera `LONG_CONTEXT_REQUIRED`, bo jego kontekst rosnie w trakcie
nieograniczonego researchu toolami i nie da sie go wiarygodnie przewidziec z
samego initial promptu. Pozostale feature'y zachowuja `AUTO`.

## Zakres i konsumenci

- nowe i wznawiane sesje korzystajace z `CopilotSdkExecutionGateway`,
- neutralny request/prepared-session context-tier preference,
- preflight przed `createSession`/`resumeSession`,
- weryfikacja efektywnego tieru przed pierwsza wiadomoscia,
- UI Explorer jako pierwszy konsument `LONG_CONTEXT_REQUIRED`,
- Incident Analysis initial/chat, Flow Explorer initial/continuation, Change
  Verification, Config Drift Viewer i Delivery Complexity Assessment pozostaja
  konsumentami `AUTO`.

Publiczne API modeli, requesty feature'ow, joby, prompty, result contracts,
exporty, skills, tools i hidden scope nie dostaja nowych pol.

## Rozszerzenie obserwowalnosci 2026-08-22

Po pierwszym tescie produkcyjnym samo `session.model.getCurrent` okazalo sie
niewystarczajacym dowodem rozszerzenia okna: RPC zwracalo tier
`long_context`, ale kolejne `session.usage_info` nadal raportowaly
`tokenLimit=272000`. Operator nie widzial przy tym platformowych eventow,
poniewaz wspolny panel FE filtrowal `platform.context_tier`, a sanitizer
historii UI Explorera usuwal parametry decyzji z `details`.

### Baseline

- platforma ustawia `long_context` tylko przed `createSession` albo
  `resumeSession`; nie wykonuje nieskutecznego runtime switchu aktywnego turnu,
- `LONG_CONTEXT_REQUIRED` zatrzymuje wyslanie promptu, gdy
  `session.model.getCurrent` nie zwroci tieru `long_context`,
- `session.usage_info` pozostaje zrodlem rzeczywistego `tokenLimit` i
  `currentTokens`, ale nie jest obecnie korelowane z decyzja tieru,
- `AnalysisAiActivityEvent` jest wspolnym kontraktem aktywnosci wszystkich
  feature'ow, a wspolny `analysis-steps-panel` renderuje runtime i usage,
- terminalny snapshot UI Explorera zachowuje activity, lecz zeruje wszystkie
  `details`; export/import uzywa tego samego sanitizowanego snapshotu,
- publiczne requesty feature'ow, wynik, prompt, tools i hidden scope nie
  zawieraja ustawien context tier.

### Conformance delta

- cel: zapisac i pokazac operatorowi odrebne fakty `REQUESTED`,
  `MODEL_STATE_CONFIRMED` oraz `EFFECTIVE_WINDOW_OBSERVED`,
- ownership: decyzja i korelacja SDK pozostaja w neutralnym
  `aiplatform.copilot.runtime.context`; prezentacja w shared komponencie FE;
  allowlista bezpiecznych danych eksportu pozostaje przy UI Explorerze,
- publiczne API/DTO: bez nowych pol i bez zmiany wersji; reuse istniejącego
  `AnalysisAiActivityEvent.type/details`,
- context, prompt, artifacts, skills, tools, policy, hidden scope, report i
  result: bez zmian,
- job state/persistence/export: UI Explorer zachowa w `details` tylko jawna
  allowliste parametrow `platform.context_tier`; pozostale activity nadal beda
  zerowane,
- shared FE/UX: `platform.context_tier` stanie sie zdarzeniem runtime i pokaze
  czytelne parametry decyzji oraz obserwowanego okna,
- konsumenci: Incident Analysis, Flow Explorer, Change Verification, Config
  Drift Viewer, Delivery Complexity Assessment i UI Explorer korzystaja ze
  wspolnego runtime/event modelu i panelu; wymagany jest wspolny test regresji,
- kompatybilnosc: starszy event bez `details` nadal bedzie czytelny przez
  `title/summary`; nie powstaje alias ani migrator,
- znany drift: rozbieznosc `model.getCurrent=long_context` kontra rzeczywisty
  `tokenLimit` jest ujawniana, a nie maskowana ani automatycznie naprawiana.

## Rozszerzenie runtime upgrade 2026-08-22

Uzytkownik zatwierdzil rozszerzenie platformy o reakcje na rzeczywiste
zapelnienie okna w trakcie jednego research turnu. Samo pokazanie
`session.usage_info` nie chroni raportu przed kompaktowaniem, gdy initial
context byl maly, ale kolejne tool results zwiekszyly kontekst ponad prog.

### Baseline

- gateway wykonuje jeden `sendAndWait` na jednym uchwycie sesji,
- `session.usage_info` jest agregowane do usage i user-visible activity, ale
  nie steruje lifecycle sesji,
- `SessionConfig` oraz `ResumeSessionConfig` potrafia ustawic
  `contextTier=long_context`, a kazdy run ma jawny stabilny `sessionId`,
- SDK udostepnia `abort()` aktywnego turnu; zamkniecie uchwytu zachowuje stan
  sesji do resume,
- resume zachowuje historie, tool state i plan, a konfiguracja resume ponownie
  ustawia tools, hooki, skille, model, reasoning i durable system instructions,
- tool evidence, budget i report store sa obecnie rejestrowane na czas jednego
  uchwytu sesji i wymagaja zachowania rejestracji przez oba etapy upgrade'u.

### Conformance delta

- dodac dynamiczny runtime threshold oparty o
  `currentTokens / tokenLimit`, ustawiony przed progiem background compaction,
- po przekroczeniu progu wykonac najwyzej jeden kontrolowany upgrade na run:
  `abort -> close handle -> resume same sessionId with long_context`,
- nie powtarzac initial promptu; po resume wyslac krotka neutralna instrukcje
  kontynuacji przerwanego turnu z zachowanej historii,
- zachowac jedna akumulacje usage, report store, tool evidence, tool budget,
  hidden context i activity sink przez oba uchwyty sesji,
- ponownie potwierdzic `session.model.getCurrent` po resume i skorelowac wynik
  z pierwszym rzeczywistym `tokenLimit` nowego uchwytu,
- zapisac i pokazac fazy `RUNTIME_TIER_SWITCH_REQUESTED`,
  `RUNTIME_SESSION_ABORTED`, `RUNTIME_RESUME_REQUESTED` oraz wynik
  weryfikacji/okna wraz z progiem, uzyciem i session id,
- nie wykonywac petli kolejnych resume, gdy pierwszy runtime upgrade nie
  zwiekszy realnego okna; ujawnic wynik i pozwolic SDK dokonczyc zgodnie z
  jego mechanizmem infinite sessions,
- publiczne API feature'ow, prompt/result contract, tools schema i hidden
  scope pozostaja bez zmian.

## Non-goals

- brak zmiany tieru przez `setModel` w aktywnym turnie,
- brak wiecej niz jednego automatycznego runtime resume na run,
- brak anulowania background compaction,
- brak statycznej mapy identyfikatorow modeli,
- brak recznego wyboru tieru w UI,
- brak heurystyki po nazwie modelu.

## Ryzyka i rollback

- `AUTO` nadal zalezy od kompletnosci typed `models.list`; brak metadanych
  pozostawia SDK default.
- `LONG_CONTEXT_REQUIRED` polega na oficjalnym `contextTier` create/resume, a
  rzeczywisty stan potwierdza `session.model.getCurrent`; niewspierany model
  zakonczy run kontrolowanym bledem przed wyslaniem promptu.
- Estymacja `AUTO` nie jest billingowym tokenizerem; liczy prompt, system
  message, tool definitions i rezerwe, bez ponownego liczenia osadzonych juz
  artifacts.
- `analysis.ai.copilot.context-tier.enabled=false` pozostaje globalnym
  rollbackiem do SDK defaults i wylacza zarowno AUTO, jak i feature preference.
- Skok uzycia pomiedzy kolejnymi `session.usage_info` moze przekroczyc prog
  kompaktowania zanim platforma zdazy przerwac turn; runtime upgrade jest
  ochrona best-effort, a eventy musza pokazac faktyczna kolejnosc.
- Abort moze zakonczyc `sendAndWait` odpowiedzia czastkowa albo wyjatkiem;
  oba warianty sa sygnalem do resume tylko wtedy, gdy istnieje skorelowane
  zadanie runtime upgrade.

## Kryteria akceptacji

- UI Explorer ustawia `long_context` przed create i resume niezaleznie od
  katalogowych metadanych modelu,
- typed RPC potwierdza efektywny tier przed pierwszym `sendAndWait`,
- brak potwierdzenia nie pozwala wyslac promptu,
- AUTO nadal wybiera long context dla duzego initial context znanego modelu,
- AUTO pozostawia default dla malego promptu albo niepelnych metadanych,
- kod nie wywoluje `setModel(..., "long_context", ...)`, a runtime threshold
  prowadzi przez abort/resume tego samego `sessionId`,
- initial prompt jest wysylany raz, a resume korzysta z neutralnej instrukcji
  kontynuacji,
- przekroczenie progu powoduje najwyzej jeden upgrade i nie podwaja rejestracji
  report/tool evidence/tool budget,
- pozostale feature'y zachowuja preference AUTO,
- activity odroznia konfiguracje oczekujaca na potwierdzenie, potwierdzony tier
  i blad weryfikacji,
- wszystkie fixture'y i przyklady sa silnie zanonimizowanym CRM.

## Kroki

- [x] Krok 1: Zachowac dynamiczny katalog i preflight estimator dla AUTO.
- [x] Krok 2: Dodac neutralne `AUTO` / `LONG_CONTEXT_REQUIRED` i przeniesc
  preference przez przygotowanie sesji.
- [x] Krok 3: Ustawic `contextTier` w obu configach przed create/resume.
- [x] Krok 4: Usunac niepoprawny runtime switch oraz jego property/testy bez
  warstwy kompatybilnosci.
- [x] Krok 5: Dodac typed odczyt efektywnego tieru i fail-before-send.
- [x] Krok 6: Zakonczyc consumer audit, dokumentacje, celowane CRM-only testy,
  `PackageDependencyGuardTest`, pelne `mvn -q test` i `git diff --check`.
- [x] Krok 7: Dodac i utrwalic user-visible lifecycle context tier z
  parametrami decyzji i rzeczywistym oknem z `session.usage_info`, wyrenderowac
  go we wspolnym panelu FE oraz wykonac silnie zanonimizowana regresje CRM dla
  platformy, UI Explorera i wszystkich konsumentow shared UI.
- [x] Krok 8: Dodac kontrolowany runtime upgrade po `session.usage_info`,
  zachowac state/stores przez abort i resume, pokazac lifecycle w shared UI,
  dodac silnie zanonimizowane testy CRM oraz wykonac pelna regresje
  backend-frontend.

## Wynik wdrozenia

UI Explorer wymaga `long_context` od poczatku sesji. Platforma ustawia tier w
obu configach SDK i potwierdza go przez `session.model.getCurrent` przed
wyslaniem promptu, z timeoutem
`analysis.ai.copilot.context-tier.verification-timeout`. Nieudana weryfikacja
jest fail-before-send, a nie cichym powrotem do kompaktowanego tieru `default`.
Pozostali konsumenci korzystaja z `AUTO`. Runtime
`setModel(..., "long_context", ...)` zostal usuniety bez warstwy
kompatybilnosci. Nowy runtime threshold nie przywraca tej sciezki: steruje
kontrolowanym abort/resume tej samej sesji przez config SDK.

Weryfikacja 2026-08-22: celowane CRM-only testy policy, typed readera,
create/resume, gateway ordering/failure, preparation/session config, UI
Explorer assemblera i `PackageDependencyGuardTest` — PASS; `mvn -q test` —
1310 testow, 0 failures, 0 errors, 0 skipped; `git diff --check` — PASS.

Rozszerzenie obserwowalnosci 2026-08-22: lifecycle zapisuje skorelowane
zdarzenia zadania `long_context`, weryfikacji stanu modelu przez SDK oraz
pierwszego rzeczywistego `tokenLimit` z `session.usage_info`. UI Explorer
utrwala tylko jawnie dozwolone skalarne parametry tych zdarzen, a wspolny
panel FE pokazuje je obok kompaktowania i wywolan tools. Celowane testy
platformy, gatewaya, persistence UI Explorera, `PackageDependencyGuardTest`
oraz komponentu FE — PASS; pelne testy Angular — 433 testy, PASS; produkcyjny
build Angular — PASS; `mvn -q -Pbackend-dev clean package` — 1311 testow,
0 failures, 0 errors, 0 skipped; `git diff --check` — PASS.

Rozszerzenie runtime upgrade 2026-08-22: platforma obserwuje
`currentTokens/tokenLimit` i przy progu 70% wykonuje najwyzej jeden kontrolowany
`abort -> close handle -> resume same sessionId` z `long_context`. Initial
prompt jest wysylany tylko raz; drugi turn dostaje neutralna instrukcje
kontynuacji, a jedna akumulacja usage oraz rejestracje report, tool evidence i
tool budget obejmuja oba uchwyty. Po resume typed RPC ponownie potwierdza tier,
a kolejne `session.usage_info` ujawnia rzeczywisty limit. Testy Angular:
433/433 PASS; produkcyjny build Angular PASS; `mvn -q -Pbackend-dev clean
package`: 1314 testow, 0 failures, 0 errors, 0 skipped; `git diff --check` —
PASS.
