import { formatFileTimestamp, sanitizeFileNamePart } from '../../../core/utils/json-file.utils';
import {
  UiExplorerExportEnvelope,
  UiExplorerJobStateSnapshot
} from '../models/ui-explorer.models';

export const UI_EXPLORER_EXPORT_SCHEMA = 'tdw.ui-explorer-export';
export const UI_EXPLORER_LOCAL_RUN_SCHEMA = 'tdw.ui-explorer-local-run';
export const UI_EXPLORER_EXPORT_VERSION = 6;
export const UI_EXPLORER_LEGACY_EXPORT_VERSION = 5;
export const UI_EXPLORER_EXPORT_PAYLOAD_TYPE = 'ui-explorer-analysis';
export const UI_EXPLORER_RESULT_CONTRACT = 'ui-explorer-result-v6';
export const UI_EXPLORER_LEGACY_RESULT_CONTRACT = 'ui-explorer-result-v5';

export function parseUiExplorerLocalRunEnvelope(payload: unknown): {
  storedAt: string;
  job: UiExplorerJobStateSnapshot;
} {
  const envelope = asObject(payload);
  if (!envelope || envelope['schema'] !== UI_EXPLORER_LOCAL_RUN_SCHEMA) {
    throw new Error('Lokalny run nie zawiera koperty UI Explorer w aktualnym formacie.');
  }
  if (![UI_EXPLORER_EXPORT_VERSION, UI_EXPLORER_LEGACY_EXPORT_VERSION].includes(
    envelope['version'] as number
  )) {
    throw new Error('Lokalny run UI Explorer ma nieobsługiwaną wersję formatu.');
  }

  const envelopePayload = asObject(envelope['payload']);
  if (!envelopePayload || envelopePayload['type'] !== UI_EXPLORER_EXPORT_PAYLOAD_TYPE) {
    throw new Error('Lokalny run nie zawiera wyniku UI Explorer.');
  }
  if (!isSupportedResultContract(envelope['version'], envelopePayload['resultContract'])) {
    throw new Error('Lokalny run UI Explorer ma nieobsługiwany kontrakt wyniku.');
  }

  const job = envelopePayload['job'];
  if (!isUiExplorerJobSnapshot(job)) {
    throw new Error('Lokalny run UI Explorer zawiera uszkodzony snapshot.');
  }
  if (!['COMPLETED', 'PARTIAL'].includes(job.status) || !job.report || !job.result) {
    throw new Error('Lokalny run UI Explorer nie zawiera zakończonego raportu.');
  }

  return {
    storedAt: typeof envelope['storedAt'] === 'string' ? envelope['storedAt'] : '',
    job: normalizeUiExplorerJobSnapshot(job)
  };
}

export function buildUiExplorerExportFileName(
  job: UiExplorerJobStateSnapshot,
  exportedAt: string
): string {
  const screen = sanitizeFileNamePart(job.request.screenId || 'ui-explorer');
  return `ui-explorer-${screen}-${formatFileTimestamp(exportedAt)}.json`;
}

export function isUiExplorerExportEnvelope(value: unknown): value is UiExplorerExportEnvelope {
  const envelope = asObject(value);
  const payload = asObject(envelope?.['payload']);
  return Boolean(
    envelope &&
      envelope['schema'] === UI_EXPLORER_EXPORT_SCHEMA &&
      envelope['version'] === UI_EXPLORER_EXPORT_VERSION &&
      typeof envelope['exportedAt'] === 'string' &&
      payload &&
      payload['type'] === UI_EXPLORER_EXPORT_PAYLOAD_TYPE &&
      payload['resultContract'] === UI_EXPLORER_RESULT_CONTRACT &&
      isUiExplorerJobSnapshot(payload['job'])
  );
}

function isUiExplorerJobSnapshot(value: unknown): value is UiExplorerJobStateSnapshot {
  const job = asObject(value);
  const request = asObject(job?.['request']);
  return Boolean(
    job &&
      typeof job['jobId'] === 'string' &&
      typeof job['status'] === 'string' &&
      request &&
      typeof request['systemId'] === 'string' &&
      typeof request['systemLabel'] === 'string' &&
      typeof request['branch'] === 'string' &&
      typeof request['screenId'] === 'string' &&
      typeof request['sourceRevision'] === 'string' &&
      Array.isArray(request['sectionModes']) &&
      Array.isArray(job['steps']) &&
      Array.isArray(job['contextSections']) &&
      Array.isArray(job['toolEvidenceSections']) &&
      Array.isArray(job['aiActivityEvents']) &&
      Array.isArray(job['toolFeedback']) &&
      typeof job['exportAvailable'] === 'boolean'
  );
}

function isSupportedResultContract(version: unknown, contract: unknown): boolean {
  return (
    (version === UI_EXPLORER_EXPORT_VERSION && contract === UI_EXPLORER_RESULT_CONTRACT) ||
    (version === UI_EXPLORER_LEGACY_EXPORT_VERSION &&
      contract === UI_EXPLORER_LEGACY_RESULT_CONTRACT)
  );
}

function normalizeUiExplorerJobSnapshot(job: UiExplorerJobStateSnapshot): UiExplorerJobStateSnapshot {
  const interruptedAt = new Date().toISOString();
  const chatMessages = (Array.isArray(job.chatMessages) ? job.chatMessages : []).map((message) =>
    message.role === 'ASSISTANT' && message.status === 'IN_PROGRESS'
      ? {
          ...message,
          status: 'FAILED',
          errorCode: 'UI_EXPLORER_CHAT_INTERRUPTED',
          errorMessage:
            'Odpowiedź została przerwana przez restart backendu. Pytanie nie zostało wysłane ponownie.',
          updatedAt: interruptedAt,
          completedAt: interruptedAt
        }
      : message
  );
  return {
    ...job,
    chatMessages,
    chatAvailability: job.chatAvailability ?? {
      available: false,
      code: 'UI_EXPLORER_LEGACY_CHAT_UNAVAILABLE',
      message: 'Ten starszy zapis nie zawiera danych potrzebnych do kontynuacji rozmowy.'
    }
  };
}

function asObject(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}
