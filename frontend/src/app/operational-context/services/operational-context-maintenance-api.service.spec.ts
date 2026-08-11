import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OperationalContextMaintenanceApiService } from './operational-context-maintenance-api.service';

describe('OperationalContextMaintenanceApiService', () => {
  let service: OperationalContextMaintenanceApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OperationalContextMaintenanceApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads local-copy maintenance capabilities', () => {
    service.getCapabilities().subscribe();
    const request = http.expectOne('/api/operational-context/catalog/capabilities');
    expect(request.request.method).toBe('GET');
    request.flush({ source: 'tdw-data/operational-context', supportedEntityTypes: ['system'] });
  });

  it('creates an anonymized CRM system without read projections or version headers', () => {
    const payload = { id: 'crm-contact-core', name: 'CRM Contact Core', references: { processes: ['crm-contact-update'] } };
    service.create({ type: 'system', id: 'crm-contact-core', payload }).subscribe();
    const request = http.expectOne('/api/operational-context/catalog/entities/system');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.has('If-Match')).toBe(false);
    expect(request.request.body).toEqual({ type: 'system', id: 'crm-contact-core', payload });
    expect(request.request.body.payload.rawSourcePreview).toBeUndefined();
    expect(request.request.body.payload.resolvedOwnership).toBeUndefined();
    request.flush({ entity: { type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml', payload } });
  });

  it('encodes anonymized CRM IDs and updates and deletes without version headers', () => {
    const payload = { id: 'crm/contact core', name: 'CRM Contact Core' };
    service.update({ type: 'system', id: 'crm/contact core', payload }).subscribe();
    const update = http.expectOne('/api/operational-context/catalog/entities/system/crm%2Fcontact%20core');
    expect(update.request.method).toBe('PUT');
    expect(update.request.headers.has('If-Match')).toBe(false);
    update.flush({ entity: { type: 'system', id: payload.id, sourceFile: 'systems.yml', payload } });

    service.delete('system', 'crm/contact core').subscribe();
    const deletion = http.expectOne('/api/operational-context/catalog/entities/system/crm%2Fcontact%20core');
    expect(deletion.request.method).toBe('DELETE');
    expect(deletion.request.headers.has('If-Match')).toBe(false);
    deletion.flush(null);
  });

  it('loads editable CRM entity and delete impact', () => {
    service.getEntity('process', 'crm-contact-update').subscribe();
    http.expectOne('/api/operational-context/catalog/entities/process/crm-contact-update').flush({});
    service.getDeleteImpact('process', 'crm-contact-update').subscribe();
    http.expectOne('/api/operational-context/catalog/entities/process/crm-contact-update/delete-impact').flush({ allowed: false, inboundReferences: [] });
  });

  it.each([
    {
      type: 'glossary-term' as const,
      id: 'crm-customer-profile',
      sourceFile: 'glossary.yml',
      payload: {
        id: 'crm-customer-profile',
        term: 'Customer profile',
        category: 'domain-term',
        definition: 'An anonymized CRM customer profile.'
      }
    },
    {
      type: 'handoff-rule' as const,
      id: 'crm-contact-sync-delayed',
      sourceFile: 'handoff-rules.yml',
      payload: {
        id: 'crm-contact-sync-delayed',
        title: 'CRM contact synchronization is delayed',
        requiredEvidence: ['An anonymized CRM correlation key.']
      }
    }
  ])('creates a structured anonymized CRM $type through the write contract', ({ type, id, sourceFile, payload }) => {
    service.create({ type, id, payload }).subscribe();

    const request = http.expectOne(`/api/operational-context/catalog/entities/${type}`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.has('If-Match')).toBe(false);
    expect(request.request.body).toEqual({ type, id, payload });
    request.flush({
      entity: { type, id, sourceFile, payload }
    });
  });
});
