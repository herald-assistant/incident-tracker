import { TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OpenApiOperationPicker } from './openapi-operation-picker';
import { OpenApiCandidate } from './system-assistant.models';

describe('OpenApiOperationPicker', () => {
  let http: HttpTestingController;
  const close = vi.fn();
  beforeEach(() => {
    close.mockReset();
    TestBed.configureTestingModule({imports: [OpenApiOperationPicker], providers: [provideHttpClient(), provideHttpClientTesting(),
      {provide: DIALOG_DATA, useValue: {id: 'crm', yaml: 'complete source', sourceName: 'crm.yaml'}},
      {provide: DialogRef, useValue: {close}}]});
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const operation = (index: number): OpenApiCandidate => ({method: 'GET', path: `/tickets/${index}`, operationId: `ticket${index}`,
    summary: `Odczyt zgłoszenia ${index}`, tags: ['Zgłoszenia'], unavailableReason: null});
  function load(operations: OpenApiCandidate[]) {
    const fixture = TestBed.createComponent(OpenApiOperationPicker); fixture.detectChanges();
    http.expectOne('/api/system-assistant/configuration/openapi/preview').flush({title: 'CRM', operations, maxSelected: 60, specification: {openapi: '3.0.3', info: {title: 'CRM', version: '1'},
      paths: Object.fromEntries(operations.map(item => [item.path, {[item.method.toLowerCase()]: {operationId: item.operationId, responses: {}}}])),
      components: {schemas: {Ticket: {type: 'object'}}}}});
    fixture.detectChanges(); return fixture;
  }
  it('keeps selection across pages and filters and applies only selected operation keys', () => {
    const operations = Array.from({length: 125}, (_, index) => operation(index));
    const fixture = load(operations); const picker = fixture.componentInstance;
    expect(picker.rows()).toHaveLength(50); expect(picker.pages()).toBe(3);
    expect(Object.keys(picker.renderedSpecification()['paths'] as object)).toHaveLength(50);
    expect(picker.renderedSpecification()['components']).toEqual({schemas: {Ticket: {type: 'object'}}});
    expect(fixture.nativeElement.querySelector('app-openapi-renderer iframe')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.picker-operations')).toBeNull();
    const rendered = picker.renderedSpecification();
    picker.contractName.set('Wybrany CRM'); picker.changeSelection({...operations[0], checked: true});
    expect(picker.renderedSpecification()).toBe(rendered);
    expect(picker.renderedSelection()[0].checked).toBe(true);
    picker.page.set(2); picker.toggle(operations[124], true);
    picker.query.set('ticket124'); expect(picker.filtered()).toHaveLength(1);
    picker.query.set(''); picker.onlySelected.set(true); expect(picker.filtered()).toHaveLength(2);
    picker.apply();
    const request = http.expectOne('/api/system-assistant/configuration/openapi/selection');
    expect(request.request.body).toEqual({id: 'crm', yaml: 'complete source', operations: [
      {method: 'GET', path: '/tickets/0'}, {method: 'GET', path: '/tickets/124'}]});
    request.flush({yaml: 'selected document', operationCount: 2});
    expect(close).toHaveBeenCalledWith({yaml: 'selected document', operationCount: 2, title: 'Wybrany CRM'});
    fixture.destroy();
  });
  it('selects and clears the whole contract regardless of filters and blocks an oversized selection', () => {
    const operations = Array.from({length: 61}, (_, index) => operation(index));
    const unsupported = {...operation(62), method: 'PATCH', unavailableReason: 'Nieobsługiwana metoda HTTP.'};
    const fixture = load([...operations, unsupported]); const picker = fixture.componentInstance;
    picker.changeSelection({...unsupported, checked: true}); expect(picker.selected().size).toBe(0);
    picker.changeSelection({method: 'GET', path: '/unknown', checked: true}); expect(picker.selected().size).toBe(0);
    picker.query.set('ticket0'); picker.method.set('GET'); picker.tag.set('Zgłoszenia'); picker.onlySelected.set(true);
    fixture.detectChanges(); expect(picker.filtered()).toHaveLength(0);
    const button = (label: string) => (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)!;
    expect(button('Zaznacz wszystko').disabled).toBe(false);
    button('Zaznacz wszystko').click(); fixture.detectChanges();
    expect(picker.selected().size).toBe(61); expect(picker.canApply()).toBe(false);
    expect(picker.selected().has(picker.key(unsupported))).toBe(false);
    picker.toggle(operations[60], false); expect(picker.canApply()).toBe(true);
    expect(picker.filtered()).toHaveLength(1);
    button('Odznacz wszystko').click(); fixture.detectChanges();
    expect(picker.selected().size).toBe(0); expect(picker.canApply()).toBe(false);
    expect(button('Odznacz wszystko').disabled).toBe(true);
    fixture.destroy();
  });
  it('retains the selection and keeps the dialog open after validation fails', () => {
    const fixture = load([operation(0)]); const picker = fixture.componentInstance;
    picker.toggle(operation(0), true); picker.apply();
    http.expectOne('/api/system-assistant/configuration/openapi/selection').flush({message: 'Nieobsługiwany schemat wybranej operacji'}, {status: 400, statusText: 'Bad request'});
    expect(picker.selected().size).toBe(1); expect(picker.error()).toContain('schemat');
    expect(picker.applying()).toBe(false); expect(close).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('requires a contract name before importing selected operations', () => {
    const fixture = load([operation(0)]); const picker = fixture.componentInstance;
    picker.toggle(operation(0), true); picker.contractName.set('   '); picker.apply();
    expect(picker.canApply()).toBe(false); http.expectNone('/api/system-assistant/configuration/openapi/selection');
    picker.contractName.set('Mój kontrakt'); expect(picker.canApply()).toBe(true); fixture.destroy();
  });
  it('starts an import without selection and cancels without preparing a document', () => {
    const fixture = load([operation(0), operation(1)]);
    expect(fixture.componentInstance.selected().size).toBe(0);
    const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')];
    buttons.find(button => button.textContent?.trim() === 'Anuluj')!.click();
    expect(close).toHaveBeenCalledWith(); http.expectNone('/api/system-assistant/configuration/openapi/selection'); fixture.destroy();
  });
});
