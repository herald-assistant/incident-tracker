---
name: incident-data-diagnostics
description: Data-first diagnostic playbook for DB-related incidents using typed database tools.
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

## Session rule

The DB environment is session-bound and comes from hidden tool context.
Do not pass or invent environment as a DB tool argument.

Use the environment from manifest/prompt only for explanation in the final answer.

If DB tools are not available in the current session, do not pretend that DB data was verified.
State that the data hypothesis requires DB verification.

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

## Tool order

Use this order unless evidence clearly justifies a different path:

1. `db_get_scope`
   - only if DB alias or schema visibility is unclear.

2. `db_find_tables`
   - when exact table is unknown.

3. `db_find_columns`
   - when key/filter columns are unknown.

4. `db_describe_table`
   - for the most likely table candidate.
   - do not describe many tables blindly.

5. `db_exists_by_key`
   - for direct primary key or business key checks.

6. `db_count_rows`
   - for key-only and full-predicate checks.

7. `db_group_count`
   - for status, state, tenant, type, deleted, retry or error distribution.

8. `db_check_orphans`
   - for stale child/parent/reference problems.

9. `db_join_count`
   - for relation or repository join checks.

10. `db_join_sample`
- only after join count when a minimal example is useful.

11. `db_sample_rows`
- only for small, explicit, technical projections.

12. `db_compare_table_to_expected_mapping`
- only after data checks or when schema/mapping symptoms are explicit.

13. `db_execute_readonly_sql`
- last resort only.

## Minimal DB exploration principle

Do not browse the database.

Each DB call should answer one diagnostic question, for example:

- Does the row exist by ID?
- Does it exist by business key?
- Does it match the full repository predicate?
- Is it in another tenant/context?
- Is it inactive, deleted or expired?
- Does a child row reference a missing parent?
- Are there duplicate active rows?
- Is an outbox/event row stuck in a failed state?

## Core comparison: key-only vs full predicate

For "not found", empty result, repository lookup or entity loading symptoms, compare:

1. count by direct key/business key only,
2. count by full application predicate.

Typical interpretation:

```text
key-only count = 0
````

Possible explanations:

* missing test data,
* wrong ID/business key,
* wrong table candidate,
* wrong DB alias/scope,
* insufficient visibility.

```text
key-only count > 0
full-predicate count = 0
```

Strong data explanation:

* data exists but is excluded by tenant, status, soft-delete, validity, type or state predicate.

```text
full-predicate count > 0
application still fails
```

Data is less likely.
Focus on:

* wrong method arguments,
* wrong code path,
* mapping/conversion,
* transaction/cache/staleness,
* downstream/runtime problem.

## Pattern: EntityNotFound / missing entity

Use for:

* `EntityNotFoundException`,
* `JpaObjectRetrievalFailureException`,
* "Unable to find ... with id ...",
* lazy-loaded missing entity,
* missing dictionary/reference entity.

Procedure:

1. Extract from logs/code:

   * entity class,
   * ID or business key,
   * parent/child clue,
   * repository method,
   * tenant/context,
   * status/state clue.

2. If table is unclear:

   * use GitLab evidence or GitLab tools to map entity/repository,
   * use `db_find_tables`,
   * then `db_describe_table` for the best candidate.

3. Check direct existence:

   * use `db_exists_by_key`.

4. If the row is missing:

   * check whether another table references that missing ID,
   * use `db_check_orphans` when a child/parent relation is known,
   * otherwise use `db_find_relationships` or focused code reading.

5. If the row exists:

   * use `db_count_rows` with the full repository predicate:

      * tenant/context,
      * status/state,
      * soft delete,
      * validity date,
      * type/discriminator,
      * active flag.

6. Conclude only what DB evidence supports.

Possible conclusions:

* missing test data,
* stale/orphan reference,
* row exists but predicate excludes it,
* wrong tenant/context,
* inactive/soft-deleted/expired data,
* DB data looks correct, implementation/runtime issue more likely.

## Pattern: repository returns empty / business "not found"

Use for:

* `Optional.empty`,
* business 404,
* "not found" exception created by service logic,
* repository method returning no rows.

Procedure:

1. Identify repository method and predicates from logs/code.
2. Extract all filters:

   * key,
   * tenant,
   * status/state,
   * type,
   * soft delete,
   * validity dates,
   * active flag,
   * ownership/context.
3. Use `db_count_rows` by key only.
4. Use `db_count_rows` by full predicate.
5. Use `db_group_count` by status/tenant/deleted/state if full predicate returns zero.
6. Use `db_sample_rows` only for minimal technical columns.

Good final explanation:

```text
Rekord istnieje, ale nie spełnia predykatu repozytorium, ponieważ ...
```

Do not call it "missing entity" if the key-only count proves the row exists.

## Pattern: tenant or context mismatch

Use when evidence contains:

* tenant,
* organization,
* customer context,
* user context,
* account scope,
* permission scope,
* environment-specific context.

Procedure:

1. Extract tenant/context used by the failing flow.
2. Count by business key without tenant/context.
3. Count by business key with tenant/context.
4. Group by tenant/context if needed.
5. Sample only minimal columns if needed.

Interpretation:

```text
count without tenant > 0
count with tenant = 0
```

Likely:

```text
Dane istnieją, ale w innym kontekście niż użyty przez aplikację.
```

## Pattern: status, lifecycle, soft delete or validity mismatch

Use when evidence mentions:

* status,
* state,
* active/inactive,
* deleted flag,
* validity dates,
* lifecycle transition,
* current version.

Procedure:

1. Identify expected state from code/logs.
2. Count by key only.
3. Count by key + expected status/state/active/validity predicate.
4. Group by status/state/deleted/active.
5. Sample minimal technical columns if needed:

   * ID,
   * business key,
   * tenant/context,
   * status/state,
   * deleted/active,
   * validity dates,
   * updated timestamp.

Interpretation:

* row exists but has wrong status/state,
* row exists but is inactive or soft-deleted,
* row exists but is outside validity window.

## Pattern: orphan or stale reference

Use when evidence suggests:

* child references missing parent,
* missing dictionary/reference row,
* lazy loading failure,
* stale relation,
* FK-like relation without declared FK.

Procedure:

1. Identify child table and reference column.
2. Identify parent/reference table and key column.
3. Describe both tables only if needed.
4. Use `db_check_orphans`.
5. Use `db_join_count` or `db_join_sample` only if orphan check is insufficient.
6. Report child key and missing parent/reference key, not large row dumps.

Strong conclusion:

```text
Potwierdzono osieroconą referencję: rekord dziecka wskazuje na brakujący rekord nadrzędny/referencyjny.
```

Only use this when DB evidence confirms it.

## Pattern: duplicate or non-unique data

Use for:

* `NonUniqueResultException`,
* `IncorrectResultSizeDataAccessException`,
* duplicate key,
* unique constraint violation,
* unexpected multiple active rows.

Procedure:

1. Identify expected unique key and active predicate.
2. Use `db_count_rows` with the full predicate.
3. If count > 1, use `db_group_count` or minimal sample.
4. Explain whether duplicates are active, inactive, soft-deleted or historical.

Possible conclusion:

```text
Duplikaty aktywnych danych naruszają założenie unikalności repozytorium/usługi.
```

## Pattern: async/outbox/event/process stuck

Use when evidence mentions:

* outbox,
* event,
* message,
* scheduler,
* listener,
* consumer,
* retry,
* processing state,
* failed process row.

Procedure:

1. Extract:

   * correlation ID,
   * event ID,
   * aggregate/business ID,
   * message type,
   * processing state,
   * retry count,
   * error code,
   * timestamps.

2. Locate table:

   * use `db_find_tables` with hints such as `OUTBOX`, `EVENT`, `MESSAGE`, `PROCESS`, `JOB`, `TASK`.

3. Locate columns:

   * `CORRELATION_ID`,
   * `EVENT_ID`,
   * `AGGREGATE_ID`,
   * `STATUS`,
   * `STATE`,
   * `RETRY_COUNT`,
   * `ERROR_CODE`.

4. Count by correlation/event/business ID.

5. Group by processing state or error code.

6. Sample minimal technical columns if needed.

Possible conclusions:

* event was not created,
* event exists but is stuck,
* retry was exhausted,
* process data points to downstream failure,
* DB process state looks normal, so runtime/downstream should be checked.

## Raw SQL rule

Do not use `db_execute_readonly_sql` if the check can be expressed with:

* `db_exists_by_key`,
* `db_count_rows`,
* `db_group_count`,
* `db_check_orphans`,
* `db_join_count`,
* `db_sample_rows`.

Raw SQL is never the first DB tool.

If raw SQL is used, explain why typed tools were insufficient.

## Evidence standard

Do not conclude "data issue" unless DB evidence confirms it.

Strong data conclusions usually need one of:

* direct missing row confirmed,
* key-only count differs from full predicate count,
* orphan reference confirmed,
* duplicate count confirmed,
* process/outbox state confirmed.

If DB checks look correct, say that data does not currently explain the incident and shift focus to code, runtime, integration, or visibility limits.

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

Include technical identifiers where useful, but do not flood the result with raw rows.
