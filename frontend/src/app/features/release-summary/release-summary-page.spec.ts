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
import { AnalysisAiModelOptionsResponse } from '../../core/models/analysis.models';

import { releaseReport, releaseSnapshot } from './release-summary.testing';
import { parseReleaseSummaryImport, releaseSummaryExport } from './release-summary-import-export';
describe('ReleaseSummary workspace',()=>{
  let api: {applications: ReturnType<typeof vi.fn>; refs: ReturnType<typeof vi.fn>; start: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn>;
    chat:ReturnType<typeof vi.fn>; edit:ReturnType<typeof vi.fn>; export:ReturnType<typeof vi.fn>};
  const config=signal({releaseSummaryEnabled:true});
  let progress:Subject<ReleaseSummarySnapshot>;
  beforeEach(()=>{
    config.set({releaseSummaryEnabled:true});progress=new Subject();
    api={applications:vi.fn(()=>of([{systemId:'crm',name:'CRM Customers'}])),
      refs:vi.fn(()=>of({branches:[],warnings:[],truncated:false,nextPage:null})),
      start:vi.fn(()=>of(releaseSnapshot('QUEUED'))),get:vi.fn(()=>of(releaseSnapshot())),
      chat:vi.fn(()=>of(releaseSnapshot())),edit:vi.fn(()=>of(releaseSnapshot())),export:vi.fn(()=>of({}))};
    TestBed.configureTestingModule({imports:[ReleaseSummaryPageComponent],providers:[
      {provide:ReleaseSummaryApiService,useValue:api},{provide:GitLabSystemBranchesApiService,useValue:{}},
      {provide:AiOptionsApiService,useValue:{getOptions:()=>of({models:[],defaultModel:'',defaultReasoningEffort:'',defaultReasoningEfforts:[]})}},
      {provide:AppUiConfigService,useValue:{config,load:()=>{}}},
      {provide:AnalysisJobPollingService,useValue:{poll:()=>progress.asObservable()}},
      {provide:ActivatedRoute,useValue:{snapshot:{queryParamMap:convertToParamMap({})}}}
    ]});
  });
  it('shows commit dates without kind badges in both revision pickers',()=>{
    api.refs.mockReturnValue(of({branches:[{name:'refs/heads/release',label:'release',kind:'BRANCH',isDefault:false,
      committedAt:'2026-11-02T12:35:00Z'}],truncated:false,warnings:[],nextPage:null}));
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);fixture.detectChanges();
    fixture.componentInstance.selectSystem('crm');fixture.detectChanges();
    const pickers=fixture.nativeElement.querySelectorAll('app-gitlab-branch-select') as NodeListOf<HTMLElement>;
    expect(pickers.length).toBe(2);
    for(const picker of pickers) {
      picker.querySelector<HTMLButtonElement>('.branch-select__control')!.click();fixture.detectChanges();
      expect(picker.querySelector('.branch-select__commit-date')?.textContent?.trim()).toBe('02.11.2026');
      expect(picker.querySelector('.branch-select__option-badges')?.textContent).not.toContain('BRANCH');
    }
  });
  it('sends the comparison selected in the form and locks selection until the run finishes',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);fixture.detectChanges();
    const page=fixture.componentInstance;page.selectSystem('crm');
    page.before.set('refs/heads/release');fixture.detectChanges();
    const targetKind=fixture.nativeElement.querySelector('select[aria-label="Typ refa stanu docelowego"]');
    targetKind.value='TAG';targetKind.dispatchEvent(new Event('change'));
    page.after.set('refs/tags/release');page.selectModel('crm-model');fixture.detectChanges();
    fixture.nativeElement.querySelector('.release-summary-actions .primary-button').click();
    expect(api.start).toHaveBeenCalledWith({systemId:'crm',mode:'LIQUIBASE',baselineRef:{kind:'BRANCH',name:'release'},targetRef:{kind:'TAG',name:'release'},model:'crm-model'});
    page.selectSystem('crm-other');expect(page.systemId()).toBe('crm');
    progress.next(releaseSnapshot());page.selectSystem('crm-other');expect(page.before()).toBe('');expect(page.after()).toBe('');
  });
  it('requires an effective model before allowing a run',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);const page=fixture.componentInstance;
    page.selectSystem('crm');page.before.set('refs/heads/current');page.after.set('refs/heads/next');fixture.detectChanges();
    expect(page.canStart()).toBe(false);page.start();expect(api.start).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Wybierz model AI, aby wygenerować raport');
    page.modelCatalog.set({models:[],defaultModel:'crm-default',defaultReasoningEffort:'',defaultReasoningEfforts:[]});
    expect(page.canStart()).toBe(true);
  });
  it.each(['QUEUED','RUNNING'])('resumes live progress from a %s history entry without starting another run',(status)=>{
    TestBed.overrideProvider(ActivatedRoute,{useValue:{snapshot:{queryParamMap:convertToParamMap({localRunId:'crm-run'})}}});
    api.get.mockReturnValue(of(releaseSnapshot(status)));
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);fixture.detectChanges();
    expect(api.get).toHaveBeenCalledWith('crm-run');
    expect(fixture.componentInstance.busy()).toBe(true);
    const completed=releaseSnapshot('NO_CHANGES');progress.next(completed);fixture.detectChanges();
    expect(fixture.componentInstance.job()).toEqual(completed);
    expect(fixture.componentInstance.busy()).toBe(false);
    expect(api.start).not.toHaveBeenCalled();
  });
  it('animates the AI badge only during an active AI step, independently of preparation and saving',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);const page=fixture.componentInstance;
    const job=releaseSnapshot('RUNNING');page.job.set(job);
    const badge=(label:string)=>fixture.nativeElement.querySelector(`button[aria-label="${label}"] .analysis-feature-aside__badge--loading`);
    for(const code of ['RESOLVE_REFS','COLLECT_DIFF','PREPARE_PROMPT','AI_REPORT']) {
      page.job.set({...job,steps:[{code,label:code,phase:code==='AI_REPORT'?'AI':'CONTEXT',status:'RUNNING',
        message:'',itemCount:0,startedAt:'',completedAt:''}]});fixture.detectChanges();
      expect(!!badge('Tok działania AI')).toBe(code==='AI_REPORT');
      expect(badge('Przebieg analizy')).not.toBeNull();
    }
    page.job.set(releaseSnapshot());page.saving.set(true);fixture.detectChanges();
    expect(badge('Tok działania AI')).toBeNull();
    page.saving.set(false);fixture.detectChanges();expect(badge('Tok działania AI')).toBeNull();
  });
  it('animates the chat badge while submitting and waiting for a response without animating the initial AI badge',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);const page=fixture.componentInstance;
    const job=releaseSnapshot();page.job.set(job);page.chatSubmitting.set(true);fixture.detectChanges();
    const badge=(label:string)=>fixture.nativeElement.querySelector(`button[aria-label="${label}"] .analysis-feature-aside__badge--loading`);
    expect(badge('Follow-up chat')).not.toBeNull();expect(badge('Tok działania AI')).toBeNull();
    page.chatSubmitting.set(false);
    page.job.set({...job,chatMessages:[{id:'crm-answer',role:'ASSISTANT',status:'IN_PROGRESS',content:'',
      errorCode:'',errorMessage:'',createdAt:'',updatedAt:'',completedAt:'',toolEvidenceSections:[],
      aiActivityEvents:[],toolFeedback:[],prompt:''}]});fixture.detectChanges();
    expect(badge('Follow-up chat')).not.toBeNull();expect(badge('Tok działania AI')).toBeNull();
    page.job.set(job);fixture.detectChanges();expect(badge('Follow-up chat')).toBeNull();
  });
  it('defaults questions to read-only and requires an explicit report update choice',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);const page=fixture.componentInstance;
    const job=releaseSnapshot();job.chatAvailability={available:true,code:'AVAILABLE',message:''};page.job.set(job);
    page.sendChat('Wyjaśnij kolumnę');expect(api.chat).toHaveBeenLastCalledWith('crm-run','Wyjaśnij kolumnę',false);
    page.job.set(job);page.updateReport.set(true);page.sendChat('Popraw opis');
    expect(api.chat).toHaveBeenLastCalledWith('crm-run','Popraw opis',true);expect(page.updateReport()).toBe(false);
  });
  it('displays the restored model and reasoning when the catalog arrives later',async()=>{
    const catalog=new Subject<AnalysisAiModelOptionsResponse>();
    TestBed.overrideProvider(AiOptionsApiService,{useValue:{getOptions:()=>catalog.asObservable()}});
    TestBed.overrideProvider(ActivatedRoute,{useValue:{snapshot:{queryParamMap:convertToParamMap({localRunId:'crm-run'})}}});
    const restored=releaseSnapshot();restored.request.model='crm-model';restored.request.reasoningEffort='high';
    api.get.mockReturnValue(of(restored));
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);fixture.detectChanges();
    catalog.next({defaultModel:'',defaultReasoningEffort:'',defaultReasoningEfforts:[],models:[
      {id:'crm-model',name:'CRM model',supportsReasoningEffort:true,reasoningEfforts:['low','high'],defaultReasoningEffort:'low'}]});
    fixture.detectChanges();await fixture.whenStable();fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('select[aria-label="Model AI"]').value).toBe('crm-model');
    expect(fixture.nativeElement.querySelector('select[aria-label="Reasoning effort"]').value).toBe('high');
  });
  it('keeps imported files read-only even if the exported availability allowed continuation',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);const page=fixture.componentInstance;
    const job=releaseSnapshot();job.chatAvailability={available:true,code:'AVAILABLE',message:''};
    job.report=releaseReport();
    page.job.set(parseReleaseSummaryImport(releaseSummaryExport(job)));page.imported.set(true);fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-analysis-result-header h3').textContent).toBe('Zmiany bazodanowe między current a next');
    expect(fixture.nativeElement.querySelector('.release-summary-result').firstElementChild.tagName).toBe('APP-ANALYSIS-RESULT-HEADER');
    page.sendChat('Pytanie');page.saveReport({expectedRevisionSha256:'old',markdownSummary:'Zmiana',sections:[]});
    expect(api.chat).not.toHaveBeenCalled();expect(api.edit).not.toHaveBeenCalled();expect(page.canEdit()).toBe(false);
  });
  it('renders a partial report directly under one header using the saved comparison',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);
    const job=releaseSnapshot('PARTIAL');job.report=releaseReport();
    fixture.componentInstance.job.set(job);fixture.detectChanges();
    const result=fixture.nativeElement.querySelector('.release-summary-result');
    expect(result.firstElementChild.tagName).toBe('APP-ANALYSIS-RESULT-HEADER');
    expect(result.querySelectorAll('h3')).toHaveLength(1);
    expect(result.querySelector('h3').textContent).toBe('Zmiany bazodanowe między current a next');
    expect(result.querySelector('app-analysis-result-tabs')).toBeNull();
    expect(result.querySelector('[role="tablist"]')).toBeNull();
    expect(result.querySelector('.analysis-report-panel__details')).toBeNull();
    expect(result.querySelector('.status-pill')).toBeNull();
    expect(result.querySelector('.release-direction')).toBeNull();
    expect(result.textContent).not.toContain('Kompletność i źródła');
    expect(result.textContent).not.toContain('Źródła przypięte');
    expect(fixture.nativeElement.querySelector('app-analysis-report-panel table')).not.toBeNull();
    expect([...fixture.nativeElement.querySelectorAll('.analysis-report-panel__section h4')].map((node:any)=>node.textContent.trim()))
      .toEqual(['Podsumowanie','Lista tabel','customer']);
    expect([...fixture.nativeElement.querySelectorAll('#report-section-DB_DETAIL_customer th')].map((node:any)=>node.textContent.trim()))
      .toEqual(['Obiekt','Zmiana','Opis funkcjonalny']);
    expect(fixture.nativeElement.textContent).toContain('typ: varchar(40); NOT NULL; wartość domyślna: general');
    expect(fixture.nativeElement.querySelector('.analysis-report-appendix').open).toBe(false);
    expect(fixture.componentInstance.shareDocument()?.markdown).not.toContain('https://');
    expect(fixture.componentInstance.shareDocument()?.markdownWithMeta).toContain('https://gitlab.example.com');
    expect(fixture.nativeElement.textContent).not.toContain('OVERVIEW');
    expect(fixture.nativeElement.textContent).toContain('Punkt wejścia');
    expect(fixture.nativeElement.textContent).toContain('Stan docelowy');
    expect(result.querySelector('[role="img"]').getAttribute('aria-label')).toContain('Raport częściowy');
    expect(fixture.nativeElement.querySelector('app-analysis-feature-aside')).not.toBeNull();
    fixture.componentInstance.before.set('refs/heads/other-current');fixture.componentInstance.after.set('refs/heads/other-next');fixture.detectChanges();
    expect(result.querySelector('h3').textContent).toBe('Zmiany bazodanowe między current a next');
    expect(fixture.componentInstance.shareDocument()?.markdown).toMatch(/^# Zmiany bazodanowe między current a next\n/);
    expect(job.report.header).toBe('Zmiany bazodanowe we wdrożeniu');
  });
  it('shows a blocked result without claiming an empty release',()=>{
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);
    const job=releaseSnapshot('BLOCKED');job.error='Brak refa w repozytorium CRM';
    fixture.componentInstance.job.set(job);fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.release-summary-result [role="alert"]').textContent).toContain('Brak refa');
    expect(fixture.nativeElement.textContent).not.toContain('Brak zmian Liquibase');
    expect(fixture.nativeElement.querySelector('app-analysis-report-panel')).toBeNull();
    expect(fixture.componentInstance.shareDocument()).toBeNull();
  });
  it('respects disabled starts while keeping saved results visible',()=>{
    config.set({releaseSummaryEnabled:false});
    const fixture=TestBed.createComponent(ReleaseSummaryPageComponent);
    const job=releaseSnapshot('NO_CHANGES');job.report=releaseReport();
    job.report.sections=[{...job.report.sections.find(section=>section.id==='OVERVIEW')!,markdown:'Brak zmian bazodanowych.'}];
    fixture.componentInstance.job.set(job);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nowe analizy Release summary są wyłączone');
    expect(fixture.nativeElement.textContent).toContain('Brak zmian bazodanowych.');
    expect(fixture.nativeElement.querySelector('app-application-select')).toBeNull();
    fixture.componentInstance.start();expect(api.start).not.toHaveBeenCalled();
  });
});

