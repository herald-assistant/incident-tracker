import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ReleaseSummaryPageComponent } from './release-summary-page';
import { ReleaseSummaryApiService } from './release-summary-api.service';
import { GitLabSystemBranchesApiService } from '../../core/services/gitlab-system-branches-api.service';
describe('ReleaseSummary selection',()=>{
 const api={applications:vi.fn(()=>of([{systemId:'crm',name:'CRM Customers'}])),refs:vi.fn(()=>of({branches:[],warnings:[],truncated:false,nextPage:null})),start:vi.fn(()=>of({jobId:'run',status:'QUEUED'}))};
 beforeEach(()=>TestBed.configureTestingModule({imports:[ReleaseSummaryPageComponent],providers:[
  {provide:ReleaseSummaryApiService,useValue:api},{provide:GitLabSystemBranchesApiService,useValue:{}}
 ]}));
 it('resets both refs and sends typed comparison',()=>{
  const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);fixture.detectChanges();
  const page=fixture.componentInstance;page.selectSystem('crm');
  page.before.set('refs/heads/release');page.after.set('refs/tags/release');page.start();
  expect(api.start).toHaveBeenCalledWith({systemId:'crm',mode:'LIQUIBASE',baselineRef:{kind:'BRANCH',name:'release'},targetRef:{kind:'TAG',name:'release'}});
  page.selectSystem('crm-other');expect(page.before()).toBe('');expect(page.after()).toBe('');
 });
});

