(function initializeInstaller(global) {
  'use strict';

  const protocol = global.__TDW_BROWSER_TOOLS_PROTOCOL_V1__;
  const status = byId('loader-status');
  const bookmarkletLink = byId('bookmarklet-link');
  const bookmarkUrl = byId('bookmark-url');
  const snippetSource = byId('snippet-source');
  const copyBookmarkButton = byId('copy-bookmark-url');
  const copySnippetButton = byId('copy-snippet');
  const sizeLabel = byId('launcher-size');
  const sizeWarning = byId('size-warning');
  let preparedBookmarkUrl = '';
  let preparedSnippet = '';

  if (!protocol) {
    showFailure('Nie udało się załadować lokalnego protokołu Inspectora.');
    return;
  }

  copyBookmarkButton.addEventListener('click', async () => {
    if (!preparedBookmarkUrl) return;
    await copyAndReport(preparedBookmarkUrl, bookmarkUrl, copyBookmarkButton);
  });
  copySnippetButton.addEventListener('click', async () => {
    if (!preparedSnippet) return;
    await copyAndReport(preparedSnippet, snippetSource, copySnippetButton);
  });
  bookmarkletLink.addEventListener('dragstart', (event) => {
    if (!preparedBookmarkUrl) {
      event.preventDefault();
    }
  });

  void prepareLauncher();

  async function prepareLauncher() {
    try {
      const [protocolSource, runtimeSource] = await Promise.all([
        loadText('./protocol.js'),
        loadText('./runtime.js')
      ]);
      preparedSnippet = protocol.buildLauncherSource(
        {
          schema: 'tdw.browser-tool-launcher',
          version: 1,
          featureId: 'ui-explorer-inspector',
          tdwOrigin: global.location.origin
        },
        protocolSource,
        runtimeSource
      );
      preparedBookmarkUrl = protocol.toBookmarkUrl(
        protocol.buildRemoteLauncherSource({
          schema: 'tdw.browser-tool-launcher',
          version: 1,
          featureId: 'ui-explorer-inspector',
          tdwOrigin: global.location.origin
        })
      );
      bookmarkletLink.href = preparedBookmarkUrl;
      bookmarkletLink.setAttribute('aria-disabled', 'false');
      bookmarkUrl.value = preparedBookmarkUrl;
      snippetSource.value = preparedSnippet;
      copyBookmarkButton.disabled = false;
      copySnippetButton.disabled = false;

      const sourceBytes = new TextEncoder().encode(preparedSnippet).byteLength;
      const bookmarkBytes = new TextEncoder().encode(preparedBookmarkUrl).byteLength;
      sizeLabel.textContent =
        `Zakładka: ${formatBytes(bookmarkBytes)} · pełny snippet: ${formatBytes(sourceBytes)}.`;
      sizeWarning.hidden = bookmarkBytes <= 4 * 1024;
      status.classList.add('status-card--ready');
      status.replaceChildren(
        element('span', 'status-dot', ''),
        document.createTextNode(
          `Launcher jest gotowy i pobierze runtime tylko z ${global.location.origin}.`
        )
      );
    } catch (error) {
      console.error('[TDW Inspector Lite] Installer failed.', error);
      showFailure(
        'Nie udało się przygotować launchera. Odśwież stronę lub sprawdź statyczne zasoby TDW.'
      );
    }
  }

  async function loadText(path) {
    const response = await global.fetch(path, {
      cache: 'no-store',
      credentials: 'same-origin'
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} for ${path}`);
    }
    return response.text();
  }

  async function copyAndReport(value, fallbackControl, button) {
    const original = button.textContent;
    let copied = false;
    try {
      await global.navigator.clipboard.writeText(value);
      copied = true;
    } catch {
      try {
        fallbackControl.hidden = false;
        fallbackControl.focus();
        fallbackControl.select();
        copied = global.document.execCommand('copy');
      } catch {
        copied = false;
      }
    }
    button.textContent = copied ? 'Skopiowano' : 'Zaznacz i skopiuj ręcznie';
    global.setTimeout(() => {
      button.textContent = original;
    }, 1800);
  }

  function showFailure(message) {
    status.classList.add('status-card--error');
    status.textContent = message;
    bookmarkletLink.setAttribute('aria-disabled', 'true');
    copyBookmarkButton.disabled = true;
    copySnippetButton.disabled = true;
  }

  function formatBytes(value) {
    return value < 1024 ? `${value} B` : `${(value / 1024).toFixed(1)} KiB`;
  }

  function byId(id) {
    const node = global.document.getElementById(id);
    if (!node) {
      throw new Error(`Missing installer element #${id}`);
    }
    return node;
  }

  function element(tag, className, text) {
    const node = global.document.createElement(tag);
    node.className = className;
    node.textContent = text;
    return node;
  }
})(globalThis);
