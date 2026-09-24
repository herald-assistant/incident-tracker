import { TestBed } from '@angular/core/testing';
import { AnalysisChatMessageResponse } from '../../core/models/analysis.models';
import { AnalysisFollowUpChatComponent } from './analysis-follow-up-chat';

describe('AnalysisFollowUpChatComponent report changes', () => {
  it('opens a raw before/after preview from the assistant reference chip', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisFollowUpChatComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    fixture.componentRef.setInput('messages', [assistantMessage(true)]);
    fixture.componentRef.setInput('openByDefault', true);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const dialog = root.querySelector<HTMLDialogElement>('.report-change-dialog')!;
    const showModal = vi.fn();
    dialog.showModal = showModal;
    const chips = root.querySelectorAll<HTMLButtonElement>('.chat-evidence-section__report-chip');
    const chip = chips[0]!;

    expect(chips.length).toBe(1);
    expect(chip.textContent).toContain('Pokaż wszystkie zmiany raportu');
    expect(chip.closest('.chat-evidence-section')?.querySelector('h4')).toBeNull();
    chip.click();
    fixture.detectChanges();

    expect(showModal).toHaveBeenCalledOnce();
    expect(dialog.querySelectorAll('.report-change-dialog__group').length).toBe(2);
    expect(dialog.querySelectorAll('.report-change-dialog__group h3')[0]?.textContent).toBe('Dane klienta');
    expect(dialog.querySelectorAll('.report-change-dialog__group h3')[1]?.textContent).toBe('Reguły CRM');
    expect(dialog.querySelectorAll('.report-change-dialog__part').length).toBe(3);
    expect(dialog.querySelector('.report-change-dialog__diff-line.is-removed')?.textContent).toContain('**Stary opis**');
    expect(dialog.querySelector('.report-change-dialog__diff-line.is-added')?.textContent).toContain('**Nowy opis**');
    expect(dialog.querySelector('app-markdown-content')).toBeNull();
    expect(dialog.querySelector('pre')).toBeNull();
    expect(dialog.querySelector('.report-change-dialog__mode-switch')).toBeNull();
    expect(dialog.querySelector<HTMLButtonElement>('.report-change-dialog__close')?.getAttribute('aria-label'))
      .toBe('Zamknij podgląd zmian raportu');
  });

  it('does not show a report change chip for an ordinary answer', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisFollowUpChatComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    fixture.componentRef.setInput('messages', [assistantMessage(false)]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.chat-evidence-section__report-chip')).toBeNull();
  });
});

function assistantMessage(withChange: boolean): AnalysisChatMessageResponse {
  return {
    id: 'crm-assistant-1', role: 'ASSISTANT', status: 'COMPLETED', content: 'Opis zaktualizowany.',
    errorCode: '', errorMessage: '', createdAt: '', updatedAt: '', completedAt: '',
    toolEvidenceSections: withChange ? [{ provider: 'report', category: 'report-change', items: [
      { title: 'Dane klienta', attributes: [
        { name: 'before', value: '**Stary opis**' }, { name: 'after', value: '**Nowy opis**' }
      ] },
      { title: 'Dane klienta — informacje dodatkowe', attributes: [
        { name: 'before', value: 'Pewność: niska' }, { name: 'after', value: 'Pewność: wysoka' }
      ] },
      { title: 'Reguły CRM', attributes: [
        { name: 'before', value: 'Stara reguła' }, { name: 'after', value: 'Nowa reguła' }
      ] }
    ] }] : [],
    aiActivityEvents: [], toolFeedback: [], prompt: ''
  };
}
