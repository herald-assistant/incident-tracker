import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { OperationalContextEntityDetailDto } from '../../models/operational-context.models';
import { ContextEntityDrawerComponent } from './context-entity-drawer';

describe('ContextEntityDrawerComponent', () => {
  it('should render resolved ownership without raw object strings', async () => {
    await TestBed.configureTestingModule({
      imports: [ContextEntityDrawerComponent],
      providers: [provideAnimationsAsync('noop')]
    }).compileComponents();

    const fixture = TestBed.createComponent(ContextEntityDrawerComponent);
    fixture.componentRef.setInput('detail', entityDetail());
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const text = compiled.textContent || '';

    expect(text).toContain('Resolved ownership');
    expect(text).toContain('bounded context boundary');
    expect(text).toContain('Customer Profile Domain Owner');
    expect(text).toContain('Customer Consent Domain Owner');
    expect(text).toContain('Resolution path');
    expect(text).toContain('Visibility limits');
    expect(text).not.toContain('Recognition signals');
    expect(text).not.toContain('[object Object]');
  });

  it('keeps standard actions and adds maintenance actions only when writable', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityDrawerComponent], providers: [provideAnimationsAsync('noop')] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityDrawerComponent);
    fixture.componentRef.setInput('detail', { ...entityDetail(), id: 'crm-contact-domain', title: 'CRM Contact Domain' });
    fixture.componentRef.setInput('writable', true);
    fixture.detectChanges();
    const labels = Array.from(
      fixture.nativeElement.querySelectorAll('.entity-drawer__actions button') as NodeListOf<HTMLButtonElement>
    ).map((button) => button.getAttribute('aria-label'));
    expect(labels).toEqual([
      'Zaproponuj uzupełnienie z AI',
      'Edytuj pozycję',
      'Usuń pozycję',
      'Kopiuj szczegóły pozycji',
      'Otwórz dane źródłowe',
      'Zamknij szczegóły'
    ]);
  });

  it('emits exact issue targets from the entity detail', async () => {
    await TestBed.configureTestingModule({ imports: [ContextEntityDrawerComponent], providers: [provideAnimationsAsync('noop')] }).compileComponents();
    const fixture = TestBed.createComponent(ContextEntityDrawerComponent);
    const finding = {
      id: 'missing-owner', severity: 'warning', category: 'ownership', entityType: 'system',
      entityId: 'crm-contact-service', title: 'Missing owner', detail: 'No owner', sourceRefs: [],
      suggestedFix: 'Set owner', impact: 'Unknown handoff'
    };
    const question = {
      id: 'owner-question', sourceFile: 'systems.yml', entityType: 'system', entityId: 'crm-contact-service',
      question: 'Who owns it?', severity: 'warning', status: 'open'
    };
    fixture.componentRef.setInput('detail', { ...entityDetail(), validationFindings: [finding], openQuestions: [question] });
    fixture.componentRef.setInput('writable', true);
    const assistEntity = vi.fn();
    const assistFinding = vi.fn();
    const assistQuestion = vi.fn();
    fixture.componentInstance.assistEntity.subscribe(assistEntity);
    fixture.componentInstance.assistValidationFinding.subscribe(assistFinding);
    fixture.componentInstance.assistOpenQuestion.subscribe(assistQuestion);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[aria-label="Zaproponuj uzupełnienie z AI"]') as HTMLButtonElement).click();
    const issueActions = fixture.nativeElement.querySelectorAll('.entity-drawer__issue-action') as NodeListOf<HTMLButtonElement>;
    issueActions[0].click();
    issueActions[1].click();

    expect(assistEntity).toHaveBeenCalledOnce();
    expect(assistFinding).toHaveBeenCalledWith(finding);
    expect(assistQuestion).toHaveBeenCalledWith(question);
  });
});

function entityDetail(): OperationalContextEntityDetailDto {
  return {
    type: 'bounded-context',
    id: 'customer-profile-management',
    title: 'Customer Profile Management',
    subtitle: '',
    overviewSections: [
      {
        title: 'Resolved ownership',
        fields: {
          situationType: 'bounded-context-boundary',
          primaryOwners: [
            {
              targetType: 'bounded-context',
              targetId: 'customer-profile-management',
              targetLabel: 'Customer Profile Management',
              ownerTeamIds: [],
              ownerLabel: 'Customer Profile Domain Owner',
              source: 'inferred-owner',
              confidence: 'medium'
            }
          ],
          partnerOwners: [
            {
              targetType: 'bounded-context',
              targetId: 'customer-consent-management',
              targetLabel: 'Customer Consent Management',
              ownerTeamIds: [],
              ownerLabel: 'Customer Consent Domain Owner',
              source: 'inferred-owner',
              confidence: 'medium'
            }
          ],
          handoffReason: 'Problem type bounded-context-boundary requires both domain owners.',
          resolutionPath: [
            'request.boundedContextIds -> customer-profile-management',
            'bounded-context:customer-consent-management -> inferred owner'
          ],
          visibilityLimits: [
            'Bounded context customer-profile-management has no explicit owner.'
          ]
        }
      }
    ],
    relatedEntities: [],
    recognitionSignals: [],
    explainabilitySections: [],
    validationFindings: [],
    openQuestions: [],
    sourceReferences: [],
    rawSourcePreview: ''
  };
}
