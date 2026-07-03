---
name: incident-operational-grounding
description: "Playbook analizy incydentu dla neutralnych tools katalogu operational context: grounding systemow, procesow, bounded contextow, ownershipu, code scope i handoffu."
---

# Incident Operational Grounding

Uzywaj tego skilla, gdy operational context tools sa dostepne w sesji analizy
incydentu.

Operational context to katalog systemow, repozytoriow, code-search scopes,
procesow, integracji, bounded contexts, teamow, glossary terms i handoff rules.

Katalog jest przydatny do ugruntowania nazw, relacji, ownershipu, code scope,
DB targeting hints i handoff guidance. Nie jest samodzielnym dowodem root
cause.

## Cel

Zbuduj `OperationalGroundingSummary`: katalogowe nazwy systemu, procesu,
bounded contextu, ownershipu, code-search scope, DB hints i handoff route
potrzebne do incident analysis.

## Rola Wobec Orkiestratora

Ten skill jest playbookiem grounding/routing wybieranym przez
`incident-analysis-orchestrator`, gdy analiza potrzebuje lepszego nazwania
systemu, procesu, bounded contextu, integracji, code-search scope, DB targetu
albo handoffu.

Nie jest to playbook root-cause diagnosis. Operational context nie potwierdza,
ze awaria wystapila. Potwierdza tylko, jak nazwac i ukierunkowac obszar, ktory
jest wsparty incident evidence.

## Klasy Bledu Obslugiwane Przez Ten Skill

Uzywaj tego skilla przede wszystkim dla:

- `outside_visibility_or_handoff`,
- `configuration_or_environment`, gdy potrzebny jest system/deployment/config
  context,
- kazdej klasy, gdy trzeba nazwac affected system, process, bounded context,
  team, integration albo repository scope.

Uzywaj go wspierajaco dla:

- `code_query_or_repository_logic`, `code_mapping_or_type_conversion` i
  `code_validation_or_business_rule`, aby dobrac code-search scope,
- `data_missing`, `data_predicate_mismatch` i
  `data_orphan_or_stale_reference`, aby dobrac application/repository/DB hints,
- `integration_downstream_failure`, aby nazwac downstream/upstream i
  receiving owner,
- `async_or_process_state`, aby powiazac event/process z systemem i handoffem.

## Wejscie Oczekiwane Od Orkiestratora

Przyjmij:

- fingerprint incydentu,
- sygnaly z logow, deploymentu, runtime albo kodu,
- aktualny szkic flow use case'u,
- failure point albo integration boundary,
- brakujace pola `affectedProcess`, `affectedBoundedContext`, `affectedTeam`,
- potrzebe GitLab scope, DB target albo handoff route.

Nie szukaj w katalogu bez sygnalu. Uzywaj concrete query z evidence albo
najnowszej wiadomosci uzytkownika.

## Hipotezy, Ktore Skill Ma Potwierdzic Albo Obalic

Ten skill ma potwierdzic albo oslabic tylko hipotezy routingowe i katalogowe:

- jaki system, proces, bounded context albo integracja najlepiej pasuje do
  incident evidence,
- ktory team, owner albo handoff route jest najbardziej uzasadniony,
- jaki repository/code-search scope albo DB target warto przekazac dalej,
- czy problem wyglada na poza zasiegiem analizowanego systemu,
- czy katalog jest zbyt niepelny albo niejednoznaczny, aby podniesc confidence.

Nie potwierdzaj root cause na podstawie katalogu. Root cause wymaga evidence z
logow, kodu, runtime, DB albo downstream.

## Testy Rozrozniajace

Dobierz najmniejszy katalogowy check, ktory rozroznia aktywne potrzeby:

- system/process match rozroznia lokalny problem od cross-system handoffu,
- repository/code-search scope rozroznia, gdzie GitLab skill ma szukac kodu,
- integration relation rozroznia upstream, downstream i local boundary,
- ownership/handoff rule rozroznia odbiorce i pierwsza akcje,
- glossary/bounded context rozroznia znaczenie biznesowe dla
  `functionalAnalysis`.

## Petla Katalogowa

Po kazdym wyniku `opctx_*` sprawdz, czy katalog rozstrzygnal waska potrzebe
orkiestratora: nazwe systemu, procesu, bounded contextu, ownera, code-search
scope, DB hint albo handoff route.

- Jezeli wynik potwierdza routing albo scope i jest wsparty evidence
  incydentu, zwroc `OperationalGroundingSummary`.
- Jezeli wynik jest niepelny, ale wskazuje konkretna encje, id, relacje,
  source ref albo handoff rule, wykonaj jedno waskie poglebienie
  `opctx_get_entity`.
- Jezeli potrzebne byloby szerokie browse katalogu, zwroc limitation i
  `Nie ustalono` dla niepewnych pol.
- Jezeli katalog wskazuje kilka konkurujacych encji, nie wybieraj ownera
  na sile; zwroc ambiguity jako visibility limit.

Nie oznaczaj ownershipu, procesu ani bounded contextu jako potwierdzonego na
podstawie slabej zgodnosci nazwy. Operational context jest groundingiem i
routingiem, nie dowodem root cause.

## Co Ten Skill Ma Dostarczyc

Po uzyciu operational context zwroc do orkiestratora:

- kanoniczna nazwe systemu/aplikacji, jesli dopasowanie jest wsparte evidence,
- candidate process i bounded context z poziomem pewnosci i limitation,
- integration/downstream/upstream context, jesli dotyczy,
- repository/code-search scope dla GitLab tools,
- application/deployment/repository hints dla DB targeting,
- handoff route, expected first action i evidence package dla odbiorcy,
- glossary/local language, jesli pomaga analitykowi zrozumiec flow.

## Wklad Do Wyniku

- `functionalAnalysis`: system, process, bounded context, integration,
  ownership, local language, handoff reason i functional routing.
- `technicalAnalysis`: repository scope, target system/integration, receiving
  owner, handoff evidence i first technical verification.
- `visibilityLimits`: weak catalog match, missing owner, incomplete process
  coverage, ambiguous repository scope, open questions.
- `confidence`: katalog moze podniesc confidence w routingu, ale nie w root
  cause bez supporting logs/code/runtime/DB evidence.

## Kontrakt Wyniku

Zwroc `OperationalGroundingSummary`:

```text
system: <system/aplikacja albo Nie ustalono>
process: <proces albo Nie ustalono>
boundedContext: <bounded context albo Nie ustalono>
affectedTeam: <team/owner albo Nie ustalono>
handoffRoute:
  receiver: <team/system/rola albo Nie ustalono>
  reason: <dlaczego handoff jest uzasadniony>
codeSearchScope:
  - <project/scope/repository hint>
dbTargetHints:
  - <application/schema/table hint, jezeli ugruntowany>
sourceRefs:
  - <artifact/tool:opctx_*>
visibilityLimits:
  - <konkretny brak katalogu albo niejednoznacznosc>
```

## Kiedy Wrocic Do Orkiestratora

Wroc, gdy:

- dopasowanie system/process/context/team jest wystarczajace dla wyniku,
- katalog jest niejednoznaczny i dalsze browse nie rozroznia hipotez,
- brakuje encji katalogowej albo source coverage,
- dalszy krok nalezy do GitLab, DB, Elasticsearch, runtime albo innego zespolu.

Jesli katalog nie potwierdza ownershipu albo procesu, wpisz `nieustalone` i
podaj limitation zamiast zgadywac.

## Walidacja

Sprawdz:

- system, process, bounded context i team sa nazwane tylko przy wsparciu
  incident evidence albo tool resultu,
- operational context nie jest uzyty jako dowod root cause,
- repository/code-search scope ma konkretny powod,
- handoff route zawiera odbiorce, powod i pierwsza akcje albo limitation.

## Fallbacki

Jezeli katalog nie rozstrzyga:

- zwroc czesciowy `OperationalGroundingSummary`,
- wpisz `Nie ustalono` zamiast zgadywac,
- nazwij brak jako `visibilityLimit`,
- skieruj dalszy krok do GitLab, DB, runtime, downstream albo zespolu
  wlascicielskiego.

## Artefakty Handoffu

Przekaz:

- `OperationalGroundingSummary`,
- handoff route i evidence package,
- code-search albo DB targeting hints,
- visibility limits dla `functionalAnalysis` i `technicalAnalysis`.

## Kiedy Uzywac

Uzywaj operational context tools, gdy:

- deterministic operational-context evidence jest brakujace, czesciowe albo za
  waskie,
- affected process, bounded context, team, repository albo integration sa
  niejasne,
- GitLab project albo code-search scope nie jest ugruntowany,
- DB application/schema target potrzebuje pomocy z system albo repository
  context,
- finalna odpowiedz potrzebuje konkretnej trasy handoffu,
- uzytkownik pyta w follow-upie o znaczenie biznesowo-operacyjne, ownership,
  proces, glossary terms albo powiazane systemy.

Nie uzywaj tych tooli tylko dlatego, ze sa dostepne.

## Kolejnosc Tooli

Preferuj ta kolejnosc:

1. Najpierw dolaczone artefakty incydentu.
2. `opctx_get_scope` najwyzej raz, gdy nie wiesz, jakie typy katalogu sa
   dostepne.
3. `opctx_list_entities` jako table-of-contents browse, gdy nie znasz jeszcze
   terminu katalogowego.
4. `opctx_search`, gdy masz konkretny sygnal z logow, kodu, tool results albo
   pytania uzytkownika.
5. `opctx_get_entity` przed poleganiem na ownershipie, handoffie, procesie,
   bounded context, relacji albo code-search details.

## Zasady Browse

Uzywaj `opctx_list_entities` wasko:

- browse jednego typu naraz,
- preferuj prosty filter, gdy istnieje jakikolwiek clue,
- nie przegladaj calego katalogu,
- czytaj kolejna strone tylko wtedy, gdy poprzednia sugeruje, ze relevant
  entity moze byc blisko,
- uzywaj szczegolnie dla processes, bounded contexts, integrations i glossary
  terms, gdy model moze nie znac lokalnego slownictwa.

## Zasady Groundingu

Nie wymyslaj i nie podnos kontekstu katalogowego do rangi incident evidence.

Nazywaj `affectedProcess`, `affectedBoundedContext` albo `affectedTeam` tylko
wtedy, gdy catalog entity jest wsparta przez artefakty incydentu albo tool
results. Gdy dopasowanie jest slabe, wpisz `nieustalone` albo opisz
ograniczenie.

`system` jest kanoniczna encja katalogowa. Runtime, deployment, service i
container names sa sygnalami oraz wlasciwosciami systemu, nie osobnymi
kanonicznymi runtime components.

## Targetowanie GitLaba

Uzywaj operational context do zawezania GitLab exploration:

- preferuj `codeSearchScope`, gdy pasuje do semantic target analizy: bounded
  context, process, system albo integration,
- traktuj `codeSearchScope.target.type/id` jako powod, dla ktorego repository
  set nalezy czytac razem,
- przekaz wszystkie istotne `projectName` ze scope do GitLab search/flow tools,
- zaczynaj od repozytoriow `primary-implementation` albo priority `1`,
- przechodz do supporting libraries, generated clients, integration adapters,
  legacy modules albo collaborator repositories tylko wtedy, gdy evidence albo
  `traversal.expandWhen` to uzasadnia,
- uzywaj package prefixes, class hints, endpoint hints i queue/topic hints jako
  focused search terms.

Nie uznawaj kodu za niedostepny po jednym repository lookup, gdy operational
context listuje szerszy code-search scope.

## Targetowanie DB

Operational context moze pomoc wybrac application, deployment, system,
repository albo DB hint dla DB tools.

Nie dowodzi data issue. Dla JPA, repository albo data-access symptoms nadal
najpierw ugruntuj entity/repository/table hints z deterministic GitLab evidence
albo enabled GitLab tools, gdy to mozliwe.

## Handoff

Uzywaj handoff hints i handoff rules jako guidance routingu.

Gdy rekomendujesz handoff, uwzglednij:

- dlaczego ta trasa jest istotna dla incydentu,
- jakie evidence trzeba przekazac,
- co receiving team albo party powinien sprawdzic jako pierwsze.

Jezeli ownership jest niejednoznaczny, napisz to wprost.

## `reason` Dla Operational Context Tooli

Kazdy operational context tool call musi miec opcjonalny argument `reason`.

Pisz `reason` po polsku jako jedno krotkie praktyczne zdanie dla operatora.
Nie umieszczaj hidden reasoning, dlugiej analizy ani chain-of-thought.

Dobre przyklady:

```text
Sprawdzam, czy sygnaly z logow pasuja do znanego procesu lub bounded contextu.
Przegladam katalog integracji, bo evidence nie wskazuje jednoznacznego downstreamu.
Pobieram szczegoly systemu, zeby potwierdzic wlasciciela i zakres szukania kodu.
```

## Antywzorce

Nie:

- przegladaj calego katalogu "dla bezpieczenstwa",
- uzywaj glossary terms jako dowodu awarii,
- nazywaj ownerow, procesow albo bounded contexts bez incident groundingu,
- uzywaj operational context zamiast GitLaba do dowodzenia zachowania kodu,
- uzywaj operational context zamiast DB tools do dowodzenia stanu danych,
- wywoluj `opctx_get_scope` wielokrotnie,
- traktuj deployment/runtime names jako osobne canonical entities.
