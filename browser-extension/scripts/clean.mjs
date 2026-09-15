import { rm } from 'node:fs/promises';
import { resolve } from 'node:path';

const extensionRoot = resolve(import.meta.dirname, '..');

await rm(resolve(extensionRoot, 'dist'), { recursive: true, force: true });
