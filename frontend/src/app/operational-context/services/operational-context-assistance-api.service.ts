import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { GitLabBranchesResponse } from '../../core/services/gitlab-system-branches-api.service';

import {
  OperationalContextAssistanceJob,
  OperationalContextAssistanceBatchPreview,
  OperationalContextAssistanceBatchReviewRequest,
  OperationalContextAssistanceRequest,
  OperationalContextAssistanceReviewDraft,
  OperationalContextAssistanceSourceOptions
} from '../models/operational-context-assistance.models';

@Injectable({ providedIn: 'root' })
export class OperationalContextAssistanceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/operational-context/assistance/jobs';

  sourceOptions(): Observable<OperationalContextAssistanceSourceOptions> {
    return this.http.get<OperationalContextAssistanceSourceOptions>('/api/operational-context/assistance/source-options');
  }

  sourceBranches(source: { project: string } | { projectUrl: string }, search = ''): Observable<GitLabBranchesResponse> {
    let params = new HttpParams();
    params = 'project' in source ? params.set('project', source.project) : params.set('projectUrl', source.projectUrl);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<GitLabBranchesResponse>(
      '/api/operational-context/assistance/source-options/branches', { params }
    );
  }

  start(request: OperationalContextAssistanceRequest): Observable<OperationalContextAssistanceJob> {
    return this.http.post<OperationalContextAssistanceJob>(this.baseUrl, request);
  }

  get(jobId: string): Observable<OperationalContextAssistanceJob> {
    return this.http.get<OperationalContextAssistanceJob>(`${this.baseUrl}/${encodeURIComponent(jobId)}`);
  }

  saveReview(jobId: string, reviewDraft: OperationalContextAssistanceReviewDraft): Observable<OperationalContextAssistanceJob> {
    return this.http.put<OperationalContextAssistanceJob>(
      `${this.baseUrl}/${encodeURIComponent(jobId)}/review`, reviewDraft
    );
  }

  previewBatch(jobId: string, request: OperationalContextAssistanceBatchReviewRequest): Observable<OperationalContextAssistanceBatchPreview> {
    return this.http.post<OperationalContextAssistanceBatchPreview>(
      `${this.baseUrl}/${encodeURIComponent(jobId)}/batch/preview`, request
    );
  }

  decideBatch(jobId: string, request: OperationalContextAssistanceBatchReviewRequest): Observable<OperationalContextAssistanceJob> {
    return this.http.post<OperationalContextAssistanceJob>(
      `${this.baseUrl}/${encodeURIComponent(jobId)}/batch/decision`, request
    );
  }
}
