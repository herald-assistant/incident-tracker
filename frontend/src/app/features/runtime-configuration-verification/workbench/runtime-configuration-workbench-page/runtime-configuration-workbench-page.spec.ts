import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  RuntimeConfigurationVerificationInputOptions,
  RuntimeConfigurationWorkbenchPreviewResponse,
  SanitizedConfigurationNode
} from '../../models/runtime-configuration-verification.models';
import { RuntimeConfigurationVerificationApiService } from '../../services/runtime-configuration-verification-api.service';
import { RuntimeConfigurationWorkbenchPageComponent } from './runtime-configuration-workbench-page';

describe('RuntimeConfigurationWorkbenchPageComponent', () => {
  let fixture: ComponentFixture<RuntimeConfigurationWorkbenchPageComponent>;
  let api: {
    getInputOptions: ReturnType<typeof vi.fn>;
    preview: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      getInputOptions: vi.fn(() => of(inputOptions())),
      preview: vi.fn(() => of(previewResponse()))
    };

    await TestBed.configureTestingModule({
      imports: [RuntimeConfigurationWorkbenchPageComponent],
      providers: [{ provide: RuntimeConfigurationVerificationApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(RuntimeConfigurationWorkbenchPageComponent);
    fixture.detectChanges();
  });

  it('should load allowlisted scope and request an exact BASIC pipeline preview', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Runtime Configuration Pipeline');
    expect(compiled.textContent).toContain('Config repository · runtime-config');
    expect(compiled.textContent).toContain('Backend · backend');
    expect(compiled.textContent).toContain('Raw configuration pozostaje poza Workbenchem');

    buttonContaining(compiled, 'Run preview')?.click();
    fixture.detectChanges();

    expect(api.preview).toHaveBeenCalledWith({
      mode: 'BASIC',
      repositoryId: 'runtime-config',
      systemId: 'backend',
      sourceBranch: 'dev1',
      targetBranch: 'zt001'
    });
    expect(compiled.textContent).toContain('FETCHED METADATA');
    expect(compiled.textContent).toContain('backend/application.yml.kv');
    expect(compiled.textContent).toContain('AI nie zostało uruchomione');
  });

  it('should render mapping, anonymization and exact AI-safe input without raw values', () => {
    buttonContaining(fixture.nativeElement, 'Run preview')?.click();
    fixture.detectChanges();
    let compiled = fixture.nativeElement as HTMLElement;

    buttonContaining(compiled, 'Mapping')?.click();
    fixture.detectChanges();
    expect(compiled.textContent).toContain('Canonical mapping');
    expect(compiled.textContent).toContain('spring.datasource.password');
    expect(compiled.textContent).toContain('VALUE_CHANGED');

    buttonContaining(compiled, 'Anonymization')?.click();
    fixture.detectChanges();
    expect(compiled.textContent).toContain('Anonymization decisions');
    expect(compiled.textContent).toContain('PSEUDONYMIZED');
    expect(compiled.textContent).toContain('cfg_1');

    buttonContaining(compiled, 'AI input')?.click();
    fixture.detectChanges();
    expect(compiled.textContent).toContain('Exact AI input');
    expect((compiled.querySelector('textarea[aria-label="Prepared prompt"]') as HTMLTextAreaElement).value)
      .toContain('Review sanitized configuration');
    expect((compiled.querySelector('textarea[aria-label="Wybrany AI artifact"]') as HTMLTextAreaElement).value)
      .toContain('"sourceValueToken":"cfg_1"');
    expect(compiled.textContent).not.toContain('raw-password-do-not-render');
    expect(fixture.componentInstance.responseJson()).not.toContain('raw-password-do-not-render');
  });

  it('should expose a DEEP partial preflight blocker, ownership and truncation', () => {
    api.preview.mockReturnValue(of(previewResponse({ deep: true, large: true })));
    buttonContaining(fixture.nativeElement, 'Deep')?.click();
    fixture.componentInstance.form.controls.codeRef.setValue('release/42');
    buttonContaining(fixture.nativeElement, 'Run preview')?.click();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    buttonContaining(compiled, 'DEEP scope')?.click();
    fixture.detectChanges();
    expect(api.preview).toHaveBeenCalledWith(expect.objectContaining({
      mode: 'DEEP',
      codeRef: 'release/42'
    }));
    expect(compiled.textContent).toContain('Preflight: BLOCKED');
    expect(compiled.textContent).toContain('CODE_REF_NOT_FOUND');
    expect(compiled.textContent).toContain('Backend Team');

    buttonContaining(compiled, 'Anonymization')?.click();
    fixture.detectChanges();
    expect(compiled.textContent).toContain('pierwsze 500 decyzji');
  });

  it('should expose accessible perspective and copy actions', () => {
    buttonContaining(fixture.nativeElement, 'Run preview')?.click();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    buttonContaining(compiled, 'AI input')?.click();
    fixture.detectChanges();

    expect(compiled.querySelector('[aria-label="Perspektywy Runtime Configuration"]')).not.toBeNull();
    expect(buttonContaining(compiled, 'AI input')?.getAttribute('aria-pressed')).toBe('true');
    expect(compiled.querySelector('button[aria-label="Kopiuj prepared prompt"]')).not.toBeNull();
    expect(compiled.querySelector('button[aria-label="Kopiuj request preview"]')).not.toBeNull();
    expect(compiled.querySelector('button[aria-label="Kopiuj JSON response"]')).not.toBeNull();
  });

  it('should block an invalid branch pair and render only a safe API error', () => {
    fixture.componentInstance.form.controls.targetBranch.setValue('dev1');
    fixture.componentInstance.submit(new Event('submit'));
    fixture.detectChanges();
    expect(api.preview).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Wybierz dwa różne branche');

    fixture.componentInstance.form.controls.targetBranch.setValue('zt001');
    api.preview.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: {
              code: 'RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_FAILED',
              message: 'Preview jest chwilowo niedostępny.',
              internalCause: 'raw-password-do-not-render'
            }
          })
      )
    );
    fixture.componentInstance.submit(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Preview jest chwilowo niedostępny.');
    expect(fixture.componentInstance.responseJson()).toContain(
      'RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_FAILED'
    );
    expect(fixture.componentInstance.responseJson()).not.toContain('raw-password-do-not-render');
  });
});

function inputOptions(): RuntimeConfigurationVerificationInputOptions {
  return {
    modes: ['BASIC', 'DEEP'],
    branches: ['dev1', 'zt001'],
    repositories: [{ id: 'runtime-config', label: 'Config repository' }],
    systems: [{ id: 'backend', label: 'Backend', configurationDirectory: 'backend' }]
  };
}

function previewResponse(
  options: { deep?: boolean; large?: boolean } = {}
): RuntimeConfigurationWorkbenchPreviewResponse {
  const root = node('spring', 'OBJECT', 'OBJECT', 'UNCHANGED', [
    node('spring.datasource.password', 'STRING', 'STRING', 'VALUE_CHANGED')
  ]);
  const baseDecision = {
    role: 'APPLICATION_YAML',
    documentIndex: 0,
    path: 'spring.datasource.password',
    relation: 'VALUE_CHANGED',
    sensitivity: 'SECRET',
    sourceType: 'STRING',
    targetType: 'STRING',
    sourceRepresentation: 'PSEUDONYMIZED' as const,
    targetRepresentation: 'PSEUDONYMIZED' as const,
    sourceValueToken: 'cfg_1',
    targetValueToken: 'cfg_2'
  };
  const decisions = options.large
    ? Array.from({ length: 501 }, (_, index) => ({
        ...baseDecision,
        path: `features.feature-${index}`
      }))
    : [baseDecision];

  return {
    mode: options.deep ? 'DEEP' : 'BASIC',
    repositoryId: 'runtime-config',
    systemId: 'backend',
    sourceBranch: 'dev1',
    targetBranch: 'zt001',
    codeRef: options.deep ? 'release/42' : null,
    sourceAcquisition: {
      configurationDirectory: 'backend',
      source: coverage('dev1'),
      target: coverage('zt001')
    },
    mapping: {
      repositoryId: 'runtime-config',
      systemId: 'backend',
      systemLabel: 'Backend',
      configurationDirectory: 'backend',
      sourceBranch: 'dev1',
      targetBranch: 'zt001',
      status: 'COMPLETE',
      sourceCoverage: coverage('dev1'),
      targetCoverage: coverage('zt001'),
      documents: [{
        role: 'APPLICATION_YAML',
        sourcePath: 'backend/application.yml.kv',
        targetPath: 'backend/application.yml.kv',
        documentIndex: 0,
        sourcePresent: true,
        targetPresent: true,
        sourceProfileToken: null,
        targetProfileToken: null,
        root
      }],
      references: [],
      differences: [{
        differenceId: 'diff-1',
        role: 'APPLICATION_YAML',
        documentIndex: 0,
        path: 'spring.datasource.password',
        kind: 'VALUE_CHANGED',
        sourceType: 'STRING',
        targetType: 'STRING',
        sensitivity: 'SECRET',
        sourceValueToken: 'cfg_1',
        targetValueToken: 'cfg_2'
      }],
      findings: [{
        findingId: 'finding-1',
        code: 'SECRET_CHANGED',
        severity: 'WARNING',
        path: 'spring.datasource.password',
        differenceIds: ['diff-1'],
        referenceIds: []
      }]
    },
    anonymization: {
      totalNodes: decisions.length,
      pseudonymizedRepresentations: decisions.length * 2,
      suppressedRepresentations: 0,
      structureOnlyRepresentations: 0,
      notPresentRepresentations: 0,
      decisions
    },
    deepContext: options.deep
      ? {
          status: 'PARTIAL',
          preflight: {
            status: 'BLOCKED',
            repositoryId: 'runtime-config',
            systemId: 'backend',
            systemLabel: 'Backend',
            resolvedConfigurationDirectory: 'backend',
            repositories: [{
              scopeId: 'backend-code',
              repositoryId: 'backend-code',
              role: 'SOURCE',
              projectPath: 'apps/backend',
              projectName: 'backend',
              pathPrefixes: ['src/main/java'],
              requestedRef: 'release/42',
              usedRef: null,
              refSource: 'REQUESTED',
              refExists: false,
              deploymentRefConfirmed: false,
              ready: false,
              visibilityLimits: ['Requested ref is unavailable']
            }],
            blockers: [{ code: 'CODE_REF_NOT_FOUND', message: 'Requested ref is unavailable.' }],
            visibilityLimits: ['Code search was not executed.'],
            ready: false
          },
          primarySystem: null,
          affectedSystems: [],
          integrations: [],
          processes: [],
          boundedContexts: [],
          codeGrounding: [],
          ownership: {
            situationType: 'CONFIGURATION_CHANGE',
            primaryOwners: [{
              targetType: 'SYSTEM',
              targetId: 'backend',
              targetLabel: 'Backend',
              ownerTeamIds: ['backend-team'],
              ownerLabel: 'Backend Team',
              source: 'OPERATIONAL_CONTEXT',
              confidence: 'HIGH'
            }],
            partnerOwners: [],
            resolutionPath: [],
            handoffReason: null,
            visibilityLimits: []
          },
          visibilityLimits: ['Code search was not executed.']
        }
      : null,
    preparedPrompt: 'Review sanitized configuration. Use artifact://runtime-configuration.json.',
    artifactContents: {
      'runtime-configuration.json': '{"sourceValueToken":"cfg_1","targetValueToken":"cfg_2"}'
    },
    artifacts: [{
      name: 'runtime-configuration.json',
      characterCount: 61,
      truncated: options.large ?? false
    }],
    visibilityLimits: options.deep ? ['DEEP context is partial.'] : []
  };
}

function coverage(branch: string) {
  return {
    branch,
    branchExists: true,
    files: [{
      role: 'APPLICATION_YAML',
      path: 'backend/application.yml.kv',
      status: 'AVAILABLE',
      commitId: 'abc123',
      lastCommitId: 'abc123',
      lastModifiedAt: '2026-07-30T10:00:00Z',
      sizeBytes: 512,
      errorCode: null
    }],
    complete: true
  };
}

function node(
  path: string,
  sourceType: string,
  targetType: string,
  relation: string,
  children: SanitizedConfigurationNode[] = []
): SanitizedConfigurationNode {
  return {
    name: path.split('.').at(-1) ?? path,
    path,
    sourceType,
    targetType,
    relation,
    sensitivity: path.includes('password') ? 'SECRET' : 'NORMAL',
    sourceValueToken: path.includes('password') ? 'cfg_1' : null,
    targetValueToken: path.includes('password') ? 'cfg_2' : null,
    sourceCardinality: null,
    targetCardinality: null,
    children
  };
}

function buttonContaining(root: HTMLElement, text: string): HTMLButtonElement | null {
  return (
    Array.from(root.querySelectorAll('button')).find((button) =>
      button.textContent?.includes(text)
    ) ?? null
  );
}
