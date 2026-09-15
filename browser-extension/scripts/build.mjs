import { build } from 'esbuild';
import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const extensionRoot = resolve(import.meta.dirname, '..');
const sourceRoot = resolve(extensionRoot, 'src');
const outputRoot = resolve(extensionRoot, 'dist');

await rm(outputRoot, { recursive: true, force: true });
await mkdir(outputRoot, { recursive: true });

const commonBuild = {
  bundle: true,
  target: ['chrome120'],
  minify: false,
  sourcemap: false,
  legalComments: 'none',
  logLevel: 'info'
};

await Promise.all([
  build({
    ...commonBuild,
    entryPoints: [resolve(sourceRoot, 'background/service-worker.ts')],
    outfile: resolve(outputRoot, 'background/service-worker.js'),
    format: 'esm'
  }),
  build({
    ...commonBuild,
    entryPoints: [resolve(sourceRoot, 'content/content-script.ts')],
    outfile: resolve(outputRoot, 'content/content-script.js'),
    format: 'iife'
  }),
  build({
    ...commonBuild,
    entryPoints: [resolve(sourceRoot, 'popup/popup.ts')],
    outfile: resolve(outputRoot, 'popup/popup.js'),
    format: 'iife'
  }),
  build({
    ...commonBuild,
    entryPoints: [resolve(sourceRoot, 'options/options.ts')],
    outfile: resolve(outputRoot, 'options/options.js'),
    format: 'iife'
  })
]);

for (const relativePath of [
  'manifest.json',
  'popup/popup.html',
  'popup/popup.css',
  'options/options.html',
  'options/options.css'
]) {
  const source = resolve(sourceRoot, relativePath);
  const target = resolve(outputRoot, relativePath);
  await mkdir(resolve(target, '..'), { recursive: true });
  await cp(source, target);
}

const manifest = JSON.parse(await readFile(resolve(outputRoot, 'manifest.json'), 'utf8'));
const packageJson = JSON.parse(await readFile(resolve(extensionRoot, 'package.json'), 'utf8'));

if (manifest.version !== packageJson.version) {
  throw new Error(`Manifest version ${manifest.version} differs from package version ${packageJson.version}.`);
}

if (manifest.host_permissions?.includes('<all_urls>')) {
  throw new Error('Permanent <all_urls> host permission is forbidden.');
}

await writeFile(
  resolve(outputRoot, 'LOAD_UNPACKED.txt'),
  [
    'TDW Browser Companion',
    '',
    'Open chrome://extensions, enable Developer mode, choose Load unpacked',
    'and select this dist directory.',
    ''
  ].join('\n'),
  'utf8'
);
