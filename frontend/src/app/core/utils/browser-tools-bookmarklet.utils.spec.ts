import {
  BROWSER_TOOLS_RUNTIME_VERSION,
  buildBrowserToolsBookmarkletUrl
} from './browser-tools-bookmarklet.utils';

describe('browser tools bookmarklet', () => {
  it('builds a small executable launcher pinned to the current TDW origin', () => {
    const bookmarklet = buildBrowserToolsBookmarkletUrl('https://tdw.example.com');
    const source = decodeURIComponent(bookmarklet.slice('javascript:'.length));

    expect(bookmarklet).toMatch(/^javascript:/);
    expect(source).toContain(
      `https://tdw.example.com/browser-tools/loader.js?v=${BROWSER_TOOLS_RUNTIME_VERSION}`
    );
    expect(source).toContain("s.dataset.tdwFeatureId='ux-inspector'");
    expect(source).not.toContain('Snippet');
    expect(new TextEncoder().encode(bookmarklet).byteLength).toBeLessThan(2048);
    expect(() => new Function(source)).not.toThrow();
  });

  it('rejects origins with a path or a non-http scheme', () => {
    expect(() => buildBrowserToolsBookmarkletUrl('https://tdw.example.com/path')).toThrow();
    expect(() => buildBrowserToolsBookmarkletUrl('javascript:alert(1)')).toThrow();
  });
});
