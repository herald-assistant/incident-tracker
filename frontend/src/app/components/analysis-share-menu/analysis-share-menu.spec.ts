import { TestBed } from '@angular/core/testing';

import { AnalysisShareMenuComponent } from './analysis-share-menu';

describe('AnalysisShareMenuComponent', () => {
  const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');

  afterEach(() => {
    if (originalClipboard) Object.defineProperty(navigator, 'clipboard', originalClipboard);
    else Reflect.deleteProperty(navigator, 'clipboard');
    vi.restoreAllMocks();
  });

  it('offers four ordered Markdown actions and copies the selected variant', async () => {
    const writeText = vi.fn(async (_value: string) => undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    await TestBed.configureTestingModule({ imports: [AnalysisShareMenuComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisShareMenuComponent);
    fixture.componentRef.setInput('document', {
      fileName: 'crm.md', markdown: '# CRM', markdownWithMeta: '# CRM\n\n## Meta'
    });
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector('button[aria-label="Udostępnij wynik"]') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    const items = Array.from(document.querySelectorAll<HTMLButtonElement>(
      '.analysis-share-menu-panel .mat-mdc-menu-item'
    ));
    expect(items.map((item) => item.textContent?.trim().replace(/\s+/g, ' '))).toEqual([
      'content_copyMarkdown', 'content_copyMarkdown + Meta',
      'downloadMarkdown', 'downloadMarkdown + Meta'
    ]);
    items[0].click();
    await fixture.whenStable();
    expect(writeText).toHaveBeenLastCalledWith('# CRM');

    trigger.click();
    fixture.detectChanges();
    document.querySelectorAll<HTMLButtonElement>('.analysis-share-menu-panel .mat-mdc-menu-item')[1].click();
    await fixture.whenStable();
    expect(writeText).toHaveBeenLastCalledWith('# CRM\n\n## Meta');
  });

  it('downloads the matching content and filename', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisShareMenuComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisShareMenuComponent);
    fixture.componentRef.setInput('document', {
      fileName: 'crm.md', markdown: '# CRM', markdownWithMeta: '# CRM\n\n## Meta'
    });
    fixture.detectChanges();
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:crm');
    const fileNames: string[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      fileNames.push(this.download);
    });
    const share = fixture.componentInstance as unknown as { download: (includeMeta: boolean) => void };
    share.download(false);
    share.download(true);

    expect(createObjectURL).toHaveBeenCalledTimes(2);
    expect((createObjectURL.mock.calls[0][0] as Blob).type).toBe('text/markdown;charset=utf-8');
    expect((createObjectURL.mock.calls[1][0] as Blob).size).toBeGreaterThan((createObjectURL.mock.calls[0][0] as Blob).size);
    expect(click).toHaveBeenCalledTimes(2);
    expect(fileNames).toEqual(['crm.md', 'crm-meta.md']);
  });
});
