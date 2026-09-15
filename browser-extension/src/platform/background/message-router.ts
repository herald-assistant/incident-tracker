import {
  failure,
  isExtensionMessage,
  type ExtensionMessage,
  type ExtensionResponse
} from '../message';

export type MessageHandler<TPayload = unknown, TResult = unknown> = (
  payload: TPayload,
  sender: chrome.runtime.MessageSender
) => Promise<ExtensionResponse<TResult>> | ExtensionResponse<TResult>;

export class MessageRouter {
  private readonly handlers = new Map<string, MessageHandler>();

  register<TPayload, TResult>(type: string, handler: MessageHandler<TPayload, TResult>): void {
    if (this.handlers.has(type)) {
      throw new Error(`Message handler already registered: ${type}`);
    }
    this.handlers.set(type, handler as MessageHandler);
  }

  async dispatch(
    value: unknown,
    sender: chrome.runtime.MessageSender
  ): Promise<ExtensionResponse<unknown>> {
    if (!isExtensionMessage(value)) {
      return failure('INVALID_EXTENSION_MESSAGE', 'Nieprawidlowy komunikat rozszerzenia.');
    }
    const handler = this.handlers.get(value.type);
    if (!handler) {
      return failure('UNKNOWN_EXTENSION_MESSAGE', `Brak obslugi komunikatu ${value.type}.`);
    }
    try {
      return await handler(value.payload, sender);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Nieznany blad rozszerzenia.';
      return failure('EXTENSION_HANDLER_FAILED', message);
    }
  }
}

export function typedMessage<TType extends string, TPayload>(
  value: ExtensionMessage<TType, TPayload>
): ExtensionMessage<TType, TPayload> {
  return value;
}
