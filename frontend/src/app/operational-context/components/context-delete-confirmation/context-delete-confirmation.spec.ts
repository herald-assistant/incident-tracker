import { TestBed } from '@angular/core/testing';

import { ContextDeleteConfirmationComponent } from './context-delete-confirmation';

describe('ContextDeleteConfirmationComponent', () => {
  it('exposes modal focus guardrails and blocks deletion for inbound anonymized CRM references', async () => {
    await TestBed.configureTestingModule({ imports: [ContextDeleteConfirmationComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextDeleteConfirmationComponent);
    fixture.componentRef.setInput('impact', {
      type: 'system', id: 'crm-contact-core', sourceFile: 'systems.yml', allowed: false,
      inboundReferences: [{ sourceType: 'process', sourceId: 'crm-contact-update', relationType: 'system', sourceFile: 'processes.yml', fieldPath: '/steps/0/systemId' }]
    });
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="dialog"]')?.getAttribute('aria-modal')).toBe('true');
    expect(element.textContent).toContain('crm-contact-update');
    expect((element.querySelector('.danger-button') as HTMLButtonElement).disabled).toBe(true);
    fixture.destroy();
  });
});
