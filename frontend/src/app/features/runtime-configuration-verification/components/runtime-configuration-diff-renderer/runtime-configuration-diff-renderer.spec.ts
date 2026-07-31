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

  it('should render changed YAML and VAR syntax with actual values, markers and explicit ABSENT', () => {
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
    expect(compiled.textContent).not.toContain('ttl:');
    expect(compiled.textContent).not.toContain('Ryzyko błędnej bazy');
    expect(compiled.querySelector('[title="zmieniono"]')?.textContent).toContain('🟡');
    expect(compiled.querySelector('[title="usunięto"]')?.textContent).toContain('🔴');
    expect(buttonContaining(compiled, 'Zmiany')?.getAttribute('aria-pressed')).toBe('true');
    expect(buttonContaining(compiled, 'Cały plik')?.getAttribute('aria-pressed')).toBe('false');
  });

  it('should show the full file while keeping unchanged branches collapsed until requested', () => {
    fixture.componentRef.setInput('projection', projection());
    fixture.detectChanges();

    buttonContaining(fixture.nativeElement, 'Cały plik')?.click();
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;
    expect(buttonContaining(compiled, 'Cały plik')?.getAttribute('aria-pressed')).toBe('true');
    expect(compiled.textContent).toContain('cache:');
    expect(compiled.textContent).not.toContain('ttl:');
    const expand = buttonContaining(compiled, 'Rozwiń 2 niezmienionych');
    expect(expand?.getAttribute('aria-expanded')).toBe('false');

    expand?.click();
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('ttl:');
    expect(buttonContaining(compiled, 'Zwiń')?.getAttribute('aria-expanded')).toBe('true');
    expect(compiled.textContent).toContain('---');
    expect(compiled.textContent).toContain('spring.config.activate.on-profile:');
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
                  source: mapValue(2),
                  target: mapValue(2),
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
              changeKind: 'CHANGED',
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
