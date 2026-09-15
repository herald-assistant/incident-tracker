import { afterEach, vi } from 'vitest';

class TestResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

Object.defineProperty(globalThis, 'ResizeObserver', {
  configurable: true,
  writable: true,
  value: TestResizeObserver
});

Object.defineProperty(window, 'requestAnimationFrame', {
  configurable: true,
  writable: true,
  value: (callback: FrameRequestCallback) => window.setTimeout(() => callback(performance.now()), 0)
});

Object.defineProperty(window, 'cancelAnimationFrame', {
  configurable: true,
  writable: true,
  value: (handle: number) => window.clearTimeout(handle)
});

const runtimeOnMessage = {
  addListener: vi.fn(),
  removeListener: vi.fn()
};

Object.defineProperty(globalThis, 'chrome', {
  configurable: true,
  writable: true,
  value: {
    runtime: {
      getManifest: vi.fn(() => ({ version: '0.1.0-test' })),
      sendMessage: vi.fn(),
      onMessage: runtimeOnMessage
    },
    storage: {
      local: {
        get: vi.fn(),
        set: vi.fn()
      }
    }
  }
});

afterEach(() => {
  document.body.replaceChildren();
  document.head.replaceChildren();
  vi.clearAllMocks();
});
