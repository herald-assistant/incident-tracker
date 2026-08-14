import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';

import { AiSkillCatalogResponse, AiSkillDetailResponse } from '../../models/ai-skills.models';
import { AiSkillsApiService } from '../../services/ai-skills-api.service';
import { AiSkillsPageComponent } from './ai-skills-page';

describe('AiSkillsPageComponent', () => {
  afterEach(() => vi.restoreAllMocks());

  it('should render a compact searchable runtime catalog', async () => {
    const { fixture, api } = await createComponent();

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(api.getCatalog).toHaveBeenCalledTimes(1);
    expect(compiled.querySelectorAll('.ai-skills-row')).toHaveLength(4);
    expect(compiled.textContent).toContain('Effective runtime catalog');
    expect(compiled.textContent).toContain('incident-analysis-orchestrator');
    expect(compiled.textContent).toContain('Flow Explorer');
    expect(compiled.textContent).toContain('Delivery Effectiveness Assessment');

    fixture.componentInstance.searchControl.setValue('follow-up');
    fixture.detectChanges();

    expect(compiled.querySelectorAll('.ai-skills-row')).toHaveLength(1);
    expect(compiled.textContent).toContain('flow-explorer-follow-up-chat');
    expect(compiled.textContent).not.toContain('incident-analysis-orchestrator');
  });

  it('should combine workflow and responsibility filters', async () => {
    const { fixture } = await createComponent();

    fixture.componentInstance.setFamilyFilter('flow-explorer');
    fixture.componentInstance.responsibilityControl.setValue('Orchestration');
    fixture.detectChanges();

    const rows = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.ai-skills-row')
    );
    expect(rows).toHaveLength(1);
    expect(rows[0]?.textContent).toContain('flow-explorer-orchestrator');
  });

  it('should expose the Delivery Effectiveness Assessment evaluator as its own family', async () => {
    const { fixture } = await createComponent();

    fixture.componentInstance.setFamilyFilter('delivery-effectiveness-assessment');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const rows = Array.from(compiled.querySelectorAll<HTMLElement>('.ai-skills-row'));
    expect(rows).toHaveLength(1);
    expect(rows[0]?.textContent).toContain('delivery-effectiveness-assessment-evaluator');
    expect(rows[0]?.textContent).toContain('Delivery Effectiveness Assessment');
    expect(rows[0]?.textContent).toContain('Assessment');
  });

  it('should render a deep-linked skill and offer its exact raw source', async () => {
    const { fixture, api } = await createComponent('incident-analysis-orchestrator');

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(api.getSkill).toHaveBeenCalledWith('incident-analysis-orchestrator');
    expect(compiled.querySelector('.markdown-content')?.textContent).toContain('Runtime guidance');
    expect(compiled.querySelector('.ai-skills-raw')).toBeNull();

    fixture.componentInstance.toggleRawView();
    fixture.detectChanges();

    expect(compiled.querySelector('.markdown-content')).toBeNull();
    expect(compiled.querySelector('.ai-skills-raw')?.textContent).toContain(
      'name: incident-analysis-orchestrator'
    );
  });

  it('should edit and save one skill as CUSTOM', async () => {
    const { fixture, api } = await createComponent('incident-analysis-orchestrator');
    fixture.detectChanges();

    fixture.componentInstance.beginEdit();
    fixture.componentInstance.editorControl.setValue(
      detail('incident-analysis-orchestrator').rawMarkdown + '\n\nChanged.'
    );
    fixture.componentInstance.saveSkill();
    fixture.detectChanges();

    expect(api.updateSkill).toHaveBeenCalledWith('incident-analysis-orchestrator', {
      rawMarkdown: expect.stringContaining('Changed.')
    });
    expect(fixture.componentInstance.detail()?.state).toBe('CUSTOM');
    expect(fixture.nativeElement.textContent).toContain('Skill saved.');
  });

  it('should restore a custom skill after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const customDetail = { ...detail('incident-analysis-orchestrator'), state: 'CUSTOM' as const };
    const { fixture, api } = await createComponent(
      'incident-analysis-orchestrator',
      of(catalog()),
      customDetail
    );
    fixture.detectChanges();

    fixture.componentInstance.restoreDefault();
    fixture.detectChanges();

    expect(api.restoreDefault).toHaveBeenCalledWith('incident-analysis-orchestrator');
    expect(fixture.componentInstance.detail()?.state).toBe('DEFAULT');
    expect(fixture.nativeElement.textContent).toContain('Default restored.');
  });

  it('should protect unsaved edits from navigation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { fixture } = await createComponent('incident-analysis-orchestrator');
    fixture.componentInstance.beginEdit();
    fixture.componentInstance.editorControl.setValue('changed');

    expect(fixture.componentInstance.canDeactivate()).toBe(false);
  });

  it('should show a controlled catalog error with retry', async () => {
    const error = new HttpErrorResponse({
      status: 503,
      error: { message: 'Runtime catalog is unavailable.' }
    });
    const { fixture } = await createComponent('', throwError(() => error));

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Catalog unavailable');
    expect(fixture.nativeElement.textContent).toContain('Runtime catalog is unavailable.');
  });

  it('should show an explicit empty runtime catalog state', async () => {
    const emptyCatalog: AiSkillCatalogResponse = {
      ...catalog(),
      skillCount: 0,
      skills: []
    };
    const { fixture } = await createComponent('', of(emptyCatalog));

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No skills available');
    expect(fixture.nativeElement.textContent).toContain('The effective runtime catalog is empty.');
  });
});

async function createComponent(
  skillName = '',
  catalogResponse: Observable<AiSkillCatalogResponse> = of(catalog()),
  detailResponse: AiSkillDetailResponse = detail(skillName || 'incident-analysis-orchestrator')
) {
  const paramMap = new BehaviorSubject(convertToParamMap(skillName ? { skillName } : {}));
  const api = {
    getCatalog: vi.fn(() => catalogResponse),
    getSkill: vi.fn(() => of(detailResponse)),
    updateSkill: vi.fn((_name: string, request: { rawMarkdown: string }) =>
      of({
        ...detailResponse,
        rawMarkdown: request.rawMarkdown,
        markdown: '# Runtime guidance\n\nChanged.',
        state: 'CUSTOM' as const
      })
    ),
    restoreDefault: vi.fn(() => of({ ...detailResponse, state: 'DEFAULT' as const }))
  };

  await TestBed.configureTestingModule({
    imports: [AiSkillsPageComponent],
    providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { paramMap } },
      { provide: AiSkillsApiService, useValue: api }
    ]
  }).compileComponents();

  return {
    fixture: TestBed.createComponent(AiSkillsPageComponent),
    api
  };
}

function catalog(): AiSkillCatalogResponse {
  return {
    contract: 'ai-skills.catalog',
    version: 2,
    mode: 'EDITABLE',
    source: 'COPILOT_RUNTIME',
    skillCount: 4,
    defaultSkillCount: 4,
    customSkillCount: 0,
    skills: [
      {
        name: 'incident-analysis-orchestrator',
        description: 'Coordinates incident analysis.',
        lineCount: 120,
        state: 'DEFAULT',
        restoreAvailable: true
      },
      {
        name: 'flow-explorer-orchestrator',
        description: 'Coordinates flow discovery.',
        lineCount: 90,
        state: 'DEFAULT',
        restoreAvailable: true
      },
      {
        name: 'flow-explorer-follow-up-chat',
        description: 'Answers follow-up questions.',
        lineCount: 70,
        state: 'DEFAULT',
        restoreAvailable: true
      },
      {
        name: 'delivery-effectiveness-assessment-evaluator',
        description: 'Assesses one delivered change.',
        lineCount: 138,
        state: 'DEFAULT',
        restoreAvailable: true
      }
    ]
  };
}

function detail(skillName: string): AiSkillDetailResponse {
  return {
    contract: 'ai-skills.detail',
    version: 2,
    mode: 'EDITABLE',
    source: 'COPILOT_RUNTIME',
    name: skillName,
    description: 'Coordinates incident analysis.',
    lineCount: 8,
    markdown: '# Runtime guidance\n\nUse verified evidence.',
    rawMarkdown: `---\nname: ${skillName}\ndescription: Coordinates incident analysis.\n---\n\n# Runtime guidance`,
    state: 'DEFAULT',
    restoreAvailable: true
  };
}
