import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { GitLabSystemBranchesApiService } from '../../core/services/gitlab-system-branches-api.service';
import { GitLabBranchSelectComponent } from './gitlab-branch-select';

describe('GitLabBranchSelectComponent', () => {
  it('groups long ref names and repositories separately from ref badges', () => {
    const name = 'feature/customer-profile-contact-preferences-and-notification-settings';
    const repositories = ['crm-customer-profile-repo', 'crm-notifications-repo'];
    const loader = vi.fn(() => of({
      branches: [{ name, kind: 'BRANCH' as const, repositories, isDefault: true }],
      truncated: false, warnings: []
    }));
    const fixture = TestBed.createComponent(GitLabBranchSelectComponent);
    const selected = vi.fn();
    fixture.componentInstance.branchSelected.subscribe(selected);
    fixture.componentRef.setInput('sourceKey', 'crm:BRANCH');
    fixture.componentRef.setInput('branchLoader', loader);
    fixture.componentRef.setInput('autoSelect', false);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.branch-select__control').click();
    fixture.detectChanges();

    const option = fixture.nativeElement.querySelector('.branch-select__option') as HTMLButtonElement;
    const content = option.querySelector('.branch-select__option-content')!;
    const refName = content.querySelector('.branch-select__option-name')!;
    const repositoryNames = content.querySelector('.branch-select__repositories')!;
    expect(refName.textContent).toBe(name);
    expect(refName.getAttribute('title')).toBe(name);
    expect(repositoryNames.textContent).toBe(repositories.join(', '));
    expect(repositoryNames.getAttribute('title')).toBe(repositories.join(', '));
    expect(option.querySelector('.branch-select__option-badges')!.textContent).toContain('BRANCH');
    expect(option.querySelector('.branch-select__option-badges')!.textContent).toContain('default');
    option.click();
    expect(selected).toHaveBeenCalledWith(name);
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('shows only a commit date instead of kind and explains repository dates without a time', () => {
    const loader = vi.fn(() => of({branches: [
      {name:'release/next',kind:'BRANCH',isDefault:true,committedAt:'2026-11-02T12:35:00Z',
        repositoryCommits:[{repositoryId:'crm-api',committedAt:'2026-11-02T12:35:00Z'},
          {repositoryId:'crm-web',committedAt:null}]},
      {name:'feature/customer-labels',kind:'BRANCH',isDefault:false,committedAt:'invalid'},
      {name:'feature/customer-search',kind:'BRANCH',isDefault:false,committedAt:null}
    ],truncated:false,warnings:[]}));
    const fixture=TestBed.createComponent(GitLabBranchSelectComponent);
    fixture.componentRef.setInput('sourceKey','crm:BRANCH');fixture.componentRef.setInput('branchLoader',loader);
    fixture.componentRef.setInput('showCommitDate',true);fixture.detectChanges();
    fixture.nativeElement.querySelector('.branch-select__control').click();fixture.detectChanges();
    const badges=[...fixture.nativeElement.querySelectorAll('.branch-select__option-badges')] as HTMLElement[];
    expect(badges[0].textContent).toContain('02.11.2026');expect(badges[0].textContent).toContain('default');
    expect(badges.every(badge=>!badge.textContent?.includes('BRANCH'))).toBe(true);
    const date=badges[0].querySelector('.branch-select__commit-date')!;
    expect(date.textContent?.trim()).toBe('02.11.2026');
    expect(date.getAttribute('title')).toContain('Najnowszy commit: 02.11.2026');
    expect(date.getAttribute('title')).toContain('crm-web: Brak daty');
    expect(date.getAttribute('title')).not.toContain('12:35');
    expect(badges[1].textContent?.trim()).toBe('Brak daty');expect(badges[2].textContent?.trim()).toBe('Brak daty');
  });

  it('keeps the newest date and repository provenance when a ref repeats on another page', () => {
    const loader=vi.fn((_key:string,_search:string,page?:number)=>of({branches:[{
      name:'release/next',isDefault:!page,committedAt:page?'2026-11-01T12:00:00Z':'2026-11-02T12:00:00Z',
      repositories:[page?'crm-web':'crm-api'],
      repositoryCommits:[{repositoryId:page?'crm-web':'crm-api',committedAt:page?'2026-11-01T12:00:00Z':'2026-11-02T12:00:00Z'}]
    }],truncated:!page,nextPage:page?null:2,warnings:[]}));
    const fixture=TestBed.createComponent(GitLabBranchSelectComponent);
    fixture.componentRef.setInput('sourceKey','crm:BRANCH');fixture.componentRef.setInput('branchLoader',loader);
    fixture.componentRef.setInput('showCommitDate',true);fixture.detectChanges();
    fixture.componentInstance.loadMore();fixture.detectChanges();
    const options=fixture.componentInstance.branches();expect(options.length).toBe(1);
    expect(options[0].committedAt).toBe('2026-11-02T12:00:00Z');
    expect(options[0].repositoryCommits?.length).toBe(2);expect(options[0].repositories).toEqual(['crm-api','crm-web']);
    expect(options[0].isDefault).toBe(true);
  });

  it('requires a manual ref choice and appends every requested page', () => {
    const loader = vi.fn((_key: string, _search: string, page?: number) => of({
      branches: [{name: page ? 'refs/tags/next' : 'refs/tags/current', label: page ? 'next' : 'current', isDefault: false}],
      truncated: !page, nextPage: page ? null : 2, warnings: []
    }));
    const fixture = TestBed.createComponent(GitLabBranchSelectComponent);
    const selected = vi.fn(); fixture.componentInstance.branchSelected.subscribe(selected);
    fixture.componentRef.setInput('sourceKey', 'crm:TAG'); fixture.componentRef.setInput('branchLoader', loader);
    fixture.componentRef.setInput('autoSelect', false); fixture.detectChanges();
    fixture.componentInstance.loadMore(); fixture.detectChanges();
    expect(loader).toHaveBeenLastCalledWith('crm:TAG', '', 2); expect(selected).not.toHaveBeenCalled();
    fixture.nativeElement.querySelector('.branch-select__control').click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[role=option]')).toHaveLength(2);
  });
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

  it('loads branches for a selected project through the shared picker', async () => {
    const loader = vi.fn((sourceKey: string, search: string) => of({
      branches: search
        ? [{ name: 'release/2026', isDefault: false }]
        : [{ name: 'main', isDefault: true }, { name: 'release/2026', isDefault: false }],
      truncated: false,
      warnings: []
    }));
    const fixture = TestBed.createComponent(GitLabBranchSelectComponent);
    const selected: string[] = [];
    fixture.componentInstance.branchSelected.subscribe((branch) => selected.push(branch));
    fixture.componentRef.setInput('branchLoader', loader);
    fixture.componentRef.setInput('sourceKey', 'project:nested/customer-api');
    fixture.detectChanges();

    expect(loader).toHaveBeenCalledWith('project:nested/customer-api', '');
    expect(api.getBranches).not.toHaveBeenCalled();
    expect(selected).toEqual(['main']);

    const nativeElement = fixture.nativeElement as HTMLElement;
    nativeElement.querySelector<HTMLButtonElement>('.branch-select__control')?.click();
    fixture.detectChanges();
    const filter = nativeElement.querySelector<HTMLInputElement>('input[type="search"]')!;
    filter.value = 'release';
    filter.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 230));
    fixture.detectChanges();
    expect(loader).toHaveBeenCalledWith('project:nested/customer-api', 'release');
    nativeElement.querySelector<HTMLButtonElement>('.branch-select__option')?.click();
    expect(selected).toEqual(['main', 'release/2026']);
  });
});
