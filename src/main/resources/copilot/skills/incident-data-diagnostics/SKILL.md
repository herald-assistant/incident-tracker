---
name: incident-data-diagnostics
description: Playbook diagnozy od danych dla incydentow powiazanych z DB, z uzyciem typowanych, session-bound Oracle database tools.
---

# Diagnostyka Danych W Incydencie

Uzywaj tego skilla, gdy logs, code, runtime evidence albo repository predicates
sugeruja, ze incydent moze zalezec od danych w bazie.

Dotyczy to m.in.:

- brakujacej encji,
- brakujacych danych slownikowych albo referencyjnych,
- repository zwracajacego pusty wynik,
- zlego tenant/context,
- zlego statusu albo lifecycle state,
- soft-deleted albo inactive row,
- validity-date mismatch,
- duplikatu aktywnych danych,
- stale albo orphan reference,
- stuck outbox/event/process state,
- write failure spowodowanego istniejacymi albo brakujacymi danymi
  powiazanymi.

W tym systemie schema problems sa mniej prawdopodobne, bo Hibernate, Spring i
Liquibase zwykle utrzymuja schema i code aligned. Dlatego najpierw sprawdzaj
przyczyny data-related. Schema i mapping checks sa dopuszczalne, gdy evidence
wyraznie na nie wskazuje.

## Cel

Potwierdz albo obal hipoteze danych przez najmniejszy potrzebny DB check i
zwroc `DataDiagnosticSummary`: finding, table/key/predicate, evidence,
visibility limits oraz nastepny krok.

## Rola Wobec Orkiestratora

Ten skill jest diagnostycznym playbookiem danych wybieranym przez
`incident-analysis-orchestrator` po wstepnym researchu flow, lokalizacji
failure point i klasyfikacji bledu jako data-related albo process-state-related.

Nie zaczynaj diagnozy od nowa. Twoim zadaniem jest potwierdzic albo obalic
hipoteze danych przez DB evidence. Nie wolno oglosic data issue bez DB
evidence.

## Wejscie Oczekiwane Od Orkiestratora

Przyjmij od orkiestratora:

- fingerprint incydentu,
- flow use case'u i miejsce przerwania,
- aktywna hipoteze danych,
- ugruntowana aplikacje/deployment/container/project name,
- code-derived hints, gdy sa dostepne: entity, repository, table, columns,
  relations, predicates,
- identyfikatory z logs/evidence: ID, business key, tenant/context, status,
  event ID, correlationId, timestamps.

Jezeli brakuje code-derived hints, ale hipoteza danych jest silna, uzyj
fallback discovery oszczednie i opisz brak ugruntowania w `visibilityLimits`.

## Klasy Bledu Obslugiwane Przez Ten Skill

Uzywaj tego skilla dla:

- `data_missing`,
- `data_predicate_mismatch`,
- `data_orphan_or_stale_reference`,
- `data_duplicate_or_non_unique`,
- `async_or_process_state`, gdy stan outbox/event/process jest utrzymywany w
  DB.

Uzywaj go wspierajaco dla:

- `configuration_or_environment`, gdy roznice danych/scope'u miedzy
  srodowiskami sa realna hipoteza,
- `outside_visibility_or_handoff`, gdy dane sa utrzymywane przez inna aplikacje
  albo schema ownera.

## Hipotezy, Ktore Skill Ma Potwierdzic Albo Obalic

Ten skill ma odpowiedziec:

- czy wymagany row istnieje,
- czy row istnieje, ale odpada przez pelny predykat aplikacji,
- czy problem wynika z tenant/context/status/soft-delete/validity/type,
- czy istnieje orphan albo stale reference,
- czy aktywne dane sa zduplikowane,
- czy outbox/event/process jest stuck, exhausted albo missing,
- czy DB data nie tlumaczy incydentu i nalezy wrocic do code/runtime/integration.

## Testy Rozrozniajace

Dobierz najmniejszy DB check, ktory rozroznia aktywne hipotezy:

- `key-only count = 0` vs `full-predicate count = 0` rozroznia missing row od
  predicate mismatch,
- group by status/tenant/deleted/state rozroznia zly stan danych od braku
  danych,
- orphan check rozroznia missing parent/reference od bledu kodu,
- duplicate count rozroznia non-unique data od zlego error handlingu,
- process/outbox group count rozroznia stuck state od downstream/runtime
  problemu.

Jesli code grounding jest wymagany, a nieudany, napisz to w `reason` DB
fallbacku.

## Petla DB Diagnostics

Po kazdym DB checku ocen, czy hipoteza danych jest potwierdzona, odrzucona czy
nadal nierozstrzygnieta.

- Jezeli DB evidence potwierdza albo obala hipoteze, zwroc
  `DataDiagnosticSummary` i zakoncz eksploracje.
- Jezeli wynik jest zbyt plytki, ale pokazuje konkretny nastepny typed check
  (`key-only`, `full-predicate`, `group_count`, `orphan`, `duplicate`,
  `process state` albo jedna relacje), wykonaj jedno waskie poglebienie.
- Jezeli brakuje entity/table/predicate hints i da sie je uzyskac z kodu,
  wroc do orkiestratora z potrzeba `incident-code-grounding` zamiast robic
  broad DB discovery.
- Jezeli dalszy DB call bylby przegladaniem danych albo zgadywaniem schema,
  zwroc limitation.

Nie potwierdzaj data issue bez DB evidence. Gdy wynik jest tylko poszlaka,
ustaw finding jako `inconclusive` albo `rejected` i nazwij brakujaca warstwe
proof.

## Wklad Do Wyniku

Po uzyciu tego skilla zwroc do orkiestratora:

- `DataDiagnosticSummary`,
- `technicalAnalysis`: application/schema scope, table, key, predicate, status,
  tenant/context, relation albo process-state finding oraz konkretna akcja dla
  Data/DBA/Developer,
- `functionalAnalysis`: co dane blokuja w procesie albo jaki obiekt
  biznesowo-systemowy nie przechodzi dalej,
- `visibilityLimits`: brak DB access, niepewny schema/application scope,
  niepotwierdzona relacja, brak code grounding albo cross-application
  ownership,
- `confidence`: confirmed tylko przy DB evidence; strong_hypothesis przy mocnym
  DB sygnale i braku sprzecznosci; weak_hypothesis gdy brakuje kluczowego
  checku.

## Kontrakt Wyniku

Zwroc `DataDiagnosticSummary`:

```text
dataHypothesis: <candidate data class>
dbChecks:
  - question: <co rozroznial check>
    toolOrEvidence: <db tool/artifact>
    result: <count/group/orphan/sample summary>
finding:
  class: data_missing | data_predicate_mismatch | data_orphan_or_stale_reference | data_duplicate_or_non_unique | async_or_process_state | rejected | inconclusive
  mechanism: <jak dane blokuja flow albo dlaczego hipoteza odpada>
targets:
  - table: <TABLE albo Nie ustalono>
    keyOrPredicate: <key/predicate/status/context>
action:
  owner: <Data/DBA/Developer/Other albo Nie ustalono>
  firstCheck: <konkretna weryfikacja albo korekta>
visibilityLimits:
  - <brak DB/code/runtime/downstream visibility>
```

## Kiedy Wrocic Do Orkiestratora

Wroc do orkiestratora, gdy:

- DB evidence potwierdza data issue,
- DB evidence obala hipoteze danych,
- dalsza diagnostyka wymaga kodu, runtime, downstream albo innego schema ownera,
- table/application scope pozostaje niejasny po focused discovery,
- kolejne DB calls bylyby browsingiem, a nie testem rozrozniajacym.

Jesli dane wygladaja poprawnie, napisz wprost: `DB evidence nie tlumaczy
incydentu` i wskaz, ktora klasa bledu powinna byc sprawdzona dalej.

## Walidacja

Sprawdz:

- data issue nie jest `confirmed` bez DB evidence,
- key-only vs full predicate jest uzyte, gdy symptom dotyczy missing/empty
  result,
- table/application scope jest ugruntowany albo limitation jest jawny,
- DB findings sa opisane operator-friendly i bez raw row dump,
- dalszy krok jest konkretny.

## Fallbacki

Jezeli DB tools nie sa dostepne albo scope jest niepewny:

- zwroc czesciowy `DataDiagnosticSummary`,
- nie udawaj DB verification,
- wskaz brakujacy code grounding, schema/application scope albo ownera,
- przekaz hipoteze z nizszym confidence do orkiestratora.

## Artefakty Handoffu

Przekaz:

- `DataDiagnosticSummary`,
- table/key/predicate/status/context albo limitation,
- minimalny DB/data action dla Data/DBA/Developer,
- informacje, czy DB evidence potwierdza, obala albo nie rozstrzyga hipotezy.

## Regula Sesji

Srodowisko DB pochodzi z ukrytego session-bound tool context. Nie przekazuj i
nie wymyslaj `environment` jako argumentu DB toola.

Wynikowego `environment` z manifestu/promptu uzywaj tylko w wyjasnieniu
finalnym.

Jesli DB tools nie sa dostepne w aktualnej sesji, nie udawaj, ze dane zostaly
zweryfikowane. Napisz, ze hipoteza danych wymaga DB verification.

Kazdy DB tool call musi miec opcjonalny argument `reason`.

Pisz `reason` po polsku jako jedno krotkie praktyczne zdanie dla junior
analityka. UI operatora pokazuje ten powod obok wyniku DB, wiec ma byc
konkretny i czytelny. Nie umieszczaj hidden reasoning, dlugiej analizy ani
chain-of-thought.

Dobre przyklady:

```text
Sprawdzam, czy istnieje rekord dla identyfikatora z bledu.
Licze rekordy, zeby porownac klucz z pelnym filtrem aplikacji.
Sprawdzam statusy rekordow powiazanych z correlationId.
```

## DB Scope I Code-First Targeting

DB tools sa session-bound: environment i techniczny DB user pochodza z ukrytego
contextu. Nie podawaj `environment` ani Oracle ownera jako model-facing input.

Mysl w kategoriach aplikacji/deploymentu, nie schema:

```text
deployment/container/GitLab project -> applicationPattern -> resolved Oracle owner/schema
```

Gdy symptom dotyczy JPA, repository lookup, relation traversal albo data
filtering, najpierw ugruntuj entity/table/predicate w evidence albo kodzie.
Jesli `DB_CODE_GROUNDING_NEEDED` jest w manifest, przed DB discovery uzyj
deterministic GitLab evidence albo focused GitLab tool. Fallback discovery jest
dozwolony tylko po nieudanym albo niedostepnym code grounding i musi to mowic
w `reason`.

Wyciagaj z kodu tylko to, co prowadzi DB check:

- `@Entity`, `@Table`, `@Column`, relation annotations,
- repository method, derived predicate albo `@Query`,
- business key, tenant/context, status/state, deleted/active, validity, type,
  event/correlation identifiers.

## Minimalny DB Flow

Nie przegladaj bazy. Wybierz najmniejszy check, ktory rozroznia hipotezy:

1. `db_get_scope` tylko gdy application/schema scope jest niejasny.
2. `db_find_tables` / `db_find_columns` tylko dla ranked discovery z
   `applicationPattern`, `tableNamePattern`, `entityOrKeywordHint` albo field
   hints.
3. `db_describe_table` dla jednego najlepszego kandydata, gdy kolumny lub
   relacje sa niepewne.
4. `db_exists_by_key` dla direct ID/business key.
5. `db_count_rows` dla key-only i full-predicate checks.
6. `db_group_count` dla tenant/status/state/deleted/validity/retry/error
   distribution.
7. `db_check_orphans`, `db_find_relationships`, `db_join_count` dla relacji.
8. `db_sample_rows` tylko po count/group i tylko dla minimalnych technical
   columns.
9. `db_compare_table_to_expected_mapping` tylko przy explicit schema/mapping
   symptoms.
10. `db_execute_readonly_sql` jako last resort, gdy typed tools nie wystarcza.

Kazdy DB call ma odpowiedziec na jedno pytanie: czy row istnieje, czy spelnia
pelny predykat, czy jest w innym context/statusie, czy relacja jest orphan, czy
istnieja duplikaty, albo czy process/event row utknal.

## Kluczowe Porownanie: Key-Only Vs Full Predicate

Dla "not found", empty result, repository lookup albo entity loading porownaj:

| Wynik | Interpretacja | Klasa |
|---|---|---|
| `key-only count = 0` | row nie istnieje po kluczu albo scope/table jest bledny | `data_missing` albo visibility gap |
| `key-only count > 0`, `full-predicate count = 0` | row istnieje, ale odpada przez tenant/status/deleted/validity/type/state | `data_predicate_mismatch` |
| `full-predicate count > 0`, aplikacja nadal failuje | dane mniej prawdopodobne; sprawdz argumenty, code path, mapping, cache, downstream/runtime | reclass poza data issue |

Nie nazywaj przypadku `data_missing`, jezeli key-only check dowodzi, ze row
istnieje.

## Patterny Diagnostyczne

| Symptom | Minimalny test DB | Wniosek / reclass |
|---|---|---|
| `EntityNotFoundException`, missing dictionary/reference | entity/table z kodu -> `db_exists_by_key`; gdy row istnieje, full predicate | brak row -> `data_missing`; row odpada -> `data_predicate_mismatch`; child wskazuje brak parent -> `data_orphan_or_stale_reference` |
| repository empty / business not found | repository predicate -> key-only count -> full-predicate count -> group by status/tenant/deleted/state | key-only > 0 i full = 0 -> predicate mismatch; full > 0 -> sprawdz code/runtime |
| tenant/context mismatch | count bez contextu, count z contextem, group by context | dane sa w innym context/tenant niz failing flow |
| status/lifecycle/soft-delete/validity | count key-only, count z expected state/validity, group by status/state/deleted/active | row istnieje, ale ma zly stan albo validity window |
| orphan/stale reference | child table + reference column + parent table -> `db_check_orphans` | relation issue potwierdzony tylko przez DB evidence |
| duplicate/non-unique | count z expected unique/active predicate, group albo minimal sample gdy count > 1 | `data_duplicate_or_non_unique` |
| async/outbox/process stuck | event/correlation/business ID -> count -> group by state/error/retry -> minimal sample | `async_or_process_state`; last error moze przekierowac do downstream/runtime |
| cross-application reference data | primary application first; related application tylko gdy evidence to wspiera | utrzymuj limitation albo handoff, nie skanuj wszystkich schemas |

## Standard Evidence I Styl

Nie wnioskuj "data issue", jezeli DB evidence tego nie potwierdza.

Mocne wnioski danych wymagaja zwykle: missing row, key-only vs full-predicate
split, orphan reference, duplicate count albo process/outbox state.

Jesli DB checks wygladaja poprawnie, napisz: `DB evidence nie tlumaczy
incydentu` i przenies uwage na code, runtime, integration albo visibility
limits.

Tlumacz DB findings jezykiem operator-friendly:

```text
Rekord istnieje, ale aplikacja go nie widzi, bo nie spelnia filtra `STATUS=ACTIVE`.
```

Podawaj table/key/predicate, gdy pomaga dzialac, ale nie dumpuj raw rows.

## Antywzorce

Nie:

- pytaj uzytkownika o environment,
- pytaj uzytkownika o Oracle schema/owner,
- pytaj uzytkownika o application name, jesli jest juz w evidence,
- przegladaj wszystkich schemas bez ugruntowanego powodu,
- wywoluj `db_sample_rows` przed count/group checks, chyba ze znany jest
  konkretny row ID,
- uzywaj `db_execute_readonly_sql` jako skrotu dla typed tools,
- diagnozuj data issue bez DB evidence,
- diagnozuj schema issue przed sprawdzeniem prawdopodobnych data causes,
- traktuj inferred relationships jako declared foreign keys,
- dumpuj raw DB rows do finalnej odpowiedzi.
