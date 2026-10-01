import { TestBed } from '@angular/core/testing';
import { AnalysisAiInvocationsComponent } from './analysis-ai-invocations';
import { invocationFixture } from '../../core/testing/analysis-ai-invocation.fixture';

describe('AnalysisAiInvocationsComponent', () => {
  it('should label multipart execution and retain failed raw response in collapsed diagnostics', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisAiInvocationsComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisAiInvocationsComponent);
    const part = invocationFixture('CRM prompt', '{"coverage":[]}');
    part.role = 'EVIDENCE_PART';
    part.partCount = 2;
    part.status = 'FAILED';
    part.errorMessage = 'Invalid findings';
    fixture.componentRef.setInput('invocations', [part]);
    fixture.componentRef.setInput('summaryOnly', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Analiza w 2 częściach');
    fixture.componentRef.setInput('summaryOnly', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Błąd');
    expect(fixture.nativeElement.textContent).toContain('Invalid findings');
    const raw = fixture.nativeElement.querySelector('.unit-raw-response') as HTMLDetailsElement;
    expect(raw.open).toBe(false);
    expect(raw.querySelector('pre')?.textContent).toBe('{"coverage":[]}');
  });

  it('should not label a normal single assessment as multipart', () => {
    const fixture = TestBed.createComponent(AnalysisAiInvocationsComponent);
    fixture.componentRef.setInput('invocations', [invocationFixture()]);
    fixture.componentRef.setInput('summaryOnly', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.analysis-parts-label')).toBeNull();
  });
});
