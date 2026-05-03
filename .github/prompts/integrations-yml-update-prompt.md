# integrations.yml update prompt

Update only `integrations.yml`.

This prompt is schema-authoritative for `integrations.yml`. If a parent operational-context prompt is also provided, follow the parent prompt for workflow, source-scope handling, discovery reports, reconciliation, response orchestration and sidecar outputs, but use this prompt as the authoritative schema, extraction policy, merge policy, quality gate and output contract for `integrations.yml`.

Do not preserve legacy fields or legacy structures unless they are explicitly represented in this schema.

## Purpose

Maintain `integrations.yml` as an enterprise-grade, evidence-backed, queryable map of operational integration contracts.

`integrations.yml` is part of a reusable operational context catalog. It is not only for incident routing. It is an integration-contract graph layer used for:

- deterministic mapping from runtime, code, configuration, deployment, data and documentation evidence to integration contracts;
- GitLab/code search scope construction across service repositories, shared libraries, generated clients, schema repositories, gateway configuration and deployment/config repositories;
- explaining integration behavior to users and LLM tools;
- function description and user-facing answers about affected capabilities, endpoints, messages, events, data sources and external dependencies;
- dependency, impact and change analysis across systems, runtime components, repositories, modules, processes, bounded contexts, terms, data models and teams;
- DB/code grounding when data-source, persistence, datasource, schema, table or replicated-data symptoms are involved;
- incident triage and coordination as one downstream view, without reducing responsibility to a single owner;
- repository onboarding, follow-up investigation, Q&A and future AI analysis features that need system relationship context.

An integration entry should explain:

- which operational graph nodes participate in the contract;
- what the contract does operationally;
- what business, technical or data behavior crosses the boundary;
- where the contract appears in code, configuration, deployment, APIs, messaging, files, databases, runtime evidence or documentation;
- which repositories, modules, shared libraries, generated clients, schema repositories or gateway/broker configuration implement or expose it;
- which deterministic signals identify it;
- which responsibilities are explicit, shared, external, unknown or disputed;
- which failure patterns are operationally useful;
- how an LLM should explain, search for and disambiguate it;
- what remains unknown after durable validation.

## Non-goals

Do not turn `integrations.yml` into:

- a full API specification;
- a full message schema catalog;
- a complete ESB, broker, gateway or network inventory;
- a vendor catalog;
- an endpoint dump;
- a process catalog;
- a repository map;
- a team ownership matrix;
- a long architecture essay;
- an incident escalation playbook;
- a scratchpad for temporary agent uncertainty.

Do not model every host, endpoint variant, queue binding, routing key, generated DTO, schema class, table, environment URL or configuration key as a separate integration.

Create a separate integration entry only when the difference changes at least one of:

- operational contract meaning;
- source, target, final target or mediator participant;
- protocol, transport or channel type;
- synchronous versus asynchronous behavior;
- bounded context or process boundary;
- failure mode or runtime behavior;
- deterministic mapping signals;
- repository/code search scope;
- DB/code grounding scope;
- coordination or handoff behavior;
- responsibility model;
- external owner or support path;
- downstream analysis behavior.

## Inputs

The agent receives:

- `CURRENT FILE`: the current content of `integrations.yml`. It may be empty, legacy or schema version 1.
- `NEW FACTS`: repository scan results, documentation fragments, runtime evidence, database discovery, deployment/config evidence, existing operational-context facts, discovery report entries or human-provided facts.
- Optional `SCAN SCOPE`: the repository, generated client, shared library, schema repository, deployment/config repository, documentation fragment, branch, module or path range that was actually analyzed.
- Optional `FULL OPERATIONAL CONTEXT`: current snapshot or summary of other operational context files, such as `systems.yml`, `runtime-components.yml`, `repo-map.yml`, `processes.yml`, `bounded-contexts.yml`, `teams.yml`, `glossary.md` or `terms.yml`, `handoff-rules.md` and `operational-context-index.md`.
- Optional `BUILD MEMORY`: temporary cross-repository build memory for scan-order uncertainty, pending joins, candidate contracts, partial facts and unresolved references. Use it during reasoning only unless the parent prompt explicitly asks for a separate sidecar output.

## Output

Return the full updated `integrations.yml` YAML only.

Do not include Markdown fences.
Do not include explanations.
Do not include diffs.
Do not include a discovery report unless the parent prompt explicitly requests a separate sidecar output.
Do not update any file other than `integrations.yml`.
Do not output `BUILD MEMORY` inside `integrations.yml`.

The final YAML must parse successfully.

## Required top-level schema

Use this exact top-level shape. Do not preserve legacy top-level fields.

```yaml
schemaVersion: 1
catalogKind: operational-context-integrations
integrations: []
gaps: []
```

Top-level order must be:

1. `schemaVersion`
2. `catalogKind`
3. `integrations`
4. `gaps`

Use `gaps`, not legacy untyped `openQuestions`. Durable unresolved questions are represented as typed gaps.

## Required integration entry schema

Each integration entry must use this structure and field order. Keep required empty lists as `[]` when there is no confirmed value. Use `null` for unknown scalar values. Empty optional fields inside `matchSignals` buckets may be omitted.

```yaml
- id: stable-integration-id
  name: Human readable integration name
  category: internal-api | external-api | messaging | event-stream | database | file-transfer | notification | auth | storage | gateway-route | batch | scheduler-triggered | composite | unknown
  lifecycleStatus: active | planned | deprecated | retired | external | unknown
  summary: Short local description of the operational contract.
  integrationStyle: synchronous-request | async-message | event-stream | webhook | batch-file | database | identity-auth | gateway-mediated | composite | unknown
  flowDirection: source-to-target | target-to-source | bidirectional | fanout | request-response | internal | unknown
  criticality: critical | high | medium | low | unknown
  dataSensitivity: public | internal | confidential | restricted | unknown
  responsibilityStatus: explicit-single | explicit-multiple | shared | unresolved | disputed | external | platform-shared | not-applicable | unknown
  useFor: []
  participants:
    source:
      system: string | null
      runtimeComponent: string | null
      boundedContext: string | null
      repositories: []
      modules: []
      role: client | server | producer | consumer | publisher | subscriber | provider | receiver | scheduler | gateway | mediator | transport | auth-provider | data-source | data-sink | file-sender | file-receiver | unknown
      externalOwner: string | null
      notes: []
    targets: []
    intermediaries: []
    finalTargets: []
  contract:
    purpose: Short purpose of the integration.
    operations: []
    messages: []
    dataObjects: []
    events: []
    schemas: []
    versioning:
      strategy: uri-version | header-version | schema-version | media-type-version | none | unknown
      current: string | null
    auth:
      type: oauth2-client-credentials | oauth2-auth-code | api-key | mtls | basic | jwt | oidc | ldap | none | unknown
      configKeys: []
      authSignals: []
    consistency: synchronous | eventual | mixed | unknown
    idempotency: required | supported | not-supported | unknown
    retryPolicy:
      type: none | client-retry | broker-redelivery | scheduled-retry | circuit-breaker | manual-replay | unknown
      configKeys: []
      retrySignals: []
  channels: []
  transport:
    protocols: []
    http:
      methods: []
      endpointPrefixes: []
      endpointTemplates: []
      operationNames: []
      hosts: []
      hostPatterns: []
      baseUrlConfigKeys: []
      clientNames: []
      gatewayRoutes: []
    messaging:
      brokers: []
      virtualHosts: []
      exchanges: []
      queues: []
      topics: []
      routingKeys: []
      bindings: []
      dlqs: []
      retryQueuesOrTopics: []
      consumerGroups: []
      partitionKeys: []
    database:
      datasourceNames: []
      connectionNames: []
      hikariPoolMarkers: []
      schemas: []
      tables: []
      entities: []
      repositories: []
      operations: []
    file:
      protocols: []
      hosts: []
      locations: []
      pathPrefixes: []
      filenamePatterns: []
      buckets: []
      shares: []
      scheduleSignals: []
    observability:
      spans: []
      metrics: []
      logMarkers: []
      exceptionClasses: []
      errorCodes: []
      timeoutMarkers: []
      retryMarkers: []
  references:
    systems: []
    runtimeComponents: []
    repositories: []
    modules: []
    processes: []
    boundedContexts: []
    integrations: []
    terms: []
    teams: []
    handoffRules: []
  implementation:
    localSide: client | server | producer | consumer | publisher | subscriber | scheduler | gateway | mediator | mixed | unknown
    clientTypes: []
    packagePrefixes: []
    modulePaths: []
    classHints: []
    clientClasses: []
    controllerClasses: []
    listenerClasses: []
    publisherClasses: []
    schedulerClasses: []
    generatedClientClasses: []
    configClasses: []
    configKeys: []
    artifactCoordinates: []
    sharedLibraries: []
    generatedClients: []
    schemaRepositories: []
    gatewayOrBrokerConfig: []
    notes: []
  responsibilities: []
  matchSignals:
    exact: {}
    strong: {}
    medium: {}
    weak: {}
  failureModes: []
  relations: []
  observability:
    dashboards: []
    alerts: []
    metrics: []
    traces: []
    logs: []
    healthChecks: []
    dlqOrRetrySignals: []
  analysisHints:
    codeSearch: []
    impactAnalysis: []
    functionDescription: []
    incidentTriage: []
    dbGrounding: []
    qa: []
  handoffHints:
    defaultRoute: []
    requiredEvidence: []
    firstActions: []
    escalationTriggers: []
  llmToolHints:
    answerWhenUserMentions: []
    disambiguationHints: []
    commonMisreads: []
    usefulSearchKeywords: []
    explanationStyle: null
  evidence: []
  sourceCoverage:
    status: complete | partial | unknown
    scannedSources: []
    expectedSources: []
    limitations: []
```

## Required gap schema

Durable gaps must use this structure and field order.

```yaml
- id: stable-gap-id
  type: responsibility-ambiguity | source-target-ambiguity | missing-reference | contract-ambiguity | conflicting-evidence | external-owner-unknown | topology-ambiguity | consumer-producer-ambiguity | mapping-signal-missing | source-coverage-incomplete | human-confirmation-required
  severity: high | medium | low
  status: open | needs-human | resolved | superseded
  summary: Short durable catalog gap summary.
  affectedNodes: []
  impact: []
  requiredEvidence: []
  evidence: []
```

## Field semantics

### `id`

Use stable kebab-case identifiers. Reuse an existing id when the integration already exists.

Prefer ids that encode source, target and contract meaning:

- `crm-core-to-email-platform-send-message`
- `crm-core-customer-profile-events`
- `integration-gateway-to-billing-customer-sync`
- `campaign-service-email-bounce-webhook`
- `crm-core-to-identity-provider-token`

Avoid ids that are too generic:

- `email-api`
- `sync`
- `queue1`
- `partner-integration`
- `crm-core-to-external`

Do not rename existing ids unless the current id is clearly invalid, the replacement is obvious and no cross-file reference will be broken.

### `name`

Use a human-readable name that an engineer, operator or LLM answer can recognize.

### `category`

Use the dominant integration category:

- `internal-api`: API contract between internal systems or runtime components.
- `external-api`: API contract with an external system, vendor, partner or third-party platform.
- `messaging`: queue, exchange, topic or command message contract.
- `event-stream`: stream/backbone event contract where offset, lag, partitioning or replay matters.
- `database`: direct database/schema/table/datasource integration crossing a system or context boundary.
- `file-transfer`: SFTP, SMB, FTP, object storage, file drop, import/export file contract.
- `notification`: email, SMS, push or notification-provider integration.
- `auth`: identity, token, OIDC, OAuth2, LDAP or AD integration.
- `storage`: object/document storage dependency used as an integration boundary.
- `gateway-route`: API gateway, integration gateway, ESB, route or mediation contract.
- `batch`: scheduled batch contract where batch execution is central.
- `scheduler-triggered`: integration primarily identified by a scheduled job trigger.
- `composite`: one operational contract intentionally combines several categories and cannot be split without losing meaning.
- `unknown`: evidence shows a cross-boundary contract but category is not yet clear.

### `lifecycleStatus`

Use:

- `active`: currently used in production or active development.
- `planned`: documented future integration.
- `deprecated`: still visible in code/logs/config/docs but should be replaced.
- `retired`: no longer active but retained because historical evidence can still refer to it.
- `external`: contract mostly lives outside the organization but is operationally visible.
- `unknown`: evidence exists but lifecycle state is not confirmed.

### `summary`

Write one short sentence describing the integration operationally.

Good:

```yaml
summary: CRM API sends customer notification requests to the external email platform when a customer profile changes.
```

Bad:

```yaml
summary: Integration with email.
```

Do not hide concrete match keys, routes, queues or class names in the summary when structured fields can hold them.

### `integrationStyle`

Allowed values:

- `synchronous-request`: request/response call such as REST, SOAP, gRPC or GraphQL.
- `async-message`: queue/topic/exchange message contract.
- `event-stream`: event backbone, stream or event bus contract.
- `webhook`: inbound or outbound callback endpoint.
- `batch-file`: scheduled file/object-storage/SFTP/SMB exchange.
- `database`: direct or replicated data-source access.
- `identity-auth`: authentication, authorization, token or identity-provider contract.
- `gateway-mediated`: operational contract whose behavior depends on a mediator, API gateway, ESB or protocol translator.
- `composite`: one operational contract intentionally combines multiple styles.
- `unknown`: evidence exists but style is not clear.

### `flowDirection`

Use:

- `source-to-target`: one-way source to provider/target.
- `target-to-source`: primary operational perspective is inbound from target to source.
- `bidirectional`: both sides initiate under the same contract.
- `fanout`: one producer publishes to multiple known or unknown consumers.
- `request-response`: synchronous calls where response semantics matter.
- `internal`: contract stays inside one deployable system or platform but crosses module/context boundaries and is operationally important.
- `unknown`: direction cannot be safely inferred.

For async messaging, producer/publisher is usually `participants.source`, consumers are usually `participants.targets`, and the broker is usually an intermediary, not the target system.

### `criticality`

Use operational impact, not team preference:

- `critical`: failure blocks a core product/process or a major external dependency.
- `high`: failure affects important flows but has workaround or degradation.
- `medium`: failure affects a specific capability or bounded context.
- `low`: auxiliary/non-core.
- `unknown`: no evidence.

Do not infer high criticality only because an integration exists.

### `dataSensitivity`

Use the most restrictive known category:

- `public`
- `internal`
- `confidential`
- `restricted`
- `unknown`

Do not invent sensitivity. Use `unknown` if not evidenced.

Never store secrets, credentials, tokens, authorization headers, sample customer records, personal data or full sensitive payloads.

### `responsibilityStatus`

Use:

- `explicit-single`: one accountable responsibility is explicitly documented.
- `explicit-multiple`: multiple responsibilities are explicitly documented.
- `shared`: responsibility is intentionally shared across source, target, mediator, platform or external parties.
- `unresolved`: responsibility matters but is not known.
- `disputed`: conflicting evidence exists.
- `external`: external owner/provider is responsible for part or all of the contract.
- `platform-shared`: platform, broker, gateway, DB or runtime team shares responsibility.
- `not-applicable`: responsibility is not meaningful for this catalog entry.
- `unknown`: no responsibility evidence.

Do not force single ownership.

### `useFor`

Non-empty list when the integration can support analysis with evidence. Allowed values:

- `deterministic-mapping`
- `code-search`
- `function-description`
- `impact-analysis`
- `db-grounding`
- `incident-analysis`
- `qa`

Use only values supported by the entry's evidence and signals.

### `participants`

Use `participants.source` for the initiating or producing side.
Use `participants.targets` for one or more target, consumer, subscriber, provider, file receiver or data-owner sides.
Use `participants.intermediaries` for brokers, gateways, ESBs, identity providers, queues, topics, protocol translators, network/platform elements or databases that are operationally relevant.
Use `participants.finalTargets` for mediated integrations where the immediate target differs from the semantic final target.

Participant shape:

```yaml
system: crm-api
runtimeComponent: crm-api-runtime
boundedContext: customer-profile
repositories:
  - crm-api-repo
modules:
  - crm-api-repo/customer-profile-module
role: producer
externalOwner: null
notes: []
```

Participant `role` values:

- `client`
- `server`
- `producer`
- `consumer`
- `publisher`
- `subscriber`
- `provider`
- `receiver`
- `scheduler`
- `gateway`
- `mediator`
- `transport`
- `auth-provider`
- `data-source`
- `data-sink`
- `file-sender`
- `file-receiver`
- `unknown`

Rules:

- `system`, `runtimeComponent`, `boundedContext`, `repositories` and `modules` should use stable catalog IDs when known.
- A participant `system` may be `null` only when the participant is evidenced but not identified.
- If the uncertainty may be resolved by scanning another known repository, record it in `BUILD MEMORY`, not final `gaps`.
- If the uncertainty is durable after expected evidence was checked, add a `gaps` entry.
- Do not invent source, target, mediator or final target to make the graph look complete.

### `contract`

Use short, operationally useful facts about what crosses the boundary.

Operation shape:

```yaml
- id: send-notification
  method: POST
  path: /v1/messages
  description: Sends a customer notification request.
  requestObjects: [NotificationRequest]
  responseObjects: [NotificationResponse]
```

Message shape:

```yaml
- id: customer-profile-updated
  direction: published
  schema: CustomerProfileUpdated
  eventType: customer.profile.updated
  routingKey: customer.profile.updated
```

Data object shape:

```yaml
- name: CustomerProfileSnapshot
  role: request | response | event-payload | file-record | database-row | unknown
  localMeaning: Snapshot of customer profile data exchanged with the target platform.
```

Do not dump entire schemas, field lists, payload samples or DTO walkthroughs here. Store only names and short meanings useful for mapping and analysis.

### `channels`

Use `channels` when a contract has multiple operational channels, mediated hops or a need for per-channel details. A simple integration may keep `channels: []` when `transport` already captures all confirmed facts.

Channel structure:

```yaml
- id: stable-channel-id
  kind: sync-endpoint | async-producer | async-consumer | event-stream | webhook-inbound | webhook-outbound | file-outbound | file-inbound | database-read | database-write | scheduler-trigger | mediated-hop | auth-token | unknown
  transport: rest | http | soap | grpc | graphql | amqp | kafka | jms | stream | sftp | smb | ftp | object-storage | jdbc | odbc | oidc | oauth2 | ldap | smtp | file | mixed | unknown
  direction: outbound | inbound | bidirectional | internal | unknown
  description: Short channel description.
  endpoint:
    methods: []
    pathTemplates: []
    pathPrefixes: []
    hostPatterns: []
    baseUrlConfigKeys: []
    authSignals: []
  messaging:
    broker: null
    virtualHost: null
    exchanges: []
    topics: []
    queues: []
    routingKeys: []
    eventNames: []
    schemaNames: []
    producerClasses: []
    consumerClasses: []
    dlqNames: []
    retrySignals: []
  fileTransfer:
    locations: []
    filenamePatterns: []
    scheduleSignals: []
  database:
    connectionNames: []
    schemas: []
    tables: []
    operations: []
    hikariPools: []
  code:
    clientClasses: []
    listenerClasses: []
    publisherClasses: []
    controllerClasses: []
    schedulerClasses: []
    configClasses: []
    generatedClientClasses: []
  failureSignals:
    exceptionClasses: []
    errorCodes: []
    logMarkers: []
    timeoutMarkers: []
```

Rules:

- Use stable channel ids.
- Do not create one channel per environment variant if the contract is the same.
- Do not use `channels` to duplicate every value already summarized in `transport`; use it for per-hop or per-channel semantics.
- If a mediator can fail independently, has separate logs, has its own owner/support path or has a separate repository/config, model the mediator clearly through `participants.intermediaries`, `channels` and possibly separate integration entries.

### `transport`

Use `transport` as the normalized deterministic transport/routing layer. It may aggregate signals across channels.

Allowed `transport.protocols` examples:

- `REST`
- `HTTP`
- `SOAP`
- `GraphQL`
- `gRPC`
- `AMQP`
- `Kafka`
- `JMS`
- `SFTP`
- `SMB`
- `FTP`
- `object-storage`
- `SMTP`
- `JDBC`
- `ODBC`
- `OIDC`
- `OAuth2`
- `LDAP`
- `file`
- `unknown`

Keep endpoint paths and path templates quoted when they contain `{}`, `:`, `?`, `&`, `#` or YAML-sensitive characters.

Good:

```yaml
endpointTemplates:
  - "/api/customers/{customerId}/notifications"
```

### `references`

Use only stable canonical catalog IDs.

Rules:

- `systems` should include source, target and operationally important intermediary systems when known.
- `runtimeComponents` should include deployed components where the integration runs or where evidence appears.
- `repositories` should include service repositories, shared library repositories, generated client repositories, schema repositories and gateway/broker/deployment config repositories when they are part of the search or evidence scope.
- `modules` should include precise modules when known.
- `processes` should include business or operational processes where this contract matters.
- `boundedContexts` should include source, target or cross-context semantic areas.
- `integrations` may link related or component integrations, especially for mediated or composite contracts.
- `terms` should reference glossary/terms IDs used to explain local vocabulary.
- `teams` should reference teams only when there is evidence of responsibility, stewardship, participation, support or coordination.
- `handoffRules` should reference incident-specific routing views only when a canonical handoff rule already exists.

Do not introduce dangling references. If a referenced node is missing and cannot be added in the current target file, either:

- record it in `BUILD MEMORY` as a pending cross-file/cross-repo reference, or
- add a durable `missing-reference` gap only when the missing node is not a temporary scan-order issue.

### `implementation`

Use this section for where the integration appears in code, build artifacts, generated clients, shared libraries and configuration.

Allowed `clientTypes` examples:

- `feign`
- `webclient`
- `rest-template`
- `openapi-generated-client`
- `soap-generated-client`
- `grpc-stub`
- `graphql-client`
- `rabbit-template`
- `kafka-template`
- `spring-cloud-stream`
- `jdbc-template`
- `jpa-repository`
- `sftp-client`
- `smtp-client`
- `oidc-client`
- `oauth2-client`

Rules:

- Include package prefixes, classes, modules and config keys that are useful for code search.
- Put generated clients in `generatedClients` and `generatedClientClasses`.
- Put shared libraries in `sharedLibraries` and their repositories/modules in `references.repositories` / `references.modules` when known.
- Do not assume a shared library or generated client repository owns the consuming runtime behavior.
- Do not invent repository identity from a package name alone.

### `responsibilities`

Use role-based responsibility. Do not force single ownership.

Responsibility shape:

```yaml
- teamId: string | null
  role: source-side | target-side | contract-steward | runtime-operator | broker-support | gateway-support | network-support | identity-provider-support | database-support | shared-library-maintainer | generated-client-maintainer | schema-maintainer | business-owner | external-provider | first-responder | participant | unknown
  scope: Short scope of responsibility.
  evidence: explicit-doc | explicit-code | explicit-config | inferred-runtime | human-input | external-doc | human-input-required
  confidence: high | medium | low
```

Rules:

- Participation is not ownership.
- A producer, consumer, worker or repository maintainer may be only a participant.
- Do not infer accountability from package names, repository names, author names, directory names or the fact that one side of the integration is visible in the current repository.
- Do not assign business responsibility to a shared library maintainer unless explicit evidence says so.
- Use `teamId: null`, `role: unknown`, `evidence: human-input-required`, `confidence: low` when responsibility is unknown and meaningful.
- Add a durable gap only when the missing responsibility affects deterministic mapping, analysis, handoff, topology or semantic interpretation at catalog level.

### `matchSignals`

`matchSignals` is the deterministic mapping layer. Do not hide match keys in prose.

Group signals by strength.

Recommended structure:

```yaml
matchSignals:
  exact:
    integrationIds: []
    explicitNames: []
    endpointTemplates: []
    queues: []
    topics: []
    exchanges: []
    routingKeys: []
    eventNames: []
    operationNames: []
    schemaNames: []
    baseUrlConfigKeys: []
  strong:
    serviceNames: []
    runtimeComponents: []
    deploymentNames: []
    containerNames: []
    applicationNames: []
    artifactNames: []
    repositoryNames: []
    modulePaths: []
    packagePrefixes: []
    clientClasses: []
    controllerClasses: []
    listenerClasses: []
    publisherClasses: []
    generatedClientClasses: []
    hostPatterns: []
    endpointPrefixes: []
    consumerGroups: []
    dbSchemas: []
    dbTables: []
    datasourceNames: []
    hikariPools: []
    configKeys: []
    exceptionClasses: []
    errorCodes: []
    logMarkers: []
  medium:
    classNames: []
    methodNames: []
    dtoNames: []
    entityNames: []
    scheduledJobs: []
    spanNames: []
    metricNames: []
    filePathPatterns: []
    dashboardNames: []
    alertNames: []
  weak:
    terms: []
    aliases: []
    operatorLabels: []
    documentationLabels: []
```

Rules:

- Include only stable, operationally useful signals.
- Do not put prose into signal lists.
- Do not include secrets, credentials, tokens, exact sensitive hostnames, customer data, personal data or sensitive payloads.
- Prefer exact endpoint templates, queue/topic/exchange/routing-key names, operation names, event names, schema names, base URL config keys and generated client names when they exist.
- Use weak signals only as supporting context, never as the sole basis for a new integration.
- Do not create an integration without at least one exact or strong signal unless `sourceCoverage.status: partial` and a durable gap explain why the entry is still useful.

### `failureModes`

List operationally useful failure patterns, not every possible exception.

Failure mode shape:

```yaml
- id: email-provider-timeout
  name: Email provider timeout
  symptoms: []
  likelySide: source | target | intermediary | broker | gateway | database | network | external | platform | contract | unknown
  matchSignals: []
  requiredEvidence: []
  firstAnalysisSteps: []
```

Useful failure modes include:

- timeout to external API;
- SOAP fault from final target behind a mediator;
- message serialization failure;
- consumer lag or DLQ growth;
- missing webhook signature;
- database connection pool exhaustion;
- generated client contract mismatch;
- retry exhaustion;
- partial batch import failure;
- token acquisition failure;
- gateway route mismatch.

Rules:

- Keep failure modes short.
- Include only failure modes that help deterministic mapping, code search, impact analysis, DB grounding, incident triage or coordination.
- Do not turn this section into a runbook.

### `relations`

Use relations to connect this integration to other graph nodes.

Relation shape:

```yaml
- target: process:customer-onboarding
  type: supports | depends-on | publishes-to | consumes-from | calls | receives-from | authenticates-via | persists-to | mediated-by | final-target-of | shares-schema-with | triggers | unknown
  via: []
  description: Short relation description.
  confidence: high | medium | low
```

Rules:

- Prefer `target` values with explicit prefixes, such as `system:<id>`, `integration:<id>`, `process:<id>`, `bounded-context:<id>`, `repository:<id>`, `team:<id>` or `term:<id>`.
- Do not create relations just because two integrations appear in the same repository.
- Create a relation when evidence shows dependency, call flow, event flow, shared schema, mediation, gatewaying, authentication, persistence, process support, semantic coupling or operational coordination.

### `observability`

Add only documented or clearly observable signals. Do not invent dashboard, alert or metric names.

Use `dlqOrRetrySignals` for DLQs, retry queues, retry topics, retry counters, dead-letter events, idempotency markers, replay signals or scheduled retry markers.

This section is for compact diagnostic signals, not a full observability catalog.

### `analysisHints`

Use compact reusable hints for downstream analysis.

- `codeSearch`: where and how to search code.
- `impactAnalysis`: what may be affected when the integration fails or changes.
- `functionDescription`: how to describe the affected function in user-facing language.
- `incidentTriage`: evidence useful for incident triage, without full handoff rules.
- `dbGrounding`: entity/table/schema/datasource hints when the integration involves persistence or data-source access.
- `qa`: how to explain the integration plainly.

Do not encode deterministic routing policy here.

### `handoffHints`

`handoffHints` is not ownership and not a full incident routing view.

Use it only for compact reusable coordination hints:

- `defaultRoute`: role labels or canonical teams to consider first when evidence supports them.
- `requiredEvidence`: concrete evidence needed before useful escalation.
- `firstActions`: short checks that are safe and generally useful.
- `escalationTriggers`: when to involve partner, platform, gateway, broker, DB, identity provider, external owner or mediator owner.

Detailed handoff, on-call logic and escalation policy belong to incident-specific routing views, not to `integrations.yml`.

### `llmToolHints`

Use short hints that help an LLM answer questions, explain the integration or decide what to search next.

Good hints:

- distinguish gateway errors from final target errors;
- explain that one service produces the message and another consumes it;
- warn that generated client classes live in a shared client repository;
- tell the LLM which exact signals are most discriminating;
- explain that a database table is a cross-system replicated dependency, not an internal entity;
- explain which terms should not be confused.

Do not put long diagnostic playbooks here.

### `evidence`

Every added or updated fact must be grounded in evidence.

Evidence shape:

```yaml
- source: code:src/main/java/com/example/crm/customer/EmailNotificationClient.java
  observation: EmailNotificationClient sends POST requests to the email provider API.
  confidence: high
```

Allowed source prefixes:

- `code:`
- `config:`
- `doc:`
- `test:`
- `deployment:`
- `runtime:`
- `build:`
- `catalog:`
- `human:`
- `unknown:` only when preserving an existing fact with no available source.

Rules:

- Use `confidence: high` for facts directly grounded in code, config, docs or runtime evidence.
- Use `confidence: medium` for facts confirmed by one reliable but partial source.
- Use `confidence: low` for weak or incomplete evidence that is still worth preserving.
- Do not add high-confidence claims from naming alone.
- Preserve existing confirmed facts unless explicit contradictory evidence exists.

### `sourceCoverage`

Use this to prevent overconfidence.

- `complete`: expected relevant sources for this contract were scanned.
- `partial`: only some known relevant sources were scanned.
- `unknown`: source coverage cannot be assessed.

`scannedSources` should include repositories, modules, documentation fragments, deployment/config sources, specs or runtime evidence actually inspected.

`expectedSources` may include consumer repository, provider repository, generated client repository, shared library repository, schema repository, deployment/config repository, gateway/broker configuration, external owner docs or team support docs expected to clarify the integration.

`limitations` must explain partial evidence, missing counterpart, missing source/target, missing external owner, missing responsibility, ambiguous mediation, or source-scope limits without turning the final file into a scratchpad.

## Core extraction principles

### 1. Integrations are operational contracts

Treat each integration as a first-class contract node or edge in the operational graph.

An integration may connect:

- internal systems;
- external systems;
- runtime components;
- repositories and modules;
- shared libraries and generated clients;
- schema repositories;
- bounded contexts;
- business or operational processes;
- terms from the glossary;
- teams and external owners;
- platform infrastructure such as brokers, gateways, identity providers, databases or object storage.

Do not model an external system itself as an integration. External systems are graph nodes. Integrations are the calls, channels, event streams, webhooks, data-source contracts, auth flows or file exchanges that connect nodes.

### 2. One entry per meaningful contract

Create one entry per meaningful operational contract, not per arbitrary host, URL, queue, topic, class, DTO, file path or environment.

Merge facts into one entry when they represent the same contract:

- several endpoints under one API resource contract;
- request and response classes for the same synchronous call;
- several routing keys under one event contract;
- several environment-specific hosts for one target service;
- generated client and consuming service code for the same call;
- shared library implementation and runtime service usage for the same contract;
- gateway route and consuming service call when the gateway does not have independent operational meaning.

Split into separate entries when contract meaning, participants, runtime behavior, responsibility, code search scope or failure/coordination behavior differs materially.

### 3. Responsibilities are not single ownership

Do not force a single owner.

An integration may have:

- source-side responsibility;
- target-side responsibility;
- contract stewardship;
- runtime operation responsibility;
- broker, gateway, network, identity-provider or database support;
- shared library maintenance;
- generated client maintenance;
- schema maintenance;
- business responsibility;
- external provider responsibility;
- first-responder coordination;
- no known responsibility yet.

Use `responsibilityStatus` and `responsibilities`, not legacy `ownerTeamId`, `partnerTeamIds` or `handoff.target`.

### 4. Routing is only a downstream view

Do not encode incident routing as the core truth of an integration.

Use `analysisHints.incidentTriage` and `handoffHints` only for compact hints that downstream incident analysis may use. Detailed routing, escalation and on-call logic belongs to an incident-specific routing view.

### 5. Deterministic mapping is more important than prose

Prefer concrete match signals over long descriptions:

- service, deployment, application and container names;
- GitLab project or repository names;
- artifact coordinates and module paths;
- package prefixes;
- client, listener, controller, publisher, scheduler and gateway class names;
- endpoint paths, templates and prefixes;
- HTTP methods and operation names;
- hosts, host patterns and base URL config keys;
- queues, topics, exchanges, routing keys, bindings, DLQs and consumer groups;
- message schema names and event names;
- OpenAPI, AsyncAPI, WSDL, XSD, GraphQL or protobuf operation names;
- database schemas, tables, entities, datasources and connection pool markers;
- file shares, object storage buckets and path prefixes;
- configuration property prefixes;
- exception classes and error markers;
- metric, span, dashboard and alert names;
- retry, circuit-breaker and timeout markers;
- recurring operator labels.

### 6. Multi-repository scans are partial by default

The current repository or documentation fragment is only a partial evidence source.

Integration facts may be split across:

- consuming service repositories;
- providing service repositories;
- mediator/gateway repositories;
- shared client library repositories;
- generated client repositories;
- integration module repositories;
- message schema repositories;
- database migration/model repositories;
- deployment/config repositories;
- API gateway, broker or service discovery configuration repositories;
- upstream or downstream service repositories;
- team-owned documentation;
- external owner or vendor documentation.

Never infer that an integration, endpoint, message, participant, responsibility, repository, module, schema, contract or failure mode does not exist only because it is absent from the current source.

Do not remove, downgrade or overwrite existing confirmed catalog facts only because they are not visible in the current scan.

Temporary scan-order uncertainty must go to `BUILD MEMORY`, not final `gaps`, when it may be resolved by scanning another known source.

### 7. Shared library and generated client evidence is not consumer ownership

A shared library, generated client or integration module may contain the failing class without owning the business behavior.

When the current source is a shared library or generated client:

- capture package prefixes, classes, module names, artifact coordinates and generated-client names;
- represent the library in `implementation.sharedLibraries` or `implementation.generatedClients`;
- link repositories and modules when known;
- do not assume the library repository owns the consuming runtime behavior;
- record consuming systems, processes or contexts as pending cross-repo joins in `BUILD MEMORY` unless explicitly known.

When the current source is a consuming service:

- capture client usage, configuration keys, endpoint usage and process/context evidence;
- link to existing shared library or generated client entries when evidence supports the join;
- do not invent library repository identity if it is not known.

### 8. Durable gaps only

Final `gaps` represent durable catalog gaps after available evidence has been checked, or issues requiring human/domain input.

Use final `gaps` for:

- unresolved responsibility after support/ownership evidence was checked;
- unclear source or target after expected repositories/configuration were checked;
- missing referenced catalog node after expected sources were checked;
- missing exact/strong mapping signal for an otherwise important contract;
- conflicting protocol, topology or contract definitions;
- ambiguous contract split/merge decision that affects analysis;
- unclear producer/consumer topology;
- missing external owner/vendor contact that cannot be inferred from code;
- unclear contract semantics that require human or domain confirmation.

Do not use final `gaps` for temporary cross-repo joins that are still pending in build memory.

## Required discovery procedure

Before editing, inspect the provided repository/documentation/runtime evidence and classify candidate integration facts.

### Discover integrations from code

Look for:

- REST clients: `@FeignClient`, `RestTemplate`, `WebClient`, generated OpenAPI clients, HTTP wrappers and API adapters;
- inbound integration endpoints: controllers, API routes, webhooks and callback endpoints;
- SOAP clients: WSDL files, generated SOAP clients, XML marshalling classes and SOAP gateways;
- gRPC clients/stubs and protobuf services;
- GraphQL clients and schemas;
- messaging consumers/producers: `@RabbitListener`, `@KafkaListener`, `KafkaTemplate`, `RabbitTemplate`, `StreamBridge`, Spring Cloud Stream bindings and channel interfaces;
- event handlers and domain event publishers;
- batch, scheduled or worker jobs that call external systems;
- secondary datasources and cross-system database reads/writes;
- file integrations: SFTP, SMB, FTP, object storage, document stores and drop folders;
- email, SMS and push notification integrations;
- authentication, token, OIDC, OAuth2, LDAP or AD integrations;
- retry, circuit breaker, timeout, fallback, idempotency and replay code;
- integration-specific exceptions, error mappers and log markers;
- generated client classes and shared schema modules.

### Discover integrations from configuration

Look for:

- `application.yml`, `application.properties` and profile-specific variants;
- base URL, host, port, path, credential reference and timeout property names;
- Spring Cloud Stream bindings;
- Kafka/RabbitMQ topic, exchange, queue, routing-key, consumer group and DLQ configuration;
- datasource, HikariPool, schema and connection configuration;
- scheduler/job configuration;
- Helm, Kubernetes, Docker, Terraform or deployment descriptors;
- config maps, secret names and environment variable names;
- service discovery names;
- gateway route configuration;
- API gateway, broker, service mesh and ingress route configuration;
- retry, circuit breaker, bulkhead and timeout configuration;
- OAuth2/OIDC/LDAP client registrations.

Do not include secret values. Only include non-sensitive config keys and stable markers.

### Discover integrations from documentation and specs

Look for:

- OpenAPI/Swagger specs;
- AsyncAPI specs;
- WSDL/XSD schemas;
- GraphQL schemas;
- protobuf schemas;
- message schema files;
- integration README files;
- architecture docs;
- sequence diagrams;
- ownership/support docs;
- runbooks;
- ADRs;
- test fixtures that encode partner contracts;
- vendor/external provider documentation referenced by the system.

### Strong indicators of an integration contract

Create or update an integration only when at least one strong indicator exists:

- explicit client/listener/controller/gateway class for a cross-system contract;
- stable endpoint/host/base URL configuration for another system;
- stable queue/topic/exchange/routing-key/consumer-group contract;
- explicit OpenAPI/AsyncAPI/WSDL/XSD/GraphQL/protobuf/message schema;
- event name, command name or schema crossing system/context boundaries;
- datasource/schema/table used to access an external or shared data source;
- file/share/bucket path used for inter-system exchange;
- documented mediator/gateway route with contract semantics;
- documentation naming the contract;
- repeated runtime evidence pointing to the same external or cross-context contract.

### Weak indicators are not enough alone

Do not create an integration from only:

- one generic configuration key without target context;
- one arbitrary DTO name;
- one generic utility class;
- a framework dependency;
- a shared library package with no consuming system evidence;
- a host name with no contract semantics;
- a queue name with no producer/consumer or process context;
- a team name without integration evidence;
- an endpoint name with no cross-system or cross-context meaning.

If evidence is promising but incomplete, record a candidate in `BUILD MEMORY` when possible.

## Cross-file context

Before editing, read all available operational context files, if provided:

- `systems.yml`;
- `runtime-components.yml`, if present;
- `repo-map.yml`;
- `processes.yml`;
- `bounded-contexts.yml`;
- `teams.yml`;
- `glossary.md` or `terms.yml`;
- `handoff-rules.md` or incident routing views;
- `operational-context-index.md`;
- `BUILD MEMORY`, if provided.

Update only `integrations.yml`, but keep IDs and references consistent with the full catalog.

Do not introduce a reference to a system, runtime component, repository, module, process, bounded context, term, team or handoff rule unless:

1. that ID already exists in the corresponding file;
2. the evidence is strong enough to create a durable catalog gap; or
3. the missing reference is recorded as a pending cross-repo/cross-file reference in `BUILD MEMORY`.

## Merge policy

Use monotonic, evidence-preserving updates.

Rules:

- Merge; do not regenerate from the current source alone.
- Add confirmed positive facts.
- Preserve existing facts that are not visible in the current source.
- Do not infer global absence from local absence.
- Do not remove, downgrade, null-out or contradict an existing value because it was not found in the current repository.
- Do not replace a non-empty list with a shorter list from the current source.
- Deduplicate exact duplicate values.
- Normalize obvious formatting differences without changing meaning.
- Keep ids stable.
- Keep list values deterministic: sort IDs alphabetically unless process/order semantics matter.
- Preserve an existing gap until concrete evidence resolves, supersedes or invalidates it.
- Mark resolved/superseded gaps rather than silently deleting them when downstream consumers may rely on the history.
- Do not use final `gaps` as temporary memory between repository-specific agents.

## Split and merge policy

### Merge into an existing integration when

- the new evidence refers to the same source-target contract;
- endpoint variants are environment or version variants of the same contract;
- request/response classes belong to the same synchronous call;
- producer and consumer evidence complete the same event/message contract;
- generated client code and consuming service usage refer to the same external call;
- gateway route evidence and service client evidence describe the same operational boundary and the gateway has no independent responsibility/failure behavior;
- a shared library stack trace confirms implementation details for an already modeled contract.

### Split into separate integrations when

- source or target participant differs materially;
- mediator has independent operational behavior, logs, ownership or failure modes;
- the same target system exposes distinct contracts with different business semantics;
- sync call and async event represent distinct operational contracts;
- database dependency and API call have different failure behavior and grounding scope;
- two contracts have different responsible teams or external owners;
- a contract crosses a different bounded context or process boundary;
- deterministic match signals would become ambiguous if merged.

When a split/merge decision affects analysis and cannot be resolved from evidence, add a `contract-ambiguity` or `topology-ambiguity` gap.

## Specific modeling rules

### Synchronous API integrations

Capture:

- endpoint prefix or path template;
- HTTP method or operation name;
- host pattern or base URL configuration key;
- client class and generated client class when relevant;
- controller or adapter class when inbound;
- timeout, retry, circuit breaker, fallback and error markers;
- request/response schema names if useful;
- auth config keys and token provider dependencies when operationally relevant.

Do not include credentials, tokens, exact sensitive URLs, full auth headers or sample sensitive payloads.

### Asynchronous integrations

Capture producer and consumer evidence separately when possible.

Include:

- exchange/topic/stream name;
- queue name;
- routing key;
- event name;
- schema/DTO name;
- producer/publisher class;
- consumer/listener class;
- consumer group;
- DLQ/retry/lag markers;
- broker or virtual host only when operationally useful and not sensitive.

If only producer or consumer side is visible in the current source, add the confirmed side. Do not invent the missing side.

### Mediators and gateways

For mediated integrations, distinguish:

1. source-to-mediator contract;
2. mediator-to-final-target contract;
3. end-to-end semantic dependency.

Create separate entries when the mediator can fail independently, has its own owner, has separate logs, has a separate repository/configuration, changes protocol/transport, or has different runtime signals.

Use `participants.intermediaries`, `participants.finalTargets`, `channels`, `relations`, `handoffHints` and `llmToolHints` to make the chain clear.

If only the gateway is known, avoid inventing the final target. Use `BUILD MEMORY` or a durable gap depending on source coverage.

### Database and data-facing integrations

Model a database integration only when crossing a meaningful system/context boundary or when database evidence is operationally important.

Examples:

- service reads a legacy or external replicated table;
- service writes an audit/outbox/import table consumed by another system;
- integration depends on a named external schema;
- Hikari pool or datasource marker is a strong runtime signal;
- DB grounding is needed before using database tools;
- database failure has integration-level symptoms.

Do not model every internal table as an integration.

### File, batch and scheduler integrations

Capture:

- scheduler/job names;
- file transfer protocol;
- object storage bucket, share or path prefix when non-sensitive;
- filename patterns;
- archive/retry/error locations;
- import/export process names;
- file reader/writer classes;
- target or source system when known.

Do not store full paths that reveal sensitive internal locations unless they are already stable operational identifiers and safe to expose.

### Auth and identity integrations

Capture:

- identity provider system;
- token endpoint or client registration names when safe;
- OAuth2/OIDC/LDAP/SAML/auth type;
- client registration/config keys;
- token acquisition classes;
- auth failure markers;
- downstream contracts that depend on the token flow.

Do not include client IDs, secrets, certificates, tokens, scopes that reveal sensitive access, authorization headers or sample claims with personal data.

### Generated clients and schema repositories

Generated clients, integration libraries, shared event schemas and shared modules may be part of the code search scope even when they do not own runtime behavior.

Rules:

- Record generated client repositories/modules when known.
- Record schema repositories/modules when they contain message/API schemas.
- Link generated classes and package prefixes.
- Explain in `llmToolHints` when generated code lives outside the consuming service repository.
- Do not assume the generated client maintainer owns the external behavior.

## Multi-repository build memory policy

If `BUILD MEMORY` is provided, read it before editing.

Use `BUILD MEMORY` to track:

- repositories already scanned;
- repositories expected but not scanned yet;
- candidate integration facts;
- partial integration facts;
- pending cross-repo joins;
- unresolved references;
- temporary questions caused by scan order;
- facts requiring human confirmation.

`BUILD MEMORY` is not final operational truth.

Promote build-memory facts to `integrations.yml` only when concrete evidence supports them.

Use build-memory temporary questions for uncertainty that may be resolved by scanning another known repository.

Promote a temporary question to final `gaps` only when:

- expected sources were checked and the issue remains unresolved;
- the issue requires human/domain/external-owner input;
- the unresolved fact affects deterministic mapping, analysis, topology, coordination or semantic interpretation at catalog level.

Do not output build memory unless explicitly requested by the parent prompt as a sidecar artifact.

## Security and privacy rules

Never include:

- secrets;
- credentials;
- tokens;
- API keys;
- private keys;
- full authorization headers;
- customer data;
- personal data;
- sensitive sample payloads;
- production-only sensitive values;
- exact confidential hostnames when a non-sensitive host pattern is sufficient;
- raw SQL results containing business/customer data.

Allowed safe values usually include:

- config key names without values;
- endpoint templates without customer identifiers;
- non-sensitive host patterns;
- queue/topic/exchange/routing-key names when not sensitive;
- class, package and module names;
- schema/table/entity names when safe and operationally necessary;
- error codes and exception class names;
- dashboard/metric/span names when safe.

When in doubt, omit the sensitive value and record the safe marker or a limitation.

## YAML formatting rules

- Return valid YAML only.
- Preserve top-level keys in this order: `schemaVersion`, `catalogKind`, `integrations`, `gaps`.
- Use `schemaVersion: 1`.
- Use `catalogKind: operational-context-integrations`.
- Use two spaces for indentation.
- Never use TAB characters.
- Use empty lists `[]` for known-empty list fields.
- Use `null` for unknown scalar values.
- Quote values that contain `{}`, `:`, `#`, `[`, `]`, commas, leading `*`, leading `&`, `?`, `|`, `<`, `>`, `=`, `!`, `%`, `@`, backslashes or other YAML-sensitive characters.
- For endpoint paths with placeholders such as `/api/customers/{customerId}`, prefer block sequences or quote every value.
- Keep list values stable and deterministic: sort IDs alphabetically unless process/order semantics matter.
- Do not include Markdown fences in the final answer.

## Consistency checks before final output

Before returning the file, verify:

1. YAML parses successfully.
2. Top-level fields are exactly `schemaVersion`, `catalogKind`, `integrations`, `gaps`.
3. `schemaVersion` is `1`.
4. `catalogKind` is `operational-context-integrations`.
5. Every integration id is stable kebab-case and unique.
6. Every integration has all required fields in the schema order.
7. Every active integration has at least one exact or strong deterministic signal, unless `sourceCoverage.status: partial` and a durable gap explain why it cannot yet be mapped.
8. Every integration has participants filled with confirmed facts; unknown sides are not invented.
9. Every referenced team ID exists in `teams.yml` or the issue is represented as a durable gap or build-memory pending reference.
10. Every referenced system ID exists in `systems.yml` or the issue is represented as a durable gap or build-memory pending reference.
11. Every referenced runtime component exists in `runtime-components.yml` or the issue is represented as a durable gap or build-memory pending reference.
12. Every referenced repository ID exists in `repo-map.yml` or the issue is represented as a durable gap or build-memory pending reference.
13. Every referenced process ID exists in `processes.yml` or the issue is represented as a durable gap or build-memory pending reference.
14. Every referenced bounded context ID exists in `bounded-contexts.yml` or the issue is represented as a durable gap or build-memory pending reference.
15. Every referenced integration ID exists in `integrations.yml` or the issue is represented as a durable gap or build-memory pending reference.
16. Every referenced term ID exists in `glossary.md` or `terms.yml`, or the issue is represented as a durable gap or build-memory pending reference.
17. Responsibility is role-based and does not force a single owner.
18. Shared library and generated client maintainers are not treated as business owners without explicit evidence.
19. Handoff hints do not encode full incident routing policy.
20. Final `gaps` are durable catalog gaps, not temporary scan-order uncertainty.
21. No existing confirmed fact is removed unless explicit contradictory evidence exists.
22. No secrets, credentials, tokens, personal data, customer data or sensitive payload samples are present.
23. Endpoint templates and special characters are quoted safely.
24. No legacy fields remain unless represented in this prompt.

## Example

A correctly filled `integrations.yml` can look like this.

The example is intentionally generic CRM-oriented data. Do not copy domain facts from the example unless they are present in the analyzed repository or documentation.

```yaml
schemaVersion: 1
catalogKind: operational-context-integrations

integrations:
  - id: crm-api-to-marketing-platform-contact-sync
    name: CRM API -> Marketing Platform Contact Sync
    category: external-api
    lifecycleStatus: active
    summary: Synchronizes CRM contact profile changes to the external marketing automation platform through a REST API.
    integrationStyle: synchronous-request
    flowDirection: request-response
    criticality: high
    dataSensitivity: confidential
    responsibilityStatus: shared
    useFor:
      - deterministic-mapping
      - code-search
      - function-description
      - impact-analysis
      - incident-analysis
      - qa
    participants:
      source:
        system: crm-api
        runtimeComponent: crm-api-runtime
        boundedContext: customer-profile
        repositories:
          - crm-api-repo
        modules:
          - crm-api-repo/customer-profile-module
        role: client
        externalOwner: null
        notes: []
      targets:
        - system: marketing-platform
          runtimeComponent: null
          boundedContext: external-marketing
          repositories: []
          modules: []
          role: provider
          externalOwner: Marketing Platform Vendor Support
          notes: []
      intermediaries: []
      finalTargets:
        - system: marketing-platform
          runtimeComponent: null
          boundedContext: external-marketing
          repositories: []
          modules: []
          role: provider
          externalOwner: Marketing Platform Vendor Support
          notes: []
    contract:
      purpose: Sends customer contact profile updates to the marketing platform.
      operations:
        - id: sync-contact
          method: POST
          path: "/v1/contacts/{contactId}/sync"
          description: Synchronizes one CRM contact profile.
          requestObjects:
            - MarketingContactSyncRequest
          responseObjects:
            - MarketingContactSyncResponse
      messages: []
      dataObjects:
        - name: MarketingContactSyncRequest
          role: request
          localMeaning: Contact profile snapshot sent to the marketing platform.
      events: []
      schemas:
        - MarketingContactSyncRequest
        - MarketingContactSyncResponse
      versioning:
        strategy: uri-version
        current: v1
      auth:
        type: oauth2-client-credentials
        configKeys:
          - marketing.oauth2.client-registration
        authSignals:
          - MarketingTokenClient
      consistency: synchronous
      idempotency: supported
      retryPolicy:
        type: client-retry
        configKeys:
          - marketing.contact-sync.retry
        retrySignals:
          - ContactSyncRetryPolicy
    channels:
      - id: contact-sync-rest-call
        kind: sync-endpoint
        transport: rest
        direction: outbound
        description: CRM API calls the marketing platform contact sync endpoint.
        endpoint:
          methods:
            - POST
          pathTemplates:
            - "/v1/contacts/{contactId}/sync"
          pathPrefixes:
            - /v1/contacts
          hostPatterns:
            - marketing-platform
          baseUrlConfigKeys:
            - marketing.api.base-url
          authSignals:
            - marketing.oauth2.client-registration
        messaging:
          broker: null
          virtualHost: null
          exchanges: []
          topics: []
          queues: []
          routingKeys: []
          eventNames: []
          schemaNames: []
          producerClasses: []
          consumerClasses: []
          dlqNames: []
          retrySignals: []
        fileTransfer:
          locations: []
          filenamePatterns: []
          scheduleSignals: []
        database:
          connectionNames: []
          schemas: []
          tables: []
          operations: []
          hikariPools: []
        code:
          clientClasses:
            - MarketingContactClient
          listenerClasses: []
          publisherClasses: []
          controllerClasses: []
          schedulerClasses: []
          configClasses:
            - MarketingClientConfiguration
          generatedClientClasses:
            - MarketingContactsApi
        failureSignals:
          exceptionClasses:
            - MarketingPlatformTimeoutException
          errorCodes: []
          logMarkers:
            - MARKETING_CONTACT_SYNC_FAILED
          timeoutMarkers:
            - marketing.contact-sync.timeout
    transport:
      protocols:
        - REST
        - OAuth2
      http:
        methods:
          - POST
        endpointPrefixes:
          - /v1/contacts
        endpointTemplates:
          - "/v1/contacts/{contactId}/sync"
        operationNames:
          - syncContact
        hosts:
          - marketing-platform
        hostPatterns:
          - marketing-platform
        baseUrlConfigKeys:
          - marketing.api.base-url
        clientNames:
          - MarketingContactClient
        gatewayRoutes: []
      messaging:
        brokers: []
        virtualHosts: []
        exchanges: []
        queues: []
        topics: []
        routingKeys: []
        bindings: []
        dlqs: []
        retryQueuesOrTopics: []
        consumerGroups: []
        partitionKeys: []
      database:
        datasourceNames: []
        connectionNames: []
        hikariPoolMarkers: []
        schemas: []
        tables: []
        entities: []
        repositories: []
        operations: []
      file:
        protocols: []
        hosts: []
        locations: []
        pathPrefixes: []
        filenamePatterns: []
        buckets: []
        shares: []
        scheduleSignals: []
      observability:
        spans:
          - marketing.contact-sync
        metrics:
          - marketing_contact_sync_duration
        logMarkers:
          - MARKETING_CONTACT_SYNC_FAILED
        exceptionClasses:
          - MarketingPlatformTimeoutException
        errorCodes: []
        timeoutMarkers:
          - marketing.contact-sync.timeout
        retryMarkers:
          - ContactSyncRetryPolicy
    references:
      systems:
        - crm-api
        - marketing-platform
      runtimeComponents:
        - crm-api-runtime
      repositories:
        - crm-api-repo
        - marketing-client-repo
      modules:
        - crm-api-repo/customer-profile-module
      processes:
        - customer-profile-update
      boundedContexts:
        - customer-profile
        - external-marketing
      integrations: []
      terms:
        - customer-profile
        - contact-sync
      teams:
        - crm-platform-team
      handoffRules: []
    implementation:
      localSide: client
      clientTypes:
        - openapi-generated-client
      packagePrefixes:
        - com.example.crm.marketing
      modulePaths:
        - crm-api/customer-profile-module
      classHints:
        - MarketingContactClient
        - MarketingContactsApi
      clientClasses:
        - MarketingContactClient
      controllerClasses: []
      listenerClasses: []
      publisherClasses: []
      schedulerClasses: []
      generatedClientClasses:
        - MarketingContactsApi
      configClasses:
        - MarketingClientConfiguration
      configKeys:
        - marketing.api.base-url
        - marketing.contact-sync.retry
      artifactCoordinates:
        - com.example:marketing-client
      sharedLibraries: []
      generatedClients:
        - marketing-client-repo
      schemaRepositories: []
      gatewayOrBrokerConfig: []
      notes:
        - Generated client classes live outside the CRM API repository.
    responsibilities:
      - teamId: crm-platform-team
        role: source-side
        scope: Maintains CRM-side call and retry configuration.
        evidence: explicit-doc
        confidence: high
      - teamId: null
        role: external-provider
        scope: External marketing platform API availability and contract behavior.
        evidence: external-doc
        confidence: medium
    matchSignals:
      exact:
        endpointTemplates:
          - "/v1/contacts/{contactId}/sync"
        baseUrlConfigKeys:
          - marketing.api.base-url
        operationNames:
          - syncContact
      strong:
        clientClasses:
          - MarketingContactClient
        generatedClientClasses:
          - MarketingContactsApi
        packagePrefixes:
          - com.example.crm.marketing
        exceptionClasses:
          - MarketingPlatformTimeoutException
        logMarkers:
          - MARKETING_CONTACT_SYNC_FAILED
      medium:
        spanNames:
          - marketing.contact-sync
        metricNames:
          - marketing_contact_sync_duration
      weak:
        terms:
          - contact sync
    failureModes:
      - id: marketing-platform-timeout
        name: Marketing platform timeout
        symptoms:
          - CRM contact update completes locally but outbound sync fails or retries.
        likelySide: external
        matchSignals:
          - MarketingPlatformTimeoutException
          - MARKETING_CONTACT_SYNC_FAILED
        requiredEvidence:
          - CRM API log marker and target timeout evidence.
        firstAnalysisSteps:
          - Check CRM-side timeout/retry configuration and marketing platform availability.
    relations:
      - target: process:customer-profile-update
        type: supports
        via:
          - syncContact
        description: Sends updated contact profile data as part of customer profile update processing.
        confidence: high
    observability:
      dashboards: []
      alerts: []
      metrics:
        - marketing_contact_sync_duration
      traces:
        - marketing.contact-sync
      logs:
        - MARKETING_CONTACT_SYNC_FAILED
      healthChecks: []
      dlqOrRetrySignals:
        - ContactSyncRetryPolicy
    analysisHints:
      codeSearch:
        - Search CRM marketing client classes and generated marketing client repository when marketing sync errors appear.
      impactAnalysis:
        - Contact sync failures may leave marketing platform contact data stale while CRM profile data remains updated.
      functionDescription:
        - Describe failures as customer contact synchronization to the marketing platform, not as generic REST failure.
      incidentTriage:
        - Collect correlationId, contact id, CRM API log marker and target timeout evidence before escalation.
      dbGrounding: []
      qa:
        - This contract sends CRM contact profile changes to the external marketing platform.
    handoffHints:
      defaultRoute:
        - crm-platform-team
      requiredEvidence:
        - MARKETING_CONTACT_SYNC_FAILED log marker
        - marketing.contact-sync span or timeout marker
      firstActions:
        - Verify CRM-side timeout/retry behavior and external platform availability evidence.
      escalationTriggers:
        - Involve external provider when CRM client logs show target timeout or provider error responses.
    llmToolHints:
      answerWhenUserMentions:
        - marketing sync
        - contact sync
        - MarketingContactClient
      disambiguationHints:
        - Do not confuse CRM-side sync failure with local customer profile persistence failure.
      commonMisreads:
        - Generated client classes are implementation evidence, not business ownership evidence.
      usefulSearchKeywords:
        - MarketingContactClient
        - MarketingContactsApi
        - MARKETING_CONTACT_SYNC_FAILED
      explanationStyle: Explain as a customer contact synchronization dependency, not as a generic HTTP integration.
    evidence:
      - source: code:src/main/java/com/example/crm/marketing/MarketingContactClient.java
        observation: MarketingContactClient sends POST requests to the marketing contact sync API.
        confidence: high
      - source: config:src/main/resources/application.yml
        observation: marketing.api.base-url and marketing.contact-sync.retry configure the CRM-side client.
        confidence: high
      - source: doc:CRM marketing integration overview
        observation: CRM Platform Team maintains CRM-side synchronization and the external provider owns target API availability.
        confidence: medium
    sourceCoverage:
      status: partial
      scannedSources:
        - crm-api-repo
        - CRM marketing integration overview
      expectedSources:
        - marketing-client-repo
        - external marketing provider API documentation
      limitations:
        - External provider operational ownership is documented only at vendor level.

gaps:
  - id: marketing-provider-support-contact-unknown
    type: external-owner-unknown
    severity: low
    status: open
    summary: Exact external support contact for the marketing platform is not represented in the catalog.
    affectedNodes:
      - integration:crm-api-to-marketing-platform-contact-sync
      - system:marketing-platform
    impact:
      - Incident coordination may require manual lookup of vendor support route.
    requiredEvidence:
      - External provider support documentation or team-owned vendor contact record.
    evidence:
      - source: doc:CRM marketing integration overview
        observation: The document names the vendor but not the operational support contact.
        confidence: medium
```
