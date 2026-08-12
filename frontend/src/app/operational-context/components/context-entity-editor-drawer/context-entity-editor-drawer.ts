import { Component, effect, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  OperationalContextEditorState,
  OperationalContextFieldError,
  OperationalContextPayload,
  OperationalContextReferenceOptions
} from '../../models/operational-context-maintenance.models';
import { ContextStructuredFieldEditorComponent } from '../context-structured-field-editor/context-structured-field-editor';
import {
  OperationalContextFormAdapter,
  OperationalContextFormField,
  operationalContextFieldTooltip
} from './operational-context-form-adapter';

@Component({
  selector: 'app-context-entity-editor-drawer',
  imports: [ReactiveFormsModule, MatIconModule, MatTooltipModule, ContextStructuredFieldEditorComponent],
  templateUrl: './context-entity-editor-drawer.html',
  styleUrl: './context-entity-editor-drawer.scss'
})
export class ContextEntityEditorDrawerComponent {
  readonly state = input.required<OperationalContextEditorState>();
  readonly busy = input(false);
  readonly error = input('');
  readonly fieldErrors = input<OperationalContextFieldError[]>([]);
  readonly referenceOptions = input<OperationalContextReferenceOptions>({});
  readonly readonly = input(false);
  readonly chrome = input(true);
  readonly saveEntity = output<OperationalContextPayload>();
  readonly cancelEditor = output<void>();
  readonly dirtyChange = output<boolean>();

  readonly adapter = new OperationalContextFormAdapter();
  readonly structuredError = signal('');
  form = new FormGroup<Record<string, FormControl<string>>>({});
  private signature = '';

  constructor() {
    effect(() => {
      const state = this.state();
      const signature = `${state.mode}:${state.type}:${state.entity.id}:${JSON.stringify(state.entity.payload)}`;
      if (signature !== this.signature) {
        this.signature = signature;
        this.form = this.adapter.build(state.type, state.entity.payload);
        this.applyDisabledState(state);
        this.form.valueChanges.subscribe(() => this.dirtyChange.emit(this.form.dirty));
      }
      this.applyDisabledState(state);
      this.applyFieldErrors();
    });
  }

  submit(): void {
    if (this.readonly()) return;
    this.structuredError.set('');
    this.validateStructuredControls();
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    try {
      const state = this.state();
      const payload = this.adapter.payload(state.type, this.form, state.entity.payload);
      if (state.mode === 'edit') payload['id'] = state.entity.id;
      this.saveEntity.emit(payload);
    } catch {
      this.structuredError.set('Correct the invalid structured fields before saving.');
    }
  }

  controlName(field: OperationalContextFormField): string {
    return this.adapter.controlName(field.path);
  }

  fieldTooltip(field: OperationalContextFormField): string {
    return operationalContextFieldTooltip(field);
  }

  isStructuredField(field: OperationalContextFormField): boolean {
    return [
      'ownership',
      'system-participants',
      'system-runtime',
      'repository-evidence',
      'repository-llm-tool-hints',
      'bounded-local-language',
      'bounded-scope',
      'bounded-semantic-boundary',
      'bounded-evidence',
      'bounded-llm-tool-hints',
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
    ].includes(field.kind);
  }

  structuredValue(field: OperationalContextFormField): unknown {
    const raw = this.form.controls[this.controlName(field)]?.value;
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  updateStructuredField(field: OperationalContextFormField, value: unknown): void {
    if (this.readonly()) return;
    const control = this.form.controls[this.controlName(field)];
    const empty = value === null
      || value === undefined
      || (Array.isArray(value) && value.length === 0)
      || (typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0);
    control.setValue(empty ? '' : JSON.stringify(value, null, 2));
    control.markAsDirty();
    const validationError = empty ? '' : this.structuredValidationError(field, value);
    if (validationError) control.setErrors({ structured: validationError });
    else control.updateValueAndValidity({ emitEvent: false });
    if (field.kind === 'code-search-target' && value && typeof value === 'object') {
      const targetType = String((value as Record<string, unknown>)['type'] || '');
      const scopeTypeControl = this.form.controls[this.adapter.controlName('scopeType')];
      if (scopeTypeControl && scopeTypeControl.value !== targetType) {
        scopeTypeControl.setValue(targetType);
        scopeTypeControl.markAsDirty();
      }
    }
  }

  controlError(field: OperationalContextFormField): string {
    const control = this.form.controls[this.controlName(field)];
    return control?.hasError('server')
      ? String(control.getError('server'))
      : control?.hasError('structured')
        ? String(control.getError('structured'))
        : control?.hasError('required') && control.touched
          ? `${field.label} is required.`
          : '';
  }

  private structuredValidationError(field: OperationalContextFormField, value: unknown): string {
    const object = value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
    if (field.kind === 'ownership' && object['ownershipStatus'] === 'explicit') {
      const teams = Array.isArray(object['ownerTeamIds']) ? object['ownerTeamIds'] : [];
      if (!teams.length && !String(object['ownerLabel'] || '').trim()) {
        return 'Explicit ownership requires an owner team or fallback owner label.';
      }
    }
    if (field.kind === 'system-participants') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'System participant data must use the guided external owner field.';
      if (object['externalOwner'] !== undefined && !isNonBlankText(object['externalOwner'])) {
        return 'External owner must be non-blank text.';
      }
    }
    if (field.kind === 'system-runtime') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'System runtime data must use the guided configuration directory field.';
      const directory = object['configurationDirectory'];
      if (directory !== undefined && !isSafeConfigurationDirectory(directory)) {
        return 'Configuration directory must be a safe repository-relative path.';
      }
    }
    if (field.kind === 'repository-evidence') {
      if (!Array.isArray(value)) return 'Repository evidence must be a list of guided cards.';
      for (const evidence of value as unknown[]) {
        if (!evidence || typeof evidence !== 'object' || Array.isArray(evidence)) return 'Every repository evidence item must be a guided card.';
        const card = evidence as Record<string, unknown>;
        if (!isNonBlankText(card['sourceRef'])) return 'Every repository evidence item requires a source reference.';
        if (!isNonBlankText(card['evidenceType'])) return 'Every repository evidence item requires an evidence type.';
        if (card['note'] !== undefined && !isNonBlankText(card['note'])) return 'Repository evidence note must be non-blank text.';
      }
    }
    if (field.kind === 'repository-llm-tool-hints') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'AI exploration guidance must use the guided phrase lists.';
      for (const key of ['answerWhenUserMentions', 'disambiguateFrom']) {
        if (object[key] !== undefined && !isNonBlankTextList(object[key])) return `${key} must be a list of non-blank guidance phrases.`;
      }
    }
    if (field.kind === 'bounded-local-language' && !isLegacyTextShape(value)) {
      return 'Local-language summary must contain non-blank guided statements.';
    }
    if (field.kind === 'bounded-scope') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'Bounded-context scope must use the guided responsibility lists.';
      for (const key of ['includes', 'excludes', 'businessCapabilities', 'coreEntities', 'keyDecisions']) {
        if (object[key] !== undefined && !isNonBlankTextList(object[key])) return `${key} must be a list of non-blank scope descriptions.`;
      }
    }
    if (field.kind === 'bounded-semantic-boundary') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'Semantic boundary must use the guided concept lists.';
      for (const key of ['coreConcepts', 'localConcepts', 'canonicalEntities', 'commands', 'events', 'invariants', 'ownsLanguage', 'doesNotOwn']) {
        if (object[key] !== undefined && !isNonBlankTextList(object[key])) return `${key} must be a list of non-blank semantic descriptions.`;
      }
    }
    if (field.kind === 'bounded-evidence') {
      if (!Array.isArray(value)) return 'Bounded-context evidence must be a list of guided cards.';
      for (const evidence of value as unknown[]) {
        if (!evidence || typeof evidence !== 'object' || Array.isArray(evidence)) return 'Every bounded-context evidence item must be a guided card.';
        const card = evidence as Record<string, unknown>;
        if (!isNonBlankText(card['sourceRef'])) return 'Every bounded-context evidence item requires a source reference.';
        if (!isNonBlankText(card['evidenceType'])) return 'Every bounded-context evidence item requires an evidence type.';
        if (card['note'] !== undefined && !isNonBlankText(card['note'])) return 'Bounded-context evidence note must be non-blank text.';
      }
    }
    if (field.kind === 'bounded-llm-tool-hints') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'Bounded-context AI guidance must use the guided fields.';
      for (const key of ['answerWhenUserMentions', 'disambiguateFrom', 'usefulSearchKeywords']) {
        if (object[key] !== undefined && !isNonBlankTextList(object[key])) return `${key} must be a list of non-blank guidance phrases.`;
      }
      if (object['explanationStyle'] !== undefined && !isNonBlankText(object['explanationStyle'])) {
        return 'Explanation style must be non-blank text.';
      }
    }
    if (field.kind === 'code-search-target') {
      if (!['system', 'bounded-context'].includes(String(object['type'] || '')) || !String(object['id'] || '').trim()) {
        return 'Choose a system or bounded context target.';
      }
    }
    if (field.kind === 'code-search-repositories') {
      const repositories = Array.isArray(value) ? value as Array<Record<string, unknown>> : [];
      if (!repositories.length) return 'Add at least one primary repository.';
      if (!repositories.some((repository) => repository['role'] === 'primary' || Number(repository['priority']) === 1)) {
        return 'At least one repository must be primary or priority 1.';
      }
      const repositoryIds = repositories
        .map((repository) => String(repository['repoId'] || '').trim())
        .filter(Boolean);
      if (new Set(repositoryIds).size !== repositoryIds.length) {
        return 'Each repository can appear only once in a code-search scope.';
      }
      for (const repository of repositories) {
        const priority = Number(repository['priority']);
        const mode = String(repository['searchMode'] || '');
        const prefixes = Array.isArray(repository['pathPrefixes']) ? repository['pathPrefixes'].map(String) : [];
        if (!String(repository['repoId'] || '').trim()) return 'Choose a repository in every row.';
        if (!Number.isInteger(priority) || priority < 1) return 'Repository priority must be a positive integer.';
        if (!['whole-repository', 'path-prefixes'].includes(mode)) return 'Choose a search mode in every row.';
        if (mode === 'path-prefixes' && !prefixes.length) return 'Path-prefixes search requires at least one path prefix.';
        if (mode === 'whole-repository' && prefixes.length) return 'Whole-repository search cannot contain path prefixes.';
        if (prefixes.some((prefix) => prefix.startsWith('/') || prefix.includes('..') || prefix.includes('\\'))) {
          return 'Path prefixes must be safe relative paths without a leading /, .. or backslashes.';
        }
      }
    }
    if (field.kind === 'integration-participants') {
      const source = object['source'] && typeof object['source'] === 'object' ? object['source'] as Record<string, unknown> : {};
      const participantPresent = (participant: Record<string, unknown>) =>
        Boolean(String(participant['system'] || participant['boundedContext'] || participant['externalOwner'] || '').trim());
      if (!participantPresent(source)) return 'Choose a source system/context or provide an external owner.';
      const targets = [
        ...(Array.isArray(object['targets']) ? object['targets'] : []),
        ...(Array.isArray(object['finalTargets']) ? object['finalTargets'] : [])
      ] as Array<Record<string, unknown>>;
      if (!targets.length || targets.some((target) => !participantPresent(target))) {
        return 'Add at least one complete target or final target.';
      }
    }
    if (field.kind === 'repository-git') {
      if (!String(object['project'] || '').trim() && !String(object['projectPath'] || '').trim()) {
        return 'Enter a Git project or provider-relative project path.';
      }
    }
    if (field.kind === 'process-steps') {
      const steps = Array.isArray(value) ? value as Array<Record<string, unknown>> : [];
      const ids = steps.map((step) => String(step['id'] || '').trim()).filter(Boolean);
      if (new Set(ids).size !== ids.length) return 'Every process step must have a unique ID.';
      for (const step of steps) {
        const id = String(step['id'] || '').trim();
        if (!id) return 'Every process step requires an ID.';
        if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(id)) return 'Process step IDs must use lowercase kebab-case.';
        if (!String(step['name'] || '').trim()) return 'Every process step requires a name.';
      }
    }
    if (field.kind === 'match-signals') {
      const strengths = ['exact', 'strong', 'medium', 'weak'];
      const tiered = strengths.some((strength) => Object.prototype.hasOwnProperty.call(object, strength));
      const buckets = tiered ? strengths.map((strength) => [strength, object[strength]] as const) : [['strong', object] as const];
      for (const [strength, rawBucket] of buckets) {
        if (rawBucket === undefined) continue;
        if (!rawBucket || typeof rawBucket !== 'object' || Array.isArray(rawBucket)) {
          return `The ${strength} signal bucket must contain signal keys and value lists.`;
        }
        for (const [key, rawValues] of Object.entries(rawBucket as Record<string, unknown>)) {
          const values = Array.isArray(rawValues) ? rawValues.map(String).filter((item) => item.trim()) : [];
          if (!key.trim()) return 'Every recognition signal requires a signal key.';
          if (!values.length) return `Recognition signal ${strength} / ${key} requires at least one value.`;
        }
      }
    }
    if (field.kind === 'relations') {
      const relations = Array.isArray(value) ? value as Array<Record<string, unknown>> : [];
      const supportedTypes = new Set([
        'system', 'repository', 'code-search-scope', 'process', 'integration',
        'bounded-context', 'team', 'glossary-term', 'handoff-rule'
      ]);
      const relationKeys = new Set<string>();
      for (const relation of relations) {
        const relationType = String(relation['type'] || '').trim();
        const targetType = normalizeRelationTargetType(String(
          relation['targetType'] || (relation['targetContextId'] ? 'bounded-context' : relation['targetProcessId'] ? 'process' : '')
        ));
        const target = String(relation['target'] || relation['targetContextId'] || relation['targetProcessId'] || '').trim();
        const externalTarget = String(relation['externalSystem'] || '').trim();
        if (!relationType) return 'Every relation requires a semantic relation type.';
        if (!externalTarget && (!supportedTypes.has(targetType) || !target)) {
          return 'Every relation requires an existing catalogue target or an external target label.';
        }
        if (targetType === this.state().type && target === this.state().entity.id) {
          return 'A relation cannot target the entity being edited.';
        }
        const relationKey = `${relationType}|${targetType}|${target || externalTarget}`;
        if (relationKeys.has(relationKey)) return 'The same semantic relation can appear only once.';
        relationKeys.add(relationKey);
      }
    }
    if (field.kind === 'process-boundary') {
      if (isLegacyTextShape(value)) return '';
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'Process boundary must use the guided boundary fields.';
      if (object['businessCapability'] !== undefined && !isNonBlankText(object['businessCapability'])) {
        return 'Business capability must be non-blank text.';
      }
      for (const key of ['startsWhen', 'endsWhen', 'includes', 'excludes', 'assumptions']) {
        if (object[key] !== undefined && !isNonBlankTextList(object[key])) return `${key} must be a list of non-blank boundary descriptions.`;
      }
    }
    if (field.kind === 'process-lifecycle') {
      if (isLegacyTextShape(value)) return '';
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'Process lifecycle must use the guided lifecycle fields.';
      for (const key of [
        'entryCriteria', 'statuses', 'terminalStates', 'successOutcomes', 'partialOutcomes',
        'failedOutcomes', 'cancellationOutcomes'
      ]) {
        if (object[key] !== undefined && !isNonBlankTextList(object[key])) return `${key} must be a list of non-blank lifecycle descriptions.`;
      }
      if (object['triggers'] !== undefined && !Array.isArray(object['triggers'])) return 'Lifecycle triggers must be a list of guided cards.';
      for (const trigger of (object['triggers'] || []) as unknown[]) {
        if (!trigger || typeof trigger !== 'object' || Array.isArray(trigger)) return 'Every lifecycle trigger must be a guided card.';
        const card = trigger as Record<string, unknown>;
        if (!isNonBlankText(card['type'])) return 'Every lifecycle trigger requires a type.';
        if (!isNonBlankText(card['name'])) return 'Every lifecycle trigger requires a name.';
        if (card['exchange'] !== undefined && !isNonBlankText(card['exchange'])) return 'Lifecycle trigger exchange must be non-blank text.';
      }
      if (object['transitions'] !== undefined && !Array.isArray(object['transitions'])) return 'Lifecycle transitions must be a list of guided cards.';
      for (const transition of (object['transitions'] || []) as unknown[]) {
        if (!transition || typeof transition !== 'object' || Array.isArray(transition)) return 'Every lifecycle transition must be a guided card.';
        const card = transition as Record<string, unknown>;
        if (card['from'] !== undefined && card['from'] !== null && !isNonBlankText(card['from'])) return 'Lifecycle transition source must be non-blank text or empty for the initial transition.';
        if (!isNonBlankText(card['to'])) return 'Every lifecycle transition requires a target status.';
        if (!isNonBlankText(card['trigger'])) return 'Every lifecycle transition requires an observable trigger.';
      }
    }
    if (field.kind === 'completion-signals') {
      if (isLegacyTextShape(value)) return '';
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'Completion signals must use the guided evidence categories.';
      for (const key of ['successful', 'partial', 'failed', 'cancelled']) {
        if (object[key] !== undefined && !isNonBlankTextList(object[key])) return `${key} must be a list of non-blank observable signals.`;
      }
    }
    if (field.kind === 'failure-modes') {
      if (!Array.isArray(value)) return 'Failure modes must be a list.';
      const modes = value as unknown[];
      if (this.state().type === 'process') {
        const ids = new Set<string>();
        const stepsRaw = this.form.controls[this.adapter.controlName('steps')]?.value;
        const steps = stepsRaw ? JSON.parse(stepsRaw) as Array<Record<string, unknown>> : [];
        const stepIds = new Set(steps.map((step) => String(step['id'] || '').trim()).filter(Boolean));
        for (const item of modes) {
          if (typeof item === 'string' && item.trim()) continue;
          if (!item || typeof item !== 'object' || Array.isArray(item)) return 'Every process failure mode must be a card or a non-blank legacy description.';
          const mode = item as Record<string, unknown>;
          const id = String(mode['id'] || '').trim();
          if (!id || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(id)) return 'Every process failure mode requires a lowercase kebab-case ID.';
          if (ids.has(id)) return 'Every process failure mode ID must be unique.';
          ids.add(id);
          if (!String(mode['name'] || '').trim()) return 'Every process failure mode requires a name.';
          if (!String(mode['summary'] || '').trim()) return 'Every process failure mode requires a summary.';
          const affectedStep = String(mode['affectedStep'] || '').trim();
          if (affectedStep && !stepIds.has(affectedStep)) return `Failure mode ${id} references an unknown process step.`;
          if (mode['signals'] !== undefined && !isNonBlankTextList(mode['signals'])) return `Failure mode ${id} signals must be a list of non-blank descriptions.`;
        }
      } else {
        for (const item of modes) {
          if (typeof item === 'string' && item.trim()) continue;
          if (!item || typeof item !== 'object' || Array.isArray(item)) return 'Every integration failure mode must be a card or a non-blank legacy description.';
          const mode = item as Record<string, unknown>;
          if (!String(mode['name'] || '').trim()) return 'Every integration failure mode requires a name.';
          if (!String(mode['type'] || '').trim()) return 'Every integration failure mode requires a type.';
          if (!String(mode['symptom'] || '').trim() && !String(mode['impact'] || '').trim()) {
            return 'Every integration failure mode requires an observable symptom or impact.';
          }
        }
      }
    }
    if (field.kind === 'data-artifacts') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return 'Data and artifacts must use the guided category lists.';
      for (const key of ['primaryObjects', 'inputArtifacts', 'outputArtifacts', 'persistedEntities', 'readModels', 'auditArtifacts', 'notes']) {
        if (object[key] !== undefined && !isNonBlankTextList(object[key])) return `${key} must be a list of non-blank artifact descriptions.`;
      }
    }
    if (field.kind === 'source-coverage') {
      const coverage = Array.isArray(value) ? value[0] as Record<string, unknown> : object;
      if (!coverage || typeof coverage !== 'object' || Array.isArray(coverage)) return 'Source coverage must use one guided coverage object.';
      const status = String(coverage['status'] || '').trim();
      if (status && !['complete', 'partial', 'unknown', 'full', 'scanned', 'fully-scanned'].includes(status)) {
        return 'Choose a supported source coverage status.';
      }
      for (const key of ['scannedSources', 'expectedSources', 'limitations']) {
        if (coverage[key] !== undefined && !isNonBlankTextList(coverage[key])) return `${key} must be a list of non-blank source descriptions.`;
      }
    }
    if (field.kind === 'catalog-gaps') {
      if (!Array.isArray(value)) return 'Gaps must be a list of guided cards.';
      const ids = new Set<string>();
      for (const item of value as unknown[]) {
        if (typeof item === 'string' && item.trim()) continue;
        if (!item || typeof item !== 'object' || Array.isArray(item)) return 'Every gap must be a card or a non-blank legacy description.';
        const gap = item as Record<string, unknown>;
        const id = String(gap['id'] || '').trim();
        if (id && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(id)) return 'Gap IDs must use lowercase kebab-case.';
        if (id && ids.has(id)) return 'Gap IDs must be unique inside the entity.';
        if (id) ids.add(id);
        if (!String(gap['summary'] || gap['question'] || '').trim()) return 'Every gap requires an actionable summary.';
        const severity = String(gap['severity'] || '').trim();
        if (severity && !['error', 'warning', 'info'].includes(severity)) return 'Gap severity must be error, warning or info.';
        const status = String(gap['status'] || '').trim();
        if (status && !['open', 'resolved'].includes(status)) return 'Gap status must be open or resolved.';
        if (gap['suggestedNextSources'] !== undefined && !isNonBlankTextList(gap['suggestedNextSources'])) {
          return 'Suggested next sources must be a list of non-blank descriptions.';
        }
      }
    }
    return '';
  }

  private validateStructuredControls(): void {
    for (const field of this.adapter.fields(this.state().type).filter((field) => this.isStructuredField(field))) {
      const control = this.form.controls[this.controlName(field)];
      if (!control?.value) continue;
      try {
        const validationError = this.structuredValidationError(field, JSON.parse(control.value));
        const errors = { ...(control.errors || {}) };
        delete errors['structured'];
        if (validationError) errors['structured'] = validationError;
        control.setErrors(Object.keys(errors).length ? errors : null);
      } catch {
        control.setErrors({ ...(control.errors || {}), structured: 'Correct this structured field before saving.' });
      }
    }
  }

  private applyFieldErrors(): void {
    if (this.readonly()) return;
    for (const error of this.fieldErrors()) {
      const field = this.adapter.fieldForPointer(this.state().type, error.field);
      if (field) this.form.controls[this.controlName(field)]?.setErrors({ server: error.message });
    }
  }

  private applyDisabledState(state: OperationalContextEditorState): void {
    if (this.readonly()) {
      this.form.disable({ emitEvent: false });
      return;
    }

    this.form.enable({ emitEvent: false });
    if (state.mode === 'edit') {
      this.form.controls[this.adapter.controlName('id')]?.disable({ emitEvent: false });
    }
  }
}

function isNonBlankTextList(value: unknown): boolean {
  return Array.isArray(value) && value.every((item) => typeof item === 'string' && item.trim().length > 0);
}

function isNonBlankText(value: unknown): boolean {
  return typeof value === 'string' && value.trim().length > 0;
}

function isLegacyTextShape(value: unknown): boolean {
  return isNonBlankText(value) || isNonBlankTextList(value);
}

function isSafeConfigurationDirectory(value: unknown): boolean {
  if (!isNonBlankText(value)) return false;
  const directory = String(value).trim();
  return /^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$/.test(directory)
    && !directory.startsWith('/')
    && !directory.endsWith('/')
    && !directory.includes('//')
    && !directory.includes('..')
    && !directory.includes('@{');
}

function normalizeRelationTargetType(value: string): string {
  const normalized = value.trim().replaceAll('_', '-').toLowerCase();
  const aliases: Record<string, string> = {
    systems: 'system', repositories: 'repository', processes: 'process', integrations: 'integration', teams: 'team',
    boundedcontext: 'bounded-context', boundedcontexts: 'bounded-context', 'bounded-contexts': 'bounded-context',
    codesearchscope: 'code-search-scope', codesearchscopes: 'code-search-scope', 'code-search-scopes': 'code-search-scope',
    terms: 'glossary-term', 'glossary-terms': 'glossary-term', handoffrules: 'handoff-rule', 'handoff-rules': 'handoff-rule'
  };
  return aliases[normalized] || normalized;
}
