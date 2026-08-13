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
    expect(compiled.querySelectorAll('.ai-skills-row')).toHaveLength(3);
    expect(compiled.textContent).toContain('Effective runtime catalog');
    expect(compiled.textContent).toContain('incident-analysis-orchestrator');
    expect(compiled.textContent).toContain('Flow Explorer');

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
  catalogResponse: Observable<AiSkillCatalogResponse> = of(catalog())
) {
  const paramMap = new BehaviorSubject(convertToParamMap(skillName ? { skillName } : {}));
  const api = {
    getCatalog: vi.fn(() => catalogResponse),
    getSkill: vi.fn(() => of(detail(skillName || 'incident-analysis-orchestrator')))
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
    version: 1,
    mode: 'READ_ONLY',
    source: 'COPILOT_RUNTIME',
    skillCount: 3,
    skills: [
      {
        name: 'incident-analysis-orchestrator',
        description: 'Coordinates incident analysis.',
        lineCount: 120
      },
      {
        name: 'flow-explorer-orchestrator',
        description: 'Coordinates flow discovery.',
        lineCount: 90
      },
      {
        name: 'flow-explorer-follow-up-chat',
        description: 'Answers follow-up questions.',
        lineCount: 70
      }
    ]
  };
}

function detail(skillName: string): AiSkillDetailResponse {
  return {
    contract: 'ai-skills.detail',
    version: 1,
    mode: 'READ_ONLY',
    source: 'COPILOT_RUNTIME',
    name: skillName,
    description: 'Coordinates incident analysis.',
    lineCount: 8,
    markdown: '# Runtime guidance\n\nUse verified evidence.',
    rawMarkdown: `---\nname: ${skillName}\ndescription: Coordinates incident analysis.\n---\n\n# Runtime guidance`
  };
}
