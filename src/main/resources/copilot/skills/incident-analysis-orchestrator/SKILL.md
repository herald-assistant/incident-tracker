---
name: incident-analysis-orchestrator
description: Glowny starter analizy incydentu - najpierw rozpoznaje flow use case'u do poziomu wymaganego przez kontrakt wyniku, potem klasyfikuje blad, uruchamia specjalistyczne playbooki, syntetyzuje akcje i zwraca kontrakt analizy.
---

# Skill Orkiestratora Analizy Incydentu

Uzywaj tego skilla jako pierwszego runtime skilla dla kazdej poczatkowej
analizy incydentu.

Ten skill steruje kolejnoscia diagnozy. Nie klasyfikuj root cause na podstawie
samej nazwy exceptiona. Najpierw zrozum use case i flow, ktore bylo wykonywane,
potem zlokalizuj przerwanie, potem sklasyfikuj blad, a dopiero potem uzyj
specjalistycznych playbookow diagnostycznych.

## Cel

Analiza ma wyjasnic na podstawie evidence:

- jaki use case, request, event, job, proces albo operacja systemowa byla
  wykonywana,
- jak dziala istotny fragment flow,
- gdzie flow zostalo przerwane,
- jaka klasa bledu najlepiej tlumaczy to przerwanie,
- jakie evidence potwierdza, oslabia albo obala klasyfikacje,
- jaka akcja powinna zostac wykonana,
- co pozostaje poza aktualna widocznoscia.

Finalny wynik nadal musi trzymac aktualny kontrakt odpowiedzi incydentu, ale
zrodlem prawdy initial analysis jest raport zapisany przez report tools. Ten
orkiestrator okresla, jak dojsc do wyniku; skille kontraktu okreslaja ksztalt
sekcji raportu mapowanych na pola publiczne:

- `incident-functional-analysis` dla `functionalAnalysis`,
- `incident-technical-handoff` dla `technicalAnalysis`.

## Ekspercki Model Pracy

Traktuj analize jak procedure dobrego eksperta, nie jak zgadywanie etykiety.
Twoim zadaniem jest odtworzyc normalny flow, porownac go z flow zaobserwowanym
w evidence, znalezc punkt rozjazdu i dopiero wtedy rozstrzygac hipotezy.

Utrzymuj roboczy ledger analizy:

```text
expectedFlow: co normalnie powinno sie wydarzyc
observedFlow: co evidence pokazuje w tym incydencie
divergencePoint: pierwszy konkretny punkt rozjazdu
hypotheses:
  - class: data_missing | code_query_or_repository_logic | ...
    mechanism: jaki warunek/input/dane/call powoduje przerwanie
    owningLayer: DB | code | integration | runtime | config | operational-context | outside
    supportingEvidence: co hipoteze wspiera
    contradictingEvidence: co hipoteze obala albo oslabia
    distinguishingTest: najmniejszy test, ktory zmieni decyzje
    nextSkillOrTool: gdzie wykonac test
    confidence: confirmed | strong_hypothesis | weak_hypothesis | rejected
causalChain: trigger -> operation -> condition -> boundary -> symptom -> impact
actionability: kto / co / gdzie / jak zweryfikowac
```

### Zasady Trafnosci Eksperta

- Mechanism-first: brak mechanizmu oznacza brak root cause. Nie wystarczy
  exception name, stack frame albo nazwa klasy.
- Hypothesis tournament: utrzymuj 2-4 konkurujace hipotezy, dopoki test
  rozrozniajacy ich nie potwierdzi, nie oslabi albo nie obali.
- Negative evidence: dla kazdej waznej hipotezy nazwij, jaki dowod by ja
  obalil, i sprawdz, czy taki dowod juz istnieje.
- Owning-layer proof: klasa moze byc `confirmed` tylko przez dowod z warstwy,
  ktora jest wlascicielem tego typu przyczyny.
- Information gain: przed kazdym tool call powiedz, jaka decyzja zmieni sie po
  wyniku. Jezeli odpowiedz brzmi tylko "dowiem sie wiecej", nie wywoluj toola.
- Fixability test: finalna analiza jest gotowa dopiero, gdy odbiorca wie, kto
  ma zadzialac, jaki obiekt/plik/tabela/endpoint sprawdzic i jak potwierdzic
  fix albo routing.

### Owning-Layer Proof

| Klasa | Warstwa potwierdzajaca | Bez tego najwyzej |
|---|---|---|
| `data_missing`, `data_predicate_mismatch`, `data_orphan_or_stale_reference`, `data_duplicate_or_non_unique` | DB evidence albo jawny brak DB visibility | `strong_hypothesis` |
| `code_mapping_or_type_conversion`, `code_query_or_repository_logic`, `code_validation_or_business_rule` | code evidence z konkretnego pliku/metody/predykatu/reguly | `strong_hypothesis` |
| `integration_downstream_failure` | downstream/log/HTTP boundary evidence | `strong_hypothesis` |
| `async_or_process_state` | event/outbox/process state evidence albo code evidence handlera | `strong_hypothesis` |
| `runtime_or_platform` | runtime/platform evidence z czasu incydentu | `strong_hypothesis` |
| `configuration_or_environment` | config/deployment/environment evidence | `strong_hypothesis` |
| `outside_visibility_or_handoff` | lokalny boundary + brak visibility + handoff route | `strong_hypothesis` |
| `inconclusive` | jawne braki evidence po minimalnym researchu | `confirmed` |

Operational context potwierdza routing, ownership, glossary, process i scope.
Nie potwierdza samodzielnie root cause.

### Macierz Diagnostyki Roznicowej

| Obserwowany punkt rozjazdu | Konkurujace klasy | Test rozrozniajacy | Regula przeklasyfikowania |
|---|---|---|---|
| repository empty result | `data_missing`, `data_predicate_mismatch`, `code_query_or_repository_logic` | code predicate + DB key-only vs full-predicate | key-only = 0 -> `data_missing`; key-only > 0 i full = 0 -> `data_predicate_mismatch`; predicate w kodzie bledny -> `code_query_or_repository_logic` |
| entity/reference lookup fails | `data_missing`, `data_orphan_or_stale_reference`, `outside_visibility_or_handoff` | parent row + referenced row + owner/source check | parent exists i reference missing/stale -> `data_orphan_or_stale_reference`; source poza systemem -> `outside_visibility_or_handoff` |
| non-unique result | `data_duplicate_or_non_unique`, `code_query_or_repository_logic` | duplicate count + expected uniqueness rule | duplicates w danych -> `data_duplicate_or_non_unique`; query zbyt szerokie -> `code_query_or_repository_logic` |
| mapper/type/null failure | `code_mapping_or_type_conversion`, `data_predicate_mismatch`, `integration_downstream_failure` | mapper expected vs actual value/source | mapper nie obsluguje wartosci -> `code_mapping_or_type_conversion`; downstream contract/value invalid -> `integration_downstream_failure` |
| business rejection | `code_validation_or_business_rule`, `data_predicate_mismatch`, `outside_visibility_or_handoff` | rule code + object state + owner | rule zgodna, stan danych blokuje -> data class; rule bledna -> `code_validation_or_business_rule`; decision poza systemem -> `outside_visibility_or_handoff` |
| downstream HTTP failure | `integration_downstream_failure`, `configuration_or_environment`, `runtime_or_platform` | boundary status/path + config/runtime evidence | 4xx/5xx downstream z boundary evidence -> `integration_downstream_failure`; env flag/URL/credential drift -> `configuration_or_environment`; local runtime abnormal -> `runtime_or_platform` |
| async/outbox stuck | `async_or_process_state`, `integration_downstream_failure`, `data_missing` | event row state + last error + retry timeline | stuck/exhausted row -> `async_or_process_state`; last error downstream -> integration; missing required row -> data class |
| pod/restart/metric abnormal | `runtime_or_platform`, `configuration_or_environment`, `inconclusive` | runtime status in incident window + deploy/config evidence | abnormal runtime signal -> `runtime_or_platform`; config drift -> `configuration_or_environment`; unavailable/no match -> limitation |

## Algorytm Orkiestracji

### 1. Przeczytaj Kontekst Sesji

Zacznij od `00-incident-manifest.json` i dolaczonych artefaktow.

Ustal:

- `correlationId`,
- `environment`,
- `gitLabBranch`,
- `gitLabGroup`,
- wlaczone i wylaczone capability groups,
- dolaczone sekcje evidence,
- luki diagnostyczne z `evidenceCoverage.gaps`,
- runtime skille z `runtimeSkills.diagnosticSkillNames` i
  `runtimeSkills.resultSkillNames`.

Traktuj artefakty jako podstawowe zrodlo prawdy.

Traktuj te wartosci jako stale dla aktualnej sesji:

- `correlationId`,
- `environment`,
- `gitLabBranch`,
- `gitLabGroup`.

Nie wymyslaj brakujacego systemu, brancha, ownera, repozytorium, procesu,
bounded contextu, tabeli, kolejki, endpointu ani downstream systemu.

Sprawdz w manifest `toolPolicy.enabledCapabilityGroups` i
`toolPolicy.disabledCapabilityGroups`, zanim zalozysz, ze GitLab,
Elasticsearch, Operational Context albo DB tools sa dostepne.

Jezeli capability group jest wylaczone, bo rownowazne artefakty sa juz
dolaczone, uzyj tych artefaktow bez narzekania na brak toola.

### 2. Zbuduj Fingerprint Incydentu

Przed diagnoza wyciagnij kompaktowy fingerprint:

- widoczny objaw,
- failing service, container, deployment albo runtime component,
- timestamp albo okno incydentu,
- typ triggera: request, event, job, scheduler, listener albo integration call,
- przetwarzany obiekt biznesowy albo techniczny,
- widoczny endpoint, message, operation, repository, class, method albo
  downstream call,
- pierwszy widoczny punkt awarii,
- znany skutek funkcjonalny,
- brakujaca widocznosc.

Fingerprint nie jest root cause. To mapa startowa do researchu flow.

### 3. Zbadaj Flow Dotknietego Use Case'u

Przed klasyfikacja bledu odtworz flow use case'u na tyle gleboko, aby dalo sie
uczciwie zbudowac oba kontrakty wyniku.

Research musi wystarczyc, zeby:

- `functionalAnalysis` wyjasnilo kontekst systemu/procesu, affected bounded
  context, operacje biznesowa albo systemowa, znaczenie integracji lub
  handoffu, skutek funkcjonalny i limity widocznosci,
- `technicalAnalysis` wyjasnilo techniczny punkt wejscia, flow wykonania,
  punkt awarii, bezposrednich collaboratorow, evidence, najlepiej wsparta
  przyczyne albo hipoteze, oczekiwana akcje, testy i weryfikacje.

Nie mapuj calego systemu ani niezwiazanych flow. Jednoczesnie nie zatrzymuj sie
na lokalnym exceptionie, jezeli wynik funkcjonalny albo techniczny bylby zbyt
plytki, zeby dalo sie go przekazac albo na nim dzialac.

Zbuduj najmniejsze flow wystarczajace dla wyniku:

```text
trigger / request / event
  -> entry point
    -> business or system operation
      -> validation / decision / lookup / mapping
        -> DB / integration / async / runtime boundary
          -> observed failure point
```

Nazwij oba flow:

```text
expectedFlow: happy path albo expected system path
observedFlow: path potwierdzony przez evidence
divergencePoint: pierwszy punkt, gdzie observedFlow odchodzi od expectedFlow
```

Uzywaj:

- najpierw artefaktow incydentu,
- operational context do kanonicznego systemu, procesu, bounded contextu,
  ownershipu, scope repozytoriow, glossary i handoff hints,
- GitLab tools do punktu wejscia, serwisu, repozytorium, mappera, walidatora,
  klienta integracji, listenera, schedulera, outboxa albo bezposrednich
  collaboratorow,
- DB tools dopiero po tym, jak kod albo evidence ugruntuje istotne dane,
  tabele albo predykat, gdy to mozliwe,
- Elasticsearch albo runtime evidence do czasu logow, porownania HTTP,
  czestotliwosci i interpretacji runtime signals.

Gdy artefakt `dynatrace/runtime-signals` zawiera strukturalne status lines,
interpretuj je literalnie:

- `collection status: COLLECTED` z component status
  `MATCHED, NO_RELEVANT_SIGNALS` oznacza brak Dynatrace-confirmed abnormal
  runtime signal dla tego komponentu w oknie incydentu.
- `collection status: UNAVAILABLE`, `DISABLED` albo `SKIPPED` oznacza brak
  widocznosci runtime, nie zdrowy runtime.
- `collection status: COLLECTED` z `correlation status: NO_MATCH` oznacza, ze
  Dynatrace jest niekonkluzywny dla tego incydentu.
- Preferuj component-level summary lines z Dynatrace zamiast raw metric detail,
  chyba ze raw detail realnie wzmacnia evidence.

Zatrzymaj research flow dopiero wtedy, gdy dotkniety use case, punkt awarii,
ownership albo handoff route oraz konkretna nastepna akcja techniczna sa
wystarczajaco jasne dla wymaganego kontraktu wyniku, albo gdy brakujaca
informacja jest poza aktualna widocznoscia.

### 4. Zlokalizuj Awaria Na Flow

Oznacz, gdzie flow zostalo przerwane:

- input albo request validation,
- business rule albo functional decision,
- repository lookup albo entity loading,
- data predicate albo filter,
- mapping, conversion albo null handling,
- write albo persistence,
- async, outbox albo event processing,
- downstream albo integration call,
- runtime, deployment albo configuration,
- ownership boundary albo outside current visibility.

Jezeli punkt awarii nie jest ustalony, napisz to wprost i kontynuuj z najlepiej
wsparta hipoteza.

### 5. Sklasyfikuj Typ Bledu

Klasyfikuj incydent dopiero po researchu flow i nazwaniu `divergencePoint`.
Nie wybieraj od razu jednej ulubionej klasy. Najpierw zbuduj turniej hipotez:

- wybierz 2-4 klasy, ktore realnie tlumacza ten sam punkt rozjazdu,
- dla kazdej opisz mechanizm, owning layer i evidence,
- dla kazdej nazwij evidence, ktore ja obala albo oslabia,
- wybierz test rozrozniajacy o najwiekszym information gain.

Uzyj jednej albo kilku klas:

- `data_missing`,
- `data_predicate_mismatch`,
- `data_orphan_or_stale_reference`,
- `data_duplicate_or_non_unique`,
- `code_mapping_or_type_conversion`,
- `code_query_or_repository_logic`,
- `code_validation_or_business_rule`,
- `integration_downstream_failure`,
- `async_or_process_state`,
- `runtime_or_platform`,
- `configuration_or_environment`,
- `outside_visibility_or_handoff`,
- `inconclusive`.

Dla kazdej aktywnej klasy utrzymuj:

- evidence wspierajace,
- evidence sprzeczne,
- brakujace evidence,
- confidence: `confirmed`, `strong_hypothesis`, `weak_hypothesis` albo
  `rejected`.

Nie oznaczaj klasy jako confirmed, jezeli evidence nie wspiera bezposrednio
mechanizmu albo nie pochodzi z owning layer dla tej klasy.

Zawsze oddzielaj:

- bezposrednio potwierdzone evidence,
- najlepiej wsparta hipoteze,
- niezweryfikowane zalozenia,
- limity widocznosci,
- dotkniete flow,
- konkretna nastepna akcje.

Uzywaj mocnego jezyka root cause tylko wtedy, gdy kilka niezaleznych sygnalow
wskazuje na ten sam mechanizm, np. logs, runtime signal, code path, DB/data
evidence albo grounded operational context. Gdy evidence jest slabsze, opisz
diagnoze jako wsparta hipoteze.

### 6. Wybierz Specjalistyczny Playbook

Uzyj klasyfikacji, zeby wybrac nastepny skill albo strategie tooli.

- Uzyj `incident-data-diagnostics` dla missing data, predicate mismatch,
  orphan/stale reference, duplicates, stuck outbox albo process data.
- Uzyj `incident-analysis-gitlab-tools` dla code flow, repository predicates,
  mapping, validation, integration clients, event/listener/job flow i
  technical grounding.
- Uzyj `incident-operational-context-tools` dla system/process/bounded-context
  grounding, repository scope, ownership i handoff route.
- Uzyj Elasticsearch/log tools dla request timing, stack traces, frequency,
  HTTP path/status comparison i correlation evidence.
- Uzyj runtime/Dynatrace evidence dla component health, deployment, metrics i
  problem signals, gdy sa dostepne.
- Uzyj `incident-functional-analysis` do sformatowania wyniku dla analityka.
- Uzyj `incident-technical-handoff` do sformatowania akcji technicznej albo
  handoffu.

Preferuj nastepny tool call, ktory najlepiej rozroznia konkurujace hipotezy.
Nie wywoluj tooli tylko dlatego, ze sa dostepne.

### 7. Wykonaj Testy Rozrozniajace

Dla kazdej waznej hipotezy zapytaj:

```text
Jaki najmniejszy nastepny dowod potwierdzi, oslabi albo obali te hipoteze?
Co zrobie inaczej, jezeli wynik bedzie pozytywny, negatywny albo niekonkluzywny?
```

Przyklady:

- Dla repository/entity errors: ugruntuj entity/repository w kodzie, potem
  porownaj DB key-only count z full predicate count.
- Dla downstream HTTP errors: porownaj failing path/status z ostatnimi udanymi
  i nieudanymi wywolaniami tej samej rodziny endpointow.
- Dla mapper/type errors: przeczytaj mapper/repository method i potwierdz
  expected vs actual type albo null behavior.
- Dla async/outbox symptoms: sprawdz event/process row state, retry count,
  error code i timestamps.
- Dla runtime/config symptoms: sprawdz, czy runtime evidence zostalo zebrane i
  czy zawiera konkretne abnormal signals.

Gdy lokalne logi albo kod pokazuja tylko opaque HTTP failure z innego systemu,
a Elasticsearch HTTP diagnostic tools sa wlaczone:

- uzyj ugruntowanej sciezki albo stabilnego prefixu sciezki z logow, user input
  albo poprzedniego evidence,
- najpierw zrob summary ostatnich calli dla tej samej rodziny endpointow,
- pobierz konkretne comparison calls tylko wtedy, gdy status/path samples moga
  zmienic diagnoze,
- porownaj status, method, caller service, timestamp cluster, message hints,
  null/empty values, constraint-like wording i request/response shape clues,
- traktuj wynik jako supporting evidence, nie proof, chyba ze logi zawieraja
  jawnie accepted/rejected value albo downstream reason.

Nie wymyslaj endpoint paths.

Zatrzymaj eksploracje, gdy kolejne sprawdzenia tylko powtorza istniejace
evidence albo wymagaja widocznosci poza aktualna sesja.

Po kazdym tescie zaktualizuj turniej hipotez:

- `confirmed`: owning-layer proof potwierdza mechanizm,
- `strong_hypothesis`: kilka sygnalow jest spojnych, ale brakuje owning-layer
  proof,
- `weak_hypothesis`: jeden sygnal bez rozroznienia,
- `rejected`: test rozrozniajacy przeczy hipotezie.

Jezeli test obala aktualna klase, przeklasyfikuj zamiast bronic pierwszej
hipotezy.

### 8. Zsyntetyzuj Przyczyne I Rozwiazanie

Przed finalnym wynikiem zbuduj causal chain:

```text
trigger
  -> use-case operation
    -> condition / input / data / config
      -> failing component or boundary
        -> observed symptom
          -> functional impact
```

Potem zmapuj diagnoze na akcje:

- data issue: konkretny row/key/table/predicate/status/tenant/reference do
  weryfikacji albo korekty,
- code issue: konkretny file/class/method/logic do zmiany i testy do dodania,
- integration issue: downstream owner, request/response/status/timestamp
  evidence i pytanie do odpowiedzi,
- runtime/platform issue: namespace/pod/service/image/config/metric/log
  verification,
- outside visibility: dokladne brakujace evidence i kto moze je dostarczyc.

Nie przedstawiaj poprawki jako potwierdzonej, jezeli root cause jest tylko
hipoteza.

Wykonaj fixability test. Wynik nie jest gotowy, jezeli nie da sie odpowiedziec:

- kto ma zadzialac,
- jaki object, key, state, endpoint, table, file albo method jest targetem,
- co dokladnie trzeba zmienic, sprawdzic, odtworzyc albo przekazac,
- jaki test albo obserwacja potwierdzi fix albo poprawny handoff.

Nie wymuszaj code-level root cause, gdy kod pokazuje tylko miejsce ujawnienia
bledu, a evidence wskazuje na downstream system, data state, configuration,
messaging layer, infrastructure albo komponent innego zespolu.

Proponowana akcja musi byc konkretna i wykonalna. Unikaj ogolnikow:

- "sprawdzic logi",
- "zweryfikowac aplikacje",
- "przeanalizowac baze",
- "skontaktowac sie z zespolem".

Preferuj:

- kto ma zadzialac,
- jaki object, key, state, endpoint, table, file albo method trzeba sprawdzic,
- ktory system, data area albo integration trzeba zweryfikowac,
- jaka zmiana, korekta danych albo handoff jest prawdopodobnie potrzebny,
- jak potwierdzic fix albo routing.

Gdy uzywasz operational context do handoffu albo ownershipu:

- traktuj `codeSearchScopes`, `codeSearchProjects` i kilka repository projects
  jako jeden implementation search scope dla dopasowanego semantic target,
- zaczynaj od repozytoriow `primary-implementation` albo priority `1`, a potem
  przechodz do supporting libraries, generated clients, integration adapters,
  legacy modules albo collaborators tylko gdy jest to potrzebne,
- nie nazywaj konkretnego process, bounded context albo team, jezeli artefakty
  incydentu albo tool results nie wspieraja dopasowania katalogowego,
- gdy ownership jest niejednoznaczny, wpisz `nieustalone`,
- podaj, dlaczego handoff jest potrzebny, jakie evidence przekazac i co
  odbiorca ma sprawdzic jako pierwsze.

### 9. Zbuduj Wymagany Kontrakt Wyniku

Zapisz finalny wynik w aktualnym kontrakcie incident analysis przez report
tools.

Uzyj:

- `report_update_header`: `header` to `detectedProblem`; `subHeader` moze
  zawierac krotki material diagnostyczny, ale nie zastepuje pol publicznych,
- `report_upsert_section` z `id=FUNCTIONAL_ANALYSIS`: tresc sekcji
  `functionalAnalysis` zgodna z Functional Analysis v1,
- `report_upsert_section` z `id=TECHNICAL_HANDOFF`: tresc sekcji
  `technicalAnalysis` zgodna z Technical Handoff v1,
- `report_update_meta`: globalne `confidence`, `visibilityLimits`, `gaps`,
  `warnings` oraz `references`.

W `report_update_meta.references` zapisz affected fields jako referencje:

- `type=process`, `label=<affectedProcess>`,
- `type=boundedContext`, `label=<affectedBoundedContext>`,
- `type=team`, `label=<affectedTeam>`.

Nie przekazuj `reportId` do report tools. Backend wybiera aktywny raport z
hidden `ToolContext`.

Po zapisie uzyj `report_get_current`, zeby sprawdzic, ze istnieja sekcje
`FUNCTIONAL_ANALYSIS` i `TECHNICAL_HANDOFF`. Finalna odpowiedz tekstowa moze
byc krotkim statusem. Jezeli report tools sa niedostepne albo zapis sie nie
powiedzie, zwroc fallback JSON w aktualnym kontrakcie incident analysis.

Glowne pola Markdown mapowane z raportu musza przestrzegac:

- `functionalAnalysis`: Functional Analysis v1,
- `technicalAnalysis`: Technical Handoff v1.

Zachowaj tez:

- `detectedProblem`,
- `affectedProcess`,
- `affectedBoundedContext`,
- `affectedTeam`,
- `confidence`,
- `visibilityLimits`,
- `prompt`,
- `usage`.

Nie przywracaj wycofanych pol: `summary`, `recommendedAction`, `rationale`,
`affectedFunction` ani `evidenceReferences`.

## Warunki Zatrzymania

Zatrzymaj diagnostyke, gdy:

- flow use case'u jest wystarczajaco jasne dla obu kontraktow wyniku,
- punkt awarii jest zlokalizowany,
- jedna klasa bledu jest confirmed albo jest najsilniejsza uczciwa hipoteza,
- proponowana akcja jest konkretna,
- pozostale pytania wymagaja innego zespolu, systemu albo srodowiska,
- dalsze tool calls bylyby spekulacyjne.

Jezeli confidence pozostaje ograniczone, napisz to wprost.

## Antywzorce

Nie:

- klasyfikuj root cause z samej nazwy exceptiona,
- pomijaj use-case flow research,
- zatrzymuj sie na lokalnym exceptionie, gdy kontrakty wyniku bylyby plytkie,
- przegladaj tools bez pytania rozrozniajacego,
- uzywaj operational context jako dowodu awarii,
- diagnozuj data issue bez DB evidence,
- wymuszaj code root cause, gdy evidence wskazuje downstream albo outside
  visibility,
- zwracaj finalnego wyniku przed zmapowaniem flow, punktu awarii, klasy,
  evidence i akcji,
- koncz initial analysis bez zapisania raportu przez report tools, jezeli te
  tools sa dostepne.
