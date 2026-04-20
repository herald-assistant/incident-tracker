# Operational Context Index

This directory is a template for operational context used in incident enrichment.

- `processes.yml`: business flow map
- `systems.yml`: runtime system map
- `integrations.yml`: integration map
- `repo-map.yml`: repository and module map
- `bounded-contexts.yml`: semantic boundary map
- `handoff-rules.md`: routing rules
- `glossary.md`: local vocabulary

| File | Purpose |
|---|---|
| systems.yml | Map of all runtime systems (internal and external) with runtime signals, dependencies, and handoff information. Entry point for identifying which system an incident belongs to. |
| repo-map.yml | Maps runtime evidence (service names, containers, endpoints) to GitLab repositories, package roots, and source code locations. Use this to navigate from an incident signal to the right codebase. |
| processes.yml | Describes the main business flows: Limit Process (TTL), Decision Process (TTY), Agreement Process (TTA), and Launch Process (GDM). Each includes participating systems, steps, and completion signals. Limit process includes detailed steps: NLO creation, BPM orchestration, RatingPlus integration (REST + MQ), sublimits calculation, document checklist/library management, survey, Salesforce R1 notification, ECM document operations, and cancellation. Agreement process includes detailed steps: decision event reception, process start, BDKK forward, Salesforce status handling, document generation, Spectrum simulation, decision changes, and expiration monitoring. |
| integrations.yml | Catalog of operational contracts between systems - REST, SOAP, AMQP, LDAP. Each entry includes signals for recognizing the integration during incident triage and handoff hints. Includes limit-process integrations: Salesforce R1 decision (CLP_SALESFORCE_DECISION_R1), Camunda limit-process (Feign start/cancel), limit engine (sublimits), checklist-document rule config (MQ), task-manager notifications (email send/cancel). Also includes agreement-process integrations: Salesforce (bidirectional), Spectrum (simulation relay), Backend/Task Manager/Org Structure/Clause Checklist (Feign), ECM (document storage), CCM (document generation via rest-mediator), Notifications (RabbitMQ), and decision event reception (TTY->TTA). |
| bounded-contexts.yml | Semantic boundary map with domain language, scope, and relations. Helps determine which part of the business domain an incident affects. Contexts: decision, limit, agreement, launch, collateral, clause-checklist, customer, product-configuration, product-repository, product-relations, checklist, notifications, organization, profitability. |
| teams.yml | Ownership map for four CLP teams: Draco, Gemini, Aquarius, Taurus. Currently missing ownership assignments - openQuestions lists what needs to be filled. |
| glossary.md | Domain glossary with definitions, synonyms, and distinguishing notes for CLP-specific terms (TTY, TTL, TTA, GDM, CBP, CIS, ECM, etc.), agreement-specific terms (BDKK, Process Driver, Credit Case, Decision Expiration, Salesforce Agreement Statuses, Spectrum Simulation Types), limit-process domain terms (NLO, Rating Sheet, RatingPlus integration, TTL Task Types, Document Process, Document Library, Sublimits, Suspended Lock, Survey, Drools Document Category Rules, Salesforce R1 Decision, scheduled jobs), and product domain terms (BaseProductData, Multiline, FX Limit, Leasing Exposure, Factoring Limit, Product Relations, LTV, etc.). |
| handoff-rules.md | Practical incident routing rules organized by symptom type: internal system failures, external integration failures, database/messaging failures, and cross-context routing. |

## How to use this catalog

1. Identify the system: Use `systems.yml` signals to match the incident to a runtime system.
2. Find the code: Use `repo-map.yml` to navigate from the system to the relevant repository and source code.
3. Understand the flow: Use `processes.yml` to understand which business flow is affected and at which step.
4. Check the integration: If the issue involves a cross-system call, use `integrations.yml` to identify the contract.
5. Understand the domain: Use `bounded-contexts.yml` and `glossary.md` to interpret the business vocabulary.
6. Route the incident: Use `handoff-rules.md` and `teams.yml` to determine who should handle the incident.
