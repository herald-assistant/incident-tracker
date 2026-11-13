import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { JSDOM } from 'jsdom';

const source = await readFile(new URL('../../public/browser-tools/protocol.js', import.meta.url), 'utf8');

function runtime(t) {
  const dom = new JSDOM('', { url: 'https://crm.example.com', runScripts: 'outside-only' });
  // Real native Web API objects, matching the types retained by HttpDebugSnapshotUtils.
  Object.assign(dom.window, { File, Blob, FormData, TextDecoder, TextEncoder });
  dom.window.eval(source);
  t.after(() => dom.window.close());
  const prepare = dom.window.__TDW_BROWSER_TOOLS_PROTOCOL_V1__.prepareHttpHistory;
  return async value => {
    const pending = prepare([{ id: 1, startedAt: '2026-11-12T10:00:00Z', durationMs: 1, state: 'success',
      request: { method: 'POST', url: 'https://crm.example.com/contacts', headers: {}, body: value },
      response: { status: 200, headers: {}, body: null } }]);
    return JSON.parse(JSON.stringify(await pending)).entries[0].request.body;
  };
}

function bytes(node) {
  return Buffer.from(node.content.data, node.content.encoding === 'base64' ? 'base64' : 'utf8');
}

test('File and Blob retain all bytes, MIME, names and modification time, including nested and empty values', async t => {
  const capture = runtime(t);
  const raw = Buffer.from(Array.from({ length: 65536 }, (_, i) => i % 256));
  const original = new File([raw], 'CRM-załącznik.bin', { type: 'application/octet-stream', lastModified: 123456789 });
  // These are exactly the native copies returned by the supplied utils customizer.
  const file = new File([original], original.name, { type: original.type, lastModified: original.lastModified });
  const blob = new Blob([raw], { type: 'application/octet-stream' }).slice(0, raw.length, 'application/octet-stream');
  const body = await capture({ attachments: [file, blob, new File([], '', { lastModified: 0 }), new Blob([])] });
  assert.equal(body.format, 'json');
  const [savedFile, savedBlob, emptyFile, emptyBlob] = body.value.attachments;
  assert.deepEqual(bytes(savedFile), raw);
  assert.deepEqual(bytes(savedBlob), raw);
  assert.equal(savedFile.$tdwType, 'File');
  assert.equal(savedFile.name, original.name);
  assert.equal(savedFile.lastModified, 123456789);
  assert.equal(savedFile.type, 'application/octet-stream');
  assert.equal(savedFile.size, raw.length);
  assert.equal(savedFile.content.byteLength, raw.length);
  assert.equal(savedFile.content.encoding, 'base64');
  assert.equal(savedFile.content.redacted, false);
  assert.equal(savedBlob.$tdwType, 'Blob');
  assert.equal('name' in savedBlob, false);
  assert.equal(emptyFile.name, '');
  assert.equal(emptyFile.lastModified, 0);
  assert.equal(emptyFile.type, '');
  assert.equal(bytes(emptyFile).length, 0);
  assert.equal(bytes(emptyBlob).length, 0);
});

test('UTF-8 blobs keep BOM, Unicode, line endings, NUL and exact JSON numbers without replacement', async t => {
  const capture = runtime(t);
  for (const text of ['\uFEFFCRM 🙂\r\n\u0000', '{"id":900719925474099312345,"id":2,"rate":1.230000000000001}', 'CRM🙂'.repeat(30000)]) {
    const body = await capture(new Blob([text], { type: 'application/json' }));
    assert.equal(body.value.content.encoding, 'utf-8');
    assert.equal(body.value.content.data, text);
    assert.deepEqual(bytes(body.value), Buffer.from(text, 'utf8'));
    assert.equal(body.value.content.byteLength, Buffer.byteLength(text));
  }
});

test('FormData preserves repeated fields, ordering, file metadata and the frozen observation', async t => {
  const capture = runtime(t);
  const original = new FormData();
  original.append('tag', 'CRM one');
  original.append('tag', 'CRM two');
  original.append('document', new File(['CRM notes'], 'contacts.txt', { type: 'text/plain', lastModified: 456 }));
  original.append('document', new Blob([new Uint8Array([255, 0, 254])]), 'raw.bin');
  original.append('empty', '');
  const form = new FormData();
  original.forEach((entry, name) => form.append(name, entry));
  const object = { form, labels: ['before'] };
  const pending = capture(object);
  form.set('tag', 'after'); form.delete('document'); object.labels[0] = 'after';
  const body = await pending;
  const entries = body.value.form.entries;
  assert.equal(body.value.form.$tdwType, 'FormData');
  assert.deepEqual(entries.map(entry => entry.name), ['tag', 'tag', 'document', 'document', 'empty']);
  assert.deepEqual(entries.slice(0, 2).map(entry => entry.value), ['CRM one', 'CRM two']);
  assert.equal(entries[2].value.name, 'contacts.txt');
  assert.equal(entries[2].value.lastModified, 456);
  assert.equal(entries[2].value.content.data, 'CRM notes');
  assert.equal(entries[3].value.name, 'raw.bin');
  assert.deepEqual(bytes(entries[3].value), Buffer.from([255, 0, 254]));
  assert.equal(entries[4].value, '');
  assert.deepEqual(body.value.labels, ['before']);
  assert.deepEqual((await capture(new FormData())).value.entries, []);
});

test('file text and FormData secrets are redacted without a second encoded copy of the secret', async t => {
  const capture = runtime(t);
  const form = new FormData();
  form.append('accessToken', 'CRM_FORM_SECRET');
  form.append('file', new File(['{"token":"CRM_FILE_SECRET","id":900719925474099312345}'], 'contacts.json', { type: 'application/json' }));
  const body = await capture(form);
  assert.equal(body.value.entries[0].value, '[REDACTED]');
  const saved = body.value.entries[1].value;
  assert.equal(saved.content.redacted, true);
  assert.equal(saved.content.byteLength, Buffer.byteLength(saved.content.data));
  assert.match(saved.content.data, /900719925474099312345/);
  assert.doesNotMatch(JSON.stringify(body), /CRM_FORM_SECRET|CRM_FILE_SECRET/);
});

test('OMITTED is source unavailability; read errors retain metadata and do not discard sibling values', async t => {
  const capture = runtime(t);
  assert.deepEqual(await capture('[OMITTED]'), { format: 'unavailable', value: null, reason: 'SOURCE_OMITTED' });
  assert.deepEqual((await capture({ note: '[OMITTED]' })).value, { note: '[OMITTED]' });
  class UnreadableFile extends File { async arrayBuffer() { throw new Error('CRM read failure'); } }
  const body = await capture({ file: new UnreadableFile(['CRM'], 'contacts.txt', { type: 'text/plain', lastModified: 789 }), other: 42 });
  assert.equal(body.format, 'json');
  assert.equal(body.value.other, 42);
  assert.equal(body.value.file.name, 'contacts.txt');
  assert.equal(body.value.file.size, 3);
  assert.deepEqual(body.value.file.content, { encoding: null, data: null, byteLength: null, redacted: false, reason: 'BINARY_READ_FAILED' });
  const cyclic = {}; cyclic.self = cyclic;
  assert.equal((await capture(cyclic)).reason, 'BODY_SERIALIZATION_FAILED');
  assert.equal((await capture(new ArrayBuffer(4))).reason, 'BODY_SERIALIZATION_FAILED');
  assert.equal((await capture(new URLSearchParams('view=contacts'))).value, 'view=contacts');
});
