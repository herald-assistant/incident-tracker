---
name: incident-data-diagnostics
description: Data-first diagnostic playbook for DB-related incidents using typed, session-bound Oracle database tools.
---

# Incident Data Diagnostics

Use this skill when logs, code, runtime evidence, or repository predicates suggest that the incident may depend on database data.

This includes:

- missing entity,
- missing reference data,
- repository returning no result,
- wrong tenant/context,
- wrong status or lifecycle state,
- soft-deleted or inactive row,
- validity-date mismatch,
- duplicate active data,
- stale or orphan reference,
- stuck outbox/event/process state,
- write failure caused by existing or missing related data.

In this system, schema problems are expected to be less common because Hibernate, Spring and Liquibase usually keep schema and code aligned.
Therefore prioritize **data-related causes** before schema/mapping causes.

Schema and mapping checks are still allowed when evidence explicitly points there.

---

## Session rule

The DB environment is session-bound and comes from hidden tool context.
Do not pass or invent environment as a DB tool argument.

Use the environment from manifest/prompt only for explanation in the final answer.

If DB tools are not available in the current session, do not pretend that DB data was verified.
State that the data hypothesis requires DB verification.

Every DB tool call must include the optional `reason` argument.

Write `reason` in Polish as one short, practical sentence for a junior analyst.

The operator UI shows this reason next to the DB result, so make it concrete and readable.

Do not include hidden reasoning, long analysis, or step-by-step deliberation.

Good examples:

```text
Sprawdzam, czy istnieje rekord dla identyfikatora z bledu.
Licze rekordy, zeby porownac klucz z pelnym filtrem aplikacji.
Sprawdzam statusy rekordow powiazanych z correlationId.
```

---

## Oracle application schema model

The analyzed environments use one Oracle database per environment.

Applications usually own tables through their dedicated Oracle users/schemas.
Database tools use:

- the session-bound environment,
- a read-only technical DB user,
- configured mapping from application/deployment names to Oracle owners/schemas.

Treat `schema` as the Oracle owner/application user.

Prefer application-scoped table discovery:

```text
application/deployment/container/project name -> configured Oracle owner/schema -> tables/views
```

Do not ask the user for the application name.
Infer the application/deployment name from logs, deployment evidence, deterministic GitLab evidence, or manifest context.

When calling DB discovery tools, pass the application/deployment/service/container/project name that is already visible in evidence.

Good examples of `applicationNamePattern`:

```text
orders-service
customer-api
payment-worker
credit-agreement-process
application container name from logs
GitLab project name from deterministic evidence
deployment name from runtime evidence
```

Do not pass Oracle schema names unless a previous DB tool result already exposed the exact schema.
The model should normally think in terms of application/deployment names, not Oracle owners.

---

## Code-first targeting before DB discovery

When the symptom points to JPA, repository lookup, missing entity, relation traversal or data filtering, first derive table and relation hints from code before broad DB discovery.

Prefer this sequence:

1. Use deterministic GitLab evidence when it already contains the grounded entity/repository class.
2. If GitLab tools are available and a class name is grounded, use:
   - `gitlab_find_class_references` to find declaring/importing/using files,
   - `gitlab_read_repository_file_outline`,
   - `gitlab_read_repository_file_chunk` or `gitlab_read_repository_file_chunks`.
3. Extract from code:
   - `@Entity`, `@Table`, `@Column`,
   - `@JoinColumn`, `@JoinTable`, `mappedBy`,
   - `@Embeddable`, `@ElementCollection`,
   - repository method names and derived `findBy...` predicates,
   - explicit `@Query`,
   - business keys, tenant/status/deleted/validity filters.
4. Use those code-derived hints to narrow:
   - `applicationNamePattern`,
   - `tableNamePattern`,
   - `entityOrKeywordHint`,
   - expected columns,
   - expected relationships.

Do not guess the table only from the exception label if code can ground it first.

---

## Data-first priority

Prefer checking these causes first:

1. missing row,
2. wrong key or wrong business identifier,
3. wrong tenant/context,
4. wrong status/state/lifecycle,
5. inactive or soft-deleted row,
6. row outside validity window,
7. missing dictionary/reference value,
8. stale or orphan reference,
9. duplicate/non-unique active data,
10. stuck outbox/event/process state.

Only move to schema/mapping if:

- logs mention invalid table/column/mapping,
- Liquibase/migration evidence is present,
- data checks contradict application behavior,
- repository predicate cannot be mapped to DB columns,
- or JPA annotations look inconsistent with DB metadata.

---

## Tool order

Use this order unless evidence clearly justifies a different path:

1. `db_get_scope`
   - use when application-to-schema scope, DB alias, or allowed schemas are unclear;
   - use to see which application aliases/schemas are available for the current environment;
   - do not call repeatedly.

2. `db_find_tables`
   - use when the exact table is unknown;
   - pass `applicationNamePattern` from evidence whenever possible;
   - use `tableNamePattern` and `entityOrKeywordHint` to get ranked candidates instead of dumping all application tables.

3. `db_find_columns`
   - use when key/filter columns are unknown;
   - pass `applicationNamePattern` from evidence whenever possible;
   - use this to locate ID, business key, tenant, status, state, soft-delete, validity, event, or correlation columns.

4. `db_describe_table`
   - use for the most likely table candidate returned by `db_find_tables`;
   - do not describe many tables blindly;
   - use it before data checks when column names, PK/FK, or relationships are uncertain.

5. `db_exists_by_key`
   - use for direct primary key or business key checks;
   - best for `EntityNotFoundException`, missing dictionary/reference value, and direct entity lookup.

6. `db_count_rows`
   - use for key-only and full-predicate checks;
   - use this before sampling rows.

7. `db_group_count`
   - use for status, state, tenant, type, deleted, active, validity, retry, or error distribution.

8. `db_check_orphans`
   - use for stale child/parent/reference problems;
   - prefer this before manually building joins for orphan checks.

9. `db_find_relationships`
   - use when relation structure is unclear;
   - distinguish declared FK relationships from inferred `*_ID` hints.

10. `db_join_count`
   - use for relation or repository join checks;
   - prefer this before `db_join_sample`.

11. `db_join_sample`
   - use only after join count when a minimal example is useful.

12. `db_sample_rows`
   - use only for small, explicit, technical projections;
   - do not use it to browse data.

13. `db_compare_table_to_expected_mapping`
   - use only after data checks or when schema/mapping symptoms are explicit.

14. `db_execute_readonly_sql`
   - last resort only.

---

## Application-scoped discovery rules

For `db_find_tables`, prefer a request shaped conceptually like:

```text
db_find_tables(applicationNamePattern, tableNamePattern, entityOrKeywordHint, limit, reason)
```

For `db_find_columns`, prefer a request shaped conceptually like:

```text
db_find_columns(applicationNamePattern, tableNamePattern, columnNamePattern, javaFieldNameHint, limit, reason)
```

The backend resolves:

```text
session environment + applicationNamePattern -> configured Oracle owner/schema
```

Then it searches Oracle metadata for that resolved owner/schema.

Use `applicationNamePattern` from:

- deployment name,
- container name,
- service name,
- GitLab project name,
- stacktrace project/module hint,
- deterministic deployment evidence,
- runtime evidence.

If the application scope is ambiguous:

1. call `db_get_scope`,
2. choose the best application candidate grounded in evidence,
3. if still ambiguous, use a small discovery query and state the ambiguity in the final rationale.

Do not ask the user for the application name during normal incident analysis.

---

## Minimal DB exploration principle

Do not browse the database.

Each DB call should have one focused purpose, for example:

- Does the row exist by ID?
- Does it exist by business key?
- Does it match the full repository predicate?
- Is it in another tenant/context?
- Is it inactive, deleted or expired?
- Does a child row reference a missing parent?
- Are there duplicate active rows?
- Is an outbox/event row stuck in a failed state?

Prefer ranked discovery and exact data checks over broad metadata dumps.

---

## Core comparison: key-only vs full predicate

For "not found", empty result, repository lookup or entity loading symptoms, compare:

1. count by direct key/business key only,
2. count by full application predicate.

Typical interpretation:

```text
key-only count = 0
```

Possible explanations:

- missing test data,
- wrong ID/business key,
- wrong table candidate,
- wrong application/schema scope,
- insufficient DB visibility.

```text
key-only count > 0
full-predicate count = 0
```

Strong data explanation:

- data exists but is excluded by tenant, status, soft-delete, validity, type or state predicate.

```text
full-predicate count > 0
application still fails
```

Data is less likely.
Focus on:

- wrong method arguments,
- wrong code path,
- mapping/conversion,
- transaction/cache/staleness,
- downstream/runtime problem.

---

## Pattern: EntityNotFound / missing entity

Use for:

- `EntityNotFoundException`,
- `JpaObjectRetrievalFailureException`,
- "Unable to find ... with id ...",
- lazy-loaded missing entity,
- missing dictionary/reference entity.

Procedure:

1. Extract from logs/code:

   - entity class,
   - ID or business key,
   - parent/child clue,
   - repository method,
   - tenant/context,
   - status/state clue,
   - application/deployment/container/project name.

2. If table is unclear:

   - use GitLab evidence or GitLab tools to map entity/repository,
   - use entity annotations, relation annotations and repository method names as hints for the likely table and links,
   - use `db_find_tables` with `applicationNamePattern`,
   - use `entityOrKeywordHint` with entity, repository, key, or domain terms,
   - then use `db_describe_table` for the best candidate.

3. Check direct existence:

   - use `db_exists_by_key`.

4. If the row is missing:

   - check whether another table references that missing ID,
   - use `db_check_orphans` when a child/parent relation is known,
   - otherwise use `db_find_relationships` or focused code reading.

5. If the row exists:

   - use `db_count_rows` with the full repository predicate:

      - tenant/context,
      - status/state,
      - soft delete,
      - validity date,
      - type/discriminator,
      - active flag.

6. Conclude only what DB evidence supports.

Possible conclusions:

- missing test data,
- stale/orphan reference,
- row exists but predicate excludes it,
- wrong tenant/context,
- inactive/soft-deleted/expired data,
- DB data looks correct, implementation/runtime issue more likely.

---

## Pattern: repository returns empty / business "not found"

Use for:

- `Optional.empty`,
- business 404,
- "not found" exception created by service logic,
- repository method returning no rows.

Procedure:

1. Identify repository method and predicates from logs/code.
2. Extract all filters:

   - key,
   - tenant,
   - status/state,
   - type,
   - soft delete,
   - validity dates,
   - active flag,
   - ownership/context,
   - joins and relation paths implied by entity annotations or repository method structure.

3. Locate the relevant table using `db_find_tables` with `applicationNamePattern` if needed.
4. Use `db_count_rows` by key only.
5. Use `db_count_rows` by full predicate.
6. Use `db_group_count` by status/tenant/deleted/state if full predicate returns zero.
7. Use `db_sample_rows` only for minimal technical columns.

Good final explanation:

```text
Rekord istnieje, ale nie spełnia predykatu repozytorium, ponieważ ...
```

Do not call it "missing entity" if the key-only count proves the row exists.

---

## Pattern: tenant or context mismatch

Use when evidence contains:

- tenant,
- organization,
- customer context,
- user context,
- account scope,
- permission scope,
- environment-specific context.

Procedure:

1. Extract tenant/context used by the failing flow.
2. Locate the relevant table using application-scoped discovery if needed.
3. Count by business key without tenant/context.
4. Count by business key with tenant/context.
5. Group by tenant/context if needed.
6. Sample only minimal columns if needed.

Interpretation:

```text
count without tenant > 0
count with tenant = 0
```

Likely:

```text
Dane istnieją, ale w innym kontekście niż użyty przez aplikację.
```

---

## Pattern: status, lifecycle, soft delete or validity mismatch

Use when evidence mentions:

- status,
- state,
- active/inactive,
- deleted flag,
- validity dates,
- lifecycle transition,
- current version.

Procedure:

1. Identify expected state from code/logs.
2. Locate key/status columns with `db_find_columns` if needed.
3. Count by key only.
4. Count by key + expected status/state/active/validity predicate.
5. Group by status/state/deleted/active.
6. Sample minimal technical columns if needed:

   - ID,
   - business key,
   - tenant/context,
   - status/state,
   - deleted/active,
   - validity dates,
   - updated timestamp.

Interpretation:

- row exists but has wrong status/state,
- row exists but is inactive or soft-deleted,
- row exists but is outside validity window.

---

## Pattern: orphan or stale reference

Use when evidence suggests:

- child references missing parent,
- missing dictionary/reference row,
- lazy loading failure,
- stale relation,
- FK-like relation without declared FK.

Procedure:

1. Identify child table and reference column.
2. Identify parent/reference table and key column.
3. Use `db_find_relationships` if relation structure is unclear.
4. Describe both tables only if needed.
5. Use `db_check_orphans`.
6. Use `db_join_count` or `db_join_sample` only if orphan check is insufficient.
7. Report child key and missing parent/reference key, not large row dumps.

Strong conclusion:

```text
Potwierdzono osieroconą referencję: rekord dziecka wskazuje na brakujący rekord nadrzędny/referencyjny.
```

Only use this when DB evidence confirms it.

---

## Pattern: duplicate or non-unique data

Use for:

- `NonUniqueResultException`,
- `IncorrectResultSizeDataAccessException`,
- duplicate key,
- unique constraint violation,
- unexpected multiple active rows.

Procedure:

1. Identify expected unique key and active predicate.
2. Locate the table using application-scoped discovery if needed.
3. Use `db_count_rows` with the full predicate.
4. If count > 1, use `db_group_count` or minimal sample.
5. Explain whether duplicates are active, inactive, soft-deleted or historical.

Possible conclusion:

```text
Duplikaty aktywnych danych naruszają założenie unikalności repozytorium/usługi.
```

---

## Pattern: async/outbox/event/process stuck

Use when evidence mentions:

- outbox,
- event,
- message,
- scheduler,
- listener,
- consumer,
- retry,
- processing state,
- failed process row.

Procedure:

1. Extract:

   - correlation ID,
   - event ID,
   - aggregate/business ID,
   - message type,
   - processing state,
   - retry count,
   - error code,
   - timestamps,
   - application/deployment/container/project name.

2. Locate table:

   - use `db_find_tables` with `applicationNamePattern`,
   - use hints such as `OUTBOX`, `EVENT`, `MESSAGE`, `PROCESS`, `JOB`, `TASK`.

3. Locate columns:

   - `CORRELATION_ID`,
   - `EVENT_ID`,
   - `AGGREGATE_ID`,
   - `STATUS`,
   - `STATE`,
   - `RETRY_COUNT`,
   - `ERROR_CODE`.

4. Count by correlation/event/business ID.
5. Group by processing state or error code.
6. Sample minimal technical columns if needed.

Possible conclusions:

- event was not created,
- event exists but is stuck,
- retry was exhausted,
- process data points to downstream failure,
- DB process state looks normal, so runtime/downstream should be checked.

---

## Pattern: cross-application or shared reference data

Use when evidence suggests:

- common dictionary/reference schema,
- shared outbox/event schema,
- another application owns the parent/reference data,
- integration flow writes data in one application and reads it in another.

Procedure:

1. Start with the primary application from evidence.
2. Use `db_get_scope` if the related application/schema is unclear.
3. Use `db_find_tables` with the related application name only when evidence supports it.
4. Do not broaden to all schemas just because the first application did not contain a table.
5. State cross-application scope explicitly in the final rationale.

Good explanation:

```text
Pierwsze sprawdzenie dotyczyło aplikacji `orders-service`, ale referencja wskazuje na dane utrzymywane przez `customer-service`.
```

---

## Raw SQL rule

Do not use `db_execute_readonly_sql` if the check can be expressed with:

- `db_exists_by_key`,
- `db_count_rows`,
- `db_group_count`,
- `db_check_orphans`,
- `db_join_count`,
- `db_sample_rows`.

Raw SQL is never the first DB tool.

If raw SQL is used, explain why typed tools were insufficient.
Put that explanation in the Polish `reason` argument.

---

## Evidence standard

Do not conclude "data issue" unless DB evidence confirms it.

Strong data conclusions usually need one of:

- direct missing row confirmed,
- key-only count differs from full predicate count,
- orphan reference confirmed,
- duplicate count confirmed,
- process/outbox state confirmed.

If DB checks look correct, say that data does not currently explain the incident and shift focus to code, runtime, integration, or visibility limits.

---

## DB result explanation style

In the final answer, explain DB findings in operator-friendly language.

Prefer:

```text
Rekord istnieje, ale aplikacja go nie widzi, bo nie spełnia filtra `STATUS=ACTIVE`.
```

over:

```text
COUNT(*) with predicate returned 0.
```

When reporting discovery results, mention application-to-schema resolution when it matters:

```text
Tabele były szukane w schemacie `ORDERS_APP`, dobranym z aplikacji/deploymentu `orders-service`.
```

Include technical identifiers where useful, but do not flood the result with raw rows.

---

## Anti-patterns

Do not:

- ask the user for environment,
- ask the user for Oracle schema/owner,
- ask the user for application name if it is already present in evidence,
- browse all schemas without a grounded reason,
- call `db_sample_rows` before count/group checks unless a concrete row ID is already known,
- call `db_execute_readonly_sql` as a shortcut for typed tools,
- diagnose a data issue without DB evidence,
- diagnose a schema issue before checking likely data causes,
- treat inferred relationships as declared foreign keys,
- dump raw DB rows into the final answer.
