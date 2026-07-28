import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ConfluenceSourceConsoleComponent } from './confluence-source-console';

describe('ConfluenceSourceConsoleComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfluenceSourceConsoleComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should fetch and render Confluence page content', () => {
    const fixture = TestBed.createComponent(ConfluenceSourceConsoleComponent);
    const pageUrl = 'https://confluence.example.com/pages/viewpage.action?pageId=123';

    fixture.componentInstance.scopeForm.setValue({ pageUrl });
    fixture.componentInstance.submit(new Event('submit'));

    const request = http.expectOne('/api/confluence/page/content');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ pageUrl });
    request.flush({
      pageId: '123',
      title: 'CRM customer profile',
      url: pageUrl,
      content: 'Customer profile description.',
      version: '7',
      limitations: []
    });

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('CRM customer profile');
    expect(compiled.textContent).toContain('pageId 123');
    expect(compiled.textContent).toContain('version 7');
    expect(compiled.textContent).toContain('29 chars');
    expect(compiled.textContent).toContain('0 limitations');
    expect(compiled.querySelector<HTMLTextAreaElement>('#confluenceJsonResponse')?.value).toContain(
      '"pageId": "123"'
    );
  });

  it('should expose adapter limitations returned with a successful HTTP response', () => {
    const fixture = TestBed.createComponent(ConfluenceSourceConsoleComponent);
    const pageUrl = 'https://confluence.example.com/pages/123';

    fixture.componentInstance.scopeForm.setValue({ pageUrl });
    fixture.componentInstance.submit(new Event('submit'));
    http.expectOne('/api/confluence/page/content').flush({
      pageId: '123',
      title: '',
      url: pageUrl,
      content: '',
      version: '',
      limitations: ['Confluence page fetch failed: ResourceAccessException.']
    });

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('1 ograniczeniem/ograniczeniami');
    expect(compiled.textContent).toContain('1 limitations');
    expect(compiled.querySelector<HTMLTextAreaElement>('#confluenceJsonResponse')?.value).toContain(
      'ResourceAccessException'
    );
  });
});
