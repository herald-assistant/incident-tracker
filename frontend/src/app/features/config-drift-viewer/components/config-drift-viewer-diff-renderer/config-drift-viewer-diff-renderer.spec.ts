import { ComponentFixture, TestBed } from '@angular/core/testing';

import {
  ConfigDriftViewerDiffAnnotation,
  ConfigDriftViewerDiffProjection
} from '../../models/config-drift-viewer.models';
import {
  ConfigDriftViewerDiffRendererComponent
} from './config-drift-viewer-diff-renderer';

describe('ConfigDriftViewerDiffRendererComponent', () => {
  let fixture: ComponentFixture<ConfigDriftViewerDiffRendererComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfigDriftViewerDiffRendererComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(ConfigDriftViewerDiffRendererComponent);
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

    let compiled = fixture.nativeElement as HTMLElement;
    expect(buttonContaining(compiled, 'Zmiany')?.getAttribute('aria-pressed')).toBe('true');
    expect(buttonContaining(compiled, 'Cały plik')?.getAttribute('aria-pressed')).toBe('false');

    buttonContaining(compiled, 'Cały plik')?.click();
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('backend/application.yml.kv');
    expect(compiled.textContent).toContain('spring:');
    expect(compiled.textContent).toContain('url:');
    const changedUrl = rowContaining(compiled, 'url:');
    const inlineDiff = changedUrl?.querySelector('.value-comparison--inline-diff');
    expect(inlineDiff?.querySelectorAll('small')[0]?.textContent).toBe('source');
    expect(inlineDiff?.querySelector('span[aria-hidden="true"]')?.textContent).toBe('≠');
    expect(inlineDiff?.querySelectorAll('small')[1]?.textContent).toBe('target');
    expect(inlineDiff?.querySelector('.inline-diff__removed')?.textContent).toBe('1');
    expect(inlineDiff?.querySelector('.inline-diff__added')?.textContent).toBe('2');
    expect(inlineDiff?.querySelector('code')?.getAttribute('aria-label')).toContain(
      'source "https://customer-profile.crm-main-dev1.svc.cluster.local:8443"'
    );
    expect(inlineDiff?.querySelector('code')?.getAttribute('aria-label')).toContain(
      'target "https://customer-profile.crm-main-dev2.svc.cluster.local:8443"'
    );
    expect(compiled.textContent).toContain('username:');
    const removedUsername = rowContaining(compiled, 'username:');
    const removedPresenceDiff = removedUsername?.querySelector('.value-comparison--presence-diff');
    expect(removedPresenceDiff?.querySelectorAll('small')).toHaveLength(0);
    expect(removedPresenceDiff?.querySelector('.presence-diff__removed')?.textContent).toBe('"operator"');
    expect(removedPresenceDiff?.querySelector('.presence-diff__absent')?.textContent).toBe('BRAK');
    expect(removedPresenceDiff?.textContent).toContain('→');
    const addedOwner = rowContaining(compiled, 'owner =');
    const addedPresenceDiff = addedOwner?.querySelector('.value-comparison--presence-diff');
    expect(addedPresenceDiff?.querySelectorAll('small')).toHaveLength(0);
    expect(addedPresenceDiff?.querySelector('.presence-diff__absent')?.textContent).toBe('BRAK');
    expect(addedPresenceDiff?.querySelector('.presence-diff__added')?.textContent).toBe('"crm-team"');
    expect(compiled.textContent).not.toContain('ABSENT');
    expect(compiled.textContent).toContain('feature {');
    expect(compiled.textContent).toContain('enabled =');
    const changedBoolean = rowContaining(compiled, 'enabled =');
    const booleanInlineDiff = changedBoolean?.querySelector('.value-comparison--inline-diff');
    expect(booleanInlineDiff?.textContent).toContain('source≠target');
    expect(booleanInlineDiff?.querySelector('.inline-diff__removed')?.textContent).toBe('false');
    expect(booleanInlineDiff?.querySelector('.inline-diff__added')?.textContent).toBe('true');
    expect(booleanInlineDiff?.querySelector('code')?.getAttribute('aria-label')).toBe(
      'source false, target true'
    );
    expect(changedBoolean?.querySelector('.value-comparison > span:not([aria-hidden])'))
      .toBeNull();
    expect(compiled.textContent).toContain('ttl:');
    expect(compiled.textContent).toContain('queueName:');
    const unchangedComparison = compiled.querySelector('.value-comparison--same');
    expect(unchangedComparison?.querySelectorAll('small')[0]?.textContent).toBe('source');
    expect(unchangedComparison?.querySelector('span')?.textContent).toBe('=');
    expect(unchangedComparison?.querySelectorAll('small')[1]?.textContent).toBe('target');
    expect(compiled.textContent).toContain('"CRM_CASE_STATUS_DEV1"');
    expect(compiled.textContent).not.toContain('pól');
    expect(compiled.textContent).not.toContain('elementów');
    expect(compiled.textContent).not.toContain('Ryzyko błędnej bazy');
    expect(compiled.querySelector('[title="zmieniono"]')?.textContent).toContain('🟠');
    expect(compiled.querySelector('[title="usunięto"]')?.textContent).toContain('🔴');
    expect(compiled.querySelector('[title="zmiana efektywna"]')?.textContent).toContain('🟡');
    const effectiveRow = rowContaining(compiled, 'logging.level:');
    expect(effectiveRow?.querySelector('.value-comparison--same')?.textContent)
      .toContain('"${local.logging_level}"');
    const effectiveResolved = effectiveRow?.querySelector('.effective-resolved-diff');
    expect(effectiveResolved?.textContent).toContain('resolved');
    expect(effectiveResolved?.textContent).toContain('source≠target');
    expect(effectiveResolved?.querySelector('.inline-diff__removed')?.textContent).toBe('DEBUG');
    expect(effectiveResolved?.querySelector('.inline-diff__added')?.textContent).toBe('INFO');
    expect(effectiveResolved?.querySelector('code')?.getAttribute('aria-label')).toBe(
      'resolved source "DEBUG", target "INFO"'
    );
    expect(compiled.querySelectorAll('.change-label')).toHaveLength(0);
    expect(rowContaining(compiled, 'spring:')?.querySelector('.change-marker:not(.change-marker--empty)'))
      .toBeNull();
    expect(buttonContaining(compiled, 'Zmiany')?.getAttribute('aria-pressed')).toBe('false');
    expect(buttonContaining(compiled, 'Cały plik')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('should render changed branches by default and allow expanding to full tree', () => {
    fixture.componentRef.setInput('projection', projection());
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;
    expect(buttonContaining(compiled, 'Zmiany')?.getAttribute('aria-pressed')).toBe('true');
    expect(compiled.textContent).not.toContain('cache:');
    expect(compiled.textContent).not.toContain('ttl:');
    expect(compiled.textContent).not.toContain('queueName:');
    expect(compiled.textContent).not.toContain('on-profile:');

    buttonContaining(compiled, 'Cały plik')?.click();
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('cache:');
    expect(compiled.textContent).toContain('ttl:');
    expect(compiled.textContent).toContain('queueName:');
    expect(compiled.textContent).toContain('---');
    expect(compiled.textContent).toContain('on-profile:');
  });

  it('should render multi-document YAML as one file without nested details or document headers', () => {
    fixture.componentRef.setInput('projection', projection());
    fixture.detectChanges();

    buttonContaining(fixture.nativeElement, 'Cały plik')?.click();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const localVar = fileContaining(compiled, 'backend/local.var');
    const applicationYaml = fileContaining(compiled, 'backend/application.yml.kv');

    expect(localVar?.querySelectorAll('.configuration-document')).toHaveLength(1);
    expect(applicationYaml?.querySelectorAll('.configuration-document')).toHaveLength(2);
    expect(applicationYaml?.querySelectorAll('details')).toHaveLength(0);
    expect(applicationYaml?.querySelectorAll('.document-separator')).toHaveLength(1);
    expect(applicationYaml?.textContent).not.toContain('Dokument 1');
    expect(applicationYaml?.textContent).not.toContain('Dokument 2');
    expect(applicationYaml?.textContent).not.toContain('Szczegóły');
    expect(applicationYaml?.querySelector('.profile-line')).toBeNull();
    const profileRow = rowContaining(applicationYaml!, 'on-profile:');
    expect(profileRow).not.toBeNull();
    expect(profileRow?.querySelector('.value-comparison--same')?.textContent).toContain('=');
    expect(applicationYaml?.textContent?.match(/on-profile:/g)).toHaveLength(1);
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

});

function projection(): ConfigDriftViewerDiffProjection {
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
                        source: scalar(
                          'STRING',
                          'https://customer-profile.crm-main-dev1.svc.cluster.local:8443'
                        ),
                        target: scalar(
                          'STRING',
                          'https://customer-profile.crm-main-dev2.svc.cluster.local:8443'
                        ),
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
                      source: scalar('STRING', 'CRM_CASE_STATUS_DEV1'),
                      target: scalar('STRING', 'CRM_CASE_STATUS_DEV1'),
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
            sourceProfile: scalar('STRING', 'default, withCustomerProfileMock'),
            targetProfile: scalar('STRING', 'default, withCustomerProfileMock'),
            root: {
              name: 'document-1',
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
                    name: 'config',
                    path: 'spring.config',
                    changeKind: 'UNCHANGED',
                    source: mapValue(1),
                    target: mapValue(1),
                    differenceIds: [],
                    children: [{
                      name: 'activate',
                      path: 'spring.config.activate',
                      changeKind: 'UNCHANGED',
                      source: mapValue(1),
                      target: mapValue(1),
                      differenceIds: [],
                      children: [{
                        name: 'on-profile',
                        path: 'spring.config.activate.on-profile',
                        changeKind: 'UNCHANGED',
                        source: scalar('STRING', 'default, withCustomerProfileMock'),
                        target: scalar('STRING', 'default, withCustomerProfileMock'),
                        differenceIds: [],
                        children: []
                      }]
                    }]
                  }]
                },
                {
                  name: 'logging.level',
                  path: 'logging.level',
                  changeKind: 'EFFECTIVE_CHANGED',
                  source: scalar('STRING', '${local.logging_level}'),
                  target: scalar('STRING', '${local.logging_level}'),
                  sourceEffective: scalar('STRING', 'DEBUG'),
                  targetEffective: scalar('STRING', 'INFO'),
                  differenceIds: ['difference-3'],
                  children: []
                }
              ]
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
              target: mapValue(2),
              differenceIds: [],
              children: [
                {
                  name: 'enabled',
                  path: 'feature.enabled',
                  changeKind: 'CHANGED',
                  source: scalar('BOOLEAN', false),
                  target: scalar('BOOLEAN', true),
                  differenceIds: ['difference-4'],
                  children: []
                },
                {
                  name: 'owner',
                  path: 'feature.owner',
                  changeKind: 'ADDED',
                  source: absent,
                  target: scalar('STRING', 'crm-team'),
                  differenceIds: ['difference-5'],
                  children: []
                }
              ]
            }]
          }
        }]
      }
    ]
  };
}

function annotations(): ConfigDriftViewerDiffAnnotation[] {
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
