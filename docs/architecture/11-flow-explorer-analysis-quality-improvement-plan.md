# Flow Explorer Analysis Quality Improvement Plan

## Cel dokumentu

Ten dokument opisuje plan usprawnien Flow Explorera po pierwszym realnym
smoke tescie wyniku wyeksportowanego z UI.

Celem nie jest dodanie kolejnych opcji do ekranu. Celem jest poprawa
merytorycznej jakosci odpowiedzi, ograniczenie kosztu tokenow i ograniczenie
losowego "AI sobie dociagnie" na rzecz lepszego deterministic context,
jasnych tool inputs, twardszej polityki tooli oraz mierzalnej diagnostyki.

Plan jest wykonywany krok po kroku:

- przed kazdym krokiem opisujemy w rozmowie problem, proponowane rozwiazanie,
  dotykane pakiety/pliki i ryzyka,
- implementujemy dopiero po zatwierdzeniu kroku przez uzytkownika,
- po kazdym wykonanym kroku aktualizujemy checklisty w tym pliku,
- jezeli w trakcie pracy zmieni sie podejscie, aktualizujemy sekcje
  "Decision log" przed dalsza implementacja,
- nie utrzymujemy kompatybilnosci wstecznej dla roboczych kontraktow Flow
  Explorera, GitLab workbencha ani diagnostyki quality, jezeli stare pola albo
  warianty zaciemniaja docelowy kontrakt,
- testy i fixture'y dodawane w ramach tego planu maja byc zawsze
  CRM-specific i zanonimizowane.

## Zrodlo obserwacji

Plan powstal po analizie jednego realnego exportu Flow Explorera z UI. Dane
konkretnej domeny i repozytoriow nie sa powtarzane w tym dokumencie; ponizsze
obserwacje sa uogolnione i zanonimizowane.

Najwazniejsze sygnaly z exportu:

- wynik byl merytorycznie uzyteczny, ale AI musialo nadrabiac braki wieloma
  tool callami,
- dla jednej analizy endpointu zuzycie bylo bardzo wysokie: okolo 294k
  tokenow, 10 API/tool-assisted tur i okolo 118 sekund,
- initial context mial tylko 4 snippet cards i trafil w czesc flow, ale nie
  zawieral wszystkich najwazniejszych metod glownego use case'u,
- AI kilkukrotnie probowalo uzyc niekanonicznych argumentow GitLaba, zanim
  trafilo w prawidlowy project path dla repozytorium w podgrupie,
- AI ponownie uruchomilo budowanie endpoint use-case context, mimo ze ten
  context byl juz deterministycznie przygotowany i osadzony w promptcie,
- `limitations` i `suggestedNextReads` byly obszerne i miejscami zbyt
  techniczne dla analityka/testera.

## Problemy, przyczyny i kierunek rozwiazan

### 1. Initial snippets nie zawsze pokrywaja glowny flow

Problem:

Initial prompt dostaje `compact-flow-manifest` oraz budzetowane
`snippetCards`, ale ranking snippetow nie zawsze wybiera najwazniejsze metody
do zrozumienia endpointu. W smoke tescie zabraklo glownego service/update use
case'u oraz mappera wejscia, a pojawily sie fragmenty mniej krytyczne dla
pierwszego zrozumienia flow.

Dlaczego tak sie dzieje:

- obecny ranking opiera sie zbyt mocno na ogolnej roli pliku i kolejnosci
  znalezionych kandydatow,
- focus areas nie steruja wystarczajaco priorytetem snippetow,
- nie ma wyraznego rozroznienia pomiedzy "primary flow spine" a detalami typu
  response enrichment, helper mapper albo secondary read model,
- limit snippet cards jest poprawny kosztowo, ale bez mocnego rankingu odcina
  czasem wlasciwe metody.

Propozycja rozwiazania:

- dodac feature-local ranking `FlowExplorerSnippetCandidateRanker`,
- nadawac najwyzszy priorytet lancuchowi:
  controller/API entrypoint -> primary use-case service -> mapper wejscia ->
  repository/query-for-update -> command repository/save -> response mapper,
- wplyw focus areas:
  - `BUSINESS_FLOW`: primary service, decyzje, porty i glowne przejscia,
  - `VALIDATIONS`: mapper wejscia, walidatory, wymagane pola, adnotacje,
  - `PERSISTENCE`: query/update/save repository, encje tylko gdy wnosza
    reguly albo mapping stanu,
  - `EXTERNAL_INTEGRATIONS`: klienty/porty outbound tylko gdy sa wywolane w
    flow endpointu,
- dodac diagnostyke, ktora pokazuje "dlaczego ten snippet zostal wybrany" i
  "co zostalo odciete przez budzet".

### 2. Model nie dostaje wystarczajaco jawnych canonical GitLab tool inputs

Problem:

AI probuje zgadywac `projectName`, `projectPath` albo `filePath`, mimo ze
backend zna te wartosci po etapie deterministic context. Powoduje to bledne
tool calle, strata tokenow i dodatkowe tury.

Dlaczego tak sie dzieje:

- `context-snapshot.json` zawiera repozytoria, ale nie prezentuje modelowi
  jednoznacznego, krotkiego bloku "uzywaj dokladnie tych argumentow",
- compact manifest i snippet cards sa dobre dla czlowieka/modelu, ale nie sa
  wystarczajaco dyrektywne jako tool-call cheat sheet,
- przy repozytoriach w podgrupach GitLaba roznica miedzy application name,
  repository id, project name i full project path jest latwa do pomylenia.

Propozycja rozwiazania:

- dodac artifact albo sekcje promptu `canonical-tool-inputs.md`,
- dla wybranego endpointu pokazywac:
  - `applicationName`,
  - `branchRef`,
  - `selectedRepository.projectName`,
  - `selectedRepository.projectPath`,
  - `endpointId`,
  - liste kanonicznych `filePath` dla flow nodes i snippet cards,
- dodac krotkie przyklady poprawnych tool calls dla
  `gitlab_read_java_method_slice`,
- w tool policy walidowac, czy `projectName` nalezy do repozytoriow
  wybranych dla tego Flow Explorer runu.

### 3. AI wykonuje redundantne discovery i rebuild context

Problem:

Model potrafi uruchomic `gitlab_build_endpoint_use_case_context`,
`gitlab_list_available_repositories` albo operational context search mimo ze
wybrany system, repozytorium, branch i endpoint sa juz znane.

Dlaczego tak sie dzieje:

- tool descriptions mowia, kiedy tool jest przydatny, ale nie egzekwuja
  twardej polityki po stronie backendu,
- Flow Explorer pozwala na tools, ktore sa przydatne przy follow-up albo przy
  braku contextu, ale sa za drogie w initial run po deterministycznym
  przygotowaniu contextu,
- brak runtime feedbacku dla modelu typu "ten tool call jest redundantny,
  uzyj danych z artifacts".

Propozycja rozwiazania:

- w initial run ograniczyc albo zablokowac:
  - `gitlab_build_endpoint_use_case_context`, jezeli endpoint context jest juz
    osadzony,
  - `gitlab_list_available_repositories`, jezeli wybrany system ma resolved
    repository scope,
  - szerokie `opctx_search`, jezeli potrzebne dane sa juz w promptcie albo
    canonical tool inputs,
- zostawic te tools w follow-up tylko z jasna polityka i budzetem,
- dodac `FlowExplorerCopilotToolInvocationPolicy`, ktora rozpoznaje redundantne
  discovery i zwraca czytelny denied message zamiast pozwalac na kosztowny call.

### 4. Brak mierzalnej quality diagnostyki po runie

Problem:

Export pokazuje usage i aktywnosc AI, ale nie ocenia, czy run byl efektywny.
Nie ma prostych flag typu: zly projectName, redundantny context rebuild,
za duzo tool calli, snippet budget obcial primary service.

Dlaczego tak sie dzieje:

- platforma zbiera usage i activity, ale feature nie mapuje ich na
  merytoryczne quality signals Flow Explorera,
- tool evidence jest user-facing, a nie quality-facing,
- nie mamy jeszcze kontraktu diagnostycznego, ktory mozna porownywac miedzy
  smoke testami.

Propozycja rozwiazania:

- dodac feature-local `FlowExplorerRunQualityReport`,
- liczyc co najmniej:
  - liczbe tool calli,
  - liczbe denied/redundant tool attempts,
  - czy byl context rebuild w initial run,
  - czy bylo repository rediscovery,
  - ile tokenow poszlo na input/output/cache,
  - czy snippet budget reached,
  - czy primary flow roles maja snippet coverage,
  - czy model uzywal niekanonicznych project/file inputs,
- pokazac quality summary w diagnostic export i opcjonalnie w UI jako zwijalna
  sekcje developerska.

### 5. `limitations` i `suggestedNextReads` sa zbyt szumne

Problem:

Lista ograniczen i nastepnych odczytow moze byc technicznie poprawna, ale zbyt
dluga i zbyt niskopoziomowa. To obniza czytelnosc dla nietechnicznego odbiorcy
i zwieksza ryzyko, ze AI bedzie czytac za duzo.

Dlaczego tak sie dzieje:

- integracja GitLaba zwraca wiele precyzyjnych ograniczen parsera/metod,
- prompt nie rozdziela ograniczen dla AI od ograniczen dla uzytkownika,
- `suggestedNextReads` nie sa wystarczajaco rankowane pod preset/focus areas.

Propozycja rozwiazania:

- rozdzielic:
  - `technicalLimitations` dla diagnostyki,
  - `userFacingVisibilityLimits` dla wyniku,
  - `aiGuidanceLimitations` dla promptu,
- grupowac powtarzalne ograniczenia, np. overloady mapperow/metod,
- ograniczyc `suggestedNextReads` w initial promptcie do top N pozycji,
  zaleznie od focus areas,
- pelna lista moze zostac w diagnostic artifact, ale nie musi byc cala
  inline w promptcie.

### 6. Result contract powinien mocniej rozdzielac fakt, inferencje i braki

Problem:

Wynik potrafi uzyc jezyka sugerujacego pewnosc, gdy dowod jest posredni.
Przyklad klasy problemu: nazwa metody sugeruje lockowanie, ale snippet nie
potwierdza mechanizmu. Taki element powinien byc opisany jako inferencja albo
otwarte pytanie, nie jako fakt.

Dlaczego tak sie dzieje:

- obecny kontrakt wymaga visibility limits, ale nie wymaga przypisania
  pewnosci do waznych twierdzen,
- skill moze byc zbyt ogolny w regule "nie zgaduj",
- response parser nie waliduje, czy sekcje techniczne maja source/evidence
  odniesienie albo uncertainty marker.

Propozycja rozwiazania:

- doprecyzowac `flow-explorer-result-contract`:
  - wazne twierdzenia techniczne maja byc oznaczone jako `fact`,
    `inference` albo `unknown`,
  - ryzykowne terminy typu lock, transaction, event publish, async handoff
    wymagaja potwierdzenia w snippetach albo jawnego oznaczenia jako
    niepotwierdzone,
  - test scenarios nie moga laczyc kilku mozliwych zachowan jako pewnik,
- dodac light validation po parsowaniu wyniku, ktora flaguje odpowiedz zbyt
  pewna wobec znanych limitations.

### 7. Export potrzebuje trybu user-facing i diagnostic

Problem:

Aktualny export jest dobry do debugowania, ale zawiera prompt, artifacts,
tool evidence i fragmenty kodu. To nie zawsze jest odpowiedni format dla
analityka/testera ani do bezpiecznego przekazywania poza zespol developerski.

Dlaczego tak sie dzieje:

- export powstal jako szybka funkcja operacyjna,
- UI nie rozroznia "pobierz wynik" od "pobierz pelna diagnostyke runu",
- Flow Explorer ma dwa rozne audytoria: nietechniczny odbiorca wyniku i
  developer/debugger platformy.

Propozycja rozwiazania:

- dodac dwa tryby exportu:
  - user-facing: wynik AI, wybrane metadane, visibility limits, usage summary,
    bez surowego kodu i pelnego promptu,
  - diagnostic: pelny prompt, artifacts, context snapshot, tool activity,
    quality report,
- UI powinno jasno nazywac tryby, np. `Export result` i `Export diagnostics`.

## Kolejnosc realizacji

### 000. Wspolny kontrakt i widok przebiegu pracy Copilota

- [x] Omowic krok i zatwierdzic zakres.
- [x] Przeniesc kontrakt referencji evidence do `shared.evidence`.
- [x] Przeniesc kontrakt kroku pracy Copilota do `shared.ai`.
- [x] Dostosowac Incident Tracker do wspolnego kontraktu bez legacy wrapperow.
- [x] Dostosowac Flow Explorer do wspolnego kontraktu bez legacy wrapperow.
- [x] Flow Explorer ma publikowac deterministic endpoint context jako
  `contextSections`, zeby wspolny panel mial te same dane co Incident Tracker.
- [x] Flow Explorer ma uzywac tego samego komponentu UI
  `app-analysis-steps-panel`.
- [x] Uruchomic testy celowane BE/FE po unifikacji.

Cel kroku:

Zanim zaczniemy poprawiac jakosc merytoryczna odpowiedzi, kazdy feature ma
pokazywac przebieg pracy Copilota w tym samym kontrakcie i tym samym widoku.
To usuwa drift miedzy Incident Trackerem i Flow Explorerem oraz daje jedno
miejsce do dalszego rozwijania podgladu krokow, evidence, promptu, activity i
tool feedbacku.

### 000a. Wspolny kontrakt i komponent follow-up chatu

- [x] Omowic krok i zatwierdzic zakres.
- [x] Przeniesc kontrakt odpowiedzi wiadomosci follow-up do `shared.ai`.
- [x] Dostosowac Incident Tracker do wspolnego kontraktu chatu bez legacy DTO.
- [x] Dostosowac Flow Explorer do wspolnego kontraktu chatu bez legacy DTO.
- [x] Wydzielic wspolny komponent FE `app-analysis-follow-up-chat`.
- [x] Podpiac Incident Tracker pod wspolny komponent bez utraty lepszego UI:
  markdown, kopiowanie, evidence i feedback tooli per wiadomosc.
- [x] Podpiac Flow Explorer pod wspolny komponent z tekstami dopasowanymi do
  endpoint follow-up.
- [x] Usunac lokalny legacy markup/style/metody chatu z Flow Explorera.
- [x] Uruchomic testy celowane BE/FE po unifikacji.

Cel kroku:

Follow-up chat jest wspolnym wzorcem pracy po wyniku AI, a nie detalem
pojedynczego feature'a. Incident Tracker mial dojrzalszy chat, dlatego jego
zachowanie zostaje przeniesione do shared komponentu i podlaczone rowniez pod
Flow Explorer. Feature'y nadal decyduja o dostepnosci chatu i wywolaniu
wlasnego endpointu, ale nie duplikuja prezentacji wiadomosci, evidence,
feedbacku tooli ani kopiowania.

### 001. Canonical tool inputs artifact

- [x] Omowic krok i zatwierdzic zakres.
- [x] Dodac artifact/sekcje `canonical-tool-inputs.md`.
- [x] Osadzic w nim kanoniczne argumenty GitLab i operational context dla
  wybranego runu.
- [x] Doprecyzowac prompt i skill, ze model ma uzywac tych wartosci bez
  rediscovery.
- [x] Dodac testy CRM-specific i zanonimizowane.

Cel kroku:

Zmniejszyc liczbe blednych tool calli spowodowanych zgadywaniem nazw projektu,
sciezek repozytorium i file pathow.

### 002. Tool policy dla redundantnego discovery

- [x] Omowic krok i zatwierdzic zakres.
- [x] Dodac Flow Explorer policy blokujaca redundantny context rebuild w
  initial run.
- [x] Ograniczyc repository rediscovery, gdy selected repository scope jest
  znany.
- [x] Zwrocic modelowi czytelny denied message z instrukcja uzycia artifacts.
- [x] Zostawic follow-up z osobna, ostrozniejsza polityka.
- [x] Dodac testy CRM-specific i zanonimizowane.

Cel kroku:

Zamienic miekkie guidance w egzekwowalna polityke runtime i ograniczyc koszt
bez polegania wylacznie na posluszenstwie modelu.

### 003. Skill guidance i UI sterowania modelem AI

- [x] Omowic krok i zatwierdzic zakres.
- [x] Dostosowac runtime skille Flow Explorera: `focusAreas` sa kierunkiem
  analizy, a nie poziomem glebokosci.
- [x] Dostosowac runtime skille Flow Explorera: `reasoningEffort` steruje
  glebokoscia eksploracji i uzasadnieniem dodatkowych tool calli.
- [x] Doprecyzowac prompt, ze wynik zawsze zaczyna od primary endpoint flow,
  a `focusAreas` tylko przesuwaja akcenty.
- [x] Dodac w UI Flow Explorera wybor modelu AI i reasoning effort z katalogu
  `GET /analysis/ai/options`.
- [x] Zachowac spojnosc UX z Incident Trackerem i nie tworzyc osobnego
  zrodla prawdy dla modeli AI.
- [x] Dodac testy CRM-specific i zanonimizowane.

Cel kroku:

Ustawic prawidlowy kontrakt sterowania analiza: `focusAreas` mowia, gdzie AI
ma patrzec, `documentationPreset` mowi dla kogo i w jakim formacie pisac, a
`reasoningEffort` mowi jak gleboko wolno zejsc w dodatkowe czytanie kodu i
operational context. UI ma pozwolic operatorowi jawnie wybrac model i effort,
tak jak w Incident Trackerze.

### 004. Snippet ranking pod primary flow i focus areas

- [ ] Omowic krok i zatwierdzic zakres.
- [ ] Dodac albo dopracowac ranking kandydatow snippetow w
  `features.flowexplorer`.
- [ ] Priorytetyzowac primary use-case service i mapper wejscia przed
  secondary read/response details.
- [ ] Uwzglednic focus areas jako kierunek rankingu, nie jako poziom
  glebokosci analizy.
- [ ] Dodac coverage diagnostics: primary roles covered/missing.
- [ ] Dodac testy CRM-specific i zanonimizowane.

Cel kroku:

Sprawic, zeby initial context przenosil najwieksza wartosc merytoryczna w
malym budzecie tokenowym.

### 005. Baseline quality report model

- [ ] Omowic krok i zatwierdzic zakres.
- [ ] Dodac feature-local model quality report dla Flow Explorer runu.
- [ ] Zmapowac istniejace usage/activity/context coverage na pierwsze
  quality signals.
- [ ] Dodac diagnostic export pola z quality report.
- [ ] Dodac testy CRM-specific i zanonimizowane.

Cel kroku:

Po wprowadzeniu pierwszych zmian kosztowych mierzymy run w powtarzalny sposob.
To pozwoli sprawdzac, czy canonical inputs, policy i ranking snippetow realnie
zmniejszaja koszt oraz redundantne tool calle.

### 006. Ranking i grupowanie limitations/next reads

- [ ] Omowic krok i zatwierdzic zakres.
- [ ] Rozdzielic limitations na technical/user-facing/AI-guidance.
- [ ] Grupowac powtarzalne niskopoziomowe ograniczenia.
- [ ] Przyciac inline `suggestedNextReads` do top N dla focus areas.
- [ ] Zostawic pelna liste w diagnostic artifact, jezeli jest potrzebna.
- [ ] Dodac testy CRM-specific i zanonimizowane.

Cel kroku:

Poprawic czytelnosc wyniku i ograniczyc pokuse nadmiernego doczytywania kodu.

### 007. Result contract: fact vs inference vs unknown

- [ ] Omowic krok i zatwierdzic zakres.
- [ ] Dostosowac runtime skill `flow-explorer-result-contract`.
- [ ] Dostosowac parser/DTO, jezeli kontrakt wymaga strukturalnej zmiany.
- [ ] Dodac walidacje albo quality flags dla zbyt pewnych twierdzen.
- [ ] Dodac testy CRM-specific i zanonimizowane.

Cel kroku:

Poprawic merytoryczna wiarygodnosc wyniku i ograniczyc ryzyko, ze AI opisze
inferencje jako potwierdzony fakt.

### 008. Export result vs export diagnostics

- [ ] Omowic krok i zatwierdzic zakres.
- [ ] Rozdzielic export user-facing od diagnostic export.
- [ ] User-facing export nie powinien zawierac surowego kodu ani pelnego
  promptu.
- [ ] Diagnostic export powinien zawierac quality report i pelne artifacts.
- [ ] Dostosowac UI i import, jezeli trzeba.
- [ ] Dodac testy CRM-specific i zanonimizowane.

Cel kroku:

Dopasowac eksport do dwoch roznych potrzeb: przekazania wyniku analitykowi i
debugowania jakosci platformy przez developera.

## Proponowane metryki sukcesu

Te progi sa startowa hipoteza i moga zostac skorygowane po pierwszych dwoch
krokach:

- initial run dla typowego endpointu nie powinien wymagac repository
  rediscovery,
- initial run nie powinien odpalac ponownie endpoint use-case context, jezeli
  context zostal juz osadzony,
- primary flow powinien miec snippet coverage dla controller + primary service
  + co najmniej jednego persistence/mapper elementu zgodnego z focus areas,
- bledne `projectName`/`filePath` powinny byc blokowane albo flagowane,
- user-facing limitations powinny byc krotkie i zrozumiale dla analityka,
- diagnostic export powinien pozwalac wyjasnic, dlaczego odpowiedz kosztowala
  wiecej niz oczekiwano.

## Guardraile architektoniczne

- `integrations.gitlab` pozostaje neutralna capability i nie importuje
  Flow Explorera.
- Flow Explorer moze budowac wlasny ranking, prompt, artifacts, policy,
  skills, quality report i export projection w `features.flowexplorer`.
- `agenttools` nie dostaje semantyki Flow Explorera; moze dostac tylko
  neutralne mechanizmy, jezeli beda faktycznie wspolne.
- `aiplatform` moze egzekwowac mechanike invocation/policy, ale nie zna
  semantyki endpointow ani result contract Flow Explorera.
- Nie przenosimy logiki do `features.incidentanalysis`.
- Testy nowych przypadkow maja uzywac zanonimizowanych nazw CRM, np.
  `crm-customer-workflow`, `CRM_CUSTOMER_WORKFLOW`,
  `crm/customer-profile`, zamiast realnych nazw systemow.

## Decision log

### 001. Plan usprawnien po smoke tescie exportu

Decyzja: tworzymy osobny plan usprawnien jakosci Flow Explorera zamiast
rozszerzac glowny plan implementacyjny.

Powod: glowny feature juz istnieje, a obecny problem dotyczy jakosci,
kosztow, polityki tooli i diagnostyki po realnym runie. Osobny dokument
ulatwia egzekwowanie kolejnych krokow i mierzenie poprawy.

Status: accepted.

### 002. Wspolny przebieg pracy Copilota przed quality improvements

Decyzja: przed usprawnieniami merytorycznymi Flow Explorera ujednolicamy
kontrakt i UI przebiegu pracy Copilota z Incident Trackerem. Flow Explorer nie
utrzymuje wlasnego, uproszczonego widoku trace ani osobnego DTO kroku joba.

Powod: lokalny trace Flow Explorera powodowal drift kontraktowy i UI. Panel
Incident Trackera jest czytelniejszy, pokazuje szczegoly evidence/activity i
powinien byc wspolnym wzorcem dla kolejnych feature'ow analitycznych.

Status: implemented and verified.

### 003. Wspolny follow-up chat przed quality improvements

Decyzja: follow-up chat ma miec wspolny publiczny model wiadomosci i wspolny
komponent frontendowy. Incident Tracker i Flow Explorer nie utrzymuja osobnych
DTO odpowiedzi chatu ani osobnych implementacji prezentacji wiadomosci.

Powod: chat po analizie ma ten sam cel produktowy w kazdym feature: dopytanie
AI o wynik, pokazanie odpowiedzi, evidence/tool feedbacku i umozliwienie
kopiowania. Lokalny chat Flow Explorera byl ubozszy i powodowal UI/contract
drift wzgledem Incident Trackera.

Status: implemented and verified.

### 004. Priorytet na redukcje blednych tool calli przed diagnostyka

Decyzja: `Baseline quality report model` nie jest pierwszym krokiem
usprawnien merytorycznych. Najpierw realizujemy `canonical-tool-inputs.md`,
potem polityke blokujaca redundantne discovery, nastepnie ranking snippetow.
Quality report zostaje przesuniety za te zmiany jako pomiar efektow, a nie
blokada startowa.

Powod: smoke test pokazal, ze najwiekszy koszt i ryzyko wynikaja z tego, ze
model zgaduje GitLab inputs albo odtwarza context, ktory backend juz zna.
Raport diagnostyczny jest przydatny, ale sam nie ogranicza kosztu ani nie
poprawia odpowiedzi.

Status: accepted.

### 005. Canonical tool inputs artifact

Decyzja: Flow Explorer dodaje osobny artefakt
`flow-explorer/canonical-tool-inputs.md` oraz osadza jego tresc w initial i
follow-up promptach. Artefakt zawiera kanoniczne wartosci potrzebne modelowi
do tool calli: `applicationName`, `systemId`, `endpointId`, `httpMethod`,
`endpointPath`, `branchRef`, repozytoria GitLaba, `projectName`,
`projectPath`, kanoniczne `filePath` i metody z flow/snippet cards.

Powod: `context-snapshot.json` pozostaje pelniejszym manifestem, ale model
potrzebuje krotkiej sciagi narzedziowej. Bez niej latwo zgaduje
`projectName`, `projectPath` albo `filePath`, szczegolnie przy repozytoriach w
podgrupach. `canonical-tool-inputs.md` ma zmniejszyc liczbe blednych tool
calli i ograniczyc rediscovery.

Status: implemented and verified.

### 006. Tool policy dla redundantnego discovery

Decyzja: Flow Explorer dodaje feature-owned
`FlowExplorerCopilotRedundantDiscoveryPolicy`, ktora dziala tylko dla ukrytego
runtime contextu `flowExplorerFeature=flow-explorer` i
`flowExplorerRunKind=initial`. Policy blokuje
`gitlab_build_endpoint_use_case_context`, gdy deterministic endpoint context
jest juz osadzony w artefaktach initial runu, oraz
`gitlab_list_available_repositories`, gdy repository scope jest juz resolved.

Powod: canonical tool inputs i prompt guidance zmniejszaja ryzyko, ale nie
gwarantuja, ze model nie wykona kosztownego rediscovery. Twarda policy zamienia
redundantny tool call w czytelny denied result z instrukcja uzycia
`flow-explorer/canonical-tool-inputs.md`, `compact-flow-manifest.md` i focused
GitLab reads. Follow-up pozostaje nieblokowany, bo tam uzytkownik moze
potrzebowac dodatkowego discovery po nowym pytaniu.

Status: implemented and verified.

### 007. Skill guidance i UI sterowania modelem AI

Decyzja: Flow Explorer traktuje `focusAreas` jako kierunki analizy i akcenty
wyniku, a nie jako poziom glebokosci ani filtr pozwalajacy pominac primary
endpoint flow. `reasoningEffort` jest jawna kontrola glebokosci eksploracji:
`low` oznacza artifact-first i minimalne tool calls, `medium` focused reads
dla brakow primary flow, a `high` glebsze edge case'y i zaleznosci, nadal przez
canonical inputs i focused reads.

Decyzja UI: ekran Flow Explorera pobiera katalog modeli z tego samego shared
operator API co Incident Tracker, czyli `GET /analysis/ai/options`, i pozwala
operatorowi wybrac `model` oraz `reasoningEffort` w composerze runu. Backendowy
kontrakt startu Flow Explorera byl juz gotowy na te pola, wiec UI tylko
przestaje polegac na domyslnym backendzie bez widocznej kontroli.

Powod: smoke test pokazal, ze samo dodawanie opcji focus moze byc odczytane
przez AI jako zaproszenie do szerokiego opisywania wielu obszarow. Lepsza
separacja sterowania pozwala utrzymac wynik w primary endpoint flow, a koszt i
glebokosc kontrolowac parametrem, ktory operator juz zna z Incident Trackera.

Status: implemented and verified.

## Checklist status

- [x] Utworzono plan usprawnien po analizie realnego exportu.
- [x] 000. Wspolny kontrakt i widok przebiegu pracy Copilota.
- [x] 000a. Wspolny kontrakt i komponent follow-up chatu.
- [x] 001. Canonical tool inputs artifact.
- [x] 002. Tool policy dla redundantnego discovery.
- [x] 003. Skill guidance i UI sterowania modelem AI.
- [ ] 004. Snippet ranking pod primary flow i focus areas.
- [ ] 005. Baseline quality report model.
- [ ] 006. Ranking i grupowanie limitations/next reads.
- [ ] 007. Result contract: fact vs inference vs unknown.
- [ ] 008. Export result vs export diagnostics.
