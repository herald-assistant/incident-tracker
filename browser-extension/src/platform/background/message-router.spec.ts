import { describe, expect, it } from 'vitest';
import { extensionMessage, success } from '../message';
import { MessageRouter } from './message-router';

describe('MessageRouter', () => {
  it('rejects values outside the extension message channel', async () => {
    const response = await new MessageRouter().dispatch(
      { type: 'crm:test', payload: {} },
      {} as chrome.runtime.MessageSender
    );
    expect(response).toEqual({
      ok: false,
      error: {
        code: 'INVALID_EXTENSION_MESSAGE',
        message: 'Nieprawidlowy komunikat rozszerzenia.'
      }
    });
  });

  it('routes a typed payload', async () => {
    const router = new MessageRouter();
    router.register<{ customerId: string }, { accepted: boolean }>('crm:test', (payload) =>
      success({ accepted: payload.customerId === 'crm-customer-1' })
    );

    const response = await router.dispatch(
      extensionMessage('crm:test', { customerId: 'crm-customer-1' }),
      {} as chrome.runtime.MessageSender
    );
    expect(response).toEqual({ ok: true, data: { accepted: true } });
  });

  it('rejects duplicate handlers', () => {
    const router = new MessageRouter();
    router.register('crm:test', () => success(null));
    expect(() => router.register('crm:test', () => success(null))).toThrow(
      'Message handler already registered: crm:test'
    );
  });
});
