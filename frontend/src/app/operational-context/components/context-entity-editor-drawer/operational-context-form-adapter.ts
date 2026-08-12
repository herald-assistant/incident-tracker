import { FormControl, FormGroup, Validators } from '@angular/forms';

import {
  OperationalContextPayload,
  OperationalContextWritableType
} from '../../models/operational-context-maintenance.models';

export type OperationalContextFieldKind =
  | 'text'
  | 'textarea'
  | 'list'
  | 'system-participants'
  | 'system-runtime'
  | 'repository-evidence'
  | 'repository-llm-tool-hints'
  | 'bounded-local-language'
  | 'bounded-scope'
  | 'bounded-semantic-boundary'
  | 'bounded-evidence'
  | 'bounded-llm-tool-hints'
  | 'ownership'
  | 'references'
  | 'code-search-target'
  | 'code-search-repositories'
  | 'integration-participants'
  | 'repository-git'
  | 'process-participants'
  | 'process-steps'
  | 'process-boundary'
  | 'process-lifecycle'
  | 'completion-signals'
  | 'match-signals'
  | 'relations'
  | 'failure-modes'
  | 'data-artifacts'
  | 'source-coverage'
  | 'catalog-gaps';

export interface OperationalContextFieldGuidance {
  whatToEnter: string;
  runtimeEffect: string;
  acceptedValues: string;
  example: string;
}

export interface OperationalContextFormField {
  path: string;
  label: string;
  kind: OperationalContextFieldKind;
  required?: boolean;
  help?: string;
  guidance: OperationalContextFieldGuidance;
}

interface OperationalContextFormSection {
  title: string;
  fields: OperationalContextFormField[];
}

interface FieldOptions {
  required?: boolean;
  help?: string;
  example?: string;
}

function field(
  path: string,
  label: string,
  kind: OperationalContextFieldKind,
  whatToEnter: string,
  runtimeEffect: string,
  acceptedValues: string,
  options: FieldOptions = {}
): OperationalContextFormField {
  return {
    path,
    label,
    kind,
    required: options.required,
    help: options.help,
    guidance: {
      whatToEnter,
      runtimeEffect,
      acceptedValues,
      example: options.example || 'Use an anonymized value from the CRM domain.'
    }
  };
}

const COMMON_EXAMPLE = {
  id: 'crm-contact-core',
  name: 'CRM Contact Core',
  shortName: 'Contact Core',
  lifecycleStatus: 'active',
  summary: 'Maintains the CRM customer contact view.',
  purpose: 'Provide a consistent customer contact record to CRM processes.',
  aliases: 'contact-core\ncrm-contact-service',
  useFor: 'Find the system responsible for CRM contact data.\nGround incident analysis in the CRM contact boundary.'
};

const BASE_FIELDS: OperationalContextFormField[] = [
  field('id', 'ID', 'text', 'Enter a stable, unique catalogue identifier.', 'References, graph edges, delete-impact checks and API URLs use this ID. AI tools also expose it as the canonical entity key.', 'Required. Use lowercase kebab-case; it becomes immutable after creation.', { required: true, help: 'Stable ID. It cannot be renamed after creation.', example: COMMON_EXAMPLE.id }),
  field('name', 'Name', 'text', 'Enter the human-readable catalogue name.', 'Shown in lists and AI/tool read models as the primary label used to explain the entity.', 'Required, non-blank text.', { required: true, example: COMMON_EXAMPLE.name }),
  field('shortName', 'Short name', 'text', 'Enter a compact name used when the full name is too long.', 'Acts as a fallback display label and an additional searchable identity for runtime and AI-assisted resolution.', 'Optional free text; keep it unambiguous inside the catalogue.', { example: COMMON_EXAMPLE.shortName }),
  field('lifecycleStatus', 'Lifecycle status', 'text', 'Enter the entity lifecycle state.', 'Returned by catalogue APIs and AI tools so consumers can distinguish current context from planned or retired context. It does not enable or disable runtime code by itself.', 'Free text in the backend. The current maintenance convention is: active, planned, deprecated, or retired; do not introduce another value without updating the convention.', { example: COMMON_EXAMPLE.lifecycleStatus }),
  field('summary', 'Summary', 'textarea', 'Describe what the entity is in one concise statement.', 'Used in catalogue search, detail read models and AI context to identify relevance without reading every advanced field.', 'Optional plain text; prefer one or two business-readable sentences.', { example: COMMON_EXAMPLE.summary }),
  field('purpose', 'Purpose', 'textarea', 'Explain why the entity exists and the outcome it supports.', 'Helps AI distinguish similarly named entities and connect technical evidence to functional intent.', 'Optional plain text focused on durable intent, not implementation inventory.', { example: COMMON_EXAMPLE.purpose }),
  field('aliases', 'Aliases', 'list', 'Enter alternate business, technical or historical names.', 'Aliases are included in generic matching signals and improve catalogue search and AI entity resolution.', 'Optional list; one distinct value per line.', { help: 'One value per line.', example: COMMON_EXAMPLE.aliases }),
  field('useFor', 'Use for', 'list', 'Enter the questions or analysis situations for which this entity is useful.', 'Exposed to AI tools and used as semantic guidance when selecting relevant operational context.', 'Optional list; one user-oriented use case per line.', { help: 'One value per line.', example: COMMON_EXAMPLE.useFor })
];

const CODE_SEARCH_BASE_FIELDS: OperationalContextFormField[] = [
  BASE_FIELDS[0],
  BASE_FIELDS[1],
  BASE_FIELDS[3],
  BASE_FIELDS[4],
  BASE_FIELDS[7]
];

const TYPE_FIELDS: Record<OperationalContextWritableType, OperationalContextFormSection[]> = {
  system: [
    { title: 'Basic', fields: [...BASE_FIELDS,
      field('systemType', 'System type', 'text', 'Classify the durable kind of system.', 'Exposed in system rows and AI tools; helps distinguish services, gateways, stores, platforms and external boundaries.', 'Free text in the backend. Current catalogue conventions include internal-service, business-service, gateway, data-store, message-broker, platform-service, external-system, external-saas, identity-provider and middleware.', { example: 'internal-service' }),
      field('operationalStatus', 'Operational status', 'text', 'Describe the system current operating state.', 'AI and operators use it as context when deciding whether evidence concerns a live, degraded, planned or unavailable system. It does not change application execution.', 'Optional free text; use a short, stable operational label and avoid incident-specific status.', { example: 'operational' }),
      field('criticality', 'Criticality', 'text', 'Enter the business or operational impact tier.', 'Displayed in read models and given to AI to prioritize affected systems and explain impact.', 'Free text in the backend. Current catalogue values are critical, high, medium, low and unknown.', { example: 'high' })
    ] },
    { title: 'Ownership and references', fields: [
      field('ownership', 'Ownership', 'ownership', 'Provide system-level ownership: ownerTeamIds, optional ownerLabel, ownershipStatus, confidence, source and notes.', 'The ownership resolver uses explicit system ownership as a fallback when no bounded-context owner is available; delete impact validates referenced team IDs.', 'Structured controls. ownershipStatus explicit requires a non-empty ownerTeamIds list or ownerLabel. Team IDs must exist; confidence is high, medium or low.', { example: 'Select CRM Domain Team, explicit and high.' }),
      field('participants', 'External participant', 'system-participants', 'Enter a durable external owner label only when this system is operated outside the local team catalogue.', 'The label is exposed in system relations and AI context so responsibility beyond the local catalogue can be explained without creating false local team ownership.', 'Guided optional externalOwner text. Leave it empty for locally operated systems; use system ownership for a local team.', { example: 'CRM platform provider' }),
      field('references', 'References', 'references', 'Link the system to known processes, bounded contexts, integrations, teams, glossary terms and handoff rules.', 'Builds navigable graph edges, powers related-entity views and delete-impact checks, and lets AI move from the system to functional context. Repositories must be linked through code-search scopes instead.', 'Structured reference pickers. Every selected ID must exist; system references do not expose repositories.', { example: 'Select CRM Contact Update, CRM Customer Context and CRM Domain Team.' }),
    ] },
    { title: 'Signals and advanced', fields: [
      field('matchSignals', 'Recognition signals', 'match-signals', 'Add durable evidence values and assign each one a confidence bucket and signal key, such as serviceNames, routes or aliases.', 'The resolver and AI tools compare evidence with these recognition signals to identify the system; stronger buckets communicate higher match confidence.', 'Guided rows stored as matchSignals. Confidence is exact, strong, medium or weak. Choose a recommended signal key for this entity; enter one distinct value per line.', { example: 'Add exact / serviceNames / crm-contact-service and strong / routes / /crm/contacts.' }),
      field('relations', 'Relations', 'relations', 'Add meaningful outgoing semantic edges to existing catalogue entities or a clearly named external target.', 'Relations become graph edges used by relation views, ownership/navigation inference and delete-impact blocking; AI sees the connected context.', 'Guided rows with relation type, canonical target type and entity, optional integration path and evidence. Self-reference and duplicate edges are blocked.', { example: 'Add depends-on → system → CRM Profile Store with anonymized CRM evidence.' }),
      field('runtime', 'Runtime configuration', 'system-runtime', 'Enter the stable repository-relative configuration directory for this internal service.', 'Config Drift Viewer resolves this value as the system configuration scope. Runtime service, deployment and application identities belong in Recognition signals so matching has one source of truth.', 'Guided optional configurationDirectory. Use a safe relative path up to 255 characters: letters, digits, dot, underscore, slash and hyphen; no leading/trailing slash, backslash, //, .. or @{.', { example: 'crm/contact-service' }),
      field('sourceCoverage', 'Source coverage', 'source-coverage', 'State which durable sources were checked, which were expected and what remains outside current visibility.', 'opctx_get_entity exposes this section to AI; limitations become explicit tool visibility limits so partial catalogue knowledge is not treated as complete.', 'Guided fields. Status uses complete, partial or unknown for new entries; legacy full, scanned and fully-scanned remain readable. Enter source descriptions only, never secrets or raw payloads.', { example: 'Set partial; scanned: anonymized CRM architecture notes; expected: CRM retry design; limitation: retry ownership not confirmed.' }),
      field('gaps', 'Gaps', 'catalog-gaps', 'Record concrete unresolved facts or maintenance questions for this system.', 'Each actionable summary becomes an Open Questions inbox item and is returned to AI by opctx_get_entity. Status and severity control how the question is presented; a gap is not evidence of a defect.', 'Repeatable cards. Summary is required. Optional ID uses lowercase kebab-case; severity is error, warning or info; status is open or resolved.', { example: 'Add crm-retry-owner, missing-evidence, warning, open: Confirm ownership of CRM batch retries.' }),
      field('notes', 'Notes', 'list', 'Add durable clarifications that do not fit a structured field.', 'Notes are preserved with the canonical payload and may be included in detailed operational context shown to AI.', 'Optional list; one concise note per line. Do not store secrets or transient incident commentary.', { help: 'One value per line.', example: 'CRM contact backfill is handled outside the synchronous request path.' })
    ] }
  ],
  repository: [
    { title: 'Basic', fields: [...BASE_FIELDS,
      field('repositoryType', 'Repository type', 'text', 'Classify the repository packaging or role.', 'Helps catalogue and AI distinguish application repositories, monorepos and shared libraries before code exploration.', 'Free text in the backend. Current catalogue conventions include service, monorepo and shared-library.', { example: 'service' }),
      field('criticality', 'Criticality', 'text', 'Enter the repository relevance or impact tier.', 'Exposed to read models and AI as prioritization context; actual code read order is controlled by code-search scope priority.', 'Free text in the backend. Current catalogue values are critical, high, medium, low and unknown.', { example: 'high' })
    ] },
    { title: 'Git identity and references', fields: [
      field('git', 'Git identity', 'repository-git', 'Enter the canonical Git provider identity: provider, group, project or projectPath, defaultBranch, URL and optional aliases.', 'project/projectPath resolves this catalogue entry to the GitLab project used by code tools, ownership resolution and repository discovery.', 'Structured Git fields. At least project or projectPath is required; projectPath should be the provider-relative group/project path. inferred is server-owned and is not editable.', { required: true, example: 'Set provider gitlab, group crm, project contact-service and projectPath crm/contact-service.' }),
      field('references', 'References', 'references', 'Link the repository to semantic systems, processes, bounded contexts, integrations, terms and handoff rules.', 'Creates graph navigation from code identity to operational meaning and feeds reverse lookup and delete-impact checks. It does not define ownership.', 'Structured reference pickers. Every selected ID must exist and the repository cannot reference itself.', { example: 'Select CRM Contact Core, CRM Customer Context and CRM Contact Update.' })
    ] },
    { title: 'Signals and advanced', fields: [
      field('matchSignals', 'Recognition signals', 'match-signals', 'Add repository evidence values such as projectNames, projectPaths, build coordinates or aliases and assign their confidence.', 'Project names and paths join the repository resolver signal set and help runtime evidence and AI locate the correct code source.', 'Guided rows stored as matchSignals. Confidence is exact, strong, medium or weak; signal keys come from repository conventions and existing data.', { example: 'Add exact / projectPaths / crm/contact-service and strong / projectNames / contact-service.' }),
      field('relations', 'Relations', 'relations', 'Add meaningful semantic edges from this repository to existing catalogue entities.', 'Builds graph edges for related-entity views and AI navigation; code ownership still resolves through system or bounded-context references.', 'Guided relation rows with an existing target. Avoid duplicating references unless the relation type adds distinct meaning.', { example: 'Add implements → bounded-context → CRM Customer Context.' }),
      field('evidence', 'Evidence', 'repository-evidence', 'Add stable evidence records that justify the repository identity or semantic mapping.', 'Known evidence records are shown in the operator detail and opctx_get_entity output, giving AI explainable provenance without exposing source contents.', 'Guided cards. sourceRef and evidenceType are required; note is optional. Reference stable paths or document labels only—never credentials, customer data or copied source code.', { example: 'sourceRef: crm/contact-service/pom.xml; evidenceType: build-definition; note: Anonymized CRM service module.' }),
      field('sourceCoverage', 'Source coverage', 'source-coverage', 'Describe which durable repository sources or areas were checked, which were expected and what remains invisible.', 'opctx_get_entity exposes this section and turns limitations into AI visibility limits, preventing a partial repository mapping from appearing exhaustive.', 'Guided fields. Prefer complete, partial or unknown; legacy full, scanned and fully-scanned remain readable. Lists contain source descriptions, one per line.', { example: 'Set partial; scanned: CRM contact module; expected: CRM migration module; limitation: migration mapping not reviewed.' }),
      field('gaps', 'Gaps', 'catalog-gaps', 'Record unresolved Git identity, code ownership or semantic mapping questions.', 'Actionable summaries appear in Open Questions and are returned to AI as explicit knowledge gaps, allowing targeted follow-up instead of invented repository links.', 'Repeatable cards. Summary is required. Optional ID is lowercase kebab-case; severity is error, warning or info; status is open or resolved.', { example: 'Add crm-migration-map, missing-evidence, info, open: Confirm which CRM migration module owns contact backfill.' }),
      field('llmToolHints', 'AI exploration guidance', 'repository-llm-tool-hints', 'List durable phrases that should lead AI to this repository and nearby repositories or concepts it must distinguish from this one.', 'answerWhenUserMentions values participate in repository discovery and are exposed with disambiguateFrom through opctx_get_entity. They guide exploration but never grant access or prove an answer.', 'Guided lists: one phrase per line in answerWhenUserMentions and one explicit contrast per line in disambiguateFrom. Describe selection guidance, not implementation answers or secrets.', { example: 'Answer when mentioned: CRM contact validation\nDisambiguate from: CRM authentication account service' }),
      field('notes', 'Notes', 'list', 'Add durable repository clarifications not represented elsewhere.', 'Preserved in the canonical entry and available in detailed context; does not affect matching unless repeated in a signal field.', 'Optional list; one note per line.', { help: 'One value per line.', example: 'CRM contract models are shared with the contact import job.' })
    ] }
  ],
  'code-search-scope': [
    { title: 'Basic', fields: [...CODE_SEARCH_BASE_FIELDS,
      field('limitations', 'Limitations', 'list', 'Describe code areas or related systems intentionally excluded from this search scope.', 'Shown to AI as a search visibility boundary so it does not assume the selected repositories provide complete coverage.', 'Optional list; one explicit limitation per line.', { help: 'One value per line.', example: 'Does not include the external CRM provider implementation.' })
    ] },
    { title: 'Target and repositories', fields: [
      field('target', 'Target', 'code-search-target', 'Choose the single semantic entity whose code this scope locates.', 'The target creates the canonical bridge from a system or bounded context to repositories and is used by code-search, flow exploration, ownership resolution and reverse mapping.', 'Structured selectors. type is strictly system or bounded-context; id must exist and scopeType is derived automatically.', { required: true, example: 'Select system and CRM Contact Core.' }),
      field('repositories', 'Repositories and search rules', 'code-search-repositories', 'List repository IDs, semantic roles, positive read priorities, reasons, readFor questions and exact GitLab search boundaries.', 'Runtime code-search reads repositories in priority order and enforces searchMode/pathPrefixes. AI uses reason/readFor to decide what to inspect and limitations to explain coverage.', 'Structured repository rows. repoId must exist and be unique; priority must be a positive integer; at least one item needs role primary or priority 1. searchMode is strictly whole-repository or path-prefixes.', { required: true, example: 'Select CRM Contact Repository as primary, priority 1 and path-prefixes apps/crm-contact.' })
    ] }
  ],
  process: [
    { title: 'Basic', fields: [...BASE_FIELDS,
      field('type', 'Process type', 'text', 'Classify the durable process or flow.', 'Exposed to process search and AI context to distinguish business processes from operational or technical flows.', 'Free text in the backend. Use the established catalogue vocabulary, for example business-process, rather than inventing per-entry labels.', { example: 'business-process' }),
      field('criticality', 'Criticality', 'text', 'Enter the impact tier of process disruption.', 'Used in catalogue views and AI impact reasoning to prioritize affected functional paths.', 'Free text in the backend. Current catalogue values are critical, high, medium, low and unknown.', { example: 'high' }),
      field('operationalOutcome', 'Operational outcome', 'textarea', 'Describe the observable business result produced when the process succeeds.', 'Gives AI and analysts a functional completion target for incident and flow interpretation.', 'Optional business-readable text; state the result, not implementation steps.', { example: 'The CRM contact profile reflects the accepted customer update.' })
    ] },
    { title: 'Flow and references', fields: [
      field('participants', 'Participants', 'process-participants', 'List actors and assign existing systems to their primary, supporting, external-system or platform-component role.', 'Every selected system becomes a typed process graph edge. AI uses the roles to reconstruct the functional path and distinguish the main system from supporting boundaries.', 'Structured selectors. System roles use existing canonical system IDs; actors are durable role labels, one per line.', { example: 'Add CRM agent, CRM Contact Core as primary and CRM Profile Store as supporting.' }),
      field('references', 'References', 'references', 'Link the process to systems, repositories, bounded contexts, integrations, glossary terms and handoff rules.', 'Creates graph navigation and lets AI enrich a process with code, domain language, integrations and handoff guidance. Processes do not own teams.', 'Structured reference pickers. Referenced IDs must exist and cannot point back to this process.', { example: 'Select CRM Contact Core, CRM Contact Sync and CRM Customer Profile.' }),
      field('steps', 'Steps', 'process-steps', 'Describe ordered business or system milestones with stable id, name, type, summary, references and optional strong business terms.', 'Step IDs, names, summaries and signals are searchable; step references create graph edges and give AI a concrete, explainable flow without embedding low-level code inventory.', 'Repeatable ordered cards. Each step needs a unique lowercase kebab-case ID and name. References must select existing catalogue entities; legacy match is preserved by the server but is not editable.', { example: 'Add accept-contact-update, reference CRM Contact Core and use the strong term accept CRM contact update.' }),
      field('processBoundary', 'Process boundary', 'process-boundary', 'Define the CRM business capability, observable start/end conditions, included responsibilities, exclusions and assumptions.', 'Runtime indexes the maintained boundary and opctx_get_entity exposes it to AI so analysis starts and stops at the intended functional scope instead of following unrelated downstream activity.', 'Guided object. businessCapability is optional text; startsWhen, endsWhen, includes, excludes and assumptions are lists of non-blank descriptions. Legacy string/list values remain readable as endsWhen.', { example: 'Capability: CRM Contact Preference Management; ends when: CRM contact view confirms the accepted update.' }),
      field('lifecycle', 'Lifecycle', 'process-lifecycle', 'Describe durable CRM triggers, entry criteria, statuses, transitions, terminal states and outcome categories.', 'Runtime indexes and exposes this state model to AI for functional flow and incident interpretation. It is descriptive operational context and never executes or configures a workflow engine.', 'Guided object. Trigger cards require type and name; transition cards require target state and trigger, while source may be empty for the initial transition. All other categories are non-blank text lists. Legacy string/list values remain readable as statuses.', { example: 'Trigger: api / CRM contact update; transition: requested → applied after CRM validation; terminal state: applied.' })
    ] },
    { title: 'Signals and advanced', fields: [
      field('completionSignals', 'Completion signals', 'completion-signals', 'Classify observable CRM evidence as successful, partial, failed or cancelled completion.', 'Runtime indexes and opctx_get_entity exposes these categories to AI so it can distinguish completed flow, incomplete progress, failure and cancellation from downstream delay. Signals guide evidence interpretation; they do not prove an incident state by themselves.', 'Guided object with successful, partial, failed and cancelled lists. Every item must be non-blank observable evidence. Legacy string/list values remain readable as successful signals.', { example: 'Successful: CRM contact confirmation recorded; partial: validation accepted but projection pending; failed: CRM validation rejected.' }),
      field('failureModes', 'Failure modes', 'failure-modes', 'Describe recognizable process failures using a stable ID, name, concise summary, optional affected step and observable signals.', 'Structured failure modes are exposed by catalogue views and opctx_get_entity, indexed as process signals and used by AI as hypotheses and handoff context. They never prove root cause by themselves.', 'Repeatable process cards. ID must be unique lowercase kebab-case; name and summary are required; affectedStep, when set, must be an existing step ID; signals are observable facts, one per line.', { example: 'Add crm-contact-validation-failed affecting validate-contact, with signal: CRM contact request rejected before persistence.' }),
      field('dataAndArtifacts', 'Data and artifacts', 'data-artifacts', 'List durable artifact kinds used by the process: primary objects, inputs, outputs, persisted entities, read models, audit artifacts and notes.', 'opctx_get_entity exposes these categories in process overview and indexes their text for search, helping AI connect evidence and milestones without storing real records.', 'Guided lists, one artifact kind per line. Describe schemas, message/document types or business objects only; never paste customer data, IDs or payloads.', { example: 'Input: anonymized CRM contact change request; output: CRM contact update confirmation; persisted entity: ContactPreference.' }),
      field('relations', 'Relations', 'relations', 'Connect this process to adjacent processes, integrations or other catalogue entities using an explicit semantic edge.', 'Validated targets become graph edges and allow AI to continue into adjacent functional or technical context.', 'Guided relation rows. Choose a non-self catalogue target or a durable external target label; legacy targetProcessId is read and normalized when edited.', { example: 'Add followed-by → process → CRM Contact Synchronization.' }),
      field('matchSignals', 'Recognition signals', 'match-signals', 'Add durable business or runtime evidence values that identify this process and assign their confidence.', 'Used by operational-context search and exposed to AI to resolve logs, routes or business phrases to the process.', 'Guided rows stored as matchSignals using exact, strong, medium or weak and process-oriented keys such as businessTerms, routes, operationNames or exchanges.', { example: 'Add strong / businessTerms / update CRM contact and medium / routes / /crm/contacts.' })
    ] }
  ],
  integration: [
    { title: 'Basic', fields: [...BASE_FIELDS,
      field('category', 'Category', 'text', 'Classify the integration boundary by its functional or transport category.', 'Used in integration filtering/search and AI context to distinguish APIs, messaging, event streams and other boundary kinds.', 'Free text in the backend. Current conventions include internal-api, external-api, gateway-route, messaging, event-stream and notification.', { example: 'internal-api' }),
      field('integrationStyle', 'Integration style', 'text', 'Describe the high-level interaction style.', 'AI uses this to reason about synchronous versus asynchronous failure propagation; read models expose it as integration metadata.', 'Free text in the backend. Current catalogue conventions include synchronous, synchronous-request, asynchronous, async-message, event-stream, gateway-mediated and mixed.', { example: 'synchronous-request' }),
      field('flowDirection', 'Flow direction', 'text', 'Describe how information moves between participants.', 'The relation builder uses participant roles for edges; validators and AI also interpret bidirectional-like values when explaining both sides of a boundary.', 'Free text in the backend. Current conventions include source-to-target, request-response, bidirectional and fanout.', { example: 'request-response' }),
      field('criticality', 'Criticality', 'text', 'Enter the impact tier of this integration failing.', 'Displayed to operators and AI for impact prioritization and handoff reasoning.', 'Free text in the backend. Current catalogue values are critical, high, medium, low and unknown.', { example: 'high' }),
      field('dataSensitivity', 'Data sensitivity', 'text', 'Classify the sensitivity of data crossing the boundary.', 'Gives operators and AI a handling constraint when explaining diagnostics; it is descriptive and does not implement access control.', 'Free text in the backend. Current catalogue conventions include internal and confidential.', { example: 'confidential' })
    ] },
    { title: 'Participants and references', fields: [
      field('participants', 'Participants', 'integration-participants', 'Define source and at least one targets or finalTargets item; optionally add intermediaries. Each participant can identify a system, bounded context, role, external owner and notes.', 'Participants create directed graph edges, power related-entity views and tell AI where an integration starts, passes through and ends.', 'Structured participant cards. source is required; targets or finalTargets must be non-empty. Selected system and bounded-context IDs must exist. Legacy participant repositories are server-owned; use top-level references and code-search scopes.', { required: true, example: 'Select CRM Contact Core as source/client and CRM Profile Store as target/server.' }),
      field('references', 'References', 'references', 'Link the integration to related systems, processes, bounded contexts, repositories, terms and handoff rules.', 'Adds validated graph navigation and richer AI context around the boundary. Ownership remains on systems or bounded contexts.', 'Structured reference pickers. Every selected ID must exist and the integration cannot reference itself.', { example: 'Select CRM Contact Update, CRM Customer Profile and CRM Contact Sync Delayed.' })
    ] },
    { title: 'Signals and advanced', fields: [
      field('matchSignals', 'Recognition signals', 'match-signals', 'Add durable endpoints, queues, routes, bindings or business phrases that identify this integration.', 'Used by search/resolution and exposed to AI so technical evidence maps to the correct boundary.', 'Guided rows stored as matchSignals using exact, strong, medium or weak and integration-oriented signal keys.', { example: 'Add exact / routes / /crm/contacts/sync and strong / operationNames / sync CRM contact.' }),
      field('relations', 'Relations', 'relations', 'Describe additional semantic edges not already captured by participants and references.', 'Becomes graph context for operators and AI; avoid duplicating participant edges without adding meaning.', 'Guided relation rows with a canonical catalogue target or durable external target label.', { example: 'Add supports → process → CRM Contact Update.' }),
      field('failureModes', 'Failure modes', 'failure-modes', 'Describe recognizable integration failures with a name, type, observable symptom and functional/technical impact.', 'Catalogue views and opctx_get_entity expose the structured cards as integration signals. AI uses them for boundary hypotheses and evidence-aware handoff, never as automatic diagnosis.', 'Repeatable integration cards. Name and type are required; provide at least a symptom or impact. Type is a stable category such as timeout, upstream-error, rejected-message or unavailable.', { example: 'Add CRM profile timeout, type timeout, symptom: contact response exceeds the agreed window, impact: CRM update cannot continue.' })
    ] }
  ],
  'bounded-context': [
    { title: 'Basic', fields: [...BASE_FIELDS,
      field('type', 'Context type', 'text', 'Classify the domain boundary.', 'Exposed in catalogue search and AI context to distinguish core, supporting and other domain slices.', 'Free text in the backend. Use the established catalogue vocabulary, such as core-domain, supporting-domain, domain or subdomain.', { example: 'core-domain' }),
      field('localLanguageSummary', 'Local language summary', 'bounded-local-language', 'Enter durable statements explaining how important terms are understood inside this bounded context.', 'The statements are searchable and exposed to AI so evidence is translated into the context local language instead of a nearby meaning.', 'Guided list, one complete semantic statement per line. Existing scalar text remains readable and is normalized to a list only after editing.', { example: 'In CRM Contact Preferences, contact means a communication profile, not an authentication account.' })
    ] },
    { title: 'Ownership and references', fields: [
      field('ownership', 'Ownership', 'ownership', 'Provide bounded-context ownership: ownerTeamIds, optional ownerLabel, ownershipStatus, confidence, source and notes.', 'Explicit bounded-context ownership has priority over system ownership in the resolver and is used for AI handoff and impact explanation.', 'Structured controls. ownershipStatus explicit requires an existing team ID or ownerLabel. confidence is high, medium or low.', { example: 'Select CRM Domain Team, explicit and high.' }),
      field('references', 'References', 'references', 'Link the context to systems, processes, integrations, terms, teams and handoff rules.', 'Builds semantic graph navigation, validates delete impact and helps AI move from domain language to systems and processes. Code navigation should normally go through a related system scope.', 'Structured reference pickers. Referenced IDs must exist and cannot self-reference this bounded context.', { example: 'Select CRM Contact Core, CRM Contact Update and CRM Customer Profile.' }),
      field('scope', 'Scope', 'bounded-scope', 'State what the context includes and excludes, which business capabilities it serves, its core entities and durable decisions.', 'Runtime indexes these lists and AI uses them as an explicit analysis boundary when deciding whether evidence belongs to this context.', 'Guided lists for includes, excludes, businessCapabilities, coreEntities and keyDecisions. Enter one durable item per line.', { example: 'Include CRM contact preference validation; exclude authentication credentials; capability: CRM Contact Preference Management.' }),
      field('semanticBoundary', 'Semantic boundary', 'bounded-semantic-boundary', 'Describe concepts, language, commands, events and invariants owned by this context and what it explicitly does not own.', 'The known fields are indexed and exposed to AI to prevent semantic conflation and explain translations or handoffs across contexts.', 'Guided lists for coreConcepts, localConcepts, canonicalEntities, commands, events, invariants, ownsLanguage and doesNotOwn.', { example: 'Own language: CRM contact preference; does not own: authentication account credential.' })
    ] },
    { title: 'Signals and advanced', fields: [
      field('matchSignals', 'Recognition signals', 'match-signals', 'Add durable domain phrases, routes, configuration keys or other evidence that indicates this bounded context.', 'Used by search and ownership inference to map evidence to the context; AI sees confidence-grouped signals.', 'Guided rows stored as matchSignals using exact, strong, medium or weak and bounded-context-oriented signal keys.', { example: 'Add strong / businessTerms / CRM contact preference and medium / routes / /crm/contacts.' }),
      field('relations', 'Relations', 'relations', 'Describe meaningful relations to other bounded contexts or catalogue entities.', 'Canonical targets become graph edges for context navigation, ownership resolution and AI reasoning.', 'Guided relation rows. A context cannot target itself; legacy targetContextId is read and normalized when edited.', { example: 'Add upstream-of → bounded-context → CRM Engagement Context.' }),
      field('evidence', 'Evidence', 'bounded-evidence', 'Record stable sources supporting the semantic boundary, scope or ownership description.', 'Known evidence cards are exposed as provenance in the operator detail and opctx_get_entity; runtime never fetches them automatically.', 'Guided cards with required sourceRef and evidenceType plus optional note. Use durable labels or safe relative paths, never customer records or copied source.', { example: 'sourceRef: CRM domain glossary review; evidenceType: domain-documentation; note: Anonymized CRM terminology review.' }),
      field('sourceCoverage', 'Source coverage', 'source-coverage', 'Describe which durable domain sources were reviewed, which were expected and the remaining visibility limits.', 'opctx_get_entity exposes this section; limitations become AI visibility limits so a partial bounded-context model is never presented as exhaustive.', 'Guided fields. Prefer complete, partial or unknown; legacy full, scanned and fully-scanned remain readable. Enter source descriptions, one per line.', { example: 'Set partial; scanned: anonymized CRM domain notes; expected: CRM consent model; limitation: engagement boundary not reviewed.' }),
      field('gaps', 'Gaps', 'catalog-gaps', 'Record unresolved boundary, ownership, terminology or evidence questions.', 'Every actionable summary feeds Open Questions and opctx_get_entity, explicitly constraining AI conclusions until the catalogue question is resolved.', 'Repeatable cards. Summary is required. Optional ID is lowercase kebab-case; severity is error, warning or info; status is open or resolved.', { example: 'Add crm-consent-boundary, unresolved-boundary, warning, open: Confirm boundary between CRM consent and engagement policy.' }),
      field('llmToolHints', 'LLM tool hints', 'bounded-llm-tool-hints', 'Provide discovery phrases, explicit contrasts, safe search keywords and a concise explanation style for this context.', 'Known hints are exposed to AI and discovery, but do not create ownership, references, access policy or code-search boundaries.', 'Guided answerWhenUserMentions, disambiguateFrom and usefulSearchKeywords lists plus optional explanationStyle text.', { example: 'Mention: CRM contact preference; contrast: authentication account; keyword: ContactPreference; style: Explain as the CRM preference boundary.' })
    ] }
  ],
  team: [
    { title: 'Basic', fields: [...BASE_FIELDS,
      field('type', 'Team type', 'text', 'Classify the durable team identity.', 'Used in team views and AI explanations; ownership is assigned from system or bounded-context ownership fields, not inferred merely from this type.', 'Free text in the backend. Use the established organization vocabulary, such as product or platform.', { example: 'product' })
    ] },
    { title: 'Signals', fields: [
      field('matchSignals', 'Recognition signals', 'match-signals', 'Add stable team names, aliases, email aliases or collaboration identifiers and assign their confidence.', 'Improves team search and helps AI recognize the same team label in evidence; it does not make the team an owner by itself.', 'Guided rows stored as matchSignals using exact, strong, medium or weak and team-oriented signal keys.', { example: 'Add exact / teamNames / CRM Domain Team and strong / aliases / crm-domain-team.' })
    ] }
  ],
  'glossary-term': [
    { title: 'Basic', fields: [
      field('id', 'ID', 'text', 'Enter a stable, unique glossary identifier.', 'Canonical references, relatedTerms, graph relations and API URLs use this ID; AI sees it as the term key.', 'Required lowercase kebab-case; immutable after creation.', { required: true, help: 'Stable term ID. It cannot be renamed after creation.', example: 'crm-customer-profile' }),
      field('term', 'Term', 'text', 'Enter the business or technical phrase users actually encounter.', 'The term is the primary glossary label returned by search and AI tools.', 'Required non-blank text.', { required: true, example: 'Customer profile' }),
      field('category', 'Category', 'text', 'Classify the kind of term.', 'Used by glossary filtering/search and helps AI interpret whether this is domain language, integration language or a technical term.', 'Required free text. Current conventions include domain-term, business-term, integration-term and technical-term.', { required: true, example: 'domain-term' }),
      field('lifecycleStatus', 'Lifecycle status', 'text', 'Enter whether the term is current, planned, deprecated or retired.', 'Exposed to glossary search and AI so obsolete language is not treated as the preferred current vocabulary.', 'Free text in the backend. Maintenance convention: active, planned, deprecated or retired.', { example: 'active' }),
      field('definition', 'Definition', 'textarea', 'Define the term in concise, context-independent language.', 'AI uses this definition to ground explanations and translate technical evidence into consistent business language.', 'Optional plain text; define one concept without implementation detail.', { example: 'The CRM representation of contact and preference information used in customer interactions.' })
    ] },
    { title: 'Meaning and usage', fields: [
      field('localMeaningAndBoundaries', 'Local meaning and boundaries', 'list', 'Explain how the term is used locally and what is outside its meaning.', 'AI uses these statements to resolve context-dependent language and avoid semantic overreach.', 'Optional list; one boundary statement per line.', { help: 'One value per line.', example: 'In CRM, the profile covers contact preferences but not authentication credentials.' }),
      field('aliases', 'Aliases', 'list', 'Enter alternative spellings, abbreviations or historical names.', 'Included in glossary matching and search so AI can map evidence language to the canonical term.', 'Optional list; one alias per line.', { help: 'One value per line.', example: 'CRM profile\ncustomer contact profile' }),
      field('useFor', 'Use for', 'list', 'State the questions or explanations for which this term should be used.', 'Guides AI when selecting the term as grounding context.', 'Optional list; one use case per line.', { help: 'One value per line.', example: 'Explain CRM contact ownership and preference handling.' }),
      field('doNotConfuseWith', 'Do not confuse with', 'list', 'List nearby concepts that have a different meaning.', 'AI uses this disambiguation to avoid merging distinct catalogue concepts.', 'Optional list; use clear term names or short contrasts, one per line.', { help: 'One value per line.', example: 'Authentication account' })
    ] },
    { title: 'References and advanced', fields: [
      field('matchSignals', 'Recognition signals', 'match-signals', 'Add phrases, aliases, field names or other durable evidence that identifies this glossary meaning.', 'Used by glossary search and AI term resolution to match operational evidence to this definition.', 'Guided rows stored as matchSignals using exact, strong, medium or weak and glossary-oriented signal keys, including existing specialized keys.', { example: 'Add exact / phrases / customer profile and strong / aliases / CRM profile.' }),
      field('canonicalReferences', 'Canonical references', 'list', 'Link the term to canonical catalogue entities using type:id.', 'Validated typed references create graph navigation from language to systems, processes, integrations and other supported entities.', 'One existing type:id per line. Supported types are system, repository, code-search-scope, process, integration, bounded-context, team, glossary-term/term and handoff-rule.', { help: 'One type:id reference per line.', example: 'system:crm-contact-core\nbounded-context:crm-customer-context' }),
      field('relatedTerms', 'Related terms', 'list', 'Enter IDs of other glossary terms with related meaning.', 'Creates glossary-to-glossary graph links for AI exploration and comparison.', 'One existing glossary term ID per line; no self-reference.', { help: 'One value per line.', example: 'crm-contact-preference' }),
      field('responsibilityHints', 'Responsibility hints', 'list', 'Describe which system or context usually explains the concept, without assigning a team owner.', 'AI uses these hints to continue context discovery; the ownership resolver still relies only on system or bounded-context ownership.', 'Optional descriptive statements, one per line. Do not put routing rules or team ownership here.', { help: 'One value per line.', example: 'Resolve responsibility through the CRM customer bounded context.' }),
      field('llmToolHints', 'LLM tool hints', 'list', 'Give AI concise instructions for using this term during evidence interpretation.', 'Directly guides AI terminology grounding and next reads; it does not alter the catalogue graph.', 'Optional evidence-oriented guidance, one item per line.', { help: 'One value per line.', example: 'Use this term only for CRM contact and preference semantics.' }),
      field('notes', 'Notes', 'list', 'Add durable clarifications or provenance notes.', 'Preserved with the glossary entry and available in detailed AI context.', 'Optional list; one note per line, without sensitive examples.', { help: 'One value per line.', example: 'Definition reviewed against anonymized CRM domain documentation.' })
    ] }
  ],
  'handoff-rule': [
    { title: 'Basic', fields: [
      field('id', 'ID', 'text', 'Enter a stable, unique handoff-rule identifier.', 'References, graph navigation and API URLs use this ID; AI uses it as the canonical rule key.', 'Required lowercase kebab-case; immutable after creation.', { required: true, help: 'Stable rule ID. It cannot be renamed after creation.', example: 'crm-contact-sync-delayed' }),
      field('title', 'Title', 'text', 'Name the recognizable situation in which this handoff rule may apply.', 'Displayed as the rule label and supplied to AI when matching evidence to a handoff scenario.', 'Required, concise, situation-oriented text.', { required: true, example: 'CRM contact synchronization is delayed' }),
      field('confidence', 'Confidence', 'text', 'State how strongly maintained evidence supports this rule.', 'AI and operators use it to calibrate the handoff suggestion; it does not override missing required evidence.', 'Free text in the backend. Maintenance convention: high, medium or low.', { example: 'medium' })
    ] },
    { title: 'Decision and evidence', fields: [
      field('useWhen', 'Use when', 'list', 'List evidence-backed conditions that make this handoff relevant.', 'AI compares the observed situation with these conditions before recommending the rule.', 'Optional list; one observable condition per line. Avoid vague symptoms.', { help: 'One value per line.', example: 'A CRM contact update succeeded locally but is absent from the downstream CRM view.' }),
      field('doNotUseWhen', 'Do not use when', 'list', 'List conditions that explicitly exclude this handoff.', 'AI uses these negative conditions to suppress a misleading route even when some positive signals match.', 'Optional list; one disqualifying condition per line.', { help: 'One value per line.', example: 'The CRM contact update is still inside its documented processing window.' }),
      field('requiredEvidence', 'Required evidence', 'list', 'State the minimum evidence that must be collected before handoff.', 'AI uses this as an evidence checklist and should expose missing items as visibility limits.', 'Optional list; one concrete, collectible evidence item per line; never include real customer data.', { help: 'One value per line.', example: 'An anonymized CRM correlation key and timestamps from both integration sides.' }),
      field('expectedFirstAction', 'Expected first action', 'list', 'Describe the first useful verification or repair action for the receiving side.', 'AI includes this in the technical handoff so the next operator can start with a concrete step.', 'Optional ordered list; put the first action on the first line and keep actions evidence-based.', { help: 'One value per line.', example: 'Verify the CRM contact synchronization boundary using the collected correlation evidence.' })
    ] },
    { title: 'Context and advanced', fields: [
      field('references', 'Operational context references', 'references', 'Link the rule to supporting systems, processes, bounded contexts, integrations, repositories and glossary terms.', 'Creates graph navigation so AI can read the relevant context before applying the rule. It must not encode a team owner.', 'Structured reference pickers. Every selected ID must exist.', { example: 'Select CRM Contact Core, CRM Contact Update and CRM Customer Profile.' }),
      field('affectedSystems', 'Affected systems', 'list', 'List system targets explicitly affected by this scenario.', 'AI uses these typed targets to scope impact and fetch the correct system context.', 'One system:id typed reference per line.', { help: 'One value per line.', example: 'system:crm-contact-core' }),
      field('affectedProcesses', 'Affected processes', 'list', 'List process targets affected by this scenario.', 'AI uses them to explain functional impact and expected process outcome.', 'One process:id typed reference per line.', { help: 'One value per line.', example: 'process:crm-contact-update' }),
      field('affectedIntegrations', 'Affected integrations', 'list', 'List integration boundaries implicated by this scenario.', 'AI uses them to retrieve participant, direction and failure-mode context for handoff.', 'One integration:id typed reference per line.', { help: 'One value per line.', example: 'integration:crm-contact-sync' }),
      field('notes', 'Notes', 'list', 'Add durable clarification or provenance for this handoff rule.', 'Preserved and shown in detailed context; notes should not replace required evidence or applicability conditions.', 'Optional list; one note per line.', { help: 'One value per line.', example: 'Rule uses only anonymized CRM evidence descriptions.' }),
      field('llmToolHints', 'LLM tool hints', 'list', 'Tell AI which evidence or operational-context reads to perform before applying the rule.', 'Directly guides AI tool usage and handoff preparation while leaving ownership resolution to systems and bounded contexts.', 'Optional evidence-oriented guidance, one item per line.', { help: 'One value per line.', example: 'Read both CRM integration participants before proposing a handoff.' }),
      field('limitations', 'Limitations', 'list', 'Describe cases or visibility boundaries this rule does not cover.', 'AI exposes these as limitations and avoids presenting the rule as universally applicable.', 'Optional list; one explicit limitation per line.', { help: 'One value per line.', example: 'Does not diagnose the external CRM provider implementation.' })
    ] }
  ]
};

export function operationalContextFieldTooltip(field: OperationalContextFormField): string {
  const guidance = field.guidance;
  return [
    `What to enter: ${guidance.whatToEnter}`,
    `Runtime / AI effect: ${guidance.runtimeEffect}`,
    `Format / values: ${guidance.acceptedValues}`,
    `CRM example: ${guidance.example}`
  ].join('\n\n');
}

export class OperationalContextFormAdapter {
  sections(type: OperationalContextWritableType): OperationalContextFormSection[] {
    return TYPE_FIELDS[type];
  }

  fields(type: OperationalContextWritableType): OperationalContextFormField[] {
    return this.sections(type).flatMap((section) => section.fields);
  }

  build(type: OperationalContextWritableType, payload: OperationalContextPayload): FormGroup<Record<string, FormControl<string>>> {
    const controls: Record<string, FormControl<string>> = {};
    for (const field of this.fields(type)) {
      const value = this.render(field, payload[field.path]);
      controls[this.key(field.path)] = new FormControl(value, { nonNullable: true, validators: field.required ? Validators.required : [] });
    }
    return new FormGroup(controls);
  }

  payload(type: OperationalContextWritableType, form: FormGroup<Record<string, FormControl<string>>>, original: OperationalContextPayload): OperationalContextPayload {
    const result: OperationalContextPayload = structuredClone(original || {});
    for (const field of this.fields(type)) {
      const raw = String(form.controls[this.key(field.path)]?.value ?? '').trim();
      if (!raw) {
        delete result[field.path];
      } else if (this.isStructuredPayloadKind(field.kind)) {
        result[field.path] = JSON.parse(raw);
      } else if (field.kind === 'list') {
        result[field.path] = raw.split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
      } else {
        result[field.path] = raw;
      }
    }
    if (type === 'code-search-scope') {
      const target = result['target'];
      if (target && typeof target === 'object' && !Array.isArray(target)) {
        const targetType = String((target as Record<string, unknown>)['type'] || '').trim();
        if (targetType) result['scopeType'] = targetType;
      }
    }
    this.removeServerOwnedNestedFields(type, result);
    return result;
  }

  controlName(path: string): string {
    return this.key(path);
  }

  fieldForPointer(type: OperationalContextWritableType, pointer: string): OperationalContextFormField | null {
    const path = pointer.replace(/^\/(payload\/)?/, '').replaceAll('/', '.').replaceAll('~1', '/').replaceAll('~0', '~');
    return this.fields(type).find((field) => path === field.path || path.startsWith(`${field.path}.`)) || null;
  }

  private render(field: OperationalContextFormField, value: unknown): string {
    if (value === null || value === undefined) return '';
    if (this.isStructuredPayloadKind(field.kind) && !Array.isArray(value) && typeof value === 'object' && Object.keys(value).length === 0) return '';
    if (this.isStructuredPayloadKind(field.kind)) return JSON.stringify(value, null, 2);
    if (field.kind === 'list') return Array.isArray(value) ? value.join('\n') : String(value);
    return String(value);
  }

  private key(path: string): string {
    return path.replaceAll('.', '__');
  }

  private isStructuredPayloadKind(kind: OperationalContextFieldKind): boolean {
    return [
      'system-participants',
      'system-runtime',
      'repository-evidence',
      'repository-llm-tool-hints',
      'bounded-local-language',
      'bounded-scope',
      'bounded-semantic-boundary',
      'bounded-evidence',
      'bounded-llm-tool-hints',
      'ownership',
      'references',
      'code-search-target',
      'code-search-repositories',
      'integration-participants',
      'repository-git',
      'process-participants',
      'process-steps',
      'process-boundary',
      'process-lifecycle',
      'completion-signals',
      'match-signals',
      'relations',
      'failure-modes',
      'data-artifacts',
      'source-coverage',
      'catalog-gaps'
    ].includes(kind);
  }

  private removeServerOwnedNestedFields(type: OperationalContextWritableType, payload: OperationalContextPayload): void {
    if (type === 'repository' && payload['git'] && typeof payload['git'] === 'object' && !Array.isArray(payload['git'])) {
      delete (payload['git'] as Record<string, unknown>)['inferred'];
    }
    if (type === 'process' && Array.isArray(payload['steps'])) {
      for (const step of payload['steps']) {
        if (step && typeof step === 'object' && !Array.isArray(step)) delete (step as Record<string, unknown>)['match'];
      }
    }
    if (type === 'integration' && payload['participants'] && typeof payload['participants'] === 'object' && !Array.isArray(payload['participants'])) {
      const participants = payload['participants'] as Record<string, unknown>;
      removeParticipantRepositories(participants['source']);
      for (const group of ['targets', 'intermediaries', 'finalTargets']) {
        if (Array.isArray(participants[group])) participants[group].forEach(removeParticipantRepositories);
      }
    }
  }
}

function removeParticipantRepositories(value: unknown): void {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    delete (value as Record<string, unknown>)['repositories'];
  }
}
