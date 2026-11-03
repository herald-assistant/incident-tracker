import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AnalysisReportEditRequest } from '../../core/models/analysis.models';
import { ApplicationOption } from '../../components/application-select/application-select';
import { ReleaseDateSelection, ReleaseRange, ReleaseRefOptions, ReleaseSummaryRequest, ReleaseSummarySnapshot } from './release-summary.models';
@Injectable({providedIn:'root'})
export class ReleaseSummaryApiService {
  private readonly http=inject(HttpClient);
  applications() { return this.http.get<ApplicationOption[]>('/api/release-summary/applications'); }
  branches(id:string,search:string,page=1) {
    return this.http.get<ReleaseRefOptions>(`/api/release-summary/applications/${encodeURIComponent(id)}/branches`,
      {params:new HttpParams().set('search',search).set('page',page)});
  }
  preview(id:string,selection:ReleaseDateSelection) {
    return this.http.post<ReleaseRange>(`/api/release-summary/applications/${encodeURIComponent(id)}/range-preview`,selection);
  }
  start(request:ReleaseSummaryRequest) { return this.http.post<ReleaseSummarySnapshot>('/api/release-summary/jobs',request); }
  get(id:string) { return this.http.get<ReleaseSummarySnapshot>(`/api/release-summary/jobs/${encodeURIComponent(id)}`); }
  chat(id:string,message:string,updateReport:boolean) {return this.http.post<ReleaseSummarySnapshot>(`/api/release-summary/jobs/${encodeURIComponent(id)}/chat/messages`,{message,updateReport});}
  edit(id:string,request:AnalysisReportEditRequest) {return this.http.put<ReleaseSummarySnapshot>(`/api/release-summary/jobs/${encodeURIComponent(id)}/report`,request);}
  export(id:string) {return this.http.get<unknown>(`/api/release-summary/jobs/${encodeURIComponent(id)}/export`);}
}

