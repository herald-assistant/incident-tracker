import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  ConfigDriftViewerInputOptions,
  ConfigDriftViewerWorkbenchAiInputResponse,
  ConfigDriftViewerWorkbenchAnonymizationPage,
  ConfigDriftViewerWorkbenchArtifactResponse,
  ConfigDriftViewerWorkbenchConfigurationDiffResponse,
  ConfigDriftViewerWorkbenchDeepResponse,
  ConfigDriftViewerWorkbenchMappingPage,
  ConfigDriftViewerWorkbenchPreviewResponse,
  ConfigDriftViewerWorkbenchSourceResponse
} from '../../models/config-drift-viewer.models';
import { ConfigDriftViewerApiService } from '../../services/config-drift-viewer-api.service';
import { ConfigDriftViewerWorkbenchPageComponent } from './config-drift-viewer-workbench-page';

describe('ConfigDriftViewerWorkbenchPageComponent', () => {
  let fixture: ComponentFixture<ConfigDriftViewerWorkbenchPageComponent>;
  let api: {
    getInputOptions: ReturnType<typeof vi.fn>;
    preview: ReturnType<typeof vi.fn>;
    getWorkbenchSource: ReturnType<typeof vi.fn>;
    getWorkbenchConfigurationDiff: ReturnType<typeof vi.fn>;
    getWorkbenchMapping: ReturnType<typeof vi.fn>;
    getWorkbenchAnonymization: ReturnType<typeof vi.fn>;
    getWorkbenchDeep: ReturnType<typeof vi.fn>;
    getWorkbenchAiInput: ReturnType<typeof vi.fn>;
    getWorkbenchArtifact: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      getInputOptions: vi.fn(() => of(inputOptions())),
      preview: vi.fn(() => of(previewResponse())),
      getWorkbenchSource: vi.fn(() => of(sourceResponse())),
      getWorkbenchConfigurationDiff: vi.fn(() => of(configurationDiffResponse())),
      getWorkbenchMapping: vi.fn(() => of(mappingPage())),
      getWorkbenchAnonymization: vi.fn(() => of(anonymizationPage())),
      getWorkbenchDeep: vi.fn(() => of(deepResponse())),
      getWorkbenchAiInput: vi.fn(() => of(aiInputResponse())),
      getWorkbenchArtifact: vi.fn(() => of(artifactResponse()))
    };

    await TestBed.configureTestingModule({
      imports: [ConfigDriftViewerWorkbenchPageComponent],
      providers: [{ provide: ConfigDriftViewerApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfigDriftViewerWorkbenchPageComponent);
    fixture.detectChanges();
  });

  it('should return a compact summary and fetch only source metadata initially', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Config Drift Viewer Pipeline');
    expect(compiled.textContent).toContain('Wartości operatorskie i granica AI są rozdzielone');
    fixture.componentInstance.form.controls.codeRef.setValue('release-42');

    buttonContaining(compiled, 'Run preview')?.click();
    fixture.detectChanges();

    expect(api.preview).toHaveBeenCalledWith({
      mode: 'BASIC',
      repositoryId: 'runtime-config',
      systemId: 'backend',
      sourceBranch: 'dev1',
      targetBranch: 'zt001'
    });
    expect(api.getWorkbenchSource).toHaveBeenCalledWith(PREVIEW_ID);
    expect(api.getWorkbenchConfigurationDiff).not.toHaveBeenCalled();
    expect(api.getWorkbenchMapping).not.toHaveBeenCalled();
    expect(api.getWorkbenchAnonymization).not.toHaveBeenCalled();
    expect(api.getWorkbenchDeep).not.toHaveBeenCalled();
    expect(api.getWorkbenchAiInput).not.toHaveBeenCalled();
    expect(api.getWorkbenchArtifact).not.toHaveBeenCalled();
    expect(compiled.textContent).toContain('backend/application.yml.kv');
    expect(compiled.textContent).toContain('855 nodes');
    expect(compiled.textContent).toContain('AI input not generated');
    expect(fixture.componentInstance.responseJson().length).toBeLessThan(50_000);
    expect(fixture.componentInstance.responseJson()).not.toContain('preparedPrompt');
    expect(fixture.componentInstance.responseJson()).not.toContain('artifactContents');
    expect(fixture.componentInstance.responseJson()).not.toContain('${VAULT_DYNAMIC_DEV}');
    expect(fixture.componentInstance.responseJson()).not.toContain('raw-password');
  });

  it('should show exact operator projection in BASIC without generating AI details', () => {
    buttonContaining(fixture.nativeElement, 'Run preview')?.click();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    buttonContaining(compiled, 'Operator projection')?.click();
    fixture.detectChanges();

    expect(api.getWorkbenchConfigurationDiff).toHaveBeenCalledWith(PREVIEW_ID);
    const projection = compiled.querySelector(
      'textarea[aria-label="Operator configuration diff"]'
    ) as HTMLTextAreaElement;
    expect(projection.value).toContain('clients.customer-zt001.password');
    expect(projection.value).toContain('${VAULT_DYNAMIC_DEV}');

    buttonContaining(compiled, 'AI boundary mapping')?.click();
    fixture.detectChanges();
    expect(api.getWorkbenchMapping).not.toHaveBeenCalled();
    expect(compiled.textContent).toContain('AI boundary mapping not generated in BASIC');

    buttonContaining(compiled, 'Anonymization')?.click();
    fixture.detectChanges();
    expect(api.getWorkbenchAnonymization).not.toHaveBeenCalled();
    expect(compiled.textContent).toContain('Anonymization decisions not generated in BASIC');

    buttonContaining(compiled, 'AI input')?.click();
    fixture.detectChanges();
    expect(api.getWorkbenchAiInput).not.toHaveBeenCalled();
    expect(api.getWorkbenchArtifact).not.toHaveBeenCalled();
    expect(compiled.textContent).toContain('BASIC kończy preview');
  });

  it('should load DEEP mapping and anonymization only after perspective selection', () => {
    api.preview.mockReturnValue(of(previewResponse(true)));
    buttonContaining(fixture.nativeElement, 'Deep')?.click();
    buttonContaining(fixture.nativeElement, 'Run preview')?.click();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    buttonContaining(compiled, 'AI boundary mapping')?.click();
    fixture.detectChanges();
    expect(api.getWorkbenchMapping).toHaveBeenCalledWith(PREVIEW_ID, 0, 100, true);
    expect(compiled.textContent).toContain('Original → sanitized mapping');
    expect(compiled.textContent).toContain('clients.customer-zt001.password');
    expect(compiled.textContent).toContain('spring.datasource.password');
    expect(compiled.textContent).toContain('difference-1');
    expect(compiled.textContent).toContain('CHANGED');

    fixture.componentInstance.setMappingChangedOnly(false);
    fixture.detectChanges();
    expect(api.getWorkbenchMapping).toHaveBeenLastCalledWith(PREVIEW_ID, 0, 100, false);

    buttonContaining(compiled, 'Anonymization')?.click();
    fixture.detectChanges();
    expect(api.getWorkbenchAnonymization).toHaveBeenCalledWith(PREVIEW_ID, 0, 100);
    expect(compiled.textContent).toContain('Anonymization decisions');
    expect(compiled.textContent).toContain('SUPPRESSED');
    expect(compiled.textContent).not.toContain('raw-password-do-not-render');
  });

  it('should require explicit actions before loading the exact prompt or an artifact', () => {
    api.preview.mockReturnValue(of(previewResponse(true)));
    buttonContaining(fixture.nativeElement, 'Deep')?.click();
    buttonContaining(fixture.nativeElement, 'Run preview')?.click();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    buttonContaining(compiled, 'AI input')?.click();
    fixture.detectChanges();
    expect(api.getWorkbenchAiInput).not.toHaveBeenCalled();
    expect(api.getWorkbenchArtifact).not.toHaveBeenCalled();
    expect(compiled.querySelector('textarea[aria-label="Prepared prompt"]')).toBeNull();

    buttonContaining(compiled, 'Załaduj dokładny input AI')?.click();
    fixture.detectChanges();
    expect(api.getWorkbenchAiInput).toHaveBeenCalledWith(PREVIEW_ID);
    expect(
      (compiled.querySelector('textarea[aria-label="Prepared prompt"]') as HTMLTextAreaElement)
        .value
    ).toContain('Review compact sanitized configuration');

    buttonContaining(compiled, 'configuration-tree.yaml')?.click();
    fixture.detectChanges();
    expect(api.getWorkbenchArtifact).toHaveBeenCalledWith(
      PREVIEW_ID,
      'config-drift-viewer/configuration-tree.yaml'
    );
    expect(
      (compiled.querySelector('textarea[aria-label="Wybrany AI artifact"]') as HTMLTextAreaElement)
        .value
    ).toContain('formatVersion: 1');
    expect(compiled.textContent).not.toContain('raw-password-do-not-render');
  });

  it('should lazy-load DEEP blockers and ownership from the same snapshot', () => {
    api.preview.mockReturnValue(of(previewResponse(true)));
    buttonContaining(fixture.nativeElement, 'Deep')?.click();
    fixture.componentInstance.form.controls.codeRef.setValue('release-42');
    buttonContaining(fixture.nativeElement, 'Run preview')?.click();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    buttonContaining(compiled, 'DEEP scope')?.click();
    fixture.detectChanges();

    expect(api.preview).toHaveBeenCalledWith(expect.objectContaining({
      mode: 'DEEP',
      codeRef: 'release-42'
    }));
    expect(api.getWorkbenchDeep).toHaveBeenCalledWith(PREVIEW_ID);
    expect(compiled.textContent).toContain('CODE_REF_NOT_FOUND');
    expect(compiled.textContent).toContain('Backend Team');
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

const PREVIEW_ID = '019fb000-1f4d-79e0-8de1-daae931197ac';

function inputOptions(): ConfigDriftViewerInputOptions {
  return {
    modes: ['BASIC', 'DEEP'],
    branches: ['dev1', 'zt001'],
    repositories: [{ id: 'runtime-config', label: 'Config repository' }],
    systems: [{ id: 'backend', label: 'Backend', configurationDirectory: 'backend' }]
  };
}

function previewResponse(deep = false): ConfigDriftViewerWorkbenchPreviewResponse {
  return {
    previewId: PREVIEW_ID,
    expiresAt: '2026-07-30T10:10:00Z',
    mode: deep ? 'DEEP' : 'BASIC',
    repositoryId: 'runtime-config',
    systemId: 'backend',
    sourceBranch: 'dev1',
    targetBranch: 'zt001',
    codeRef: deep ? 'release-42' : null,
    source: {
      configurationDirectory: 'backend',
      sourceBranchExists: true,
      sourceComplete: true,
      targetBranchExists: true,
      targetComplete: true
    },
    counts: {
      documents: 3,
      nodes: 855,
      differences: 136,
      findings: 103,
      references: 90
    },
    anonymization: {
      totalNodes: deep ? 855 : 0,
      pseudonymizedRepresentations: deep ? 1400 : 0,
      suppressedRepresentations: deep ? 20 : 0,
      structureOnlyRepresentations: deep ? 200 : 0,
      notPresentRepresentations: deep ? 90 : 0
    },
    deep: {
      requested: deep,
      status: deep ? 'PARTIAL' : null,
      preflightStatus: deep ? 'BLOCKED' : null,
      repositoryScopes: deep ? 1 : 0,
      blockers: deep ? 1 : 0,
      codeGroundings: 0,
      primaryOwners: deep ? 1 : 0
    },
    aiInputGenerated: deep,
    artifacts: deep
      ? [{
          name: 'config-drift-viewer/configuration-tree.yaml',
          mediaType: 'application/yaml',
          characterCount: 71_175,
          truncated: false
        }]
      : [],
    visibilityLimits: deep ? ['Code search was not executed.'] : []
  };
}

function configurationDiffResponse(): ConfigDriftViewerWorkbenchConfigurationDiffResponse {
  return {
    previewId: PREVIEW_ID,
    configurationDiff: {
      sourceBranch: 'dev1',
      targetBranch: 'zt001',
      files: [{
        role: 'APPLICATION_YAML',
        format: 'YAML',
        sourcePath: 'backend/application.yml.kv',
        targetPath: 'backend/application.yml.kv',
        sourcePresent: true,
        targetPresent: true,
        documents: [{
          documentIndex: 0,
          sourcePresent: true,
          targetPresent: true,
          sourceProfile: {
            presence: 'ABSENT',
            type: null,
            value: null,
            cardinality: null
          },
          targetProfile: {
            presence: 'ABSENT',
            type: null,
            value: null,
            cardinality: null
          },
          root: {
            name: 'password',
            path: 'clients.customer-zt001.password',
            changeKind: 'CHANGED',
            source: {
              presence: 'PRESENT',
              type: 'STRING',
              value: '${VAULT_DYNAMIC_DEV}',
              cardinality: null
            },
            target: {
              presence: 'PRESENT',
              type: 'STRING',
              value: '${VAULT_DYNAMIC_ZT}',
              cardinality: null
            },
            differenceIds: ['difference-1'],
            children: []
          }
        }]
      }]
    }
  };
}

function sourceResponse(): ConfigDriftViewerWorkbenchSourceResponse {
  return {
    previewId: PREVIEW_ID,
    configurationDirectory: 'backend',
    source: coverage('dev1'),
    target: coverage('zt001')
  };
}

function mappingPage(): ConfigDriftViewerWorkbenchMappingPage {
  return {
    previewId: PREVIEW_ID,
    offset: 0,
    limit: 100,
    totalItems: 136,
    totalNodes: 855,
    changedOnly: true,
    items: [{
      role: 'APPLICATION_YAML',
      documentIndex: 0,
      depth: 2,
      originalName: 'password',
      originalPath: 'clients.customer-zt001.password',
      sanitizedName: 'property-1',
      sanitizedPath: 'spring.datasource.password',
      sourceType: 'STRING',
      targetType: 'STRING',
      changeKind: 'CHANGED',
      sensitivity: 'SENSITIVE',
      sourceValueToken: null,
      targetValueToken: null,
      differenceIds: ['difference-1']
    }]
  };
}

function anonymizationPage(): ConfigDriftViewerWorkbenchAnonymizationPage {
  return {
    previewId: PREVIEW_ID,
    offset: 0,
    limit: 100,
    totalItems: 855,
    items: [{
      role: 'APPLICATION_YAML',
      documentIndex: 0,
      path: 'spring.datasource.password',
      relation: 'CHANGED',
      sensitivity: 'SENSITIVE',
      sourceType: 'STRING',
      targetType: 'STRING',
      sourceRepresentation: 'SUPPRESSED',
      targetRepresentation: 'SUPPRESSED',
      sourceValueToken: null,
      targetValueToken: null
    }]
  };
}

function aiInputResponse(): ConfigDriftViewerWorkbenchAiInputResponse {
  return {
    previewId: PREVIEW_ID,
    generated: true,
    characterCount: 120,
    prompt: 'Review compact sanitized configuration. Use configuration-tree.yaml.'
  };
}

function artifactResponse(): ConfigDriftViewerWorkbenchArtifactResponse {
  return {
    previewId: PREVIEW_ID,
    name: 'config-drift-viewer/configuration-tree.yaml',
    mediaType: 'application/yaml',
    characterCount: 42,
    truncated: false,
    content: 'formatVersion: 1\ndocuments:\n  - meta: []'
  };
}

function deepResponse(): ConfigDriftViewerWorkbenchDeepResponse {
  return {
    previewId: PREVIEW_ID,
    requested: true,
    context: {
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
          requestedRef: 'release-42',
          usedRef: null,
          refSource: 'REQUESTED',
          refExists: false,
          deploymentRefConfirmed: false,
          ready: false,
          visibilityLimits: ['Requested ref is unavailable']
        }],
        blockers: [{
          code: 'CODE_REF_NOT_FOUND',
          message: 'Requested ref is unavailable.'
        }],
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

function buttonContaining(root: HTMLElement, text: string): HTMLButtonElement | null {
  return (
    Array.from(root.querySelectorAll('button')).find((button) =>
      button.textContent?.includes(text)
    ) ?? null
  );
}
