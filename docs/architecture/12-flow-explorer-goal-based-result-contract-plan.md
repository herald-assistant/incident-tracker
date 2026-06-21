# Flow Explorer Goal-Based Result Contract Plan

## Cel dokumentu

Ten dokument opisuje ground-breaking zmiane kontraktu merytorycznego Flow
Explorera. Celem jest zastapienie obecnych presetow i zbyt ogolnego wyniku
modelem opartym o cel analizy, stale sekcje rezultatu i jawna glebokosc sekcji.

Nie utrzymujemy kompatybilnosci wstecznej dla roboczych kontraktow Flow
Explorera, import/export, parsera wyniku, enumow UI ani runtime skilli.
Stare pola, wartosci i widoki usuwamy w trakcie implementacji, jezeli
zaciemniaja docelowy model.

Plan wykonujemy krok po kroku:

- przed kazdym krokiem opisujemy potrzebe, proponowane rozwiazanie, dotykane
  pliki/pakiety i ryzyka,
- implementujemy dopiero po zatwierdzeniu kroku przez uzytkownika,
- po kazdym wykonanym kroku aktualizujemy checklisty w tym pliku,
- jezeli w trakcie pracy zmieni sie decyzja, aktualizujemy sekcje
  "Decision log" przed dalsza implementacja,
- testy i fixture'y maja byc zawsze CRM-specific i zanonimizowane.

## Problem do rozwiazania

Aktualny wynik Flow Explorera bywa poprawny faktograficznie, ale zbyt malo
uzyteczny dla analityka biznesowego, analityka systemowego albo testera.
Wynik opisuje czesci endpointu, ale nie daje wystarczajaco kompletnego,
samowystarczalnego materialu do pracy.

Najwazniejsze przyczyny:

- obecne presety wygladaja jak typ dokumentacji, a nie realny cel uzytkownika,
- `focusAreas` mieszaja obszary analizy z celami typu testy/ryzyka,
- rezultat nie ma jednego twardego template'u: model sam decyduje, co
  rozwinac, a co pominac,
- follow-up chat staje sie narzedziem ratowania zbyt powierzchownego initial
  rezultatu,
- UI nie moze latwo renderowac wyniku w powtarzalnym, przewidywalnym ukladzie.

Docelowo initial result ma pokrywac wiekszosc potrzeb. Follow-up chat zostaje
do wyjatkow, doprecyzowan i nowych pytan, a nie jako standardowy drugi etap
uzyskania wartosci.

## Docelowy model produktu

### Cele analizy

Flow Explorer ma trzy cele analizy:

1. `DEEP_DISCOVERY` / `Deep Discovery`
   - cel: kompleksowe zrozumienie endpointu i glownego use case'u,
   - odbiorca: analityk biznesowy, analityk systemowy, tester poznajacy flow,
   - wynik: co endpoint robi, jak przebiega flow, jakie reguly, walidacje,
     zapisy i integracje sa istotne.

2. `TEST_SCENARIOS` / `Test scenarios`
   - cel: przygotowanie scenariuszy testowych i danych do weryfikacji,
   - odbiorca: tester, analityk systemowy, osoba przygotowujaca odbior,
   - wynik: scenariusze happy path, negatywne, brzegowe, regresyjne oraz
     zaleznosci danych i systemow.

3. `RISK_DETECTION` / `Risk detection`
   - cel: znalezienie ryzyk, luk widocznosci, zaleznosci i miejsc regresji,
   - odbiorca: tester, analityk systemowy, PO/BA oceniajacy zmiane,
   - wynik: ryzyka biznesowe/systemowe, niepewne zalozenia, impacted areas i
     pytania otwarte.

### Focus areas

Focus areas nie sa celami. Sa obszarami, ktore steruja glebokoscia sekcji:

- `BUSINESS_FLOW_RULES` / `Business flow/rules`
- `VALIDATIONS` / `Validations`
- `PERSISTENCE` / `Persistence`
- `INTEGRATIONS` / `Integrations`

Usuwamy focus areas `TEST_SCENARIOS` i `RISKS`, bo sa reprezentowane przez
cele analizy.

### Sekcje rezultatu

Kazdy wynik initial run ma zawsze:

1. `Overview`
2. `Business flow/rules`
3. `Validations`
4. `Persistence`
5. `Integrations`

Kazda z czterech sekcji szczegolowych ma tryb:

- `compact` - gdy focus area nie jest zaznaczony,
- `deep` - gdy focus area jest zaznaczony.

`compact` nie znaczy powierzchownie. Compact ma zawierac najwazniejsze fakty,
decyzje, zaleznosci i ograniczenia widocznosci w zwartej formie.

`deep` ma pokazac konkretne reguly, warianty, przypadki brzegowe, source refs,
otwarte pytania i ograniczenia widocznosci dla danego obszaru.

### Transport i UI

Backend nadal wymaga twardego JSON jako kontraktu transportowego, ale pola
sekcji moga zawierac Markdown. UI renderuje wynik jako spójny ekran:

- Overview na gorze,
- cztery stale sekcje w znanej kolejnosc,
- badge `compact` albo `deep` przy kazdej sekcji,
- confidence/source refs/visibility limits w znanym wzorcu UI,
- follow-up chat pod wynikiem jako opcjonalna kontynuacja.

Przykladowy ksztalt docelowego kontraktu:

```json
{
  "goal": "DEEP_DISCOVERY",
  "audience": "business_or_system_analyst_tester",
  "overview": {
    "markdown": "string",
    "confidence": "high|medium|low",
    "sourceRefs": ["string"]
  },
  "sections": [
    {
      "id": "BUSINESS_FLOW_RULES",
      "title": "Business flow/rules",
      "mode": "compact|deep",
      "markdown": "string",
      "sourceRefs": ["string"],
      "visibilityLimits": ["string"],
      "openQuestions": ["string"]
    }
  ],
  "globalVisibilityLimits": ["string"],
  "globalOpenQuestions": ["string"],
  "sourceReferences": ["string"],
  "confidence": "high|medium|low"
}
```

Ostateczny ksztalt DTO moze zostac doprecyzowany w kroku kontraktowym, ale
nie powinien wracac do luznych list typu `businessRules`,
`testScenarios`, `risksAndEdgeCases` jako rownorzednych top-level pol.

## Zasady skilli

Kazdy cel analizy ma dedykowany skill:

- `flow-explorer-goal-deep-discovery`,
- `flow-explorer-goal-test-scenarios`,
- `flow-explorer-goal-risk-detection`.

Skill celu opisuje:

- kiedy dany cel jest uzywany,
- jak interpretowac evidence,
- jak wypelniac Overview,
- jak wypelniac kazda z czterech sekcji,
- co oznacza `compact` i `deep` w tym celu,
- jakiego rodzaju wnioski sa wartosciowe dla nietechnicznego uczestnika
  procesu wytworczego,
- czego nie robic, np. nie opisywac klas technicznie bez powiazania z
  zachowaniem endpointu.

Wspolny skill `flow-explorer-result-contract` zostaje odpowiedzialny za:

- twardy JSON-only transport,
- zawsze ten sam uklad Overview + 4 sekcje,
- reguly confidence/source refs/visibility limits/open questions,
- zasade: initial result ma byc samowystarczalny dla wiekszosci potrzeb,
- zasade: follow-up chat nie sluzy do nadrabiania zbyt ogolnej odpowiedzi.

## Relacja do reasoning effort

`reasoningEffort` nadal steruje glebokoscia eksploracji tools i kosztem
rozumowania modelu. Nie zastępuje trybu sekcji.

- focus area zaznaczone -> sekcja `deep`,
- focus area niezaznaczone -> sekcja `compact`,
- reasoning effort `low|medium|high` -> jak gleboko AI moze dociagac braki
  przez tools i jak ambitnie ma weryfikowac edge case'y.

## Kolejnosc realizacji

### 000. Utworzenie planu ground-breaking zmiany

Status: [x]

Potrzeba:

Zmiana dotyka publicznego kontraktu Flow Explorera, promptow, runtime skilli,
parsera, UI, import/export i testow. Potrzebny jest osobny plan, zeby nie
mieszac tej pracy z biezacym planem quality improvements.

Co wykonujemy:

- tworzymy ten dokument,
- zapisujemy decyzje startowe,
- ustalamy zasade braku kompatybilnosci wstecznej,
- ustalamy sposob aktualizacji checklisty po kazdym kroku.

### 001. Wspolny fundament kontraktu: goal, focus areas i result DTO

Status: [x]

Potrzeba:

Obecne enumy presetow i focus areas nie wyrazaja docelowego modelu. Parser i
job state musza miec jeden stabilny kontrakt wyniku, ktory UI moze renderowac
przewidywalnie. Ten krok nie powinien jeszcze probowac dopracowac wszystkich
trzech celow merytorycznie; ma przygotowac wspolne szyny, po ktorych kolejne
cele beda implementowane pionowo.

Co wykonujemy:

- zastapic obecne `FlowExplorerDocumentationPreset` celami:
  `DEEP_DISCOVERY`, `TEST_SCENARIOS`, `RISK_DETECTION`,
- zastapic obecne focus areas czterema obszarami:
  `BUSINESS_FLOW_RULES`, `VALIDATIONS`, `PERSISTENCE`, `INTEGRATIONS`,
- usunac stare wartosci bez legacy mapperow,
- zaprojektowac i wprowadzic nowy DTO wyniku:
  Overview + lista/cztery sekcje z `id`, `title`, `mode`, `markdown`,
  `sourceRefs`, `visibilityLimits`, `openQuestions`,
- dodac resolver trybu sekcji:
  zaznaczony focus area -> `deep`, brak focus area -> `compact`,
- dostosowac parser AI response do nowego shape'u, ale bez goal-specific
  semantyki,
- dostosowac job state do nowego result DTO,
- usunac stare pola resultu bez legacy fallbackow,
- dodac testy CRM-specific i zanonimizowane.

Ryzyka:

- import starych exportow Flow Explorera przestanie dzialac; to jest
  akceptowane w ramach braku kompatybilnosci,
- frontend bedzie wymagal rownoleglej zmiany, bo stare pola resultu znikna.

Wykonano:

- dodano `FlowExplorerAnalysisGoal` z wartosciami `DEEP_DISCOVERY`,
  `TEST_SCENARIOS`, `RISK_DETECTION`,
- ograniczono `FlowExplorerFocusArea` do:
  `BUSINESS_FLOW_RULES`, `VALIDATIONS`, `PERSISTENCE`, `INTEGRATIONS`,
- usunieto `FlowExplorerDocumentationPreset` oraz stare DTO
  `FlowExplorerAiEndpointContract` i `FlowExplorerAiFlowStep`,
- wprowadzono DTO wyniku:
  `FlowExplorerResultOverview`, `FlowExplorerResultSection`,
  `FlowExplorerResultSectionId`, `FlowExplorerResultSectionMode`,
  `FlowExplorerResultSectionModeAssignment`,
- dodano `FlowExplorerResultSectionModeResolver`, ktory mapuje requestowe
  focus areas na tryby `deep`/`compact`,
- dostosowano parser AI response, job state, snapshot/result API, context
  request, prompt preparation, follow-up prompt preparation i artefakty
  diagnostyczne do nowego shape'u,
- zaktualizowano backendowe testy Flow Explorera na zanonimizowanych
  CRM-specific fixture'ach,
- potwierdzono, ze frontend nadal zawiera stary roboczy kontrakt i zostanie
  przepiety w kolejnym pionowym slice'ie UI/result.

### 002. Deep Discovery vertical slice

Status: [x]

Potrzeba:

`Deep Discovery` powinien byc pierwszym celem wdrozonym end-to-end, bo jest
najbardziej bazowy: ma pokazac, czy nowy template rzeczywiscie daje
samowystarczalny opis endpointu. Ten slice waliduje caly mechanizm, zanim
powielimy go dla testow i ryzyk.

Co wykonujemy:

- przebudowac `flow-explorer-result-contract` pod nowy JSON transport i stale
  sekcje,
- dodac `flow-explorer-goal-deep-discovery`,
- skill ma zawierac konkretny template dla:
  - Overview,
  - Business flow/rules compact/deep,
  - Validations compact/deep,
  - Persistence compact/deep,
  - Integrations compact/deep,
- dopisac zasade, ze initial result ma byc kompleksowy i samowystarczalny,
  a follow-up jest wyjatkiem,
- zmienic prompt preparation tak, aby dla `DEEP_DISCOVERY` ladowal i wskazywal
  wlasciwy goal skill,
- przekazywac do promptu cztery sekcje i tryby `compact/deep`,
- dostosowac UI composer tak, aby `Deep Discovery` byl pierwszym dzialajacym
  celem,
- przebudowac bazowy UI result view pod Overview + 4 stale sekcje,
- dodac parser/prompt/UI tests dla `DEEP_DISCOVERY`,
- wykonac pierwszy smoke test albo fixture oceny rezultatu dla CRM-specific
  endpointu.

Ryzyka:

- zbyt agresywny template moze tworzyc dlugie odpowiedzi; dlatego template ma
  wymagac konkretu i source refs, a nie narracyjnego rozwijania oczywistosci,
- w trakcie tego kroku UI moze jeszcze nie eksponowac pozostalych celow jako
  gotowych do uzycia.

Wykonano:

- przebudowano `flow-explorer-result-contract` na JSON-only transport z
  `Overview` oraz czterema stalymi sekcjami,
- zaktualizowano `flow-explorer-orchestrator`, zeby uzywal `goal`,
  `sectionModes`, `focusAreas` jako trybow sekcji i `reasoningEffort` jako
  glebokosci eksploracji,
- dodano runtime skill `flow-explorer-goal-deep-discovery` z konkretnym
  template'em dla `Overview`, `Business flow/rules`, `Validations`,
  `Persistence`, `Integrations` oraz trybow `compact`/`deep`,
- zaktualizowano playbooki `flow-explorer-gitlab-tools` i
  `flow-explorer-operational-context-tools` do nowych nazw sekcji i pol wyniku,
- initial run dla `DEEP_DISCOVERY` laduje teraz goal-specific skill, a follow-up
  pozostaje lzejszy i nie laduje result contractu ani goal template'u,
- frontend Flow Explorera zostal przepiety z `documentationPreset` na `goal`,
  z domyslnym i aktywnym celem `Deep Discovery`,
- pozostale cele (`Test scenarios`, `Risk detection`) sa widoczne w UI jako
  zaplanowane, ale zablokowane do czasu ich vertical slice'ow,
- result view renderuje `Overview` oraz cztery sekcje AI response z badge
  `compact`/`deep`, source refs, visibility limits i open questions,
- import/export Flow Explorera obsluguje nowy result shape bez legacy mapperow,
- testy backendowe i frontendowe zaktualizowano na CRM-specific,
  zanonimizowanych fixture'ach,
- produkcyjny bundle Angulara zostal odswiezony w `src/main/resources/static`.

### 003. Test scenarios vertical slice

Status: [ ]

Potrzeba:

Po sprawdzeniu mechaniki na `Deep Discovery` wdrazamy drugi cel: przygotowanie
scenariuszy testowych. Ten cel ma inna wartosc produktu: wynik powinien
pomagac testerowi przygotowac pokrycie happy path, sciezek negatywnych,
przypadkow brzegowych i regresji bez dopytywania w follow-up.

Co wykonujemy:

- dodac `flow-explorer-goal-test-scenarios`,
- template celu ma wymagac scenariuszy testowych w kazdej sekcji:
  - Business flow/rules -> scenariusze procesowe i warunki biznesowe,
  - Validations -> przypadki negatywne i wymagane dane,
  - Persistence -> dane wejscia/wyjscia, stany zapisane, regresje danych,
  - Integrations -> zaleznosci systemowe, handoffy i awarie integracji,
- prompt preparation ma wybierac ten skill dla `TEST_SCENARIOS`,
- UI composer ma wlaczyc cel `Test scenarios`,
- parser tests i prompt tests maja potwierdzac nowy goal,
- dodac CRM-specific fixture wyniku dla celu testowego.

Ryzyka:

- wynik moze zaczac dublowac te same scenariusze w kilku sekcjach; skill ma
  wymuszac rozne perspektywy sekcji, a nie powtarzanie tej samej listy.

### 004. Risk detection vertical slice

Status: [ ]

Potrzeba:

Trzeci cel ma sluzyc wykrywaniu ryzyk, luk widocznosci i miejsc regresji.
Powinien byc wdrazany po `Deep Discovery` i `Test scenarios`, zeby skorzystac
z doswiadczen z dwoch poprzednich template'ow.

Co wykonujemy:

- dodac `flow-explorer-goal-risk-detection`,
- template celu ma wymagac ryzyk w kazdej sekcji:
  - Business flow/rules -> ryzyka niezrozumianych regul i wariantow procesu,
  - Validations -> ryzyka brakujacych walidacji albo zlych danych,
  - Persistence -> ryzyka stanu, transakcji, read/write i migracji danych,
  - Integrations -> ryzyka handoffow, awarii downstream/upstream i opoznien,
- prompt preparation ma wybierac ten skill dla `RISK_DETECTION`,
- UI composer ma wlaczyc cel `Risk detection`,
- parser tests i prompt tests maja potwierdzac nowy goal,
- dodac CRM-specific fixture wyniku dla celu ryzyk.

Ryzyka:

- model moze opisywac hipotetyczne ryzyka jako fakty; skill musi wymagac
  rozroznienia faktu, inferencji, luki widocznosci i pytania otwartego.

### 005. Cross-goal parser hardening i response validation

Status: [ ]

Potrzeba:

Gdy wszystkie trzy cele maja juz template'y i fixture'y, mozna bezpiecznie
utwardzic parser i walidacje. Wczesniejsze hardening moglby blokowac prace
nad template'ami zbyt wcześnie.

Co wykonujemy:

- parser ma wymagac poprawnego JSON bez Markdown poza polami `markdown`,
- parser ma wymagac dokladnie czterech sekcji o znanych `id`,
- parser ma weryfikowac `mode` sekcji wobec requestowych focus areas,
- parser ma wymagac `overview.markdown` i `section.markdown`,
- parser ma zachowac `visibilityLimits`, `openQuestions`, `confidence` i
  `sourceRefs`,
- parser ma odrzucac stare top-level pola jako kontrakt legacy,
- dodac testy negatywne i pozytywne dla wszystkich trzech celow.

Ryzyka:

- zbyt twardy parser moze czesciej failowac initial run; dlatego robimy go po
  wdrozeniu i przetestowaniu wszystkich goal-specific template'ow.

### 006. UI polish: composer, result view i copy

Status: [ ]

Potrzeba:

Po wlaczeniu trzech celow trzeba dopracowac UI jako jeden spójny workflow:
uzytkownik ma rozumiec, ze wybiera cel, a focus areas tylko pogłębiają sekcje.

Co wykonujemy:

- dopracowac select celu i tooltipy:
  - goal = po co uruchamiasz analize,
  - focus area = ktore sekcje maja byc bardziej szczegolowe,
  - reasoning effort = jak gleboko AI moze eksplorowac tools,
- upewnic sie, ze wszystkie trzy cele sa aktywne i opisane jezykiem
  nietechnicznym,
- dopracowac result view:
  - Overview,
  - Business flow/rules,
  - Validations,
  - Persistence,
  - Integrations,
- przy kazdej sekcji pokazywac badge `compact`/`deep`,
- renderowac markdown sekcji w bezpieczny sposob zgodny z obecnym podejsciem
  UI,
- source refs, visibility limits i open questions pokazac w czytelnym,
  powtarzalnym wzorcu,
- zachowac wspolny workflow UI: przebieg AI, tool evidence, usage, import/export
  i follow-up chat,
- dodac testy FE.

Ryzyka:

- zbyt wiele nested cards moze pogorszyc UX; sekcje powinny byc proste,
  szerokie i zgodne z aktualnym stylem Flow Explorera.

### 007. Import/export i diagnostic artifacts

Status: [ ]

Potrzeba:

Zmiana kontraktu wyniku oznacza, ze export/import Flow Explorera oraz
diagnostic artifacts musza opisywac nowy result shape bez legacy pol.

Co wykonujemy:

- zaktualizowac export completed analysis do nowego result DTO,
- usunac obsluge starych pol resultu w import/export,
- diagnostic export ma zachowac:
  - goal,
  - focus areas,
  - section modes,
  - prompt,
  - artifacts,
  - clipping notes,
  - usage/activity/tool evidence,
- user-facing export ma renderowac Overview + cztery sekcje bez surowego kodu,
  jezeli ten tryb zostal juz rozdzielony,
- dodac testy import/export.

Ryzyka:

- stare pliki exportu Flow Explorera nie beda kompatybilne; to jest
  akceptowane i powinno byc opisane w finalnym podsumowaniu kroku.

### 008. Follow-up chat alignment

Status: [ ]

Potrzeba:

Follow-up chat ma dzialac na nowym wyniku i nie powinien zachecac do
odtwarzania calej analizy od nowa. Ma sluzyc do doprecyzowania wyjatkow.

Co wykonujemy:

- prompt follow-up ma streszczac goal, section modes i wynik initial,
- follow-up ma rozumiec cztery sekcje jako stale punkty odniesienia,
- jezeli uzytkownik pyta o obszar, ktory byl `compact`, AI moze dociagnac
  szczegol przez tools zgodnie z reasoning effort i tool policy,
- jezeli uzytkownik pyta o obszar `deep`, AI ma najpierw wykorzystac initial
  evidence i wynik, zanim zrobi dodatkowe calls,
- dodac testy follow-up promptu i job/chat API.

Ryzyka:

- follow-up moze zaczac powtarzac caly initial result; prompt powinien
  wymagac odpowiedzi tylko na pytanie uzytkownika.

### 009. Cross-goal smoke test i korekta template'ow

Status: [ ]

Potrzeba:

Twarde template'y musza zostac sprawdzone dla kazdego celu na realnym albo
zanonimizowanym przykladzie CRM. Sama zgodnosc parsera nie gwarantuje
uzytecznosci wyniku.

Co wykonujemy:

- uruchomic smoke test Flow Explorera dla CRM-specific endpointu dla:
  - `Deep Discovery`,
  - `Test scenarios`,
  - `Risk detection`,
- ocenic:
  - czy initial result jest samowystarczalny,
  - czy Overview daje szybkie zrozumienie,
  - czy kazda sekcja wnosi wartosc,
  - czy `compact` nie jest powierzchowne,
  - czy `deep` jest konkretne, a nie rozwlekle,
  - czy follow-up jest potrzebny tylko do wyjatkow,
  - czy usage/token koszt pozostaje akceptowalny,
- zaktualizowac skille/template'y, jezeli smoke test pokaze braki,
- zaktualizowac ten plan o decyzje wynikajace ze smoke testu.

Ryzyka:

- pierwszy template moze byc zbyt dlugi albo nadal zbyt ogolny; korekta
  skilli jest spodziewana czescia tego kroku.

## Checklist status

- [x] 000. Utworzenie planu ground-breaking zmiany.
- [x] 001. Wspolny fundament kontraktu: goal, focus areas i result DTO.
- [x] 002. Deep Discovery vertical slice.
- [ ] 003. Test scenarios vertical slice.
- [ ] 004. Risk detection vertical slice.
- [ ] 005. Cross-goal parser hardening i response validation.
- [ ] 006. UI polish: composer, result view i copy.
- [ ] 007. Import/export i diagnostic artifacts.
- [ ] 008. Follow-up chat alignment.
- [ ] 009. Cross-goal smoke test i korekta template'ow.

## Guardraile architektoniczne

- Zmiana result contractu jest feature-specific i zostaje w
  `features.flowexplorer`.
- `integrations.gitlab` pozostaje neutralna capability; nie dostaje semantyki
  celow Flow Explorera.
- `agenttools` pozostaja neutralne; goal-specific zasady uzycia tools mieszkaja
  w promptach, skillach i policy Flow Explorera.
- `aiplatform` nie zna celow Flow Explorera ani sekcji wyniku.
- Shared UI/UX nadal obejmuje trace, activity, tool evidence, usage, follow-up
  chat i import/export mechanics, ale merytoryczny result renderer moze byc
  feature-specific.
- Nie importujemy `features.incidentanalysis` do Flow Explorera.
- Nie utrzymujemy legacy mapperow dla starych presetow/focus areas.

## Decision log

### 001. Przejscie z presetow dokumentacji na cele analizy

Decyzja: Flow Explorer zastapi obecne presety trzema celami:
`DEEP_DISCOVERY`, `TEST_SCENARIOS`, `RISK_DETECTION`.

Powod: uzytkownik nietechniczny mysli celem pracy, np. rozpoznanie flow,
przygotowanie testow albo wykrycie ryzyk, a nie typem dokumentacji.

Status: accepted.

### 002. Focus areas steruja trybem sekcji

Decyzja: focus areas zostaja ograniczone do czterech obszarow wyniku:
`BUSINESS_FLOW_RULES`, `VALIDATIONS`, `PERSISTENCE`, `INTEGRATIONS`.
Zaznaczony focus ustawia sekcje na `deep`, brak zaznaczenia ustawia `compact`.

Powod: test scenarios i risks sa celami analizy, nie obszarami wyniku.
Oddzielenie celu od glebokosci sekcji daje przewidywalny rezultat i prostszy
UI.

Status: accepted.

### 003. JSON transport, Markdown w polach sekcji

Decyzja: wynik AI pozostaje twardym JSON-em, ale Overview i sekcje moga niesc
Markdown jako zawartosc pol `markdown`.

Powod: backend potrzebuje stabilnego parsera, import/export i walidacji, a UI
potrzebuje czytelnego formatowania dla czlowieka.

Status: accepted.

### 004. Initial result ma byc samowystarczalny

Decyzja: runtime skille maja wymagac kompleksowego initial resultu, ktory
pokrywa wiekszosc potrzeb uzytkownika. Follow-up chat sluzy do wyjatkow,
doprecyzowan i nowych pytan, a nie do nadrabiania powierzchownej odpowiedzi.

Powod: celem Flow Explorera jest szybkie poznanie endpointu bottom-up.
Jesli kazdy run wymaga wielu follow-upow, initial analysis nie spelnia celu
produktu i zwieksza koszt.

Status: accepted.

### 005. Realizacja cel po celu

Decyzja: po wspolnym fundamencie kontraktu wdrazamy cele pionowo:
najpierw `Deep Discovery`, potem `Test scenarios`, a na koncu
`Risk detection`. Nie tworzymy wszystkich goal-specific skilli i promptow w
jednym kroku.

Powod: `Deep Discovery` jest najlepszym pierwszym dowodem nowego modelu,
poniewaz sprawdza caly uklad Overview + cztery sekcje + compact/deep bez
dodatkowej presji generowania scenariuszy albo ryzyk. Po jego ocenie mozna
lepiej napisac template'y dla kolejnych celow i uniknac powielenia bledow w
trzech skillach naraz.

Status: accepted.

### 006. Backendowy fundament kontraktu wdrozony bez legacy mapperow

Decyzja: krok 001 zostal zamkniety jako backendowy fundament kontraktu.
Backend nie przyjmuje juz `documentationPreset`, starych focus areas ani
starych top-level pol resultu typu `endpointContract`, `flowSteps`,
`businessRules`, `testScenarios`, `risksAndEdgeCases`.

Powod: kontrakt Flow Explorera ma byc dalej rozwijany cel po celu na jednym
stabilnym modelu `goal` + `Overview` + cztery sekcje z trybem
`compact`/`deep`. Utrzymywanie starych DTO lub mapperow zwiekszaloby ryzyko,
ze prompt, UI albo import/export zaczna korzystac z poprzedniego modelu.

Status: implemented.

### 007. Deep Discovery jako pierwszy aktywny cel end-to-end

Decyzja: `DEEP_DISCOVERY` jest pierwszym aktywnym celem Flow Explorera
wdrozonym end-to-end. `TEST_SCENARIOS` i `RISK_DETECTION` zostaja w modelu i
UI jako zaplanowane cele, ale w UI sa zablokowane do czasu ich osobnych
vertical slice'ow.

Powod: jeden kompletny cel pozwala zweryfikowac template, runtime skille,
prompt, parser, UI, import/export i koszt odpowiedzi bez powielania bledow w
trzech celach jednoczesnie.

Status: implemented.
