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

## Wklad Do Wyniku

Po uzyciu tego skilla zwroc do orkiestratora:

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

## Kiedy Wrocic Do Orkiestratora

Wroc do orkiestratora, gdy:

- DB evidence potwierdza data issue,
- DB evidence obala hipoteze danych,
- dalsza diagnostyka wymaga kodu, runtime, downstream albo innego schema ownera,
- table/application scope pozostaje niejasny po focused discovery,
- kolejne DB calls bylyby browsingiem, a nie testem rozrozniajacym.

Jesli dane wygladaja poprawnie, napisz wprost: `DB evidence nie tlumaczy
incydentu` i wskaz, ktora klasa bledu powinna byc sprawdzona dalej.

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

## Model Oracle Application Schema

Analizowane srodowiska uzywaja jednej bazy Oracle per environment.

Aplikacje zwykle posiadaja tabele przez dedykowanych Oracle users/schemas.
Database tools uzywaja:

- session-bound environment,
- readonly technical DB user,
- skonfigurowanego mapowania application/deployment names na Oracle
  owners/schemas.

Traktuj `schema` jako Oracle owner/application user.

Preferuj application-scoped table discovery:

```text
application/deployment/container/project name -> configured Oracle owner/schema -> tables/views
```

Nie pytaj uzytkownika o application name. Wywnioskuj application/deployment
name z logow, deployment evidence, deterministic GitLab evidence albo manifest
context.

Dobre `applicationPattern`:

```text
crm-service
customer-api
crm-sync-worker
crm-case-service
application container name from logs
GitLab project name from deterministic evidence
deployment name from runtime evidence
```

Nie przekazuj Oracle schema names, chyba ze poprzedni DB tool result pokazal
dokladny schema. Model ma zwykle myslec w kategoriach application/deployment,
nie Oracle owner.

## Obowiazkowe Code-First Targeting Przed DB Discovery

Gdy symptom wskazuje JPA, repository lookup, missing entity, relation traversal
albo data filtering, najpierw wyprowadz table i relation hints z kodu przed
broad DB discovery.

Jesli `DB_CODE_GROUNDING_NEEDED` wystepuje w `00-incident-manifest.json`, nie
zaczynaj `db_find_tables`, `db_find_columns`, `db_describe_table`, row counts
ani samples z guessed table/column names.

Przed pierwszym DB table/column/schema-table query zrob jedno z:

1. Uzyj deterministic GitLab evidence, jezeli juz pokazuje entity/repository
   mapping.
2. Jesli GitLab tools sa dostepne, wykonaj focused GitLab tool call, aby
   znalezc entity/repository/table mapping.
3. Jesli code grounding jest niedostepny albo nie znaleziono entity/repository,
   napisz to w DB `reason` i uzyj DB discovery jako fallbacku.

Fallback jest dozwolony, ale dopiero po code-grounding attempt albo po
potwierdzeniu, ze nie ma odpowiedniego GitLab toola.

Preferowana sekwencja:

1. Uzyj deterministic GitLab evidence, gdy zawiera grounded entity/repository.
2. Gdy GitLab tools sa dostepne i klasa jest ugruntowana, uzyj:
   - `gitlab_find_class_references`,
   - `gitlab_read_repository_file_outline`,
   - `gitlab_read_repository_file_chunk` albo
     `gitlab_read_repository_file_chunks`.
3. Wyciagnij z kodu:
   - `@Entity`, `@Table`, `@Column`,
   - `@JoinColumn`, `@JoinTable`, `mappedBy`,
   - `@Embeddable`, `@ElementCollection`,
   - repository method names i derived `findBy...` predicates,
   - jawne `@Query`,
   - business keys, tenant/status/deleted/validity filters.
4. Uzyj code-derived hints do zawezenia:
   - `applicationPattern`,
   - `tableNamePattern`,
   - `entityOrKeywordHint`,
   - expected columns,
   - expected relationships.

Nie zgaduj tabeli tylko z etykiety exceptiona, jesli kod moze ja ugruntowac.

Dobry fallback `reason`:

```text
Nie udalo sie potwierdzic encji w dostepnym kodzie, wiec szukam tabeli po nazwie aplikacji i slowach z bledu.
```

## Priorytet Data-First

Najpierw sprawdzaj:

1. brakujacy row,
2. zly key albo business identifier,
3. zly tenant/context,
4. zly status/state/lifecycle,
5. inactive albo soft-deleted row,
6. row poza validity window,
7. missing dictionary/reference value,
8. stale albo orphan reference,
9. duplicate/non-unique active data,
10. stuck outbox/event/process state.

Przejdz do schema/mapping tylko gdy:

- logs wspominaja invalid table/column/mapping,
- jest Liquibase/migration evidence,
- data checks sa sprzeczne z zachowaniem aplikacji,
- repository predicate nie da sie zmapowac na DB columns,
- JPA annotations wygladaja niespojnie z DB metadata.

## Kolejnosc Tooli

Uzywaj tej kolejnosci, chyba ze evidence wyraznie uzasadnia inna:

1. `db_get_scope`
   - gdy application-to-schema scope, DB alias albo allowed schemas sa niejasne,
   - zeby zobaczyc application aliases/schemas dla aktualnego environment,
   - nie wywoluj wielokrotnie.

2. `db_find_tables`
   - gdy dokladna tabela jest nieznana,
   - przekaz `applicationPattern` z evidence, gdy to mozliwe,
   - uzywaj `tableNamePattern` i `entityOrKeywordHint` do ranked candidates.

3. `db_find_columns`
   - gdy key/filter columns sa nieznane,
   - przekaz `applicationPattern`,
   - lokalizuj ID, business key, tenant, status, state, soft-delete, validity,
     event albo correlation columns.

4. `db_describe_table`
   - dla najbardziej prawdopodobnej tabeli,
   - nie opisuj wielu tabel na slepo,
   - uzyj przed data checks, gdy column names, PK/FK albo relationships sa
     niepewne.

5. `db_exists_by_key`
   - dla direct primary key albo business key checks,
   - najlepszy dla `EntityNotFoundException`, missing dictionary/reference
     value i direct entity lookup.

6. `db_count_rows`
   - dla key-only i full-predicate checks,
   - uzywaj przed samplingiem.

7. `db_group_count`
   - dla status, state, tenant, type, deleted, active, validity, retry albo
     error distribution.

8. `db_check_orphans`
   - dla stale child/parent/reference problems,
   - preferuj przed recznym joinowaniem.

9. `db_find_relationships`
   - gdy relation structure jest niejasna,
   - odrozniaj declared FK relationships od inferred `*_ID` hints.

10. `db_join_count`
    - dla relation albo repository join checks,
    - przed `db_join_sample`.

11. `db_join_sample`
    - tylko po join count, gdy minimalny przyklad jest uzyteczny.

12. `db_sample_rows`
    - tylko dla malych, jawnych technical projections,
    - nie uzywaj do browse danych.

13. `db_compare_table_to_expected_mapping`
    - tylko po data checks albo przy explicit schema/mapping symptoms.

14. `db_execute_readonly_sql`
    - last resort.

## Application-Scoped Discovery

Dla `db_find_tables` preferuj request:

```text
db_find_tables(applicationPattern, tableNamePattern, entityOrKeywordHint, limit, reason)
```

Dla `db_find_columns` preferuj:

```text
db_find_columns(applicationPattern, tableNamePattern, columnNamePattern, javaFieldNameHint, limit, reason)
```

Backend rozwiazuje:

```text
session environment + applicationPattern -> configured Oracle owner/schema
```

Potem szuka w Oracle metadata dla resolved owner/schema.

Uzywaj `applicationPattern` z:

- deployment name,
- container name,
- service name,
- GitLab project name,
- stacktrace project/module hint,
- deterministic deployment evidence,
- runtime evidence.

Jesli application scope jest niejednoznaczny:

1. wywolaj `db_get_scope`,
2. wybierz najlepszego application candidate ugruntowanego w evidence,
3. jesli nadal jest niejasno, uzyj malego discovery query i opisz ambiguity w
   final technical analysis.

Nie pytaj uzytkownika o application name podczas normalnej analizy incydentu.

## Minimalna Eksploracja DB

Nie przegladaj bazy.

Kazdy DB call ma miec jeden cel:

- Czy row istnieje po ID?
- Czy istnieje po business key?
- Czy spelnia pelny predykat aplikacji?
- Czy jest w innym tenant/context?
- Czy jest inactive, deleted albo expired?
- Czy child row wskazuje missing parent?
- Czy sa duplicate active rows?
- Czy outbox/event row utknal w failed state?

Preferuj ranked discovery i exact data checks zamiast szerokich dumpow
metadata.

## Kluczowe Porownanie: Key-Only Vs Full Predicate

Dla "not found", empty result, repository lookup albo entity loading symptoms
porownaj:

1. count po direct key/business key,
2. count po pelnym predykacie aplikacji.

Interpretacja:

```text
key-only count = 0
```

Mozliwe wyjasnienia:

- brak danych testowych,
- zly ID/business key,
- zly table candidate,
- zly application/schema scope,
- niewystarczajaca DB visibility.

```text
key-only count > 0
full-predicate count = 0
```

Mocne data explanation:

- dane istnieja, ale sa odfiltrowane przez tenant, status, soft-delete,
  validity, type albo state predicate.

```text
full-predicate count > 0
application still fails
```

Dane sa mniej prawdopodobne. Skup sie na:

- wrong method arguments,
- wrong code path,
- mapping/conversion,
- transaction/cache/staleness,
- downstream/runtime problem.

## Pattern: EntityNotFound / Missing Entity

Uzywaj dla:

- `EntityNotFoundException`,
- `JpaObjectRetrievalFailureException`,
- "Unable to find ... with id ...",
- lazy-loaded missing entity,
- missing dictionary/reference entity.

Procedura:

1. Wyciagnij z logs/code:
   - entity class,
   - ID albo business key,
   - parent/child clue,
   - repository method,
   - tenant/context,
   - status/state clue,
   - application/deployment/container/project name.
2. Jesli tabela jest niejasna:
   - uzyj GitLab evidence albo GitLab tools do mapowania entity/repository,
   - uzyj entity annotations, relation annotations i repository method names
     jako hints,
   - uzyj `db_find_tables` z `applicationPattern`,
   - uzyj `entityOrKeywordHint`,
   - potem `db_describe_table` dla najlepszego kandydata.
3. Sprawdz direct existence przez `db_exists_by_key`.
4. Jesli row brakuje:
   - sprawdz, czy inna tabela referencjonuje missing ID,
   - uzyj `db_check_orphans`, gdy znana jest relacja child/parent,
   - w przeciwnym razie uzyj `db_find_relationships` albo focused code read.
5. Jesli row istnieje:
   - uzyj `db_count_rows` z pelnym repository predicate:
     tenant/context, status/state, soft delete, validity date,
     type/discriminator, active flag.
6. Wnioskuj tylko to, co wspiera DB evidence.

Mozliwe wnioski:

- missing test data,
- stale/orphan reference,
- row exists but predicate excludes it,
- wrong tenant/context,
- inactive/soft-deleted/expired data,
- DB data looks correct, implementation/runtime issue more likely.

## Pattern: Repository Empty / Business Not Found

Uzywaj dla:

- `Optional.empty`,
- business 404,
- "not found" exception z logiki serwisu,
- repository method returning no rows.

Procedura:

1. Zidentyfikuj repository method i predicates z logs/code.
2. Wyciagnij filtry:
   - key,
   - tenant,
   - status/state,
   - type,
   - soft delete,
   - validity dates,
   - active flag,
   - ownership/context,
   - joins i relation paths z entity annotations albo method structure.
3. Zlokalizuj tabele przez `db_find_tables` z `applicationPattern`, jesli
   trzeba.
4. Uzyj `db_count_rows` po key only.
5. Uzyj `db_count_rows` po full predicate.
6. Uzyj `db_group_count` po status/tenant/deleted/state, jesli full predicate
   zwraca zero.
7. `db_sample_rows` tylko dla minimalnych technical columns.

Dobra finalna fraza:

```text
Rekord istnieje, ale aplikacja go nie widzi, bo nie spelnia predykatu repozytorium: ...
```

Nie nazywaj tego "missing entity", jesli key-only count dowodzi, ze row
istnieje.

## Pattern: Tenant Albo Context Mismatch

Uzywaj, gdy evidence zawiera tenant, organization, customer context, user
context, account scope, permission scope albo environment-specific context.

Procedura:

1. Wyciagnij tenant/context uzyty przez failing flow.
2. Zlokalizuj relevant table.
3. Count by business key bez tenant/context.
4. Count by business key z tenant/context.
5. Group by tenant/context, jesli trzeba.
6. Sample tylko minimal columns, jesli trzeba.

Interpretacja:

```text
count without tenant > 0
count with tenant = 0
```

Prawdopodobny wniosek:

```text
Dane istnieja, ale w innym kontekscie niz uzyty przez aplikacje.
```

## Pattern: Status, Lifecycle, Soft Delete Albo Validity Mismatch

Uzywaj, gdy evidence wspomina status, state, active/inactive, deleted flag,
validity dates, lifecycle transition albo current version.

Procedura:

1. Ustal expected state z code/logs.
2. Zlokalizuj key/status columns przez `db_find_columns`, jesli trzeba.
3. Count by key only.
4. Count by key + expected status/state/active/validity predicate.
5. Group by status/state/deleted/active.
6. Sample minimal technical columns, jesli trzeba:
   ID, business key, tenant/context, status/state, deleted/active, validity
   dates, updated timestamp.

Mozliwe wnioski:

- row exists but has wrong status/state,
- row exists but is inactive albo soft-deleted,
- row exists but is outside validity window.

## Pattern: Orphan Albo Stale Reference

Uzywaj, gdy evidence sugeruje:

- child references missing parent,
- missing dictionary/reference row,
- lazy loading failure,
- stale relation,
- FK-like relation bez declared FK.

Procedura:

1. Zidentyfikuj child table i reference column.
2. Zidentyfikuj parent/reference table i key column.
3. Uzyj `db_find_relationships`, jesli relation structure jest niejasna.
4. Opisz obie tabele tylko gdy trzeba.
5. Uzyj `db_check_orphans`.
6. Uzyj `db_join_count` albo `db_join_sample` tylko gdy orphan check nie
   wystarcza.
7. Raportuj child key i missing parent/reference key, nie duze row dumps.

Mocny wniosek:

```text
Potwierdzono osierocona referencje: rekord dziecka wskazuje na brakujacy rekord nadrzedny/referencyjny.
```

Uzyj go tylko, gdy DB evidence to potwierdza.

## Pattern: Duplicate Albo Non-Unique Data

Uzywaj dla:

- `NonUniqueResultException`,
- `IncorrectResultSizeDataAccessException`,
- duplicate key,
- unique constraint violation,
- unexpected multiple active rows.

Procedura:

1. Zidentyfikuj expected unique key i active predicate.
2. Zlokalizuj table przez application-scoped discovery, jesli trzeba.
3. Uzyj `db_count_rows` z pelnym predykatem.
4. Jesli count > 1, uzyj `db_group_count` albo minimal sample.
5. Wyjasnij, czy duplikaty sa active, inactive, soft-deleted czy historical.

Mozliwy wniosek:

```text
Duplikaty aktywnych danych naruszaja zalozenie unikalnosci repozytorium/uslugi.
```

## Pattern: Async / Outbox / Event / Process Stuck

Uzywaj, gdy evidence wspomina outbox, event, message, scheduler, listener,
consumer, retry, processing state albo failed process row.

Procedura:

1. Wyciagnij:
   - correlation ID,
   - event ID,
   - aggregate/business ID,
   - message type,
   - processing state,
   - retry count,
   - error code,
   - timestamps,
   - application/deployment/container/project name.
2. Zlokalizuj table:
   - `db_find_tables` z `applicationPattern`,
   - hints: `OUTBOX`, `EVENT`, `MESSAGE`, `PROCESS`, `JOB`, `TASK`.
3. Zlokalizuj columns:
   - `CORRELATION_ID`,
   - `EVENT_ID`,
   - `AGGREGATE_ID`,
   - `STATUS`,
   - `STATE`,
   - `RETRY_COUNT`,
   - `ERROR_CODE`.
4. Count by correlation/event/business ID.
5. Group by processing state albo error code.
6. Sample minimal technical columns, jesli trzeba.

Mozliwe wnioski:

- event was not created,
- event exists but is stuck,
- retry was exhausted,
- process data points to downstream failure,
- DB process state looks normal, so runtime/downstream should be checked.

## Pattern: Cross-Application Albo Shared Reference Data

Uzywaj, gdy evidence sugeruje:

- common dictionary/reference schema,
- shared outbox/event schema,
- another application owns parent/reference data,
- integration flow writes data in one application and reads it in another.

Procedura:

1. Zacznij od primary application z evidence.
2. Uzyj `db_get_scope`, jesli related application/schema jest niejasny.
3. Uzyj `db_find_tables` z related application name tylko gdy evidence to
   wspiera.
4. Nie rozszerzaj do wszystkich schemas tylko dlatego, ze pierwsza aplikacja
   nie miala tabeli.
5. Opisz cross-application scope w final technical analysis.

Dobre wyjasnienie:

```text
Pierwsze sprawdzenie dotyczylo aplikacji `crm-service`, ale referencja wskazuje na dane utrzymywane przez `customer-service`.
```

## Regula Raw SQL

Nie uzywaj `db_execute_readonly_sql`, jezeli check da sie wyrazic przez:

- `db_exists_by_key`,
- `db_count_rows`,
- `db_group_count`,
- `db_check_orphans`,
- `db_join_count`,
- `db_sample_rows`.

Raw SQL nigdy nie jest pierwszym DB tool.

Jesli raw SQL jest uzyty, wyjasnij w polskim `reason`, dlaczego typed tools nie
wystarczyly.

## Standard Evidence

Nie wnioskuj "data issue", jezeli DB evidence tego nie potwierdza.

Mocne wnioski danych zwykle wymagaja jednego z:

- direct missing row confirmed,
- key-only count rozni sie od full predicate count,
- orphan reference confirmed,
- duplicate count confirmed,
- process/outbox state confirmed.

Jesli DB checks wygladaja poprawnie, napisz, ze dane aktualnie nie tlumacza
incydentu i przenies uwage na code, runtime, integration albo visibility
limits.

## Styl Wyjasniania Wynikow DB

W finalnej odpowiedzi tlumacz DB findings jezykiem operator-friendly.

Preferuj:

```text
Rekord istnieje, ale aplikacja go nie widzi, bo nie spelnia filtra `STATUS=ACTIVE`.
```

zamiast:

```text
COUNT(*) with predicate returned 0.
```

Gdy raportujesz discovery, wspomnij application-to-schema resolution, jesli ma
znaczenie:

```text
Tabele byly szukane w schemacie `CRM_APP`, dobranym z aplikacji/deploymentu `crm-service`.
```

Podawaj techniczne identyfikatory, gdy sa przydatne, ale nie zalewaj wyniku raw
rows.

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
