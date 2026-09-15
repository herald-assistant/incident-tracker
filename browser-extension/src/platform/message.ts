export const MESSAGE_CHANNEL = 'tdw-browser-extension/v1';

export interface ExtensionMessage<TType extends string = string, TPayload = unknown> {
  readonly channel: typeof MESSAGE_CHANNEL;
  readonly type: TType;
  readonly payload: TPayload;
}

export interface ExtensionError {
  readonly code: string;
  readonly message: string;
}

export type ExtensionResponse<TData> =
  | { readonly ok: true; readonly data: TData }
  | { readonly ok: false; readonly error: ExtensionError };

export function extensionMessage<TType extends string, TPayload>(
  type: TType,
  payload: TPayload
): ExtensionMessage<TType, TPayload> {
  return { channel: MESSAGE_CHANNEL, type, payload };
}

export function isExtensionMessage(value: unknown): value is ExtensionMessage {
  if (!isRecord(value)) {
    return false;
  }
  return (
    value['channel'] === MESSAGE_CHANNEL &&
    typeof value['type'] === 'string' &&
    Object.hasOwn(value, 'payload')
  );
}

export function success<TData>(data: TData): ExtensionResponse<TData> {
  return { ok: true, data };
}

export function failure(code: string, message: string): ExtensionResponse<never> {
  return { ok: false, error: { code, message } };
}

export async function sendExtensionMessage<TData>(
  message: ExtensionMessage
): Promise<ExtensionResponse<TData>> {
  try {
    const response: unknown = await chrome.runtime.sendMessage(message);
    if (!isExtensionResponse<TData>(response)) {
      return failure(
        'INVALID_EXTENSION_RESPONSE',
        'Rozszerzenie zwrocilo nieprawidlowa odpowiedz.'
      );
    }
    return response;
  } catch (error) {
    return failure(
      'EXTENSION_MESSAGE_FAILED',
      error instanceof Error ? error.message : 'Nie mozna polaczyc sie z service workerem.'
    );
  }
}

function isExtensionResponse<TData>(value: unknown): value is ExtensionResponse<TData> {
  if (!isRecord(value) || typeof value['ok'] !== 'boolean') {
    return false;
  }
  if (value['ok']) {
    return Object.hasOwn(value, 'data');
  }
  const error = value['error'];
  return (
    isRecord(error) && typeof error['code'] === 'string' && typeof error['message'] === 'string'
  );
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
