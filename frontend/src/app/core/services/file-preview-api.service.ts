import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface FilePreviewImage {
  name: string;
  mimeType: string;
  size: number;
  data: string;
}

export interface FilePreviewResponse {
  name: string;
  mimeType: string;
  size: number;
  status: 'READY' | 'FAILED';
  validationOnly: boolean;
  message: string;
  images: FilePreviewImage[];
}

@Injectable({ providedIn: 'root' })
export class FilePreviewApiService {
  private readonly http = inject(HttpClient);

  preview(file: File) {
    return this.http.post<FilePreviewResponse>('/api/files/preview', file, {
      params: { name: file.name }, headers: { 'Content-Type': 'application/octet-stream' }
    });
  }
}
