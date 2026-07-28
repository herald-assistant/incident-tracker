import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ConfluencePageContentPayload {
  pageUrl: string;
}

export interface ConfluencePageContentResponse {
  pageId?: string | null;
  title?: string | null;
  url?: string | null;
  content?: string | null;
  version?: string | null;
  limitations: string[];
}

@Injectable({
  providedIn: 'root'
})
export class ConfluenceSourceApiService {
  private readonly http = inject(HttpClient);

  getPageContent(
    payload: ConfluencePageContentPayload
  ): Observable<ConfluencePageContentResponse> {
    return this.http.post<ConfluencePageContentResponse>(
      '/api/confluence/page/content',
      payload
    );
  }
}
