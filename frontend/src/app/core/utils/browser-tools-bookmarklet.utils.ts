export const BROWSER_TOOLS_LAUNCHER_VERSION = 1 as const;
export const BROWSER_TOOLS_RUNTIME_VERSION = '1.0.0' as const;

export function buildBrowserToolsBookmarkletUrl(tdwOrigin: string): string {
  const origin = normalizeHttpOrigin(tdwOrigin);
  const loaderUrl = new URL('/browser-tools/loader.js', origin);
  loaderUrl.searchParams.set('v', BROWSER_TOOLS_RUNTIME_VERSION);

  const source = [
    ';(function(d){',
    "var s=d.createElement('script');",
    `s.src=${JSON.stringify(loaderUrl.toString())};`,
    "s.dataset.tdwFeatureId='ux-inspector';",
    "s.referrerPolicy='no-referrer';",
    "s.onerror=function(){s.remove();alert('TDW Browser Tools: skrypt zostal zablokowany.');};",
    '(d.head||d.documentElement).appendChild(s);',
    '}(document));'
  ].join('');

  return `javascript:${encodeURIComponent(source)}`;
}

function normalizeHttpOrigin(value: string): string {
  const normalized = value.trim();
  const url = new URL(normalized);
  if (!['http:', 'https:'].includes(url.protocol) || url.origin !== normalized) {
    throw new Error('TDW Browser Tools wymaga originu HTTP(S) bez ścieżki.');
  }
  return url.origin;
}
