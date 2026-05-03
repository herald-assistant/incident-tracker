# 01 Repository Discovery Prompt

This prompt is used by the Researcher phase.

The Researcher analyzes exactly one source repository or documentation fragment and produces a discovery report for the operational-context catalog.

The Researcher does not update final operational-context files.

## Inputs

The Researcher receives:

```yaml
catalogRoot: <catalog repository root>
sourceRepositoryRoot: <source repository root to analyze>
repositoryId: <stable id for current source repository>
repositoryType: service-repository | shared-library | generated-client | integration-client | frontend | infrastructure | documentation | unknown
branch: <optional branch>
commit: <optional commit hash>
parentPrompt: .github/prompts/00-parent-operational-context-update-prompt.md
discoveryPrompt: .github/prompts/01-repository-discovery-prompt.md
outputDiscoveryReport: .github/discovery/<repositoryId>.discovery.yml
```

The source repository is read-only.
The discovery report must be written under the catalog root, never in the source repository.

## Mission

Extract operational-context candidate facts from the selected source repository.

The result must help later File Updater phases update:

```text
systems.yml
repo-map.yml
integrations.yml
processes.yml
bounded-contexts.yml
teams.yml
glossary.md
handoff-rules.md
```

Only report facts that are useful for:

- deterministic runtime-to-system mapping;
- runtime-to-repository/code-scope mapping;
- code search scoping;
- integration dependency analysis;
- process or lifecycle understanding;
- domain vocabulary explanation;
- responsibility/coordination analysis;
- future LLM Q&A over the system.

## Hard rules

- Analyze only `sourceRepositoryRoot`.
- Do not edit `sourceRepositoryRoot`.
- Do not update final catalog files.
- Do not infer global absence from local absence.
- Do not invent ownership.
- Do not collapse multi-team responsibility into one owner.
- Do not write repo-local uncertainty into final `openQuestions`.
- Do not include secrets, tokens, credentials, personal data, or production records.
- Use stable kebab-case candidate IDs.
- Include source/provenance for important facts.

## Discovery output

Write or return a single YAML discovery report.

Default path:

```text
operational-context/discovery/<repositoryId>.discovery.yml
```

The YAML must follow this structure:

```yaml
schemaVersion: 2
kind: operational-context-repository-discovery

run:
  runId: string
  generatedAt: string
  catalogRoot: string
  sourceRepositoryRoot: string

source:
  repositoryId: string
  repositoryName: string
  repositoryType: service-repository | shared-library | generated-client | integration-client | frontend | infrastructure | documentation | unknown
  localPath: string
  branch: string | null
  commit: string | null
  scannedPaths: []
  excludedPaths: []
  notes: []

candidateFacts:
  systems: []
  repositories: []
  integrations: []
  processes: []
  boundedContexts: []
  teams: []
  glossaryTerms: []
  handoffRules: []

deferredCandidates: []
localAbsences: []
conflicts: []
targetFileRecommendations: []
qualityNotes: []
```

## Candidate fact shape

Each candidate fact should use this common shape when possible:

```yaml
- id: candidate-id
  name: Human Readable Name
  confidence: high | medium | low
  factType: system | repository | integration | process | bounded-context | team | glossary-term | handoff-rule
  targetFiles:
    - systems.yml
  summary: Short explanation of the candidate fact.
  evidence:
    positiveSignals: []
    sourceRefs: []
  links:
    systems: []
    repositories: []
    integrations: []
    processes: []
    boundedContexts: []
    teams: []
    glossaryTerms: []
  notes: []
```

Use file-specific shapes in the sections below when richer structure helps later updaters.

## System candidates

Use `candidateFacts.systems` for runtime/deployment systems, external systems, gateways, platforms, message brokers, databases, and frontends.

Do not create a runtime system for a code-only library unless explicit deployment evidence exists.

Recommended shape:

```yaml
candidateFacts:
  systems:
    - id: crm-core-api
      name: CRM Core API
      systemType: internal-service
      confidence: high
      summary: Main CRM backend service exposing customer and opportunity APIs.
      match:
        serviceNames: [crm-core-api]
        applicationNames: [crm-core]
        containerNames: [crm-core-api]
        deploymentNames: [crm-core-api]
        artifactIds: [crm-core-api]
        endpointPrefixes:
          - /api/customers
          - /api/opportunities
        queueNames: []
        topicNames: []
        hikariPools: [CRM-CORE-HikariPool]
        dbSchemas: [CRM_CORE]
        packagePrefixes: [com.example.crm.core]
        classHints: [CustomerController, OpportunityService]
        logMarkers: [CRM_CORE]
        exceptionClasses: [CustomerNotFoundException]
      links:
        repositories: [crm-core-api-repo]
        boundedContexts: [customer-management, sales-pipeline]
        processes: [lead-to-opportunity-process]
        integrations: []
      evidence:
        sourceRefs:
          - filePath: src/main/resources/application.yml
            evidenceType: spring-application-name
          - filePath: src/main/java/com/example/crm/customer/CustomerController.java
            evidenceType: spring-controller
```

## Repository/code-scope candidates

Use `candidateFacts.repositories` for Git/code repositories, service repos, shared libraries, generated clients, integration clients, monorepo modules, or config repos.

Recommended shape:

```yaml
candidateFacts:
  repositories:
    - id: crm-core-api-repo
      name: CRM Core API Repository
      repositoryType: service-repository
      confidence: high
      git:
        projectPath: example/crm/crm-core-api
        defaultBranch: main
      codeScope:
        role: main-service
        systems: [crm-core-api]
        modules:
          - id: customer-module
            paths:
              - src/main/java/com/example/crm/customer
            packagePrefixes:
              - com.example.crm.customer
            classHints:
              - CustomerController
              - CustomerService
        sharedLibraries: []
        generatedClients: []
      match:
        artifactIds: [crm-core-api]
        packagePrefixes: [com.example.crm]
        classHints: [CustomerController, OpportunityController]
        endpointPrefixes:
          - /api/customers
          - /api/opportunities
      evidence:
        sourceRefs:
          - filePath: pom.xml
            evidenceType: maven-artifact
          - filePath: src/main/java/com/example/crm/customer/CustomerController.java
            evidenceType: package-and-controller
```

## Integration candidates

Use `candidateFacts.integrations` for operational contracts, not generic hosts.

Model separate integrations when target owner, protocol, transport, failure mode, endpoint family, queue/topic, handoff, or semantic contract differs.

Recommended shape:

```yaml
candidateFacts:
  integrations:
    - id: crm-core-to-email-platform
      name: CRM Core API -> Email Platform
      integrationType: sync-rest
      confidence: high
      sourceSystem: crm-core-api
      targetSystem: email-platform
      mediatorSystem: null
      direction: outbound
      contract:
        protocol: REST
        transport: HTTPS
        style: request-response
        purpose: Send customer notification email.
      match:
        endpointPrefixes:
          - /v1/messages
        hostPatterns:
          - email-platform.internal
        clientClasses:
          - EmailPlatformClient
        exceptionClasses:
          - EmailPlatformTimeoutException
        logMarkers:
          - EMAIL_PLATFORM
      links:
        processes: [lead-to-opportunity-process]
        boundedContexts: [customer-communication]
        repositories: [crm-core-api-repo]
      evidence:
        sourceRefs:
          - filePath: src/main/java/com/example/crm/integration/email/EmailPlatformClient.java
            evidenceType: feign-client
          - filePath: src/main/resources/application.yml
            evidenceType: base-url-property
```

For asynchronous integrations, include exchanges, queues, topics, routing keys, event names, producer/listener classes, and DLQ/lag markers when known.

## Process candidates

Use `candidateFacts.processes` for business or operational flows with meaningful lifecycle, triggers, state transitions, or failure points.

Do not model every technical method call as a process.

Recommended shape:

```yaml
candidateFacts:
  processes:
    - id: lead-to-opportunity-process
      name: Lead to Opportunity Process
      confidence: high
      processType: business-process
      summary: Converts a qualified lead into a sales opportunity.
      triggers:
        endpoints:
          - /api/leads/{leadId}/qualify
        events:
          - lead.qualified
        jobs: []
      steps:
        - id: qualify-lead
          name: Qualify lead
          match:
            classHints: [LeadQualificationService]
            endpointPrefixes:
              - /api/leads
            eventNames: [lead.qualified]
        - id: create-opportunity
          name: Create opportunity
          match:
            classHints: [OpportunityService]
            dbTables: [OPPORTUNITY]
      completionSignals:
        events: [opportunity.created]
        statuses: [OPPORTUNITY_CREATED]
      links:
        systems: [crm-core-api]
        repositories: [crm-core-api-repo]
        boundedContexts: [sales-pipeline]
        integrations: [crm-core-to-email-platform]
      evidence:
        sourceRefs:
          - filePath: src/main/java/com/example/crm/lead/LeadController.java
            evidenceType: endpoint
          - filePath: src/main/java/com/example/crm/opportunity/OpportunityService.java
            evidenceType: service-flow
```

## Bounded context candidates

Use `candidateFacts.boundedContexts` for semantic/domain boundaries.

Recommended shape:

```yaml
candidateFacts:
  boundedContexts:
    - id: customer-management
      name: Customer Management
      confidence: high
      summary: Owns CRM customer identity, profile, segmentation, and lifecycle language.
      localLanguage:
        terms: [customer, customer-profile, segment]
        aliases: [account, client]
        disambiguation:
          - Customer in CRM means a sales/account entity, not an authentication user.
      match:
        packagePrefixes:
          - com.example.crm.customer
        endpointPrefixes:
          - /api/customers
        classHints:
          - CustomerController
          - CustomerProfileService
        dbTables:
          - CUSTOMER
      links:
        systems: [crm-core-api]
        repositories: [crm-core-api-repo]
        processes: [lead-to-opportunity-process]
      evidence:
        sourceRefs:
          - filePath: src/main/java/com/example/crm/customer
            evidenceType: package-cluster
```

## Team/responsibility candidates

Use `candidateFacts.teams` only for explicitly documented responsibility evidence.

Do not infer ownership from package names, author names, module names, or current repository presence.

Recommended shape:

```yaml
candidateFacts:
  teams:
    - id: crm-sales-team
      name: CRM Sales Team
      confidence: medium
      responsibilityEvidenceType: documented-maintainer
      roles:
        accountableFor: []
        maintains:
          repositories: [crm-core-api-repo]
        participatesIn:
          processes: [lead-to-opportunity-process]
        domainExpertFor:
          boundedContexts: [sales-pipeline]
        firstResponderFor: []
      evidence:
        sourceRefs:
          - filePath: CODEOWNERS
            evidenceType: codeowners
```

If evidence only shows contributors or CODEOWNERS, do not call them accountable owners unless the source says so.

## Glossary term candidates

Use `candidateFacts.glossaryTerms` for local vocabulary useful for LLM Q&A, evidence interpretation, repo lookup, or disambiguation.

Recommended shape:

```yaml
candidateFacts:
  glossaryTerms:
    - id: qualified-lead
      term: Qualified Lead
      category: business-term
      confidence: high
      localMeaning: Lead that passed CRM qualification checks and can become an opportunity.
      aliases: [sales-qualified lead, SQL]
      doNotConfuseWith:
        - Marketing-qualified lead, which is earlier in the funnel.
      useFor:
        - explaining lead-to-opportunity flow
        - mapping lead.qualified events
      typicalEvidenceSignals:
        events: [lead.qualified]
        classes: [LeadQualificationService]
        endpoints:
          - /api/leads/{leadId}/qualify
      canonicalReferences:
        processes: [lead-to-opportunity-process]
        boundedContexts: [sales-pipeline]
      evidence:
        sourceRefs:
          - filePath: src/main/java/com/example/crm/lead/LeadStatus.java
            evidenceType: enum
```

## Handoff rule candidates

Use `candidateFacts.handoffRules` sparingly.

A handoff rule candidate should exist only when the source reveals routing/coordination behavior that changes who should act next.

Recommended shape:

```yaml
candidateFacts:
  handoffRules:
    - id: email-platform-timeout-during-lead-conversion
      title: Email platform timeout during lead conversion
      confidence: medium
      routeWhen:
        signals:
          exceptionClasses: [EmailPlatformTimeoutException]
          endpointPrefixes:
            - /v1/messages
          processIds: [lead-to-opportunity-process]
      routeTo:
        firstResponderTeamIds: [crm-integration-team]
        partnerTeamIds: [crm-sales-team]
      requiredEvidence:
        - traceId
        - endpoint
        - exceptionClass
        - sourceSystem
        - targetSystem
      expectedFirstAction: Verify email platform availability and message request payload mapping.
      evidence:
        sourceRefs:
          - filePath: docs/support/email-platform-routing.md
            evidenceType: support-doc
```

## Deferred candidates

Use `deferredCandidates` for plausible facts that require another repository or source.

Example:

```yaml
deferredCandidates:
  - id: email-platform-consumer-side-contract
    kind: integration-counterpart
    relatedCandidateIds: [crm-core-to-email-platform]
    reason: Producer/client side is visible in this repository, but target-side ownership and endpoint implementation are outside this repo.
    suggestedNextSources:
      - email-platform repository
      - integration ownership matrix
    doNotPromoteToOpenQuestions: true
```

## Local absences

Use `localAbsences` for things searched but not found locally.

Local absences must not be promoted into final catalog as deletions or final open questions.

Example:

```yaml
localAbsences:
  - id: no-kafka-bindings-observed
    searchedFor: Kafka bindings
    searchedPaths:
      - src/main/resources
      - src/main/java
    result: not-observed
    interpretation: This repo appears to use REST and AMQP only; this is not global evidence that CRM has no Kafka integrations.
```

## Conflicts

Use `conflicts` for direct contradictions.

Example:

```yaml
conflicts:
  - id: application-name-conflict
    severity: medium
    existingCatalogFact: crm-core-api runtime.applicationNames contains crm-core
    observedFact: application.yml contains spring.application.name=crm-api
    affectedTargetFiles: [systems.yml, repo-map.yml]
    recommendation: Keep both as aliases unless deployment evidence confirms one is obsolete.
    sourceRefs:
      - filePath: src/main/resources/application.yml
        evidenceType: spring-application-name
```

## Target file recommendations

At the end of discovery, recommend which files should be updated.

Example:

```yaml
targetFileRecommendations:
  - targetFile: repo-map.yml
    reason: New package prefixes, modules, and artifact ID were discovered.
    candidateIds: [crm-core-api-repo]
  - targetFile: systems.yml
    reason: Runtime service name and Hikari pool were discovered.
    candidateIds: [crm-core-api]
  - targetFile: integrations.yml
    reason: REST email platform client was discovered.
    candidateIds: [crm-core-to-email-platform]
```

## Detection checklist

When analyzing Java/Spring/JVM repositories, search for:

```text
spring.application.name
application.yml / application.properties
Helm/Kubernetes/deployment files
Docker image names
Maven/Gradle artifact IDs and modules
@RestController, @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
@FeignClient, WebClient, RestTemplate
SOAP/WSDL/generated clients
Rabbit/Kafka/Spring Cloud Stream bindings
@RabbitListener, @KafkaListener, StreamBridge
exchange/queue/topic/routing-key constants
event classes and schemas
@Scheduled, ShedLock, Quartz jobs
BPMN/Camunda/state-machine definitions
@Entity, @Table, repositories/DAOs
Liquibase/Flyway migrations
Hikari pool names and datasource config
exception classes and error markers
CODEOWNERS, ownership docs, support docs
README, docs, ADRs, runbooks
```

For frontend repositories, search for routes, API clients, generated models, domain terms, feature modules, and backend API endpoint usage.

For shared libraries and generated clients:

- do not create runtime systems unless there is explicit deployment evidence;
- capture code scope, package prefixes, class hints, DTOs, exported APIs, generated clients, and possible consumers;
- put unknown consumers into `deferredCandidates`, not final `openQuestions`.

## Example discovery report

```yaml
schemaVersion: 2
kind: operational-context-repository-discovery

run:
  runId: crm-core-api-2026-05-04
  generatedAt: '2026-05-04T10:15:00+02:00'
  catalogRoot: incident-tracker
  sourceRepositoryRoot: crm-core-api

source:
  repositoryId: crm-core-api
  repositoryName: CRM Core API
  repositoryType: service-repository
  localPath: C:\Users\example\IdeaProjects\crm-core-api
  branch: main
  commit: null
  scannedPaths:
    - pom.xml
    - src/main/java
    - src/main/resources
  excludedPaths:
    - target
    - build
  notes: []

candidateFacts:
  systems:
    - id: crm-core-api
      name: CRM Core API
      systemType: internal-service
      confidence: high
      summary: Main CRM backend service exposing customer, lead, and opportunity APIs.
      match:
        serviceNames: [crm-core-api]
        applicationNames: [crm-core]
        containerNames: [crm-core-api]
        deploymentNames: []
        artifactIds: [crm-core-api]
        endpointPrefixes:
          - /api/customers
          - /api/leads
          - /api/opportunities
        queueNames: []
        topicNames: []
        hikariPools: [CRM-CORE-HikariPool]
        dbSchemas: [CRM_CORE]
        packagePrefixes: [com.example.crm]
        classHints: [CustomerController, LeadController, OpportunityService]
        logMarkers: [CRM_CORE]
        exceptionClasses: [CustomerNotFoundException]
      links:
        repositories: [crm-core-api-repo]
        boundedContexts: [customer-management, sales-pipeline]
        processes: [lead-to-opportunity-process]
        integrations: [crm-core-to-email-platform]
      evidence:
        positiveSignals:
          - spring.application.name=crm-core
          - artifactId=crm-core-api
        sourceRefs:
          - filePath: src/main/resources/application.yml
            evidenceType: spring-application-name
          - filePath: pom.xml
            evidenceType: maven-artifact

  repositories:
    - id: crm-core-api-repo
      name: CRM Core API Repository
      repositoryType: service-repository
      confidence: high
      git:
        projectPath: example/crm/crm-core-api
        defaultBranch: main
      codeScope:
        role: main-service
        systems: [crm-core-api]
        modules:
          - id: customer-module
            paths:
              - src/main/java/com/example/crm/customer
            packagePrefixes:
              - com.example.crm.customer
            classHints:
              - CustomerController
              - CustomerService
          - id: sales-pipeline-module
            paths:
              - src/main/java/com/example/crm/sales
              - src/main/java/com/example/crm/lead
              - src/main/java/com/example/crm/opportunity
            packagePrefixes:
              - com.example.crm.sales
              - com.example.crm.lead
              - com.example.crm.opportunity
            classHints:
              - LeadController
              - OpportunityService
        sharedLibraries: []
        generatedClients: []
      match:
        artifactIds: [crm-core-api]
        packagePrefixes: [com.example.crm]
        classHints: [CustomerController, OpportunityController, EmailPlatformClient]
        endpointPrefixes:
          - /api/customers
          - /api/opportunities
      evidence:
        sourceRefs:
          - filePath: pom.xml
            evidenceType: maven-artifact

  integrations:
    - id: crm-core-to-email-platform
      name: CRM Core API -> Email Platform
      integrationType: sync-rest
      confidence: high
      sourceSystem: crm-core-api
      targetSystem: email-platform
      mediatorSystem: null
      direction: outbound
      contract:
        protocol: REST
        transport: HTTPS
        style: request-response
        purpose: Send customer and opportunity notifications.
      match:
        endpointPrefixes:
          - /v1/messages
        hostPatterns:
          - email-platform.internal
        clientClasses:
          - EmailPlatformClient
        exceptionClasses:
          - EmailPlatformTimeoutException
        logMarkers:
          - EMAIL_PLATFORM
      links:
        processes: [lead-to-opportunity-process]
        boundedContexts: [customer-communication]
        repositories: [crm-core-api-repo]
      evidence:
        sourceRefs:
          - filePath: src/main/java/com/example/crm/integration/email/EmailPlatformClient.java
            evidenceType: feign-client

  processes:
    - id: lead-to-opportunity-process
      name: Lead to Opportunity Process
      confidence: high
      processType: business-process
      summary: Converts a qualified lead into a sales opportunity.
      triggers:
        endpoints:
          - /api/leads/{leadId}/qualify
        events:
          - lead.qualified
        jobs: []
      steps:
        - id: qualify-lead
          name: Qualify lead
          match:
            classHints: [LeadQualificationService]
            endpointPrefixes:
              - /api/leads
            eventNames: [lead.qualified]
        - id: create-opportunity
          name: Create opportunity
          match:
            classHints: [OpportunityService]
            dbTables: [OPPORTUNITY]
      completionSignals:
        events: [opportunity.created]
        statuses: [OPPORTUNITY_CREATED]
      links:
        systems: [crm-core-api]
        repositories: [crm-core-api-repo]
        boundedContexts: [sales-pipeline]
        integrations: [crm-core-to-email-platform]
      evidence:
        sourceRefs:
          - filePath: src/main/java/com/example/crm/lead/LeadController.java
            evidenceType: endpoint

  boundedContexts:
    - id: customer-management
      name: Customer Management
      confidence: high
      summary: Owns CRM customer identity, profile, segmentation, and lifecycle language.
      localLanguage:
        terms: [customer, customer-profile, segment]
        aliases: [account, client]
        disambiguation:
          - Customer in CRM means a sales/account entity, not an authentication user.
      match:
        packagePrefixes:
          - com.example.crm.customer
        endpointPrefixes:
          - /api/customers
        classHints:
          - CustomerController
          - CustomerProfileService
        dbTables:
          - CUSTOMER
      links:
        systems: [crm-core-api]
        repositories: [crm-core-api-repo]
        processes: []
      evidence:
        sourceRefs:
          - filePath: src/main/java/com/example/crm/customer
            evidenceType: package-cluster

  teams: []

  glossaryTerms:
    - id: qualified-lead
      term: Qualified Lead
      category: business-term
      confidence: high
      localMeaning: Lead that passed CRM qualification checks and can become an opportunity.
      aliases: [sales-qualified lead, SQL]
      doNotConfuseWith:
        - Marketing-qualified lead, which is earlier in the funnel.
      useFor:
        - explaining lead-to-opportunity flow
        - mapping lead.qualified events
      typicalEvidenceSignals:
        events: [lead.qualified]
        classes: [LeadQualificationService]
        endpoints:
          - /api/leads/{leadId}/qualify
      canonicalReferences:
        processes: [lead-to-opportunity-process]
        boundedContexts: [sales-pipeline]
      evidence:
        sourceRefs:
          - filePath: src/main/java/com/example/crm/lead/LeadStatus.java
            evidenceType: enum

  handoffRules: []

deferredCandidates:
  - id: email-platform-target-ownership
    kind: integration-counterpart
    relatedCandidateIds: [crm-core-to-email-platform]
    reason: Client side is visible in CRM Core API; target service ownership and implementation are outside this repository.
    suggestedNextSources:
      - email-platform repository
      - integration ownership matrix
    doNotPromoteToOpenQuestions: true

localAbsences:
  - id: no-kafka-bindings-observed
    searchedFor: Kafka bindings
    searchedPaths:
      - src/main/resources
      - src/main/java
    result: not-observed
    interpretation: Kafka was not observed in this repo; this is not global evidence that CRM has no Kafka integrations.

conflicts: []

targetFileRecommendations:
  - targetFile: systems.yml
    reason: Runtime service identity and DB pool were discovered.
    candidateIds: [crm-core-api]
  - targetFile: repo-map.yml
    reason: Repository modules, package prefixes, and class hints were discovered.
    candidateIds: [crm-core-api-repo]
  - targetFile: integrations.yml
    reason: Email Platform REST client was discovered.
    candidateIds: [crm-core-to-email-platform]
  - targetFile: processes.yml
    reason: Lead-to-opportunity process was discovered.
    candidateIds: [lead-to-opportunity-process]
  - targetFile: bounded-contexts.yml
    reason: Customer management context was discovered.
    candidateIds: [customer-management]
  - targetFile: glossary.md
    reason: Qualified Lead has local CRM meaning and evidence signals.
    candidateIds: [qualified-lead]

qualityNotes:
  - Ownership was not inferred because no explicit ownership source was found.
```
