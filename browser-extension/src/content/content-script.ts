import { ContentRuntime } from './content-runtime';

const RUNTIME_KEY = '__tdwBrowserCompanionRuntimeV1__';

interface RuntimeGlobal {
  [RUNTIME_KEY]?: ContentRuntime;
}

const runtimeGlobal = globalThis as RuntimeGlobal;
const existing = runtimeGlobal[RUNTIME_KEY];

if (existing) {
  void existing.refresh();
} else {
  const runtime = new ContentRuntime();
  runtimeGlobal[RUNTIME_KEY] = runtime;
  void runtime.start();
}
