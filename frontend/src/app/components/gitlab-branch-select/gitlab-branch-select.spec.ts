import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { GitLabSystemBranchesApiService } from '../../core/services/gitlab-system-branches-api.service';
import { GitLabBranchSelectComponent } from './gitlab-branch-select';

describe('GitLabBranchSelectComponent', () => {
  const api = {
    getBranches: vi.fn((systemId: string, search = '') => of({
      systemId,
      branches: search
        ? [{ name: 'release/2026', isDefault: false }]
        : [
            { name: 'main', isDefault: true },
            { name: 'release/2026', isDefault: false }
          ],
      truncated: false,
      warnings: []
    }))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [GitLabBranchSelectComponent],
      providers: [{ provide: GitLabSystemBranchesApiService, useValue: api }]
    }).compileComponents();
  });

  it('chooses the GitLab default and filters remotely without changing selection', async () => {
    const fixture = TestBed.createComponent(GitLabBranchSelectComponent);
    const selected: string[] = [];
    fixture.componentInstance.branchSelected.subscribe((branch) => selected.push(branch));
    fixture.componentRef.setInput('systemId', 'crm-service');
    fixture.detectChanges();

    expect(api.getBranches).toHaveBeenCalledWith('crm-service', '');
    expect(selected).toEqual(['main']);

    const nativeElement = fixture.nativeElement as HTMLElement;
    nativeElement.querySelector<HTMLButtonElement>('.branch-select__control')?.click();
    fixture.detectChanges();
    const filter = nativeElement.querySelector<HTMLInputElement>('input[type="search"]')!;
    filter.value = 'release';
    filter.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(selected).toEqual(['main']);
    await new Promise((resolve) => setTimeout(resolve, 230));
    fixture.detectChanges();
    expect(api.getBranches).toHaveBeenCalledWith('crm-service', 'release');
    expect(nativeElement.querySelectorAll('.branch-select__option')).toHaveLength(1);

    nativeElement.querySelector<HTMLButtonElement>('.branch-select__option')?.click();
    expect(selected).toEqual(['main', 'release/2026']);
  });

  it('keeps an existing branch when the selected system offers it', () => {
    const fixture = TestBed.createComponent(GitLabBranchSelectComponent);
    const selected: string[] = [];
    fixture.componentInstance.branchSelected.subscribe((branch) => selected.push(branch));
    fixture.componentRef.setInput('selectedBranch', 'release/2026');
    fixture.componentRef.setInput('systemId', 'crm-service');
    fixture.detectChanges();

    expect(selected).toEqual(['release/2026']);
  });
});
