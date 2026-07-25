import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface JiraIssueMaterialPayload {
  issueRef: string;
}

export interface JiraIssueLink {
  type?: string | null;
  title?: string | null;
  url?: string | null;
}

export interface JiraIssueComment {
  author?: string | null;
  createdAt?: string | null;
  body?: string | null;
}

export interface JiraIssueMaterialResponse {
  issueKey?: string | null;
  issueUrl?: string | null;
  summary?: string | null;
  description?: string | null;
  issueType?: string | null;
  status?: string | null;
  labels: string[];
  acceptanceCriteria: string[];
  links: JiraIssueLink[];
  comments: JiraIssueComment[];
  limitations: string[];
}

@Injectable({
  providedIn: 'root'
})
export class JiraSourceApiService {
  private readonly http = inject(HttpClient);

  getIssueMaterial(payload: JiraIssueMaterialPayload): Observable<JiraIssueMaterialResponse> {
    return this.http.post<JiraIssueMaterialResponse>('/api/jira/issue/material', payload);
  }
}
