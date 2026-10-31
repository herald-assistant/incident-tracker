import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, Subject } from 'rxjs';
import { vi } from 'vitest';
import { ReleaseSummaryPageComponent } from './release-summary-page';
import { ReleaseSummaryApiService } from './release-summary-api.service';
import { ReleaseSummarySnapshot } from './release-summary.models';
import { GitLabSystemBranchesApiService } from '../../core/services/gitlab-system-branches-api.service';
import { AiOptionsApiService } from '../../core/services/ai-options-api.service';
import { AppUiConfigService } from '../../core/services/app-ui-config.service';
import { AnalysisJobPollingService } from '../../core/services/analysis-job-polling.service';

export function releaseSnapshot(status = 'COMPLETED'): ReleaseSummarySnapshot {
  return {jobId:'crm-run',status,request:{systemId:'crm',mode:'LIQUIBASE',baselineRef:{kind:'BRANCH',name:'current'},targetRef:{kind:'TAG',name:'next'}},
    comparison:null,evidence:null,errorCode:null,error:null,createdAt:'2026-10-31T10:00:00Z',updatedAt:'2026-10-31T10:00:00Z',completedAt:null,
    steps:[],contextSections:[],toolEvidenceSections:[],aiActivityEvents:[],toolFeedback:[],preparedPrompt:'CRM full prompt',report:null,result:null,usage:null,chatMessages:[],
    chatAvailability:{available:false,code:'UNAVAILABLE',message:'Brak sesji'},exportAvailable:true};
}
describe('ReleaseSummary workspace',()=>{
  let api: {applications: ReturnType<typeof vi.fn>; refs: ReturnType<typeof vi.fn>; start: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn>};
  const config=signal({releaseSummaryEnabled:true});
  let progress:Subject<ReleaseSummarySnapshot>;
  beforeEach(()=>{
    config.set({releaseSummaryEnabled:true});progress=new Subject();
    api={applications:vi.fn(()=>of([{systemId:'crm',name:'CRM Customers'}])),
      refs:vi.fn(()=>of({branches:[],warnings:[],truncated:false,nextPage:null})),
      start:vi.fn(()=>of(releaseSnapshot('QUEUED'))),get:vi.fn(()=>of(releaseSnapshot()))};
    TestBed.configureTestingModule({imports:[ReleaseSummaryPageComponent],providers:[
      {provide:ReleaseSummaryApiService,useValue:api},{provide:GitLabSystemBranchesApiService,useValue:{}},
      {provide:AiOptionsApiService,useValue:{getOptions:()=>of({models:[],defaultModel:'',defaultReasoningEffort:'',defaultReasoningEfforts:[]})}},
      {provide:AppUiConfigService,useValue:{config,load:()=>{}}},
      {provide:AnalysisJobPollingService,useValue:{poll:()=>progress.asObservable()}},
      {provide:ActivatedRoute,useValue:{snapshot:{queryParamMap:convertToParamMap({})}}}
    ]});
  });
  it('sends typed comparison and locks selection until the run finishes',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);fixture.detectChanges();
    const page=fixture.componentInstance;page.selectSystem('crm');
    page.before.set('refs/heads/release');page.after.set('refs/tags/release');page.start();
    expect(api.start).toHaveBeenCalledWith({systemId:'crm',mode:'LIQUIBASE',baselineRef:{kind:'BRANCH',name:'release'},targetRef:{kind:'TAG',name:'release'}});
    page.selectSystem('crm-other');expect(page.systemId()).toBe('crm');
    progress.next(releaseSnapshot());page.selectSystem('crm-other');expect(page.before()).toBe('');expect(page.after()).toBe('');
  });
  it('swaps both values and their ref kinds',()=>{
    const page=TestBed.createComponent(ReleaseSummaryPageComponent).componentInstance;
    page.before.set('refs/heads/current');page.after.set('refs/tags/next');page.afterKind.set('TAG');page.swap();
    expect(page.before()).toBe('refs/tags/next');expect(page.beforeKind()).toBe('TAG');expect(page.afterKind()).toBe('BRANCH');
  });
  it('renders partial report through the shared renderer and exposes coverage',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);
    const job=releaseSnapshot('PARTIAL');job.report={reportId:'report',header:'CRM release',subHeader:'CRM',
      markdownSummary:'Zmiany',meta:{references:[],visibilityLimits:[],openQuestions:[],gaps:[],confidence:"",warnings:[]},
      sections:[{id:'OVERVIEW',title:'CRM',order:0,markdown:'| Kolumna | Cel |\n| --- | --- |\n| label | Wyszukiwanie klienta |',
        meta:{references:[],visibilityLimits:[],openQuestions:[],gaps:[],confidence:"",warnings:[]}}]};
    fixture.componentInstance.job.set(job);fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-analysis-report-panel table')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Raport częściowy');
    expect(fixture.nativeElement.querySelector('app-analysis-feature-aside')).not.toBeNull();
    fixture.componentInstance.activeTab.set('coverage');fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Źródła przypięte');
  });
  it('shows a blocked result without claiming an empty release',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);
    const job=releaseSnapshot('BLOCKED');job.error='Brak refa w repozytorium CRM';
    fixture.componentInstance.job.set(job);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Przygotowanie zablokowane');
    expect(fixture.nativeElement.textContent).toContain('Brak refa');
    expect(fixture.nativeElement.textContent).not.toContain('Brak zmian Liquibase');
  });
  it('respects disabled starts while keeping saved results visible',()=>{
    config.set({releaseSummaryEnabled:false});
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);
    fixture.componentInstance.job.set(releaseSnapshot('NO_CHANGES'));fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nowe analizy Release summary są wyłączone');
    expect(fixture.nativeElement.textContent).toContain('Brak zmian Liquibase');
    expect(fixture.nativeElement.querySelector('app-application-select')).toBeNull();
    fixture.componentInstance.start();expect(api.start).not.toHaveBeenCalled();
  });
});

