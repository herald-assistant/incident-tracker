import { TestBed } from '@angular/core/testing';

import { ContextEntityEditorDrawerComponent } from './context-entity-editor-drawer';

describe('ContextEntityEditorDrawerComponent', () => {
  it('keeps the anonymized CRM ID immutable and emits the edited canonical payload', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'system',
      entity: { type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml', payload: { id: 'crm-contact-core', name: 'CRM Contact Core', ownership: { teams: ['crm-domain-team'] } } }
    });
    const emitted = vi.fn();
    fixture.componentInstance.saveEntity.subscribe(emitted);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.controls['id'].disabled).toBe(true);
    fixture.componentInstance.form.controls['name'].setValue('CRM Contact Platform');
    fixture.componentInstance.submit();
    expect(emitted).toHaveBeenCalledWith(expect.objectContaining({ id: 'crm-contact-core', name: 'CRM Contact Platform', ownership: { teams: ['crm-domain-team'] } }));
  });

  it('retains the form and reports invalid guided CRM process data', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'create', type: 'process',
      entity: { type: 'process', id: '', sourceFile: '', payload: { id: '', name: '' } }
    });
    fixture.detectChanges();
    fixture.componentInstance.form.controls['id'].setValue('crm-contact-update');
    fixture.componentInstance.form.controls['name'].setValue('CRM Contact Update');
    fixture.componentInstance.form.controls['processBoundary'].setValue('[invalid structured CRM value]');
    fixture.componentInstance.submit();
    fixture.detectChanges();
    const boundaryField = fixture.componentInstance.adapter.fields('process').find((field) => field.path === 'processBoundary')!;
    expect(fixture.componentInstance.controlError(boundaryField)).toContain('Correct this structured field');
    expect(fixture.componentInstance.form.controls['name'].value).toBe('CRM Contact Update');
  });

  it('renders keyboard-accessible runtime and AI help for every anonymized CRM input', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'create', type: 'code-search-scope',
      entity: { type: 'code-search-scope', id: '', sourceFile: '', payload: { id: '', name: '' } }
    });
    fixture.detectChanges();

    const fields = fixture.componentInstance.adapter.fields('code-search-scope');
    const helpButtons = fixture.nativeElement.querySelectorAll('.editor-field__help');
    expect(helpButtons.length).toBe(fields.length);
    expect(helpButtons[0].getAttribute('aria-label')).toContain('Help for');
    expect(fixture.componentInstance.fieldTooltip(fields.find((field) => field.path === 'target')!)).toContain('strictly system or bounded-context');
  });

  it('renders structured CRM selectors instead of raw JSON for code-search target and repositories', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'create', type: 'code-search-scope',
      entity: { type: 'code-search-scope', id: '', sourceFile: '', payload: { id: '', name: '' } }
    });
    fixture.componentRef.setInput('referenceOptions', {
      system: [{ id: 'crm-contact-core', label: 'CRM Contact Core' }],
      repository: [{ id: 'crm-contact-repository', label: 'CRM Contact Repository' }]
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#code-target-type')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('button.add-row-button')?.textContent).toContain('Add repository');
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="target"]')).toBeNull();
  });

  it('blocks invalid CRM code-search rows before sending them to the backend', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'create', type: 'code-search-scope',
      entity: { type: 'code-search-scope', id: '', sourceFile: '', payload: { id: '', name: '' } }
    });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const repositoriesField = component.adapter.fields('code-search-scope').find((field) => field.path === 'repositories')!;
    component.updateStructuredField(repositoriesField, [{ role: 'primary', priority: 0, searchMode: 'whole-repository' }]);

    expect(component.controlError(repositoriesField)).toContain('Choose a repository');
    expect(component.form.controls['repositories'].invalid).toBe(true);
  });

  it('blocks duplicate anonymized CRM repositories before saving', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'create', type: 'code-search-scope',
      entity: { type: 'code-search-scope', id: '', sourceFile: '', payload: { id: '', name: '' } }
    });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const repositoriesField = component.adapter.fields('code-search-scope').find((field) => field.path === 'repositories')!;
    component.updateStructuredField(repositoriesField, [
      { repoId: 'crm-contact-repository', role: 'primary', priority: 1, searchMode: 'whole-repository' },
      { repoId: 'crm-contact-repository', role: 'supporting', priority: 2, searchMode: 'whole-repository' }
    ]);

    expect(component.controlError(repositoriesField)).toContain('only once');
    expect(component.form.controls['repositories'].invalid).toBe(true);
  });

  it('requires a runtime-resolvable CRM Git identity before saving a repository', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'create', type: 'repository',
      entity: { type: 'repository', id: '', sourceFile: '', payload: { id: '', name: '' } }
    });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const gitField = component.adapter.fields('repository').find((field) => field.path === 'git')!;
    component.updateStructuredField(gitField, { provider: 'gitlab', group: 'crm' });

    expect(component.controlError(gitField)).toContain('project');
    expect(component.form.controls['git'].invalid).toBe(true);
  });

  it('renders guided CRM system and repository metadata instead of raw JSON', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'system',
      entity: {
        type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml',
        payload: {
          id: 'crm-contact-core', name: 'CRM Contact Core',
          participants: { externalOwner: 'CRM platform provider' },
          runtime: { configurationDirectory: 'crm/contact-service' }
        }
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#system-external-owner')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#runtime-configuration-directory')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="participants"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="runtime"]')).toBeNull();

    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'repository',
      entity: {
        type: 'repository', id: 'crm-contact-repository', sourceFile: 'repo-map.yml',
        payload: {
          id: 'crm-contact-repository', name: 'CRM Contact Repository', git: { projectPath: 'crm/contact-service' },
          evidence: [{ sourceRef: 'crm/contact-service/pom.xml', evidenceType: 'build-definition' }],
          llmToolHints: { answerWhenUserMentions: ['CRM contact validation'] }
        }
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#repository-evidence-source-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#repository-answer-when-mentioned')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="evidence"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="llmToolHints"]')).toBeNull();
  });

  it('blocks invalid guided CRM system and repository metadata before save', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'system',
      entity: { type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml', payload: { id: 'crm-contact-core', name: 'CRM Contact Core' } }
    });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const runtimeField = component.adapter.fields('system').find((field) => field.path === 'runtime')!;
    component.updateStructuredField(runtimeField, { configurationDirectory: '../crm/contact-service' });
    expect(component.controlError(runtimeField)).toContain('safe repository-relative path');

    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'repository',
      entity: { type: 'repository', id: 'crm-contact-repository', sourceFile: 'repo-map.yml', payload: { id: 'crm-contact-repository', name: 'CRM Contact Repository', git: { projectPath: 'crm/contact-service' } } }
    });
    fixture.detectChanges();
    const evidenceField = fixture.componentInstance.adapter.fields('repository').find((field) => field.path === 'evidence')!;
    const hintsField = fixture.componentInstance.adapter.fields('repository').find((field) => field.path === 'llmToolHints')!;
    fixture.componentInstance.updateStructuredField(evidenceField, [{ sourceRef: '', evidenceType: 'build-definition' }]);
    expect(fixture.componentInstance.controlError(evidenceField)).toContain('source reference');
    fixture.componentInstance.updateStructuredField(hintsField, { answerWhenUserMentions: 'CRM contact validation' });
    expect(fixture.componentInstance.controlError(hintsField)).toContain('list');
  });

  it('blocks duplicate or incomplete CRM process steps before saving', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'create', type: 'process',
      entity: { type: 'process', id: '', sourceFile: '', payload: { id: '', name: '' } }
    });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const stepsField = component.adapter.fields('process').find((field) => field.path === 'steps')!;
    component.updateStructuredField(stepsField, [
      { id: 'accept-update', name: 'Accept CRM update' },
      { id: 'accept-update', name: '' }
    ]);

    expect(component.controlError(stepsField)).toContain('unique');
    expect(component.form.controls['steps'].invalid).toBe(true);
  });

  it('renders guided CRM recognition signals and relations instead of raw JSON', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'system',
      entity: {
        type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml',
        payload: {
          id: 'crm-contact-core', name: 'CRM Contact Core',
          matchSignals: { strong: { routes: ['/crm/contacts'] } },
          relations: [{ type: 'uses', targetType: 'process', target: 'crm-contact-update' }]
        }
      }
    });
    fixture.componentRef.setInput('referenceOptions', {
      process: [{ id: 'crm-contact-update', label: 'CRM Contact Update' }]
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#signal-strength-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#relation-target-type-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="matchSignals"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="relations"]')).toBeNull();
  });

  it('blocks empty CRM recognition signals, duplicate relations and self relations before save', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'system',
      entity: { type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml', payload: { id: 'crm-contact-core', name: 'CRM Contact Core' } }
    });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const signalsField = component.adapter.fields('system').find((field) => field.path === 'matchSignals')!;
    const relationsField = component.adapter.fields('system').find((field) => field.path === 'relations')!;

    component.updateStructuredField(signalsField, { strong: { routes: [] } });
    expect(component.controlError(signalsField)).toContain('at least one value');

    component.updateStructuredField(relationsField, [
      { type: 'uses', targetType: 'process', target: 'crm-contact-update' },
      { type: 'uses', targetType: 'process', target: 'crm-contact-update' }
    ]);
    expect(component.controlError(relationsField)).toContain('only once');

    component.updateStructuredField(relationsField, [
      { type: 'depends-on', targetType: 'system', target: 'crm-contact-core' }
    ]);
    expect(component.controlError(relationsField)).toContain('cannot target');
  });

  it('renders guided CRM failure, artifact, coverage and gap controls instead of raw JSON', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'process',
      entity: {
        type: 'process', id: 'crm-contact-update', sourceFile: 'processes.yml',
        payload: {
          id: 'crm-contact-update', name: 'CRM Contact Update',
          steps: [{ id: 'validate-contact', name: 'Validate CRM contact' }],
          failureModes: [{ id: 'crm-contact-rejected', name: 'CRM contact rejected', summary: 'The CRM contact update is rejected.', affectedStep: 'validate-contact' }],
          dataAndArtifacts: { inputArtifacts: ['Anonymized CRM contact change request'] }
        }
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#process-failure-id-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#data-artifacts-inputArtifacts')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="failureModes"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="dataAndArtifacts"]')).toBeNull();

    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'bounded-context',
      entity: {
        type: 'bounded-context', id: 'crm-customer-context', sourceFile: 'bounded-contexts.yml',
        payload: {
          id: 'crm-customer-context', name: 'CRM Customer Context',
          localLanguageSummary: ['CRM contact means a communication profile.'],
          scope: { includes: ['CRM contact preference validation'] },
          semanticBoundary: { ownsLanguage: ['CRM contact preference'] },
          evidence: [{ sourceRef: 'Anonymized CRM glossary', evidenceType: 'domain-documentation' }],
          llmToolHints: { answerWhenUserMentions: ['CRM contact preference'] },
          sourceCoverage: { status: 'partial', scannedSources: ['Anonymized CRM domain notes'] },
          gaps: [{ id: 'crm-consent-boundary', summary: 'Confirm the CRM consent boundary.', status: 'open' }]
        }
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#source-coverage-status')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#gap-summary-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#bounded-local-language')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#bounded-scope-includes')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#bounded-semantic-ownsLanguage')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#bounded-evidence-source-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#bounded-answer-when-mentioned')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="sourceCoverage"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="gaps"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="localLanguageSummary"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="scope"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="semanticBoundary"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="evidence"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="llmToolHints"]')).toBeNull();
  });

  it('blocks invalid structured CRM failure modes, coverage and gaps before save', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'process',
      entity: {
        type: 'process', id: 'crm-contact-update', sourceFile: 'processes.yml',
        payload: { id: 'crm-contact-update', name: 'CRM Contact Update', steps: [{ id: 'validate-contact', name: 'Validate CRM contact' }] }
      }
    });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const failuresField = component.adapter.fields('process').find((field) => field.path === 'failureModes')!;

    component.updateStructuredField(failuresField, [{
      id: 'CRM invalid ID', name: '', summary: '', affectedStep: 'missing-crm-step'
    }]);
    expect(component.controlError(failuresField)).toContain('kebab-case');

    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'bounded-context',
      entity: { type: 'bounded-context', id: 'crm-customer-context', sourceFile: 'bounded-contexts.yml', payload: { id: 'crm-customer-context', name: 'CRM Customer Context' } }
    });
    fixture.detectChanges();
    const coverageField = fixture.componentInstance.adapter.fields('bounded-context').find((field) => field.path === 'sourceCoverage')!;
    const gapsField = fixture.componentInstance.adapter.fields('bounded-context').find((field) => field.path === 'gaps')!;
    const scopeField = fixture.componentInstance.adapter.fields('bounded-context').find((field) => field.path === 'scope')!;
    const semanticField = fixture.componentInstance.adapter.fields('bounded-context').find((field) => field.path === 'semanticBoundary')!;
    const evidenceField = fixture.componentInstance.adapter.fields('bounded-context').find((field) => field.path === 'evidence')!;
    const hintsField = fixture.componentInstance.adapter.fields('bounded-context').find((field) => field.path === 'llmToolHints')!;

    fixture.componentInstance.updateStructuredField(coverageField, { status: 'invented-crm-status' });
    expect(fixture.componentInstance.controlError(coverageField)).toContain('supported');
    fixture.componentInstance.updateStructuredField(gapsField, [{ id: 'crm-gap', summary: '', severity: 'critical', status: 'open' }]);
    expect(fixture.componentInstance.controlError(gapsField)).toContain('summary');
    fixture.componentInstance.updateStructuredField(scopeField, { includes: 'not-a-crm-list' });
    expect(fixture.componentInstance.controlError(scopeField)).toContain('list');
    fixture.componentInstance.updateStructuredField(semanticField, { invariants: [''] });
    expect(fixture.componentInstance.controlError(semanticField)).toContain('list');
    fixture.componentInstance.updateStructuredField(evidenceField, [{ sourceRef: '', evidenceType: 'domain-documentation' }]);
    expect(fixture.componentInstance.controlError(evidenceField)).toContain('source reference');
    fixture.componentInstance.updateStructuredField(hintsField, { usefulSearchKeywords: 'not-a-crm-list' });
    expect(fixture.componentInstance.controlError(hintsField)).toContain('list');
  });

  it('renders guided CRM boundary, lifecycle and completion fields instead of raw JSON', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'process',
      entity: {
        type: 'process', id: 'crm-contact-update', sourceFile: 'processes.yml',
        payload: {
          id: 'crm-contact-update', name: 'CRM Contact Update',
          processBoundary: { businessCapability: 'CRM Contact Preference Management', endsWhen: ['CRM contact confirmation is visible.'] },
          lifecycle: {
            triggers: [{ type: 'api', name: 'CRM contact update' }],
            statuses: ['requested', 'applied'],
            transitions: [{ from: 'requested', to: 'applied', trigger: 'CRM validation succeeds.' }]
          },
          completionSignals: { successful: ['CRM contact confirmation is recorded.'] }
        }
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#process-boundary-business-capability')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#lifecycle-trigger-name-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#lifecycle-transition-to-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#completion-signals-successful')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="processBoundary"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="lifecycle"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea[formcontrolname="completionSignals"]')).toBeNull();
  });

  it('blocks invalid guided CRM boundary, lifecycle and completion structures before save', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityEditorDrawerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityEditorDrawerComponent);
    fixture.componentRef.setInput('state', {
      mode: 'edit', type: 'process',
      entity: { type: 'process', id: 'crm-contact-update', sourceFile: 'processes.yml', payload: { id: 'crm-contact-update', name: 'CRM Contact Update' } }
    });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const boundaryField = component.adapter.fields('process').find((field) => field.path === 'processBoundary')!;
    const lifecycleField = component.adapter.fields('process').find((field) => field.path === 'lifecycle')!;
    const completionField = component.adapter.fields('process').find((field) => field.path === 'completionSignals')!;

    component.updateStructuredField(boundaryField, { endsWhen: 'not-a-crm-list' });
    expect(component.controlError(boundaryField)).toContain('list');

    component.updateStructuredField(lifecycleField, {
      triggers: [{ type: '', name: '' }],
      transitions: [{ from: 'requested', to: '', trigger: '' }]
    });
    expect(component.controlError(lifecycleField)).toContain('trigger requires a type');

    component.updateStructuredField(completionField, { successful: 'not-a-crm-list' });
    expect(component.controlError(completionField)).toContain('list');
  });
});
