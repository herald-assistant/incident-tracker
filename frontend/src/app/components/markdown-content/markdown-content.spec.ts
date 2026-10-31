import { TestBed } from '@angular/core/testing';
import { MarkdownContentComponent } from './markdown-content';

describe('MarkdownContentComponent fragment navigation', () => {
  it('opens a report section in the current document without navigating to the base route', () => {
    const fixture = TestBed.createComponent(MarkdownContentComponent);
    fixture.componentRef.setInput('content', '[Szczegóły](#report-section-crm)');
    fixture.detectChanges();
    const target = document.createElement('details');
    target.id = 'report-section-crm';
    const scroll = vi.fn();
    target.scrollIntoView = scroll;
    document.body.append(target);
    try {
      const click = new MouseEvent('click', { bubbles: true, cancelable: true });
      fixture.nativeElement.querySelector('a').dispatchEvent(click);
      expect(click.defaultPrevented).toBe(true);
      expect(target.open).toBe(true);
      expect(scroll).toHaveBeenCalled();
    } finally { target.remove(); fixture.destroy(); }
  });
});
