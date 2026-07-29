# Open Work

Status: draft

Source need: brak jednego dokumentu; kazdy obszar ponizej nazywa wlasna
potrzebe

## Potrzeba / dlaczego

Projekt potrzebuje jednego miejsca dla niesfinalizowanych prac przekrojowych,
ktore nie maja jeszcze osobnego planu. Backlog ma zapobiegac gubieniu
wartosciowych follow-upow, ale nie moze zastapic business need, decyzji
architektonicznej ani zatwierdzonego planu implementacji.

## Proponowane rozwiazanie

Kazdy ponizszy obszar jest niezaleznym kandydatem do realizacji. Przed
implementacja jego aktualny baseline musi byc potwierdzony w kodzie i
dokumentacji, zakres ograniczony do jednego spojnego inkrementu, a kroki,
testy i kryteria akceptacji doprecyzowane oraz zatwierdzone przez uzytkownika
zgodnie z `../AGENTS.md`.

Wykonanie jednego obszaru nie zatwierdza pozostalych.

## Zakres

- jakosc i UX Flow Explorera,
- jakosc kontraktow wyniku i eksportu,
- hardening reusable platformy i shared UI,
- jawne decyzje produktowo-techniczne,
- niezawodnosc widoczna dla operatora.

## Non-goals

- historia zakonczonych refaktorow,
- automatyczna zgoda na implementacje,
- zastapienie dokumentow w `../needs/`,
- utrzymywanie trwalych decyzji, ktore powinny trafic do `../architecture/`.

## Ograniczenia i ryzyka

- Backlog moze sie zdezaktualizowac wraz z kodem; baseline jest obowiazkowy
  przed zatwierdzeniem obszaru.
- Laczenie kilku obszarow w jeden inkrement zwieksza ryzyko zmiany kontraktow
  bez pelnej listy konsumentow.
- Quality signals nie uzasadniaja przywracania niewidocznej telemetryki.
- Refaktor shared/platform bez dwoch zgodnych konsumentow moze utrwalic
  semantyke jednego feature'a jako pozornie neutralna abstrakcje.

## Kryterium zamkniecia obszaru

Obszar jest zamkniety dopiero wtedy, gdy wszystkie zatwierdzone kroki maja
`[x]`, ich testy albo inne dowody weryfikacji przeszly, wynikowy stan zostal
opisany w `../architecture/`, a uzytkownik otrzymal informacje o rezultacie i
pozostalych ograniczeniach.

## Zasady pracy

- Kazdy krok wykonawczy ma status `[ ]` albo `[x]`.
- Przed rozpoczeciem kroku wymagane jest zatwierdzenie uzytkownika; jawne
  zatwierdzenie calego obszaru moze objac wszystkie jego kroki.
- `[x]` oznacza wykonany krok z potwierdzonym kryterium akceptacji, a nie tylko
  rozpoczecie pracy.
- Nie usuwaj wykonanych krokow, dopoki caly obszar jest aktywny.
- Po zamknieciu obszaru przenies wynikowy stan do `../architecture/`, a
  niepotrzebny plan usun; historie zachowuje Git.
- Testy i fixtures Flow Explorera maja byc CRM-specific i zanonimizowane.
- Gdy Java SDK albo bytecode nie wyjasnia zachowania Copilota, sprawdz
  upstream `github/copilot-sdk`, szczegolnie dokumentacje Node SDK i protokol
  `@github/copilot`.

## 1. Cross-goal smoke test Flow Explorera

Status: draft

Potrzeba / dlaczego: potwierdzic, ze goal-based result contract daje
uzyteczny i ekonomiczny wynik dla kazdego glownego celu, a nie tylko
przechodzi przez parser.

Proponowane rozwiazanie: wykonac porownywalne smoke testy na jednym
zanonimizowanym use case i na ich podstawie korygowac kontrakt, skille oraz
template'y.

- [ ] Potwierdzic baseline i przygotowac jeden realny albo zanonimizowany
  CRM-specific endpoint dla `DEEP_DISCOVERY`, `TEST_SCENARIOS` i
  `RISK_DETECTION`.
- [ ] Uruchomic trzy cele i zapisac jakosc wyniku, usage oraz token cost.
- [ ] Ocenic, czy initial result jest samowystarczalny, overview daje szybkie
  zrozumienie, a kazda aktywna sekcja wnosi wartosc.
- [ ] Porownac `compact` i `deep`: `compact` nie moze byc zbyt plytkie, a
  `deep` rozwlekle bez konkretow.
- [ ] Potwierdzic, ze follow-up jest potrzebny do wyjatkow, a nie do uzyskania
  podstawowej wartosci.
- [ ] Skorygowac skille, template'y i testy tylko dla brakow wykazanych przez
  smoke test.

## 2. Snippet ranking pod primary flow i focus areas

Status: draft

Potrzeba / dlaczego: zwiekszyc wartosc initial contextu Flow Explorera przy
malym budzecie tokenowym.

Proponowane rozwiazanie: ranking oparty o role elementu w primary flow i
focus areas, uzupelniony jawnymi coverage diagnostics.

- [ ] Potwierdzic aktualny baseline rankingu w `features.flowexplorer`.
- [ ] Priorytetyzowac controller/API entrypoint, primary use-case service,
  input mapper, persistence/update/save i response mapper przed
  drugorzednymi detalami read/response.
- [ ] Traktowac focus areas jako kierunek rankingu, a nie poziom glebokosci
  analizy.
- [ ] Dodac coverage diagnostics dla primary roles covered/missing.
- [ ] Dodac CRM-specific, zanonimizowane testy rankingu i budzetu.

## 3. Baseline quality report dla Flow Explorer runu

Status: draft

Potrzeba / dlaczego: mierzyc jakosc i koszt runu w powtarzalny sposob przed
optymalizacja kolejnych fragmentow.

Proponowane rozwiazanie: feature-local quality report zbudowany z juz
dostepnych usage, activity i context coverage, publikowany w diagnostic
export.

- [ ] Zweryfikowac, ktore quality signals sa juz dostepne bez nowej
  telemetryki.
- [ ] Dodac feature-local `FlowExplorerRunQualityReport` albo rownowazny
  kontrakt pod `features.flowexplorer`.
- [ ] Zmapowac usage, activity i context coverage na pierwsze quality
  signals.
- [ ] Uwzglednic tool calls, denied/redundant attempts, context rebuild,
  repository rediscovery, tokens, snippet budget, primary flow role coverage
  i noncanonical tool inputs.
- [ ] Dodac quality report do diagnostic exportu bez rozszerzania
  user-facing exportu.
- [ ] Dodac CRM-specific, zanonimizowane testy kontraktu i eksportu.

## 4. Ranking i grupowanie limitations oraz next reads

Status: draft

Potrzeba / dlaczego: poprawic czytelnosc wyniku i ograniczyc pokuse
nadmiernego doczytywania kodu.

Proponowane rozwiazanie: osobne kategorie ograniczen, deduplikacja oraz
limitowana lista next reads w wyniku operatora.

- [ ] Rozdzielic `limitations` na technical, user-facing i AI-guidance.
- [ ] Grupowac powtarzalne niskopoziomowe ograniczenia.
- [ ] Przyciac inline `suggestedNextReads` do top N pozycji dla focus areas.
- [ ] Pelna liste pozostawic tylko w diagnostic artifact, jezeli nadal jest
  potrzebna.
- [ ] Dla nowych AI/UI consumers preferowac strukturalne `nextReads`, a
  `suggestedNextReads` traktowac jako jawna kompatybilnosc przejsciowa.
- [ ] Dodac CRM-specific, zanonimizowane testy sortowania, grupowania i
  limitow.

## 5. Result contract: fact, inference, unknown

Status: draft

Potrzeba / dlaczego: zmniejszyc ryzyko prezentowania inferencji modelu jako
potwierdzonego faktu.

Proponowane rozwiazanie: oznaczenia epistemiczne w skillu i kontrakcie wyniku,
source references dla mocnych twierdzen oraz quality flags.

- [ ] Dostosowac runtime skill `flow-explorer-write-report`.
- [ ] Dostosowac parser i DTO, jezeli kontrakt wymaga strukturalnej zmiany.
- [ ] Wprowadzic walidacje albo quality flags dla zbyt pewnych twierdzen.
- [ ] Wymagac source reference dla mocnych twierdzen albo oznaczenia ich jako
  inference/unknown.
- [ ] Dodac CRM-specific, zanonimizowane testy parsera, kontraktu i
  prezentacji.

## 6. User-facing export vs diagnostic export

Status: draft

Potrzeba / dlaczego: oddzielic przenosny rezultat dla analityka od materialu
do debugowania platformy i nie ujawniac niepotrzebnie kodu ani promptu.

Proponowane rozwiazanie: dwa jawnie nazwane kontrakty eksportu z osobnymi
politykami zawartosci i importu.

- [ ] Zdefiniowac wersjonowane kontrakty user-facing i diagnostic exportu.
- [ ] Usunac surowy kod i pelny prompt z user-facing exportu.
- [ ] Dodac quality report i pelne artifacts tylko do diagnostic exportu.
- [ ] Dostosowac UI, import i dokumentacje roznicy pomiedzy eksportem do
  podgladu a local workspace do kontynuacji.
- [ ] Dodac testy round-trip, migracji wersji i braku danych diagnostycznych w
  user-facing eksporcie.

## 7. Flow Explorer UX polish

Status: draft

Potrzeba / dlaczego: poprawic czytelnosc wyboru systemu i endpointu bez
rozszerzania merytorycznego scope'u feature'a.

Proponowane rozwiazanie: wykorzystac istniejace modele Operational Context i
wspolne wzorce Material, dodajac informacje dopiero po manualnej ocenie ich
wartosci.

- [ ] Dodac karte albo panel szczegolow systemu: owner/team, lifecycle,
  code-search scope summary, validation findings i open questions.
- [ ] Po smoke testach rozstrzygnac, czy collapsed endpoint item powinien
  pokazywac confidence.
- [ ] W expanded/popover endpoint item dodac tags, documentation source i
  next reads, jezeli nie zaszumia wyboru.
- [ ] Potwierdzic manualnie hover, focus, click, keyboard navigation i
  zachowanie tooltipow/popoverow Materiala.

## 8. Platform/shared architecture hardening

Status: draft

Potrzeba / dlaczego: utrzymac kierunek platformowy przy rozwoju wielu
feature'ow i blokowac ponowny wzrost architecture drift.

Proponowane rozwiazanie: wzmacniac guardy oraz wyciagac neutralne kontrakty
dopiero po porownaniu co najmniej dwoch rzeczywistych konsumentow.

- [ ] Zweryfikowac aktualny zakres `PackageDependencyGuardTest` wobec
  `../architecture/package-dependencies.md`.
- [ ] Stopniowo wzmacniac guard albo ArchUnit rules dla `features`,
  `aiplatform`, `agenttools`, `integrations`, `api`, `shared` i
  `localworkspace`.
- [ ] Nie wymuszac pelnego cycle-free graphu, dopoki pozostale wyjatki nie
  maja zaplanowanej migracji.
- [ ] Porownac Incident Analysis, Flow Explorer i Change Verification oraz
  potwierdzic neutralnosc `AnalysisEvidenceSection`,
  `AnalysisAiActivityEvent`, `AnalysisAiUsage` i
  `AnalysisAiToolFeedback`.
- [ ] Wydzielic neutralny run/job projection tylko wtedy, gdy podobienstwa sa
  potwierdzone w co najmniej dwoch feature'ach.
- [ ] Wydzielic wspolne UI timeline, prompt, usage i tool evidence tylko przy
  zgodnej semantyce i lifecycle.
- [ ] Wrocic do neutralnego per-run budget request tylko po potwierdzeniu
  realnej potrzeby wielu feature'ow.

## 9. Decyzje produktowo-techniczne

Status: draft

Potrzeba / dlaczego: rozstrzygnac decyzje wplywajace na publiczne kontrakty i
UX dopiero wtedy, gdy istnieje wystarczajacy material z realnych feature'ow.

Proponowane rozwiazanie: dla kazdej decyzji przygotowac warianty, trade-offy,
impact na obecnych konsumentow i rekomendacje; po zatwierdzeniu zapisac trwala
decyzje w `../architecture/key-decisions.md`.

- [ ] Rozstrzygnac, czy source refs maja zostac stringami, czy przejsc na
  strukturalny kontrakt z `file`, `method`, `line` i `toolCallId`.
- [ ] Rozstrzygnac, czy DB tools wejda do Flow Explorera jako V2 runtime data,
  czy pozostana czescia natural-language data diagnostics.
- [ ] Rozstrzygnac, czy GitLab endpoint discovery ma miec scalony widok z
  wielu repozytoriow, czy primary repository pozostaje podstawa.
- [ ] Zdefiniowac jeden kontrakt branch/ref resolution dla feature'ow bez
  incident evidence, obejmujacy UI, Operational Context i GitLab tools.

## 10. Operational hardening

Status: draft

Potrzeba / dlaczego: domknac niezawodnosc i obserwowalnosc tam, gdzie awarie
sa widoczne dla uzytkownika albo utrudniaja utrzymanie produktu.

Proponowane rozwiazanie: scenariusze smoke/e2e, jawne timeouts i retries oraz
diagnostyka operator-facing bez przywracania niewidocznej telemetryki.

- [ ] Dodac e2e albo manual smoke scripts dla glownych sciezek Incident
  Analysis i Flow Explorera.
- [ ] Dla Incident Analysis pokryc Elasticsearch po `correlationId`, upload
  CSV i brak konfiguracji Elasticsearch/Kibana.
- [ ] Dopracowac timeouty i retry tylko dla operacji, dla ktorych istnieje
  jawna semantyka idempotencji oraz wartosc produktowa.
- [ ] Potwierdzic brak fallbacku follow-up chat do nowej sesji; awaria
  wznowienia tej samej sesji ma byc jawna.
- [ ] Dodac widoczne error/raw-response diagnostics tam, gdzie pomagaja
  operatorowi albo developerowi zrozumiec awarie.
- [ ] Potwierdzic, ze nie istnieje niewidoczna telemetryka sesji Copilota;
  ewentualny powrot wymaga osobnej potrzeby, planu, testow i widocznego celu.
