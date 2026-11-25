import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { AnalysisChatMessageResponse } from '../../core/models/analysis.models';
import { AnalysisFollowUpChatComponent } from './analysis-follow-up-chat';

@Component({ imports: [AnalysisFollowUpChatComponent], template: `
  <app-analysis-follow-up-chat [compact]="true" [canUseChat]="true" emptyText="" (sendMessage)="sent = $event">
    <button chatActions type="button" (click)="actions = actions + 1">Szczegóły</button>
  </app-analysis-follow-up-chat>` })
class CompactChatHost { sent = ''; actions = 0; }

describe('AnalysisFollowUpChatComponent attachment names', () => {
  it.each([false, true])('places file names above the matching messages and pending names in the composer (compact=%s)', async compact => {
    await TestBed.configureTestingModule({imports: [AnalysisFollowUpChatComponent]}).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    fixture.componentRef.setInput('compact', compact);
    fixture.componentRef.setInput('openByDefault', true);
    fixture.componentRef.setInput('messages', [
      {...assistantMessage(false), id: 'first', role: 'USER', content: 'Pierwsze pytanie'},
      {...assistantMessage(false), id: 'second', role: 'USER', content: 'Drugie pytanie'}
    ]);
    fixture.componentRef.setInput('messageAttachmentNames', {first: ['report.pdf', 'report.pdf'], second: ['picture.png']});
    fixture.componentRef.setInput('pendingAttachmentNames', ['next.docx']);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const rows = root.querySelectorAll('.chat-message');
    expect(rows[0].querySelectorAll('.chat-attachments__file')).toHaveLength(2);
    expect(rows[0].querySelector('.chat-attachments')?.textContent).toContain('report.pdf');
    expect(rows[1].querySelector('.chat-attachments')?.textContent).toContain('picture.png');
    expect(rows[1].querySelector('.chat-attachments')?.textContent).not.toContain('report.pdf');
    expect(rows[0].querySelector('.chat-attachments')!.compareDocumentPosition(rows[0].querySelector('.chat-message__content')!)
      & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(root.querySelector('form .chat-attachments')?.textContent).toContain('next.docx');
    expect(root.querySelector('.analysis-chat__messages')?.textContent).not.toContain('next.docx');
  });
});

describe('AnalysisFollowUpChatComponent agent progress', () => {
  it.each([false, true])('keeps one composer loader throughout a turn (compact=%s)', async (compact) => {
    await TestBed.configureTestingModule({imports: [AnalysisFollowUpChatComponent]}).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    fixture.componentRef.setInput('compact', compact);
    fixture.componentRef.setInput('canUseChat', true);
    fixture.componentRef.setInput('openByDefault', true);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const loaders = () => root.querySelectorAll('.analysis-chat__agent-loader');
    expect(loaders()).toHaveLength(0);

    fixture.componentRef.setInput('isSubmitting', true);
    fixture.detectChanges();
    expect(loaders()).toHaveLength(1);
    expect(loaders()[0].closest('form')).not.toBeNull();
    expect(loaders()[0].closest('.analysis-chat__messages')).toBeNull();
    expect(loaders()[0].getAttribute('role')).toBe('status');
    expect(loaders()[0].getAttribute('aria-label')).toBe('AI przygotowuje odpowiedź');

    const pending = {...assistantMessage(false), status: 'IN_PROGRESS' as const, content: ''};
    fixture.componentRef.setInput('messages', [pending]);
    fixture.detectChanges();
    expect(loaders()).toHaveLength(1);
    fixture.componentRef.setInput('isSubmitting', false);
    fixture.detectChanges();
    expect(loaders()).toHaveLength(1);
    expect(root.querySelector('textarea')?.disabled).toBe(true);
    expect(root.querySelector('.chat-message__actions')).toBeNull();

    for (const status of ['COMPLETED', 'FAILED'] as const) {
      fixture.componentRef.setInput('messages', [{...pending, status}]);
      fixture.detectChanges();
      expect(loaders()).toHaveLength(0);
      expect(root.querySelector('textarea')?.disabled).toBe(false);
      expect(root.querySelector('.chat-message__copy-button')).not.toBeNull();
    }
  });
});

describe('AnalysisFollowUpChatComponent compact composer', () => {
  it('projects auxiliary actions into the composer without submitting or losing a draft', async () => {
    await TestBed.configureTestingModule({imports: [CompactChatHost]}).compileComponents();
    const fixture = TestBed.createComponent(CompactChatHost); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const input = root.querySelector('textarea')!;
    input.value = 'Odczytaj klienta'; input.dispatchEvent(new Event('input', {bubbles: true})); fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('.analysis-chat__actions button[type=button]')!.click(); fixture.detectChanges();
    expect(fixture.componentInstance.actions).toBe(1);
    expect(fixture.componentInstance.sent).toBe(''); expect(input.value).toBe('Odczytaj klienta');
    expect(root.querySelector('summary')).toBeNull(); expect(root.querySelector('.empty-state')).toBeNull();
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true, cancelable: true})); fixture.detectChanges();
    expect(fixture.componentInstance.sent).toBe('Odczytaj klienta'); expect(input.value).toBe('');
  });

  it('keeps Shift+Enter and IME intact and respects request locks', async () => {
    await TestBed.configureTestingModule({imports: [AnalysisFollowUpChatComponent]}).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    fixture.componentRef.setInput('compact', true); fixture.componentRef.setInput('canUseChat', true);
    const send = vi.fn(); fixture.componentInstance.sendMessage.subscribe(send);
    fixture.componentInstance.messageText.set('Pytanie'); fixture.detectChanges();
    const input = (fixture.nativeElement as HTMLElement).querySelector('textarea')!;
    for (const options of [{shiftKey: true}, {isComposing: true}]) {
      const key = new KeyboardEvent('keydown', {key: 'Enter', bubbles: true, cancelable: true, ...options});
      input.dispatchEvent(key); expect(key.defaultPrevented).toBe(false);
    }
    fixture.componentRef.setInput('isSubmitting', true); fixture.detectChanges();
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true, cancelable: true}));
    expect(send).not.toHaveBeenCalled(); expect(fixture.componentInstance.messageText()).toBe('Pytanie');
    fixture.componentRef.setInput('isSubmitting', false); fixture.componentRef.setInput('canUseChat', false); fixture.detectChanges();
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true, cancelable: true}));
    expect(send).not.toHaveBeenCalled();
  });

  it('follows new messages only while the operator stays near the bottom', async () => {
    await TestBed.configureTestingModule({imports: [AnalysisFollowUpChatComponent]}).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFollowUpChatComponent);
    fixture.componentRef.setInput('compact', true); fixture.detectChanges();
    const list = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.analysis-chat__messages')!;
    Object.defineProperty(list, 'scrollHeight', {configurable: true, value: 1000});
    Object.defineProperty(list, 'clientHeight', {configurable: true, value: 200});
    fixture.componentRef.setInput('messages', [assistantMessage(false)]); fixture.detectChanges();
    expect(list.scrollTop).toBe(1000);
    list.scrollTop = 100; list.dispatchEvent(new Event('scroll'));
    fixture.componentRef.setInput('messages', [assistantMessage(true)]); fixture.detectChanges();
    expect(list.scrollTop).toBe(100);
    list.scrollTop = 800; list.dispatchEvent(new Event('scroll')); fixture.detectChanges();
    expect(list.scrollTop).toBe(1000);
  });
});

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
