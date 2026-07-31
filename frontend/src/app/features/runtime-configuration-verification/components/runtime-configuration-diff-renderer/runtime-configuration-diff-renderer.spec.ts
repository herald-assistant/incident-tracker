import { ComponentFixture, TestBed } from '@angular/core/testing';

import {
  RuntimeConfigurationDiffAnnotation,
  RuntimeConfigurationDiffProjection,
  RuntimeConfigurationDifference
} from '../../models/runtime-configuration-verification.models';
import {
  RuntimeConfigurationDiffRendererComponent
} from './runtime-configuration-diff-renderer';

describe('RuntimeConfigurationDiffRendererComponent', () => {
  let fixture: ComponentFixture<RuntimeConfigurationDiffRendererComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RuntimeConfigurationDiffRendererComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(RuntimeConfigurationDiffRendererComponent);
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: vi.fn()
    });
  });

  it('should render leaf-only markers, tooltips and unchanged values without collection counts', () => {
    fixture.componentRef.setInput('projection', projection());
    fixture.componentRef.setInput('mode', 'BASIC');
    fixture.componentRef.setInput('annotations', annotations());
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('backend/application.yml.kv');
    expect(compiled.textContent).toContain('spring:');
    expect(compiled.textContent).toContain('url:');
    expect(compiled.textContent).toContain('"jdbc:dev"');
    expect(compiled.textContent).toContain('"jdbc:zt"');
    expect(compiled.textContent).toContain('username:');
    expect(compiled.textContent).toContain('ABSENT');
    expect(compiled.textContent).toContain('feature {');
    expect(compiled.textContent).toContain('enabled =');
    expect(compiled.textContent).toContain('ttl:');
    expect(compiled.textContent).toContain('queueName:');
    const unchangedComparison = compiled.querySelector('.value-comparison--same');
    expect(unchangedComparison?.querySelectorAll('small')[0]?.textContent).toBe('source');
    expect(unchangedComparison?.querySelector('span')?.textContent).toBe('=');
    expect(unchangedComparison?.querySelectorAll('small')[1]?.textContent).toBe('target');
    expect(compiled.textContent).toContain('"CLP_SF_SF_CHANGE_STATUS_DEV1"');
    expect(compiled.textContent).not.toContain('pól');
    expect(compiled.textContent).not.toContain('elementów');
    expect(compiled.textContent).not.toContain('Ryzyko błędnej bazy');
    expect(compiled.querySelector('[title="zmieniono"]')?.textContent).toContain('🟠');
    expect(compiled.querySelector('[title="usunięto"]')?.textContent).toContain('🔴');
    expect(compiled.querySelector('[title="zmiana efektywna"]')?.textContent).toContain('🟡');
    expect(compiled.querySelectorAll('.change-label')).toHaveLength(0);
    expect(rowContaining(compiled, 'spring:')?.querySelector('.change-marker:not(.change-marker--empty)'))
      .toBeNull();
    expect(buttonContaining(compiled, 'Zmiany')?.getAttribute('aria-pressed')).toBe('false');
    expect(buttonContaining(compiled, 'Cały plik')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('should allow narrowing the full tree to changed branches', () => {
    fixture.componentRef.setInput('projection', projection());
    fixture.detectChanges();

    buttonContaining(fixture.nativeElement, 'Zmiany')?.click();
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;
    expect(buttonContaining(compiled, 'Zmiany')?.getAttribute('aria-pressed')).toBe('true');
    expect(compiled.textContent).not.toContain('cache:');
    expect(compiled.textContent).not.toContain('ttl:');
    expect(compiled.textContent).not.toContain('queueName:');

    buttonContaining(compiled, 'Cały plik')?.click();
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('cache:');
    expect(compiled.textContent).toContain('ttl:');
    expect(compiled.textContent).toContain('queueName:');
    expect(compiled.textContent).toContain('---');
    expect(compiled.textContent).toContain('spring.config.activate.on-profile:');
  });

  it('should omit the document frame for a single document and keep it for multi-document YAML', () => {
    fixture.componentRef.setInput('projection', projection());
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const localVar = fileContaining(compiled, 'backend/local.var');
    const applicationYaml = fileContaining(compiled, 'backend/application.yml.kv');

    expect(localVar?.querySelector('.configuration-document--single')).not.toBeNull();
    expect(localVar?.querySelector('.configuration-document > summary')).toBeNull();
    expect(applicationYaml?.querySelectorAll('.configuration-document--single')).toHaveLength(0);
    expect(applicationYaml?.querySelectorAll('.configuration-document > summary')).toHaveLength(2);
    expect(applicationYaml?.textContent).toContain('Dokument 1');
    expect(applicationYaml?.textContent).toContain('Dokument 2');
  });

  it('should order files from detailed kv configuration through local to global variables', () => {
    const shuffled = projection();
    const application = shuffled.files[0];
    const local = shuffled.files[1];
    const global = {
      ...local,
      role: 'GLOBAL_VAR' as const,
      sourcePath: 'global.var',
      targetPath: 'global.var'
    };
    shuffled.files = [global, local, application];

    fixture.componentRef.setInput('projection', shuffled);
    fixture.detectChanges();

    const labels = Array.from(
      (fixture.nativeElement as HTMLElement)
        .querySelectorAll<HTMLElement>('.configuration-file > summary strong')
    ).map((label) => label.textContent?.trim());
    expect(labels).toEqual([
      'backend/application.yml.kv',
      'backend/local.var',
      'global.var'
    ]);
  });

  it('should attach DEEP annotations by difference id and navigate to their AI source', () => {
    fixture.componentRef.setInput('projection', projection());
    fixture.componentRef.setInput('mode', 'DEEP');
    fixture.componentRef.setInput('annotations', annotations());
    fixture.componentRef.setInput('focusedReferenceId', 'difference-1');
    const selected: string[] = [];
    fixture.componentInstance.referenceSelected.subscribe((id) => selected.push(id));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('AI INTERPRETATION');
    expect(compiled.textContent).toContain('Ryzyko błędnej bazy');
    expect(compiled.querySelector('#difference-1')?.closest('.configuration-row')?.classList)
      .toContain('configuration-row--focused');

    buttonContaining(compiled, 'Ryzyko błędnej bazy')?.click();
    expect(selected).toEqual(['observation-1']);
  });

  it('should provide an explicit fallback for older results without a file projection', () => {
    fixture.componentRef.setInput('projection', null);
    fixture.componentRef.setInput('fallbackDifferences', legacyDifferences());
    fixture.componentRef.setInput('focusedReferenceId', 'legacy-difference');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('przed wprowadzeniem widoku plikowego');
    expect(compiled.textContent).toContain('legacy.path');
    expect(compiled.textContent).toContain('bez wartości source/target');
    expect(compiled.querySelector('#legacy-difference')?.classList)
      .toContain('configuration-row--focused');
  });
});

function projection(): RuntimeConfigurationDiffProjection {
  const absent = {
    presence: 'ABSENT' as const,
    type: null,
    value: null,
    cardinality: null
  };
  const mapValue = (cardinality: number) => ({
    presence: 'PRESENT' as const,
    type: 'MAP',
    value: null,
    cardinality
  });
  const scalar = (type: string, value: unknown) => ({
    presence: 'PRESENT' as const,
    type,
    value,
    cardinality: null
  });
  return {
    sourceBranch: 'dev1',
    targetBranch: 'zt001',
    files: [
      {
        role: 'APPLICATION_YAML',
        format: 'YAML',
        sourcePath: 'backend/application.yml.kv',
        targetPath: 'backend/application.yml.kv',
        sourcePresent: true,
        targetPresent: true,
        documents: [
          {
            documentIndex: 0,
            sourcePresent: true,
            targetPresent: true,
            sourceProfile: absent,
            targetProfile: absent,
            root: {
              name: 'document-0',
              path: '',
              changeKind: 'UNCHANGED',
              source: mapValue(2),
              target: mapValue(2),
              differenceIds: [],
              children: [
                {
                  name: 'spring',
                  path: 'spring',
                  changeKind: 'UNCHANGED',
                  source: mapValue(1),
                  target: mapValue(1),
                  differenceIds: [],
                  children: [{
                    name: 'datasource',
                    path: 'spring.datasource',
                    changeKind: 'UNCHANGED',
                    source: mapValue(2),
                    target: mapValue(1),
                    differenceIds: [],
                    children: [
                      {
                        name: 'url',
                        path: 'spring.datasource.url',
                        changeKind: 'CHANGED',
                        source: scalar('STRING', 'jdbc:dev'),
                        target: scalar('STRING', 'jdbc:zt'),
                        differenceIds: ['difference-1'],
                        children: []
                      },
                      {
                        name: 'username',
                        path: 'spring.datasource.username',
                        changeKind: 'REMOVED',
                        source: scalar('STRING', 'operator'),
                        target: absent,
                        differenceIds: ['difference-2'],
                        children: []
                      }
                    ]
                  }]
                },
                {
                  name: 'cache',
                  path: 'cache',
                  changeKind: 'UNCHANGED',
                  source: mapValue(3),
                  target: mapValue(3),
                  differenceIds: [],
                  children: [
                    {
                      name: 'enabled',
                      path: 'cache.enabled',
                      changeKind: 'UNCHANGED',
                      source: scalar('BOOLEAN', true),
                      target: scalar('BOOLEAN', true),
                      differenceIds: [],
                      children: []
                    },
                    {
                      name: 'ttl',
                      path: 'cache.ttl',
                      changeKind: 'UNCHANGED',
                      source: scalar('NUMBER', 60),
                      target: scalar('NUMBER', 60),
                      differenceIds: [],
                      children: []
                    },
                    {
                      name: 'queueName',
                      path: 'cache.queueName',
                      changeKind: 'UNCHANGED',
                      source: scalar('STRING', 'CLP_SF_SF_CHANGE_STATUS_DEV1'),
                      target: scalar('STRING', 'CLP_SF_SF_CHANGE_STATUS_DEV1'),
                      differenceIds: [],
                      children: []
                    }
                  ]
                }
              ]
            }
          },
          {
            documentIndex: 1,
            sourcePresent: true,
            targetPresent: true,
            sourceProfile: scalar('STRING', 'dev'),
            targetProfile: scalar('STRING', 'zt'),
            root: {
              name: 'document-1',
              path: 'logging.level',
              changeKind: 'EFFECTIVE_CHANGED',
              source: scalar('STRING', 'DEBUG'),
              target: scalar('STRING', 'INFO'),
              differenceIds: ['difference-3'],
              children: []
            }
          }
        ]
      },
      {
        role: 'LOCAL_VAR',
        format: 'VAR',
        sourcePath: 'backend/local.var',
        targetPath: 'backend/local.var',
        sourcePresent: true,
        targetPresent: true,
        documents: [{
          documentIndex: 0,
          sourcePresent: true,
          targetPresent: true,
          sourceProfile: absent,
          targetProfile: absent,
          root: {
            name: 'document-0',
            path: '',
            changeKind: 'UNCHANGED',
            source: mapValue(1),
            target: mapValue(1),
            differenceIds: [],
            children: [{
              name: 'feature',
              path: 'feature',
              changeKind: 'UNCHANGED',
              source: mapValue(1),
              target: mapValue(1),
              differenceIds: [],
              children: [{
                name: 'enabled',
                path: 'feature.enabled',
                changeKind: 'CHANGED',
                source: scalar('BOOLEAN', false),
                target: scalar('BOOLEAN', true),
                differenceIds: ['difference-4'],
                children: []
              }]
            }]
          }
        }]
      }
    ]
  };
}

function annotations(): RuntimeConfigurationDiffAnnotation[] {
  return [{
    sourceId: 'observation-1',
    kind: 'OBSERVATION',
    comment: 'Ryzyko błędnej bazy po wdrożeniu.',
    confidence: null,
    hypothesis: false,
    differenceIds: ['difference-1'],
    findingIds: ['finding-1']
  }];
}

function legacyDifferences(): RuntimeConfigurationDifference[] {
  return [{
    differenceId: 'legacy-difference',
    role: 'LOCAL_VAR',
    documentIndex: 0,
    path: 'legacy.path',
    kind: 'ADDED',
    sourceType: null,
    targetType: 'STRING',
    sensitivity: 'NON_SENSITIVE',
    sourceValueToken: null,
    targetValueToken: 'value-1'
  }];
}

function buttonContaining(root: HTMLElement, text: string): HTMLButtonElement | null {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button'))
    .find((button) => button.textContent?.includes(text)) ?? null;
}

function rowContaining(root: HTMLElement, text: string): HTMLElement | null {
  return Array.from(root.querySelectorAll<HTMLElement>('.configuration-row'))
    .find((row) => row.querySelector('.syntax-name')?.textContent?.includes(text)) ?? null;
}

function fileContaining(root: HTMLElement, text: string): HTMLElement | null {
  return Array.from(root.querySelectorAll<HTMLElement>('.configuration-file'))
    .find((file) => file.querySelector('summary strong')?.textContent?.includes(text)) ?? null;
}
