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
    expect(text).toContain('Credit Decision Domain Owner');
    expect(text).toContain('Customer Consent Domain Owner');
    expect(text).toContain('Resolution path');
    expect(text).toContain('Visibility limits');
    expect(text).not.toContain('[object Object]');
  });
});

function entityDetail(): OperationalContextEntityDetailDto {
  return {
    type: 'bounded-context',
    id: 'credit-decision-management',
    title: 'Credit Decision Management',
    subtitle: '',
    overviewSections: [
      {
        title: 'Resolved ownership',
        fields: {
          situationType: 'bounded-context-boundary',
          primaryOwners: [
            {
              targetType: 'bounded-context',
              targetId: 'credit-decision-management',
              targetLabel: 'Credit Decision Management',
              ownerTeamIds: [],
              ownerLabel: 'Credit Decision Domain Owner',
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
            'request.boundedContextIds -> credit-decision-management',
            'bounded-context:customer-consent-management -> inferred owner'
          ],
          visibilityLimits: [
            'Bounded context credit-decision-management has no explicit owner.'
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
