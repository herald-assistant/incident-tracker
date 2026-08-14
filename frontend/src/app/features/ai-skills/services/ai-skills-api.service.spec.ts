import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AiSkillsApiService } from './ai-skills-api.service';

describe('AiSkillsApiService', () => {
  let service: AiSkillsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AiSkillsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should load the runtime catalog', () => {
    service.getCatalog().subscribe();

    const request = http.expectOne('/api/ai/skills');
    expect(request.request.method).toBe('GET');
    request.flush({ skills: [] });
  });

  it('should encode the exact skill name in the detail URL', () => {
    service.getSkill('skill/name').subscribe();

    const request = http.expectOne('/api/ai/skills/skill%2Fname');
    expect(request.request.method).toBe('GET');
    request.flush({ name: 'skill/name' });
  });

  it('should overwrite one skill with the edited raw source', () => {
    service.updateSkill('skill/name', { rawMarkdown: '# Updated' }).subscribe();

    const request = http.expectOne('/api/ai/skills/skill%2Fname');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ rawMarkdown: '# Updated' });
    request.flush({ name: 'skill/name' });
  });

  it('should restore one packaged default', () => {
    service.restoreDefault('skill/name').subscribe();

    const request = http.expectOne('/api/ai/skills/skill%2Fname/restore-default');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    request.flush({ name: 'skill/name' });
  });
});
