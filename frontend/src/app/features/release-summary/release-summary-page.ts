import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApplicationOption, ApplicationSelectComponent } from '../../components/application-select/application-select';
import { GitLabBranchSelectComponent } from '../../components/gitlab-branch-select/gitlab-branch-select';
import { ReleaseSummaryApiService } from './release-summary-api.service';
import { ReleaseRefKind, ReleaseSummarySnapshot, releaseRef } from './release-summary.models';
import { AnalysisJobPollingService } from '../../core/services/analysis-job-polling.service';
import { Subscription } from 'rxjs';
@Component({
 selector:'app-release-summary-page',
 imports:[ApplicationSelectComponent,GitLabBranchSelectComponent],
 template:`
 <main class="page-shell">
  <section class="analysis-card">
   <h2>Release summary · zmiany Liquibase</h2>
   <p>Porównaj stan bazowy z docelowym i przygotuj opis zmian wdrożeniowych.</p>
   <div class="target-grid">
    <app-application-select [options]="applications()" [selectedId]="systemId()" [loading]="loading()"
      [error]="error()" (selected)="selectSystem($event)" (retry)="loadApplications()" />
    <div><label class="form-field">Rodzaj refa bazowego
      <select [value]="beforeKind()" (change)="setKind('before',$any($event.target).value)">
        <option>BRANCH</option><option>TAG</option><option>COMMIT</option>
      </select></label>
      <app-gitlab-branch-select label="Stan bazowy" [sourceKey]="systemId() ? systemId()+':'+beforeKind():''"
        [branchLoader]="beforeLoader" [selectedBranch]="before()" [autoSelect]="false"
        (branchSelected)="before.set($event)" />
    </div>
    <div><label class="form-field">Rodzaj refa docelowego
      <select [value]="afterKind()" (change)="setKind('after',$any($event.target).value)">
        <option>BRANCH</option><option>TAG</option><option>COMMIT</option>
      </select></label>
      <app-gitlab-branch-select label="Stan docelowy" [sourceKey]="systemId() ? systemId()+':'+afterKind():''"
        [branchLoader]="afterLoader" [selectedBranch]="after()" [autoSelect]="false"
        (branchSelected)="after.set($event)" />
    </div>
   </div>
   <button class="primary-button" type="button" [disabled]="!systemId() || !before() || !after() || starting()" (click)="start()">Przygotuj zakres</button>
   @if(error()) { <p role="alert">{{error()}}</p> }
  </section>
  @if(job(); as current) { <section class="analysis-card"><p role="status">{{ current.status }}</p>
    @if(current.error) { <p role="alert">{{current.error}}</p> }
    @for(repo of current.comparison?.repositories || [];track repo.repositoryId) {
      <p>{{repo.repositoryId}}: {{repo.baselineCommit}} → {{repo.targetCommit}}</p>
    }
  </section> }
 </main>`,
 styles:[`.target-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin-bottom:20px}
   @media(max-width:800px){.target-grid{grid-template-columns:1fr}}`]
})
export class ReleaseSummaryPageComponent {
 readonly api=inject(ReleaseSummaryApiService);
 private readonly polling=inject(AnalysisJobPollingService);
 private pollSubscription?:Subscription;
 readonly destroyRef=inject(DestroyRef);
 readonly applications=signal<ApplicationOption[]>([]);
 readonly systemId=signal('');
 readonly before=signal(''); readonly after=signal('');
 readonly beforeKind=signal<ReleaseRefKind>('BRANCH'); readonly afterKind=signal<ReleaseRefKind>('BRANCH');
 readonly loading=signal(false); readonly starting=signal(false); readonly error=signal('');
 readonly job=signal<ReleaseSummarySnapshot|null>(null);
 readonly beforeLoader=(key:string,search:string,page?:number)=>this.api.refs(key.slice(0,key.lastIndexOf(':')),key.slice(key.lastIndexOf(':')+1) as ReleaseRefKind,search,page);
 readonly afterLoader=this.beforeLoader;
 constructor(){this.loadApplications();}
 loadApplications(){this.loading.set(true);this.error.set('');this.api.applications().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
  next:items=>{this.applications.set(items);this.loading.set(false);},
  error:()=>{this.loading.set(false);this.error.set('Nie udało się pobrać aplikacji.');}
 });}
 selectSystem(id:string){this.systemId.set(id);this.before.set('');this.after.set('');}
 setKind(side:'before'|'after',kind:ReleaseRefKind){if(side==='before'){this.beforeKind.set(kind);this.before.set('');}else{this.afterKind.set(kind);this.after.set('');}}
 start(){if(!this.systemId()||!this.before()||!this.after())return;
  this.starting.set(true);this.error.set('');
  this.api.start({systemId:this.systemId(),mode:'LIQUIBASE',baselineRef:releaseRef(this.before()),targetRef:releaseRef(this.after())})
   .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:job=>{this.job.set(job);this.starting.set(false);this.pollSubscription?.unsubscribe();
     this.pollSubscription=this.polling.poll({load:()=>this.api.get(job.jobId),initialDelayMs:1000,isTerminal:s=>!['QUEUED','RUNNING'].includes(s.status)})
       .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:s=>this.job.set(s),error:()=>this.error.set('Odczyt postępu nie powiódł się.')});},
   error:()=>{this.starting.set(false);this.error.set('Nie udało się uruchomić porównania.');}});
 }
}

