import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {AssistantConversationPicker} from './assistant-conversation-picker';

const run = (id: string, feature = 'system-assistant') => ({analysisId: id, feature, name: `Rozmowa ${id}`, status: 'COMPLETED',
  createdAt: '2026-11-21T10:00:00Z', updatedAt: '2026-11-21T10:00:00Z', completedAt: '2026-11-21T10:00:00Z'});

describe('AssistantConversationPicker', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({imports: [AssistantConversationPicker], providers: [provideHttpClient(), provideHttpClientTesting()]});
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lists only assistant sessions, newest first, searches names and emits an ID without reading transcripts', () => {
    const fixture = TestBed.createComponent(AssistantConversationPicker); fixture.detectChanges();
    http.expectOne('/api/analysis/runs').flush({runs: [run('old'), run('other', 'incident-analysis'), {...run('new'), updatedAt: '2026-11-21T11:00:00Z'}]});
    fixture.detectChanges(); const root = fixture.nativeElement as HTMLElement;
    expect([...root.querySelectorAll('.conversation-name')].map(el => el.textContent)).toEqual(['Rozmowa new', 'Rozmowa old']);
    fixture.componentInstance.search('OLD'); fixture.detectChanges();
    const selected = vi.fn(); fixture.componentInstance.selected.subscribe(selected);
    root.querySelector<HTMLButtonElement>('.conversation-row')!.click();
    expect(selected).toHaveBeenCalledWith('old'); http.expectNone('/api/system-assistant/conversations/old');
    fixture.componentRef.setInput('disabled', true); fixture.detectChanges();
    fixture.componentInstance.choose('new'); expect(selected).toHaveBeenCalledTimes(1); fixture.destroy();
  });

  it('bounds the visible list and resets paging after a search', () => {
    const fixture = TestBed.createComponent(AssistantConversationPicker); fixture.detectChanges();
    http.expectOne('/api/analysis/runs').flush({runs: Array.from({length: 80}, (_, i) => run(`id-${i}`))});
    fixture.detectChanges(); const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelectorAll('.conversation-row')).toHaveLength(50);
    root.querySelector<HTMLButtonElement>('.picker-more')!.click(); fixture.detectChanges();
    expect(root.querySelectorAll('.conversation-row')).toHaveLength(80);
    fixture.componentInstance.search('id-79'); fixture.detectChanges();
    expect(root.querySelectorAll('.conversation-row')).toHaveLength(1); expect(fixture.componentInstance.limit()).toBe(50); fixture.destroy();
  });

  it('allows retry and new conversation after a list error and displays selection errors', () => {
    const fixture = TestBed.createComponent(AssistantConversationPicker); fixture.detectChanges();
    http.expectOne('/api/analysis/runs').flush({}, {status: 503, statusText: 'Unavailable'}); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[role=alert]')!.textContent).toContain('Nie udało');
    root.querySelector<HTMLButtonElement>('[aria-label="Odśwież rozmowy"]')!.click();
    http.expectOne('/api/analysis/runs').flush({runs: []}); fixture.detectChanges();
    expect(root.querySelector('[role=alert]')).toBeNull();
    fixture.componentRef.setInput('selectionError', 'Rozmowa została usunięta.'); fixture.detectChanges();
    expect(root.querySelector('[role=alert]')!.textContent).toContain('usunięta');
    const start = vi.fn(); fixture.componentInstance.newConversation.subscribe(start);
    root.querySelector<HTMLButtonElement>('[aria-label="Nowa rozmowa"]')!.click(); expect(start).toHaveBeenCalledOnce(); fixture.destroy();
  });
});
