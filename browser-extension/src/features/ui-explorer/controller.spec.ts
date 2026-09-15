import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UiExplorerController } from './controller';

describe('UiExplorerController', () => {
  let controller: UiExplorerController | null = null;

  beforeEach(() => {
    document.body.replaceChildren();
  });

  afterEach(() => {
    controller?.dispose();
    controller = null;
  });

  it('tracks an element, opens the modal and receives dummy accepted', async () => {
    const button = document.createElement('button');
    button.textContent = 'Zapisz kontakt';
    button.disabled = true;
    button.dataset['testid'] = 'contact-save';
    document.body.append(button);
    vi.spyOn(button, 'getBoundingClientRect').mockReturnValue({
      x: 30,
      y: 50,
      left: 30,
      top: 50,
      right: 170,
      bottom: 90,
      width: 140,
      height: 40,
      toJSON: () => ({})
    });
    Object.defineProperty(document, 'elementsFromPoint', {
      configurable: true,
      value: vi.fn(() => [button])
    });
    const sendMessageMock = chrome.runtime.sendMessage as unknown as ReturnType<typeof vi.fn>;
    sendMessageMock.mockResolvedValue({
      ok: true,
      data: {
        jobId: 'demo-crm-job-1',
        status: 'QUEUED',
        acceptedAt: '2026-09-15T10:00:00Z',
        mode: 'DUMMY'
      }
    });

    controller = new UiExplorerController('0.1.0-test', { shadowMode: 'open' });
    const host = document.querySelector<HTMLElement>('[data-tdw-browser-extension-root]')!;
    const root = host.shadowRoot!;
    const shield = root.querySelector<HTMLElement>('.tdw-selection-shield')!;

    window.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Alt', ctrlKey: true, altKey: true, bubbles: true })
    );
    expect(shield.dataset['active']).toBe('true');

    shield.dispatchEvent(
      new MouseEvent('pointermove', { clientX: 80, clientY: 70, bubbles: true })
    );
    await nextTick();
    expect(root.querySelector<HTMLElement>('.tdw-highlight')?.dataset['visible']).toBe('true');

    const clickWasNotCancelled = shield.dispatchEvent(
      new MouseEvent('click', { button: 0, bubbles: true, cancelable: true })
    );
    expect(clickWasNotCancelled).toBe(false);
    const dialog = root.querySelector<HTMLElement>('[role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog?.textContent).toContain('Tryb demo');

    const textarea = root.querySelector<HTMLTextAreaElement>('textarea')!;
    textarea.value = 'Dlaczego ten przycisk jest wyszarzony?';
    root
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    await nextTick();

    expect(chrome.runtime.sendMessage).toHaveBeenCalledOnce();
    expect(dialog?.textContent).toContain('Analiza demonstracyjna rozpoczeta');
    expect(dialog?.textContent).toContain('demo-crm-job-1');
  });
});

function nextTick(): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, 5));
}
