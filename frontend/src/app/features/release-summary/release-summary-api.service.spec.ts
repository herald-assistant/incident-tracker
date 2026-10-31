import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReleaseSummaryApiService } from './release-summary-api.service';
describe('ReleaseSummaryApiService',()=>{
  let http:HttpTestingController;let api:ReleaseSummaryApiService;
  beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);api=TestBed.inject(ReleaseSummaryApiService);});
  afterEach(()=>http.verify());
  it('lists typed paginated refs with an encoded application identifier',()=>{
    api.refs('crm/customer','TAG','release/next',2).subscribe();
    const request=http.expectOne(r=>r.url==='/api/release-summary/applications/crm%2Fcustomer/refs');
    expect(request.request.params.get('kind')).toBe('TAG');
    expect(request.request.params.get('search')).toBe('release/next');
    expect(request.request.params.get('page')).toBe('2');
    request.flush({branches:[],nextPage:null,warnings:[],truncated:false});
  });
  it('posts only the current Liquibase contract',()=>{
    const payload={systemId:'crm',mode:'LIQUIBASE' as const,baselineRef:{kind:'BRANCH' as const,name:'current'},targetRef:{kind:'TAG' as const,name:'next'}};
    api.start(payload).subscribe();const request=http.expectOne('/api/release-summary/jobs');
    expect(request.request.method).toBe('POST');expect(request.request.body).toEqual(payload);request.flush({});
  });
});

