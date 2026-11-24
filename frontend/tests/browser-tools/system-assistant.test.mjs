import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';

const root = new URL('../../public/browser-tools/', import.meta.url);
const protocol = await readFile(new URL('protocol.js', root), 'utf8');
const runtime = await readFile(new URL('runtime.js', root), 'utf8');
const origin = 'http://127.0.0.1:8080';
function setup(enabled = true) {
  const dom = new JSDOM('<html><head><title>CRM demo</title></head><body><input value="private" /></body></html>', {
    url: 'http://127.0.0.1:8099/tickets/CRM-101?secret=never-transfer#private', runScripts: 'outside-only'
  });
  const window = dom.window;
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOLS_AVAILABILITY__ = { systemAssistantEnabled: enabled };
  window.__TDW_BROWSER_TOOL_CONFIG__ = { schema: 'tdw.browser-tool-launcher', version: 1, featureId: 'browser-tools', tdwOrigin: origin };
  let popups = 0; window.open = () => {popups++; return null;}; window.eval(protocol); window.eval(runtime);
  const shell = window.document.querySelector('[data-tdw-browser-tool-root="browser-tools-shell"]');
  const action = shell.shadowRoot.querySelector('[data-feature-id="system-assistant"]');
  return {dom, window, shell, action, popups: () => popups,
    panel: () => window.document.querySelector('[data-tdw-browser-tool-root="system-assistant"]')};
}
function handshake(current) {
  const frame=current.panel().shadowRoot.querySelector('iframe'); const sent=[];
  const peer={postMessage:(data,target)=>sent.push({data,target})};
  Object.defineProperty(frame,'contentWindow',{value:peer,configurable:true});
  const nonce=new URLSearchParams(new URL(frame.src).hash.slice(1)).get('nonce');
  const receive=(data,source=peer,eventOrigin=origin)=>current.window.dispatchEvent(new current.window.MessageEvent('message',{data,source,origin:eventOrigin}));
  const ready={type:'TDW_SYSTEM_ASSISTANT_READY',protocolVersion:1,nonce};
  return {frame,sent,peer,nonce,receive,ready};
}
function pointer(current, target, type, x, id = 1, button = 0) {
  const event = new current.window.MouseEvent(type, {bubbles: true, cancelable: true, clientX: x, button});
  Object.defineProperty(event, 'pointerId', {value: id}); target.dispatchEvent(event);
}
function viewport(current, width) {
  Object.defineProperty(current.window, 'innerWidth', {configurable: true, value: width});
  current.window.dispatchEvent(new current.window.Event('resize'));
}
function panelWidth(panel) { return Number.parseInt(panel.style.getPropertyValue('--assistant-panel-width'), 10); }

test('left-edge dragging resizes in both directions, clamps width and keeps the iframe', t => {
  const current = setup(); t.after(() => current.dom.window.close()); current.action.click(); viewport(current, 1200);
  const panel = current.panel(); const frame = panel.shadowRoot.querySelector('iframe'); const src = frame.src;
  const handle = panel.shadowRoot.querySelector('.assistant-resize-handle');
  const captured = new Set(); handle.setPointerCapture = id => captured.add(id);
  handle.hasPointerCapture = id => captured.has(id); handle.releasePointerCapture = id => captured.delete(id);
  pointer(current, handle, 'pointerdown', 632);
  assert.equal(captured.has(1), true); assert.equal(panel.hasAttribute('data-resizing'), true);
  pointer(current, current.window, 'pointermove', 432, 2); assert.equal(panelWidth(panel), 660);
  pointer(current, current.window, 'pointermove', 432); assert.equal(panelWidth(panel), 860);
  pointer(current, current.window, 'pointermove', -2000); assert.equal(panelWidth(panel), 1184);
  pointer(current, current.window, 'pointermove', 2000); assert.equal(panelWidth(panel), 320);
  pointer(current, current.window, 'pointerup', 2000);
  assert.equal(captured.size, 0); assert.equal(panel.hasAttribute('data-resizing'), false);
  assert.equal(panel.shadowRoot.querySelector('.assistant-resize-shield').hidden, true);
  pointer(current, current.window, 'pointermove', 0); assert.equal(panelWidth(panel), 320);
  assert.equal(panel.shadowRoot.querySelector('iframe'), frame); assert.equal(frame.src, src);
});

test('maximization restores custom width, survives hide/reopen and responds to viewport changes', t => {
  const current = setup(); t.after(() => current.dom.window.close()); current.action.click(); viewport(current, 1200);
  const panel = current.panel(); const frame = panel.shadowRoot.querySelector('iframe'); const src = frame.src;
  const handle = panel.shadowRoot.querySelector('.assistant-resize-handle');
  const maximize = panel.shadowRoot.querySelector('.assistant-maximize');
  assert.equal(maximize.nextElementSibling.className, 'assistant-close');
  pointer(current, handle, 'pointerdown', 600); pointer(current, current.window, 'pointermove', 560); pointer(current, current.window, 'pointerup', 560);
  assert.equal(panelWidth(panel), 700); maximize.click();
  assert.equal(panelWidth(panel), 1184); assert.equal(handle.hidden, true);
  assert.equal(maximize.getAttribute('aria-pressed'), 'true'); assert.equal(maximize.title, 'Przywróć rozmiar panelu');
  viewport(current, 800); assert.equal(panelWidth(panel), 784);
  panel.shadowRoot.querySelector('.assistant-close').click(); current.action.click();
  assert.equal(panelWidth(panel), 784); assert.equal(maximize.getAttribute('aria-pressed'), 'true');
  maximize.click(); assert.equal(panelWidth(panel), 700); assert.equal(handle.hidden, false);
  assert.equal(maximize.getAttribute('aria-label'), 'Maksymalizuj panel');
  viewport(current, 300); assert.equal(panelWidth(panel), 284);
  assert.equal(handle.getAttribute('aria-valuemin'), '284'); assert.equal(handle.getAttribute('aria-valuemax'), '284');
  viewport(current, 1200); assert.equal(panelWidth(panel), 700);
  assert.equal(panel.shadowRoot.querySelector('iframe'), frame); assert.equal(frame.src, src);
});

test('the resize separator supports keyboard width control within the same limits', t => {
  const current = setup(); t.after(() => current.dom.window.close()); current.action.click(); viewport(current, 1200);
  const panel = current.panel(); const handle = panel.shadowRoot.querySelector('[role=separator]');
  const key = (name, shiftKey = false) => handle.dispatchEvent(new current.window.KeyboardEvent('keydown', {key: name, shiftKey, bubbles: true, cancelable: true}));
  assert.equal(handle.tabIndex, 0); assert.equal(handle.getAttribute('aria-orientation'), 'vertical');
  key('ArrowLeft'); assert.equal(panelWidth(panel), 680); key('ArrowRight', true); assert.equal(panelWidth(panel), 630);
  key('Home'); assert.equal(panelWidth(panel), 320); key('End'); assert.equal(panelWidth(panel), 1184);
  assert.equal(handle.getAttribute('aria-valuenow'), '1184'); key('ArrowLeft'); assert.equal(panelWidth(panel), 1184);
});

for (const end of ['pointercancel', 'lostpointercapture', 'blur', 'hide', 'dispose']) {
  test(`resize cleanup after ${end} leaves no active shield or listeners`, t => {
    const current = setup(); t.after(() => current.dom.window.close()); current.action.click();
    const panel = current.panel(); const handle = panel.shadowRoot.querySelector('.assistant-resize-handle');
    pointer(current, handle, 'pointerdown', 600, 1, 2); assert.equal(panel.hasAttribute('data-resizing'), false);
    pointer(current, handle, 'pointerdown', 600); pointer(current, current.window, 'pointermove', 550);
    const before = panelWidth(panel);
    if (end === 'hide') panel.shadowRoot.querySelector('.assistant-close').click();
    else if (end === 'dispose') current.window.__TDW_BROWSER_TOOL_ACTIVE_INSTANCE__.dispose();
    else if (end === 'blur') current.window.dispatchEvent(new current.window.Event('blur'));
    else pointer(current, end === 'lostpointercapture' ? handle : current.window, end, 550);
    assert.equal(panel.hasAttribute('data-resizing'), false); assert.equal(panel.shadowRoot.querySelector('.assistant-resize-shield').hidden, true);
    pointer(current, current.window, 'pointermove', 300); assert.equal(panelWidth(panel), before);
    if (end === 'dispose') { viewport(current, 2000); assert.equal(panelWidth(panel), before); }
  });
}
test('assistant is hidden when disabled and opens an isolated iframe instead of a popup', t => {
  const disabled=setup(false); t.after(()=>disabled.dom.window.close());
  disabled.action.click(); assert.equal(disabled.action.hidden,true); assert.equal(disabled.panel(),null);
  const current=setup(); t.after(()=>current.dom.window.close());current.action.click();
  const frame=current.panel().shadowRoot.querySelector('iframe');
  assert.equal(current.popups(),0); assert.equal(new URL(frame.src).pathname,'/system-assistant/chat');
  assert.equal(frame.title,'Rozmowa System Assistant'); assert.equal(frame.referrerPolicy,'no-referrer');
  assert.equal(frame.getAttribute('sandbox'),'allow-scripts allow-same-origin allow-forms');
});
test('iframe handshake checks origin, source, nonce and shape and transfers only small page context', t => {
  const current=setup();t.after(()=>current.dom.window.close());current.action.click();
  const h=handshake(current);
  h.receive(h.ready,h.peer,'https://wrong.example');h.receive(h.ready,null);
  h.receive({...h.ready,nonce:'wrong'});h.receive({...h.ready,extra:'forbidden'});
  assert.equal(h.sent.length,0);h.receive(h.ready);
  assert.equal(h.sent.length,1);
  assert.deepEqual(Object.keys(h.sent[0].data.context).sort(),['capturedAt','origin','pathname','title']);
  assert.equal(h.sent[0].data.context.pathname,'/tickets/CRM-101');
  assert.equal(JSON.stringify(h.sent).includes('never-transfer'),false);
  assert.equal(h.sent[0].target,origin);
  h.receive({type:'TDW_SYSTEM_ASSISTANT_RECEIVED',protocolVersion:1,nonce:h.nonce,contextId:h.sent[0].data.contextId});
  assert.equal(current.panel().shadowRoot.querySelector('.assistant-status').hidden,true);
  assert.equal(current.shell.hidden,true);
});
test('closing and reopening keeps the same frame and original context, removal cleans up everything', t => {
  const current=setup();t.after(()=>current.dom.window.close());current.action.click();
  const h=handshake(current);h.receive(h.ready);
  h.receive({type:'TDW_SYSTEM_ASSISTANT_RECEIVED',protocolVersion:1,nonce:h.nonce,contextId:h.sent[0].data.contextId});
  const panel=current.panel();const src=h.frame.src;
  panel.shadowRoot.querySelector('.assistant-close').click();assert.equal(panel.hidden,true);assert.equal(current.shell.hidden,false);
  current.window.history.replaceState(null,'','/different/page');current.action.click();
  assert.equal(current.panel(),panel);assert.equal(panel.hidden,false);assert.equal(panel.shadowRoot.querySelector('iframe'),h.frame);assert.equal(h.frame.src,src);
  current.window.__TDW_BROWSER_TOOL_ACTIVE_INSTANCE__.dispose();
  assert.equal(current.panel(),null);assert.equal(current.shell.isConnected,false);
  h.receive(h.ready);assert.equal(h.sent.length,1);
});
test('failure is visible in the panel with a manual link and retry uses a fresh nonce', async t => {
  const current=setup();t.after(()=>current.dom.window.close());current.action.click();
  const panel=current.panel();const previous=panel.shadowRoot.querySelector('iframe').src;
  await new Promise(resolve=>current.window.setTimeout(resolve,110));
  assert.equal(panel.shadowRoot.querySelector('[role="alert"]').hidden,false);
  const link=panel.shadowRoot.querySelector('a');assert.equal(link.href,origin+'/system-assistant');assert.equal(link.rel,'noopener noreferrer');
  panel.shadowRoot.querySelector('.assistant-retry').click();
  assert.notEqual(panel.shadowRoot.querySelector('iframe').src,previous);assert.equal(current.popups(),0);
});
test('reloading the bookmarklet preserves a live panel and disabling availability disposes it', t => {
  const current=setup();t.after(()=>current.dom.window.close());current.action.click();const panel=current.panel();
  const h=handshake(current);h.receive(h.ready);h.receive({type:'TDW_SYSTEM_ASSISTANT_RECEIVED',protocolVersion:1,nonce:h.nonce,contextId:h.sent[0].data.contextId});
  current.window.__TDW_BROWSER_TOOL_CONFIG__={schema:'tdw.browser-tool-launcher',version:1,featureId:'browser-tools',tdwOrigin:origin};
  current.window.__TDW_BROWSER_TOOLS_AVAILABILITY__={systemAssistantEnabled:true};current.window.eval(protocol);current.window.eval(runtime);
  assert.equal(current.panel(),panel);assert.equal(panel.hidden,false);
  current.window.__TDW_BROWSER_TOOL_CONFIG__={schema:'tdw.browser-tool-launcher',version:1,featureId:'browser-tools',tdwOrigin:origin};
  current.window.__TDW_BROWSER_TOOLS_AVAILABILITY__={systemAssistantEnabled:false};current.window.eval(protocol);current.window.eval(runtime);
  assert.equal(current.panel(),null);assert.equal(current.action.hidden,true);assert.equal(current.shell.hidden,false);
});
