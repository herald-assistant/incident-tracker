# integrations.yml update prompt

Update only `integrations.yml`.

## Goal

Keep a short map of important operational integrations:
- source and target
- owner
- protocol and type
- runtime signals that reveal this integration

## Instructions

1. Read the current `integrations.yml`.
2. Merge the new facts.
3. Return the full updated YAML only.

## Rules

- Keep the structure minimal: `id`, `name`, `from`, `to`, `ownerTeamId`, `partnerTeamIds`, `externalOwner`, `protocol`, `type`, `processes`, `contexts`, `signals`, `handoff`.
- One entry per meaningful integration contract, not per generic host or queue.
- Keep only signals that are useful in incidents.
- Preserve the top-level wrapper: `schemaVersion`, `integrations`, `openQuestions`.
- If ownership is unclear, do not guess; add an `openQuestions` entry.

## Example

A correctly filled file can look like this:

```yaml
schemaVersion: 1

integrations:
  - id: app-core-to-partner-sync
    name: App Core -> Partner Service
    from: app-core
    to: partner-service
    ownerTeamId: integration-team
    partnerTeamIds: [core-team]
    externalOwner: partner-owner
    protocol: SOAP
    type: sync
    processes: [main-process]
    contexts: [core-context, partner-context]
    signals:
      endpoints: [/partner/resource]
      hosts: [api.partner.local]
      queues: []
      topics: []
      schemas: []
      spans: [SyncGateway.call]
      markers: [PARTNER]
      errors: [SOAPFault, Read timed out]
    handoff:
      target: Integration Team
      requiredEvidence: [correlationId, environment, host, endpoint, exception]

  - id: app-core-work-item-events
    name: App Core -> Work Item Events
    from: app-core
    to: partner-service
    ownerTeamId: integration-team
    partnerTeamIds: [core-team]
    externalOwner: partner-owner
    protocol: AMQP
    type: async
    processes: [main-process]
    contexts: [core-context, partner-context]
    signals:
      endpoints: []
      hosts: []
      queues: [work-item.sync.queue]
      topics: [work-item.events]
      schemas: [WORK_ITEM_SYNC]
      spans: []
      markers: [ASYNC_SYNC]
      errors: [consumer lag, serialization]
    handoff:
      target: Integration Team
      requiredEvidence: [correlationId, environment, queueName, topicName, exception]

openQuestions: []
```

## Input

`CURRENT FILE`

`NEW FACTS`
