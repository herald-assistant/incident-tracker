import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface DatabaseToolScopePayload {
  environment: string;
}

@Injectable({
  providedIn: 'root'
})
export class DatabaseApiService {
  private readonly http = inject(HttpClient);

  runTool(endpoint: string, scope: DatabaseToolScopePayload, request?: unknown): Observable<unknown> {
    const body = endpoint === 'scope' ? { scope } : { scope, request };
    return this.http.post(`/api/database/${endpoint}`, body);
  }
}
