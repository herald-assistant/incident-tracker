import { TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OpenApiContractViewer } from './openapi-contract-viewer';

describe('OpenApiContractViewer', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({imports: [OpenApiContractViewer], providers: [provideHttpClient(), provideHttpClientTesting(),
      {provide: DIALOG_DATA, useValue: {id: 'crm', name: 'CRM', yaml: 'selected YAML'}}, {provide: DialogRef, useValue: {close: vi.fn()}}]});
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('delivers a validated document only to its sandboxed child window', () => {
    const fixture = TestBed.createComponent(OpenApiContractViewer); fixture.detectChanges();
    const specification = {openapi: '3.0.3', paths: {'/tickets': {get: {operationId: 'read'}}}};
    http.expectOne('/api/system-assistant/configuration/openapi/document').flush({specification}); fixture.detectChanges();
    const renderer = fixture.nativeElement.querySelector('app-openapi-renderer');
    expect(renderer).not.toBeNull();
    expect(renderer.querySelector('iframe').getAttribute('sandbox')).toBe('allow-scripts');
    http.expectNone('/api/system-assistant/configuration'); fixture.destroy();
  });
  it('keeps a document error visible and permits retry without a save', () => {
    const fixture = TestBed.createComponent(OpenApiContractViewer); fixture.detectChanges();
    http.expectOne('/api/system-assistant/configuration/openapi/document').flush({message: 'Niepoprawny kontrakt'}, {status: 400, statusText: 'Bad request'});
    expect(fixture.componentInstance.error()).toContain('kontrakt');
    fixture.componentInstance.load();
    http.expectOne('/api/system-assistant/configuration/openapi/document').flush({specification: {openapi: '3.0.3'}});
    expect(fixture.componentInstance.error()).toBe(''); fixture.destroy();
  });
});
