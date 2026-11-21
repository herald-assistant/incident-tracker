import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

test('renders only a parent-provided document and blocks API execution and external validation', () => {
  let closeMessage; let selectionMode;
  const parent = {postMessage(message) { closeMessage = message; }}; let receive; let escape; let options; let renders = 0; let updates = 0; let removed = false;
  const window = {parent, addEventListener(type, handler) { if (type === 'message') receive = handler; else if (type === 'keydown') escape = handler; },
    SwaggerUIBundle(configuration) { options = configuration; renders++; return {specActions: {updateJsonSpec() { updates++; }}}; }};
  vm.runInNewContext(readFileSync(new URL('../public/openapi-viewer/viewer.js', import.meta.url), 'utf8'), {
    window, document: {documentElement: {classList: {toggle(name, enabled) { selectionMode = enabled; }}}, getElementById() { return {remove() { removed = true; }}; }}
  });
  const specification = {openapi: '3.0.3', paths: {}};
  receive({source: {}, data: {type: 'TDW_OPENAPI_DOCUMENT', specification, selection: null}}); assert.equal(renders, 0);
  receive({source: parent, data: {type: 'TDW_OPENAPI_DOCUMENT', specification, selection: null}});
  assert.equal(options.spec, specification); assert.equal(options.supportedSubmitMethods.length, 0);
  assert.equal(options.tryItOutEnabled, false); assert.equal(options.validatorUrl, null);
  assert.equal(options.queryConfigEnabled, false); assert.equal(options.withCredentials, false);
  assert.equal(options.url, undefined); assert.throws(() => options.requestInterceptor({url: 'http://example.invalid'}));
  assert.equal(removed, true);
  assert.equal(selectionMode, false);
  receive({source: parent, data: {type: 'TDW_OPENAPI_DOCUMENT', specification, selection: null}}); assert.equal(renders, 1); assert.equal(updates, 1);
  receive({source: parent, data: {type: 'TDW_OPENAPI_SELECTION_STATE', selection: []}}); assert.equal(selectionMode, true);
  escape({key: 'Escape', preventDefault() {}}); assert.equal(closeMessage.type, 'TDW_OPENAPI_VIEWER_CLOSE');
});
