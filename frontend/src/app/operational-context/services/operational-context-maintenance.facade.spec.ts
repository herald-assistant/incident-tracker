import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { OperationalContextMaintenanceApiService } from './operational-context-maintenance-api.service';
import { OperationalContextMaintenanceFacade } from './operational-context-maintenance.facade';

describe('OperationalContextMaintenanceFacade', () => {
  it('disables maintenance when the anonymized CRM local copy is unavailable', () => {
    const { facade } = setup({ getCapabilities: vi.fn(() => throwError(() => new Error('offline'))) });
    facade.loadCapabilities();
    expect(facade.writable()).toBe(false);
    expect(facade.capabilities()).toBeNull();
    expect(facade.capabilitiesError()).toContain('local operational context copy');
  });

  it('keeps anonymized CRM edits after a domain validation error', () => {
    const apiError = new HttpErrorResponse({ status: 422, error: { code: 'INVALID_CANDIDATE', message: 'CRM catalogue data is invalid.', fieldErrors: [] } });
    const { facade } = setup({ update: vi.fn(() => throwError(() => apiError)) });
    makeWritable(facade);
    facade.openEdit('system', 'crm-contact-core');
    facade.save({ id: 'crm-contact-core', name: 'CRM Contact Platform' });
    expect(facade.editor()?.entity.id).toBe('crm-contact-core');
    expect(facade.error()).toContain('CRM catalogue data is invalid');
  });

  it('blocks delete when an anonymized CRM process still references the system', () => {
    const { facade, api } = setup({
      getDeleteImpact: vi.fn(() => of({
        type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml', allowed: false,
        inboundReferences: [{ sourceType: 'process', sourceId: 'crm-contact-update', relationType: 'system', sourceFile: 'processes.yml', fieldPath: '/steps/0/systemId' }]
      }))
    });
    makeWritable(facade);
    facade.requestDelete('system', 'crm-contact-core');
    facade.confirmDelete();
    expect(facade.deleteImpact()?.allowed).toBe(false);
    expect(api.delete).not.toHaveBeenCalled();
  });

  it.each([
    ['glossary-term', { id: '', term: '', category: '' }],
    ['handoff-rule', { id: '', title: '' }]
  ] as const)('opens a correctly shaped anonymized CRM create form for %s', (type, payload) => {
    const { facade } = setup();
    facade.capabilities.set({
      source: 'tdw-data/operational-context',
      supportedEntityTypes: [type],
    });

    facade.openCreate(type);

    expect(facade.editor()?.entity.payload).toEqual(payload);
    expect(facade.editor()?.type).toBe(type);
  });
});

function setup(overrides: Record<string, unknown> = {}) {
  const crmEntity = { type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml', payload: { id: 'crm-contact-core', name: 'CRM Contact Core' } };
  const api = {
    getCapabilities: vi.fn(() => of({ source: 'tdw-data/operational-context', supportedEntityTypes: ['system'] })),
    getEntity: vi.fn(() => of(crmEntity)),
    create: vi.fn(() => of({ entity: crmEntity })),
    update: vi.fn(() => of({ entity: crmEntity })),
    getDeleteImpact: vi.fn(() => of({ type: 'system', id: crmEntity.id, sourceFile: crmEntity.sourceFile, allowed: true, inboundReferences: [] })),
    delete: vi.fn(() => of(void 0)),
    ...overrides
  };
  TestBed.configureTestingModule({ providers: [{ provide: OperationalContextMaintenanceApiService, useValue: api }] });
  return { facade: TestBed.inject(OperationalContextMaintenanceFacade), api };
}

function makeWritable(facade: OperationalContextMaintenanceFacade): void {
  facade.capabilities.set({ source: 'tdw-data/operational-context', supportedEntityTypes: ['system'] });
}
