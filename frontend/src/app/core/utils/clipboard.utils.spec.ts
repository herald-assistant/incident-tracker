import { copyElementToClipboard, copyTextToClipboard } from './clipboard.utils';

describe('clipboard utils', () => {
  const navigatorClipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
  const clipboardItemDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'ClipboardItem');

  afterEach(() => {
    vi.restoreAllMocks();
    restoreNavigatorClipboard();
    restoreClipboardItem();
    document.body.innerHTML = '';
  });

  it('should copy plain text through navigator clipboard when available', async () => {
    const writeText = vi.fn(() => Promise.resolve());
    installNavigatorClipboard({ writeText });

    await expect(copyTextToClipboard('diagnosis')).resolves.toBe(true);

    expect(writeText).toHaveBeenCalledWith('diagnosis');
  });

  it('should copy an element as html and plain text without excluded controls', async () => {
    const write = vi.fn(() => Promise.resolve());
    installNavigatorClipboard({ write });
    installClipboardItemMock();

    const message = document.createElement('article');
    message.className = 'chat-message';
    message.innerHTML = `
      <div>
        <strong>AI</strong>
        <button data-clipboard-exclude>Kopiuj</button>
      </div>
      <p><strong>Diagnoza</strong>: sprawdz timeout.</p>
    `;
    document.body.appendChild(message);

    await expect(copyElementToClipboard(message)).resolves.toBe(true);

    const [[items]] = write.mock.calls as unknown as Array<[ClipboardItemMock[]]>;
    const clipboardItem = items[0];
    const html = await readBlobText(clipboardItem.items['text/html']);
    const plainText = await readBlobText(clipboardItem.items['text/plain']);

    expect(html).toContain('<article');
    expect(html).toContain('Diagnoza');
    expect(html).not.toContain('Kopiuj');
    expect(plainText).toContain('Diagnoza');
    expect(plainText).not.toContain('Kopiuj');
  });

  function installNavigatorClipboard(clipboard: Partial<Clipboard>): void {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: clipboard
    });
  }

  function installClipboardItemMock(): void {
    Object.defineProperty(globalThis, 'ClipboardItem', {
      configurable: true,
      value: ClipboardItemMock
    });
  }

  function restoreNavigatorClipboard(): void {
    if (navigatorClipboardDescriptor) {
      Object.defineProperty(navigator, 'clipboard', navigatorClipboardDescriptor);
      return;
    }

    Reflect.deleteProperty(navigator, 'clipboard');
  }

  function restoreClipboardItem(): void {
    if (clipboardItemDescriptor) {
      Object.defineProperty(globalThis, 'ClipboardItem', clipboardItemDescriptor);
      return;
    }

    Reflect.deleteProperty(globalThis, 'ClipboardItem');
  }
});

class ClipboardItemMock {
  constructor(readonly items: Record<string, Blob>) {}
}

function readBlobText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}
