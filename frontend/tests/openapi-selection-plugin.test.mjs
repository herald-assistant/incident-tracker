import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';

const bundle = readFileSync(new URL('../node_modules/swagger-ui-dist/swagger-ui-bundle.js', import.meta.url), 'utf8');
const viewer = readFileSync(new URL('../public/openapi-viewer/viewer.js', import.meta.url), 'utf8');
const viewerCss = readFileSync(new URL('../public/openapi-viewer/viewer.css', import.meta.url), 'utf8');
const specification = {openapi: '3.0.3', info: {title: 'Fikcyjny CRM', version: '1'}, servers: [{url: 'http://127.0.0.1:8099'}], paths: {
  '/tickets/{id}': {get: {operationId: 'getTicket', parameters: [{name: 'id', in: 'path', required: true, schema: {type: 'string'}}],
    responses: {'200': {description: 'Dane', content: {'application/json': {schema: {$ref: '#/components/schemas/Ticket'}}}}}}},
  '/simulate': {post: {operationId: 'simulateCost', responses: {'200': {description: 'Wynik'}}}},
  '/unsupported': {patch: {operationId: 'patchTicket', responses: {'200': {description: 'Dane'}}}}
}, components: {schemas: {Ticket: {type: 'object', properties: {title: {type: 'string'}}}}}};
const rows = [
  {method: 'GET', path: '/tickets/{id}', checked: false, disabled: false, unavailableReason: null},
  {method: 'POST', path: '/simulate', checked: false, disabled: false, unavailableReason: null},
  {method: 'PATCH', path: '/unsupported', checked: false, disabled: true, unavailableReason: 'Nieobsługiwana metoda HTTP.'}
];
async function until(condition) {
  const deadline = Date.now() + 5000;
  while (!condition()) {
    if (Date.now() > deadline) throw new Error('Swagger UI did not render the expected state');
    await new Promise(resolve => setTimeout(resolve, 15));
  }
}
function setup() {
  const dom = new JSDOM('<p id="viewer-status">Ładowanie</p><div id="swagger-ui"></div>', {url: 'https://tdw.invalid/openapi-viewer/index.html', runScripts: 'outside-only', pretendToBeVisual: true});
  const {window} = dom; const sent = [];
  const style = window.document.createElement('style'); style.textContent = viewerCss; window.document.head.appendChild(style);
  window.scrollTo = () => {};
  window.matchMedia = () => ({matches: false, addListener() {}, removeListener() {}});
  window.postMessage = message => sent.push(message);
  window.eval(bundle); window.eval(viewer);
  const send = (data, source = window) => window.dispatchEvent(new window.MessageEvent('message', {source, data}));
  return {dom, window, sent, send, document: window.document};
}
test('real Swagger plugin supports controlled checkboxes, details, state and document updates', async () => {
  const {dom, window, sent, send, document} = setup();
  try {
    send({type: 'TDW_OPENAPI_DOCUMENT', specification, selection: rows});
    await until(() => document.querySelectorAll('.tdw-operation-checkbox').length === 3);
    const checkbox = () => document.querySelector('[aria-label="Udostępnij GET /tickets/{id}"]');
    const blocked = document.querySelector('[aria-label="Udostępnij PATCH /unsupported"]');
    assert.equal(blocked.disabled, true); assert.match(document.body.textContent, /Nieobsługiwana metoda HTTP/);
    document.querySelector('.opblock-get .opblock-summary-control').click();
    await until(() => /Parameters/.test(document.querySelector('.opblock-get')?.textContent));
    assert.match(document.querySelector('.opblock-get').textContent, /Parameters/);
    assert.equal(document.querySelector('.try-out, .execute'), null);
    checkbox().click();
    assert.deepEqual(JSON.parse(JSON.stringify(sent.at(-1))), {type: 'TDW_OPENAPI_SELECTION_CHANGE', method: 'GET', path: '/tickets/{id}', checked: true});
    send({type: 'TDW_OPENAPI_SELECTION_STATE', selection: rows.map((item, index) => ({...item, checked: index === 0}))});
    await until(() => checkbox().checked);
    assert.ok(document.querySelector('.opblock-get.is-open'), 'selection does not collapse details');
    document.querySelector('.opblock-tag').click();
    await until(() => document.querySelectorAll('.tdw-operation-checkbox').length === 0);
    assert.ok(document.documentElement.classList.contains('tdw-selection-mode'));
    assert.equal(window.getComputedStyle(document.querySelector('.information-container')).display, 'none');
    assert.equal(window.getComputedStyle(document.querySelector('.scheme-container')).display, 'none');
    document.querySelector('.opblock-tag').click();
    await until(() => document.querySelectorAll('.tdw-operation-checkbox').length === 3);
    assert.equal(checkbox().checked, true);
    send({type: 'TDW_OPENAPI_SELECTION_STATE', selection: rows}, {});
    assert.equal(checkbox().checked, true);
    send({type: 'TDW_OPENAPI_DOCUMENT', specification: {...specification, paths: {'/simulate': specification.paths['/simulate']}}, selection: [rows[1]]});
    await until(() => document.querySelectorAll('.tdw-operation-checkbox').length === 1);
    assert.ok(document.querySelector('[aria-label="Udostępnij POST /simulate"]'));
    document.querySelector('.opblock-summary-control').dispatchEvent(new window.KeyboardEvent('keydown', {key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true, cancelable: true}));
    assert.equal(sent.at(-1).type, 'TDW_OPENAPI_VIEWER_CLOSE');
  } finally { dom.window.close(); }
});
test('the same renderer remains read-only in contract preview', async () => {
  const {dom, send, document, window, sent} = setup();
  try {
    send({type: 'TDW_OPENAPI_DOCUMENT', specification, selection: null});
    await until(() => document.querySelectorAll('.opblock').length === 3);
    assert.equal(document.querySelector('.tdw-operation-checkbox'), null);
    assert.equal(document.querySelector('.try-out, .execute'), null);
    assert.ok(document.querySelector('.models'));
    assert.equal(document.documentElement.classList.contains('tdw-selection-mode'), false);
    assert.notEqual(window.getComputedStyle(document.querySelector('.information-container')).display, 'none');
    const operation = document.querySelector('.opblock-summary-control');
    operation.addEventListener('keydown', event => event.stopPropagation());
    operation.dispatchEvent(new window.KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
    assert.equal(sent.at(-1).type, 'TDW_OPENAPI_VIEWER_CLOSE');
  } finally { dom.window.close(); }
});

test('checkboxes follow summary centers after wrapping and release observers when collapsed', async () => {
  const {dom, window, send, document} = setup();
  const observers = []; let summaryHeight = 40;
  window.ResizeObserver = class {
    constructor(update) { this.update = update; observers.push(this); }
    observe() {}
    disconnect() { this.disconnected = true; }
  };
  const originalBounds = window.Element.prototype.getBoundingClientRect;
  window.Element.prototype.getBoundingClientRect = function () {
    if (this.classList.contains('tdw-operation-row')) return {top: 10, height: 300};
    if (this.classList.contains('opblock-summary')) return {
      top: this.closest('.tdw-operation-row').querySelector('.tdw-operation-unavailable') ? 70 : 30,
      height: summaryHeight
    };
    return originalBounds.call(this);
  };
  try {
    send({type: 'TDW_OPENAPI_DOCUMENT', specification, selection: rows});
    await until(() => document.querySelectorAll('.tdw-operation-checkbox').length === 3);
    const centers = () => [...document.querySelectorAll('.tdw-operation-row')].map(row => row.style.getPropertyValue('--tdw-summary-center'));
    assert.deepEqual(centers(), ['40px', '40px', '80px']);
    summaryHeight = 80; observers.forEach(observer => observer.update());
    assert.deepEqual(centers(), ['60px', '60px', '100px']);
    document.querySelector('.opblock-tag').click();
    await until(() => document.querySelectorAll('.tdw-operation-checkbox').length === 0);
    assert.ok(observers.every(observer => observer.disconnected));
  } finally { dom.window.close(); }
});
