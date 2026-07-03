import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  WorkspaceSettingsResponse,
  WorkspaceSettingsUpdateRequest
} from '../models/workspace-settings.models';

@Injectable({
  providedIn: 'root'
})
export class WorkspaceSettingsApiService {
  private readonly http = inject(HttpClient);

  getSettings(): Observable<WorkspaceSettingsResponse> {
    return this.http.get<WorkspaceSettingsResponse>('/api/workspace/settings');
  }

  saveSettings(request: WorkspaceSettingsUpdateRequest): Observable<WorkspaceSettingsResponse> {
    return this.http.put<WorkspaceSettingsResponse>('/api/workspace/settings', request);
  }
}
