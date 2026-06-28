import { TestBed } from '@angular/core/testing';

import { AnalysisChatMessageResponse } from '../../core/models/analysis.models';
import {
  AnalysisFollowUpChatComponent,
  AnalysisResultUpdateReviewRequest
} from './analysis-follow-up-chat';

describe('AnalysisFollowUpChatComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisFollowUpChatComponent]
    }).compileComponents();
  });

  it('should not render result update review actions by default', () => {
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    fixture.componentRef.setInput('messages', [
      chatMessage({ resultUpdate: { confidence: 'medium' } })
    ]);

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.chat-message__review-button')).toBeNull();
  });

  it('should emit review changes for completed assistant messages with result update', () => {
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    const resultUpdate = { confidence: 'medium', sections: [] };
    const emitted: AnalysisResultUpdateReviewRequest[] = [];
    const subscription = fixture.componentInstance.reviewResultUpdate.subscribe((event) =>
      emitted.push(event)
    );
    fixture.componentRef.setInput('canReviewResultUpdates', true);
    fixture.componentRef.setInput('messages', [
      chatMessage({ id: 'assistant-1', resultUpdate })
    ]);

    fixture.detectChanges();
    const reviewButton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '.chat-message__review-button'
    );
    reviewButton?.click();

    expect(reviewButton?.textContent).toContain('Review changes');
    expect(emitted).toEqual([{ messageId: 'assistant-1', resultUpdate }]);
    subscription.unsubscribe();
  });

  it('should only show review changes for completed assistant messages with result update', () => {
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    fixture.componentRef.setInput('canReviewResultUpdates', true);
    fixture.componentRef.setInput('messages', [
      chatMessage({ id: 'user-1', role: 'USER', resultUpdate: { confidence: 'low' } }),
      chatMessage({ id: 'assistant-1', status: 'IN_PROGRESS', resultUpdate: { confidence: 'low' } }),
      chatMessage({ id: 'assistant-2', status: 'COMPLETED' })
    ]);

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.chat-message__review-button')).toHaveLength(0);
  });
});

function chatMessage(
  overrides: Partial<AnalysisChatMessageResponse> = {}
): AnalysisChatMessageResponse {
  return {
    id: 'assistant-1',
    role: 'ASSISTANT',
    status: 'COMPLETED',
    content: 'Updated the result.',
    errorCode: '',
    errorMessage: '',
    createdAt: '2026-06-18T10:00:00Z',
    updatedAt: '2026-06-18T10:00:01Z',
    completedAt: '2026-06-18T10:00:01Z',
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    prompt: '',
    ...overrides
  };
}
