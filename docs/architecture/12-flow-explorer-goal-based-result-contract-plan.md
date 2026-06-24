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

- `FUNCTIONAL_FLOW` / `Functional flow`
- `VALIDATIONS` / `Validations`
- `PERSISTENCE` / `Persistence`
- `INTEGRATIONS` / `Integrations`

Usuwamy focus areas `TEST_SCENARIOS` i `RISKS`, bo sa reprezentowane przez
cele analizy.

### Sekcje rezultatu

Kazdy wynik initial run ma zawsze `Overview` oraz sekcje aktywne wedlug
`sectionModes`. Kanoniczne obszary wyniku to:

1. `Overview`
2. `Functional flow`
3. `Validations`
4. `Persistence`
5. `Integrations`

Kazda aktywna sekcja szczegolowa ma tryb:

- `compact` - gdy focus area nie jest zaznaczony,
- `deep` - gdy focus area jest zaznaczony.

Sekcja moze miec tez tryb `OFF`, jezeli uzytkownik ja wylaczy w
`sectionModes`; wtedy nie pojawia sie w `sections`. `sectionModes` jest
zrodlem prawdy dla transportu, a `focusAreas` sa skrotem ustawiajacym wybrane
sekcje na `deep`.

`compact` nie znaczy powierzchownie. Compact ma zawierac najwazniejsze fakty,
decyzje, zaleznosci i ograniczenia widocznosci w zwartej formie.

`deep` ma pokazac konkretne reguly, warianty, przypadki brzegowe, source refs,
otwarte pytania i ograniczenia widocznosci dla danego obszaru.

`Functional flow` jest glowna sekcja przebiegu; domyslnie prowadzi interpretacje
endpointu od wejscia do efektow ubocznych. Jej markdown ma stala, flow-first
strukture:

- `Cel funkcjonalny`,
- `Flow krok po kroku`,
- `Koordynacja i routing`,
- `Kalkulacje i reguly funkcjonalne`,
- `Rozgalezienia zalezne od kontekstu`,
- `Handoffy i efekty uboczne`,
- `Akcent goal`.

Evidence, source refs, ograniczenia widocznosci i pytania otwarte nie sa
punktami glownego markdownu `Functional flow`. Sa przekazywane przez osobne
pola `sourceRefs`, `visibilityLimits` i `openQuestions`, ktore UI pokazuje w
zwijanych elementach tak samo jak przy innych sekcjach.

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
      "id": "FUNCTIONAL_FLOW",
      "title": "Functional flow",
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
nie powinien wracac do luznych list tematycznych jako rownorzednych top-level
pol. Perspektywa celu analizy ma mieszkac w `goal`, `overview` i aktywnych
sekcjach.

## Zasady skilli

Kazdy cel analizy ma dedykowany skill:

- `flow-explorer-goal-deep-discovery`,
- `flow-explorer-goal-test-scenarios`,
- `flow-explorer-goal-risk-detection`.

Skill celu opisuje:

- kiedy dany cel jest uzywany,
- jak interpretowac evidence,
- jak wypelniac Overview,
- jak wypelniac kazda aktywna sekcje,
- co oznacza `compact` i `deep` w tym celu,
- jakiego rodzaju wnioski sa wartosciowe dla nietechnicznego uczestnika
  procesu wytworczego,
- czego nie robic, np. nie opisywac klas technicznie bez powiazania z
  zachowaniem endpointu.

Wspolny skill `flow-explorer-result-contract` zostaje odpowiedzialny za:

- twardy JSON-only transport,
- zawsze ten sam uklad Overview + aktywne sekcje wedlug `sectionModes`,
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
  `FUNCTIONAL_FLOW`, `VALIDATIONS`, `PERSISTENCE`, `INTEGRATIONS`,
- usunac stare wartosci bez mapperow kompatybilnosci wstecznej,
- zaprojektowac i wprowadzic nowy DTO wyniku:
  Overview + lista aktywnych sekcji z `id`, `title`, `mode`, `markdown`,
  `sourceRefs`, `visibilityLimits`, `openQuestions`,
- dodac resolver trybu sekcji:
  zaznaczony focus area -> `deep`, brak focus area -> `compact`,
- dostosowac parser AI response do nowego shape'u, ale bez goal-specific
  semantyki,
- dostosowac job state do nowego result DTO,
- usunac stare pola resultu bez fallbackow kompatybilnosci wstecznej,
- dodac testy CRM-specific i zanonimizowane.

Ryzyka:

- import starych exportow Flow Explorera przestanie dzialac; to jest
  akceptowane w ramach braku kompatybilnosci,
- frontend bedzie wymagal rownoleglej zmiany, bo stare pola resultu znikna.

Wykonano:

- dodano `FlowExplorerAnalysisGoal` z wartosciami `DEEP_DISCOVERY`,
  `TEST_SCENARIOS`, `RISK_DETECTION`,
- ograniczono `FlowExplorerFocusArea` do:
  `FUNCTIONAL_FLOW`, `VALIDATIONS`, `PERSISTENCE`, `INTEGRATIONS`,
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
  - Functional flow compact/deep,
  - Validations compact/deep,
  - Persistence compact/deep,
  - Integrations compact/deep,
- dopisac zasade, ze initial result ma byc kompleksowy i samowystarczalny,
  a follow-up jest wyjatkiem,
- zmienic prompt preparation tak, aby dla `DEEP_DISCOVERY` ladowal i wskazywal
  wlasciwy goal skill,
- przekazywac do promptu `sectionModes`, aktywne sekcje i tryby
  `compact/deep`,
- dostosowac UI composer tak, aby `Deep Discovery` byl pierwszym dzialajacym
  celem,
- przebudowac bazowy UI result view pod Overview + aktywne sekcje w stalej
  kolejnosci,
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
  `Overview` oraz aktywnymi sekcjami z `sectionModes`,
- zaktualizowano `flow-explorer-orchestrator`, zeby uzywal `goal`,
  `sectionModes`, `focusAreas` jako trybow sekcji i `reasoningEffort` jako
  glebokosci eksploracji,
- dodano runtime skill `flow-explorer-goal-deep-discovery` z konkretnym
  template'em dla `Overview`, `Functional flow`, `Validations`,
  `Persistence`, `Integrations` oraz trybow `compact`/`deep`,
- zaktualizowano playbooki `flow-explorer-gitlab-tools` i
  `flow-explorer-operational-context-tools` do nowych nazw sekcji i pol wyniku,
- initial run dla `DEEP_DISCOVERY` laduje teraz goal-specific skill, a follow-up
  pozostaje lzejszy i nie laduje result contractu ani goal template'u,
- frontend Flow Explorera zostal przepiety z `documentationPreset` na `goal`,
  z domyslnym i aktywnym celem `Deep Discovery`,
- pozostale cele (`Test scenarios`, `Risk detection`) sa widoczne w UI jako
  zaplanowane, ale zablokowane do czasu ich vertical slice'ow,
- result view renderuje `Overview` oraz aktywne sekcje AI response z badge
  `compact`/`deep`, source refs, visibility limits i open questions,
- import/export Flow Explorera obsluguje nowy result shape bez mapperow
  kompatybilnosci wstecznej,
- testy backendowe i frontendowe zaktualizowano na CRM-specific,
  zanonimizowanych fixture'ach,
- produkcyjny bundle Angulara zostal odswiezony w `src/main/resources/static`.

### 003. Test scenarios vertical slice

Status: [x]

Potrzeba:

Po sprawdzeniu mechaniki na `Deep Discovery` wdrazamy drugi cel: przygotowanie
scenariuszy testowych. Ten cel ma inna wartosc produktu: wynik powinien
pomagac testerowi przygotowac pokrycie happy path, sciezek negatywnych,
przypadkow brzegowych i regresji bez dopytywania w follow-up.

Co wykonujemy:

- dodac `flow-explorer-goal-test-scenarios`,
- template celu ma wymagac scenariuszy testowych w kazdej sekcji:
  - Functional flow -> scenariusze procesowe i warunki biznesowe,
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

Wykonano:

- dodano runtime skill `flow-explorer-goal-test-scenarios` z konkretnym
  template'em dla `Overview`, `Functional flow`, `Validations`,
  `Persistence`, `Integrations` oraz trybow `compact`/`deep`,
- initial run dla `TEST_SCENARIOS` laduje teraz goal-specific skill testowy i
  nie laduje skilla `DEEP_DISCOVERY`,
- `FlowExplorerCopilotRuntimeSkillNames.allSkillNames()` zawiera skill testowy,
  zeby kontrakt runtime i pliki skillow byly walidowane razem,
- frontend Flow Explorera odblokowuje cel `Test scenarios` w selectcie celu,
- test UI potwierdza, ze wybor `Test scenarios` trafia do request payload jako
  `goal: TEST_SCENARIOS`,
- dodano CRM-specific, zanonimizowany fixture parsera AI response dla
  `TEST_SCENARIOS` z aktywnymi sekcjami,
- produkcyjny bundle Angulara zostal odswiezony w `src/main/resources/static`.

### 004. Risk detection vertical slice

Status: [x]

Potrzeba:

Trzeci cel ma sluzyc wykrywaniu ryzyk, luk widocznosci i miejsc regresji.
Powinien byc wdrazany po `Deep Discovery` i `Test scenarios`, zeby skorzystac
z doswiadczen z dwoch poprzednich template'ow.

Co wykonujemy:

- dodac `flow-explorer-goal-risk-detection`,
- template celu ma wymagac ryzyk w kazdej sekcji:
  - Functional flow -> ryzyka niezrozumianych regul i wariantow procesu,
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

Wykonano:

- dodano runtime skill `flow-explorer-goal-risk-detection` z konkretnym
  template'em dla `Overview`, `Functional flow`, `Validations`,
  `Persistence`, `Integrations` oraz trybow `compact`/`deep`,
- skill wymaga rozroznienia ryzyk na: `Fakt z evidence`, `Inferencja`,
  `Luka widocznosci` i `Pytanie otwarte`,
- initial run dla `RISK_DETECTION` laduje goal-specific skill ryzyk i nie
  laduje skilli `DEEP_DISCOVERY` ani `TEST_SCENARIOS`,
- `FlowExplorerCopilotRuntimeSkillNames.allSkillNames()` zawiera skill ryzyk,
  zeby kontrakt runtime i pliki skillow byly walidowane razem,
- frontend Flow Explorera odblokowuje cel `Risk detection` w selectcie celu,
- test UI potwierdza, ze wybor `Risk detection` trafia do request payload jako
  `goal: RISK_DETECTION`,
- prompt preparation test potwierdza `RISK_DETECTION` w canonical prompt,
- dodano CRM-specific, zanonimizowany fixture parsera AI response dla
  `RISK_DETECTION` z aktywnymi sekcjami.

### 005. Cross-goal parser hardening i response validation

Status: [x]

Potrzeba:

Gdy wszystkie trzy cele maja juz template'y i fixture'y, mozna bezpiecznie
utwardzic parser i walidacje. Wczesniejsze hardening moglby blokowac prace
nad template'ami zbyt wcześnie.

Co wykonujemy:

- parser ma wymagac poprawnego JSON bez Markdown poza polami `markdown`,
- parser ma wymagac dokladnie aktywnych sekcji wynikajacych z `sectionModes`,
- parser ma weryfikowac `mode` sekcji wobec requestowych focus areas,
- parser ma wymagac `overview.markdown` i `section.markdown`,
- parser ma zachowac `visibilityLimits`, `openQuestions`, `confidence` i
  `sourceRefs`,
- parser ma odrzucac top-level pola spoza aktualnej allowlisty kontraktu,
- dodac testy negatywne i pozytywne dla wszystkich trzech celow.

Ryzyka:

- zbyt twardy parser moze czesciej failowac initial run; dlatego robimy go po
  wdrozeniu i przetestowaniu wszystkich goal-specific template'ow.

Wykonano:

- parser akceptuje teraz tylko czysty JSON object; tekst wokol JSON-a albo
  fenced block trafia do kontrolowanego fallbacku,
- parser wymaga znanego `goal` i dla initial job waliduje zgodnosc goal z
  requestem,
- parser wymaga `overview.markdown`,
- parser wymaga dokladnie aktywnych sekcji z id:
  `FUNCTIONAL_FLOW`, `VALIDATIONS`, `PERSISTENCE`, `INTEGRATIONS`,
- parser wymaga `section.markdown` w kazdej sekcji,
- parser waliduje `section.mode` wobec requestowych `focusAreas` w initial
  jobie,
- parser odrzuca top-level pola spoza aktualnej allowlisty:
  `goal`, `audience`, `overview`, `sections`, `globalVisibilityLimits`,
  `globalOpenQuestions`, `sourceReferences`, `confidence`,
- parser wymaga listowego shape'u dla pol:
  `sourceRefs`, `visibilityLimits`, `openQuestions`,
  `globalVisibilityLimits`, `globalOpenQuestions`, `sourceReferences`,
- naruszenie kontraktu nie wywala joba; zwracany jest fallback z
  `visibilityLimits` i pytaniem o ponowienie JSON w wymaganym formacie,
- dodano pozytywne i negatywne testy parsera na CRM-specific,
  zanonimizowanych fixture'ach,
- `FlowExplorerJobService` przekazuje do parsera `goal` i `focusAreas`, zeby
  walidacja mogla byc zgodna z requestem.

### 006. UI polish: composer, result view i copy

Status: [x]

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
  - Functional flow,
  - Validations,
  - Persistence,
  - Integrations,
- przy kazdej sekcji pokazywac badge `compact`/`deep`,
- renderowac markdown sekcji przez wspolny `MarkdownContentComponent`
  (`app-markdown-content`), tak jak w Incident Trackerze i wspolnych widokach
  AI activity; nie renderowac markdown jako zwyklego `<p>` ani przez lokalny
  parser Flow Explorera,
- source refs, visibility limits i open questions pokazac w czytelnym,
  powtarzalnym wzorcu,
- dodac akcje kopiowania calego rezultatu Flow Explorera do schowka,
  wykorzystujac wspolne clipboard utilsy FE (`copyElementToClipboard` dla
  sformatowanego wyniku albo `copyTextToClipboard` dla przygotowanego tekstu,
  zaleznie od finalnego DOM), z wykluczeniem przyciskow przez
  `data-clipboard-exclude`,
- zachowac wspolny workflow UI: przebieg AI, tool evidence, usage, import/export
  i follow-up chat,
- dodac testy FE.

Ryzyka:

- zbyt wiele nested cards moze pogorszyc UX; sekcje powinny byc proste,
  szerokie i zgodne z aktualnym stylem Flow Explorera.

Wykonano:

- result view Flow Explorera renderuje `overview.markdown` i markdown aktywnych
  sekcji przez wspolny `MarkdownContentComponent` (`app-markdown-content`),
- naglowek wyniku pokazuje tekstowy skrot overview bez surowej skladni
  Markdown,
- karta wyniku ma akcje `Copy result`, ktora uzywa wspolnego
  `copyElementToClipboard` i wyklucza przycisk przez `data-clipboard-exclude`,
- dodano feedback `Copied` oraz reset feedbacku przy zmianie runu,
- testy FE sprawdzaja uzycie wspolnego markdown componentu i kopiowanie wyniku
  bez action controls,
- wykonano `npm test -- --watch=false` oraz produkcyjny `npm run build`.

### 007. Import/export i diagnostic artifacts

Status: [x]

Potrzeba:

Zmiana kontraktu wyniku oznacza, ze export/import Flow Explorera oraz
diagnostic artifacts musza opisywac nowy result shape bez pol poprzedniego
kontraktu.

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
- user-facing export ma renderowac Overview + aktywne sekcje bez surowego kodu,
  jezeli ten tryb zostal juz rozdzielony,
- dodac testy import/export.

Ryzyka:

- stare pliki exportu Flow Explorera nie beda kompatybilne; to jest
  akceptowane i powinno byc opisane w finalnym podsumowaniu kroku.

Wykonano:

- format exportu Flow Explorera zostal podbity do v2 bez kompatybilnosci
  wstecznej z v1,
- export/import wymaga `COMPLETED` joba z ustrukturyzowanym `aiResponse`;
  failed, in-progress albo niepelne wyniki nie sa eksportowalne/importowalne,
- payload exportu zawiera `resultContract:
  flow-explorer-goal-result-v1`,
- payload exportu zawiera jawne `diagnostics` z:
  goal, focus areas, section modes, targetem endpointu, coverage contextu,
  clipping notes, usage flag, licznikami evidence/activity/tool feedback oraz
  lista artefaktow diagnostycznych,
- export zawiera user-facing `resultMarkdown` z Overview + aktywnymi sekcjami
  bez surowego kodu,
- import odrzuca pola `result` i `aiResponse` spoza aktualnej allowlisty
  kontraktu,
- import odrzuca niekompletny zestaw aktywnych sekcji,
- dodano CRM-specific, zanonimizowane testy FE dla exportu v2, diagnostics,
  odrzucenia nieobslugiwanych pol i odrzucenia niepelnych sekcji,
- wykonano `npm test -- --watch=false`.

### 008. Follow-up chat alignment

Status: [x]

Potrzeba:

Follow-up chat ma dzialac na nowym wyniku i nie powinien zachecac do
odtwarzania calej analizy od nowa. Ma sluzyc do doprecyzowania wyjatkow.

Co wykonujemy:

- prompt follow-up ma streszczac goal, section modes i wynik initial,
- follow-up ma rozumiec aktywne sekcje jako stale punkty odniesienia,
- jezeli uzytkownik pyta o obszar, ktory byl `compact`, AI moze dociagnac
  szczegol przez tools zgodnie z reasoning effort i tool policy,
- jezeli uzytkownik pyta o obszar `deep`, AI ma najpierw wykorzystac initial
  evidence i wynik, zanim zrobi dodatkowe calls,
- dodac testy follow-up promptu i job/chat API.

Ryzyka:

- follow-up moze zaczac powtarzac caly initial result; prompt powinien
  wymagac odpowiedzi tylko na pytanie uzytkownika.

Wykonano:

- follow-up prompt ma jawny answer contract: bezposrednia odpowiedz na pytanie,
  tylko potrzebne szczegoly, bez generowania calego initial resultu od nowa,
- prompt wskazuje stale punkty odniesienia:
  Overview, Functional flow, Validations, Persistence, Integrations,
- initial result w follow-up artifact jest renderowany sekcyjnie z goal,
  confidence, Overview, aktywnymi sekcjami, mode, source refs, visibility
  limits i open questions,
- sekcje `DEEP` maja byc najpierw obslugiwane z initial result, initial
  artifacts i zebranych evidence; tool call tylko gdy pytanie wychodzi poza
  znany material albo wskazuje sprzecznosc,
- sekcje `COMPACT` moga byc poglobione toolsami, ale wasko do pytania i zgodnie
  z `reasoningEffort`,
- jezeli tools zostaly uzyte, AI ma wskazac jednym zdaniem, co nowego wniosly
  wzgledem initial resultu,
- dodano CRM-specific, zanonimizowany test `FlowExplorerFollowUpPromptPreparationServiceTest`,
- rozszerzono `FlowExplorerJobServiceTest`, zeby potwierdzal przekazanie do
  follow-up prompt preparation initial requestu, context snapshotu, wyniku z
  aktywnymi sekcjami i aktualnego pytania uzytkownika,
- wykonano `mvn -q "-Dtest=FlowExplorerFollowUpPromptPreparationServiceTest,FlowExplorerJobServiceTest" test`,
- wykonano `mvn -q "-Dtest=FlowExplorer*Test,PackageDependencyGuardTest" test`.

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
- [x] 003. Test scenarios vertical slice.
- [x] 004. Risk detection vertical slice.
- [x] 005. Cross-goal parser hardening i response validation.
- [x] 006. UI polish: composer, result view i copy.
- [x] 007. Import/export i diagnostic artifacts.
- [x] 008. Follow-up chat alignment.
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
- Nie utrzymujemy mapperow kompatybilnosci wstecznej dla starych
  presetow/focus areas.

## Decision log

### 001. Przejscie z presetow dokumentacji na cele analizy

Decyzja: Flow Explorer zastapi obecne presety trzema celami:
`DEEP_DISCOVERY`, `TEST_SCENARIOS`, `RISK_DETECTION`.

Powod: uzytkownik nietechniczny mysli celem pracy, np. rozpoznanie flow,
przygotowanie testow albo wykrycie ryzyk, a nie typem dokumentacji.

Status: accepted.

### 002. Focus areas steruja trybem sekcji

Decyzja: focus areas zostaja ograniczone do czterech obszarow wyniku:
`FUNCTIONAL_FLOW`, `VALIDATIONS`, `PERSISTENCE`, `INTEGRATIONS`.
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
poniewaz sprawdza caly uklad Overview + aktywne sekcje + compact/deep bez
dodatkowej presji generowania scenariuszy albo ryzyk. Po jego ocenie mozna
lepiej napisac template'y dla kolejnych celow i uniknac powielenia bledow w
trzech skillach naraz.

Status: accepted.

### 006. Backendowy fundament kontraktu wdrozony bez mapperow kompatybilnosci

Decyzja: krok 001 zostal zamkniety jako backendowy fundament kontraktu.
Backend nie przyjmuje juz `documentationPreset`, starych focus areas ani
top-level pol spoza aktualnej allowlisty kontraktu.

Powod: kontrakt Flow Explorera ma byc dalej rozwijany cel po celu na jednym
stabilnym modelu `goal` + `Overview` + aktywne sekcje z trybem
`compact`/`deep`. Utrzymywanie starych DTO lub mapperow zwiekszaloby ryzyko,
ze prompt, UI albo import/export zaczna korzystac z poprzedniego modelu.

Status: implemented.

### 007. Deep Discovery jako pierwszy aktywny cel end-to-end

Decyzja: `DEEP_DISCOVERY` jest pierwszym aktywnym celem Flow Explorera
wdrozonym end-to-end. Na tym etapie `TEST_SCENARIOS` i `RISK_DETECTION`
pozostawaly w modelu i UI jako zaplanowane cele do osobnych vertical slice'ow.

Powod: jeden kompletny cel pozwala zweryfikowac template, runtime skille,
prompt, parser, UI, import/export i koszt odpowiedzi bez powielania bledow w
trzech celach jednoczesnie.

Status: implemented.

### 008. Test scenarios jako drugi aktywny cel end-to-end

Decyzja: `TEST_SCENARIOS` jest drugim aktywnym celem Flow Explorera. Ma wlasny
runtime skill, jest wybierany przez initial session config i jest dostepny w UI
bez feature flaga albo mapperow kompatybilnosci wstecznej.

Powod: po ustabilizowaniu `Deep Discovery` mozemy wdrazac kolejne cele pionowo.
`Test scenarios` wnosi odrebna wartosc dla testerow i analitykow: initial
result ma dostarczyc material do pokrycia happy path, negative path, edge cases,
danych testowych i zaleznosci integracyjnych bez standardowego follow-upu.

Status: implemented.

### 009. Risk detection jako trzeci aktywny cel end-to-end

Decyzja: `RISK_DETECTION` jest trzecim aktywnym celem Flow Explorera. Ma wlasny
runtime skill, jest wybierany przez initial session config i jest dostepny w UI
bez feature flaga albo mapperow kompatybilnosci wstecznej.

Powod: po wdrozeniu rozpoznania flow i scenariuszy testowych mozemy domknac
zestaw celow o ocene ryzyk. Wynik ma nie tylko wskazywac ryzyka, ale tez
rozrozniac fakt z evidence, inferencje, luke widocznosci i pytanie otwarte,
zeby analityk albo tester nie traktowal hipotez jak potwierdzonego zachowania.

Status: implemented.

### 010. Parser waliduje shape kontraktu, nie jakosc merytoryczna

Decyzja: parser Flow Explorera waliduje twardy shape odpowiedzi AI: czysty JSON,
znany `goal`, dokladnie aktywne sekcje z `sectionModes`, wymagane pola
`markdown`, zgodnosc `mode` z requestem oraz brak top-level pol spoza
allowlisty kontraktu. Parser dodatkowo pilnuje stalej struktury markdownu
`FUNCTIONAL_FLOW`.

Powod: backend, UI, import/export i follow-up chat potrzebuja stabilnego
kontraktu transportowego. Ocena jakosci rezultatu powinna zostac w skillach,
smoke testach i korektach promptow, bo kod nie ma wystarczajacego kontekstu,
zeby wiarygodnie oceniac wartosc analizy.

Status: implemented.

### 011. Flow Explorer uzywa wspolnego markdown renderera i clipboard utils

Decyzja: wynik Flow Explorera renderuje pola `markdown` przez wspolny
`MarkdownContentComponent`, a kopiowanie calego rezultatu uzywa wspolnego
`copyElementToClipboard` z wykluczaniem kontrolek przez `data-clipboard-exclude`.

Powod: przebieg i wynik feature'ow maja korzystac ze wspolnych mechanizmow UI
tam, gdzie uzytkownik wykonuje znane czynnosci: czyta wynik AI, kopiuje go i
wraca do niego w follow-upie. Flow Explorer nie powinien miec lokalnego parsera
markdown ani osobnego mechanizmu clipboard.

Status: implemented.

### 012. Export Flow Explorera jest v2 diagnostic envelope

Decyzja: Flow Explorer export uzywa formatu v2 i payload type
`flow-explorer-analysis`. Plik zawiera pelny `job` do odtworzenia ekranu oraz
jawne `diagnostics` z indeksem kontraktu, targetu, requestu, trybow sekcji,
coverage, clipping notes, artefaktow, usage, tool evidence i AI activity.
Import wymaga v2 oraz `flow-explorer-goal-result-v1`.

Powod: po zmianie na goal-based result nie chcemy cichego wspierania starych
plikow ani niepelnych danych. Jednoczesnie plik ma byc przydatny diagnostycznie:
odtwarza UI, ale daje tez szybki indeks tego, co zostalo rzeczywiscie
zapisane i czy wynik ma cztery wymagane sekcje.

Status: implemented.

### 013. Follow-up chat jest precyzyjnym doprecyzowaniem, nie drugim raportem

Decyzja: Flow Explorer follow-up prompt uzywa goal-based initial result jako
stalego punktu odniesienia. Odpowiedz ma dotyczyc aktualnego pytania, nie
odtwarzac calej analizy. Sekcje `DEEP` sa najpierw obslugiwane z initial
resultu i evidence, a sekcje `COMPACT` moga byc wasko poglobione toolsami
zgodnie z `reasoningEffort`.

Powod: initial result ma pokrywac wiekszosc potrzeb uzytkownika. Follow-up ma
byc narzedziem do wyjatkow, doprecyzowan i nowych pytan, a nie sposobem na
nadrabianie zbyt ogolnej odpowiedzi poczatkowej.

Status: implemented.
