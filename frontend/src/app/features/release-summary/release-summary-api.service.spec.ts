import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { releaseRange } from './release-summary.testing';
import { ReleaseSummaryApiService } from './release-summary-api.service';
describe('ReleaseSummaryApiService',()=>{
  let http:HttpTestingController;let api:ReleaseSummaryApiService;
  beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);api=TestBed.inject(ReleaseSummaryApiService);});
  afterEach(()=>http.verify());
  it('lists typed paginated refs with an encoded application identifier',()=>{
    api.branches('crm/customer','release/next',2).subscribe();
    const request=http.expectOne(r=>r.url==='/api/release-summary/applications/crm%2Fcustomer/branches');
    expect(request.request.params.get('search')).toBe('release/next');
    expect(request.request.params.get('page')).toBe('2');
    request.flush({branches:[],nextPage:null,warnings:[],truncated:false});
  });
  it('previews dates in the browser time zone with an encoded system id',()=>{
    api.preview('crm/customer',releaseRange().selection).subscribe();
    const request=http.expectOne('/api/release-summary/applications/crm%2Fcustomer/range-preview');
    expect(request.request.method).toBe('POST');expect(request.request.body).toEqual(releaseRange().selection);
    request.flush(releaseRange());
  });
  it('posts only the current Liquibase contract',()=>{
    const payload={systemId:'crm',mode:'LIQUIBASE' as const,range:releaseRange()};
    api.start(payload).subscribe();const request=http.expectOne('/api/release-summary/jobs');
    expect(request.request.method).toBe('POST');expect(request.request.body).toEqual(payload);request.flush({});
  });
});

