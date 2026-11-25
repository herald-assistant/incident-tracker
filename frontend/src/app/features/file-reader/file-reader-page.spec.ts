import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FileReaderPage } from './file-reader-page';

describe('FileReaderPage', () => {
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FileReaderPage], providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function select(component: FileReaderPage, files: File[]) {
    const input = document.createElement('input');
    input.type = 'file';
    Object.defineProperty(input, 'files', { value: files });
    component.selectFiles(input);
    expect(input.value).toBe('');
  }

  it('requires files and allows removing and selecting the same file again', () => {
    const fixture = TestBed.createComponent(FileReaderPage);
    fixture.detectChanges();
    const readButton = fixture.nativeElement.querySelector('.primary-button') as HTMLButtonElement;
    expect(readButton.disabled).toBe(true);
    const file = new File(['CRM'], 'CRM.png');
    select(fixture.componentInstance, [file]);
    select(fixture.componentInstance, [file]);
    expect(fixture.componentInstance.files().length).toBe(2);
    const id = fixture.componentInstance.files()[0].id;
    fixture.componentInstance.removeFile(id);
    expect(fixture.componentInstance.files().length).toBe(1);
    fixture.componentInstance.clear();
    expect(fixture.componentInstance.files()).toEqual([]);
  });

  it('reads files sequentially and presents images plus DOCX validation without AI requests', async () => {
    const fixture = TestBed.createComponent(FileReaderPage);
    const component = fixture.componentInstance;
    const png = new File(['CRM'], 'CRM.png', { type: 'image/png' });
    const docx = new File(['CRM'], 'CRM.docx');
    select(component, [png, docx]);
    const reading = component.readFiles();
    expect(component.busy()).toBe(true);
    component.clear();
    expect(component.files().length).toBe(2);
    const imageRequest = http.expectOne('/api/files/preview?name=CRM.png');
    expect(imageRequest.request.body).toBe(png);
    expect(imageRequest.request.headers.get('Content-Type')).toBe('application/octet-stream');
    http.expectNone('/api/files/preview?name=CRM.docx');
    imageRequest.flush({ name: 'CRM.png', mimeType: 'image/png', size: 3, status: 'READY',
      validationOnly: false, message: 'Obraz gotowy', images: [{ name: 'CRM.png', mimeType: 'image/png', size: 3, data: 'YWJj' }] });
    await Promise.resolve();
    const wordRequest = http.expectOne('/api/files/preview?name=CRM.docx');
    wordRequest.flush({ name: 'CRM.docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      size: 3, status: 'READY', validationOnly: true, message: 'DOCX można odczytać', images: [] });
    await reading;
    fixture.detectChanges();
    expect(component.busy()).toBe(false);
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.file-reader__image img').length).toBe(1);
    expect(element.querySelector('img')?.getAttribute('src')).toBe('data:image/png;base64,YWJj');
    expect(element.textContent).toContain('DOCX można odczytać');
    const imageResult = element.querySelector('.file-reader__result')!;
    expect(imageResult.querySelector('figcaption')).toBeNull();
    expect(imageResult.querySelector('h3')?.textContent).toBe('CRM.png');
    expect(imageResult.querySelector('.field-hint')?.textContent).toContain('image/png');
    expect(imageResult.querySelector('.field-hint')?.textContent).toContain('3 B');
    expect(imageResult.textContent?.match(/CRM\.png/g)?.length).toBe(1);
    expect(imageResult.textContent?.match(/3 B/g)?.length).toBe(1);
    expect(imageResult.textContent).not.toContain('Obraz gotowy');
    expect(element.querySelector('app-ai-execution-controls')).toBeNull();
    expect(component.requestJson()).not.toContain('YWJj');
    expect(component.responseJson()).toContain('YWJj');
    component.removeFile(component.files()[0].id);
    expect(component.results()).toEqual([]);
  });

  it('keeps a failed file visible and continues with the next file', async () => {
    const fixture = TestBed.createComponent(FileReaderPage);
    const component = fixture.componentInstance;
    select(component, [new File(['bad'], 'broken.docx'), new File(['png'], 'CRM.png')]);
    const reading = component.readFiles();
    http.expectOne('/api/files/preview?name=broken.docx').flush({
      name: 'broken.docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      size: 3, status: 'FAILED', validationOnly: true, message: 'Nie można odczytać pliku.', images: []
    }, { status: 422, statusText: 'Unprocessable Entity' });
    await Promise.resolve();
    http.expectOne('/api/files/preview?name=CRM.png').flush({
      name: 'CRM.png', mimeType: 'image/png', size: 3, status: 'READY', validationOnly: false,
      message: 'Obraz gotowy', images: []
    });
    await reading;
    fixture.detectChanges();
    expect(component.results().length).toBe(2);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Nie można odczytać');
    expect(fixture.nativeElement.textContent).toContain('Obraz gotowy');
    expect(component.busy()).toBe(false);
  });

  it('does not launch duplicate or later requests after destruction', async () => {
    const fixture = TestBed.createComponent(FileReaderPage);
    const component = fixture.componentInstance;
    select(component, [new File(['png'], 'CRM.png'), new File(['pdf'], 'CRM.pdf')]);
    const reading = component.readFiles();
    await component.readFiles();
    const request = http.expectOne('/api/files/preview?name=CRM.png');
    fixture.destroy();
    await reading;
    expect(request.cancelled).toBe(true);
    http.expectNone('/api/files/preview?name=CRM.pdf');
  });

  it('labels PDF pages by page number and keeps original metadata in one header', async () => {
    const fixture = TestBed.createComponent(FileReaderPage);
    const component = fixture.componentInstance;
    select(component, [new File(['pdf'], 'CRM.pdf')]);
    const reading = component.readFiles();
    http.expectOne('/api/files/preview?name=CRM.pdf').flush({
      name: 'CRM.pdf', mimeType: 'application/pdf', size: 100, status: 'READY', validationOnly: false,
      message: 'Przygotowano wszystkie strony PDF jako obrazy dla AI.',
      images: [1, 2].map(page => ({ name: `CRM.pdf — strona ${page}/2.jpg`, mimeType: 'image/jpeg', size: 30, data: 'YWJj' }))
    });
    await reading;
    fixture.detectChanges();
    const result = fixture.nativeElement.querySelector('.file-reader__result') as HTMLElement;
    expect(result.querySelectorAll('img').length).toBe(2);
    expect(Array.from(result.querySelectorAll('figcaption'), item => item.textContent?.trim())).toEqual([
      'Strona 1 / 2 · 30 B', 'Strona 2 / 2 · 30 B'
    ]);
    expect(result.querySelector('.field-hint')?.textContent).toContain('application/pdf · 100 B');
    expect(result.textContent?.match(/CRM\.pdf/g)?.length).toBe(1);
    expect(result.textContent).not.toContain('Przygotowano wszystkie strony');
  });
});
