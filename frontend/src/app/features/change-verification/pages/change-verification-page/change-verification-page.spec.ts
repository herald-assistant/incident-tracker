import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';
import { NEVER, of } from 'rxjs';
import { AiOptionsApiService } from '../../../../core/services/ai-options-api.service';
import { AnalysisJobPollingService } from '../../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import { ChangeVerificationApiService } from '../../services/change-verification-api.service';
import { ChangeVerificationPageComponent } from './change-verification-page';

describe('ChangeVerificationPageComponent form controls', () => {
  it('keeps model-dependent effort and sends the selected values with text input', async () => {
    const startJob = vi.fn().mockReturnValue(NEVER);
    await TestBed.configureTestingModule({
      imports: [ChangeVerificationPageComponent],
      providers: [
        provideRouter([]),
        { provide: ChangeVerificationApiService, useValue: { startJob } },
        { provide: AnalysisJobPollingService, useValue: {} },
        { provide: AnalysisRunHistoryApiService, useValue: {} },
        { provide: AiOptionsApiService, useValue: { getOptions: () => of({
          defaultModel: 'crm-analysis', defaultReasoningEffort: 'low', defaultReasoningEfforts: ['low', 'high'],
          models: [
            { id: 'crm-analysis', name: 'CRM Analysis', supportsReasoningEffort: true,
              reasoningEfforts: ['low', 'high'], defaultReasoningEffort: 'low' },
            { id: 'crm-auto', name: 'CRM Auto', supportsReasoningEffort: false,
              reasoningEfforts: [], defaultReasoningEffort: '' }
          ]
        }) } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(ChangeVerificationPageComponent);
    fixture.detectChanges();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const model = await loader.getHarness(MatSelectHarness.with({ selector: '#changeVerificationAiModel' }));
    const effort = await loader.getHarness(MatSelectHarness.with({ selector: '#changeVerificationReasoningEffort' }));
    await model.open();
    await model.clickOptions({ text: /CRM Auto/ });
    expect(await effort.isDisabled()).toBe(true);
    expect(fixture.componentInstance.selectedReasoningEffort()).toBe('');
    await model.open();
    await model.clickOptions({ text: /CRM Analysis/ });
    expect(await effort.isDisabled()).toBe(false);
    await effort.open();
    await effort.clickOptions({ text: 'Wysoki' });
    expect(fixture.componentInstance.aiModelControl.dirty).toBe(true);
    expect(fixture.componentInstance.aiModelControl.touched).toBe(true);
    expect(fixture.componentInstance.reasoningEffortControl.dirty).toBe(true);
    expect(fixture.componentInstance.reasoningEffortControl.touched).toBe(true);
    const issue = fixture.nativeElement.querySelector('app-text-field[label="Jira key or link"] input') as HTMLInputElement;
    issue.value = ' CRM-123 ';
    issue.dispatchEvent(new Event('input', { bubbles: true }));
    const notes = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    notes.value = ' Sprawdź zapis kontaktu CRM. ';
    notes.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.primary-button').click();
    expect(startJob).toHaveBeenCalledWith({
      issueKey: 'CRM-123', checkStoryCompliance: true, checkInstructionCompliance: true,
      userInstructions: 'Sprawdź zapis kontaktu CRM.', model: 'crm-analysis', reasoningEffort: 'high'
    });
  });
});
