import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTooltipModule } from '@angular/material/tooltip';
import { firstValueFrom } from 'rxjs';
import { TextFieldComponent } from '../../components/text-field/text-field';
import { FilePreviewApiService, FilePreviewResponse } from '../../core/services/file-preview-api.service';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';

interface SelectedFile { id: number; file: File; }
interface PreviewResult { id: number; name: string; response: FilePreviewResponse | null; error: string; }

@Component({
  selector: 'app-file-reader-page',
  imports: [MatTooltipModule, TextFieldComponent],
  templateUrl: './file-reader-page.html',
  styleUrls: ['../source-console-layout.scss', './file-reader-page.scss']
})
export class FileReaderPage {
  private readonly api = inject(FilePreviewApiService);
  private readonly destroyRef = inject(DestroyRef);
  private nextId = 0;
  readonly files = signal<SelectedFile[]>([]);
  readonly results = signal<PreviewResult[]>([]);
  readonly busy = signal(false);
  readonly currentFile = signal('');
  readonly copied = signal('');
  readonly requestJson = computed(() => JSON.stringify(this.files().map(({ file }) => ({
    method: 'POST', endpoint: '/api/files/preview', name: file.name,
    contentType: 'application/octet-stream', size: file.size
  })), null, 2));
  readonly responseJson = computed(() => JSON.stringify(this.results().map(result => result.response ?? {
    name: result.name, status: 'FAILED', message: result.error
  }), null, 2));

  selectFiles(input: HTMLInputElement): void {
    if (this.busy()) return;
    const added = Array.from(input.files ?? []).map(file => ({ id: ++this.nextId, file }));
    input.value = '';
    if (!added.length) return;
    this.files.update(files => [...files, ...added]);
    this.results.set([]);
  }

  removeFile(id: number): void {
    if (this.busy()) return;
    this.files.update(files => files.filter(file => file.id !== id));
    this.results.set([]);
  }

  clear(): void {
    if (this.busy()) return;
    this.files.set([]);
    this.results.set([]);
  }

  async readFiles(): Promise<void> {
    if (this.busy() || !this.files().length) return;
    this.busy.set(true);
    this.results.set([]);
    try {
      for (const selected of this.files()) {
        if (this.destroyRef.destroyed) break;
        this.currentFile.set(selected.file.name);
        try {
          const response = await firstValueFrom(this.api.preview(selected.file).pipe(takeUntilDestroyed(this.destroyRef)));
          if (this.destroyRef.destroyed) break;
          this.results.update(results => [...results, { id: selected.id, name: selected.file.name, response, error: '' }]);
        } catch (error) {
          if (this.destroyRef.destroyed) break;
          const payload = error instanceof HttpErrorResponse ? error.error : null;
          const response = payload && payload.status === 'FAILED' && Array.isArray(payload.images)
            ? payload as FilePreviewResponse : null;
          const message = response?.message || 'Nie udało się odczytać pliku. Spróbuj ponownie.';
          this.results.update(results => [...results, { id: selected.id, name: selected.file.name, response, error: message }]);
        }
      }
    } finally {
      this.currentFile.set('');
      this.busy.set(false);
    }
  }

  async copy(kind: 'request' | 'response'): Promise<void> {
    if (await copyTextToClipboard(kind === 'request' ? this.requestJson() : this.responseJson())) {
      this.copied.set(kind);
    }
  }

  download(kind: 'request' | 'response'): void {
    const data = kind === 'request' ? this.requestJson() : this.responseJson();
    const url = URL.createObjectURL(new Blob([data], { type: 'application/json;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `file-reader-${kind}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  size(bytes: number): string {
    return bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB`
      : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
