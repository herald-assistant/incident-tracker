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

## Non-goals

- brak zatrzymywania i wznawiania aktywnego turnu,
- brak runtime tier switch na podstawie `session.usage_info`,
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

## Kryteria akceptacji

- UI Explorer ustawia `long_context` przed create i resume niezaleznie od
  katalogowych metadanych modelu,
- typed RPC potwierdza efektywny tier przed pierwszym `sendAndWait`,
- brak potwierdzenia nie pozwala wyslac promptu,
- AUTO nadal wybiera long context dla duzego initial context znanego modelu,
- AUTO pozostawia default dla malego promptu albo niepelnych metadanych,
- kod nie wywoluje `setModel(..., "long_context", ...)` i nie posiada runtime
  usage threshold,
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

## Wynik wdrozenia

UI Explorer wymaga `long_context` od poczatku sesji. Platforma ustawia tier w
obu configach SDK i potwierdza go przez `session.model.getCurrent` przed
wyslaniem promptu, z timeoutem
`analysis.ai.copilot.context-tier.verification-timeout`. Nieudana weryfikacja
jest fail-before-send, a nie cichym powrotem do kompaktowanego tieru `default`.
Pozostali konsumenci korzystaja z `AUTO`. Runtime
`setModel(..., "long_context", ...)` i runtime usage threshold zostaly usuniete
bez warstwy kompatybilnosci.

Weryfikacja 2026-08-22: celowane CRM-only testy policy, typed readera,
create/resume, gateway ordering/failure, preparation/session config, UI
Explorer assemblera i `PackageDependencyGuardTest` — PASS; `mvn -q test` —
1310 testow, 0 failures, 0 errors, 0 skipped; `git diff --check` — PASS.
